package com.example.scanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val time: Long,
    val session: String,
    val score: Int,
    val level: String,
    val apps: Int,
    val files: Int,
    val scripts: Int,
    val checked: Int
)

object History {
    private const val PREFS = "history"
    private const val KEY = "entries"
    private const val MAX = 50

    fun list(ctx: Context): List<HistoryEntry> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<HistoryEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    HistoryEntry(
                        o.getLong("time"),
                        o.getString("session"),
                        o.getInt("score"),
                        o.getString("level"),
                        o.getInt("apps"),
                        o.getInt("files"),
                        o.getInt("scripts"),
                        o.getInt("checked")
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(ctx: Context, e: HistoryEntry) {
        val all = ArrayList<HistoryEntry>()
        all.add(e)
        all.addAll(list(ctx))
        val arr = JSONArray()
        for (x in all.take(MAX)) {
            val o = JSONObject()
            o.put("time", x.time)
            o.put("session", x.session)
            o.put("score", x.score)
            o.put("level", x.level)
            o.put("apps", x.apps)
            o.put("files", x.files)
            o.put("scripts", x.scripts)
            o.put("checked", x.checked)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
