package com.blocker.dns.steps

import android.os.Handler
import android.os.Looper
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
    private var lastActionTime = 0L

    fun reset() {
        currentState = SubState.CLICK_MENU
        lastActionTime = 0L
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || isDone()) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 500) {
            return false
        }

        when (currentState) {
            SubState.CLICK_MENU -> {
                // If already on Secure DNS page inside Chrome
                if (NodeHelper.findNodeByViewId(root, "com.android.chrome:id/custom_server") != null) {
                    currentState = SubState.ENTER_DOH_URL
                    return true
                }
                if (NodeHelper.findNodeByViewId(root, "com.android.chrome:id/secure") != null) {
                    currentState = SubState.SELECT_CUSTOM_PROVIDER
                    return true
                }

                val menuNode = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/menu_button")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("More options", "المزيد من الخيارات", "تخصيص Google Chrome"))

                if (menuNode != null) {
                    lastActionTime = now
                    NodeHelper.performClick(menuNode)
                    Log.d(TAG, "Clicked Chrome menu button")
                    currentState = SubState.CLICK_SETTINGS
                    return true
                }
            }

            SubState.CLICK_SETTINGS -> {
                val settingsNode = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/preferences_id")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("Settings", "الإعدادات"))

                if (settingsNode != null) {
                    lastActionTime = now
                    NodeHelper.performClick(settingsNode)
                    Log.d(TAG, "Clicked Chrome Settings")
                    currentState = SubState.CLICK_PRIVACY
                    return true
                } else {
                    // Swipe up on menu to reveal Settings item at bottom
                    lastActionTime = now
                    NodeHelper.tapAt(300f, 1500f) // Focus list
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.CLICK_PRIVACY -> {
                val privacyNode = NodeHelper.findNodeByAnyText(root, listOf("Privacy and security", "الخصوصية والأمان"))
                if (privacyNode != null) {
                    lastActionTime = now
                    NodeHelper.performClick(privacyNode)
                    Log.d(TAG, "Clicked Privacy and Security")
                    currentState = SubState.CLICK_SECURE_DNS
                    return true
                } else {
                    lastActionTime = now
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.CLICK_SECURE_DNS -> {
                val secureDnsKeywords = listOf(
                    "Use secure DNS",
                    "استخدام نظام أسماء نطاقات آمن",
                    "استخدام \"نظام أسماء النطاقات\" (DNS) الآمن",
                    "نظام أسماء النطاقات\" (DNS)",
                    "DNS"
                )
                val secureDnsNode = NodeHelper.findNodeByAnyText(root, secureDnsKeywords)
                if (secureDnsNode != null) {
                    lastActionTime = now
                    NodeHelper.performClick(secureDnsNode)
                    Log.d(TAG, "Clicked Secure DNS in Chrome")
                    currentState = SubState.SELECT_CUSTOM_PROVIDER
                    return true
                } else {
                    lastActionTime = now
                    NodeHelper.scrollForward(root)
                }
            }

            SubState.SELECT_CUSTOM_PROVIDER -> {
                // If custom URL input is already displayed
                val customInput = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/custom_server")
                if (customInput != null) {
                    currentState = SubState.ENTER_DOH_URL
                    return processNode(root)
                }

                val customRadio = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/secure")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("Choose another provider", "اختيار مقدِّم خدمة آخر"))

                if (customRadio != null) {
                    lastActionTime = now
                    NodeHelper.performClick(customRadio)
                    Log.d(TAG, "Selected Choose another provider in Chrome")
                    currentState = SubState.ENTER_DOH_URL
                    return true
                }
            }

            SubState.ENTER_DOH_URL -> {
                val editNode = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/custom_server")
                    ?: NodeHelper.findFirstEditableNode(root)

                if (editNode != null) {
                    lastActionTime = now
                    val typed = NodeHelper.setText(editNode, CleanBrowsingConfig.DOH_URL)
                    Log.d(TAG, "Entered Chrome DoH URL: ${CleanBrowsingConfig.DOH_URL} -> $typed")
                    currentState = SubState.DONE
                    return true
                }
            }

            SubState.DONE -> return true
        }

        return false
    }
}
