package com.syrmos.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.syrmos.app.tab.HomeTab
import com.syrmos.app.tab.LinesTab
import com.syrmos.app.tab.MapTab
import com.syrmos.app.tab.SettingsTab
import com.syrmos.core.data.seed.DataSeeder
import com.syrmos.core.data.seed.LinesRefresher
import com.syrmos.core.designsystem.theme.SyrmosTheme
import org.koin.compose.koinInject

@Composable
fun SyrmosApp() {
    val dataSeeder = koinInject<DataSeeder>()
    val linesRefresher = koinInject<LinesRefresher>()
    var isSeeded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            dataSeeder.seedIfNeeded()
        } catch (_: Exception) {
        } finally {
            isSeeded = true
        }
        // Best-effort online overlay. The app is fully functional even if
        // this never succeeds; on success it picks up any new stations the
        // server has gained since the last app release.
        runCatching { linesRefresher.refresh() }
    }

    SyrmosTheme {
        if (!isSeeded) {
            BootSplash()
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (isWebPlatform && maxWidth >= 900.dp) {
                    DesktopWebApp()
                } else {
                    TabNavigator(HomeTab) {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                ) {
                                    TabNavItem(HomeTab, Icons.Filled.Home)
                                    TabNavItem(LinesTab, Icons.Filled.DirectionsTransit)
                                    TabNavItem(MapTab, Icons.Filled.Map)
                                    TabNavItem(SettingsTab, Icons.Filled.Settings)
                                }
                            },
                        ) { padding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding),
                            ) {
                                CurrentTab()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BootSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RowScope.TabNavItem(
    tab: Tab,
    icon: ImageVector,
) {
    val tabNavigator = LocalTabNavigator.current

    NavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        label = { Text(tab.options.title) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        icon = {
            Icon(imageVector = icon, contentDescription = tab.options.title)
        },
    )
}
