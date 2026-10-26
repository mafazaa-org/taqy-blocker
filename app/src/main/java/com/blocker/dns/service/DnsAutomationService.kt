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

    fun disableService() {
        Log.d(TAG, "Revoking accessibility service via disableSelf().")
        disableSelf()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        stateMachine = AutomationStateMachine(this)
        Log.d(TAG, "DnsAutomationService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventPkg = event.packageName?.toString() ?: ""
        if (eventPkg == packageName) return

        if (::stateMachine.isInitialized && 
            stateMachine.currentStep != com.blocker.dns.model.AutomationStep.IDLE && 
            stateMachine.currentStep != com.blocker.dns.model.AutomationStep.COMPLETED) {
            val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
            com.blocker.dns.engine.LogExporter.log("[Service] Event: type=$eventType from pkg=$eventPkg")
        }

        val root = rootInActiveWindow ?: event.source
        if (root == null) {
            if (::stateMachine.isInitialized && 
                stateMachine.currentStep != com.blocker.dns.model.AutomationStep.IDLE && 
                stateMachine.currentStep != com.blocker.dns.model.AutomationStep.COMPLETED) {
                com.blocker.dns.engine.LogExporter.log("[Service] rootInActiveWindow and event.source both NULL for pkg=$eventPkg")
            }
            return
        }
        val rootPkg = root.packageName?.toString() ?: ""
        if (rootPkg == packageName) return

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
