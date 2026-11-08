package com.reachnivi.focus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reachnivi.focus.ui.FocusRoot
import com.reachnivi.focus.ui.theme.FocusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FocusApp).container
        setContent {
            FocusTheme {
                FocusRoot(container)
            }
        }
    }
}
