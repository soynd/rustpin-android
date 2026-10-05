package com.rustpin.android.work

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mirrors native/src/jobs.rs: download original -> fit inside target box -> save.
 * Desktop upscales with Upscayl (no Android GPU equivalent), so Android saves
 * the true original and downscales only when it exceeds the chosen target box.
 */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(K_URL).orEmpty()
        val title = inputData.getString(K_TITLE).orEmpty()
        val pinId = inputData.getString(K_PIN).orEmpty()
        val boxW = inputData.getInt(K_BOX_W, 1920)
        val boxH = inputData.getInt(K_BOX_H, 1080)
        if (url.isEmpty()) return@withContext Result.failure()
        setProgress(workDataOf(K_STAGE to "downloading"))
        try {
            val bytes = fetch(url) ?: return@withContext Result.failure(workDataOf(K_ERR to "empty image"))
            setProgress(workDataOf(K_STAGE to "saving"))
            val fitted = fitInside(bytes, boxW, boxH)
            val name = saveToPictures(fitted, fileStem(title, pinId))
            Result.success(workDataOf(K_STAGE to "done", K_FILE to name))
        } catch (e: Exception) {
            Result.failure(workDataOf(K_ERR to (e.message ?: "download failed").take(500)))
        }
    }

    private suspend fun fetch(url: String): ByteArray? {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
            .header("Referer", "https://www.pinterest.com/").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
            val body = r.body ?: return null
            val total = body.contentLength()
            val stream = body.byteStream()
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var done = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                if (total > 0) setProgress(workDataOf(K_STAGE to "downloading", K_PCT to (done.toFloat() / total).coerceIn(0f, 1f)))
            }
            val b = out.toByteArray()
            if (b.isEmpty()) return null
            setProgress(workDataOf(K_STAGE to "saving", K_PCT to 1f))
            return b
        }
    }

    /** Downscale only; never upscale (keeps original quality like desktop delete_orig=false path). */
    private fun fitInside(bytes: ByteArray, bw: Int, bh: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth; val h = bounds.outHeight
        if (w < 2 || h < 2 || (w <= bw && h <= bh)) return bytes
        val s = minOf(bw.toDouble() / w, bh.toDouble() / h)
        var nw = ((w * s).toInt().coerceAtLeast(16)) and 1.inv()
        var nh = ((h * s).toInt().coerceAtLeast(16)) and 1.inv()
        var sample = 1
        while (w / (sample * 2) >= nw && h / (sample * 2) >= nh) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return bytes
        val sw = (nw.toDouble() / bmp.width).coerceAtMost(1.0)
        val sh = (nh.toDouble() / bmp.height).coerceAtMost(1.0)
        val sc = minOf(sw, sh)
        val out = if (sc < 1.0) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(16), (bmp.height * sc).toInt().coerceAtLeast(16), true) else bmp
        val baos = java.io.ByteArrayOutputStream()
        out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
        if (out !== bmp) bmp.recycle()
        out.recycle()
        return baos.toByteArray()
    }

    private fun saveToPictures(bytes: ByteArray, stem: String): String {
        val name = uniqueName(stem)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/rustpin")
            }
            val uri = applicationContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
            applicationContext.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            return name
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "rustpin")
            dir.mkdirs()
            var f = File(dir, name); var n = 2
            while (f.exists()) { f = File(dir, stem + "-" + n + ".png"); n++ }
            f.writeBytes(bytes)
            return f.name
        }
    }

    private fun uniqueName(stem: String): String = stem + ".png"

    companion object {
        const val K_URL = "url"
        const val K_TITLE = "title"
        const val K_PIN = "pin"
        const val K_BOX_W = "boxW"
        const val K_BOX_H = "boxH"
        const val K_STAGE = "stage"
        const val K_PCT = "pct"
        const val K_FILE = "file"
        const val K_ERR = "err"

        // Single source of truth lives in store/Names.kt (unit-tested).
        fun fileStem(title: String, pinId: String): String =
            com.rustpin.android.store.fileStem(title, pinId)
    }
}
