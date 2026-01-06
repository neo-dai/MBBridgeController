package com.mbbridge.controller

import org.json.JSONObject

/**
 * 命令数据模型
 *
 * JSON 格式：
 * {
 *   "v": 1,
 *   "ts": 1730000000000,
 *   "source": "mbbridge"
 * }
 *
 * v 含义：
 * - 1 = PREV（手环 0x01）
 * - 2 = NEXT（手环 0x02）
 */
data class Command(
    val v: Int,           // 命令类型：1=PREV, 2=NEXT
    val ts: Long,         // 时间戳（毫秒）
    val source: String    // 来源标识
) {
    companion object {
        private const val TAG = "MBBridgeCtrl"

        fun fromJson(jsonString: String): Command? {
            return try {
                val json = JSONObject(jsonString)
                Command(
                    v = json.getInt("v"),
                    ts = json.getLong("ts"),
                    source = json.getString("source")
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to parse command JSON", e)
                null
            }
        }
    }

    fun getCommandType(): CommandType {
        return when (v) {
            1 -> CommandType.PREV
            2 -> CommandType.NEXT
            else -> CommandType.UNKNOWN(v)
        }
    }

    fun toJson(): String {
        return JSONObject().apply {
            put("v", v)
            put("ts", ts)
            put("source", source)
        }.toString()
    }
}

sealed class CommandType {
    object PREV : CommandType()
    object NEXT : CommandType()
    data class UNKNOWN(val value: Int) : CommandType()

    override fun toString(): String {
        return when (this) {
            is PREV -> "PREV"
            is NEXT -> "NEXT"
            is UNKNOWN -> "UNKNOWN($value)"
        }
    }
}

/**
 * HTTP 响应模型
 */
data class HttpResponse(
    val ok: Int,
    val err: String? = null,
    val app: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("ok", ok)
            err?.let { put("err", it) }
            app?.let { put("app", it) }
        }.toString()
    }

    companion object {
        fun success(app: String? = null) = HttpResponse(ok = 1, app = app)
        fun error(message: String) = HttpResponse(ok = 0, err = message)
    }
}

/**
 * 统计数据
 */
data class CommandStats(
    var prevCount: Int = 0,
    var nextCount: Int = 0,
    var totalCount: Int = 0
) {
    fun increment(commandType: CommandType) {
        when (commandType) {
            is CommandType.PREV -> prevCount++
            is CommandType.NEXT -> nextCount++
            is CommandType.UNKNOWN -> {} // 未知命令不计数
        }
        totalCount++
    }

    fun reset() {
        prevCount = 0
        nextCount = 0
        totalCount = 0
    }
}

/**
 * 日志级别
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
