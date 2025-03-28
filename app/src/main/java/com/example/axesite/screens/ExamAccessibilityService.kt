package com.example.axesite.screens  // Must match your app's package

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ExamAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        val config = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        this.serviceInfo = config
        Log.d("ExamService", "Service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_HOME -> true  // Block Home button
            KeyEvent.KEYCODE_APP_SWITCH -> true  // Block Recent Apps
            else -> super.onKeyEvent(event)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Handle window changes if needed
    }

    override fun onInterrupt() {
        Log.w("ExamService", "Service interrupted")
    }
}

