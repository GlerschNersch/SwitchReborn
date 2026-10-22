package dev.switchreborn

import dev.switchreborn.home.Sfx
import dev.switchreborn.home.SfxSynth
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SfxTest {
    /** Every sound stays inside full scale and actually makes noise. */
    @Test fun soundsAreAudibleAndDoNotClip() {
        for (k in Sfx.Kind.entries) {
            val pcm = SfxSynth.render(k)
            val peak = pcm.maxOf { kotlin.math.abs(it) }
            assertTrue("$k peak $peak", peak in 0.05f..0.99f)
        }
    }

    /** Writes each sound to build/sfx so tools/sound_shape.py can compare it with the references. */
    @Test fun writeWavsForAnalysis() {
        val dir = File("build/sfx").apply { mkdirs() }
        for (k in Sfx.Kind.entries) {
            val pcm = SfxSynth.render(k)
            val data = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
            data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SfxSynth.RATE)
                .putInt(SfxSynth.RATE * 2).putShort(2).putShort(16)
            data.put("data".toByteArray()).putInt(pcm.size * 2)
            for (v in pcm) data.putShort((v.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            File(dir, "${k.name.lowercase()}.wav").writeBytes(data.array())
        }
    }
}
