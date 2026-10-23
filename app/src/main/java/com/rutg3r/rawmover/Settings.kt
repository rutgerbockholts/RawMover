package com.rutg3r.rawmover

import android.content.Context
import android.content.SharedPreferences

/**
 * All user settings, stored in SharedPreferences.
 * Every read goes straight to the prefs, so changes take effect on the next run without a restart.
 */
class Settings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("rawmover", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Videos longer than this are moved. */
    var minDurationMs: Long
        get() = prefs.getLong(KEY_MIN_DURATION, DEFAULT_MIN_DURATION_MS)
        set(v) = prefs.edit().putLong(KEY_MIN_DURATION, v.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)).apply()

    /** Optional second rule: also move videos larger than [minSizeMb]. */
    var sizeRuleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIZE_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_SIZE_ENABLED, v).apply()

    var minSizeMb: Int
        get() = prefs.getInt(KEY_MIN_SIZE_MB, DEFAULT_MIN_SIZE_MB)
        set(v) = prefs.edit().putInt(KEY_MIN_SIZE_MB, v.coerceIn(1, 100_000)).apply()

    /** Relative path under the primary storage, e.g. "DCIM/Camera/". */
    var sourceRelPath: String
        get() = prefs.getString(KEY_SOURCE, DEFAULT_SOURCE)!!
        set(v) = prefs.edit().putString(KEY_SOURCE, normalizeRelPath(v)).apply()

    var targetRelPath: String
        get() = prefs.getString(KEY_TARGET, DEFAULT_TARGET)!!
        set(v) = prefs.edit().putString(KEY_TARGET, normalizeRelPath(v)).apply()

    /** Only files whose name matches this regex are considered (camera originals). */
    var namePattern: String
        get() = prefs.getString(KEY_PATTERN, DEFAULT_PATTERN)!!
        set(v) = prefs.edit().putString(KEY_PATTERN, v).apply()

    var notify: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFY, v).apply()

    /**
     * Only videos added at or after this time (epoch seconds) are considered.
     * Set once at first launch so existing (already synced) videos are left alone.
     */
    var processSinceSec: Long
        get() = prefs.getLong(KEY_SINCE, 0L)
        set(v) = prefs.edit().putLong(KEY_SINCE, v).apply()

    fun initIfNeeded() {
        if (!prefs.contains(KEY_SINCE)) {
            processSinceSec = System.currentTimeMillis() / 1000
        }
    }

    /** Returns the compiled pattern, or null when the regex is invalid. */
    fun compiledPattern(): Regex? = runCatching { Regex(namePattern) }.getOrNull()

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_MIN_DURATION = "minDurationMs"
        const val KEY_SIZE_ENABLED = "sizeRuleEnabled"
        const val KEY_MIN_SIZE_MB = "minSizeMb"
        const val KEY_SOURCE = "sourceRelPath"
        const val KEY_TARGET = "targetRelPath"
        const val KEY_PATTERN = "namePattern"
        const val KEY_NOTIFY = "notify"
        const val KEY_SINCE = "processSinceSec"

        const val DEFAULT_MIN_DURATION_MS = 120_000L
        const val MIN_DURATION_MS = 5_000L
        const val MAX_DURATION_MS = 60 * 60_000L
        const val DEFAULT_MIN_SIZE_MB = 500

        const val DEFAULT_SOURCE = "DCIM/Camera/"
        const val DEFAULT_TARGET = "Movies/Raw/"

        /**
         * Camera originals only:
         *  - Samsung:         20261023_141522.mp4
         *  - Pixel:           PXL_20261023_141522123.mp4
         *  - many others:     VID_20261023_141522.mp4
         * Edited exports (e.g. "..._exported_...", "CapCut_...") don't match and stay put.
         */
        const val DEFAULT_PATTERN = """^(\d{8}_\d{6}|PXL_\d{8}_\d{9}|VID_\d{8}_\d{6,9})\.(mp4|MP4)$"""

        fun normalizeRelPath(path: String): String {
            val trimmed = path.trim().trim('/')
            return if (trimmed.isEmpty()) "" else "$trimmed/"
        }
    }
}
