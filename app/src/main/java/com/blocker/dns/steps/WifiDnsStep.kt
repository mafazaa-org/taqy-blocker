package com.blocker.dns.steps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig

class WifiDnsStep {
    private val TAG = "WifiDnsStep"

    enum class SubState {
        CLICK_CONNECTED_NETWORK,
        OPEN_ADVANCED_SETTINGS,
        SWITCH_TO_STATIC,
        FILL_DNS,
        CLICK_SAVE,
        DONE
    }

    private var currentState = SubState.CLICK_CONNECTED_NETWORK

    fun reset() {
        currentState = SubState.CLICK_CONNECTED_NETWORK
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || isDone()) return false

        when (currentState) {
            SubState.CLICK_CONNECTED_NETWORK -> {
                val connectedKeywords = listOf("Connected", "متصل", "Current network", "الشبكة الحالية")
                val node = NodeHelper.findNodeByAnyText(root, connectedKeywords)
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/settings_button")
                if (node != null) {
                    val clicked = NodeHelper.performClick(node)
                    if (clicked) {
                        Log.d(TAG, "Opened connected Wi-Fi details")
                        currentState = SubState.OPEN_ADVANCED_SETTINGS
                        return true
                    }
                }
            }

            SubState.OPEN_ADVANCED_SETTINGS -> {
                val advancedKeywords = listOf("Advanced", "متقدم", "Network details", "تفاصيل الشبكة", "Modify network", "تعديل الشبكة")
                val node = NodeHelper.findNodeByAnyText(root, advancedKeywords)
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/edit_btn")

                if (node != null) {
                    NodeHelper.performClick(node)
                    currentState = SubState.SWITCH_TO_STATIC
                    return true
                }
                // Check if already in edit screen
                if (NodeHelper.findNodeByAnyText(root, listOf("IP settings", "إعدادات IP")) != null) {
                    currentState = SubState.SWITCH_TO_STATIC
                    return true
                }
            }

            SubState.SWITCH_TO_STATIC -> {
                val dhcpNode = NodeHelper.findNodeByAnyText(root, listOf("DHCP", "IP settings", "إعدادات IP"))
                if (dhcpNode != null) {
                    NodeHelper.performClick(dhcpNode)
                    val staticOption = NodeHelper.findNodeByAnyText(root, listOf("Static", "ثابت"))
                    if (staticOption != null) {
                        NodeHelper.performClick(staticOption)
                        currentState = SubState.FILL_DNS
                        return true
                    }
                }
                // If DNS inputs are already visible
                if (NodeHelper.findNodeByAnyText(root, listOf("DNS 1", "DNS 2")) != null) {
                    currentState = SubState.FILL_DNS
                    return true
                }
            }

            SubState.FILL_DNS -> {
                val dns1Label = NodeHelper.findNodeByAnyText(root, listOf("DNS 1", "dns1"))
                if (dns1Label != null) {
                    val dns1Edit = NodeHelper.findFirstEditableNode(dns1Label.parent ?: dns1Label)
                    if (dns1Edit != null) {
                        NodeHelper.setText(dns1Edit, CleanBrowsingConfig.WIFI_DNS_PRIMARY)
                    }
                }

                val dns2Label = NodeHelper.findNodeByAnyText(root, listOf("DNS 2", "dns2"))
                if (dns2Label != null) {
                    val dns2Edit = NodeHelper.findFirstEditableNode(dns2Label.parent ?: dns2Label)
                    if (dns2Edit != null) {
                        NodeHelper.setText(dns2Edit, CleanBrowsingConfig.WIFI_DNS_SECONDARY)
                    }
                }

                currentState = SubState.CLICK_SAVE
                return true
            }

            SubState.CLICK_SAVE -> {
                val saveNode = NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.SAVE_BUTTON_KEYWORDS)
                if (saveNode != null) {
                    val clicked = NodeHelper.performClick(saveNode)
                    if (clicked) {
                        Log.d(TAG, "Saved static Wi-Fi DNS")
                        currentState = SubState.DONE
                        return true
                    }
                }
            }

            SubState.DONE -> return true
        }

        return false
    }
}
