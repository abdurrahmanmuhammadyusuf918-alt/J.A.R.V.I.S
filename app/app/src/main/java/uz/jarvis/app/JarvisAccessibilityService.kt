package uz.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Boshqa ilovalar ekranini o'qiydi va ularda bosish / yozish / aylantirish amallarini bajaradi.
 * Faqat foydalanuvchi sozlamalarda yoqqanda ishlaydi.
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
    }

    class Snapshot(
        val pkg: String,
        val text: String,
        val nodes: Map<Int, AccessibilityNodeInfo>,
        val labels: Map<Int, String>,
        val scrollable: AccessibilityNodeInfo?,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Hozirgi ekrandagi ko'rinadigan elementlarning ixcham ro'yxati. */
    fun snapshot(): Snapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg == packageName) return null // Jarvisning o'z paneli/ilovasi

        val sb = StringBuilder()
        val nodes = LinkedHashMap<Int, AccessibilityNodeInfo>()
        val labels = HashMap<Int, String>()
        var scrollable: AccessibilityNodeInfo? = null
        var next = 1

        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (next > 120 || depth > 25) return
            if (n.isVisibleToUser) {
                val text = n.text?.toString()?.replace('\n', ' ')?.take(80).orEmpty()
                val desc = n.contentDescription?.toString()?.replace('\n', ' ')?.take(80).orEmpty()
                val hint = if (android.os.Build.VERSION.SDK_INT >= 26)
                    n.hintText?.toString()?.take(60).orEmpty() else ""
                val editable = n.isEditable
                val useful = text.isNotBlank() || desc.isNotBlank() || hint.isNotBlank() ||
                    n.isClickable || editable || n.isScrollable
                if (useful) {
                    val id = next++
                    nodes[id] = n
                    if (n.isScrollable && scrollable == null) scrollable = n
                    val cls = n.className?.toString()?.substringAfterLast('.').orEmpty()
                    val flags = buildList {
                        if (n.isClickable) add("click")
                        if (editable) add("edit")
                        if (n.isScrollable) add("scroll")
                        if (n.isFocused) add("focused")
                    }.joinToString(",")
                    val label = listOf(text, desc, hint).firstOrNull { it.isNotBlank() } ?: cls
                    labels[id] = label
                    sb.append('[').append(id).append("] ").append(cls)
                    if (text.isNotBlank()) sb.append(" \"").append(text).append('"')
                    if (desc.isNotBlank() && desc != text) sb.append(" desc=\"").append(desc).append('"')
                    if (hint.isNotBlank()) sb.append(" hint=\"").append(hint).append('"')
                    if (flags.isNotEmpty()) sb.append(" {").append(flags).append('}')
                    sb.append('\n')
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        if (nodes.isEmpty()) return null
        return Snapshot(pkg, sb.toString(), nodes, labels, scrollable)
    }

    fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        return tap(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun type(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(node: AccessibilityNodeInfo?, down: Boolean): Boolean {
        val target = node ?: return false
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return target.performAction(action)
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
