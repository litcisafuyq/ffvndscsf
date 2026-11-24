package com.a17.assist.ffvn.inject

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class A17A11yService : AccessibilityService() {

    companion object {
        @Volatile var instance: A17A11yService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
