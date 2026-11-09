package com.reachnivi.focus.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reachnivi.focus.FocusApp
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.launcher.LauncherScreen
import com.reachnivi.focus.ui.launcher.LauncherViewModel
import com.reachnivi.focus.ui.theme.FocusTheme

/** Focus's optional minimal home screen. Registered for the HOME intent; the user picks it as default. */
class LauncherActivity : ComponentActivity() {

    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A home screen has nowhere to go back to.
        onBackPressedDispatcher.addCallback(this) { }
        val container = (application as FocusApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                FocusTheme {
                    val vm: LauncherViewModel = viewModel { LauncherViewModel(container) }
                    LauncherScreen(vm, homePresses)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
    }
}
