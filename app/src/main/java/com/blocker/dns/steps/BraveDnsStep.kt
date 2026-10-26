package com.blocker.dns.steps

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.LogExporter
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig
import com.blocker.dns.service.DnsAutomationService

class BraveDnsStep {
    private val TAG = "BraveDnsStep"

    enum class SubState {
        CLICK_MENU,
        SCROLL_AND_CLICK_SETTINGS,
        SCROLL_AND_CLICK_SHIELDS_PRIVACY,
        SCROLL_AND_CLICK_SECURE_DNS,
        SELECT_CUSTOM_PROVIDER,
        ENTER_DOH_URL,
        DONE
    }

    private var currentState = SubState.CLICK_MENU
    private var lastActionTime = 0L
    private var scrollCount = 0

    fun reset() {
        currentState = SubState.CLICK_MENU
        lastActionTime = 0L
        scrollCount = 0
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) {
            LogExporter.log("[BraveDnsStep] root is null")
            return false
        }
        if (isDone()) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 700) {
            return false
        }

        LogExporter.log("[الطبقة الرابعة: متصفح Brave المشفر (DoH)] الحالة: $currentState | ${NodeHelper.dumpScreen(root)}")

        // Fast-path: Check if already on custom DoH input or secure DNS
        if (NodeHelper.findNodeByViewId(root, "com.brave.browser:id/custom_server") != null) {
            LogExporter.log("[BraveDnsStep] Found custom_server field directly -> transitioning to ENTER_DOH_URL")
            currentState = SubState.ENTER_DOH_URL
            return enterDohUrl(root, now)
        }

        when (currentState) {
            SubState.CLICK_MENU -> {
                if (NodeHelper.findNodeByViewId(root, "com.brave.browser:id/secure") != null) {
                    LogExporter.log("[BraveDnsStep] Found secure provider radio -> transitioning to SELECT_CUSTOM_PROVIDER")
                    currentState = SubState.SELECT_CUSTOM_PROVIDER
                    return processNode(root)
                }

                val menuNode = NodeHelper.findNodeByViewId(root, "com.brave.browser:id/bottom_toolbar_menu")
                    ?: NodeHelper.findNodeByViewId(root, "com.brave.browser:id/menu_button")
                    ?: NodeHelper.findNodeByViewId(root, "com.brave.browser:id/btn_menu")
                    ?: NodeHelper.findNodeByViewId(root, "com.brave.browser:id/more_options")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("More options", "المزيد من الخيارات", "Menu", "قائمة", "خيارات إضافية")
                    )

                lastActionTime = now
                if (menuNode != null) {
                    val clicked = NodeHelper.forceGestureTap(menuNode)
                    LogExporter.log("[BraveDnsStep] Tapped Brave menu button -> $clicked")
                } else {
                    // Fallback: In Brave, 3-dots is usually at the bottom-right
                    val (w, h) = NodeHelper.getScreenDimensions()
                    val tapped = NodeHelper.tapAt(w - 70f, h - 120f)
                    LogExporter.log("[BraveDnsStep] Fallback tapped Brave bottom-right menu coordinates -> $tapped")
                }
                currentState = SubState.SCROLL_AND_CLICK_SETTINGS
                scrollCount = 0
                return true
            }

            SubState.SCROLL_AND_CLICK_SETTINGS -> {
                val settingsNode = NodeHelper.findNodeByViewId(root, "com.brave.browser:id/preferences_id")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("Settings", "الإعدادات", "الاعدادات", "الضبط"))

                if (settingsNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(settingsNode)
                    LogExporter.log("[BraveDnsStep] Tapped Brave Settings -> $clicked")
                    currentState = SubState.SCROLL_AND_CLICK_SHIELDS_PRIVACY
                    scrollCount = 0
                    return true
                } else {
                    lastActionTime = now
                    scrollCount++
                    LogExporter.log("[BraveDnsStep] Brave Settings not visible, swiping gently (swipe $scrollCount)")
                    if (scrollCount <= 2) {
                        val (w, h) = NodeHelper.getScreenDimensions()
                        NodeHelper.swipeUp(w * 0.8f, h * 0.65f, h * 0.35f, 250)
                    } else {
                        // Menu was closed; reset to CLICK_MENU to avoid infinite scroll loop
                        LogExporter.log("[BraveDnsStep] Brave Settings not found after 2 swipes. Re-tapping menu.")
                        currentState = SubState.CLICK_MENU
                        scrollCount = 0
                    }
                    return true
                }
            }

            SubState.SCROLL_AND_CLICK_SHIELDS_PRIVACY -> {
                val shieldsKeywords = listOf(
                    "Brave Shields & privacy",
                    "Shields & privacy",
                    "دروع Brave والخصوصية",
                    "دروع وفلاتر Brave",
                    "Brave Shields",
                    "Shields",
                    "الخصوصية والأمان",
                    "الخصوصية والامان",
                    "Privacy and security",
                    "الخصوصية"
                )
                val shieldsNode = NodeHelper.findNodeByAnyText(root, shieldsKeywords)

                if (shieldsNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(shieldsNode)
                    LogExporter.log("[BraveDnsStep] Tapped Brave Shields & Privacy -> $clicked")
                    currentState = SubState.SCROLL_AND_CLICK_SECURE_DNS
                    scrollCount = 0
                    return true
                } else {
                    lastActionTime = now
                    scrollCount++
                    LogExporter.log("[BraveDnsStep] Shields & Privacy not found on screen, scrollCount=$scrollCount")
                    if (scrollCount <= 4) {
                        LogExporter.log("[BraveDnsStep] Scrolling down for Shields & Privacy in Brave")
                        NodeHelper.scrollDown(root)
                    } else {
                        LogExporter.log("[BraveDnsStep] Max scrolls reached, advancing to SCROLL_AND_CLICK_SECURE_DNS")
                        currentState = SubState.SCROLL_AND_CLICK_SECURE_DNS
                        scrollCount = 0
                    }
                    return true
                }
            }

            SubState.SCROLL_AND_CLICK_SECURE_DNS -> {
                val secureDnsKeywords = listOf(
                    "Use secure DNS",
                    "استخدام نظام أسماء نطاقات آمن",
                    "استخدام نظام أسماء النطاقات (DNS) الآمن",
                    "استخدام نظام أسماء النطاقات الآمن",
                    "نظام أسماء النطاقات\" (DNS) الآمن",
                    "نظام أسماء النطاقات الآمن",
                    "نظام أسماء النطاقات",
                    "Secure DNS",
                    "DNS"
                )

                val secureDnsNode = NodeHelper.findNodeByAnyText(root, secureDnsKeywords)
                if (secureDnsNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(secureDnsNode)
                    LogExporter.log("[BraveDnsStep] Tapped Secure DNS in Brave -> $clicked")
                    currentState = SubState.SELECT_CUSTOM_PROVIDER
                    scrollCount = 0
                    return true
                } else {
                    lastActionTime = now
                    scrollCount++
                    LogExporter.log("[BraveDnsStep] Secure DNS not found in Brave, scrollCount=$scrollCount")
                    if (scrollCount <= 4) {
                        LogExporter.log("[BraveDnsStep] Scrolling down for Secure DNS in Brave")
                        NodeHelper.scrollDown(root)
                    } else {
                        LogExporter.log("[BraveDnsStep] Max scrolls reached, advancing to SELECT_CUSTOM_PROVIDER")
                        currentState = SubState.SELECT_CUSTOM_PROVIDER
                        scrollCount = 0
                    }
                    return true
                }
            }

            SubState.SELECT_CUSTOM_PROVIDER -> {
                val customInput = NodeHelper.findNodeByViewId(root, "com.brave.browser:id/custom_server")
                if (customInput != null) {
                    LogExporter.log("[BraveDnsStep] Custom server input field already visible -> ENTER_DOH_URL")
                    currentState = SubState.ENTER_DOH_URL
                    return enterDohUrl(root, now)
                }

                val customRadio = NodeHelper.findNodeByViewId(root, "com.brave.browser:id/secure")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("Choose another provider", "اختيار مقدِّم خدمة آخر", "اختيار موفّر آخر", "اختيار موفر اخر", "Custom", "مخصص")
                    )

                if (customRadio != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(customRadio)
                    LogExporter.log("[BraveDnsStep] Selected 'Choose another provider' in Brave -> $clicked")
                    currentState = SubState.ENTER_DOH_URL
                    return true
                } else {
                    LogExporter.log("[BraveDnsStep] Custom provider radio option not found")
                }

                val firstEdit = NodeHelper.findFirstEditableNode(root)
                if (firstEdit != null) {
                    LogExporter.log("[BraveDnsStep] Fallback: Found first editable node -> ENTER_DOH_URL")
                    currentState = SubState.ENTER_DOH_URL
                    return enterDohUrl(root, now)
                } else {
                    LogExporter.log("[BraveDnsStep] No editable node found in SELECT_CUSTOM_PROVIDER")
                }
            }

            SubState.ENTER_DOH_URL -> {
                return enterDohUrl(root, now)
            }

            SubState.DONE -> return true
        }

        return false
    }

    private fun enterDohUrl(root: AccessibilityNodeInfo, now: Long): Boolean {
        val editNode = NodeHelper.findNodeByViewId(root, "com.brave.browser:id/custom_server")
            ?: NodeHelper.findFirstEditableNode(root)

        if (editNode != null) {
            val currentVal = editNode.text?.toString() ?: ""
            LogExporter.log("[BraveDnsStep] Custom server editNode found, current text='$currentVal'")
            if (currentVal == CleanBrowsingConfig.DOH_URL) {
                LogExporter.log("[BraveDnsStep] Brave DoH URL already matches target. Step done.")
                // Exit Brave settings back to browser
                DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                currentState = SubState.DONE
                return true
            }

            lastActionTime = now
            val typed = NodeHelper.setText(editNode, CleanBrowsingConfig.DOH_URL)
            LogExporter.log("[BraveDnsStep] Entered Brave DoH URL: ${CleanBrowsingConfig.DOH_URL} -> $typed")
            if (typed) {
                // Exit Brave settings back to browser
                DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                currentState = SubState.DONE
                return true
            }
        } else {
            LogExporter.log("[BraveDnsStep] enterDohUrl: editNode is NULL")
        }
        return false
    }
}
