package com.terminal.rootnode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.terminal.rootnode.ui.theme.LocalBlurEnabled
import com.terminal.rootnode.ui.theme.RootNodeTheme

class MainActivity : ComponentActivity() {
    private var isBlurEnabled by mutableStateOf(false)
    private var blurListener: java.util.function.Consumer<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Hide system bars for immersive experience
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Set up window background blur (Android 12+ / API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(80)
            isBlurEnabled = windowManager.isCrossWindowBlurEnabled
            val listener = java.util.function.Consumer<Boolean> { enabled ->
                isBlurEnabled = enabled
            }
            blurListener = listener
            windowManager.addCrossWindowBlurEnabledListener(mainExecutor, listener)
        } else {
            isBlurEnabled = false
        }

        setContent {
            RootNodeTheme {
                CompositionLocalProvider(LocalBlurEnabled provides isBlurEnabled) {
                    RootNodeNavHost()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener?.let {
                windowManager.removeCrossWindowBlurEnabledListener(it)
            }
        }
    }
}