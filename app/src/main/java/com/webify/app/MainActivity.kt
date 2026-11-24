package com.webify.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.ui.nav.WebifyRoot
import com.webify.app.ui.theme.WebifyAppTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val container = (application as WebifyApplication).container
        handleIntent(intent)
        // A5: izin notifikasi agar hasil build bisa dikabarkan saat Webify di latar belakang
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val ui by container.securePrefs.uiSettings.collectAsStateWithLifecycle()
            WebifyAppTheme(darkOverride = ui.darkMode, dynamicColor = ui.dynamicColor) {
                WebifyRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_OPEN) ?: return
        (application as WebifyApplication).container.navRequests.value = target
        intent.removeExtra(EXTRA_OPEN)
    }

    companion object {
        const val EXTRA_OPEN = "webify_open"
        const val OPEN_CONSOLE = "console"
        const val OPEN_CRASHES = "crashes"
    }
}
