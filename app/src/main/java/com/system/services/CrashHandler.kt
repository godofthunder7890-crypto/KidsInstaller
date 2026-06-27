package com.system.services

import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * Global uncaught exception handler — equivalent to Flash Get Kids' DefaultCrashActivity handler.
 *
 * Intercepts any unhandled crash, saves the stack trace, and launches
 * CrashActivity instead of showing the generic "App has stopped" dialog.
 */
class CrashHandler private constructor(
    private val ctx: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
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
            Thread.sleep(300)
        } catch (_: Exception) {
            // If CrashActivity itself fails, fall through to default handler
        }
        defaultHandler?.uncaughtException(thread, throwable)
            ?: Process.killProcess(Process.myPid())
    }

    companion object {
        fun install(context: Context) {
            val app = context.applicationContext
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(app, Thread.getDefaultUncaughtExceptionHandler())
            )
        }
    }
}
