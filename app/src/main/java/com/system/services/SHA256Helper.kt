package com.system.services

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 file verification — equivalent to Flash Get Kids' MD5/hash check.
 * Verifies downloaded APK integrity before installation.
 */
object SHA256Helper {

    /** Compute SHA-256 hex string for a file. Returns empty string on error. */
    fun compute(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(65536).use { input ->
                val buf = ByteArray(65536)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    md.update(buf, 0, read)
                }
            }
            md.digest().joinToString("") { byte -> "%02x".format(byte) }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Verify file against expected hash.
     * Returns true if expectedHash is blank (skip verification)
     * or if the computed hash matches (case-insensitive).
     */
    fun verify(file: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return true
        val actual = compute(file)
        return actual.isNotEmpty() && actual.equals(expectedHash.trim(), ignoreCase = true)
    }
}
