package com.blocker.dns.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.service.DnsAutomationService

object NodeHelper {
    private const val TAG = "NodeHelper"

    fun findNodeByText(
        root: AccessibilityNodeInfo?,
        query: String,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        val nodes = root.findAccessibilityNodeInfosByText(query)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                if (exact) {
                    if (text.equals(query, ignoreCase = true)) return node
                } else {
                    if (text.contains(query, ignoreCase = true)) return node
                }
            }
        }
        return null
    }

    fun findNodeByAnyText(
        root: AccessibilityNodeInfo?,
        keywords: List<String>,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        for (keyword in keywords) {
            val node = findNodeByText(root, keyword, exact)
            if (node != null) return node
        }
        return null
    }

    fun findNodeByViewId(root: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes?.firstOrNull()
    }

    fun findFirstEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable || root.className == "android.widget.EditText") {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFirstEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }

        if (target != null && target.isClickable) {
            val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                Log.d(TAG, "performAction ACTION_CLICK success on '${target.viewIdResourceName ?: target.text}'")
                return true
            }
        }

        // Gesture tap fallback using screen coordinates (essential for MIUI spinners/unclickable items)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            val cx = rect.centerX().toFloat()
            val cy = rect.centerY().toFloat()
            val dispatched = tapAt(cx, cy)
            Log.d(TAG, "Gesture tap at ($cx, $cy) on '${node.viewIdResourceName ?: node.text}' -> $dispatched")
            return dispatched
        }

        return false
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val service = DnsAutomationService.instance ?: return false
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false

        val target = if (node.isEditable) node else findFirstEditableNode(node) ?: node
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d(TAG, "setText '$text' on '${target.viewIdResourceName}' -> $success")
        return success
    }

    fun scrollForward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isScrollable) {
            target = target.parent
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }
}
