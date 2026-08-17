package com.gameocr.app.capture

import java.io.File
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * root 能力探测。
 *
 * 检测路径覆盖常见 su 位置（Magisk / system 内置 / vendor 等）。真正判定是否可用时执行
 * `su -c id`，退出码 0 且 stdout 含 `uid=0` / `(root)` 才认为 root 就绪。
 */
internal object RootCapabilities {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/system/bin/.ext/.su",
        "/data/adb/magisk",
    )

    /** 轻量检测：常见 su 路径是否至少存在一个。capture 前调用，不真正 exec。 */
    fun hasSuBinary(): Boolean = SU_PATHS.any { File(it).exists() }

    /**
     * 真正验证 root 是否可用：执行 `su -c id`。
     * 注意：首次执行 Magisk 会弹 root 授权窗，可能耗时；应放在 IO 线程调用。
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val terminated = process.waitFor(3, TimeUnit.SECONDS)
            if (!terminated) {
                Timber.w("[root-cap] su id timed out; destroying")
                runCatching { process.destroy() }
                return false
            }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.exitValue()
            exit == 0 && (stdout.contains("uid=0") || stdout.contains("(root)"))
        } catch (t: Throwable) {
            Timber.w(t, "[root-cap] id check threw")
            false
        }
    }
}
