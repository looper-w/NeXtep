package io.github.lujinxin.nextep.trigger

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.lujinxin.nextep.R
import io.github.lujinxin.nextep.logging.NeXtepLog

/**
 * Headless entry point for external automation. It reuses the Quick Settings tile
 * path and finishes as soon as the coordinated request completes, so `am start`
 * behaves like a one-shot command instead of leaving a visible window behind.
 */
class WorkspaceToggleActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutGuard = Runnable {
        NeXtepLog.warn("external_toggle", "External toggle timed out; finishing")
        finishQuietly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedActive = resolveRequestedActive(intent)
        handler.postDelayed(timeoutGuard, TIMEOUT_MS)
        NeXtepLog.info("external_toggle", "External toggle requested active=$requestedActive")
        val callback: (TriggerCoordinator.RequestResult) -> Unit = { result ->
            NeXtepLog.info("external_toggle", "External toggle completed result=$result")
            if (result is TriggerCoordinator.RequestResult.NotReady) {
                runCatching {
                    Toast.makeText(applicationContext, R.string.tile_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
            finishQuietly()
        }
        if (requestedActive == null) {
            TriggerCoordinator.requestToggle(applicationContext, TriggerSource.EXTERNAL_COMMAND, callback)
        } else {
            TriggerCoordinator.requestSet(
                applicationContext,
                requestedActive,
                TriggerSource.EXTERNAL_COMMAND,
                onResult = callback,
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutGuard)
        super.onDestroy()
    }

    private fun finishQuietly() {
        handler.removeCallbacks(timeoutGuard)
        if (!isFinishing) finish()
    }

    private fun resolveRequestedActive(source: Intent?): Boolean? {
        if (source == null) return null
        if (source.hasExtra(EXTRA_ACTIVE)) return source.getBooleanExtra(EXTRA_ACTIVE, false)
        if (source.hasExtra(TriggerBroadcastContract.EXTRA_REQUESTED_ACTIVE)) {
            return source.getBooleanExtra(TriggerBroadcastContract.EXTRA_REQUESTED_ACTIVE, false)
        }
        return null
    }

    private companion object {
        // Short alias so the shell command stays readable: --ez active true
        const val EXTRA_ACTIVE = "active"
        const val TIMEOUT_MS = 5_000L
    }
}
