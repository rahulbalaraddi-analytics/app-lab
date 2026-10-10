package com.applab.ideadrop.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** Ask to delete audio older than this. 0 = never ask. */
    val audioRetentionDays: Int = 30,
    /** Don't ask about cleanup again before this time. */
    val cleanupSnoozeUntil: Long = 0,
    /** Bedtime auto-listen window in minutes from midnight (used from M2). */
    val bedtimeStartMin: Int = 22 * 60,
    val bedtimeEndMin: Int = 1 * 60,
    /** Put a short instruction for Claude at the top of exports. */
    val exportIncludePrompt: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load() = AppSettings(
        audioRetentionDays = prefs.getInt("audioRetentionDays", 30),
        cleanupSnoozeUntil = prefs.getLong("cleanupSnoozeUntil", 0),
        bedtimeStartMin = prefs.getInt("bedtimeStartMin", 22 * 60),
        bedtimeEndMin = prefs.getInt("bedtimeEndMin", 60),
        exportIncludePrompt = prefs.getBoolean("exportIncludePrompt", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putInt("audioRetentionDays", s.audioRetentionDays)
            .putLong("cleanupSnoozeUntil", s.cleanupSnoozeUntil)
            .putInt("bedtimeStartMin", s.bedtimeStartMin)
            .putInt("bedtimeEndMin", s.bedtimeEndMin)
            .putBoolean("exportIncludePrompt", s.exportIncludePrompt)
            .apply()
        _state.value = s
    }
}
