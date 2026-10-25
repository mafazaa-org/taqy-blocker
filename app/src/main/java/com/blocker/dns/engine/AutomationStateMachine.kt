package com.blocker.dns.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.model.AutomationStep
import com.blocker.dns.service.DnsAutomationService
import com.blocker.dns.steps.BraveDnsStep
import com.blocker.dns.steps.ChromeDnsStep
import com.blocker.dns.steps.PrivateDnsStep
import com.blocker.dns.steps.WifiDnsStep

class AutomationStateMachine(private val context: Context) {
    private val TAG = "AutomationStateMachine"

    interface AutomationListener {
        fun onStepChanged(step: AutomationStep, message: String)
        fun onLog(message: String)
        fun onCompleted()
        fun onError(step: AutomationStep, error: String)
    }

    private var listener: AutomationListener? = null
    var currentStep: AutomationStep = AutomationStep.IDLE
        private set

    private val privateDnsStep = PrivateDnsStep()
    private val wifiDnsStep = WifiDnsStep()
    private val chromeDnsStep = ChromeDnsStep()
    private val braveDnsStep = BraveDnsStep()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setListener(listener: AutomationListener?) {
        this.listener = listener
    }

    fun start() {
        Log.d(TAG, "Starting DNS enforcement pipeline...")
        listener?.onLog("Pipeline started. Launching Step 1: Private DNS...")
        transitionTo(AutomationStep.PRIVATE_DNS)
    }

    fun stop() {
        currentStep = AutomationStep.IDLE
        listener?.onLog("Automation halted.")
    }

    fun onAccessibilityNode(root: AccessibilityNodeInfo?) {
        if (root == null || currentStep == AutomationStep.IDLE || currentStep == AutomationStep.COMPLETED) {
            return
        }

        when (currentStep) {
            AutomationStep.PRIVATE_DNS -> {
                privateDnsStep.processNode(root)
                if (privateDnsStep.isDone()) {
                    listener?.onLog("Step 1 (Private DNS) SUCCESS. Applied family-filter-dns.cleanbrowsing.org")
                    DnsAutomationService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    transitionTo(AutomationStep.WIFI_DNS)
                }
            }

            AutomationStep.WIFI_DNS -> {
                wifiDnsStep.processNode(root)
                if (wifiDnsStep.isDone()) {
                    listener?.onLog("Step 2 (Wi-Fi DNS) SUCCESS. Applied 185.228.168.168 / 185.228.169.168")
                    DnsAutomationService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    transitionTo(AutomationStep.CHROME_DOH)
                }
            }

            AutomationStep.CHROME_DOH -> {
                chromeDnsStep.processNode(root)
                if (chromeDnsStep.isDone()) {
                    listener?.onLog("Step 3 (Chrome DoH) SUCCESS.")
                    transitionTo(AutomationStep.BRAVE_DOH)
                }
            }

            AutomationStep.BRAVE_DOH -> {
                braveDnsStep.processNode(root)
                if (braveDnsStep.isDone()) {
                    listener?.onLog("Step 4 (Brave DoH) SUCCESS.")
                    transitionTo(AutomationStep.COMPLETED)
                }
            }

            else -> Unit
        }
    }

    private fun transitionTo(nextStep: AutomationStep) {
        currentStep = nextStep
        listener?.onStepChanged(nextStep, nextStep.title)

        mainHandler.postDelayed({
            when (nextStep) {
                AutomationStep.PRIVATE_DNS -> {
                    privateDnsStep.reset()
                    listener?.onLog("Launching System Private DNS Settings...")
                    IntentLauncher.launchPrivateDnsSettings(context)
                }

                AutomationStep.WIFI_DNS -> {
                    wifiDnsStep.reset()
                    listener?.onLog("Launching Wi-Fi Settings...")
                    IntentLauncher.launchWifiSettings(context)
                }

                AutomationStep.CHROME_DOH -> {
                    chromeDnsStep.reset()
                    listener?.onLog("Launching Google Chrome...")
                    val launched = IntentLauncher.launchApp(context, IntentLauncher.PACKAGE_CHROME)
                    if (!launched) {
                        listener?.onLog("Chrome not installed. Skipping to Brave...")
                        transitionTo(AutomationStep.BRAVE_DOH)
                    }
                }

                AutomationStep.BRAVE_DOH -> {
                    braveDnsStep.reset()
                    listener?.onLog("Launching Brave Browser...")
                    val launched = IntentLauncher.launchApp(context, IntentLauncher.PACKAGE_BRAVE)
                    if (!launched) {
                        listener?.onLog("Brave not installed. Skipping to Complete...")
                        transitionTo(AutomationStep.COMPLETED)
                    }
                }

                AutomationStep.COMPLETED -> {
                    listener?.onLog("All DNS enforcement steps completed successfully.")
                    listener?.onCompleted()
                }

                else -> Unit
            }
        }, 1200) // Delay ensures smooth OS screen transitions
    }
}
