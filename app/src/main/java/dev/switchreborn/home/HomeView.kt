package dev.switchreborn.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.OverScroller
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** One row of the options popup. */
class Option(val label: String, val run: () -> Unit)

/**
 * The whole HOME menu in one view: status row, software row, round buttons, hint bar, the
 * All Software grid and the options popup. Everything is laid out in 1920x1080 reference px
 * (see [Metrics]) inside a virtual frame of `VW x VH` at scale [s]: landscape phones get extra
 * width, portrait gets extra height, and the row/button block floats in the middle.
 */
@SuppressLint("ViewConstructor")
class HomeView(
    context: Context,
    private val pal: Palette,
    private val sfx: Sfx,
    private val onLaunch: (Launch, Tile?) -> Unit,
    private val optionsFor: (Tile) -> List<Option>,
    renderArt: (Tile, Int) -> Bitmap?,
) : View(context) {

    private val artCache = ArtCache(renderArt) { invalidate() }

    private var row: List<Tile> = emptyList()
    private var apps: List<Tile> = emptyList()
    private var buttons: List<RoundButton> = emptyList()
    var userName = "User"
    var userLaunch: Launch? = null
    var battery = -1
    var charging = false
    var wifi = false

    // Focus: which strip owns the cursor, and where it sits in each
    private var zone = ZONE_ROW
    private var rowSel = 0
    private var btnSel = 0
    private var gridSel = 0
    private var selectedAt = 0L

    // Motion
    private val start = SystemClock.uptimeMillis()
    private var now = 0L
    private var lastFrame = 0L
    private var running = false
    private val scroll = Tween(0f, Metrics.SCROLL_MS)
    private val gridScroll = Tween(0f, Metrics.SCROLL_MS)
    private val allMode = Tween(0f, Metrics.MODE_MS)
    private val popup = Tween(0f, Metrics.MODE_MS)
    private val press = Blip(Metrics.PRESS_MS * 2)
    private val bump = Blip(Metrics.BUMP_MS)
    private var bumpDx = 0f
    private var bumpDy = 0f
    private val scroller = OverScroller(context)
    private var flingGrid = false

    // Popup
    private var popTitle = ""
    private var options: List<Option> = emptyList()
    private var popSel = 0

    // Frame mapping: draw area (cutouts and nav bar removed), scale, virtual size
    private var ax = 0f
    private var ay = 0f
    private var aw = 1f
    private var ah = 1f
    private var s = 1f
    private var vw = Metrics.REF_W
    private var vh = Metrics.REF_H
    private var insetL = 0
    private var insetT = 0
    private var insetR = 0
    private var insetB = 0

    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val frameRect = RectF()
    private val hints = ArrayList<Pair<RectF, () -> Unit>>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setContent(row: List<Tile>, apps: List<Tile>, buttons: List<RoundButton>) {
        // Keep the cursor on the same software (or on All Software) across reloads.
        val onAll = rowSel == this.row.size && this.row.isNotEmpty()
        val keep = this.row.getOrNull(rowSel)?.id
        this.row = row
        this.apps = apps
        this.buttons = buttons
        rowSel = if (onAll) row.size else row.indexOfFirst { it.id == keep }.coerceAtLeast(0)
        btnSel = btnSel.coerceIn(0, maxOf(0, buttons.size - 1))
        gridSel = gridSel.coerceIn(0, maxOf(0, apps.size - 1))
        scroll.snapTo(RowLayout.scrollFor(rowSel, row.size, vw, scroll.value))
        invalidate()
    }

    /** Back out one level; false when already at the top of HOME. */
    fun back(): Boolean = when {
        popup.target > 0f -> { popup.animateTo(0f); sfx.play(Sfx.Kind.BACK); invalidate(); true }
        allMode.target > 0f -> { allMode.animateTo(0f); sfx.play(Sfx.Kind.BACK); invalidate(); true }
        zone != ZONE_ROW -> { zone = ZONE_ROW; selectedAt = now; invalidate(); true }
        else -> false
    }

    /** Home pressed while home: close everything and return to the first software. */
    fun home() {
        popup.animateTo(0f); allMode.animateTo(0f)
        zone = ZONE_ROW; rowSel = 0; selectedAt = now
        scroll.animateTo(0f)
        invalidate()
    }

    fun resume() { running = true; lastFrame = 0L; invalidate() }
    fun pause() { running = false }

    fun showOptions(title: String, list: List<Option>) {
        if (list.isEmpty()) return
        popTitle = title
        options = list
        popSel = 0
        popup.animateTo(1f)
        sfx.play(Sfx.Kind.OPEN)
        invalidate()
    }

    // ---------------------------------------------------------------- layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = relayout()

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val cut = insets.displayCutout
        val nav = insets.getInsets(WindowInsets.Type.navigationBars())
        insetL = maxOf(cut?.safeInsetLeft ?: 0, nav.left)
        insetR = maxOf(cut?.safeInsetRight ?: 0, nav.right)
        insetT = cut?.safeInsetTop ?: 0
        insetB = maxOf(cut?.safeInsetBottom ?: 0, nav.bottom)
        relayout()
        return super.onApplyWindowInsets(insets)
    }

    private fun relayout() {
        if (width == 0) return
        ax = insetL.toFloat()
        ay = insetT.toFloat()
        aw = (width - insetL - insetR).toFloat()
        ah = (height - insetT - insetB).toFloat()
        s = minOf(ah / Metrics.REF_H, aw / Metrics.MIN_W)
        vw = aw / s
        vh = ah / s
        scroll.snapTo(RowLayout.scrollFor(rowSel, row.size, vw, scroll.value))
        gridScroll.snapTo(gridScrollFor(gridSel, gridScroll.value))
        invalidate()
    }

    override fun onDetachedFromWindow() {
        artCache.shutdown()
        super.onDetachedFromWindow()
    }

    private val dm get() = resources.displayMetrics
    /** Reference text size, floored at [minSp] so console text stays legible on a phone. */
    private fun sp(ref: Float, minSp: Float) = maxOf(ref * s, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, minSp, dm))

    /** Screen x for a left-anchored reference x. */
    private fun X(x: Float) = ax + x * s
    /** Screen x for an x measured from the frame's right edge (status row, hints). */
    private fun XR(x: Float) = ax + (x + vw - Metrics.REF_W) * s
    /** Screen y for the status row (top-anchored). */
    private fun T(y: Float) = ay + y * s
    /** Screen y for the row/button block, which floats in the middle of tall screens. */
    private fun M(y: Float) = ay + (y + (vh - Metrics.REF_H) / 2f) * s
    /** Screen y for the hint bar (bottom-anchored). */
    private fun B(y: Float) = ay + (y + vh - Metrics.REF_H) * s
    private fun btnCx(i: Int) = vw / 2f + (i - (buttons.size - 1) / 2f) * Metrics.BTN_PITCH

    // ---------------------------------------------------------------- drawing

    override fun onDraw(c: Canvas) {
        now = SystemClock.uptimeMillis() - start
        val t = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (t - lastFrame).coerceIn(0, 50).toFloat()
        lastFrame = t
        step(dt)

        c.drawColor(pal.bg)
        hints.clear()
        val all = allMode.value
        if (all < 1f) {
            val layer = if (all > 0f) c.saveLayerAlpha(null, ((1f - all) * 255).toInt()) else -1
            drawHome(c)
            if (layer >= 0) c.restoreToCount(layer)
        }
        if (all > 0f) {
            val layer = c.saveLayerAlpha(null, (all * 255).toInt())
            drawAllSoftware(c)
            c.restoreToCount(layer)
        }
        drawHintBar(c)
        if (popup.value > 0f) drawPopup(c)
        if (!running) return
        removeCallbacks(idleTick)
        // The highlight pulses forever, so idle still redraws, just at half rate.
        if (animating()) postInvalidateOnAnimation() else postDelayed(idleTick, IDLE_FRAME_MS)
    }

    private val idleTick = Runnable { invalidate() }

    private fun animating() = touching || !scroll.settled || !gridScroll.settled || !allMode.settled ||
        !popup.settled || press.active || bump.active || !scroller.isFinished

    private fun step(dt: Float) {
        scroll.step(dt); gridScroll.step(dt); allMode.step(dt); popup.step(dt)
        press.step(dt); bump.step(dt)
        if (scroller.computeScrollOffset()) {
            if (flingGrid) gridScroll.snapTo(scroller.currY / s) else scroll.snapTo(scroller.currX / s)
        }
    }

    private fun pulseColor() = lerpColor(pal.pulseLo, pal.pulseHi, pulse(now))

    private fun drawHome(c: Canvas) {
        drawStatus(c)
        drawRow(c)
        drawButtons(c)
    }

    private fun drawStatus(c: Canvas) {
        // Avatar: the phone's owner, as an initial on a coloured disc
        val acx = X(Metrics.AVATAR_X); val acy = T(Metrics.AVATAR_Y); val ar = Metrics.AVATAR_D / 2 * s
        fill.color = if (pal.dark) 0xFF1E1F1D.toInt() else 0xFFD5D5D5.toInt()
        c.drawCircle(acx, acy, ar, fill)
        fill.color = AVATAR_COLOR
        c.drawCircle(acx, acy, ar - Metrics.AVATAR_RING * s, fill)
        text.typeface = medium; text.textSize = ar * 1.0f; text.color = 0xFFFFFFFF.toInt(); text.textAlign = Paint.Align.CENTER
        c.drawText(userName.take(1).uppercase(), acx, acy + text.textSize * 0.36f, text)
        if (zone == ZONE_USER && all0()) {
            stroke.color = pulseColor(); stroke.strokeWidth = Metrics.RING_STROKE * s
            c.drawCircle(acx, acy, ar + (5 + Metrics.RING_STROKE / 2) * s, stroke)
            text.typeface = regular; text.textSize = sp(Metrics.BTN_LABEL_SIZE, 12f); text.color = pal.title; text.textAlign = Paint.Align.LEFT
            c.drawText("$userName's Page", X(Metrics.AVATAR_X - Metrics.AVATAR_D / 2), T(Metrics.PAGE_LABEL_Y), text)
        }

        // Right to left: battery, percentage, Wi-Fi, clock
        var x = XR(Metrics.STATUS_RIGHT)
        val base = T(Metrics.STATUS_BASELINE)
        val bh = Metrics.BATTERY_H * s; val bw = Metrics.BATTERY_W * s
        val btop = T(Metrics.AVATAR_Y + 1) - bh / 2
        val nub = 4 * s
        stroke.color = pal.text; stroke.strokeWidth = 3 * s
        rect.set(x - bw + 1.5f * s, btop + 1.5f * s, x - nub - 1.5f * s, btop + bh - 1.5f * s)
        c.drawRoundRect(rect, 3 * s, 3 * s, stroke)
        fill.color = pal.text
        rect.set(x - nub, btop + bh * 0.3f, x, btop + bh * 0.7f); c.drawRect(rect, fill)
        val level = if (battery < 0) 100 else battery
        fill.color = when { charging -> 0xFF5BD83A.toInt(); level <= 15 -> 0xFFE8463C.toInt(); else -> pal.text }
        val inner = (bw - nub - 9 * s)
        rect.set(x - bw + 4.5f * s, btop + 4.5f * s, x - bw + 4.5f * s + inner * level / 100f, btop + bh - 4.5f * s)
        c.drawRect(rect, fill)
        x -= bw + 10 * s

        text.typeface = regular; text.color = pal.text; text.textAlign = Paint.Align.RIGHT
        if (battery >= 0) {
            text.textSize = sp(Metrics.AMPM_SIZE, 10f)
            c.drawText("%", x, base, text); x -= text.measureText("%") + 1 * s
            text.textSize = sp(Metrics.CLOCK_SIZE * 0.8f, 13f)
            c.drawText("$battery", x, base, text); x -= text.measureText("$battery")
        }
        x -= 37 * s
        Glyphs.draw(c, Glyph.WIFI, x - Metrics.WIFI_W / 2 * s, T(Metrics.AVATAR_Y - 4), Metrics.WIFI_W * s * 1.25f,
            if (wifi) pal.text else (pal.text and 0x00FFFFFF) or 0x50000000)
        x -= Metrics.WIFI_W * s + 35 * s

        val cal = Calendar.getInstance()
        val h24 = android.text.format.DateFormat.is24HourFormat(context)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val mins = "%02d".format(cal.get(Calendar.MINUTE))
        if (!h24) {
            text.textSize = sp(Metrics.AMPM_SIZE, 10f); text.typeface = medium
            val ampm = if (hour < 12) "AM" else "PM"
            c.drawText(ampm, x, base, text); x -= text.measureText(ampm) + 4 * s
        }
        text.typeface = regular; text.textSize = sp(Metrics.CLOCK_SIZE, 14f)
        val shown = if (h24) "%d:%s".format(hour, mins) else "%d:%s".format(if (hour % 12 == 0) 12 else hour % 12, mins)
        text.letterSpacing = 0.06f
        c.drawText(shown, x, base, text)
        text.letterSpacing = 0f
    }

    private fun all0() = allMode.target == 0f

    private fun drawRow(c: Canvas) {
        val n = row.size
        val sc = scroll.value
        val top = M(Metrics.TILE_TOP)
        val size = Metrics.TILE * s
        val artPx = ceil(size).toInt()
        val selActive = zone == ZONE_ROW
        for (i in 0..n) {
            val left = X(RowLayout.slotX(i) - sc)
            val w = RowLayout.slotW(i, n) * s
            if (left > width || left + w < 0) continue
            val selected = selActive && i == rowSel
            val k = if (selected) 1f - 0.035f * press.value else 1f
            val dx = if (selected) bumpDx * bump.value * s else 0f
            c.save()
            c.translate(dx, 0f)
            if (i == n) {
                val cx = left + w / 2; val cy = top + size / 2; val r = Metrics.ALL_D / 2 * s * k
                drawDisc(c, cx, cy, r, pal.button)
                Glyphs.draw(c, Glyph.ALL_SOFTWARE, cx, cy, 96 * s, pal.icon)
                if (selected) ring(c, cx, cy, r)
            } else {
                val cx = left + size / 2; val cy = top + size / 2
                val half = size / 2 * k
                rect.set(cx - half, cy - half, cx + half, cy + half)
                if (selected) frame(c, rect)
                val art = row[i].art?.takeIf { it.width >= artPx * 0.9f }
                    ?: artCache.get(row[i], artPx) ?: row[i].art
                if (art != null) c.drawBitmap(art, null, rect, bmpPaint)
                else { fill.color = pal.emptySlot; c.drawRect(rect, fill) }
            }
            c.restore()
        }
        if (selActive) drawRowTitle(c)
    }

    /** The pulsing selection frame: stroke, then a gap, then the art sits inside. */
    private fun frame(c: Canvas, art: RectF) {
        val out = Metrics.FRAME_OUT * s
        val sw = Metrics.FRAME_STROKE * s
        fill.color = pulseColor()
        frameRect.set(art.left - out, art.top - out, art.right + out, art.bottom + out)
        c.drawRoundRect(frameRect, Metrics.FRAME_RADIUS * s, Metrics.FRAME_RADIUS * s, fill)
        fill.color = pal.frameGap
        frameRect.inset(sw, sw)
        c.drawRect(frameRect, fill)
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float) {
        stroke.color = pulseColor(); stroke.strokeWidth = Metrics.RING_STROKE * s
        c.drawCircle(cx, cy, r + (Metrics.RING_R - Metrics.BTN_D / 2 - Metrics.RING_STROKE / 2) * s, stroke)
    }

    private fun drawDisc(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        if (pal.buttonShadow != 0) {
            fill.color = pal.buttonShadow
            c.drawCircle(cx, cy + 3 * s, r + 1 * s, fill)
        }
        fill.color = color
        c.drawCircle(cx, cy, r, fill)
    }

    private fun drawRowTitle(c: Canvas) {
        val n = row.size
        val isAll = rowSel == n
        val title = if (isAll) "All Software" else row.getOrNull(rowSel)?.title ?: return
        val left = RowLayout.slotX(rowSel) - scroll.value + bumpDx * bump.value - (Metrics.TILE_X - Metrics.BADGE_X)
        val badgeL = X(left)
        val textL = if (isAll) badgeL else X(left + (Metrics.TITLE_X - Metrics.BADGE_X))
        val base = M(Metrics.TITLE_BASELINE)
        if (!isAll) {
            Glyphs.draw(c, Glyph.BADGE, badgeL + Metrics.BADGE_W / 2 * s,
                M(Metrics.BADGE_TOP + Metrics.BADGE_H / 2), Metrics.BADGE_H * s * 64f / 46f, pal.title, pal.bg)
        }
        text.typeface = regular; text.textSize = sp(Metrics.TITLE_SIZE, 16f); text.color = pal.title; text.textAlign = Paint.Align.LEFT
        val clipR = X(vw - 80f)
        val avail = clipR - textL
        val tw = text.measureText(title)
        if (tw <= avail) { c.drawText(title, textL, base, text); return }
        // Too long: hold, then marquee leftward with the title repeated after a gap
        val loop = tw + Metrics.MARQUEE_GAP * s
        val run = (now - selectedAt - Metrics.MARQUEE_HOLD_MS).coerceAtLeast(0f) / 1000f * Metrics.MARQUEE_PX_PER_S * s
        val off = run % loop
        c.save()
        c.clipRect(textL, base - text.textSize * 1.2f, clipR, base + text.textSize * 0.4f)
        c.drawText(title, textL - off, base, text)
        c.drawText(title, textL - off + loop, base, text)
        c.restore()
    }

    private fun drawButtons(c: Canvas) {
        val cy = M(Metrics.BTN_Y)
        val r = Metrics.BTN_D / 2 * s
        for ((i, b) in buttons.withIndex()) {
            val selected = zone == ZONE_BUTTONS && i == btnSel
            val k = if (selected) 1f - 0.05f * press.value else 1f
            val cx = X(btnCx(i)) + if (selected) bumpDx * bump.value * s else 0f
            drawDisc(c, cx, cy, r * k, if (b.solid) pal.online else pal.button)
            val color = when {
                b.solid -> 0xFFFFFFFF.toInt()
                b.glyph == Glyph.NEWS -> pal.news
                b.glyph == Glyph.SHOP -> pal.shop
                b.glyph == Glyph.ALBUM -> pal.album
                else -> pal.icon
            }
            Glyphs.draw(c, b.glyph, cx, cy, Metrics.BTN_ICON * s * k, color)
            if (selected) {
                ring(c, cx, cy, r)
                text.typeface = regular; text.textSize = sp(Metrics.BTN_LABEL_SIZE, 12f); text.color = pal.title
                text.textAlign = Paint.Align.CENTER
                c.drawText(b.label, cx, M(Metrics.BTN_LABEL_BASELINE), text)
            }
        }
    }

    private fun drawHintBar(c: Canvas) {
        fill.color = pal.rule
        val ry = B(Metrics.RULE_Y)
        c.drawRect(X(Metrics.RULE_INSET), ry, X(vw - Metrics.RULE_INSET - 2), ry + maxOf(1f, Metrics.RULE_H * s), fill)
        // Handheld: one controller, player 1 lit
        val cy = B(Metrics.HINT_Y)
        Glyphs.draw(c, Glyph.GAMEPAD, X(Metrics.CONTROLLER_X), cy, Metrics.CONTROLLER_W * s, pal.text, pal.bg)
        for (i in 0 until 4) {
            fill.color = if (i == 0) pal.ledOn else pal.ledOff
            val lx = X(Metrics.CONTROLLER_X - 31 + i * 15)
            c.drawRect(lx, B(Metrics.LED_Y - Metrics.LED / 2), lx + Metrics.LED * s, B(Metrics.LED_Y + Metrics.LED / 2), fill)
        }

        val list = ArrayList<Triple<String, String, () -> Unit>>()
        when {
            popup.target > 0f -> { list += Triple("B", "Back") { back() }; list += Triple("A", "OK") { confirm() } }
            allMode.target > 0f -> {
                list += Triple("B", "Back") { back() }
                if (apps.isNotEmpty()) { list += Triple("+", "Options") { openOptions() }; list += Triple("A", "Start") { confirm() } }
            }
            zone == ZONE_ROW && rowSel < row.size -> { list += Triple("+", "Options") { openOptions() }; list += Triple("A", "Start") { confirm() } }
            else -> list += Triple("A", "OK") { confirm() }
        }
        text.typeface = regular; text.textSize = sp(Metrics.HINT_SIZE, 13f); text.color = pal.text; text.textAlign = Paint.Align.RIGHT
        var x = XR(Metrics.HINT_RIGHT)
        val d = Metrics.HINT_CIRCLE * s
        for ((key, label, run) in list.asReversed()) {
            val right = x
            c.drawText(label, x, cy + text.textSize * 0.36f, text)
            x -= text.measureText(label) + Metrics.HINT_LABEL_GAP * s
            if (key == "+") Glyphs.draw(c, Glyph.PLUS, x - d / 2, cy, d, pal.text, pal.bg)
            else {
                fill.color = pal.text; c.drawCircle(x - d / 2, cy, d / 2, fill)
                val keep = text.textSize
                text.typeface = medium; text.textSize = d * 0.66f; text.color = pal.bg; text.textAlign = Paint.Align.CENTER
                c.drawText(key, x - d / 2, cy + text.textSize * 0.36f, text)
                text.typeface = regular; text.textSize = keep; text.color = pal.text; text.textAlign = Paint.Align.RIGHT
            }
            x -= d
            hints += RectF(x - 10 * s, cy - 40 * s, right + 10 * s, cy + 40 * s) to run
            x -= Metrics.HINT_GAP * s
        }
    }

    // ---------------------------------------------------------------- All Software

    private fun gridCols() = maxOf(2, floor((vw - 2 * Metrics.ALL_TITLE_X + (Metrics.GRID_PITCH - Metrics.GRID_TILE)) / Metrics.GRID_PITCH).toInt())
    private fun gridLeft() = (vw - (gridCols() * Metrics.GRID_PITCH - (Metrics.GRID_PITCH - Metrics.GRID_TILE))) / 2f
    /** Visible band for grid rows, in reference px from the top of the frame. */
    private fun gridBottom() = vh - (Metrics.REF_H - Metrics.RULE_Y) - 30f

    private fun gridScrollFor(sel: Int, current: Float): Float {
        val r = sel / gridCols()
        val top = Metrics.GRID_TOP + r * Metrics.GRID_PITCH
        val maxRows = ceil(apps.size / gridCols().toFloat()).toInt()
        val maxS = maxOf(0f, Metrics.GRID_TOP + maxRows * Metrics.GRID_PITCH - gridBottom())
        return current.coerceIn(top + Metrics.GRID_TILE - gridBottom(), top - Metrics.GRID_TOP).coerceIn(0f, maxS)
    }

    private fun drawAllSoftware(c: Canvas) {
        text.typeface = regular; text.textSize = sp(Metrics.ALL_TITLE_SIZE, 16f); text.color = pal.text; text.textAlign = Paint.Align.LEFT
        c.drawText("All Software", X(Metrics.ALL_TITLE_X), T(Metrics.ALL_TITLE_BASELINE), text)
        text.textSize = sp(Metrics.ALL_TITLE_SIZE * 0.7f, 12f); text.color = pal.subtext; text.textAlign = Paint.Align.RIGHT
        c.drawText("${apps.size}", XR(Metrics.REF_W - Metrics.ALL_TITLE_X), T(Metrics.ALL_TITLE_BASELINE), text)
        fill.color = pal.rule
        c.drawRect(X(Metrics.RULE_INSET), T(Metrics.ALL_RULE_Y), X(vw - Metrics.RULE_INSET), T(Metrics.ALL_RULE_Y) + maxOf(1f, 2 * s), fill)

        apps.getOrNull(gridSel)?.let {
            text.textSize = sp(Metrics.BTN_LABEL_SIZE, 13f); text.color = pal.title; text.textAlign = Paint.Align.LEFT
            c.drawText(TextUtils.ellipsize(it.title, text, (vw - 2 * gridLeft()) * s, TextUtils.TruncateAt.END).toString(),
                X(gridLeft()), T(Metrics.GRID_NAME_BASELINE), text)
        }

        val cols = gridCols()
        val gl = gridLeft()
        val size = Metrics.GRID_TILE * s
        val px = ceil(size).toInt()
        c.save()
        c.clipRect(0f, T(Metrics.GRID_TOP - 24), width.toFloat(), T(gridBottom() + 24))
        for ((i, t) in apps.withIndex()) {
            val y = Metrics.GRID_TOP + (i / cols) * Metrics.GRID_PITCH - gridScroll.value
            if (y + Metrics.GRID_TILE < Metrics.GRID_TOP - 40 || y > gridBottom() + 40) continue
            val left = X(gl + (i % cols) * Metrics.GRID_PITCH); val top = T(y)
            val sel = i == gridSel
            val k = if (sel) 1f - 0.035f * press.value else 1f
            val half = size / 2 * k
            rect.set(left + size / 2 - half, top + size / 2 - half, left + size / 2 + half, top + size / 2 + half)
            if (sel) {
                c.save(); c.translate(bumpDx * bump.value * s, bumpDy * bump.value * s)
                frame(c, rect)
            }
            val art = artCache.get(t, px)
            if (art != null) c.drawBitmap(art, null, rect, bmpPaint) else { fill.color = pal.emptySlot; c.drawRect(rect, fill) }
            if (sel) c.restore()
        }
        c.restore()
    }

    // ---------------------------------------------------------------- popup

    private fun popRect(out: RectF) {
        val h = Metrics.POP_TITLE + options.size * Metrics.POP_ROW + 30f
        val w = minOf(Metrics.POP_W, vw - 120f)
        val l = (vw - w) / 2; val t = (vh - h) / 2
        out.set(X(l), T(t), X(l + w), T(t + h))
    }

    private fun drawPopup(c: Canvas) {
        val a = popup.value
        fill.color = pal.scrim; fill.alpha = (((pal.scrim ushr 24) and 0xFF) * a).toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val box = RectF(); popRect(box)
        c.save()
        c.translate(0f, (1f - a) * 40 * s)
        val layer = c.saveLayerAlpha(box.left - 20 * s, box.top - 20 * s, box.right + 20 * s, box.bottom + 20 * s, (a * 255).toInt())
        fill.color = pal.popup; fill.alpha = 255
        c.drawRoundRect(box, 6 * s, 6 * s, fill)
        text.typeface = regular; text.textSize = sp(Metrics.POP_LABEL, 13f); text.color = pal.text; text.textAlign = Paint.Align.LEFT
        c.drawText(TextUtils.ellipsize(popTitle, text, box.width() - 80 * s, TextUtils.TruncateAt.END).toString(),
            box.left + 40 * s, box.top + 68 * s, text)
        fill.color = pal.popupRule
        c.drawRect(box.left + 30 * s, box.top + (Metrics.POP_TITLE - 6) * s, box.right - 30 * s, box.top + (Metrics.POP_TITLE - 4) * s, fill)
        for ((i, o) in options.withIndex()) {
            val top = box.top + (Metrics.POP_TITLE + 10 + i * Metrics.POP_ROW) * s
            rect.set(box.left + 30 * s, top, box.right - 30 * s, top + (Metrics.POP_ROW - 10) * s)
            if (i == popSel) {
                stroke.color = pulseColor(); stroke.strokeWidth = 5 * s
                c.drawRect(rect, stroke)
            }
            text.color = pal.text
            c.drawText(o.label, rect.left + 24 * s, rect.centerY() + text.textSize * 0.36f, text)
        }
        c.restoreToCount(layer)
        c.restore()
    }

    // ---------------------------------------------------------------- input

    private var touching = false
    private var dragAxis = 0

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            dragAxis = 0
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (popup.target > 0f) return true
            if (allMode.target > 0f) {
                val max = gridScrollMax()
                gridScroll.snapTo((gridScroll.value + dy / s).coerceIn(0f, max))
            } else {
                if (dragAxis == 0) dragAxis = if (abs(dx) >= abs(dy)) 1 else 2
                if (dragAxis == 1) scroll.snapTo((scroll.value + dx / s).coerceIn(0f, RowLayout.maxScroll(row.size, vw)))
            }
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (popup.target > 0f) return true
            if (allMode.target > 0f) {
                flingGrid = true
                scroller.fling(0, (gridScroll.value * s).toInt(), 0, (-vy).toInt(), 0, 0, 0, (gridScrollMax() * s).toInt())
            } else if (dragAxis == 1) {
                flingGrid = false
                scroller.fling((scroll.value * s).toInt(), 0, (-vx).toInt(), 0, 0, (RowLayout.maxScroll(row.size, vw) * s).toInt(), 0, 0)
            }
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean { tap(e.x, e.y); return true }

        override fun onLongPress(e: MotionEvent) {
            if (popup.target > 0f) return
            if (allMode.target > 0f) {
                val i = gridHit(e.x, e.y); if (i < 0) return
                gridSel = i
            } else {
                val i = rowHit(e.x, e.y); if (i < 0 || i >= row.size) return
                zone = ZONE_ROW; rowSel = i
            }
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openOptions()
        }
    })

    private fun gridScrollMax(): Float {
        val rows = ceil(apps.size / gridCols().toFloat()).toInt()
        return maxOf(0f, Metrics.GRID_TOP + rows * Metrics.GRID_PITCH - gridBottom())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> touching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        gestures.onTouchEvent(e)
        invalidate()
        return true
    }

    private fun rowHit(x: Float, y: Float): Int {
        val top = M(Metrics.TILE_TOP)
        if (y < top - 20 * s || y > top + (Metrics.TILE + 20) * s) return -1
        return RowLayout.hit((x - ax) / s, scroll.value, row.size)
    }

    private fun gridHit(x: Float, y: Float): Int {
        if (y < T(Metrics.GRID_TOP - 24) || y > T(gridBottom() + 24)) return -1
        val rx = (x - ax) / s - gridLeft(); val ry = (y - ay) / s - Metrics.GRID_TOP + gridScroll.value
        if (rx < 0 || ry < 0) return -1
        val col = (rx / Metrics.GRID_PITCH).toInt(); val r = (ry / Metrics.GRID_PITCH).toInt()
        if (col >= gridCols() || rx - col * Metrics.GRID_PITCH > Metrics.GRID_TILE || ry - r * Metrics.GRID_PITCH > Metrics.GRID_TILE) return -1
        return (r * gridCols() + col).takeIf { it < apps.size } ?: -1
    }

    private fun tap(x: Float, y: Float) {
        hints.firstOrNull { it.first.contains(x, y) }?.let { it.second(); return }
        if (popup.target > 0f) {
            val box = RectF(); popRect(box)
            if (!box.contains(x, y)) { back(); return }
            val i = ((y - box.top) / s - Metrics.POP_TITLE - 10).let { if (it < 0) -1 else (it / Metrics.POP_ROW).toInt() }
            if (i in options.indices) { popSel = i; confirm() }
            return
        }
        if (allMode.target > 0f) {
            val i = gridHit(x, y); if (i < 0) return
            if (i == gridSel) confirm() else { gridSel = i; moved() }
            return
        }
        val i = rowHit(x, y)
        if (i >= 0) {
            // Like the console's touchscreen: the first tap selects, a tap on the selection opens.
            if (zone == ZONE_ROW && i == rowSel) confirm()
            else { zone = ZONE_ROW; rowSel = i; scroll.animateTo(RowLayout.scrollFor(i, row.size, vw, scroll.value)); moved() }
            return
        }
        val by = M(Metrics.BTN_Y)
        if (abs(y - by) < Metrics.RING_R * s) {
            for (b in buttons.indices) if (abs(x - X(btnCx(b))) < Metrics.RING_R * s) {
                zone = ZONE_BUTTONS; btnSel = b; confirm(); return
            }
        }
        if (abs(x - X(Metrics.AVATAR_X)) < Metrics.AVATAR_D * s && abs(y - T(Metrics.AVATAR_Y)) < Metrics.AVATAR_D * s) {
            zone = ZONE_USER; confirm()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { move(-1, 0); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { move(1, 0); true }
        KeyEvent.KEYCODE_DPAD_UP -> { move(0, -1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { move(0, 1); true }
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { confirm(); true }
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> { back(); true }
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_X -> { openOptions(); true }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun moved() {
        selectedAt = now
        sfx.play(Sfx.Kind.MOVE)
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    /** Cursor hit an edge: the selection nudges toward it and springs back. */
    private fun bumped(dx: Int, dy: Int) {
        bumpDx = dx * Metrics.BUMP_PX; bumpDy = dy * Metrics.BUMP_PX
        bump.fire()
        sfx.play(Sfx.Kind.BUMP)
        invalidate()
    }

    private fun move(dx: Int, dy: Int) {
        if (popup.target > 0f) {
            if (dy == 0) return
            val to = popSel + dy
            if (to in options.indices) { popSel = to; moved() } else bumped(0, dy)
            return
        }
        if (allMode.target > 0f) {
            val cols = gridCols()
            val to = gridSel + dx + dy * cols
            val ok = when {
                dx != 0 -> to in apps.indices && to / cols == gridSel / cols
                else -> to in apps.indices || (dy > 0 && gridSel / cols < (apps.size - 1) / cols)
            }
            if (!ok) { bumped(dx, dy); return }
            gridSel = to.coerceAtMost(apps.size - 1)
            gridScroll.animateTo(gridScrollFor(gridSel, gridScroll.target))
            moved(); return
        }
        when (zone) {
            ZONE_USER -> when {
                dy > 0 -> { zone = ZONE_ROW; moved() }
                else -> bumped(dx, dy)
            }
            ZONE_ROW -> when {
                dy < 0 -> { zone = ZONE_USER; moved() }
                dy > 0 -> {
                    if (buttons.isEmpty()) { bumped(0, 1); return }
                    // Land on the button nearest the selection's screen position
                    val sx = RowLayout.slotX(rowSel) - scroll.value + RowLayout.slotW(rowSel, row.size) / 2
                    btnSel = buttons.indices.minByOrNull { abs(btnCx(it) - sx) } ?: 0
                    zone = ZONE_BUTTONS; moved()
                }
                else -> {
                    val to = rowSel + dx
                    if (to !in 0..row.size) { bumped(dx, 0); return }
                    rowSel = to
                    scroll.animateTo(RowLayout.scrollFor(to, row.size, vw, scroll.target))
                    moved()
                }
            }
            ZONE_BUTTONS -> when {
                dy < 0 -> { zone = ZONE_ROW; moved() }
                dy > 0 -> bumped(0, 1)
                else -> {
                    val to = btnSel + dx
                    if (to !in buttons.indices) bumped(dx, 0) else { btnSel = to; moved() }
                }
            }
        }
    }

    private fun confirm() {
        if (popup.target > 0f) {
            val o = options.getOrNull(popSel) ?: return
            popup.animateTo(0f)
            sfx.play(Sfx.Kind.OPEN)
            o.run()
            invalidate(); return
        }
        press.fire()
        sfx.play(Sfx.Kind.OPEN)
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        invalidate()
        if (allMode.target > 0f) {
            val t = apps.getOrNull(gridSel) ?: return
            postDelayed({ onLaunch(t.launch, t) }, Metrics.PRESS_MS.toLong())
            return
        }
        when (zone) {
            ZONE_USER -> userLaunch?.let { l -> postDelayed({ onLaunch(l, null) }, Metrics.PRESS_MS.toLong()) }
            ZONE_ROW -> {
                if (rowSel == row.size) {
                    allMode.animateTo(1f)
                    gridScroll.snapTo(gridScrollFor(gridSel, gridScroll.value))
                    selectedAt = now
                    return
                }
                val t = row.getOrNull(rowSel) ?: return
                postDelayed({ onLaunch(t.launch, t) }, Metrics.PRESS_MS.toLong())
            }
            ZONE_BUTTONS -> {
                val b = buttons.getOrNull(btnSel) ?: return
                postDelayed({ onLaunch(b.launch, null) }, Metrics.PRESS_MS.toLong())
            }
        }
    }

    private fun openOptions() {
        if (popup.target > 0f) return
        val t = when {
            allMode.target > 0f -> apps.getOrNull(gridSel)
            zone == ZONE_ROW -> row.getOrNull(rowSel)
            else -> null
        } ?: return
        showOptions(t.title, optionsFor(t))
    }

    private companion object {
        const val ZONE_USER = 0
        const val ZONE_ROW = 1
        const val ZONE_BUTTONS = 2
        const val IDLE_FRAME_MS = 33L
        val AVATAR_COLOR = 0xFFE0533D.toInt()
    }
}
