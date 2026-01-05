package com.example.blepageturner

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickA11yService"
    }

    private val cmdReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val cmd = intent.getIntExtra(PageTurnerService.EXTRA_CMD, -1)
            when (cmd) {
                PageTurnerService.CMD_NEXT -> tapRightEdge()
                PageTurnerService.CMD_PREV -> tapLeftEdge()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(cmdReceiver, IntentFilter(PageTurnerService.ACTION_PAGE_COMMAND))
        Log.i(TAG, "accessibility connected")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(cmdReceiver)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不依赖事件，只负责执行手势
    }

    override fun onInterrupt() {
        // 无需处理
    }

    private fun tapRightEdge() {
        val (x, y) = edgePoint(isRight = true)
        performTap(x, y)
    }

    private fun tapLeftEdge() {
        val (x, y) = edgePoint(isRight = false)
        performTap(x, y)
    }

    private fun edgePoint(isRight: Boolean): Pair<Float, Float> {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()

        // 点击坐标计算（重点）：
        // 下一页：点击屏幕右侧边缘垂直居中 (X=95%, Y=50%)
        // 上一页：点击屏幕左侧边缘垂直居中 (X=5%,  Y=50%)
        val x = if (isRight) w * 0.95f else w * 0.05f
        val y = h * 0.50f
        return x to y
    }

    private fun performTap(x: Float, y: Float) {
        // 使用 dispatchGesture 模拟 Tap（不使用 Swipe，逻辑更简单）
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }

        val clickDurationMs = 50L // 点击持续 50ms，模拟快速轻触
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, clickDurationMs))
            .build()

        val ok = dispatchGesture(gesture, null, null)
        Log.i(TAG, "tap($x,$y) dispatched=$ok")
    }
}
