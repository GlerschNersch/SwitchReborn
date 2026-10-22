package dev.switchreborn.home

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.provider.Settings
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Short UI sounds synthesized at startup: a soft tick for moving, a rising pop for opening
 * and a low knock for hitting the end of the row. Original sounds, not sampled from the console.
 * Muted when the system "Touch sounds" setting is off.
 */
class Sfx(private val context: Context) {
    enum class Kind { MOVE, OPEN, BUMP, BACK }

    private val tracks = HashMap<Kind, AudioTrack>()

    init {
        tracks[Kind.MOVE] = build(blip(1900.0, 0.028, 90.0, 0.30))
        tracks[Kind.OPEN] = build(mix(blip(880.0, 0.07, 45.0, 0.28), blip(1320.0, 0.09, 40.0, 0.24, delay = 0.035)))
        tracks[Kind.BUMP] = build(blip(150.0, 0.07, 55.0, 0.55))
        tracks[Kind.BACK] = build(mix(blip(1320.0, 0.06, 45.0, 0.22), blip(880.0, 0.08, 40.0, 0.22, delay = 0.03)))
    }

    fun play(kind: Kind) {
        if (Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return
        val t = tracks[kind] ?: return
        runCatching {
            t.pause(); t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() = tracks.values.forEach { it.release() }

    /** A sine at [hz] with an exponential [decay] (per second) and a 2 ms attack. */
    private fun blip(hz: Double, seconds: Double, decay: Double, gain: Double, delay: Double = 0.0): ShortArray {
        val n = ((seconds + delay) * RATE).toInt()
        val off = (delay * RATE).toInt()
        val out = ShortArray(n)
        for (i in off until n) {
            val t = (i - off).toDouble() / RATE
            val env = minOf(1.0, t / 0.002) * exp(-decay * t)
            out[i] = (sin(2 * PI * hz * t) * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun mix(a: ShortArray, b: ShortArray): ShortArray {
        val out = ShortArray(maxOf(a.size, b.size))
        for (i in out.indices) out[i] = ((a.getOrElse(i) { 0 } + b.getOrElse(i) { 0 }).coerceIn(-32768, 32767)).toShort()
        return out
    }

    private fun build(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    private companion object {
        const val RATE = 44100
    }
}
