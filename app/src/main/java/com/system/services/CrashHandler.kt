package com.system.services

import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * Global uncaught exception handler.
 *
 * FIX: Previously called defaultHandler?.uncaughtException() after launching CrashActivity,
 * which caused the system "App has stopped" dialog to appear alongside CrashActivity
 * (double crash screen). Now we always kill the process ourselves after launching
 * CrashActivity, suppressing the system dialog entirely.
 */
class CrashHandler private constructor(
    private val ctx: Context
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val intent = Intent(ctx, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(CrashActivity.EXTRA_ERROR, throwable.message ?: "Unknown error")
                putExtra(CrashActivity.EXTRA_STACK, throwable.stackTraceToString().take(3000))
                putExtra(CrashActivity.EXTRA_DEVICE, DeviceHelper.getSummary(ctx))
            }
            ctx.startActivity(intent)
            // FIX: Removed Thread.sleep(400) — if this handler runs on the main thread
            // (e.g. main thread crashes), sleep causes an ANR on top of the crash.
            // startActivity() is async; Android's process model keeps the process alive
            // long enough for CrashActivity to launch before killProcess() tears it down.
        } catch (_: Exception) {
            // CrashActivity itself failed — fall through to process kill
        }
        // Always kill the process ourselves to suppress the system "App has stopped" dialog.
        Process.killProcess(Process.myPid())
    }

    companion object {
        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(context.applicationContext)
            )
        }
    }
}
