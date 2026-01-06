package com.mbbridge.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 前台服务：保持 HTTP 服务器运行
 */
class MBBridgeService : Service() {

    companion object {
        private const val TAG = "MBBridgeCtrl"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "MBBridgeServiceChannel"

        const val ACTION_START = "com.mbbridge.controller.START"
        const val ACTION_STOP = "com.mbbridge.controller.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, MBBridgeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MBBridgeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private var httpServer: MBBridgeHttpServer? = null
    private var isRunning = false

    inner class LocalBinder : Binder() {
        fun getService(): MBBridgeService = this@MBBridgeService
    }

    /**
     * 获取服务器状态
     */
    fun isServerRunning(): Boolean = isRunning

    /**
     * 设置命令监听器
     */
    fun setCommandListener(listener: MBBridgeHttpServer.CommandListener?) {
        httpServer?.setCommandListener(listener)
    }

    /**
     * 设置日志监听器
     */
    fun setLogListener(listener: MBBridgeHttpServer.LogListener?) {
        httpServer?.setLogListener(listener)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MBBridgeService created")
        createNotificationChannel()
        httpServer = MBBridgeHttpServer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Start command received")
                startHttpServer()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stop command received")
                stopHttpServer()
                stopSelf()
            }
            else -> {
                Log.d(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MBBridgeService destroyed")
        stopHttpServer()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台通知
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 启动 HTTP 服务器
     */
    private fun startHttpServer() {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return
        }

        val server = httpServer ?: run {
            Log.e(TAG, "HTTP server not initialized")
            return
        }

        if (server.startServer()) {
            isRunning = true
            startForeground(NOTIFICATION_ID, createNotification())
            Log.i(TAG, "HTTP server started successfully")
        } else {
            Log.e(TAG, "Failed to start HTTP server")
        }
    }

    /**
     * 停止 HTTP 服务器
     */
    private fun stopHttpServer() {
        if (!isRunning) {
            return
        }

        httpServer?.stopServer()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "HTTP server stopped")
    }
}
