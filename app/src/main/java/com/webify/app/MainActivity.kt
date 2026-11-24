package com.webify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.ui.nav.WebifyRoot
import com.webify.app.ui.theme.WebifyAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val container = (application as WebifyApplication).container
        setContent {
            val ui by container.securePrefs.uiSettings.collectAsStateWithLifecycle()
            WebifyAppTheme(darkOverride = ui.darkMode, dynamicColor = ui.dynamicColor) {
                WebifyRoot(container)
            }
        }
    }
}
