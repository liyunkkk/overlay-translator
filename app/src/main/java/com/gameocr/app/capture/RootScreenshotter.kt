package com.gameocr.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 root (su) 的截屏实现：通过 `su -c screencap` 在 root 权限下执行 raw `screencap`，
 * 不兼容时回退到 `screencap -p` PNG。复用 [ShizukuRawScreencapDecoder] 解码 raw 头。
 *
 * 优势：
 * - 免 MediaProjection 每次系统授权窗（Android 14+ 强制弹），与 Shizuku 路径等价
 * - 不需要安装 / 配对 Shizuku，只需设备已 root（Magisk / KernelSU 等）
 *
 * 代价：
 * - 首次执行时 Magisk 等会弹一次 root 授权窗（此后记住）
 * - 每次截屏走 su 进程，~150-400ms，帧率上限 ~5 FPS，仅适合"按需触发"
 *
 * 与 [ShizukuScreenshotter] 的区别仅在进程执行方式：Shizuku 用 shell uid，
 * 本类用 root uid 直接跑 `screencap`。
 */
class RootScreenshotter : Screenshotter {

    private val released = AtomicBoolean(false)

    override val isReady: Boolean
        get() = !released.get() && runCatching { RootCapabilities.hasSuBinary() }.getOrDefault(false)

    override suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        if (!isReady) {
            Timber.w("[root-cap] skip: not ready (released=%s, su=%s)",
                released.get(),
                runCatching { RootCapabilities.hasSuBinary() }.getOrDefault(false))
            return@withContext null
        }
        try {
            // Raw screencap avoids vendor-specific PNG stream corruption. Keep PNG as a
            // compatibility fallback for devices whose raw header or pixel format is unknown.
            val rawBitmap = executeScreencap(arrayOf("screencap"), "raw")?.let { bytes ->
                runCatching { ShizukuRawScreencapDecoder.decode(bytes) }
                    .onFailure { Timber.w(it, "[root-cap] raw decode threw") }
                    .getOrNull()
            }
            if (rawBitmap != null) {
                Timber.d("[root-cap] raw ok %dx%d", rawBitmap.width, rawBitmap.height)
                return@withContext rawBitmap
            }

            Timber.w("[root-cap] raw capture/decode failed; retrying PNG")
            val pngBytes = executeScreencap(arrayOf("screencap", "-p"), "png")
                ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            if (bitmap == null) {
                Timber.w(
                    "[root-cap] decode PNG failed, bytes=%d, head=%s",
                    pngBytes.size,
                    pngBytes.take(8).joinToString { "%02x".format(it) }
                )
            } else {
                Timber.d("[root-cap] png ok %dx%d", bitmap.width, bitmap.height)
            }
            bitmap
        } catch (t: Throwable) {
            Timber.w(t, "[root-cap] capture threw")
            null
        }
    }

    private fun executeScreencap(command: Array<String>, format: String): ByteArray? {
        return try {
            val cmdLine = command.joinToString(" ")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmdLine))
            // 先等退出，再读流，避免 su 授权等待时 readText 阻塞。
            val terminated = process.waitFor(8, TimeUnit.SECONDS)
            if (!terminated) {
                Timber.w("[root-cap] %s timed out; destroying", format)
                runCatching { process.destroy() }
                return null
            }
            val out = ByteArrayOutputStream(16 * 1024 * 1024)
            process.inputStream.use { it.copyTo(out) }
            val exitCode = process.exitValue()
            val bytes = out.toByteArray()
            if (exitCode != 0) {
                val error = runCatching {
                    process.errorStream.use { String(it.readBytes()).take(300) }
                }.getOrElse { "<no stderr>" }
                Timber.w(
                    "[root-cap] %s exit=%d, stdoutBytes=%d, stderr=%s",
                    format,
                    exitCode,
                    bytes.size,
                    error
                )
                null
            } else if (bytes.isEmpty()) {
                Timber.w("[root-cap] %s exit=0 but empty payload", format)
                null
            } else {
                bytes
            }
        } catch (t: Throwable) {
            Timber.w(t, "[root-cap] execute %s threw", format)
            null
        }
    }

    override fun release() {
        released.set(true)
    }
}
