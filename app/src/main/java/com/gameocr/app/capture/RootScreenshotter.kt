package com.gameocr.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RootScreenshotter : Screenshotter {
    private val released = AtomicBoolean(false)

    override val isReady: Boolean
        get() = !released.get()

    override suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext null
        var process: Process? = null
        try {
            process = ProcessBuilder("su", "-c", "screencap -p")
                .redirectErrorStream(false)
                .start()
            val bitmap = BufferedInputStream(process.inputStream, 65536).use { bis ->
                BitmapFactory.decodeStream(bis)
            }
            process.waitFor(3, TimeUnit.SECONDS)
            bitmap
        } catch (t: Throwable) {
            Timber.w(t, "[root-cap] capture failed")
            null
        } finally {
            try {
                process?.destroy()
            } catch (_: Throwable) {}
        }
    }

    override fun release() {
        released.set(true)
    }
}
