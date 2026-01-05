package com.example.blepageturner

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.ParcelUuid
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class PageTurnerService : Service() {

    companion object {
        private const val TAG = "PageTurnerService"

        // 重要：这是随机生成的 128-bit Service UUID。
        // 手环端（Zepp OS）广播里必须使用同一个 UUID 才能被过滤命中，降低被其他设备干扰（但不是加密）。
        val SERVICE_UUID: UUID = UUID.fromString("c76393eb-1994-4b4d-b1e2-1d7bde0571fa")

        const val ACTION_PAGE_COMMAND = "com.example.blepageturner.ACTION_PAGE_COMMAND"
        const val ACTION_RESTART_SCAN = "com.example.blepageturner.ACTION_RESTART_SCAN"
        const val EXTRA_CMD = "cmd"

        const val CMD_PREV = 1
        const val CMD_NEXT = 2

        // 防抖：400ms 内忽略重复信号，避免一次按键广播多包触发多次点击
        private const val DEBOUNCE_MS = 400L

        private const val NOTIF_CHANNEL_ID = "page_turner"
        private const val NOTIF_ID = 1001
    }

    private val lastTriggerAt = AtomicLong(0L)

    private var scanner: BluetoothLeScanner? = null
    private var scanning = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startScanIfNeeded()
                Intent.ACTION_SCREEN_OFF -> stopScanIfNeeded()
            }
        }
    }

    private val restartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_RESTART_SCAN) return
            stopScanIfNeeded()
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isInteractive) startScanIfNeeded()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val debugAddrOk = matchesDebugAddressFilter(result)
            if (ProtocolLogStore.scanAll && debugAddrOk) {
                logAnyAdvertisement(result)
            }

            val parsed = parsePayload(result) ?: return

            val now = SystemClock.elapsedRealtime()
            val last = lastTriggerAt.get()

            val cmd = parsed.cmd
            val debounced = (cmd != null) && (now - last < DEBOUNCE_MS)

            if (cmd != null && !debounced) {
                lastTriggerAt.set(now)
                dispatchCommand(cmd)
            }

            if (!ProtocolLogStore.scanAll || debugAddrOk) {
                maybeLog(result.rssi, parsed.source, parsed.payload, cmd, debounced)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r)
        }

        override fun onScanFailed(errorCode: Int) {
            AppLog.w(TAG, "scan failed: $errorCode")
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        createNotificationChannel()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )

        registerReceiver(restartReceiver, IntentFilter(ACTION_RESTART_SCAN))
    }

    override fun onDestroy() {
        stopScanIfNeeded()
        unregisterReceiver(screenReceiver)
        unregisterReceiver(restartReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        // 根据当前屏幕状态决定是否扫描：亮屏扫描、灭屏立即 stopScan 省电
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isInteractive) startScanIfNeeded() else stopScanIfNeeded()

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val piFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            piFlags
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("BLE 翻页器运行中")
            .setContentText("亮屏扫描；灭屏停止扫描以省电")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIF_CHANNEL_ID,
                "BLE Page Turner",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun startScanIfNeeded() {
        if (scanning) return

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            AppLog.w(TAG, "Bluetooth disabled")
            return
        }

        if (!hasScanPermission()) {
            AppLog.w(TAG, "Missing permissions for scan (BLUETOOTH_SCAN and/or ACCESS_FINE_LOCATION)")
            return
        }

        val s = adapter.bluetoothLeScanner ?: return
        scanner = s

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // 不使用 ScanFilter.setServiceUuid：部分设备/系统对 128-bit UUID 的硬件过滤不稳定。
            // 改为“全部扫描 + 回调里用 SERVICE_UUID 做软件过滤”，保证协议包不会被系统过滤掉。
            s.startScan(null, settings, scanCallback)
            scanning = true
            AppLog.i(TAG, "scan started")
        } catch (t: Throwable) {
            scanning = false
            AppLog.w(TAG, "startScan failed", t)
        }
    }

    private fun logAnyAdvertisement(result: ScanResult) {
        val addr = result.device?.address ?: "-"
        val rssi = result.rssi
        val raw = result.scanRecord?.bytes

        val rawHex = if (raw == null) "-" else toHex(raw)
        AppLog.i(TAG, "adv addr=$addr rssi=$rssi raw=$rawHex")
    }

    private fun matchesDebugAddressFilter(result: ScanResult): Boolean {
        val filter = ProtocolLogStore.scanAddressFilter
        if (filter.isBlank()) return true
        val addr = result.device?.address ?: return false
        return addr.equals(filter, ignoreCase = true)
    }

    private fun stopScanIfNeeded() {
        if (!scanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Throwable) {
        } finally {
            scanning = false
            // 释放引用：灭屏时尽可能进入休眠态，降低后台消耗
            scanner = null
            AppLog.i(TAG, "scan stopped")
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            // 用字符串写权限名：避免低 compileSdk 下引用不到常量
            checkSelfPermission("android.permission.BLUETOOTH_SCAN") == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

    private data class ParsedPayload(
        val source: String,
        val payload: ByteArray,
        val cmd: Int?
    )

    private fun parsePayload(result: ScanResult): ParsedPayload? {
        val record = result.scanRecord ?: return null

        // 优先解析 Service Data：key 为 SERVICE_UUID
        val serviceData = record.getServiceData(ParcelUuid(SERVICE_UUID))
        val hasOurService = record.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
        val payload: ByteArray? = when {
            serviceData != null && serviceData.isNotEmpty() -> serviceData
            hasOurService -> {
                // 兼容：如果手环端把数据放在 Manufacturer Data 里，这里取第一个 entry
                val msd = record.manufacturerSpecificData
                if (msd != null && msd.size() > 0) msd.valueAt(0) else null
            }
            else -> null
        }

        val p = payload ?: return null

        val source = if (serviceData != null && serviceData.isNotEmpty()) {
            "serviceData"
        } else {
            "manufacturerData"
        }

        val cmd = if (p.isNotEmpty()) {
            when (p[0].toInt() and 0xFF) {
                0x01 -> CMD_PREV
                0x02 -> CMD_NEXT
                else -> null
            }
        } else {
            null
        }

        return ParsedPayload(source, p, cmd)
    }

    private fun maybeLog(rssi: Int, source: String, payload: ByteArray, cmd: Int?, debounced: Boolean) {
        val cmdLabel = when (cmd) {
            CMD_PREV -> "PREV(0x01)"
            CMD_NEXT -> "NEXT(0x02)"
            else -> "UNKNOWN"
        }

        AppLog.i(
            TAG,
            "protocol rssi=$rssi src=$source len=${payload.size} data=${toHex(payload)} cmd=$cmdLabel debounced=$debounced"
        )
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            val hi = v ushr 4
            val lo = v and 0x0F
            sb.append("0123456789ABCDEF"[hi])
            sb.append("0123456789ABCDEF"[lo])
        }
        return sb.toString()
    }

    private fun dispatchCommand(cmd: Int) {
        // 用 package 限定广播只在本 App 内部流转，降低外部干扰
        val i = Intent(ACTION_PAGE_COMMAND)
            .setPackage(packageName)
            .putExtra(EXTRA_CMD, cmd)
        sendBroadcast(i)
        AppLog.i(TAG, "command dispatched: $cmd")
    }
}
