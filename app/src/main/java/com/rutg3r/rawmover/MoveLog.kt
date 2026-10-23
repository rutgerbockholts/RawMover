package com.rutg3r.rawmover

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MoveEntry(
    val timeMs: Long,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val reason: String,
)

/** Keeps the last [MAX] moved files for the "Recent moves" list. */
object MoveLog {
    private const val PREFS = "rawmover_log"
    private const val KEY = "entries"
    private const val MAX = 30

    @Synchronized
    fun add(context: Context, entry: MoveEntry) {
        val list = listOf(entry) + read(context)
        val arr = JSONArray()
        list.take(MAX).forEach {
            arr.put(
                JSONObject()
                    .put("t", it.timeMs)
                    .put("n", it.name)
                    .put("d", it.durationMs)
                    .put("s", it.sizeBytes)
                    .put("r", it.reason)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun read(context: Context): List<MoveEntry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MoveEntry(o.getLong("t"), o.getString("n"), o.getLong("d"), o.getLong("s"), o.optString("r"))
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) = prefs(context).edit().remove(KEY).apply()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
