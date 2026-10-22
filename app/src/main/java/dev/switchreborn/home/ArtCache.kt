package dev.switchreborn.home

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * All Software art, rendered on demand off the main thread and kept in a byte-bounded LRU.
 * Pre-rendering every app at tile size cost ~190 MB on a 170-app phone; only what's on
 * screen (plus a screen or two of scrollback) is held now.
 */
class ArtCache(private val render: (Tile, Int) -> Bitmap?, private val onReady: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    // Newest request first, so a fast scroll paints where the user stopped, not where they passed.
    private val queue = object : LinkedBlockingDeque<Runnable>() {
        override fun offer(e: Runnable) = offerFirst(e)
    }
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queue)
    private val pending = HashSet<String>()
    private var px = 0
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The art for [t] at [size] px, or null while it renders (a redraw follows when it lands). */
    fun get(t: Tile, size: Int): Bitmap? {
        if (size != px) clear().also { px = size }
        cache.get(t.id)?.let { return it }
        if (pending.add(t.id)) {
            val gen = px
            worker.execute {
                val bmp = runCatching { render(t, gen) }.getOrNull()
                main.post {
                    pending.remove(t.id)
                    if (bmp != null && gen == px) { cache.put(t.id, bmp); onReady() }
                }
            }
        }
        return null
    }

    fun clear() {
        queue.clear()
        pending.clear()
        cache.evictAll()
    }

    fun shutdown() = worker.shutdownNow()

    private companion object {
        const val MAX_BYTES = 24 * 1024 * 1024
    }
}
