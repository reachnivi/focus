package com.reachnivi.focus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reachnivi.focus.AppContainer
import com.reachnivi.focus.ui.block.AppPickerScreen
import com.reachnivi.focus.ui.block.BlockRulesScreen
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.bedtime.BedtimeScreen
import com.reachnivi.focus.ui.connect.AfterCallHost
import com.reachnivi.focus.ui.connect.CircleScreen
import com.reachnivi.focus.ui.focus.FocusSessionScreen
import com.reachnivi.focus.ui.onboarding.OnboardingScreen
import com.reachnivi.focus.ui.onboarding.PermissionsScreen
import com.reachnivi.focus.ui.settings.SettingsScreen
import com.reachnivi.focus.ui.today.TodayScreen
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Icons.Rounded.Insights),
    Focus("focus", "Focus", Icons.Rounded.SelfImprovement),
    Block("block", "Block", Icons.Rounded.Block),
    Settings("settings", "Settings", Icons.Rounded.Tune),
}

object Routes {
    const val PERMISSIONS = "permissions"
    const val CIRCLE = "circle"
    const val BEDTIME = "bedtime"
    const val PICKER = "picker/{purpose}"
    fun picker(purpose: String) = "picker/$purpose"
}

@Composable
fun FocusRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
        val scope = rememberCoroutineScope()
        AnimatedContent(
            targetState = settings?.onboardingDone,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "root",
        ) { done ->
            when (done) {
                null -> Box(Modifier.fillMaxSize()) // first DataStore read, a few ms
                false -> OnboardingScreen(onFinished = { scope.launch { container.settings.setOnboardingDone(true) } })
                true -> {
                    MainScaffold()
                    AfterCallHost()
                }
            }
        }
    }
}

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Tab.Today.route) {
                TodayScreen(
                    onStartFocus = { nav.navigate(Tab.Focus.route) { launchSingleTop = true } },
                    onFixPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onOpenCircle = { nav.navigate(Routes.CIRCLE) },
                )
            }
            composable(Routes.CIRCLE) {
                CircleScreen(onBack = { nav.popBackStack() })
            }
            composable(Tab.Focus.route) {
                FocusSessionScreen(
                    onEditBlockList = { nav.navigate(Tab.Block.route) { launchSingleTop = true } },
                    onBedtime = { nav.navigate(Routes.BEDTIME) },
                )
            }
            composable(Routes.BEDTIME) {
                BedtimeScreen(onBack = { nav.popBackStack() })
            }
            composable(Tab.Block.route) {
                BlockRulesScreen(
                    onPickApps = { nav.navigate(Routes.picker("block")) },
                    onPickPauseApps = { nav.navigate(Routes.picker("pause")) },
                    onPickLimitApp = { nav.navigate(Routes.picker("limit")) },
                    onFixPermissions = { nav.navigate(Routes.PERMISSIONS) },
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    onPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onCircle = { nav.navigate(Routes.CIRCLE) },
                )
            }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onDone = { nav.popBackStack() }, standalone = true)
            }
            composable(Routes.PICKER, arguments = listOf(navArgument("purpose") { type = NavType.StringType })) { entry ->
                val purpose = entry.arguments?.getString("purpose")
                AppPickerScreen(
                    forLimit = purpose == "limit",
                    forPause = purpose == "pause",
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

