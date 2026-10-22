package dev.switchreborn.home

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * UI sounds, synthesized at startup. Nothing is sampled from the console. HOME, OPEN and BACK
 * are rebuilt from measurements of reference recordings (pitches, harmonic levels, envelopes;
 * see docs/RE_NOTES.md "Sounds"). MOVE and BUMP aren't measured yet and are our own placeholders.
 * The HOME menu itself is silent between sounds: the console has no ambient loop there.
 * They play as game audio (the media volume), not system sounds: the system stream is often
 * muted on phones, and Android's "Touch sounds" switch is about taps, not a console's UI.
 * The launcher's own Interface Sounds option turns them off.
 */
class Sfx {
    enum class Kind { MOVE, OPEN, BUMP, BACK, HOME }

    private val tracks = HashMap<Kind, AudioTrack>()
    var enabled = true

    init {
        for (k in Kind.entries) tracks[k] = build(SfxSynth.render(k))
    }

    fun play(kind: Kind) {
        if (!enabled) return
        val t = tracks[kind] ?: return
        runCatching {
            t.pause(); t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() = tracks.values.forEach { it.release() }

    private fun build(pcm: FloatArray): AudioTrack {
        val data = ShortArray(pcm.size) { (pcm[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        track.write(data, 0, data.size)
        return track
    }

    private companion object {
        const val RATE = SfxSynth.RATE
    }
}

/** The sound shapes as plain sample arrays, so JVM tests can render and check them. */
object SfxSynth {
    const val RATE = 44100

    fun render(kind: Sfx.Kind): FloatArray = when (kind) {
        Sfx.Kind.MOVE -> blip(1900.0, 0.028, 90.0, 0.30)
        Sfx.Kind.BUMP -> blip(150.0, 0.07, 55.0, 0.55)
        Sfx.Kind.OPEN -> open()
        Sfx.Kind.BACK -> back()
        Sfx.Kind.HOME -> home()
    }

    /**
     * Opening something: a three-impulse rattle (0, 7 and 16 ms) under a short G6 (1564 Hz)
     * tone. Measured: 2nd harmonic -29 dB, 3rd -38 dB; the tone peaks at 15 ms, holds to
     * ~45 ms, then falls at ~1100 dB/s and is gone by ~90 ms. The rattle's energy sits below 2 kHz (4 kHz is -20 dB).
     */
    fun open(): FloatArray {
        val out = FloatArray(samples(0.13))
        addTone(out, 0.0, 1564.0, doubleArrayOf(1.0, 0.035, 0.0126), 0.30, attack = 0.012, hold = 0.03, decayDbPerS = 1100.0)
        val rattle = doubleArrayOf(1000.0, 1.0, 1900.0, 0.8, 4000.0, 0.1)
        for ((i, t) in doubleArrayOf(0.0, 0.007, 0.016).withIndex()) addImpulse(out, t, rattle, 0.22 * (1 - 0.25 * i), 0.0012)
        return out
    }

    /**
     * Backing out: a buzz of eight impulses 4.6 ms apart (217 Hz) ringing at 2150 Hz until
     * ~60 ms, its 3rd harmonic at -23 dB, then a second, brighter click 80.5 ms in, centred near 3 kHz
     * (5 kHz at -13 dB).
     */
    fun back(): FloatArray {
        val out = FloatArray(samples(0.14))
        val buzz = doubleArrayOf(2150.0, 1.0, 6450.0, 0.07, 4300.0, 0.02)
        for (i in 0 until 8) addImpulse(out, i * 0.0046, buzz, 0.10 * (1 - 0.06 * i), 0.009)
        addImpulse(out, 0.0805, doubleArrayOf(3000.0, 1.0, 5000.0, 0.22, 1500.0, 0.04), 0.2, 0.003)
        return out
    }

    /**
     * Arriving at HOME: five notes rising in perfect fifths, C3 G3 D4 A4 E5, 62 ms apart on
     * average (offsets measured as 0/62/126/176/250 ms). It's a hollow, clarinet-like tone:
     * odd harmonics only, 3rd at -20 to -31 dB, 5th at about -31 dB. Fundamentals ring on at
     * ~70 dB/s, but each note's upper partials stop when the next note starts, so the
     * brightness passes up the chord. The last note keeps its full tone.
     */
    fun home(): FloatArray {
        val out = FloatArray(samples(1.1))
        val notes = doubleArrayOf(130.81, 196.0, 293.66, 440.0, 659.26)
        val at = doubleArrayOf(0.0, 0.062, 0.126, 0.176, 0.250)
        for (i in notes.indices) {
            addTone(out, at[i], notes[i], doubleArrayOf(1.0, 0.004, 0.10, 0.0, 0.04), 0.16,
                attack = 0.004, hold = 0.0, decayDbPerS = 70.0,
                cutUpperAfter = if (i + 1 < notes.size) at[i + 1] - at[i] else null)
        }
        return out
    }

    /**
     * Adds one impulse ringing at a few resonances: [modes] is (Hz, relative amplitude) pairs,
     * each a sine decaying with time constant [tau] seconds.
     */
    private fun addImpulse(out: FloatArray, at: Double, modes: DoubleArray, gain: Double, tau: Double) {
        val start = samples(at)
        val n = minOf(samples(tau * 8), out.size - start)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            var v = 0.0
            for (m in modes.indices step 2) v += modes[m + 1] * sin(2 * PI * modes[m] * t)
            out[start + i] += (v * exp(-t / tau) * gain).toFloat()
        }
    }

    private fun samples(seconds: Double) = (seconds * RATE).toInt()

    /** Adds a harmonic tone. [harmonics] are amplitudes of partials 1, 2, 3... relative to the fundamental. */
    private fun addTone(
        out: FloatArray, at: Double, hz: Double, harmonics: DoubleArray, gain: Double,
        attack: Double, hold: Double, decayDbPerS: Double, cutUpperAfter: Double? = null,
    ) {
        val rate = decayDbPerS / 8.686 // dB/s to an exponential rate on amplitude
        val start = samples(at)
        for (n in 0 until out.size - start) {
            val t = n.toDouble() / RATE
            val env = when {
                t < attack -> t / attack
                t < attack + hold -> 1.0
                else -> exp(-rate * (t - attack - hold))
            }
            if (env < 1e-4 && t > attack) break
            var v = 0.0
            // After [cutUpperAfter], everything above the fundamental falls away at 600 dB/s
            val cut = cutUpperAfter?.let { if (t > it) exp(-600.0 / 8.686 * (t - it)) else 1.0 } ?: 1.0
            for ((k, a) in harmonics.withIndex()) if (a > 0) {
                val f = hz * (k + 1)
                if (f < RATE / 2) v += a * (if (k > 0) cut else 1.0) * sin(2 * PI * f * t)
            }
            out[start + n] += (v * env * gain).toFloat()
        }
    }

    /** A sine at [hz] with an exponential [decay] (per second) and a 2 ms attack (placeholder sounds). */
    private fun blip(hz: Double, seconds: Double, decay: Double, gain: Double): FloatArray {
        val out = FloatArray(samples(seconds))
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            out[i] = (sin(2 * PI * hz * t) * minOf(1.0, t / 0.002) * exp(-decay * t) * gain).toFloat()
        }
        return out
    }
}
