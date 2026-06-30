package com.system.services

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Crash recovery screen.
 *
 * FIX: Replaced deprecated onBackPressed() override with OnBackPressedCallback
 * (required for Android 13+ correct behaviour).
 */
class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ERROR  = "error"
        const val EXTRA_STACK  = "stacktrace"
        const val EXTRA_DEVICE = "device_info"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val tvMsg      = findViewById<TextView>(R.id.tvCrashMsg)
        val tvStack    = findViewById<TextView>(R.id.tvCrashStack)
        val btnRestart = findViewById<Button>(R.id.btnCrashRestart)

        tvMsg.text   = intent.getStringExtra(EXTRA_ERROR) ?: "An unexpected error occurred."
        tvStack.text = intent.getStringExtra(EXTRA_STACK) ?: ""

        btnRestart.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }

        // FIX: Use OnBackPressedCallback instead of deprecated onBackPressed()
        // Block back press — user must explicitly restart from the crash screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* blocked */ }
        })
    }
}
