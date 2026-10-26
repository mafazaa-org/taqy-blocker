package com.blocker.dns.steps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.LogExporter
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig

class PrivateDnsStep {
    private val TAG = "PrivateDnsStep"

    enum class SubState {
        FIND_AND_CLICK_RADIO,
        INPUT_HOSTNAME,
        CLICK_SAVE,
        DONE
    }

    private var currentState = SubState.FIND_AND_CLICK_RADIO
    private var lastActionTime = 0L

    fun reset() {
        currentState = SubState.FIND_AND_CLICK_RADIO
        lastActionTime = 0L
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) {
            LogExporter.log("[PrivateDnsStep] root is null")
            return false
        }
        if (isDone()) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 500) {
            return false
        }

        LogExporter.log("[الطبقة الأولى: نظام أسماء النطاقات الخاص (Private DNS)] الحالة: $currentState | ${NodeHelper.dumpScreen(root)}")

        when (currentState) {
            SubState.FIND_AND_CLICK_RADIO -> {
                // If hostname EditText already present
                val directInput = NodeHelper.findNodeByViewId(root, "com.android.settings:id/private_dns_mode_provider_hostname")
                    ?: NodeHelper.findFirstEditableNode(root)

                if (directInput != null) {
                    LogExporter.log("[PrivateDnsStep] Found Private DNS input field directly -> transitioning to INPUT_HOSTNAME")
                    currentState = SubState.INPUT_HOSTNAME
                    return processNode(root)
                }

                // Check for Provider Hostname radio option
                val radioKeywords = listOf(
                    "Private DNS provider hostname",
                    "اسم مضيف مزوّد",
                    "اسم مضيف موفر",
                    "اسم مضيف",
                    "مزوّد نظام أسماء النطاقات الخاص",
                    "Hostname",
                    "Provider hostname"
                )
                val radioNode = NodeHelper.findNodeByAnyText(root, radioKeywords)
                if (radioNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(radioNode)
                    LogExporter.log("[PrivateDnsStep] Clicked Provider Hostname radio node -> $clicked")
                    currentState = SubState.INPUT_HOSTNAME
                    return true
                } else {
                    LogExporter.log("[PrivateDnsStep] Provider Hostname radio node not found on screen")
                }

                // Fallback: If in main settings menu, look for Private DNS row
                val entryNode = NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.PRIVATE_DNS_KEYWORDS)
                if (entryNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(entryNode)
                    LogExporter.log("[PrivateDnsStep] Found and tapped Private DNS settings entry row -> $clicked")
                    return true
                } else {
                    lastActionTime = now
                    LogExporter.log("[PrivateDnsStep] Private DNS entry row not found, attempting scrollDown")
                    NodeHelper.scrollDown(root)
                }
            }

            SubState.INPUT_HOSTNAME -> {
                val editNode = NodeHelper.findNodeByViewId(root, "com.android.settings:id/private_dns_mode_provider_hostname")
                    ?: NodeHelper.findFirstEditableNode(root)

                if (editNode != null) {
                    val currentText = editNode.text?.toString() ?: ""
                    LogExporter.log("[PrivateDnsStep] Found input field. Current text='$currentText'")
                    if (currentText == CleanBrowsingConfig.PRIVATE_DNS_HOSTNAME) {
                        LogExporter.log("[PrivateDnsStep] Hostname already matches target -> transitioning to CLICK_SAVE")
                        currentState = SubState.CLICK_SAVE
                        return processNode(root)
                    }

                    lastActionTime = now
                    val typed = NodeHelper.setText(editNode, CleanBrowsingConfig.PRIVATE_DNS_HOSTNAME)
                    LogExporter.log("[PrivateDnsStep] Typed target hostname '${CleanBrowsingConfig.PRIVATE_DNS_HOSTNAME}' -> $typed")
                    if (typed) {
                        currentState = SubState.CLICK_SAVE
                        return true
                    } else {
                        LogExporter.log("[PrivateDnsStep] Failed to set text on hostname editNode")
                    }
                } else {
                    LogExporter.log("[PrivateDnsStep] Editable hostname field not found in INPUT_HOSTNAME state")
                }
            }

            SubState.CLICK_SAVE -> {
                val saveNode = NodeHelper.findNodeByViewId(root, "android:id/button1")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/save")
                    ?: NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.SAVE_BUTTON_KEYWORDS)

                if (saveNode != null) {
                    LogExporter.log("[PrivateDnsStep] Found Save button (enabled=${saveNode.isEnabled})")
                    if (saveNode.isEnabled) {
                        lastActionTime = now
                        val clicked = NodeHelper.tapNode(saveNode)
                        LogExporter.log("[PrivateDnsStep] Tapped Save button -> $clicked")
                        if (clicked) {
                            currentState = SubState.DONE
                            return true
                        } else {
                            LogExporter.log("[PrivateDnsStep] Tapping Save button returned false")
                        }
                    } else {
                        LogExporter.log("[PrivateDnsStep] Save button is currently disabled")
                    }
                } else {
                    LogExporter.log("[PrivateDnsStep] Save button not found on screen")
                }
            }

            SubState.DONE -> return true
        }

        return false
    }
}
