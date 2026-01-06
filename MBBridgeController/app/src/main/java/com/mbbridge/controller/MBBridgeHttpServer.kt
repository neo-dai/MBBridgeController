package com.mbbridge.controller

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.IOException

/**
 * HTTP 服务器实现
 * 监听 127.0.0.1:27123
 *
 * 路由：
 * - POST /cmd：接收命令
 * - GET /health：健康检查
 */
class MBBridgeHttpServer(
    private val context: Context,
    port: Int = PORT
) : NanoHTTPD(HOST, port) {

    companion object {
        private const val TAG = "MBBridgeCtrl"
        const val HOST = "127.0.0.1"
        const val PORT = 27123

        // SharedPreferences 键名
        private const val PREFS_NAME = "mbbridge_prefs"
        private const val KEY_TOKEN = "auth_token"
    }

    private var commandListener: CommandListener? = null
    private var logListener: LogListener? = null

    interface CommandListener {
        fun onCommandReceived(command: Command)
    }

    interface LogListener {
        fun onLog(level: LogLevel, message: String)
    }

    fun setCommandListener(listener: CommandListener?) {
        this.commandListener = listener
    }

    fun setLogListener(listener: LogListener?) {
        this.logListener = listener
    }

    private fun log(level: LogLevel, message: String) {
        // 输出到 Logcat
        when (level) {
            LogLevel.VERBOSE -> Log.v(TAG, message)
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        // 通知监听器
        logListener?.onLog(level, message)
    }

    /**
     * 获取配置的 Token（可能为空）
     */
    private fun getConfiguredToken(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
    }

    /**
     * 保存 Token
     */
    fun saveToken(token: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
        Log.i(TAG, "Token ${if (token.isNullOrEmpty()) "cleared" else "updated"}")
    }

    /**
     * 验证 Token
     */
    private fun verifyToken(session: IHTTPSession): Boolean {
        val expectedToken = getConfiguredToken() ?: return true // 未配置 token 则跳过验证
        val providedToken = session.headers.get("x-mbbridge-token") ?: session.headers.get("X-MBBridge-Token")
        return providedToken == expectedToken
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri
            val method = session.method

            log(LogLevel.DEBUG, "➤ Request: $method $uri from ${session.remoteIpAddress}")
            log(LogLevel.DEBUG, "  Headers: ${session.headers}")

            when {
                // POST /cmd - 接收命令
                method == Method.POST && uri == "/cmd" -> handleCommand(session)

                // GET /health - 健康检查
                method == Method.GET && uri == "/health" -> handleHealth()

                // 404 Not Found
                else -> handleNotFound()
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "✗ Error handling request: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                HttpResponse.error("Internal server error: ${e.message}").toJson()
            )
        }
    }

    /**
     * 处理命令请求
     * POST /cmd
     * Body: { "v": 1, "ts": 1730000000000, "source": "mbbridge" }
     */
    private fun handleCommand(session: IHTTPSession): Response {
        log(LogLevel.INFO, "↕ POST /cmd - Command request received")

        // 验证 Token
        val tokenProvided = session.headers["x-mbbridge-token"] ?: session.headers["X-MBBridge-Token"]
        if (!verifyToken(session)) {
            log(LogLevel.WARN, "✗ Token validation failed (provided: ${if (tokenProvided.isNullOrEmpty()) "none" else "***"})")
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                HttpResponse.error("Unauthorized: Invalid or missing token").toJson()
            )
        }

        log(LogLevel.DEBUG, "✓ Token validated ${if (tokenProvided.isNullOrEmpty()) "(skipped - no token configured)" else "successfully"}")

        // 读取请求体
        val body = parseRequestBody(session)
        if (body.isNullOrBlank()) {
            log(LogLevel.WARN, "✗ Empty request body")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                HttpResponse.error("Bad request: Empty body").toJson()
            )
        }

        log(LogLevel.DEBUG, "  Request body: $body")

        // 解析 JSON
        val command = Command.fromJson(body)
        if (command == null) {
            log(LogLevel.ERROR, "✗ Invalid JSON format: $body")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                HttpResponse.error("Bad request: Invalid JSON format").toJson()
            )
        }

        log(LogLevel.INFO, "✓ Command parsed: type=${command.getCommandType()}, v=${command.v}, ts=${command.ts}, source=${command.source}")

        // 通知监听器
        commandListener?.onCommandReceived(command)

        // 返回成功响应
        val responseJson = HttpResponse.success().toJson()
        log(LogLevel.DEBUG, "← Response: $responseJson")
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            responseJson
        )
    }

    /**
     * 解析请求体
     */
    private fun parseRequestBody(session: IHTTPSession): String? {
        return try {
            // 读取 Content-Length
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength <= 0) return null

            // 读取 body
            val buffer = ByteArray(contentLength)
            session.inputStream.use { it.read(buffer) }
            String(buffer, Charsets.UTF_8)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read request body", e)
            null
        }
    }

    /**
     * 处理健康检查请求
     * GET /health
     */
    private fun handleHealth(): Response {
        log(LogLevel.INFO, "↕ GET /health - Health check")
        val responseJson = HttpResponse.success(app = "MBBridgeCtrl").toJson()
        log(LogLevel.DEBUG, "← Response: $responseJson")
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            responseJson
        )
    }

    /**
     * 处理 404
     */
    private fun handleNotFound(): Response {
        log(LogLevel.WARN, "✗ 404 Not Found")
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "application/json",
            HttpResponse.error("Not found").toJson()
        )
    }

    /**
     * 启动服务器
     */
    fun startServer(): Boolean {
        return try {
            log(LogLevel.INFO, "▶ Starting HTTP server on $HOST:$PORT...")
            start()
            log(LogLevel.INFO, "✓ HTTP server started successfully")
            true
        } catch (e: IOException) {
            log(LogLevel.ERROR, "✗ Failed to start HTTP server: ${e.message}")
            false
        }
    }

    /**
     * 停止服务器
     */
    fun stopServer() {
        try {
            log(LogLevel.INFO, "■ Stopping HTTP server...")
            stop()
            log(LogLevel.INFO, "✓ HTTP server stopped")
        } catch (e: Exception) {
            log(LogLevel.ERROR, "✗ Error stopping HTTP server: ${e.message}")
        }
    }
}
