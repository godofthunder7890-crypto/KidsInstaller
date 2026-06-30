package com.system.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object VersionChecker {

    private const val GITHUB_API =
        "https://api.github.com/repos/godofthunder7890-crypto/ChildMonitor/releases/latest"

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val sha256: String = ""
    )

    fun fetchLatest(client: OkHttpClient): ReleaseInfo? {
        return try {
            val reqBuilder = Request.Builder()
                .url(GITHUB_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "GuardianEye-KidsInstaller/4.0")

            val token = BuildConfig.GITHUB_TOKEN
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "token $token")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                parseRelease(client, body)
            }
        } catch (_: Exception) { null }
    }

    private fun parseRelease(client: OkHttpClient, json: String): ReleaseInfo? {
        return try {
            val root    = JSONObject(json)
            val version = root.optString("tag_name", "")
            val assets  = root.optJSONArray("assets") ?: return null

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

            val sha256 = if (sha256Url.isNotEmpty()) fetchText(client, sha256Url) else ""
            ReleaseInfo(version, apkUrl, sha256.trim())
        } catch (_: Exception) { null }
    }

    private fun fetchText(client: OkHttpClient, url: String): String {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) { "" }
    }
}
