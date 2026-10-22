package dev.switchreborn.home

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Cubic ease-out; the row scroll settles quickly with no overshoot. */
fun easeOut(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * The highlight pulse, 0 (dark) to 1 (light). Captured at 60 fps it holds ~0.15-0.2 s at each
 * extreme and ramps between them; smoothstep over a cosine reproduces those flat shoulders.
 */
fun pulse(ms: Long): Float {
    val phase = (ms % Metrics.PULSE_MS.toLong()) / Metrics.PULSE_MS
    return smoothstep(0.5f - 0.5f * cos(phase * 2f * PI.toFloat()))
}

fun lerpColor(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = (lerp(((a shr shift) and 0xFF).toFloat(), ((b shr shift) and 0xFF).toFloat(), t) + 0.5f).toInt()
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** A value that eases toward its target; retargeting mid-flight restarts from the current value. */
class Tween(initial: Float, private val durationMs: Float) {
    var value = initial
        private set
    var target = initial
        private set
    private var from = initial
    private var elapsed = durationMs

    val settled get() = elapsed >= durationMs

    fun animateTo(to: Float) {
        if (to == target && !settled) return
        from = value
        target = to
        elapsed = 0f
        if (abs(to - value) < 1e-4f) snapTo(to)
    }

    fun snapTo(to: Float) {
        value = to; target = to; from = to; elapsed = durationMs
    }

    fun step(dtMs: Float): Boolean {
        if (settled) return false
        elapsed = (elapsed + dtMs).coerceAtMost(durationMs)
        value = lerp(from, target, easeOut(elapsed / durationMs))
        return !settled
    }
}

/** A one-shot 0→1→0 envelope (press dip, edge bump), driven by elapsed time. */
class Blip(private val durationMs: Float) {
    private var elapsed = durationMs
    val active get() = elapsed < durationMs

    fun fire() { elapsed = 0f }

    fun step(dtMs: Float) { if (active) elapsed = (elapsed + dtMs).coerceAtMost(durationMs) }

    /** Rises over the first third, decays over the rest. */
    val value: Float get() {
        if (!active) return 0f
        val t = elapsed / durationMs
        return if (t < 0.33f) easeOut(t / 0.33f) else 1f - smoothstep((t - 0.33f) / 0.67f)
    }
}
