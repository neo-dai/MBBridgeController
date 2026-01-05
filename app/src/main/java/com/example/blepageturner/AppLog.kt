package com.example.blepageturner

import android.content.Context
import android.content.Intent
import android.util.Log

object AppLog {

    private var appContext: Context? = null

    fun init(context: Context) {
        // 用 applicationContext 避免 Activity/Service 泄漏
        appContext = context.applicationContext
        // 同步一次开关状态（保证 Service 先启动时也能读到 prefs）
        ProtocolLogStore.isEnabled(appContext!!)
        ProtocolLogStore.isScanAll(appContext!!)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        publish('I', tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        publish('W', tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        Log.w(tag, msg, tr)
        publish('W', tag, "$msg | ${tr}")
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        publish('E', tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
        publish('E', tag, "$msg | ${tr}")
    }

    private fun publish(level: Char, tag: String, msg: String) {
        if (!ProtocolLogStore.enabled) return

        val line = "ts=${System.currentTimeMillis()} $level/$tag: $msg"
        ProtocolLogStore.add(line)

        val ctx = appContext ?: return
        val i = Intent(ProtocolLogStore.ACTION_PROTOCOL_LOG)
            .setPackage(ctx.packageName)
            .putExtra(ProtocolLogStore.EXTRA_LINE, line)
        ctx.sendBroadcast(i)
    }
}
