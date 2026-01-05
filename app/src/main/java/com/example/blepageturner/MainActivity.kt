package com.example.blepageturner

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Switch
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLog.init(this)

        status = TextView(this).apply {
            textSize = 14f
            text = buildStatusText()
        }

        val btnPerm = Button(this).apply {
            text = "申请/检查权限"
            setOnClickListener {
                requestNeededPermissions()
                status.text = buildStatusText()
            }
        }

        val swLog = Switch(this).apply {
            text = "显示日志(含协议)"
            isChecked = ProtocolLogStore.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                ProtocolLogStore.setEnabled(this@MainActivity, checked)
            }
        }

        val swScanAll = Switch(this).apply {
            text = "调试：记录所有 BLE 广播(很刷屏)"
            isChecked = ProtocolLogStore.isScanAll(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                ProtocolLogStore.setScanAll(this@MainActivity, checked)
                sendBroadcast(Intent(PageTurnerService.ACTION_RESTART_SCAN).setPackage(packageName))
            }
        }

        val btnLogs = Button(this).apply {
            text = "查看协议日志"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LogActivity::class.java))
            }
        }

        val btnA11y = Button(this).apply {
            text = "打开无障碍设置"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnStart = Button(this).apply {
            text = "启动后台翻页服务"
            setOnClickListener {
                startPageTurnerService()
                status.text = buildStatusText()
            }
        }

        val btnStop = Button(this).apply {
            text = "停止后台翻页服务"
            setOnClickListener {
                stopService(Intent(this@MainActivity, PageTurnerService::class.java))
                status.text = buildStatusText()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 48)
            addView(status)
            addView(btnPerm)
            addView(swLog)
            addView(swScanAll)
            addView(btnLogs)
            addView(btnA11y)
            addView(btnStart)
            addView(btnStop)
        }

        setContentView(root)
    }

    private fun buildStatusText(): String {
        val a11y = if (isAccessibilityServiceEnabled()) "已开启" else "未开启"
        return "无障碍服务：$a11y\n" +
            "提示：亮屏时才会扫描；灭屏立即 stopScan 省电。"
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT < 23) return

        val perms = mutableListOf<String>()

        // Android 13+：通知权限（前台服务通知需要显示）
        if (Build.VERSION.SDK_INT >= 33) {
            perms += "android.permission.POST_NOTIFICATIONS"
        }

        // Android 12+：蓝牙运行时权限
        if (Build.VERSION.SDK_INT >= 31) {
            perms += "android.permission.BLUETOOTH_SCAN"
            perms += "android.permission.BLUETOOTH_CONNECT"
            // 本项目 targetSdk=30：在 Android 12+ 上扫描回调可能仍被定位权限/定位开关影响
            perms += "android.permission.ACCESS_FINE_LOCATION"
        } else {
            // Android 6-11：BLE 扫描通常需要定位权限
            perms += "android.permission.ACCESS_FINE_LOCATION"
        }

        requestPermissions(perms.toTypedArray(), 100)
    }

    private fun startPageTurnerService() {
        val i = Intent(this, PageTurnerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // 判断无障碍服务是否已开启：检查 ENABLED_ACCESSIBILITY_SERVICES 列表中是否包含本服务
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val myId = "$packageName/${ClickAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(myId, ignoreCase = true) }
    }
}
