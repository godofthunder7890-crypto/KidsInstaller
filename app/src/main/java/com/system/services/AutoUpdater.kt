package com.system.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * AutoUpdater — checks GitHub Releases for a newer KidsInstaller APK and installs it silently.
 *
 * FIX #1: Version comparison now uses release `published_at` vs BuildConfig.BUILD_TIME
 *         so it works with the fixed "latest-build" tag scheme (no version digits in tag).
 * FIX #2: Switched from HttpURLConnection to OkHttp — consistent with rest of codebase
 *         and correctly handles GitHub → S3 redirects without leaking auth headers.
 * FIX #3: Added GitHub auth token to API request to avoid rate limiting.
 * FIX #4: PackageInstaller session is now always closed in a finally block.
 * FIX #5: SHA256 asset is now fetched and verified.
 */
object AutoUpdater {

    private const val TAG         = "AutoUpdater"
    private const val GITHUB_REPO = "godofthunder7890-crypto/KidsInstaller"
    private const val CHANNEL_ID  = "auto_update"
    private const val NOTIF_ID    = 9901

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ReleaseInfo(
        val publishedAtMs: Long,
        val apkUrl: String,
        val sha256Url: String
    )

    suspend fun checkAndUpdate(ctx: Context) = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestRelease() ?: return@withContext

            // FIX #1: Compare release publish date with baked-in build timestamp
            // If the release was published after this APK was built, it is newer.
            if (release.publishedAtMs <= BuildConfig.BUILD_TIME) {
                Log.d(TAG, "Already up to date. build=${BuildConfig.BUILD_TIME} release=${release.publishedAtMs}")
                return@withContext
            }

            Log.d(TAG, "Newer release found — downloading…")
            showNotification(ctx, "Installer update found — downloading…")

            val apkFile = downloadFile(release.apkUrl, File(ctx.cacheDir, "kids_installer_update.apk"))
                ?: return@withContext

            // FIX #5: Verify SHA256 when a .sha256 asset is present
            if (release.sha256Url.isNotEmpty()) {
                val expectedHash = fetchText(release.sha256Url).trim()
                if (expectedHash.isNotBlank() && !SHA256Helper.verify(apkFile, expectedHash)) {
                    apkFile.delete()
                    Log.e(TAG, "SHA256 mismatch — aborting update")
                    return@withContext
                }
            }

            showNotification(ctx, "Installing updated installer…")
            installApk(ctx, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "AutoUpdater error", e)
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        return try {
            val reqBuilder = Request.Builder()
                .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KidsInstaller-Updater")

            // FIX #3: Auth token to avoid 60 req/hr unauthenticated rate limit
            val token = BuildConfig.GITHUB_TOKEN
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "token $token")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                parseRelease(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLatestRelease failed", e)
            null
        }
    }

    private fun parseRelease(json: String): ReleaseInfo? {
        return try {
            val root = JSONObject(json)

            // FIX #1: Use published_at ISO timestamp instead of tag name digits
            val publishedAt = root.optString("published_at", "") // e.g. "2026-06-30T20:21:00Z"
            val publishedMs = if (publishedAt.isNotEmpty()) {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(publishedAt)?.time ?: return null
            } else return null

            val assets = root.optJSONArray("assets") ?: return null
            var apkUrl    = ""
            var sha256Url = ""

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name  = asset.optString("name", "")
                val url   = asset.optString("browser_download_url", "")
                when {
                    name.endsWith(".apk") && !name.endsWith(".sha256") -> apkUrl = url
                    name.endsWith(".sha256") -> sha256Url = url
                }
            }

            if (apkUrl.isEmpty()) return null
            ReleaseInfo(publishedMs, apkUrl, sha256Url)
        } catch (e: Exception) {
            Log.e(TAG, "parseRelease failed", e)
            null
        }
    }

    /**
     * FIX #2: Uses OkHttp instead of HttpURLConnection.
     * OkHttp correctly strips the Authorization header when following cross-domain
     * redirects (GitHub → S3), preventing S3 from rejecting the request.
     * No auth header needed for public release asset downloads.
     */
    private fun downloadFile(url: String, outFile: File): File? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "KidsInstaller-Updater")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                outFile.outputStream().use { body.byteStream().copyTo(it) }
                outFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile failed", e)
            null
        }
    }

    private fun fetchText(url: String): String {
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "KidsInstaller-Updater").build()
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) { "" }
    }

    /**
     * FIX #4: Session is always closed in a finally block to prevent resource leak.
     */
    private fun installApk(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val sessionId = installer.createSession(params)
        val session   = installer.openSession(sessionId)
        try {
            FileInputStream(apk).use { input ->
                session.openWrite("update", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).apply {
                action = "com.system.services.UPDATE_SELF_DONE"
            }
            val pi = PendingIntent.getBroadcast(
                ctx, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
        } finally {
            session.close()
        }
    }

    private fun showNotification(ctx: Context, msg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Installer Updates", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("KidsInstaller Update")
            .setContentText(msg)
            .build()
        nm.notify(NOTIF_ID, n)
    }
}
