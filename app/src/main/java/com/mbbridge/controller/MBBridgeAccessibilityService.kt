package com.mbbridge.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class MBBridgeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MBBridgeCtrl"
        private const val TAP_DURATION_MS = 50L
        private const val LEFT_RATIO = 0.25f
        private const val RIGHT_RATIO = 0.75f
        private const val CENTER_Y_RATIO = 0.5f
    }

    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TapAction.ACTION_TAP) {
                return
            }
            val side = intent.getStringExtra(TapAction.EXTRA_SIDE)
            when (side) {
                TapAction.SIDE_LEFT -> {
                    Log.i(TAG, "Tap request: left")
                    performTap(isLeft = true)
                }
                TapAction.SIDE_RIGHT -> {
                    Log.i(TAG, "Tap request: right")
                    performTap(isLeft = false)
                }
                else -> Log.w(TAG, "Unknown tap side: $side")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(TapAction.ACTION_TAP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tapReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tapReceiver, filter)
        }
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: we only dispatch gestures on demand.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(tapReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Unregister receiver failed", e)
        }
    }

    private fun performTap(isLeft: Boolean) {
        val currentPackage = rootInActiveWindow?.packageName?.toString()
        if (currentPackage == packageName) {
            Log.i(TAG, "Skip tap: MBBridgeController is foreground")
            return
        }
        val bounds = getScreenBounds()
        val x = (bounds.width() * if (isLeft) LEFT_RATIO else RIGHT_RATIO).toInt()
        val y = (bounds.height() * CENTER_Y_RATIO).toInt()
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        val label = if (isLeft) "left" else "right"
        val dispatched = dispatchGesture(gesture, null, null)
        Log.i(TAG, "Dispatch tap $label at x=$x y=$y result=$dispatched")
        sendTapResult(label, dispatched, x, y)
    }

    private fun sendTapResult(side: String, success: Boolean, x: Int, y: Int) {
        val intent = Intent(TapAction.ACTION_TAP_RESULT).apply {
            setPackage(packageName)
            putExtra(TapAction.EXTRA_SIDE, side)
            putExtra(TapAction.EXTRA_RESULT, success)
            putExtra(TapAction.EXTRA_X, x)
            putExtra(TapAction.EXTRA_Y, y)
        }
        sendBroadcast(intent)
    }

    private fun getScreenBounds(): Rect {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val metrics = resources.displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }
}
