package com.example.moment.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogStore {
    private const val PREF_NAME = "MomentDebugLogs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOG_LINES = 300
    private const val MAX_LOG_CHARS = 24000

    @Synchronized
    fun append(context: Context, message: String) {
        val line = "${timestamp()} $message"
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_LOGS, "").orEmpty()
        val updated = trimLogs(if (existing.isBlank()) line else "$existing\n$line")
        prefs.edit().putString(KEY_LOGS, updated).apply()
    }

    @Synchronized
    fun readAll(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOGS, "")
            .orEmpty()
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOGS)
            .apply()
    }

    private fun trimLogs(raw: String): String {
        var lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size > MAX_LOG_LINES) {
            lines = lines.takeLast(MAX_LOG_LINES)
        }

        var text = lines.joinToString("\n")
        if (text.length > MAX_LOG_CHARS) {
            text = text.takeLast(MAX_LOG_CHARS)
            val firstNewline = text.indexOf('\n')
            if (firstNewline >= 0 && firstNewline + 1 < text.length) {
                text = text.substring(firstNewline + 1)
            }
        }
        return text
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}

