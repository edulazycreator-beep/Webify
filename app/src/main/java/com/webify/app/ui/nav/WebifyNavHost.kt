package com.webify.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.webify.app.AppContainer
import com.webify.app.ui.console.BuildConsoleScreen
import com.webify.app.ui.copilot.CopilotScreen
import com.webify.app.ui.dashboard.DashboardScreen
import com.webify.app.ui.studio.StudioPickerScreen
import com.webify.app.ui.studio.StudioScreen
import com.webify.app.ui.vault.KeystoreVaultScreen

object Routes {
    const val PROJECTS = "projects"
    const val STUDIO = "studio"
    const val STUDIO_PROJECT = "studio/{projectId}"
    const val KEYSTORES = "keystores"
    const val COPILOT = "copilot"
    const val CONSOLE = "console"
    fun studio(id: String) = "studio/$id"
}

private data class TopDest(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val topDestinations = listOf(
    TopDest(Routes.PROJECTS, "Projects", Icons.Rounded.SpaceDashboard, Icons.Filled.SpaceDashboard),
    TopDest(Routes.STUDIO, "Studio", Icons.Rounded.Construction, Icons.Filled.Construction),
    TopDest(Routes.KEYSTORES, "Keystore", Icons.Rounded.VpnKey, Icons.Filled.VpnKey),
    TopDest(Routes.COPILOT, "Co-Pilot", Icons.Rounded.AutoAwesome, Icons.Filled.AutoAwesome)
)

@Composable
fun WebifyRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var lastProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val showNav = route != Routes.CONSOLE

    fun selected(dest: TopDest) = when (dest.route) {
        Routes.STUDIO -> route == Routes.STUDIO || route == Routes.STUDIO_PROJECT
        else -> route == dest.route
    }

    fun go(dest: TopDest) {
        val target = if (dest.route == Routes.STUDIO && lastProjectId != null) Routes.studio(lastProjectId!!) else dest.route
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = dest.route != Routes.STUDIO
        }
    }

    val host: @Composable (Modifier) -> Unit = { modifier ->
        WebifyNavGraph(nav, container, modifier, onOpenProject = { lastProjectId = it })
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            if (showNav) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    topDestinations.forEach { d ->
                        val sel = selected(d)
                        NavigationRailItem(
                            selected = sel,
                            onClick = { go(d) },
                            icon = { Icon(if (sel) d.selectedIcon else d.icon, d.label, Modifier.size(24.dp)) },
                            label = { Text(d.label) },
                            colors = if (d.route == Routes.COPILOT) NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                            ) else NavigationRailItemDefaults.colors()
                        )
                    }
                }
            }
            Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
                Box(Modifier.padding(pad).consumeWindowInsets(pad)) { host(Modifier.fillMaxSize()) }
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showNav) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        topDestinations.forEach { d ->
                            val sel = selected(d)
                            NavigationBarItem(
                                selected = sel,
                                onClick = { go(d) },
                                icon = { Icon(if (sel) d.selectedIcon else d.icon, d.label, Modifier.size(24.dp)) },
                                label = { Text(d.label, style = MaterialTheme.typography.labelMedium) },
                                colors = if (d.route == Routes.COPILOT) NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.tertiary,
                                    indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                                ) else NavigationBarItemDefaults.colors()
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).consumeWindowInsets(pad)) { host(Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun WebifyNavGraph(
    nav: NavHostController,
    container: AppContainer,
    modifier: Modifier,
    onOpenProject: (String) -> Unit
) {
    val openStudio: (String) -> Unit = { id ->
        onOpenProject(id)
        nav.navigate(Routes.studio(id)) { launchSingleTop = true }
    }
    val openConsole: () -> Unit = { nav.navigate(Routes.CONSOLE) { launchSingleTop = true } }
    val openCopilot: () -> Unit = {
        nav.navigate(Routes.COPILOT) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.PROJECTS,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(260), initialOffset = { it / 12 }) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(260), targetOffset = { it / 12 }) }
    ) {
        composable(Routes.PROJECTS) {
            DashboardScreen(container = container, onOpenProject = openStudio, onOpenConsole = openConsole)
        }
        composable(Routes.STUDIO) {
            StudioPickerScreen(container = container, onOpenProject = openStudio)
        }
        composable(
            Routes.STUDIO_PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("projectId") ?: return@composable
            LaunchedEffect(id) { onOpenProject(id) }
            StudioScreen(
                projectId = id,
                container = container,
                onBack = { if (!nav.popBackStack()) nav.navigate(Routes.PROJECTS) },
                onOpenConsole = openConsole,
                onOpenCopilot = openCopilot
            )
        }
        composable(Routes.KEYSTORES) { KeystoreVaultScreen(container = container) }
        composable(Routes.COPILOT) { CopilotScreen(container = container) }
        composable(Routes.CONSOLE) {
            BuildConsoleScreen(
                container = container,
                onBack = { if (!nav.popBackStack()) nav.navigate(Routes.PROJECTS) },
                onAnalyzeWithAi = openCopilot
            )
        }
    }
}
