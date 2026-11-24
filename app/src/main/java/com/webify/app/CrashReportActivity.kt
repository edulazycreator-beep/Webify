package com.webify.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.webify.app.data.crash.CrashReportStore

/**
 * Penerima laporan crash dari aplikasi hasil build debug (WebifyCrashReporter.java).
 * Tanpa UI: menyimpan laporan lalu membuka daftar crash di Webify.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val pkg = intent?.getStringExtra("package") ?: callingPackage ?: "unknown"
        if (!text.isNullOrBlank()) {
            CrashReportStore(this).save(pkg, text)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_CRASHES)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        finish()
    }
}
