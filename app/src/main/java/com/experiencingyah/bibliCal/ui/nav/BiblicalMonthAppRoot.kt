package com.experiencingyah.bibliCal.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.experiencingyah.bibliCal.ui.theme.BibliCalThemeTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.ui.screens.CalendarScreen
import com.experiencingyah.bibliCal.ui.screens.PassagesIntegrationScreen
import com.experiencingyah.bibliCal.ui.screens.PassagesRequest
import com.experiencingyah.bibliCal.ui.screens.RecommendedResourcesScreen
import com.experiencingyah.bibliCal.ui.screens.SettingsScreen
import com.experiencingyah.bibliCal.ui.screens.TodayScreen
import com.experiencingyah.bibliCal.ui.screens.WelcomeScreen
import com.experiencingyah.bibliCal.ui.screens.WidgetShowcaseScreen

@Composable
fun BiblicalMonthAppRoot(
    passagesRequest: PassagesRequest?,
    onSendToPassages: () -> Unit,
    onPassagesDismissed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack = navController.currentBackStackEntryAsState()
    val currentRoute = backStack.value?.destination?.route
    val context = LocalContext.current
    val repo = remember { LunarRepository(context) }
    
    var hasAnchor by remember { mutableStateOf<Boolean?>(null) }
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(passagesRequest) {
        Log.d("PassAges", "BiblicalMonthAppRoot passagesRequest=$passagesRequest")
    }
    
    // Check if anchor exists on startup
    LaunchedEffect(Unit) {
        val anchorExists = repo.hasAnyAnchor()
        hasAnchor = anchorExists
        startDestination = if (anchorExists) "today" else "welcome"
    }

    if (passagesRequest != null) {
        PassagesIntegrationScreen(
            request = passagesRequest,
            onSend = onSendToPassages,
            onDismiss = onPassagesDismissed
        )
        return
    }

    val items = listOf(
        NavItem("today", "Today", Icons.Default.Today),
        NavItem("calendar", "Calendar", Icons.Default.CalendarMonth),
        NavItem("settings", "Settings", Icons.Default.Settings),
    )

    // Only show bottom bar if not on welcome, widget showcase, or recommended resources screens
    val showBottomBar = currentRoute != "welcome" && currentRoute != "widget_showcase" && currentRoute != "recommended_resources" && currentRoute != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = BibliCalThemeTokens.colors.outlineSoft,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Wait for start destination to be determined
        startDestination?.let { destination ->
            NavHost(
                navController = navController,
                startDestination = destination,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                composable("welcome") { WelcomeScreen(navController = navController) }
                composable("today") { 
                    TodayScreen(
                        onNavigateToWidgetShowcase = { navController.navigate("widget_showcase") }
                    ) 
                }
                composable("calendar") { CalendarScreen() }
                composable("settings") {
                    SettingsScreen(
                        onNavigateToRecommendedResources = { navController.navigate("recommended_resources") }
                    )
                }
                composable("recommended_resources") {
                    RecommendedResourcesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("widget_showcase") {
                    WidgetShowcaseScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

