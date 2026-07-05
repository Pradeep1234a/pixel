package com.pradeep.pixelgrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pradeep.pixelgrid.ui.MainApp
import com.pradeep.pixelgrid.ui.theme.PixelVaultTheme

import coil.ImageLoader
import coil.decode.VideoFrameDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Coil ImageLoader with VideoFrameDecoder
        val imageLoader = ImageLoader.Builder(applicationContext)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
        coil.Coil.setImageLoader(imageLoader)
        
        // Enable edge-to-edge transparent system bars
        enableEdgeToEdge()

        setContent {
            // Read persisted theme settings, default to true (Dark Theme matches premium design best)
            val themePrefs = remember { getSharedPreferences("pixelvault_settings", MODE_PRIVATE) }
            val systemDefaultDark = isSystemInDarkTheme()
            var darkTheme by remember { 
                mutableStateOf(themePrefs.getBoolean("dark_theme", true)) 
            }

            PixelVaultTheme(darkTheme = darkTheme) {
                MainApp(
                    darkTheme = darkTheme,
                    onThemeChange = { isDark ->
                        darkTheme = isDark
                        themePrefs.edit().putBoolean("dark_theme", isDark).apply()
                    }
                )
            }
        }
    }
}
