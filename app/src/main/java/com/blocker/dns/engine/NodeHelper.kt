package com.blocker.dns.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.service.DnsAutomationService

object NodeHelper {
    private const val TAG = "NodeHelper"

    fun normalizeArabic(input: String): String {
        return input
            .replace(Regex("[\\u064B-\\u0652]"), "") // Remove tashkeel
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .trim()
    }

    fun findNodeByText(
        root: AccessibilityNodeInfo?,
        query: String,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        val normQuery = normalizeArabic(query).lowercase()

        // BFS through node tree to match text and contentDescription with Arabic normalization
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""

            val normText = normalizeArabic(text).lowercase()
            val normDesc = normalizeArabic(desc).lowercase()

            if (exact) {
                if (normText == normQuery || normDesc == normQuery) return node
            } else {
                if (normText.contains(normQuery) || normDesc.contains(normQuery)) return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
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
        if (!nodes.isNullOrEmpty()) {
            return nodes.first()
        }
        // Fallback BFS search if direct query missed
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.viewIdResourceName == viewId) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }
        return null
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

    fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    fun getScreenDimensions(): Pair<Int, Int> {
        val service = DnsAutomationService.instance
        val root = service?.rootInActiveWindow
        if (root != null) {
            val rect = Rect()
            root.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                return Pair(rect.width(), rect.height())
            }
        }
        return if (service != null) {
            val dm = service.resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        } else {
            Pair(1080, 2400)
        }
    }

    fun dumpScreen(root: AccessibilityNodeInfo?, maxNodes: Int = 60): String {
        if (root == null) return "لا توجد نافذة نشطة (root=null)"
        val items = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && items.size < maxNodes) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val id = node.viewIdResourceName?.substringAfterLast('/') ?: ""
            val label = when {
                text.isNotEmpty() && id.isNotEmpty() -> "[$id: \"$text\"]"
                text.isNotEmpty() -> "[\"$text\"]"
                desc.isNotEmpty() && id.isNotEmpty() -> "[$id (وصف: \"$desc\")]"
                desc.isNotEmpty() -> "[(وصف: \"$desc\")]"
                id.isNotEmpty() -> "[$id]"
                else -> ""
            }
            if (label.isNotEmpty() && !items.contains(label)) {
                items.add(label)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        val elementsText = if (items.isNotEmpty()) items.joinToString(", ") else "لا توجد عناصر نصية ظاهرة"
        return "حزمة الصفحة: [${root.packageName}] | العناصر الظاهرة (${items.size}): $elementsText"
    }

    fun tapNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            LogExporter.log("[NodeHelper] tapNode failed: node is null")
            return false
        }

        val nodeDesc = "'${node.viewIdResourceName ?: node.text ?: node.contentDescription}'"

        // Standard accessibility click
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        if (target != null && target.isClickable) {
            val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            LogExporter.log("[NodeHelper] performAction ACTION_CLICK on $nodeDesc -> $success")
            if (success) {
                return true
            }
        }

        // Gesture tap fallback using screen coordinates
        LogExporter.log("[NodeHelper] Standard click unhandled, falling back to forceGestureTap on $nodeDesc")
        return forceGestureTap(node)
    }

    fun forceGestureTap(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            LogExporter.log("[NodeHelper] forceGestureTap failed: node is null")
            return false
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val nodeDesc = "'${node.viewIdResourceName ?: node.text ?: node.contentDescription}'"
        if (rect.width() > 0 && rect.height() > 0) {
            val cx = rect.centerX().toFloat()
            val cy = rect.centerY().toFloat()
            val dispatched = tapAt(cx, cy)
            LogExporter.log("[NodeHelper] forceGestureTap at ($cx, $cy) [bounds: $rect] on $nodeDesc -> $dispatched")
            return dispatched
        }
        LogExporter.log("[NodeHelper] forceGestureTap invalid bounds $rect on $nodeDesc")
        return false
    }

    fun tapRightEdge(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val nodeDesc = "'${node.viewIdResourceName ?: node.text ?: node.contentDescription}'"
        if (rect.width() > 0 && rect.height() > 0) {
            val x = (rect.right - 60f).coerceAtLeast(rect.left + 10f)
            val y = rect.centerY().toFloat()
            val dispatched = tapAt(x, y)
            LogExporter.log("[NodeHelper] tapRightEdge at ($x, $y) on $nodeDesc -> $dispatched")
            return dispatched
        }
        return false
    }

    fun tapLeftEdge(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val nodeDesc = "'${node.viewIdResourceName ?: node.text ?: node.contentDescription}'"
        if (rect.width() > 0 && rect.height() > 0) {
            val x = (rect.left + 60f).coerceAtMost(rect.right - 10f)
            val y = rect.centerY().toFloat()
            val dispatched = tapAt(x, y)
            LogExporter.log("[NodeHelper] tapLeftEdge at ($x, $y) on $nodeDesc -> $dispatched")
            return dispatched
        }
        return false
    }

    fun tapAt(x: Float, y: Float, durationMs: Long = 60): Boolean {
        val service = DnsAutomationService.instance
        if (service == null) {
            LogExporter.log("[NodeHelper] tapAt ($x, $y) failed: DnsAutomationService.instance is null")
            return false
        }
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        LogExporter.log("[NodeHelper] dispatchGesture tap at ($x, $y) -> $dispatched")
        return dispatched
    }

    fun performClick(node: AccessibilityNodeInfo?): Boolean = tapNode(node)

    fun scrollDown(root: AccessibilityNodeInfo?): Boolean {
        val nativeScrolled = findScrollableNode(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
        val gestureScrolled = swipeUp(durationMs = 350)
        LogExporter.log("[NodeHelper] scrollDown: native=$nativeScrolled, physicalSwipe=$gestureScrolled")
        return gestureScrolled || nativeScrolled
    }

    fun scrollForward(root: AccessibilityNodeInfo? = null): Boolean = scrollDown(root)

    fun swipeUp(
        fromX: Float? = null,
        fromY: Float? = null,
        toY: Float? = null,
        durationMs: Long = 350
    ): Boolean {
        val service = DnsAutomationService.instance
        if (service == null) {
            LogExporter.log("[NodeHelper] swipeUp failed: service instance is null")
            return false
        }
        val (width, height) = getScreenDimensions()
        val sx = fromX ?: (width * 0.5f)
        val sy = fromY ?: (height * 0.75f)
        val ey = toY ?: (height * 0.20f)

        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(sx, ey)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        LogExporter.log("[NodeHelper] swipeUp gesture from ($sx, $sy) to ($sx, $ey) duration=${durationMs}ms -> $dispatched (screen: ${width}x$height)")
        return dispatched
    }

    fun swipeDown(
        fromX: Float? = null,
        fromY: Float? = null,
        toY: Float? = null,
        durationMs: Long = 350
    ): Boolean {
        val service = DnsAutomationService.instance
        if (service == null) {
            LogExporter.log("[NodeHelper] swipeDown failed: service instance is null")
            return false
        }
        val (width, height) = getScreenDimensions()
        val sx = fromX ?: (width * 0.5f)
        val sy = fromY ?: (height * 0.20f)
        val ey = toY ?: (height * 0.75f)

        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(sx, ey)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        LogExporter.log("[NodeHelper] swipeDown gesture from ($sx, $sy) to ($sx, $ey) duration=${durationMs}ms -> $dispatched")
        return dispatched
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) {
            LogExporter.log("[NodeHelper] setText failed: node is null")
            return false
        }

        val target = if (node.isEditable) node else findFirstEditableNode(node) ?: node
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        val nodeDesc = "'${target.viewIdResourceName ?: target.text}'"
        LogExporter.log("[NodeHelper] setText '$text' on $nodeDesc -> $success")
        return success
    }
}
