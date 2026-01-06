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
        const val EXTRA_STOP_REASON = "stop_reason"

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
            stopService(context, "unknown")
        }

        fun stopService(context: Context, reason: String) {
            val intent = Intent(context, MBBridgeService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_STOP_REASON, reason)
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private var httpServer: MBBridgeHttpServer? = null
    private var serverPort: Int = MBBridgeHttpServer.DEFAULT_PORT
    private var commandListener: MBBridgeHttpServer.CommandListener? = null
    private var logListener: MBBridgeHttpServer.LogListener? = null
    private val portStore by lazy { PortStore(this) }

    inner class LocalBinder : Binder() {
        fun getService(): MBBridgeService = this@MBBridgeService
    }

    /**
     * 获取服务器状态
     */
    fun isServerRunning(): Boolean = httpServer?.isRunning() == true

    /**
     * 设置命令监听器
     */
    fun setCommandListener(listener: MBBridgeHttpServer.CommandListener?) {
        commandListener = listener
        httpServer?.setCommandListener(listener)
    }

    /**
     * 设置日志监听器
     */
    fun setLogListener(listener: MBBridgeHttpServer.LogListener?) {
        logListener = listener
        httpServer?.setLogListener(listener)
    }

    fun setPort(port: Int) {
        if (port == serverPort) {
            return
        }
        val wasRunning = isServerRunning()
        stopHttpServer()
        serverPort = port
        httpServer = MBBridgeHttpServer(this, port).also {
            it.setCommandListener(commandListener)
            it.setLogListener(logListener)
        }
        if (wasRunning) {
            startForeground(NOTIFICATION_ID, createNotification(port))
            startHttpServer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        emitLog(LogLevel.INFO, "Service created")
        createNotificationChannel()
        serverPort = portStore.getPort()
        httpServer = MBBridgeHttpServer(this, serverPort)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                emitLog(LogLevel.INFO, "Start command received")
                val desiredPort = portStore.getPort()
                if (desiredPort != serverPort) {
                    emitLog(LogLevel.INFO, "Port changed $serverPort -> $desiredPort")
                    setPort(desiredPort)
                }
                startForeground(NOTIFICATION_ID, createNotification(serverPort))
                startHttpServer()
            }
            ACTION_STOP -> {
                val reason = intent.getStringExtra(EXTRA_STOP_REASON) ?: "unknown"
                emitLog(LogLevel.WARN, "Stop command received (reason=$reason)")
                stopHttpServer()
                stopSelf()
            }
            else -> {
                emitLog(LogLevel.DEBUG, "Unknown action: ${intent?.action}")
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
        emitLog(LogLevel.WARN, "Service destroyed")
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
    private fun createNotification(port: Int): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, port))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 启动 HTTP 服务器
     */
    private fun startHttpServer() {
        if (httpServer?.isRunning() == true) {
            emitLog(LogLevel.WARN, "Server already running")
            return
        }

        val server = httpServer ?: run {
            emitLog(LogLevel.ERROR, "HTTP server not initialized")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (server.startServer()) {
            emitLog(LogLevel.INFO, "HTTP server started successfully")
        } else {
            emitLog(LogLevel.ERROR, "Failed to start HTTP server")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * 停止 HTTP 服务器
     */
    private fun stopHttpServer() {
        if (httpServer?.isRunning() != true) {
            return
        }

        httpServer?.stopServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        emitLog(LogLevel.WARN, "HTTP server stopped")
    }

    private fun emitLog(level: LogLevel, message: String) {
        when (level) {
            LogLevel.VERBOSE -> Log.v(TAG, message)
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        logListener?.onLog(level, message)
    }
}
