package com.blocker.dns.steps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig

class BraveDnsStep {
    private val TAG = "BraveDnsStep"

    enum class SubState {
        CLICK_MENU,
        CLICK_SETTINGS,
        CLICK_SHIELDS_PRIVACY,
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
                val menuNode = NodeHelper.findNodeByViewId(root, "com.brave.browser:id/menu_button")
                    ?: NodeHelper.findNodeByViewId(root, "com.brave.browser:id/btn_menu")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("More options", "المزيد من الخيارات", "Menu"))

                if (menuNode != null) {
                    val clicked = NodeHelper.performClick(menuNode)
                    if (clicked) {
                        Log.d(TAG, "Opened Brave menu")
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
                        Log.d(TAG, "Opened Brave Settings")
                        currentState = SubState.CLICK_SHIELDS_PRIVACY
                        return true
                    }
                }
            }

            SubState.CLICK_SHIELDS_PRIVACY -> {
                val shieldsNode = NodeHelper.findNodeByAnyText(
                    root,
                    listOf("Brave Shields & privacy", "Shields & privacy", "دروع Brave والخصوصية", "الخصوصية والأمان")
                )
                if (shieldsNode != null) {
                    val clicked = NodeHelper.performClick(shieldsNode)
                    if (clicked) {
                        Log.d(TAG, "Opened Brave Shields & Privacy")
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
                        Log.d(TAG, "Opened Secure DNS in Brave")
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
                    listOf("Choose another provider", "اختيار موفّر آخر", "Custom", "مخصص")
                )
                if (customProviderNode != null) {
                    NodeHelper.performClick(customProviderNode)
                    currentState = SubState.ENTER_DOH_URL
                    return true
                }
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
                        Log.d(TAG, "Set Brave DoH URL: ${CleanBrowsingConfig.DOH_URL}")
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
