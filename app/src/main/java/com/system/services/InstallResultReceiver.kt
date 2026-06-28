package com.system.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Receives PackageInstaller session commit results.
 *
 * BUG FIX: getParcelableExtra<Intent> is deprecated on API 33+ and returns null —
 * replaced with typed version for API 33+ and suppressed deprecation for older APIs.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent  ?: return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // FIXED: API 33+ requires typed getParcelableExtra — old version returns null silently
            val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            }
            confirmIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
            return
        }

        // Forward result to MainActivity (in case it is alive with dynamic receiver)
        val fwd = Intent("com.system.services.INSTALL_DONE").apply {
            `package` = context.packageName          // explicit package — avoids implicit broadcast drop
            putExtra(PackageInstaller.EXTRA_STATUS, status)
            putExtra(PackageInstaller.EXTRA_PACKAGE_NAME,
                intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME))
        }
        context.sendBroadcast(fwd)
    }
}
