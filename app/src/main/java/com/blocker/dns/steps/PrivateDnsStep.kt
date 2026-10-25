package com.blocker.dns.steps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
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

    fun reset() {
        currentState = SubState.FIND_AND_CLICK_RADIO
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || isDone()) return false

        when (currentState) {
            SubState.FIND_AND_CLICK_RADIO -> {
                // If hostname EditText already present
                val directInput = NodeHelper.findNodeByViewId(root, "com.android.settings:id/private_dns_mode_provider_hostname")
                    ?: NodeHelper.findFirstEditableNode(root)

                if (directInput != null) {
                    Log.d(TAG, "Found Private DNS input field directly.")
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
                    val clicked = NodeHelper.performClick(radioNode)
                    Log.d(TAG, "Clicked Private DNS radio option -> $clicked")
                    currentState = SubState.INPUT_HOSTNAME
                    return true
                }

                // Fallback: If in main settings menu, look for Private DNS row
                val entryNode = NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.PRIVATE_DNS_KEYWORDS)
                if (entryNode != null) {
                    NodeHelper.performClick(entryNode)
                    return true
                }
            }

            SubState.INPUT_HOSTNAME -> {
                val editNode = NodeHelper.findNodeByViewId(root, "com.android.settings:id/private_dns_mode_provider_hostname")
                    ?: NodeHelper.findFirstEditableNode(root)

                if (editNode != null) {
                    val currentText = editNode.text?.toString() ?: ""
                    if (currentText == CleanBrowsingConfig.PRIVATE_DNS_HOSTNAME) {
                        Log.d(TAG, "Private DNS hostname already matches target.")
                        currentState = SubState.CLICK_SAVE
                        return processNode(root)
                    }

                    val typed = NodeHelper.setText(editNode, CleanBrowsingConfig.PRIVATE_DNS_HOSTNAME)
                    Log.d(TAG, "Typed hostname: $typed")
                    if (typed) {
                        currentState = SubState.CLICK_SAVE
                        return true
                    }
                }
            }

            SubState.CLICK_SAVE -> {
                // Look for Save button by ID or text
                val saveNode = NodeHelper.findNodeByViewId(root, "android:id/button1")
                    ?: NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.SAVE_BUTTON_KEYWORDS)

                if (saveNode != null && saveNode.isEnabled) {
                    val clicked = NodeHelper.performClick(saveNode)
                    Log.d(TAG, "Clicked Save on Private DNS dialog -> $clicked")
                    if (clicked) {
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
