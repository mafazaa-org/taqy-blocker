package com.blocker.dns.steps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig

class PrivateDnsStep {
    private val TAG = "PrivateDnsStep"

    enum class SubState {
        CLICK_ENTRY,
        SELECT_RADIO,
        INPUT_HOSTNAME,
        CLICK_SAVE,
        DONE
    }

    private var currentState = SubState.CLICK_ENTRY

    fun reset() {
        currentState = SubState.CLICK_ENTRY
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || isDone()) return false

        when (currentState) {
            SubState.CLICK_ENTRY -> {
                val node = NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.PRIVATE_DNS_KEYWORDS)
                if (node != null) {
                    val clicked = NodeHelper.performClick(node)
                    if (clicked) {
                        Log.d(TAG, "Clicked Private DNS entry")
                        currentState = SubState.SELECT_RADIO
                        return true
                    }
                } else {
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.SELECT_RADIO -> {
                val radioKeywords = listOf(
                    "Private DNS provider hostname",
                    "اسم مضيف موفر نظام أسماء النطاقات الخاص",
                    "Hostname",
                    "موفّر اسم المضيف"
                )
                val node = NodeHelper.findNodeByAnyText(root, radioKeywords)
                if (node != null) {
                    NodeHelper.performClick(node)
                    currentState = SubState.INPUT_HOSTNAME
                    return true
                }
                // If input field is already visible, jump straight to input
                val editNode = NodeHelper.findFirstEditableNode(root)
                if (editNode != null) {
                    currentState = SubState.INPUT_HOSTNAME
                    return true
                }
            }

            SubState.INPUT_HOSTNAME -> {
                val editNode = NodeHelper.findFirstEditableNode(root)
                if (editNode != null) {
                    val typed = NodeHelper.setText(editNode, CleanBrowsingConfig.PRIVATE_DNS_HOSTNAME)
                    if (typed) {
                        Log.d(TAG, "Entered Private DNS hostname: ${CleanBrowsingConfig.PRIVATE_DNS_HOSTNAME}")
                        currentState = SubState.CLICK_SAVE
                        return true
                    }
                }
            }

            SubState.CLICK_SAVE -> {
                val saveNode = NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.SAVE_BUTTON_KEYWORDS, exact = true)
                    ?: NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.SAVE_BUTTON_KEYWORDS, exact = false)

                if (saveNode != null) {
                    val clicked = NodeHelper.performClick(saveNode)
                    if (clicked) {
                        Log.d(TAG, "Clicked Save on Private DNS")
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
