package com.syrmos.app.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syrmos.app.platform.requestLocationPermission
import com.syrmos.app.platform.requestNotificationPermission
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val tint: Color,
    val title: L,
    val body: L,
    val ctaLabel: L? = null,
    val isLocationStep: Boolean = false,
    val isNotificationStep: Boolean = false,
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val lang by LocalizationManager.language.collectAsState()

    val pages = listOf(
        OnboardingPage(
            icon = Icons.Filled.DirectionsTransit,
            tint = SyrmosColorTokens.brand,
            title = L.ONBOARD_WELCOME_TITLE,
            body = L.ONBOARD_WELCOME_BODY,
        ),
        OnboardingPage(
            icon = Icons.Filled.AccessTime,
            tint = SyrmosColorTokens.metroRed,
            title = L.ONBOARD_LIVE_TITLE,
            body = L.ONBOARD_LIVE_BODY,
        ),
        OnboardingPage(
            icon = Icons.Filled.LocationOn,
            tint = SyrmosColorTokens.tram,
            title = L.ONBOARD_LOCATION_TITLE,
            body = L.ONBOARD_LOCATION_BODY,
            ctaLabel = L.ONBOARD_LOCATION_CTA,
            isLocationStep = true,
        ),
        OnboardingPage(
            icon = Icons.Filled.Notifications,
            tint = SyrmosColorTokens.metroRed,
            title = L.ONBOARD_NOTIF_TITLE,
            body = L.ONBOARD_NOTIF_BODY,
            ctaLabel = L.ONBOARD_NOTIF_CTA,
            isNotificationStep = true,
        ),
        OnboardingPage(
            icon = Icons.Filled.Verified,
            tint = SyrmosColorTokens.brand,
            title = L.ONBOARD_PRIVACY_TITLE,
            body = L.ONBOARD_PRIVACY_BODY,
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        )
                    )
                )
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { index ->
                    PageContent(page = pages[index], lang = lang)
                }

                Indicator(currentPage = pagerState.currentPage, total = pages.size)
                Spacer(modifier = Modifier.height(16.dp))

                val currentPage = pages[pagerState.currentPage]
                val isLast = pagerState.currentPage == pages.lastIndex

                Button(
                    onClick = {
                        if (currentPage.isLocationStep) {
                            scope.launch { requestLocationPermission() }
                        }
                        if (currentPage.isNotificationStep) {
                            scope.launch { requestNotificationPermission() }
                        }
                        if (isLast) {
                            onComplete()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = when {
                            isLast -> L.ONBOARD_GET_STARTED.text(lang)
                            currentPage.ctaLabel != null -> currentPage.ctaLabel.text(lang)
                            else -> L.ONBOARD_CONTINUE.text(lang)
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                if (!isLast) {
                    TextButton(onClick = onComplete) {
                        Text(L.ONBOARD_SKIP.text(lang))
                    }
                } else {
                    Spacer(modifier = Modifier.height(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage, lang: AppLanguage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = page.tint,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = page.title.text(lang),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = page.body.text(lang),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Indicator(currentPage: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val active = i == currentPage
            val width by animateFloatAsState(if (active) 22f else 6f, label = "indicatorWidth")
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

