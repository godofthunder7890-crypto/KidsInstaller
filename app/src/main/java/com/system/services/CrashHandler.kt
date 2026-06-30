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
            // Give CrashActivity time to start before killing the process
            Thread.sleep(400)
        } catch (_: Exception) {
            // CrashActivity itself failed — fall through to process kill
        }
        // FIX: Always kill the process ourselves. Do NOT call defaultHandler —
        // that would show the system "App has stopped" dialog on top of CrashActivity.
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
