package dev.switchreborn.home

/**
 * Horizontal geometry of the HOME row in reference px. Slot [n] (one past the software) is the
 * round All Software button. The console keeps the selection fully on screen with the same
 * 162 px margin on both sides: in a 1920 frame tiles 1-4 fit, and stepping to the 5th scrolls
 * by exactly one pitch.
 */
object RowLayout {
    fun slotX(i: Int): Float = Metrics.TILE_X + i * Metrics.TILE_PITCH

    fun slotW(i: Int, n: Int): Float = if (i == n) Metrics.ALL_D else Metrics.TILE

    fun maxScroll(n: Int, viewW: Float): Float =
        maxOf(0f, slotX(n) + Metrics.ALL_D - (viewW - Metrics.TILE_X))

    /** The smallest change to [current] that brings slot [sel] fully into view. */
    fun scrollFor(sel: Int, n: Int, viewW: Float, current: Float): Float {
        val left = slotX(sel) - Metrics.TILE_X
        val right = slotX(sel) + slotW(sel, n) - (viewW - Metrics.TILE_X)
        return current.coerceIn(minOf(left, right).coerceAtLeast(0f), left).coerceIn(0f, maxScroll(n, viewW))
    }

    /** The slot under reference x [x] at scroll [scroll], or -1 (gaps and past the end). */
    fun hit(x: Float, scroll: Float, n: Int): Int {
        val rel = x + scroll - Metrics.TILE_X
        if (rel < 0) return -1
        val i = (rel / Metrics.TILE_PITCH).toInt()
        if (i > n) return -1
        return if (rel - i * Metrics.TILE_PITCH <= slotW(i, n)) i else -1
    }
}
