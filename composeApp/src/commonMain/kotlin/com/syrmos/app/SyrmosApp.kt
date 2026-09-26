package com.syrmos.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import com.syrmos.core.designsystem.layout.LocalFloatingBarInset
import com.syrmos.core.designsystem.layout.LocalLauncherEndInset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import com.syrmos.core.common.layout.PaneRole
import com.syrmos.core.common.layout.WorkspaceArrangement
import com.syrmos.core.common.layout.WorkspaceTask
import com.syrmos.core.designsystem.layout.rememberContentWorkspace
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import com.syrmos.core.common.layout.ContentBreakpoint
import com.syrmos.core.common.layout.ContentMode
import com.syrmos.core.designsystem.layout.LocalReservedRegions
import com.syrmos.app.platform.rememberReservedRegions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.syrmos.app.platform.consumePendingAssistantQuery
import com.syrmos.app.platform.markOnboardingCompleted
import com.syrmos.app.platform.readOnboardingCompleted
import com.syrmos.app.platform.readLastWhatsNewVersion
import com.syrmos.app.platform.markWhatsNewSeen
import com.syrmos.app.platform.readSelectedTabId
import com.syrmos.app.platform.writeSelectedTabId
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
import com.syrmos.core.data.sync.AnnouncementsRepository
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
    val announcements = koinInject<AnnouncementsRepository>()
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
        runCatching { announcements.hydrateFromBundleIfNeeded() }
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
        // A root Surface provides the theme's content colour (LocalContentColor).
        // Without it Material falls back to black for any text that names no
        // colour, which only shows in dark mode: the Home headings and the hero
        // title read as near-black on the dark canvas.
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
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
                val whatsNewVersion = "3.0.0"
                var showWhatsNew by remember { mutableStateOf(readLastWhatsNewVersion() != whatsNewVersion) }
                if (showWhatsNew) {
                    WhatsNewDialog(onDismiss = {
                        markWhatsNewSeen(whatsNewVersion)
                        showWhatsNew = false
                    })
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalReservedRegions provides rememberReservedRegions(),
                ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (isWebPlatform && maxWidth >= 900.dp) {
                        DesktopWebApp()
                    } else {
                        val initialTab = remember { tabFromId(readSelectedTabId()) }
                        TabNavigator(initialTab) {
                            val pendingQuery = remember { consumePendingAssistantQuery() }
                            var showAriadne by remember { mutableStateOf(pendingQuery != null) }
                            androidx.compose.runtime.CompositionLocalProvider(
                                LocalAriadneOpener provides { showAriadne = true }
                            ) {
                            val lang by LocalizationManager.language.collectAsState()
                            val tabNavigator = LocalTabNavigator.current
                            val currentTab = tabNavigator.current
                            LaunchedEffect(Unit) {
                                NotificationNavBus.events.collect { event ->
                                    tabNavigator.current = HomeTab
                                    NotificationNavBus.dispatchToHome(event)
                                }
                            }
                            // Continuity: after a cold launch / process death with a
                            // persisted live GO session, return the rider to their
                            // journey instead of the last tab (prompt section 7: do not
                            // jump to Home during GO). The gate is a per-process field, so
                            // this fires once on a genuine cold start and NOT on an
                            // activity recreation (rotation / fold), which would otherwise
                            // yank the user to Home and stack a duplicate GO. The Home
                            // navigator rebuilds guidance from the snapshot and pushes GO.
                            LaunchedEffect(Unit) {
                                if (!GoResumeGate.consumed) {
                                    GoResumeGate.consumed = true
                                    if (com.syrmos.app.journey.ActiveJourneyRepository.active.value != null) {
                                        tabNavigator.current = HomeTab
                                        NotificationNavBus.dispatchToHome(NotificationNavEvent.ResumeGo)
                                    }
                                }
                            }
                            LaunchedEffect(currentTab) {
                                writeSelectedTabId(tabId(currentTab))
                            }
                            // Hide the launcher on More (would sit on the
                            // scrolling controls), on Map (the Locate + Vehicles
                            // buttons already own bottom-right) and while GO is on
                            // screen (its controls and timeline own the bottom; iOS
                            // GO shows no launcher either).
                            val showLauncher = currentTab != MoreTab && currentTab != MapTab &&
                                !com.syrmos.app.journey.GoScreenPresence.onScreen

                            // Adaptive navigation (prompt section 5, Android). A
                            // native large window (tablet / unfolded foldable) uses a
                            // navigation rail; the compact phone window keeps the
                            // floating liquid-glass bottom bar. The web desktop shell
                            // above still owns >=900dp on web. Driven by the shared
                            // ContentBreakpoint rule so iOS/web resolve identically.
                            // Content width rules apply to the region AFTER the rail.
                            val layout = ContentBreakpoint.resolve(
                                width = maxWidth.value.toInt(),
                                height = maxHeight.value.toInt(),
                            )
                            val useRail = !isWebPlatform && layout.mode != ContentMode.COMPACT

                            // CurrentTab + the Ariadne launcher pill + the full-screen
                            // assistant overlay. Shared by both nav layouts so the
                            // per-tab screens and their state are identical either way.
                            // One assistant view model for the shell's lifetime, so the
                            // conversation survives closing, reopening and a move between
                            // the docked pane and the full-screen presentation (master
                            // plan, Ariadne: moving between sheet and pane never resends).
                            val assistantViewModel = koinInject<com.syrmos.feature.home.assistant.AssistantViewModel>()
                            LaunchedEffect(showAriadne) {
                                if (showAriadne) {
                                    com.syrmos.app.platform.requestUserLocation()?.let {
                                        assistantViewModel.onLocationUpdate(it.latitude, it.longitude)
                                    }
                                    if (pendingQuery != null) {
                                        assistantViewModel.ask(pendingQuery)
                                    }
                                }
                            }
                            @Composable
                            fun AssistantHost() {
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

                            // Foldables and tablets: with room for two panes the
                            // conversation docks beside the content instead of covering
                            // it. The shared policy decides (ARIADNE task) on the canvas
                            // after the rail; a phone keeps the full-screen presentation.
                            val assistantCanvasWidth = maxWidth.value.toInt() - (if (useRail) 80 else 0)
                            val assistantWs = rememberContentWorkspace(
                                task = WorkspaceTask.ARIADNE,
                                width = assistantCanvasWidth,
                                height = maxHeight.value.toInt(),
                            )
                            val dockAssistant = useRail && assistantWs.arrangement == WorkspaceArrangement.SIDE_BY_SIDE
                            val dockWidth = minOf(assistantWs.pane(PaneRole.COMPANION)?.rect?.width ?: 400, 480).dp

                            @Composable
                            fun BoxScope.TabContentWithOverlays(pillBottomInset: Dp) {
                            // Tabs read the real bottom clearance instead of guessing it.
                            CompositionLocalProvider(
                                LocalFloatingBarInset provides pillBottomInset,
                                LocalLauncherEndInset provides (if (showLauncher && !showAriadne) 84.dp else 16.dp),
                            ) {
                                if (dockAssistant && showAriadne) {
                                    Row(Modifier.fillMaxSize()) {
                                        Box(Modifier.weight(1f).fillMaxHeight()) { CurrentTab() }
                                        VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                        Box(Modifier.width(dockWidth).fillMaxHeight()) { AssistantHost() }
                                    }
                                } else {
                                CurrentTab()
                                AnimatedVisibility(
                                    visible = showLauncher && !showAriadne,
                                    enter = fadeIn() + slideInVertically(
                                        initialOffsetY = { it / 2 },
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    ),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                                    // Lift the pill above the system navigation bar and,
                                    // in compact, above the LiquidGlassTabBar (~96dp);
                                    // in rail mode there is no bottom bar so 16dp is enough.
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(end = 16.dp, bottom = pillBottomInset)
                                        .zIndex(2f),
                                ) {
                                    AriadneLauncherPill(
                                        label = askAriadneLabel(lang),
                                        onClick = { showAriadne = true },
                                    )
                                }


                                if (showAriadne) {
                                    Box(modifier = Modifier.fillMaxSize().zIndex(3f)) { AssistantHost() }
                                }
                                }
                            }
                            }

                            if (useRail) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    SyrmosNavigationRail()
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        TabContentWithOverlays(pillBottomInset = 16.dp)
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    TabContentWithOverlays(pillBottomInset = 96.dp)
                                    LiquidGlassTabBar(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .windowInsetsPadding(WindowInsets.navigationBars),
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
}

/**
 * Per-process gate for the one-shot GO resume. A plain field survives an activity
 * recreation (same process) so rotation does not re-trigger the resume, and resets
 * on a genuine process death (new process) so a cold launch resumes once.
 */
private object GoResumeGate {
    var consumed: Boolean = false
}

private fun tabFromId(id: String?): Tab = when (id) {
    "explore" -> ExploreTab
    "map" -> MapTab
    "departures" -> DeparturesTab
    "more" -> MoreTab
    else -> HomeTab
}

private fun tabId(tab: Tab): String = when (tab) {
    ExploreTab -> "explore"
    MapTab -> "map"
    DeparturesTab -> "departures"
    MoreTab -> "more"
    else -> "home"
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

/**
 * Adaptive navigation rail for native large windows (tablet / unfolded foldable).
 * Same five destinations and order as the compact bottom bar; the rail consumes
 * its width first, then the content breakpoint rules apply to the remainder.
 * Items are centred so the rail reads as a peer of the bottom bar, not a toolbar.
 */
@Composable
private fun SyrmosNavigationRail() {
    val tabNavigator = LocalTabNavigator.current
    val items = listOf(
        HomeTab to Icons.Filled.Home,
        ExploreTab to Icons.Filled.Explore,
        MapTab to Icons.Filled.Map,
        DeparturesTab to Icons.Filled.Flight,
        MoreTab to Icons.Filled.MoreVert,
    )
    NavigationRail(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Spacer(Modifier.weight(1f))
        items.forEach { (tab, icon) ->
            NavigationRailItem(
                selected = tabNavigator.current == tab,
                onClick = { tabNavigator.current = tab },
                icon = { Icon(imageVector = icon, contentDescription = tab.options.title) },
                label = { Text(tab.options.title) },
            )
        }
        Spacer(Modifier.weight(1f))
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
