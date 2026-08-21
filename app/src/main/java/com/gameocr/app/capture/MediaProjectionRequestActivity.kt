package com.gameocr.app.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.gameocr.app.service.CaptureService
import com.gameocr.app.service.CaptureServiceState
import timber.log.Timber

/**
 * 快捷手势 / 外部快捷方式 Activity：一键切换/启动 Root 实时翻译前台服务。
 * 0 弹窗、无 UI、执行后立即 finish 退出。
 */
class MediaProjectionRequestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toggleOrStartService()
        finish()
    }

    private fun toggleOrStartService() {
        val isRunning = CaptureServiceState.running.value
        if (isRunning) {
            Timber.i("QuickToggle: Stopping CaptureService")
            val stopIntent = CaptureService.stopIntent(this)
            startService(stopIntent)
            Toast.makeText(this, "屏译：已停止实时翻译", Toast.LENGTH_SHORT).show()
        } else {
            Timber.i("QuickToggle: Starting CaptureService in Root mode")
            val svc = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_USE_ROOT, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, svc)
            } else {
                startService(svc)
            }
            Toast.makeText(this, "屏译：已启动 Root 实时翻译", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, MediaProjectionRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}