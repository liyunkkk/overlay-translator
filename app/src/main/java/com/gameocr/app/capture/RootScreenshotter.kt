package com.gameocr.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class RootScreenshotter : Screenshotter {
    private val released = AtomicBoolean(false)

    override val isReady: Boolean
        get() = !released.get()

    override suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext null
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
            val baos = ByteArrayOutputStream()
            process.inputStream.use { input ->
                input.copyTo(baos)
            }
            process.waitFor()
            val bytes = baos.toByteArray()
            if (bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (t: Throwable) {
            Timber.w(t, "[root-cap] capture threw")
            null
        }
    }

    override fun release() {
        released.set(true)
    }
}