package com.applab.batterypilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applab.batterypilot.analysis.Engine
import com.applab.batterypilot.analysis.Snapshot
import com.applab.batterypilot.data.AppDatabase
import com.applab.batterypilot.data.FixLog
import com.applab.batterypilot.data.Prefs
import com.applab.batterypilot.fixes.Fix
import com.applab.batterypilot.fixes.Fixes
import com.applab.batterypilot.fixes.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val snap: Snapshot? = null,
    val loading: Boolean = true,
    val message: String? = null,
    /** Fixes ticked in the checklist. */
    val selected: Set<String> = emptySet(),
    /** Guided fixes still to do, one settings screen at a time. */
    val guidedQueue: List<Fix> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    private var refreshAgain = false

    /**
     * Fast pass first (no dumpsys) so the screen updates at once, then the full pass with
     * exact per-app numbers. A refresh asked for while one is running is queued, not dropped.
     */
    fun refresh(record: Boolean = true) {
        if (refreshJob?.isActive == true) { refreshAgain = true; return }
        refreshJob = viewModelScope.launch {
            do {
                refreshAgain = false
                _state.update { it.copy(loading = true) }
                if (record) Engine.recordNow(ctx, "open")
                for (withReport in listOf(false, true)) {
                    val snap = runCatching { Engine.snapshot(ctx, withReport) }.getOrElse { e ->
                        _state.update { it.copy(loading = false, message = "Couldn't analyse: ${e.message}") }
                        return@launch
                    }
                    if (!withReport && !snap.access.batteryStats) {
                        publish(snap); break
                    }
                    publish(snap, done = withReport)
                }
            } while (refreshAgain)
        }
    }

    private fun publish(snap: Snapshot, done: Boolean = true) = _state.update { s ->
        // Keep exact per-app rows from the last full pass while the fast pass runs.
        val merged = if (!done && s.snap != null && snap.apps.firstOrNull()?.source != "exact" &&
            s.snap.apps.firstOrNull()?.source == "exact"
        ) snap.copy(apps = s.snap.apps, parts = s.snap.parts, fixes = s.snap.fixes) else snap
        val stillOpen = merged.fixes.filter { it.done != true }.map { it.id }.toSet()
        s.copy(snap = merged, loading = !done, selected = s.selected intersect stillOpen)
    }

    /** Show a settings change immediately, before the re-analysis finishes. */
    private fun patchPrefs(f: (com.applab.batterypilot.analysis.PrefsView) -> com.applab.batterypilot.analysis.PrefsView) =
        _state.update { s -> s.copy(snap = s.snap?.let { it.copy(prefs = f(it.prefs)) }) }

    fun toggle(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun selectRecommended() = _state.update { s ->
        val fixes = s.snap?.fixes.orEmpty().filter { it.done != true && it.savedPctPerDay >= 1.0 && !it.id.startsWith("power_saving") }
        s.copy(selected = fixes.map { it.id }.toSet())
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Apply every ticked fix: automatic ones now, then guided ones one by one. */
    fun applySelected() {
        val s = _state.value
        val fixes = s.snap?.fixes.orEmpty().filter { it.id in s.selected }
        if (fixes.isEmpty()) return
        viewModelScope.launch {
            val done = ArrayList<String>()
            val guided = ArrayList<Fix>()
            for (f in fixes) {
                if (f.mode == Mode.AUTO) {
                    val r = withContext(Dispatchers.IO) { Fixes.apply(ctx, f) }
                    if (r.openedSettings) {
                        log(f, "opened")
                    } else if (r.ok) {
                        done += f.title; log(f, "applied")
                    }
                } else guided += f
            }
            val msg = buildString {
                if (done.isNotEmpty()) append("Done: ${done.size} change${if (done.size > 1) "s" else ""}. ")
                if (guided.isNotEmpty()) append("${guided.size} need a tap in Settings — opening the first one.")
            }.ifBlank { null }
            _state.update { it.copy(selected = emptySet(), guidedQueue = guided, message = msg) }
            if (guided.isNotEmpty()) openNextGuided() else refresh(record = false)
        }
    }

    fun openNextGuided() {
        val q = _state.value.guidedQueue
        val f = q.firstOrNull() ?: return
        viewModelScope.launch {
            val r = Fixes.openGuided(ctx, f)
            log(f, "opened")
            _state.update { it.copy(guidedQueue = q.drop(1), message = "👉 ${r.message}") }
        }
    }

    fun skipGuided() = _state.update { it.copy(guidedQueue = emptyList()) }

    fun applyOne(f: Fix) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Fixes.apply(ctx, f) }
            log(f, if (r.openedSettings) "opened" else "applied")
            _state.update { it.copy(message = r.message) }
            if (!r.openedSettings) refresh(record = false)
        }
    }

    fun undo(f: Fix) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Fixes.undo(ctx, f) }
            if (r.ok) log(f, "undone")
            _state.update { it.copy(message = r.message) }
            refresh(record = false)
        }
    }

    private suspend fun log(f: Fix, action: String) = withContext(Dispatchers.IO) {
        AppDatabase.get(ctx).fixes().insert(
            FixLog(time = System.currentTimeMillis(), fixId = f.id, title = f.title, action = action),
        )
    }

    // ---------- settings ----------

    private val prefs get() = Prefs(ctx)
    fun setTargetHour(h: Int) { prefs.targetHour = h; patchPrefs { it.copy(targetHour = prefs.targetHour) }; refresh(false) }
    fun setBuffer(p: Int) { prefs.bufferPct = p; patchPrefs { it.copy(bufferPct = prefs.bufferPct) }; refresh(false) }
    fun setAlerts(on: Boolean) { prefs.alertsOn = on; patchPrefs { it.copy(alertsOn = on) }; refresh(false) }
    fun setSmartSaver(on: Boolean) {
        prefs.smartSaver = on
        patchPrefs { it.copy(smartSaver = on) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.get(ctx).fixes().insert(
                    FixLog(time = System.currentTimeMillis(), fixId = "smart_saver", title = "Smart saver", action = if (on) "applied" else "undone"),
                )
            }
            refresh(false)
        }
    }
    fun setSaverBelow(p: Int) { prefs.saverBelowPct = p; patchPrefs { it.copy(saverBelowPct = prefs.saverBelowPct) }; refresh(false) }
}
