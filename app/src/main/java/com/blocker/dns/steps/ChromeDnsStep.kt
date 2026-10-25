package com.blocker.dns.steps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig

class ChromeDnsStep {
    private val TAG = "ChromeDnsStep"

    enum class SubState {
        CLICK_MENU,
        CLICK_SETTINGS,
        CLICK_PRIVACY,
        CLICK_SECURE_DNS,
        SELECT_CUSTOM_PROVIDER,
        ENTER_DOH_URL,
        DONE
    }

    private var currentState = SubState.CLICK_MENU

    fun reset() {
        currentState = SubState.CLICK_MENU
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || isDone()) return false

        when (currentState) {
            SubState.CLICK_MENU -> {
                val menuNode = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/menu_button")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("More options", "المزيد من الخيارات", "Menu"))

                if (menuNode != null) {
                    val clicked = NodeHelper.performClick(menuNode)
                    if (clicked) {
                        Log.d(TAG, "Opened Chrome menu")
                        currentState = SubState.CLICK_SETTINGS
                        return true
                    }
                }
            }

            SubState.CLICK_SETTINGS -> {
                val settingsNode = NodeHelper.findNodeByAnyText(root, listOf("Settings", "الإعدادات"))
                if (settingsNode != null) {
                    val clicked = NodeHelper.performClick(settingsNode)
                    if (clicked) {
                        Log.d(TAG, "Opened Chrome Settings")
                        currentState = SubState.CLICK_PRIVACY
                        return true
                    }
                }
            }

            SubState.CLICK_PRIVACY -> {
                val privacyNode = NodeHelper.findNodeByAnyText(root, listOf("Privacy and security", "الخصوصية والأمان"))
                if (privacyNode != null) {
                    val clicked = NodeHelper.performClick(privacyNode)
                    if (clicked) {
                        Log.d(TAG, "Opened Chrome Privacy and Security")
                        currentState = SubState.CLICK_SECURE_DNS
                        return true
                    }
                } else {
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.CLICK_SECURE_DNS -> {
                val secureDnsNode = NodeHelper.findNodeByAnyText(root, listOf("Use secure DNS", "استخدام نظام أسماء نطاقات آمن"))
                if (secureDnsNode != null) {
                    val clicked = NodeHelper.performClick(secureDnsNode)
                    if (clicked) {
                        Log.d(TAG, "Opened Secure DNS settings in Chrome")
                        currentState = SubState.SELECT_CUSTOM_PROVIDER
                        return true
                    }
                } else {
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.SELECT_CUSTOM_PROVIDER -> {
                val customProviderNode = NodeHelper.findNodeByAnyText(
                    root,
                    listOf("Choose another provider", "اختيار موفّر آخر")
                )
                if (customProviderNode != null) {
                    NodeHelper.performClick(customProviderNode)
                    currentState = SubState.ENTER_DOH_URL
                    return true
                }
                // Check if edit text already displayed
                if (NodeHelper.findFirstEditableNode(root) != null) {
                    currentState = SubState.ENTER_DOH_URL
                    return true
                }
            }

            SubState.ENTER_DOH_URL -> {
                val editNode = NodeHelper.findFirstEditableNode(root)
                if (editNode != null) {
                    val typed = NodeHelper.setText(editNode, CleanBrowsingConfig.DOH_URL)
                    if (typed) {
                        Log.d(TAG, "Set Chrome DoH URL: ${CleanBrowsingConfig.DOH_URL}")
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
