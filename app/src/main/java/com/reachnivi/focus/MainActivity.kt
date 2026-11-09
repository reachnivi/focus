package com.reachnivi.focus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.reachnivi.focus.widget.FocusWidget
import kotlinx.coroutines.launch
import com.reachnivi.focus.ui.FocusRoot
import com.reachnivi.focus.ui.theme.FocusTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { FocusWidget.refresh(applicationContext) }
    }

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
