package com.blocker.dns.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.blocker.dns.engine.AutomationStateMachine

class DnsAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "DnsAutomationService"
        var instance: DnsAutomationService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    lateinit var stateMachine: AutomationStateMachine
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        stateMachine = AutomationStateMachine(this)
        Log.d(TAG, "DnsAutomationService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val root = rootInActiveWindow ?: return
        if (::stateMachine.isInitialized) {
            stateMachine.onAccessibilityNode(root)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "DnsAutomationService interrupted.")
        if (::stateMachine.isInitialized) {
            stateMachine.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "DnsAutomationService destroyed.")
    }
}
