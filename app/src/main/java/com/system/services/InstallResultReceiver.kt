package com.system.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Receives PackageInstaller session commit results.
 *
 * Flash Get Kids routes install results back through a BroadcastReceiver;
 * this class handles that contract and forwards to MainActivity if it is alive.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent  ?: return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)

        // STATUS_PENDING_USER_ACTION must be handled immediately
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirmIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
            return
        }

        // Forward result to MainActivity via the same broadcast action it already listens to
        val fwd = Intent("com.system.services.INSTALL_DONE").apply {
            putExtra(PackageInstaller.EXTRA_STATUS, status)
            putExtra(PackageInstaller.EXTRA_PACKAGE_NAME,
                intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME))
        }
        context.sendBroadcast(fwd)
    }
}
