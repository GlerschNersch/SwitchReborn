package dev.switchreborn.home

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.Button
import kotlin.math.roundToInt

/**
 * Screen-reader support for a launcher drawn entirely on one Canvas view. Without it TalkBack
 * sees a single blank view. The host view describes what is on screen right now as a list of
 * [Item]s (label, bounds in view pixels, what tapping does); this class turns that into the
 * framework accessibility tree (no AndroidX), handles accessibility focus, click and
 * long-click, page scrolling on the host, touch exploration, and the events readers need.
 *
 * Shared by the Reborn launchers (one copy per app, since they have no common module).
 */
class VirtualA11y(
    private val host: View,
    /** What is on screen now, in reading order. Ids must be stable while an item stays put. */
    private val items: () -> List<Item>,
    /** Spoken state of the screen as a whole ("All Software, 120 apps"), or null. */
    private val hostState: () -> String? = { null },
    /** Scroll the host one page; true if it moved. Null when that direction can't scroll now. */
    private val scrollForward: () -> (() -> Boolean)? = { null },
    private val scrollBackward: () -> (() -> Boolean)? = { null },
    /** Accessibility focus landed on an item: move the launcher's own highlight there. */
    private val onFocus: (Item) -> Unit = {},
) : AccessibilityNodeProvider() {

    class Item(
        val id: Int,
        val label: String,
        /** Where it is drawn, in the host view's coordinates. */
        val bounds: RectF,
        val click: (() -> Unit)? = null,
        val longClick: (() -> Unit)? = null,
        /** What a long press does, spoken as the action name ("options"). */
        val longClickLabel: String? = null,
        val selected: Boolean = false,
        val heading: Boolean = false,
        val state: String? = null,
    )

    private val am = host.context.getSystemService(AccessibilityManager::class.java)
    private var focused = NONE
    private var hovered = NONE

    val enabled get() = am.isEnabled

    private fun visible(): List<Item> {
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        return items().filter { it.bounds.right > 0f && it.bounds.left < w && it.bounds.bottom > 0f && it.bounds.top < h }
    }

    private fun find(id: Int) = visible().firstOrNull { it.id == id }

    private fun screenRect(b: RectF): Rect {
        val loc = IntArray(2)
        host.getLocationOnScreen(loc)
        // Clamp to the host so half-scrolled items don't report bounds off the display.
        val l = b.left.coerceAtLeast(0f); val t = b.top.coerceAtLeast(0f)
        val r = b.right.coerceAtMost(host.width.toFloat()); val btm = b.bottom.coerceAtMost(host.height.toFloat())
        return Rect(loc[0] + l.roundToInt(), loc[1] + t.roundToInt(), loc[0] + r.roundToInt(), loc[1] + btm.roundToInt())
    }

    override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
        if (id == HOST_VIEW_ID) {
            val info = AccessibilityNodeInfo(host)
            host.onInitializeAccessibilityNodeInfo(info)
            for (item in visible()) info.addChild(host, item.id)
            hostState()?.let { info.stateDescription = it }
            val fwd = scrollForward()
            val back = scrollBackward()
            info.isScrollable = fwd != null || back != null
            if (fwd != null) info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD)
            if (back != null) info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD)
            return info
        }
        val item = find(id) ?: return null
        val n = AccessibilityNodeInfo(host, id)
        n.setParent(host)
        n.packageName = host.context.packageName
        n.className = if (item.click != null) Button::class.java.name else View::class.java.name
        n.contentDescription = item.label
        n.setBoundsInScreen(screenRect(item.bounds))
        n.isVisibleToUser = true
        n.isEnabled = true
        n.isFocusable = true
        n.isSelected = item.selected
        n.isHeading = item.heading
        item.state?.let { n.stateDescription = it }
        if (item.click != null) {
            n.isClickable = true
            n.addAction(AccessibilityAction.ACTION_CLICK)
        }
        if (item.longClick != null) {
            n.isLongClickable = true
            n.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, item.longClickLabel))
        }
        n.isAccessibilityFocused = focused == id
        n.addAction(if (focused == id) AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS else AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
        return n
    }

    override fun performAction(id: Int, action: Int, args: Bundle?): Boolean {
        if (id == HOST_VIEW_ID) {
            val run = when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> scrollForward()
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> scrollBackward()
                else -> return host.performAccessibilityAction(action, args)
            } ?: return false
            if (!run()) return false
            treeChanged()
            // Put the reader on the first item of what scrolled in.
            visible().firstOrNull { it.click != null }?.let { focus(it) }
            return true
        }
        val item = find(id) ?: return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> if (focused == id) false else { focus(item); true }
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                if (focused != id) return false
                focused = NONE
                send(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                true
            }
            AccessibilityNodeInfo.ACTION_CLICK -> {
                val run = item.click ?: return false
                run()
                send(id, AccessibilityEvent.TYPE_VIEW_CLICKED)
                true
            }
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                val run = item.longClick ?: return false
                run()
                send(id, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
                true
            }
            else -> false
        }
    }

    override fun findFocus(focus: Int): AccessibilityNodeInfo? =
        if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && focused != NONE) createAccessibilityNodeInfo(focused) else null

    private fun focus(item: Item) {
        val old = focused
        focused = item.id
        if (old != NONE && old != item.id) send(old, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
        onFocus(item)
        host.invalidate()
        send(item.id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
    }

    /** What is on screen changed (new page, popup, reload): readers re-read the tree. */
    fun treeChanged() {
        if (!am.isEnabled) return
        if (focused != NONE && find(focused) == null) focused = NONE
        send(HOST_VIEW_ID, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)
    }

    /** The launcher's own cursor moved (d-pad, gamepad) to item [id]: announce it. */
    fun cursorMoved(id: Int) {
        if (!am.isEnabled || find(id) == null) return
        send(id, AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    /** Call from the host's dispatchHoverEvent; true when touch exploration consumed it. */
    fun hover(e: MotionEvent): Boolean {
        if (!am.isEnabled || !am.isTouchExplorationEnabled) return false
        val id = when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                visible().lastOrNull { it.bounds.contains(e.x, e.y) }?.id ?: NONE
            else -> NONE
        }
        if (id != hovered) {
            val old = hovered
            hovered = id
            if (id != NONE) send(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            if (old != NONE) send(old, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
        }
        return true
    }

    private fun send(id: Int, type: Int, changeTypes: Int = 0) {
        if (!am.isEnabled) return
        val parent = host.parent ?: return
        val ev = AccessibilityEvent(type)
        ev.packageName = host.context.packageName
        if (id == HOST_VIEW_ID) {
            ev.setSource(host)
            ev.className = host.javaClass.name
        } else {
            ev.setSource(host, id)
            val item = find(id)
            ev.className = if (item?.click != null) Button::class.java.name else View::class.java.name
            ev.contentDescription = item?.label
        }
        if (changeTypes != 0) ev.contentChangeTypes = changeTypes
        parent.requestSendAccessibilityEvent(host, ev)
    }

    companion object {
        const val NONE = Int.MIN_VALUE
    }
}
