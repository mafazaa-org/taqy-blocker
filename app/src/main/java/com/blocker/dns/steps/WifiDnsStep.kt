package com.blocker.dns.steps

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig

class WifiDnsStep {
    private val TAG = "WifiDnsStep"

    enum class SubState {
        CLICK_DETAIL_ARROW,
        CLICK_IP_SPINNER,
        SELECT_STATIC_OPTION,
        FILL_DNS_FIELDS,
        CLICK_SAVE,
        DONE
    }

    private var currentState = SubState.CLICK_DETAIL_ARROW
    private var lastActionTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    fun reset() {
        currentState = SubState.CLICK_DETAIL_ARROW
        lastActionTime = 0L
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || isDone()) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 500) {
            return false // Debounce to allow UI to settle
        }

        when (currentState) {
            SubState.CLICK_DETAIL_ARROW -> {
                // If already on Static / DNS fields inside network details
                if (NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1") != null) {
                    currentState = SubState.FILL_DNS_FIELDS
                    return true
                }
                if (NodeHelper.findNodeByViewId(root, "com.android.settings:id/ip_settings") != null) {
                    currentState = SubState.CLICK_IP_SPINNER
                    return true
                }

                // Click MIUI detail arrow
                val miuiDetail = NodeHelper.findNodeByViewId(root, "com.android.settings:id/preference_detail")
                if (miuiDetail != null) {
                    lastActionTime = now
                    NodeHelper.performClick(miuiDetail)
                    currentState = SubState.CLICK_IP_SPINNER
                    return true
                }

                // Standard Stock Android gear icon or connected network row
                val connectedKeywords = listOf("Connected", "متصل", "Current network", "الشبكة الحالية", "تفاصيل شبكة")
                val node = NodeHelper.findNodeByAnyText(root, connectedKeywords)
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/settings_button")

                if (node != null) {
                    lastActionTime = now
                    NodeHelper.performClick(node)
                    currentState = SubState.CLICK_IP_SPINNER
                    return true
                }
            }

            SubState.CLICK_IP_SPINNER -> {
                // Check if already Static
                val staticDns = NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1")
                if (staticDns != null) {
                    currentState = SubState.FILL_DNS_FIELDS
                    return true
                }

                // Check if popup with 'ثابت' / 'Static' is already open
                val staticOption = NodeHelper.findNodeByAnyText(root, listOf("ثابت", "Static"))
                if (staticOption != null) {
                    currentState = SubState.SELECT_STATIC_OPTION
                    return processNode(root)
                }

                val ipSpinner = NodeHelper.findNodeByViewId(root, "com.android.settings:id/ip_settings")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("DHCP", "إعدادات IP", "IP settings"))

                if (ipSpinner != null) {
                    lastActionTime = now
                    NodeHelper.performClick(ipSpinner)
                    Log.d(TAG, "Tapped IP Settings spinner")
                    currentState = SubState.SELECT_STATIC_OPTION
                    return true
                } else {
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.SELECT_STATIC_OPTION -> {
                val staticOption = NodeHelper.findNodeByAnyText(root, listOf("ثابت", "Static"))
                if (staticOption != null) {
                    lastActionTime = now
                    NodeHelper.performClick(staticOption)
                    Log.d(TAG, "Selected Static IP mode")
                    currentState = SubState.FILL_DNS_FIELDS
                    return true
                }
            }

            SubState.FILL_DNS_FIELDS -> {
                val dns1Node = NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("DNS 1", "dns1", "نظام أسماء النطاقات 1"))

                if (dns1Node != null) {
                    val dns1Edit = if (dns1Node.isEditable) dns1Node else NodeHelper.findFirstEditableNode(dns1Node.parent ?: dns1Node)
                    if (dns1Edit != null) {
                        NodeHelper.setText(dns1Edit, CleanBrowsingConfig.WIFI_DNS_PRIMARY)
                    }

                    val dns2Node = NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns2")
                        ?: NodeHelper.findNodeByAnyText(root, listOf("DNS 2", "dns2", "نظام أسماء النطاقات 2"))

                    if (dns2Node != null) {
                        val dns2Edit = if (dns2Node.isEditable) dns2Node else NodeHelper.findFirstEditableNode(dns2Node.parent ?: dns2Node)
                        if (dns2Edit != null) {
                            NodeHelper.setText(dns2Edit, CleanBrowsingConfig.WIFI_DNS_SECONDARY)
                        }
                    }

                    lastActionTime = now
                    currentState = SubState.CLICK_SAVE
                    return true
                } else {
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.CLICK_SAVE -> {
                // Look for Save button / top right checkmark
                val saveNode = NodeHelper.findNodeByViewId(root, "android:id/button1")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/action_bar")
                    ?: NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.SAVE_BUTTON_KEYWORDS)

                if (saveNode != null) {
                    lastActionTime = now
                    NodeHelper.performClick(saveNode)
                    Log.d(TAG, "Clicked Save on Wi-Fi details")
                    currentState = SubState.DONE
                    return true
                } else {
                    // Fallback tap top-right checkmark in MIUI (x=1020, y=160)
                    lastActionTime = now
                    NodeHelper.tapAt(1020f, 160f)
                    currentState = SubState.DONE
                    return true
                }
            }

            SubState.DONE -> return true
        }

        return false
    }
}
