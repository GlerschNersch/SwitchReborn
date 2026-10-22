package dev.switchreborn.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Original line icons for the round buttons and chrome. They depict the same objects as the
 * console's (speech bubble, bag, picture, controller, gear, power) but are drawn from scratch.
 */
enum class Glyph { ONLINE, NEWS, SHOP, ALBUM, CONTROLLERS, SETTINGS, POWER, ALL_SOFTWARE, WIFI, GAMEPAD, PLUS, BADGE }

object Glyphs {
    private val path = Path()
    private val r = RectF()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    /** Draws [g] centred on ([cx], [cy]) inside a [size] box. [cut] is the colour behind it, for knock-outs. */
    fun draw(c: Canvas, g: Glyph, cx: Float, cy: Float, size: Float, color: Int, cut: Int = 0) {
        val u = size / 64f
        val sw = 6f * u
        p.color = color
        p.strokeWidth = sw
        p.style = Paint.Style.STROKE
        when (g) {
            Glyph.NEWS -> {
                // Bubble with a tail, a headline bar and a short line
                r.set(cx - 29 * u, cy - 23 * u, cx + 29 * u, cy + 15 * u)
                c.drawRoundRect(r, 7 * u, 7 * u, p)
                path.reset()
                path.moveTo(cx - 9 * u, cy + 15 * u); path.lineTo(cx - 1 * u, cy + 25 * u); path.lineTo(cx + 7 * u, cy + 15 * u)
                c.drawPath(path, p)
                c.drawLine(cx - 29 * u, cy - 6 * u, cx + 29 * u, cy - 6 * u, p)
                c.drawLine(cx - 18 * u, cy + 4 * u, cx + 4 * u, cy + 4 * u, p)
            }
            Glyph.SHOP -> {
                r.set(cx - 25 * u, cy - 10 * u, cx + 25 * u, cy + 26 * u)
                c.drawRect(r, p)
                r.set(cx - 11 * u, cy - 28 * u, cx + 11 * u, cy - 2 * u)
                c.drawArc(r, 180f, 180f, false, p)
                c.drawLine(cx - 11 * u, cy - 15 * u, cx - 11 * u, cy - 10 * u, p)
                c.drawLine(cx + 11 * u, cy - 15 * u, cx + 11 * u, cy - 10 * u, p)
            }
            Glyph.ALBUM -> {
                r.set(cx - 30 * u, cy - 22 * u, cx + 30 * u, cy + 22 * u)
                c.drawRect(r, p)
                // A rolling hill line across the picture
                path.reset()
                path.moveTo(cx - 20 * u, cy + 22 * u)
                path.lineTo(cx - 20 * u, cy - 4 * u)
                path.cubicTo(cx - 20 * u, cy - 14 * u, cx - 4 * u, cy - 14 * u, cx - 4 * u, cy - 2 * u)
                path.cubicTo(cx - 4 * u, cy - 9 * u, cx + 10 * u, cy - 9 * u, cx + 10 * u, cy + 2 * u)
                path.lineTo(cx + 18 * u, cy + 6 * u)
                path.lineTo(cx + 18 * u, cy + 22 * u)
                c.drawPath(path, p)
            }
            Glyph.CONTROLLERS -> {
                c.save()
                c.rotate(-40f, cx, cy)
                r.set(cx - 32 * u, cy - 13 * u, cx + 32 * u, cy + 13 * u)
                c.drawRoundRect(r, 13 * u, 13 * u, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx - 16 * u, cy, 5.5f * u, p)
                for ((dx, dy) in listOf(10f to -5f, 16f to 0f, 10f to 5f, 4f to 0f)) c.drawCircle(cx + dx * u + 6 * u, cy + dy * u, 2.6f * u, p)
                c.restore()
            }
            Glyph.SETTINGS -> {
                c.drawCircle(cx, cy, 15 * u, p)
                for (i in 0 until 8) {
                    c.save(); c.rotate(i * 45f, cx, cy)
                    c.drawLine(cx, cy - 21 * u, cx, cy - 29 * u, p)
                    c.restore()
                }
            }
            Glyph.POWER -> {
                r.set(cx - 25 * u, cy - 23 * u, cx + 25 * u, cy + 27 * u)
                c.drawArc(r, -60f, 300f, false, p)
                c.drawLine(cx, cy - 30 * u, cx, cy - 2 * u, p)
            }
            Glyph.ONLINE -> {
                // A plain globe: our own mark for the "online" button
                c.drawCircle(cx, cy, 25 * u, p)
                r.set(cx - 11 * u, cy - 25 * u, cx + 11 * u, cy + 25 * u)
                c.drawOval(r, p)
                c.drawLine(cx - 25 * u, cy, cx + 25 * u, cy, p)
            }
            Glyph.ALL_SOFTWARE -> {
                p.style = Paint.Style.FILL
                val cell = 14 * u; val pitch = 20 * u
                for (i in -1..1) for (j in -1..1) {
                    r.set(cx + i * pitch - cell / 2, cy + j * pitch - cell / 2, cx + i * pitch + cell / 2, cy + j * pitch + cell / 2)
                    c.drawRoundRect(r, 2 * u, 2 * u, p)
                }
            }
            Glyph.WIFI -> {
                p.strokeWidth = 5f * u
                for ((i, rad) in listOf(10f, 22f, 34f).withIndex()) {
                    r.set(cx - rad * u, cy + 14 * u - rad * u, cx + rad * u, cy + 14 * u + rad * u)
                    if (i > 0) c.drawArc(r, 225f, 90f, false, p)
                }
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx, cy + 16 * u); path.lineTo(cx - 8 * u, cy + 7 * u); path.lineTo(cx + 8 * u, cy + 7 * u); path.close()
                c.drawPath(path, p)
            }
            Glyph.GAMEPAD -> {
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - 20 * u, cy - 14 * u)
                path.lineTo(cx + 20 * u, cy - 14 * u)
                path.cubicTo(cx + 34 * u, cy - 14 * u, cx + 34 * u, cy + 22 * u, cx + 26 * u, cy + 22 * u)
                path.cubicTo(cx + 20 * u, cy + 22 * u, cx + 16 * u, cy + 8 * u, cx + 10 * u, cy + 8 * u)
                path.lineTo(cx - 10 * u, cy + 8 * u)
                path.cubicTo(cx - 16 * u, cy + 8 * u, cx - 20 * u, cy + 22 * u, cx - 26 * u, cy + 22 * u)
                path.cubicTo(cx - 34 * u, cy + 22 * u, cx - 34 * u, cy - 14 * u, cx - 20 * u, cy - 14 * u)
                path.close()
                c.drawPath(path, p)
                p.color = cut
                p.strokeWidth = 3f * u
                p.style = Paint.Style.STROKE
                c.drawLine(cx - 20 * u, cy - 3 * u, cx - 12 * u, cy - 3 * u, p)
                c.drawLine(cx - 16 * u, cy - 7 * u, cx - 16 * u, cy + 1 * u, p)
                p.style = Paint.Style.FILL
                for ((dx, dy) in listOf(16f to -7f, 21f to -3f, 16f to 1f, 11f to -3f)) c.drawCircle(cx + dx * u, cy + dy * u, 2.2f * u, p)
            }
            Glyph.PLUS -> {
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, 32 * u, p)
                p.color = cut
                p.style = Paint.Style.STROKE
                p.strokeWidth = 8 * u
                p.strokeCap = Paint.Cap.BUTT
                c.drawLine(cx - 17 * u, cy, cx + 17 * u, cy, p)
                c.drawLine(cx, cy - 17 * u, cx, cy + 17 * u, p)
                p.strokeCap = Paint.Cap.ROUND
            }
            Glyph.BADGE -> {
                // Software-type badge beside the title: a filled card with a down notch
                p.style = Paint.Style.FILL
                r.set(cx - 20 * u, cy - 23 * u, cx + 20 * u, cy + 23 * u)
                c.drawRoundRect(r, 3 * u, 3 * u, p)
                p.color = cut
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3 * u
                c.drawLine(cx - 6 * u, cy + 13 * u, cx, cy + 17 * u, p)
                c.drawLine(cx, cy + 17 * u, cx + 6 * u, cy + 13 * u, p)
            }
        }
    }
}
