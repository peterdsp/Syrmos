package com.syrmos.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.syrmos.app.platform.consumePendingAssistantQuery
import com.syrmos.app.platform.consumePendingNotificationDeepLink
import com.syrmos.app.platform.markOnboardingCompleted
import com.syrmos.app.platform.readOnboardingCompleted
import com.syrmos.app.platform.readLastWhatsNewVersion
import com.syrmos.app.platform.markWhatsNewSeen
import com.syrmos.app.screen.OnboardingScreen
import com.syrmos.app.screen.WhatsNewDialog
import org.jetbrains.compose.resources.painterResource
import syrmos.composeapp.generated.resources.Res
import syrmos.composeapp.generated.resources.ariadne_mark
import syrmos.composeapp.generated.resources.start_screen
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.syrmos.app.tab.DeparturesTab
import com.syrmos.app.tab.ExploreTab
import com.syrmos.app.tab.HomeTab
import com.syrmos.app.tab.MapTab
import com.syrmos.app.tab.MoreTab
import com.syrmos.core.data.seed.DataSeeder
import com.syrmos.core.data.seed.LinesRefresher
import com.syrmos.core.data.sync.FaresRepository
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.data.sync.VisualOverridesRepository
import com.syrmos.core.common.AppThemeMode
import com.syrmos.core.common.ThemeManager
import com.syrmos.core.designsystem.component.liquidGlassOverlay
import com.syrmos.core.designsystem.theme.SyrmosTheme
import org.koin.compose.koinInject

@Composable
fun SyrmosApp() {
    val dataSeeder = koinInject<DataSeeder>()
    val linesRefresher = koinInject<LinesRefresher>()
    val scheduleSync = koinInject<ScheduleSyncRepository>()
    val stationOffsets = koinInject<StationOffsetsRepository>()
    val fares = koinInject<FaresRepository>()
    val visualOverrides = koinInject<VisualOverridesRepository>()
    var isSeeded by remember { mutableStateOf(false) }
    var hasCompletedOnboarding by remember { mutableStateOf(readOnboardingCompleted()) }

    LaunchedEffect(Unit) {
        val startedAt = kotlin.time.TimeSource.Monotonic.markNow()
        try {
            dataSeeder.seedIfNeeded()
        } catch (_: Exception) {
        }
        val held = startedAt.elapsedNow().inWholeMilliseconds
        if (held < 3500L) {
            kotlinx.coroutines.delay(3500L - held)
        }
        isSeeded = true
        // Hydrate from bundled snapshot first so the projector + visuals have
        // correct data immediately, even on airplane mode. Live refresh then
        // overlays anything newer when the network is available.
        // Offline-first: hydrate ONLY from the bundled JSON snapshot.
        // No network refresh on app start — Syrmos is an offline app,
        // every schedule / fare / icon / station offset ships in the
        // bundle. The user triggers a server refresh exclusively via
        // Settings -> Check now. Removing the auto-refresh pass that
        // used to fire here means the app never silently talks to the
        // Pi, which was leaking clock drift / API hiccups into the UI.
        runCatching { scheduleSync.hydrateFromBundleIfNeeded() }
        runCatching { stationOffsets.hydrateFromBundleIfNeeded() }
        runCatching { fares.hydrateFromBundleIfNeeded() }
        runCatching { visualOverrides.hydrateFromBundleIfNeeded() }
    }

    val themeMode by ThemeManager.theme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemDark
    }

    SyrmosTheme(darkTheme = darkTheme) {
        if (!isSeeded) {
            BootSplash()
        } else if (!hasCompletedOnboarding) {
            OnboardingScreen(onComplete = {
                markOnboardingCompleted()
                hasCompletedOnboarding = true
            })
        } else {
            // One-time highlights after an install/update. The web build shows
            // its own card (web-map.js), so this is effectively the native path.
            val whatsNewVersion = "2.0.0"
            var showWhatsNew by remember { mutableStateOf(readLastWhatsNewVersion() != whatsNewVersion) }
            if (showWhatsNew) {
                WhatsNewDialog(onDismiss = {
                    markWhatsNewSeen(whatsNewVersion)
                    showWhatsNew = false
                })
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (isWebPlatform && maxWidth >= 900.dp) {
                    DesktopWebApp()
                } else {
                    TabNavigator(HomeTab) {
                        val pendingQuery = remember { consumePendingAssistantQuery() }
                        var showAriadne by remember { mutableStateOf(pendingQuery != null) }
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalAriadneOpener provides { showAriadne = true }
                        ) {
                        val pendingNotif = remember { consumePendingNotificationDeepLink() }
                        val tabNavigator2 = LocalTabNavigator.current
                        LaunchedEffect(pendingNotif) {
                            if (pendingNotif != null) {
                                tabNavigator2.current = HomeTab
                            }
                        }
                        val lang by LocalizationManager.language.collectAsState()
                        val tabNavigator = LocalTabNavigator.current
                        val currentTab = tabNavigator.current
                        // Hide the launcher on More (would sit on the
                        // scrolling controls) and on Map (the Locate +
                        // Vehicles buttons already own bottom-right).
                        val showLauncher = currentTab != MoreTab && currentTab != MapTab

                        Box(modifier = Modifier.fillMaxSize()) {
                            CurrentTab()
                            LiquidGlassTabBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .windowInsetsPadding(WindowInsets.navigationBars),
                            )

                            // App-level Ariadne launcher pill: floats above
                            // the tab bar on Home / Explore / Map / Departures.
                            // Hidden on More so the settings scroll isn't
                            // obstructed by a chat pill. Slides in with a
                            // spring so tab changes feel physical.
                            AnimatedVisibility(
                                visible = showLauncher && !showAriadne,
                                enter = fadeIn() + slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                ),
                                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                                // Lift the pill above the system navigation
                                // bar (three-button / gesture bar) AND above
                                // the LiquidGlassTabBar. The tab bar itself
                                // uses windowInsetsPadding + ~64dp of visual
                                // height + 4dp margin, so we need ~96dp of
                                // clearance ON TOP of the nav-bar inset. On
                                // devices with a tall three-button nav bar
                                // (Xiaomi HyperOS etc) the previous fixed
                                // 90dp overlapped the tab bar.
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(end = 16.dp, bottom = 96.dp)
                                    .zIndex(2f),
                            ) {
                                AriadneLauncherPill(
                                    label = askAriadneLabel(lang),
                                    onClick = { showAriadne = true },
                                )
                            }

                            if (showAriadne) {
                                val assistantViewModel = koinInject<com.syrmos.feature.home.assistant.AssistantViewModel>()
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    com.syrmos.app.platform.requestUserLocation()?.let {
                                        assistantViewModel.onLocationUpdate(it.latitude, it.longitude)
                                    }
                                    if (pendingQuery != null) {
                                        assistantViewModel.ask(pendingQuery)
                                    }
                                }
                                Box(modifier = Modifier.fillMaxSize().zIndex(3f)) {
                                    com.syrmos.feature.home.assistant.AssistantScreen(
                                        viewModel = assistantViewModel,
                                        onClose = { showAriadne = false },
                                        onOpenStation = { stationId ->
                                            showAriadne = false
                                            tabNavigator.current = HomeTab
                                            AriadneNavBus.navigate(AriadneNavEvent.Station(stationId))
                                        },
                                        onOpenLine = { lineId ->
                                            showAriadne = false
                                            tabNavigator.current = HomeTab
                                            AriadneNavBus.navigate(AriadneNavEvent.Line(lineId))
                                        },
                                    )
                                }
                            }
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
            .background(Color(0xFF0A1628)),
    ) {
        Image(
            painter = painterResource(Res.drawable.start_screen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LiquidGlassTabBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().liquidGlassOverlay(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassTabItem(HomeTab, Icons.Filled.Home)
            LiquidGlassTabItem(ExploreTab, Icons.Filled.Explore)
            LiquidGlassTabItem(MapTab, Icons.Filled.Map)
            LiquidGlassTabItem(DeparturesTab, Icons.Filled.Flight)
            LiquidGlassTabItem(MoreTab, Icons.Filled.MoreVert)
        }
    }
}

@Composable
private fun LiquidGlassTabItem(
    tab: Tab,
    icon: ImageVector,
) {
    val tabNavigator = LocalTabNavigator.current
    val selected = tabNavigator.current == tab
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clickable(onClick = { tabNavigator.current = tab })
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = tab.options.title, tint = tint)
        Text(
            text = tab.options.title,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * App-level Ariadne launcher. Owl glyph (Athena's owl -> wisdom /
 * Athens) matches the branding on the web. Sits above the tab bar
 * across Home / Explore / Map / Departures; hidden on More.
 */
@Composable
private fun AriadneLauncherPill(label: String, onClick: () -> Unit) {
    Image(
        painter = painterResource(Res.drawable.ariadne_mark),
        contentDescription = label,
        modifier = Modifier
            .size(56.dp)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    )
}

private fun askAriadneLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Ρώτα την Αριάδνη"
    AppLanguage.ALBANIAN -> "Pyet Ariadne"
    AppLanguage.ITALIAN -> "Chiedi ad Ariadne"
    else -> "Ask Ariadne"
}
