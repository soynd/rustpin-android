package com.rustpin.android.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.rustpin.android.model.Pin
import com.rustpin.android.net.PinterestClient
import com.rustpin.android.store.Accent
import com.rustpin.android.store.AppSettings
import com.rustpin.android.store.SettingsStore
import com.rustpin.android.work.DownloadWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Single source of truth: search feed, detail, downloads, appearance. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val wm = WorkManager.getInstance(app)

    var settings by mutableStateOf(AppSettings()); private set
    var query by mutableStateOf(""); private set
    var pins = mutableStateListOf<Pin>(); private set
    var bookmarks = mutableStateListOf<String>(); private set
    var loading by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set
    var searchFocusTick by mutableIntStateOf(0); private set

    fun focusSearch() { searchFocusTick++ }
    var selected: Pin? by mutableStateOf(null); private set
    var similar = mutableStateListOf<Pin>(); private set
    var similarLoading by mutableStateOf(false); private set
    var similarError by mutableStateOf(""); private set
    var fullscreen by mutableStateOf(false); private set
    var activeJobId: UUID? by mutableStateOf(null); private set
    var activeProgress by mutableFloatStateOf(0f); private set
    var activeStage by mutableStateOf(""); private set
    var activeFile by mutableStateOf(""); private set
    var activeError by mutableStateOf(""); private set
    private val seen = mutableSetOf<String>()
    private var lastQuerySent = ""
    private var searchJob: Job? = null
    private var autoJob: Job? = null

    init {
        viewModelScope.launch {
            store.flow.collect { s ->
                val first = settings.lastQuery.isEmpty() && s.lastQuery.isNotEmpty() && pins.isEmpty() && query.isEmpty()
                settings = s
                if (query.isEmpty() && s.lastQuery.isNotEmpty()) query = s.lastQuery
                if (first) search(s.lastQuery, fresh = true)
            }
        }
    }

    /** Every keystroke updates the field and re-searches after a short pause. */
    fun onQueryChange(q: String) {
        query = q
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            delay(450)
            val needle = query.trim()
            if (needle.isEmpty()) {
                searchJob?.cancel()
                loading = false
                pins.clear(); seen.clear(); bookmarks.clear()
                lastQuerySent = ""
                error = null
            } else {
                search(needle, fresh = true)
            }
        }
    }

    fun clearQuery() {
        autoJob?.cancel()
        searchJob?.cancel()
        query = ""
        loading = false
        pins.clear(); seen.clear(); bookmarks.clear()
        lastQuerySent = ""
        error = null
    }

    /** Immediate search (keyboard Search action, retry button). */
    fun searchNow() {
        autoJob?.cancel()
        val needle = query.trim()
        if (needle.isEmpty()) { clearQuery(); return }
        search(needle, fresh = true)
    }

    fun search(q: String = query, fresh: Boolean = true) {
        val needle = q.trim()
        if (needle.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            loading = true
            error = null
            if (fresh) { pins.clear(); seen.clear(); bookmarks.clear(); lastQuerySent = needle }
            store.setQuery(needle)
            val r = PinterestClient.search(needle, bookmarks.toList())
            loading = false
            r.onSuccess { (found, bm) ->
                bookmarks.clear(); bookmarks.addAll(bm)
                for (p in found) if (seen.add(p.id)) pins.add(p)
            }.onFailure { e ->
                if (pins.isEmpty()) error = e.message ?: "search failed"
            }
        }
    }

    fun loadMore() { if (!loading && bookmarks.isNotEmpty()) search(lastQuerySent.ifEmpty { query }, fresh = false) }

    fun open(pin: Pin) {
        if (seen.add(pin.id)) pins.add(pin)
        selected = pin
        fullscreen = false
        activeJobId = null; activeStage = ""; activeFile = ""; activeError = ""; activeProgress = 0f
        similar.clear(); similarError = ""; similarLoading = true
        viewModelScope.launch {
            PinterestClient.related(pin.id).onSuccess { (found, _) ->
                if (selected?.id == pin.id) { similar.clear(); similar.addAll(found.filter { it.id != pin.id }) }
            }.onFailure { e -> if (selected?.id == pin.id) similarError = e.message ?: "related failed" }
            if (selected?.id == pin.id) similarLoading = false
        }
    }
    fun closeDetail() { selected = null; fullscreen = false; similar.clear(); similarError = "" }
    fun toggleFullscreen() { if (selected != null) fullscreen = !fullscreen }

    fun openSimilarAsMain(pin: Pin) = open(pin)

    fun setAccent(key: String) { viewModelScope.launch { store.setAccent(key) } }
    fun setTarget(key: String) { viewModelScope.launch { store.setTarget(key) } }
    fun setHd(v: Boolean) { viewModelScope.launch { store.setHd(v) } }

    fun download(pin: Pin) {
        viewModelScope.launch {
            val box = settings.target.boxFor(pin.w.coerceAtLeast(1), pin.h.coerceAtLeast(1))
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(
                    DownloadWorker.K_URL to pin.orig,
                    DownloadWorker.K_TITLE to pin.title,
                    DownloadWorker.K_PIN to pin.id,
                    DownloadWorker.K_BOX_W to box.first,
                    DownloadWorker.K_BOX_H to box.second,
                )).build()
            activeJobId = req.id; activeStage = "downloading"; activeFile = ""; activeError = ""; activeProgress = 0f
            wm.enqueue(req)
            wm.getWorkInfoByIdLiveData(req.id).observeForever(object : Observer<WorkInfo> {
                override fun onChanged(v: WorkInfo) {
                    if (req.id != activeJobId) return
                    activeProgress = v.progress.getFloat(DownloadWorker.K_PCT, -1f).let { if (it < 0f) activeProgress else it }
                    val stage = v.progress.getString(DownloadWorker.K_STAGE)
                        ?: v.outputData.getString(DownloadWorker.K_STAGE)
                        ?: v.state.name.lowercase()
                    activeStage = stage
                    if (v.state.isFinished) {
                        val file = v.outputData.getString(DownloadWorker.K_FILE).orEmpty()
                        val err = v.outputData.getString(DownloadWorker.K_ERR).orEmpty()
                        activeFile = file; activeError = err
                        activeProgress = if (v.state == WorkInfo.State.SUCCEEDED) 1f else activeProgress
                        activeStage = if (v.state == WorkInfo.State.SUCCEEDED) "done" else "error"
                        wm.getWorkInfoByIdLiveData(req.id).removeObserver(this)
                    }
                }
            })
        }
    }

    fun dismissActive() { activeJobId = null; activeStage = ""; activeFile = ""; activeError = ""; activeProgress = 0f }

    /** Effective accent: auto wallpaper color wins when enabled, else the chosen one. */
    fun effectiveAccent(): Accent = settings.accent
}
