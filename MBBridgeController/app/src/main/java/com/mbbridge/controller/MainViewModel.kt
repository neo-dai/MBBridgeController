package com.mbbridge.controller

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI 状态
 */
data class UiState(
    val isServerRunning: Boolean = false,
    val lastCommand: Command? = null,
    val stats: CommandStats = CommandStats(),
    val logs: List<String> = emptyList(),
    val detailedLogs: List<String> = emptyList(),  // 详细日志（包含协议交互）
    val token: String = "",
    val showLogWindow: Boolean = false  // 是否显示日志窗口
)

/**
 * Main ViewModel
 */
class MainViewModel(application: Application) : AndroidViewModel(application),
    MBBridgeHttpServer.CommandListener, MBBridgeHttpServer.LogListener {

    companion object {
        private const val TAG = "MBBridgeCtrl"
        private const val MAX_LOGS = 50
        private const val MAX_DETAILED_LOGS = 200  // 详细日志保留更多
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var service: MBBridgeService? = null
    private val context: Context = application.applicationContext

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected")
            val localBinder = binder as? MBBridgeService.LocalBinder
            service = localBinder?.getService()
            service?.setCommandListener(this@MainViewModel)
            service?.setLogListener(this@MainViewModel)  // 设置日志监听器
            updateServerStatus()
            addDetailedLog(LogLevel.INFO, "✓ Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            service = null
            updateServerStatus()
            addDetailedLog(LogLevel.WARN, "✗ Service disconnected")
        }
    }

    init {
        loadToken()
        bindToService()
    }

    /**
     * 绑定到服务
     */
    private fun bindToService() {
        val intent = Intent(context, MBBridgeService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 解绑服务
     */
    private fun unbindFromService() {
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unbind service", e)
        }
    }

    /**
     * 更新服务器状态
     */
    private fun updateServerStatus() {
        val isRunning = service?.isServerRunning() ?: false
        _uiState.value = _uiState.value.copy(isServerRunning = isRunning)
    }

    /**
     * 启动服务器
     */
    fun startServer() {
        Log.i(TAG, "Starting server...")
        addDetailedLog(LogLevel.INFO, "▶ Starting server...")
        MBBridgeService.startService(context)
        // 延迟检查状态
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            updateServerStatus()
        }
    }

    /**
     * 停止服务器
     */
    fun stopServer() {
        Log.i(TAG, "Stopping server...")
        addDetailedLog(LogLevel.INFO, "■ Stopping server...")
        MBBridgeService.stopService(context)
        updateServerStatus()
    }

    /**
     * 日志回调（来自 HTTP 服务器）
     */
    override fun onLog(level: LogLevel, message: String) {
        addDetailedLog(level, message)
    }

    /**
     * 接收命令回调
     */
    override fun onCommandReceived(command: Command) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val newStats = currentState.stats.apply {
                increment(command.getCommandType())
            }

            val logEntry = buildLogEntry(command)
            val newLogs = (listOf(logEntry) + currentState.logs).take(MAX_LOGS)

            _uiState.value = currentState.copy(
                lastCommand = command,
                stats = newStats,
                logs = newLogs
            )

            Log.i(TAG, "Command received: ${command.getCommandType()}, v=${command.v}, source=${command.source}")
        }
    }

    /**
     * 构建日志条目
     */
    private fun buildLogEntry(command: Command): String {
        val timestamp = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", command.ts)
        return "[$timestamp] ${command.getCommandType()} (v=${command.v}, source=${command.source})"
    }

    /**
     * 模拟命令（用于测试）
     */
    fun simulateCommand(type: CommandType) {
        val command = Command(
            v = when (type) {
                is CommandType.PREV -> 1
                is CommandType.NEXT -> 2
                is CommandType.UNKNOWN -> type.value
            },
            ts = System.currentTimeMillis(),
            source = "simulate"
        )
        onCommandReceived(command)
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    /**
     * 保存 Token
     */
    fun saveToken(token: String) {
        viewModelScope.launch {
            context.getSharedPreferences("mbbridge_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("auth_token", token)
                .apply()
            _uiState.value = _uiState.value.copy(token = token)
            Log.i(TAG, "Token saved")
        }
    }

    /**
     * 加载 Token
     */
    private fun loadToken() {
        val token = context.getSharedPreferences("mbbridge_prefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
        _uiState.value = _uiState.value.copy(token = token)
    }

    /**
     * 打开无障碍设置
     */
    fun openAccessibilitySettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 切换日志窗口显示
     */
    fun toggleLogWindow() {
        _uiState.value = _uiState.value.copy(
            showLogWindow = !_uiState.value.showLogWindow
        )
    }

    /**
     * 添加详细日志（协议交互、关键步骤）
     */
    fun addDetailedLog(level: LogLevel, message: String) {
        viewModelScope.launch {
            val timestamp = android.text.format.DateFormat.format(
                "HH:mm:ss.SSS",
                System.currentTimeMillis()
            )
            val logEntry = "[$timestamp] [${level.name}] $message"

            val currentState = _uiState.value
            val newLogs = (listOf(logEntry) + currentState.detailedLogs).take(MAX_DETAILED_LOGS)

            _uiState.value = currentState.copy(detailedLogs = newLogs)

            // 同时输出到 Logcat
            when (level) {
                LogLevel.VERBOSE -> Log.v(TAG, message)
                LogLevel.DEBUG -> Log.d(TAG, message)
                LogLevel.INFO -> Log.i(TAG, message)
                LogLevel.WARN -> Log.w(TAG, message)
                LogLevel.ERROR -> Log.e(TAG, message)
            }
        }
    }

    /**
     * 清空详细日志
     */
    fun clearDetailedLogs() {
        _uiState.value = _uiState.value.copy(detailedLogs = emptyList())
    }

    /**
     * 导出日志为文本
     */
    fun exportLogs(): String {
        return _uiState.value.detailedLogs.joinToString("\n")
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromService()
        service?.setCommandListener(null)
        service?.setLogListener(null)  // 清除日志监听器
    }
}
