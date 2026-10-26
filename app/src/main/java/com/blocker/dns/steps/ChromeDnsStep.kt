package com.blocker.dns.steps

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.LogExporter
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig
import com.blocker.dns.service.DnsAutomationService

class ChromeDnsStep {
    private val TAG = "ChromeDnsStep"

    enum class SubState {
        CLICK_MENU,
        SCROLL_AND_CLICK_SETTINGS,
        SCROLL_AND_CLICK_PRIVACY,
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
            LogExporter.log("[ChromeDnsStep] root is null")
            return false
        }
        if (isDone()) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 700) {
            return false
        }

        LogExporter.log("[الطبقة الثالثة: متصفح Google Chrome المشفر (DoH)] الحالة: $currentState | ${NodeHelper.dumpScreen(root)}")

        // Fast-path: Check if already on custom DoH URL field
        if (NodeHelper.findNodeByViewId(root, "com.android.chrome:id/custom_server") != null) {
            LogExporter.log("[ChromeDnsStep] Found custom_server field directly -> transitioning to ENTER_DOH_URL")
            currentState = SubState.ENTER_DOH_URL
            return enterDohUrl(root, now)
        }

        when (currentState) {
            SubState.CLICK_MENU -> {
                if (NodeHelper.findNodeByViewId(root, "com.android.chrome:id/secure") != null) {
                    LogExporter.log("[ChromeDnsStep] Found secure provider radio -> transitioning to SELECT_CUSTOM_PROVIDER")
                    currentState = SubState.SELECT_CUSTOM_PROVIDER
                    return processNode(root)
                }

                val menuNode = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/menu_button")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.chrome:id/more_options")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("More options", "المزيد من الخيارات", "خيارات إضافية", "Menu", "قائمة", "تخصيص Google Chrome")
                    )

                lastActionTime = now
                if (menuNode != null) {
                    val clicked = NodeHelper.forceGestureTap(menuNode)
                    LogExporter.log("[ChromeDnsStep] Tapped Chrome menu button -> $clicked")
                } else {
                    val (w, _) = NodeHelper.getScreenDimensions()
                    val tapped = NodeHelper.tapAt(w - 70f, 130f)
                    LogExporter.log("[ChromeDnsStep] Fallback tapped Chrome top-right menu coordinates -> $tapped")
                }
                currentState = SubState.SCROLL_AND_CLICK_SETTINGS
                scrollCount = 0
                return true
            }

            SubState.SCROLL_AND_CLICK_SETTINGS -> {
                val settingsNode = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/preferences_id")
                    ?: NodeHelper.findNodeByAnyText(root, listOf("Settings", "الإعدادات", "الاعدادات", "الضبط"))

                if (settingsNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(settingsNode)
                    LogExporter.log("[ChromeDnsStep] Tapped Chrome Settings -> $clicked")
                    currentState = SubState.SCROLL_AND_CLICK_PRIVACY
                    scrollCount = 0
                    return true
                } else {
                    lastActionTime = now
                    scrollCount++
                    if (scrollCount <= 2) {
                        LogExporter.log("[ChromeDnsStep] Settings not visible, swiping gently to reveal (swipe $scrollCount)")
                        val (w, h) = NodeHelper.getScreenDimensions()
                        NodeHelper.swipeUp(w * 0.8f, h * 0.65f, h * 0.35f, 250)
                    } else {
                        LogExporter.log("[ChromeDnsStep] Settings not found after 2 scrolls. Resetting to CLICK_MENU.")
                        currentState = SubState.CLICK_MENU
                        scrollCount = 0
                    }
                    return true
                }
            }

            SubState.SCROLL_AND_CLICK_PRIVACY -> {
                val privacyKeywords = listOf(
                    "Privacy and security",
                    "الخصوصية والأمان",
                    "الخصوصية والامان",
                    "الأمان والخصوصية",
                    "الامان والخصوصية",
                    "Privacy",
                    "الخصوصية"
                )
                val privacyNode = NodeHelper.findNodeByAnyText(root, privacyKeywords)
                if (privacyNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(privacyNode)
                    LogExporter.log("[ChromeDnsStep] Tapped Privacy and Security in Chrome -> $clicked")
                    currentState = SubState.SCROLL_AND_CLICK_SECURE_DNS
                    scrollCount = 0
                    return true
                } else {
                    lastActionTime = now
                    scrollCount++
                    LogExporter.log("[ChromeDnsStep] Privacy not found on screen, scrollCount=$scrollCount")
                    if (scrollCount <= 4) {
                        LogExporter.log("[ChromeDnsStep] Scrolling down to reveal Privacy")
                        NodeHelper.scrollDown(root)
                    } else {
                        LogExporter.log("[ChromeDnsStep] Max scrolls reached for Privacy, advancing to SCROLL_AND_CLICK_SECURE_DNS")
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
                    LogExporter.log("[ChromeDnsStep] Tapped Secure DNS in Chrome -> $clicked")
                    currentState = SubState.SELECT_CUSTOM_PROVIDER
                    scrollCount = 0
                    return true
                } else {
                    lastActionTime = now
                    scrollCount++
                    LogExporter.log("[ChromeDnsStep] Secure DNS not found on screen, scrollCount=$scrollCount")
                    if (scrollCount <= 4) {
                        LogExporter.log("[ChromeDnsStep] Scrolling down to reveal Secure DNS")
                        NodeHelper.scrollDown(root)
                    } else {
                        LogExporter.log("[ChromeDnsStep] Max scrolls reached for Secure DNS, advancing to SELECT_CUSTOM_PROVIDER")
                        currentState = SubState.SELECT_CUSTOM_PROVIDER
                        scrollCount = 0
                    }
                    return true
                }
            }

            SubState.SELECT_CUSTOM_PROVIDER -> {
                val customInput = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/custom_server")
                if (customInput != null) {
                    LogExporter.log("[ChromeDnsStep] Custom server input field already visible -> ENTER_DOH_URL")
                    currentState = SubState.ENTER_DOH_URL
                    return enterDohUrl(root, now)
                }

                val customRadio = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/secure")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("Choose another provider", "اختيار مقدِّم خدمة آخر", "اختيار موفّر آخر", "اختيار موفر اخر", "Custom", "مخصص")
                    )

                if (customRadio != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(customRadio)
                    LogExporter.log("[ChromeDnsStep] Selected 'Choose another provider' in Chrome -> $clicked")
                    currentState = SubState.ENTER_DOH_URL
                    return true
                } else {
                    LogExporter.log("[ChromeDnsStep] Custom provider radio option not found")
                }

                val firstEdit = NodeHelper.findFirstEditableNode(root)
                if (firstEdit != null) {
                    LogExporter.log("[ChromeDnsStep] Fallback: Found first editable node -> ENTER_DOH_URL")
                    currentState = SubState.ENTER_DOH_URL
                    return enterDohUrl(root, now)
                } else {
                    LogExporter.log("[ChromeDnsStep] No editable node found in SELECT_CUSTOM_PROVIDER")
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
        val editNode = NodeHelper.findNodeByViewId(root, "com.android.chrome:id/custom_server")
            ?: NodeHelper.findFirstEditableNode(root)

        if (editNode != null) {
            val currentVal = editNode.text?.toString() ?: ""
            LogExporter.log("[ChromeDnsStep] Custom server editNode found, current text='$currentVal'")
            if (currentVal == CleanBrowsingConfig.DOH_URL) {
                LogExporter.log("[ChromeDnsStep] Chrome DoH URL already matches target. Step done.")
                // Exit Chrome settings back to browser
                DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                currentState = SubState.DONE
                return true
            }

            lastActionTime = now
            val typed = NodeHelper.setText(editNode, CleanBrowsingConfig.DOH_URL)
            LogExporter.log("[ChromeDnsStep] Entered Chrome DoH URL: ${CleanBrowsingConfig.DOH_URL} -> $typed")
            if (typed) {
                // Exit Chrome settings back to browser
                DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                currentState = SubState.DONE
                return true
            }
        } else {
            LogExporter.log("[ChromeDnsStep] enterDohUrl: editNode is NULL")
        }
        return false
    }
}
