package com.blocker.dns.steps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.engine.LogExporter
import com.blocker.dns.engine.NodeHelper
import com.blocker.dns.model.CleanBrowsingConfig

class WifiDnsStep {
    private val TAG = "WifiDnsStep"

    enum class SubState {
        CLICK_DETAIL_ARROW,
        SCROLL_TO_IP_SPINNER,
        CLICK_IP_SPINNER,
        SELECT_STATIC_OPTION,
        SCROLL_TO_DNS_FIELDS,
        FILL_DNS_FIELDS,
        CLICK_SAVE,
        DONE
    }

    private var currentState = SubState.CLICK_DETAIL_ARROW
    private var lastActionTime = 0L
    private var scrollAttempts = 0
    private var spinnerAttempts = 0

    fun reset() {
        currentState = SubState.CLICK_DETAIL_ARROW
        lastActionTime = 0L
        scrollAttempts = 0
        spinnerAttempts = 0
    }

    fun isDone(): Boolean = currentState == SubState.DONE

    fun processNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) {
            LogExporter.log("[WifiDnsStep] root is null")
            return false
        }
        if (isDone()) return false

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 500) {
            return false // Debounce to allow animations to settle
        }

        LogExporter.log("[الطبقة الثانية: إعدادات Wi-Fi و DNS الثابت] الحالة: $currentState | ${NodeHelper.dumpScreen(root)}")

        when (currentState) {
            SubState.CLICK_DETAIL_ARROW -> {
                // If already on Static / DNS fields inside network details
                if (NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1") != null) {
                    LogExporter.log("[WifiDnsStep] Found dns1 field -> transitioning to FILL_DNS_FIELDS")
                    currentState = SubState.FILL_DNS_FIELDS
                    return true
                }
                if (NodeHelper.findNodeByViewId(root, "com.android.settings:id/ip_settings") != null) {
                    LogExporter.log("[WifiDnsStep] Found ip_settings field -> transitioning to CLICK_IP_SPINNER")
                    currentState = SubState.CLICK_IP_SPINNER
                    return true
                }

                // Check for popup option directly
                val staticOption = NodeHelper.findNodeByAnyText(root, listOf("ثابت", "Static"))
                if (staticOption != null) {
                    LogExporter.log("[WifiDnsStep] Found Static option directly -> transitioning to SELECT_STATIC_OPTION")
                    currentState = SubState.SELECT_STATIC_OPTION
                    return processNode(root)
                }

                // 1. Target specific gear/arrow/edit buttons directly
                val detailNode = NodeHelper.findNodeByViewId(root, "com.android.settings:id/preference_detail")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/settings_button")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/friction_icon")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/edit")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/edit_btn")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("Network details", "تفاصيل الشبكة", "إعدادات الشبكة", "ضبط الشبكة")
                    )

                if (detailNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.forceGestureTap(detailNode)
                    LogExporter.log("[WifiDnsStep] Tapped Wi-Fi detail gear/arrow node -> $clicked")
                    currentState = SubState.SCROLL_TO_IP_SPINNER
                    scrollAttempts = 0
                    return true
                } else {
                    LogExporter.log("[WifiDnsStep] No direct detail arrow/gear node found")
                }

                // 2. Find connected Wi-Fi row
                val connectedKeywords = listOf("Connected", "متصل", "Current network", "الشبكة الحالية", "اتصال", "موصول")
                val connectedNode = NodeHelper.findNodeByAnyText(root, connectedKeywords)

                if (connectedNode != null) {
                    lastActionTime = now
                    val row = connectedNode.parent ?: connectedNode
                    val tappedEdge = NodeHelper.tapRightEdge(row)
                    LogExporter.log("[WifiDnsStep] Found connected Wi-Fi text. Tapped right edge -> $tappedEdge")
                    if (!tappedEdge) {
                        val tappedNode = NodeHelper.tapNode(row)
                        LogExporter.log("[WifiDnsStep] Tapped connected row fallback -> $tappedNode")
                    }

                    currentState = SubState.SCROLL_TO_IP_SPINNER
                    scrollAttempts = 0
                    return true
                } else {
                    LogExporter.log("[WifiDnsStep] Connected network text not found on screen")
                }
            }

            SubState.SCROLL_TO_IP_SPINNER -> {
                // If DNS fields already visible
                if (NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1") != null) {
                    LogExporter.log("[WifiDnsStep] Found dns1 in SCROLL_TO_IP_SPINNER -> transitioning to FILL_DNS_FIELDS")
                    currentState = SubState.FILL_DNS_FIELDS
                    return true
                }

                // If Static popup already open
                val staticOption = NodeHelper.findNodeByAnyText(root, listOf("ثابت", "Static"))
                if (staticOption != null) {
                    LogExporter.log("[WifiDnsStep] Static popup open in SCROLL_TO_IP_SPINNER -> transitioning to SELECT_STATIC_OPTION")
                    currentState = SubState.SELECT_STATIC_OPTION
                    return processNode(root)
                }

                // Look for IP settings row / spinner
                val ipSpinner = NodeHelper.findNodeByViewId(root, "com.android.settings:id/ip_settings")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("DHCP", "إعدادات IP", "ضبط IP", "الاعدادات IP", "IP settings")
                    )

                if (ipSpinner != null) {
                    LogExporter.log("[WifiDnsStep] Found IP settings spinner -> transitioning to CLICK_IP_SPINNER")
                    currentState = SubState.CLICK_IP_SPINNER
                    return processNode(root)
                }

                // Check for expandable "Advanced" / "عرض المزيد" section
                val advancedKeywords = listOf(
                    "Advanced options",
                    "خيارات متقدمة",
                    "Advanced",
                    "متقدم",
                    "عرض المزيد",
                    "View more",
                    "المزيد",
                    "خيارات إضافية"
                )
                val advancedNode = NodeHelper.findNodeByAnyText(root, advancedKeywords)
                if (advancedNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.forceGestureTap(advancedNode)
                    LogExporter.log("[WifiDnsStep] Found and tapped Advanced/View more section -> $clicked")
                    return true
                }

                // Scroll down physically using dynamic gesture swipe
                lastActionTime = now
                scrollAttempts++
                LogExporter.log("[WifiDnsStep] IP settings not found, scrollDown attempt #$scrollAttempts")
                NodeHelper.scrollDown(root)
                if (scrollAttempts > 6) {
                    LogExporter.log("[WifiDnsStep] Exceeded 6 scroll attempts, forcing CLICK_IP_SPINNER state")
                    currentState = SubState.CLICK_IP_SPINNER
                }
                return true
            }

            SubState.CLICK_IP_SPINNER -> {
                // If DNS fields already visible, jump straight to filling
                if (NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1") != null) {
                    LogExporter.log("[WifiDnsStep] DNS fields already visible in CLICK_IP_SPINNER -> transitioning to FILL_DNS_FIELDS")
                    currentState = SubState.FILL_DNS_FIELDS
                    return true
                }

                // Check if popup with 'ثابت' / 'Static' is already open
                val staticOption = NodeHelper.findNodeByAnyText(root, listOf("ثابت", "Static"))
                if (staticOption != null) {
                    LogExporter.log("[WifiDnsStep] Static popup already open in CLICK_IP_SPINNER -> transitioning to SELECT_STATIC_OPTION")
                    currentState = SubState.SELECT_STATIC_OPTION
                    return processNode(root)
                }

                val ipSpinner = NodeHelper.findNodeByViewId(root, "com.android.settings:id/ip_settings")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("DHCP", "إعدادات IP", "ضبط IP", "الاعدادات IP", "IP settings")
                    )

                if (ipSpinner != null) {
                    lastActionTime = now
                    spinnerAttempts++
                    val clicked = NodeHelper.forceGestureTap(ipSpinner)
                    LogExporter.log("[WifiDnsStep] Tapped IP Settings spinner with forceGestureTap -> $clicked (attempt #$spinnerAttempts)")
                    currentState = SubState.SELECT_STATIC_OPTION
                    return true
                } else {
                    lastActionTime = now
                    LogExporter.log("[WifiDnsStep] IP spinner not found in CLICK_IP_SPINNER, scrolling down")
                    NodeHelper.scrollDown(root)
                    return true
                }
            }

            SubState.SELECT_STATIC_OPTION -> {
                val staticOption = NodeHelper.findNodeByAnyText(
                    root,
                    listOf("ثابت", "Static", "Static IP", "عنوان IP ثابت")
                )

                if (staticOption != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.forceGestureTap(staticOption)
                    LogExporter.log("[WifiDnsStep] Selected Static option -> $clicked")
                    currentState = SubState.SCROLL_TO_DNS_FIELDS
                    scrollAttempts = 0
                    return true
                } else {
                    lastActionTime = now
                    LogExporter.log("[WifiDnsStep] Static option not visible (spinnerAttempts=$spinnerAttempts)")
                    if (spinnerAttempts < 3) {
                        LogExporter.log("[WifiDnsStep] Re-attempting CLICK_IP_SPINNER (attempts < 3)")
                        currentState = SubState.CLICK_IP_SPINNER
                    } else {
                        LogExporter.log("[WifiDnsStep] Max spinner attempts reached. Advancing to SCROLL_TO_DNS_FIELDS in case static is already selected.")
                        currentState = SubState.SCROLL_TO_DNS_FIELDS
                    }
                    return true
                }
            }

            SubState.SCROLL_TO_DNS_FIELDS -> {
                val dns1Node = NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("DNS 1", "dns1", "نظام أسماء النطاقات 1", "خادم نظام أسماء النطاقات 1")
                    )

                if (dns1Node != null) {
                    LogExporter.log("[WifiDnsStep] Found dns1 in SCROLL_TO_DNS_FIELDS -> transitioning to FILL_DNS_FIELDS")
                    currentState = SubState.FILL_DNS_FIELDS
                    return processNode(root)
                } else {
                    lastActionTime = now
                    scrollAttempts++
                    LogExporter.log("[WifiDnsStep] Scrolling to reveal DNS fields (attempt #$scrollAttempts)")
                    NodeHelper.scrollDown(root)
                    if (scrollAttempts > 5) {
                        currentState = SubState.FILL_DNS_FIELDS
                    }
                    return true
                }
            }

            SubState.FILL_DNS_FIELDS -> {
                val dns1Node = NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns1")
                    ?: NodeHelper.findNodeByAnyText(
                        root,
                        listOf("DNS 1", "dns1", "نظام أسماء النطاقات 1", "خادم نظام أسماء النطاقات 1")
                    )

                if (dns1Node != null) {
                    val dns1Edit = if (dns1Node.isEditable) dns1Node else NodeHelper.findFirstEditableNode(dns1Node.parent ?: dns1Node)
                    if (dns1Edit != null) {
                        val set1 = NodeHelper.setText(dns1Edit, CleanBrowsingConfig.WIFI_DNS_PRIMARY)
                        LogExporter.log("[WifiDnsStep] Set DNS1 to '${CleanBrowsingConfig.WIFI_DNS_PRIMARY}' -> $set1")
                    } else {
                        LogExporter.log("[WifiDnsStep] dns1Edit is null")
                    }

                    val dns2Node = NodeHelper.findNodeByViewId(root, "com.android.settings:id/dns2")
                        ?: NodeHelper.findNodeByAnyText(
                            root,
                            listOf("DNS 2", "dns2", "نظام أسماء النطاقات 2", "خادم نظام أسماء النطاقات 2")
                        )

                    if (dns2Node != null) {
                        val dns2Edit = if (dns2Node.isEditable) dns2Node else NodeHelper.findFirstEditableNode(dns2Node.parent ?: dns2Node)
                        if (dns2Edit != null) {
                            val set2 = NodeHelper.setText(dns2Edit, CleanBrowsingConfig.WIFI_DNS_SECONDARY)
                            LogExporter.log("[WifiDnsStep] Set DNS2 to '${CleanBrowsingConfig.WIFI_DNS_SECONDARY}' -> $set2")
                        } else {
                            LogExporter.log("[WifiDnsStep] dns2Edit is null")
                        }
                    } else {
                        LogExporter.log("[WifiDnsStep] dns2Node not found on screen")
                    }

                    lastActionTime = now
                    currentState = SubState.CLICK_SAVE
                    return true
                } else {
                    lastActionTime = now
                    LogExporter.log("[WifiDnsStep] dns1Node not found in FILL_DNS_FIELDS, scrolling down")
                    NodeHelper.scrollDown(root)
                    return true
                }
            }

            SubState.CLICK_SAVE -> {
                val saveNode = NodeHelper.findNodeByViewId(root, "android:id/button1")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/action_bar")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/save")
                    ?: NodeHelper.findNodeByViewId(root, "com.android.settings:id/btn_save")
                    ?: NodeHelper.findNodeByAnyText(root, CleanBrowsingConfig.SAVE_BUTTON_KEYWORDS)

                if (saveNode != null) {
                    lastActionTime = now
                    val clicked = NodeHelper.tapNode(saveNode)
                    LogExporter.log("[WifiDnsStep] Clicked Save on Wi-Fi details -> $clicked")
                    currentState = SubState.DONE
                    return true
                } else {
                    val (w, _) = NodeHelper.getScreenDimensions()
                    lastActionTime = now
                    val tappedCheck = NodeHelper.tapAt(w - 60f, 140f)
                    LogExporter.log("[WifiDnsStep] Fallback tap top-right checkmark in MIUI -> $tappedCheck")
                    currentState = SubState.DONE
                    return true
                }
            }

            SubState.DONE -> return true
        }

        return false
    }
}
