package com.blocker.dns.engine

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

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

        return if (target != null && target.isClickable) {
            val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "performClick on '${target.text ?: target.viewIdResourceName}' -> $success")
            success
        } else {
            // Force click direct node
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "performClick fallback on '${node.text ?: node.viewIdResourceName}' -> $success")
            success
        }
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

    fun dumpHierarchy(root: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (root == null) return ""
        val indent = "  ".repeat(depth)
        val text = root.text?.toString() ?: ""
        val desc = root.contentDescription?.toString() ?: ""
        val resId = root.viewIdResourceName ?: ""
        val cls = root.className?.toString()?.substringAfterLast('.') ?: ""

        val sb = StringBuilder()
        sb.append("$indent[$cls] id='$resId' text='$text' desc='$desc'\n")
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                sb.append(dumpHierarchy(child, depth + 1))
            }
        }
        return sb.toString()
    }
}
