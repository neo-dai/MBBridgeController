package com.example.blepageturner

import android.content.Context
import java.util.ArrayDeque

object ProtocolLogStore {
    private const val PREFS_NAME = "ble_page_turner"
    const val PREF_LOG_ENABLED = "log_enabled"
    private const val PREF_SCAN_ALL = "scan_all"

    const val ACTION_PROTOCOL_LOG = "com.example.blepageturner.ACTION_PROTOCOL_LOG"
    const val EXTRA_LINE = "line"

    private const val MAX_LINES = 200

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var scanAll: Boolean = false
        private set

    private val lock = Any()
    private val lines = ArrayDeque<String>(MAX_LINES)

    fun isEnabled(context: Context): Boolean {
        val v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_LOG_ENABLED, false)
        enabled = v
        return v
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        this.enabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_LOG_ENABLED, enabled)
            .apply()
    }

    fun isScanAll(context: Context): Boolean {
        val v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_SCAN_ALL, false)
        scanAll = v
        return v
    }

    fun setScanAll(context: Context, enabled: Boolean) {
        scanAll = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SCAN_ALL, enabled)
            .apply()
    }

    fun add(line: String) {
        synchronized(lock) {
            while (lines.size >= MAX_LINES) {
                lines.removeFirst()
            }
            lines.addLast(line)
        }
    }

    fun snapshot(): List<String> {
        return synchronized(lock) {
            lines.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
        }
    }
}
