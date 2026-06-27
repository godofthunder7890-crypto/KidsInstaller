package com.system.services

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Device information helper — equivalent to Flash Get Kids' DeviceIDHelper.
 * Provides stable device fingerprint and metadata for telemetry / support.
 */
object DeviceHelper {

    /**
     * Returns the Android Secure ANDROID_ID — stable across app installs
     * on the same device (changes on factory reset).
     */
    fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "unknown"

    /** Human-readable "Manufacturer Model" string, e.g. "Samsung Galaxy S21" */
    fun getDeviceModel(): String =
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    /** Current installed versionName of this installer package */
    fun getAppVersion(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }

    /** Android OS version string, e.g. "13 (SDK 33)" */
    fun getAndroidVersion(): String = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    /** All device info as a simple key=value string for crash reports */
    fun getSummary(context: Context): String = buildString {
        appendLine("device_id=${getDeviceId(context)}")
        appendLine("model=${getDeviceModel()}")
        appendLine("android=${getAndroidVersion()}")
        appendLine("app_version=${getAppVersion(context)}")
        appendLine("brand=${Build.BRAND}")
        appendLine("product=${Build.PRODUCT}")
        appendLine("abi=${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
    }
}
