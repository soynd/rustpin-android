package com.rustpin.android.net

import com.rustpin.android.model.Pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// Same desktop UA as native/src/pinterest.rs - mobile UAs get different/blocked responses.
private const val UA = "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"
private const val FALLBACK_APP_VERSION = "bb7a14c"
private const val PAGE_SIZE = 25

/** In-memory cookies: mirrors native/src/pinterest.rs session. No login. */
private class MemoryCookies : CookieJar {
    private val jar = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) { jar.removeAll { it.name == c.name }; jar.add(c) }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = jar.toList()
    @Synchronized fun value(name: String): String = jar.firstOrNull { it.name == name }?.value.orEmpty()
}

object PinterestClient {
    private val cookies = MemoryCookies()
    private val http = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var appVersion: String = FALLBACK_APP_VERSION
    @Volatile private var sessionReady = false

    suspend fun ensureSession(force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (sessionReady && !force) return@withContext Result.success(Unit)
        try {
            val req = Request.Builder().url("https://www.pinterest.com/")
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Upgrade-Insecure-Requests", "1")
                .get().build()
            http.newCall(req).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                appVersionOf(html)?.let { if (it.isNotBlank()) appVersion = it }
            }
            sessionReady = true
            Result.success(Unit)
        } catch (e: Exception) {
            sessionReady = false
            Result.failure(Exception(e.message ?: "session failed"))
        }
    }

    suspend fun search(query: String, bookmarks: List<String>): Result<Pair<List<Pin>, List<String>>> =
        withContext(Dispatchers.IO) {
            val sess = ensureSession()
            if (sess.isFailure && cookies.value("csrftoken").isEmpty()) {
                return@withContext Result.failure(sess.exceptionOrNull() ?: Exception("session failed"))
            }
            try {
                // NOTE: source_url is sent RAW here - apiGet encodes it once,
                // exactly like native encode(&source_url). Pre-encoding it
                // would double-encode the query and break search.
                val sourceUrl = "/search/pins/?q=" + query + "&rs=typed"
                val options = buildJsonObject {
                    put("appliedProductFilters", "---")
                    put("auto_correction_disabled", false)
                    put("bookmarks", JsonArray(bookmarks.map { JsonPrimitive(it) }))
                    put("page_size", PAGE_SIZE)
                    put("query", query)
                    put("redux_normalize_feed", true)
                    put("rs", "typed")
                    put("scope", "pins")
                    put("source_url", sourceUrl)
                }
                val data = apiGet("BaseSearchResource", options, "https://www.pinterest.com/search/pins/?q=" + enc(query), "www/search/[scope].js")
                val items = data["resource_response"]?.jsonObject?.get("data")?.jsonObject?.get("results")?.jsonArray ?: JsonArray(emptyList())
                Result.success(parsePins(items) to bookmarksOf(data))
            } catch (e: Exception) { Result.failure(Exception(e.message ?: "search failed")) }
        }

    suspend fun related(pinId: String, bookmarks: List<String> = emptyList()): Result<Pair<List<Pin>, List<String>>> =
        withContext(Dispatchers.IO) {
            ensureSession()
            try {
                val options = buildJsonObject {
                    put("field_set_key", "unauth_react")
                    put("page_size", PAGE_SIZE)
                    put("pin_id", pinId)
                    put("bookmarks", JsonArray(bookmarks.map { JsonPrimitive(it) }))
                }
                // source_url must carry pin id like native "/pin/{id}/" so Pinterest returns related modules
                val withSource = buildJsonObject {
                    for ((k, v) in options) put(k, v)
                    put("source_url", "/pin/" + pinId + "/")
                }
                val data = apiGet("RelatedModulesResource", withSource, "https://www.pinterest.com/pin/" + pinId + "/", "www/pin/[id].js")
                val node = data["resource_response"]?.jsonObject?.get("data")
                val items: JsonArray = when {
                    node is JsonArray -> node
                    node is JsonObject -> node["results"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                Result.success(parsePins(items) to bookmarksOf(data))
            } catch (e: Exception) { Result.failure(Exception(e.message ?: "related failed")) }
        }

    private fun apiGet(resource: String, options: JsonObject, referer: String, handler: String): JsonObject {
        val sourceUrl = options["source_url"]?.jsonPrimitive?.contentOrNull ?: "/"
        val payload = buildJsonObject { put("options", options); put("context", buildJsonObject {}) }.toString()
        val stamp = System.currentTimeMillis()
        val url = "https://www.pinterest.com/resource/" + resource + "/get/?source_url=" + enc(sourceUrl) + "&data=" + enc(payload) + "&_=" + stamp
        val csrf = cookies.value("csrftoken")
        val b = Request.Builder().url(url).header("User-Agent", UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Pinterest-AppState", "active")
            .header("X-Pinterest-PWS-Handler", handler)
            .header("Referer", referer)
        if (csrf.isNotEmpty()) b.header("X-CSRFToken", csrf)
        if (appVersion.isNotEmpty()) b.header("X-Pinterest-App-Version", appVersion)
        http.newCall(b.get().build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val snippet = (resp.body?.string().orEmpty()).take(300).replace('\n', ' ')
                throw IllegalStateException("HTTP " + resp.code + " " + snippet)
            }
            val body = resp.body?.string().orEmpty()
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun parsePins(items: JsonArray): List<Pin> {
        val out = mutableListOf<Pin>()
        for (el in items) {
            if (el !is JsonObject) continue
            val images = el["images"] as? JsonObject ?: continue
            val orig = urlOf(images, "orig")
            if (orig.isEmpty()) continue
            var (w, h) = dimsOf(images, "orig")
            if (w == 0 || h == 0) { val d = dimsOf(images, "736x"); w = d.first; h = d.second }
            val id = firstStr(el, listOf("id", "pin_id"))
            var title = firstStr(el, listOf("grid_title", "title", "description"))
            if (title.isBlank()) title = "untitled"
            if (title.length > 120) title = title.take(120)
            val preview = urlOf(images, "736x").ifEmpty { orig }
            val thumb = urlOf(images, "236x").ifEmpty { preview }
            out.add(Pin(id, title, thumb, preview, orig, if (id.isEmpty()) "" else "https://www.pinterest.com/pin/" + id + "/", w, h))
        }
        return out
    }

    private fun bookmarksOf(data: JsonObject): List<String> {
        val raw = data["resource"]?.jsonObject?.get("options")?.jsonObject?.get("bookmarks")?.jsonArray ?: return emptyList()
        if (raw.size == 1 && raw[0].jsonPrimitive.contentOrNull == "-end-") return emptyList()
        return raw.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    private fun urlOf(images: JsonObject, size: String): String =
        images[size]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun dimsOf(images: JsonObject, size: String): Pair<Int, Int> {
        val n = images[size]?.jsonObject ?: return 0 to 0
        fun num(k: String) = n[k]?.jsonPrimitive?.doubleOrNull?.coerceAtLeast(1.0)?.toInt() ?: 0
        return num("width") to num("height")
    }
    private fun firstStr(o: JsonObject, keys: List<String>): String {
        for (k in keys) { val s = o[k]?.jsonPrimitive?.contentOrNull; if (!s.isNullOrBlank()) return s }
        return ""
    }
    private fun appVersionOf(html: String): String? {
        // same as native: key '"appVersion":' then trim + strip leading quote
        val key = "\"appVersion\":\""
        val s = html.indexOf(key)
        if (s < 0) return null
        val rest = html.substring(s + key.length)
        val e = rest.indexOf('\"')
        return if (e > 0) rest.substring(0, e) else null
    }
    // Native encode(): leaves alphanumerics + -_.~ unescaped, everything else %XX.
    // URLEncoder mangles that (~ -> %7E etc.), so do it by hand for parity.
    private fun enc(s: String): String {
        val sb = StringBuilder(s.length)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~') sb.append(ch)
            else sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
        return sb.toString()
    }
    private val HEX = "0123456789ABCDEF".toCharArray()
}
