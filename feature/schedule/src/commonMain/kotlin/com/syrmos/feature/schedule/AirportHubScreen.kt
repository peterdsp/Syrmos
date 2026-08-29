package com.syrmos.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

data class AirportCalendarTrip(
    val id: String,
    val title: String,
    val startEpochMillis: Long,
    val location: String,
)

@Composable
fun AirportHubScreen(
    calendarTrips: List<AirportCalendarTrip>,
    calendarConnected: Boolean,
    onConnectCalendar: () -> Unit,
) {
    val sync = koinInject<ScheduleSyncRepository>()
    val offsetsRepo = koinInject<StationOffsetsRepository>()
    val bundles by sync.lineBundles.collectAsState()
    val offsets by offsetsRepo.offsets.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    val zone = remember { TimeZone.of("Europe/Athens") }

    var dayOffset by remember { mutableIntStateOf(0) }
    var selectedCity by remember { mutableStateOf(AirportCity.ATHENS) }
    var selectedRoute by remember { mutableStateOf("M3") }
    var flightMinutes by remember { mutableIntStateOf(18 * 60 + 40) }
    val hub = remember(selectedCity) { airportHub(selectedCity) }

    val selectedTrip = remember(calendarTrips, dayOffset) {
        val selectedDate = airportTodayPlus(dayOffset)
        calendarTrips.firstOrNull { trip ->
            Instant.fromEpochMilliseconds(trip.startEpochMillis).toLocalDateTime(zone).date == selectedDate
        }
    }

    LaunchedEffect(selectedTrip?.id) {
        selectedTrip?.let { trip ->
            val time = Instant.fromEpochMilliseconds(trip.startEpochMillis).toLocalDateTime(zone).time
            flightMinutes = time.hour * 60 + time.minute
        }
    }

    LaunchedEffect(Unit) {
        sync.hydrateFromBundleIfNeeded()
        offsetsRepo.hydrateFromBundleIfNeeded()
    }

    val now = Clock.System.now().toLocalDateTime(zone)
    val airportDepartures = remember(bundles, offsets, dayOffset, now.date) {
        DepartureProjection.compute(
            bundles = bundles,
            offsets = offsetsRepo,
            stationId = "M3_AER",
            lineId = "M3",
            dayOffset = dayOffset,
            now = now,
        )
    }
    val suburbanAirportDepartures = remember(bundles, offsets, dayOffset, now.date) {
        DepartureProjection.compute(
            bundles = bundles,
            offsets = offsetsRepo,
            stationId = "A1_AIR",
            lineId = "A1",
            dayOffset = dayOffset,
            now = now,
        )
    }
    val airportBoundDepartures = remember(bundles, offsets, dayOffset, now.date) {
        DepartureProjection.compute(
            bundles = bundles,
            offsets = offsetsRepo,
            stationId = "M3_SYN",
            lineId = "M3",
            dayOffset = dayOffset,
            now = now,
        ).filter { it.destinationLabel.isAirportLabel() }
    }
    // Thessaloniki: next metro departures at each interchange that feeds an
    // airport shuttle (the shuttle bus itself has no per-stop timetable).
    val metroLegDepartures = remember(bundles, offsets, dayOffset, now.date, selectedCity) {
        hub.metroLegs.associate { leg ->
            leg.stationId to DepartureProjection.compute(
                bundles = bundles,
                offsets = offsetsRepo,
                stationId = leg.stationId,
                lineId = leg.lineId,
                dayOffset = dayOffset,
                now = now,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 126.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AirportHero(hub, lang)
        AirportCitySelector(selectedCity, lang, onSelect = {
            selectedCity = it
            selectedRoute = "M3"
        })
        CalendarHub(
            lang = lang,
            selectedDay = dayOffset,
            flightMinutes = flightMinutes,
            selectedTrip = selectedTrip,
            calendarConnected = calendarConnected,
            onConnectCalendar = onConnectCalendar,
            onDaySelected = { dayOffset = it },
            onFlightMinutesChanged = { flightMinutes = it.coerceIn(0, 1439) },
        )
        if (hub.hasDirectRail) {
            AirportRouteOverview(lang, selectedRoute, dayOffset, onRouteSelected = { selectedRoute = it })
            PredictiveItinerary(lang, flightMinutes, airportBoundDepartures, selectedTrip?.title)
            NextAirportServices(lang, dayOffset, airportDepartures, suburbanAirportDepartures, now.time.hour * 60 + now.time.minute)
            Text(
                text = airportText(lang, "Airport services", "Υπηρεσίες αεροδρομίου", "Shërbimet e aeroportit", "Servizi aeroportuali"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            AirportDepartureRows(lang, dayOffset, airportDepartures, suburbanAirportDepartures)
        } else {
            AirportConnections(hub, lang)
            Text(
                text = airportText(lang, "Metro departures to the airport shuttle", "Αναχωρήσεις μετρό προς το λεωφορείο αεροδρομίου", "Nisjet e metros drejt autobusit të aeroportit", "Partenze metro verso la navetta aeroporto"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            AirportMetroLegs(hub, lang, dayOffset, metroLegDepartures, now.time.hour * 60 + now.time.minute)
        }
        AirportAlert(lang)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AirportHero(hub: AirportHubData, lang: AppLanguage) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(hub.gradient))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(color = Color.White.copy(alpha = 0.16f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AirplanemodeActive, null, tint = Color.White, modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    airportText(lang, "AIRPORT", "ΑΕΡΟΔΡΟΜΙΟ", "AEROPORTI", "AEROPORTO"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Text(hub.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    hub.subtitle.t(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            Surface(color = Color.White.copy(alpha = 0.16f), shape = CircleShape) {
                Text(hub.code, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        // Mode pills wrap as complete units so a label like "M3" or "24/7" is
        // never compressed and split across lines. The schedule-status chip
        // sits on its own trailing line, so it never competes with the pills
        // for width and can never clip at the card edge.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hub.pills.forEach { (label, icon) -> HeroPill(label, icon) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF63E6A6)))
                Spacer(Modifier.width(5.dp))
                Text(
                    airportText(lang, "Schedules", "Ωράρια", "Oraret", "Orari"),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun AirportCitySelector(selected: AirportCity, lang: AppLanguage, onSelect: (AirportCity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(airportHub(AirportCity.ATHENS), airportHub(AirportCity.THESSALONIKI)).forEach { hub ->
            val isSelected = hub.city == selected
            val shape = RoundedCornerShape(14.dp)
            val background: Modifier = if (isSelected) {
                Modifier.background(Brush.horizontalGradient(hub.gradient), shape)
            } else {
                Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), shape)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(background)
                    .clickable { onSelect(hub.city) }
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.AirplanemodeActive,
                    null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )
                Column {
                    Text(hub.cityName.t(lang), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                    Text(hub.code, style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AirportConnections(hub: AirportHubData, lang: AppLanguage) {
    AirportCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(airportText(lang, "How to reach the airport", "Πώς να φτάσετε στο αεροδρόμιο", "Si të shkoni në aeroport", "Come raggiungere l'aeroporto"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    airportText(lang, "The metro does not reach the terminal yet, so finish on a shuttle bus.", "Το μετρό δεν φτάνει ακόμη στον τερματικό, οπότε ολοκληρώστε με λεωφορείο.", "Metroja nuk arrin ende te terminali, prandaj përfundoni me autobus.", "La metro non arriva ancora al terminal, quindi si prosegue in navetta."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            hub.connections.forEach { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = RoundedCornerShape(11.dp), color = c.color, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (c.isBus) Icons.Filled.DirectionsBus else Icons.Filled.Train, null, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = CircleShape, color = c.color.copy(alpha = 0.12f)) {
                                Text(c.badge, color = c.color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                            Text(c.title.t(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(c.detail.t(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                airportText(lang, "Shuttle and city bus times are set by OASTH/OSETH. The metro departures below are live from the timetable.", "Τα δρομολόγια λεωφορείων ορίζονται από τον ΟΑΣΘ/ΟΣΕΘ. Οι αναχωρήσεις μετρό παρακάτω είναι από το ωράριο.", "Oraret e autobusëve caktohen nga OASTH/OSETH. Nisjet e metros më poshtë janë nga orari.", "Gli orari dei bus sono fissati da OASTH/OSETH. Le partenze metro qui sotto sono da orario."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AirportMetroLegs(hub: AirportHubData, lang: AppLanguage, dayOffset: Int, legDepartures: Map<String, List<ProjectedDeparture>>, nowMinutes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        hub.metroLegs.forEach { leg ->
            val deps = legDepartures[leg.stationId].orEmpty()
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = leg.color, modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(leg.badge, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(leg.stationName.t(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(leg.towards.t(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (deps.isEmpty()) {
                        Text(
                            airportText(lang, "No scheduled metro departure in the current window.", "Καμία προγραμματισμένη αναχώρηση μετρό αυτή τη στιγμή.", "Asnjë nisje e programuar e metros tani.", "Nessuna partenza metro programmata al momento."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            deps.take(4).forEach { dep ->
                                val label = if (dayOffset == 0) formatMinutes((dep.timeMinutes - nowMinutes).coerceAtLeast(0), lang) else dep.time
                                Surface(shape = CircleShape, color = leg.color.copy(alpha = 0.12f)) {
                                    Text(label, color = leg.color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
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
private fun HeroPill(label: String, icon: ImageVector) {
    Row(
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CalendarHub(
    lang: AppLanguage,
    selectedDay: Int,
    flightMinutes: Int,
    selectedTrip: AirportCalendarTrip?,
    calendarConnected: Boolean,
    onConnectCalendar: () -> Unit,
    onDaySelected: (Int) -> Unit,
    onFlightMinutesChanged: (Int) -> Unit,
) {
    AirportCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarMonth, null, tint = SyrmosColorTokens.metroBlue)
                Spacer(Modifier.width(8.dp))
                Text(airportText(lang, "Calendar Hub", "Κέντρο ημερολογίου", "Qendra e kalendarit", "Centro calendario"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(airportDateLabel(selectedDay, lang), style = MaterialTheme.typography.labelMedium, color = SyrmosColorTokens.metroBlue, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (offset in 0..6) {
                    val selected = offset == selectedDay
                    Surface(
                        shape = CircleShape,
                        color = if (selected) SyrmosColorTokens.metroBlue else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(46.dp).clickable { onDaySelected(offset) },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(airportDayLabel(offset, lang), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(airportTodayPlus(offset).dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SyrmosColorTokens.metroBlue.copy(alpha = 0.08f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (selectedTrip != null) airportText(lang, "SAVED AIRPORT TRIP", "ΑΠΟΘΗΚΕΥΜΕΝΟ ΤΑΞΙΔΙ", "UDHETIM I RUAJTUR", "VIAGGIO SALVATO")
                        else airportText(lang, "PLANNED DEPARTURE", "ΠΡΟΓΡΑΜΜΑΤΙΣΜΕΝΗ ΑΝΑΧΩΡΗΣΗ", "NISJE E PLANIFIKUAR", "PARTENZA PIANIFICATA"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        selectedTrip?.title ?: airportText(lang, "No saved airport trip", "Δεν υπαρχει αποθηκευμενο ταξιδι", "Nuk ka udhetim te ruajtur", "Nessun viaggio salvato"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !calendarConnected, onClick = onConnectCalendar),
                    ) {
                        Icon(
                            if (calendarConnected) Icons.Filled.CheckCircle else Icons.Filled.CalendarMonth,
                            null,
                            tint = if (calendarConnected) SyrmosColorTokens.live else SyrmosColorTokens.metroBlue,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (selectedTrip != null) airportText(lang, "From device calendar", "Απο το ημερολογιο συσκευης", "Nga kalendari i pajisjes", "Dal calendario del dispositivo")
                            else if (calendarConnected) airportText(lang, "Calendar connected", "Το ημερολογιο συνδεθηκε", "Kalendari u lidh", "Calendario collegato")
                            else airportText(lang, "Connect device calendar", "Συνδεση ημερολογιου συσκευης", "Lidh kalendarin e pajisjes", "Collega il calendario"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (calendarConnected) SyrmosColorTokens.live else SyrmosColorTokens.metroBlue,
                        )
                    }
                }
                IconButton(onClick = { onFlightMinutesChanged(flightMinutes - 10) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Remove, null, modifier = Modifier.size(18.dp))
                }
                Text(clockString(flightMinutes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SyrmosColorTokens.metroBlue)
                IconButton(onClick = { onFlightMinutesChanged(flightMinutes + 10) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AirportRouteOverview(lang: AppLanguage, selectedRoute: String, dayOffset: Int, onRouteSelected: (String) -> Unit) {
    AirportCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(airportText(lang, "Airport route overview", "Επισκόπηση διαδρομών αεροδρομίου", "Pamja e linjave të aeroportit", "Panoramica percorsi aeroporto"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (dayOffset == 0) airportText(lang, "Every stop on the way to the terminal", "Κάθε στάση στη διαδρομή προς τον τερματικό", "Çdo stacion drejt terminalit", "Ogni fermata verso il terminal")
                else airportText(lang, "Planned service for the selected day", "Προγραμματισμένη υπηρεσία για την επιλεγμένη ημέρα", "Shërbimi i planifikuar për ditën e zgjedhur", "Servizio previsto per il giorno selezionato"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("M3", "A1", "X95", "X93", "X96", "X97").forEach { route ->
                    val color = airportRouteColor(route)
                    val selected = route == selectedRoute
                    Surface(
                        color = if (selected) color else color.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.clickable { onRouteSelected(route) },
                    ) {
                        Text(route, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = if (selected) Color.White else color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            AirportRouteStrip(selectedRoute, lang)
        }
    }
}

private data class RouteStripStop(val label: String, val isAirport: Boolean, val isInterchange: Boolean)

private fun airportRouteStops(route: String, lang: AppLanguage): List<RouteStripStop> {
    val airport = airportText(lang, "Airport", "Αεροδρόμιο", "Aeroporti", "Aeroporto")
    fun stop(label: String) = RouteStripStop(label, false, false)
    fun interchange(label: String) = RouteStripStop(label, false, true)
    val terminal = RouteStripStop(airport, true, false)
    return when (route) {
        // Real termini + the major airport-route interchanges (stable stops).
        "M3" -> listOf(stop("Dimotiko Theatro"), interchange("Syntagma"), interchange("Doukissis Plakentias"), terminal)
        "A1" -> listOf(stop("Piraeus"), interchange("Athens"), interchange("Doukissis Plakentias"), terminal)
        "X95" -> listOf(stop("Syntagma"), terminal)
        "X93" -> listOf(stop(airportText(lang, "Kifisos B Station", "ΚΤΕΛ Κηφισού", "Stacioni Kifisos", "Stazione Kifisos")), terminal)
        "X96" -> listOf(stop(airportText(lang, "Piraeus", "Πειραιάς", "Pireus", "Pireo")), terminal)
        "X97" -> listOf(stop("Elliniko"), terminal)
        else -> listOf(stop(airportText(lang, "City", "Πόλη", "Qyteti", "Città")), terminal)
    }
}

private fun airportServiceNote(route: String, lang: AppLanguage): String = when (route) {
    "M3" -> airportText(lang, "Metro Line 3, direct to the terminal", "Μετρό Γραμμή 3, απευθείας στον τερματικό", "Metro Linja 3, direkt te terminali", "Metro Linea 3, diretto al terminal")
    "A1" -> airportText(lang, "Suburban A1, direct to the terminal", "Προαστιακός Α1, απευθείας στον τερματικό", "Suburban A1, direkt te terminali", "Suburbano A1, diretto al terminal")
    else -> airportText(lang, "24-hour express bus. Times set by OASA.", "24ωρο λεωφορείο express. Ωράρια από τον ΟΑΣΑ.", "Autobus express 24 orë. Oraret nga OASA.", "Bus express 24 ore. Orari da OASA.")
}

@Composable
private fun AirportRouteStrip(route: String, lang: AppLanguage) {
    val color = airportRouteColor(route)
    val stops = airportRouteStops(route, lang)
    val isBus = route.startsWith("X")
    val lineColor = if (isBus) color.copy(alpha = 0.35f) else color.copy(alpha = 0.8f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stops.first().label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("→", color = color, fontWeight = FontWeight.Bold)
            Text(stops.last().label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.05f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                stops.forEachIndexed { index, stop ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
                            Row(Modifier.fillMaxWidth().height(3.dp)) {
                                Box(Modifier.weight(1f).fillMaxHeight().background(if (index == 0) Color.Transparent else lineColor))
                                Box(Modifier.weight(1f).fillMaxHeight().background(if (index == stops.lastIndex) Color.Transparent else lineColor))
                            }
                            RouteStripDot(stop, color)
                        }
                        Text(
                            stop.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (stop.isAirport) FontWeight.Bold else FontWeight.Normal,
                            color = if (stop.isAirport) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (isBus) Icons.Filled.DirectionsBus else Icons.Filled.Train, null, tint = color, modifier = Modifier.size(14.dp))
            Text(airportServiceNote(route, lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RouteStripDot(stop: RouteStripStop, color: Color) {
    when {
        stop.isAirport -> Surface(shape = CircleShape, color = color, modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.AirplanemodeActive, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
        }
        stop.isInterchange -> Box(
            Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(3.dp, color, CircleShape),
        )
        else -> Box(Modifier.size(11.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun PredictiveItinerary(lang: AppLanguage, flightMinutes: Int, departures: List<ProjectedDeparture>, tripTitle: String?) {
    val target = flightMinutes - 133
    val metroMinutes = departures.mapNotNull { d -> clockMinutes(d.time) }.lastOrNull { it <= target }
        ?: departures.firstOrNull()?.time?.let(::clockMinutes)

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(SyrmosColorTokens.metroBlue.copy(alpha = 0.12f), SyrmosColorTokens.suburban.copy(alpha = 0.09f)))).border(1.dp, SyrmosColorTokens.metroBlue.copy(alpha = 0.15f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = SyrmosColorTokens.metroBlue)
            Spacer(Modifier.width(8.dp))
            Text(airportText(lang, "Smart trip plan", "Έξυπνο πλάνο ταξιδιού", "Plani i mençur i udhëtimit", "Piano di viaggio intelligente"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                tripTitle?.let { airportText(lang, "For $it", "Για $it", "Per $it", "Per $it") }
                    ?: airportText(lang, "Manual plan", "Χειροκινητο πλανο", "Plan manual", "Piano manuale"),
                style = MaterialTheme.typography.labelSmall,
                color = SyrmosColorTokens.suburban,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metroMinutes != null) {
            ItineraryTimeline(
                leaveTime = clockString(metroMinutes - 12),
                boardTime = clockString(metroMinutes),
                arriveTime = clockString(metroMinutes + 43),
                lang = lang,
            )
        } else {
            Text(
                airportText(lang, "No scheduled M3 departure was found for this date. Choose another day or check official operator information.", "Δεν βρέθηκε προγραμματισμένη αναχώρηση M3 για αυτή την ημερομηνία. Επιλέξτε άλλη ημέρα ή ελέγξτε τις επίσημες πληροφορίες.", "Nuk u gjet nisje e programuar M3 për këtë datë. Zgjidh një ditë tjetër ose kontrollo informacionin zyrtar.", "Nessuna partenza M3 programmata trovata per questa data. Scegli un altro giorno o controlla le informazioni ufficiali."),
                style = MaterialTheme.typography.bodySmall,
                color = SyrmosColorTokens.disruption,
            )
        }
        Text(
            airportText(lang, "Includes the selected day's timetable and a 90 minute airport buffer.", "Περιλαμβάνει το ωράριο της επιλεγμένης ημέρας και περιθώριο 90 λεπτών.", "Përfshin orarin e ditës së zgjedhur dhe 90 minuta rezervë në aeroport.", "Include l'orario del giorno selezionato e 90 minuti di margine in aeroporto."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// A connected 3-node timeline: a continuous line joins the icon centers (each
// step is equal width, so centers land at 1/6, 1/2, 5/6) with the leg durations
// pinned to the midpoints. Replaces the old floating 18dp dashes that read as
// broken disconnected lines.
@Composable
private fun ItineraryTimeline(leaveTime: String, boardTime: String, arriveTime: String, lang: AppLanguage) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fullWidth = maxWidth
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val y = size.height / 2f
            drawLine(
                SyrmosColorTokens.metroBlue.copy(alpha = 0.35f),
                Offset(size.width / 6f, y),
                Offset(size.width * 5f / 6f, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ItineraryStep(leaveTime, airportText(lang, "Leave", "Αναχώρηση", "Nisja", "Parti"), Icons.AutoMirrored.Filled.DirectionsWalk, Modifier.weight(1f))
            ItineraryStep(boardTime, "M3 · Syntagma", Icons.Filled.Train, Modifier.weight(1f))
            ItineraryStep(arriveTime, airportText(lang, "Terminal", "Τερματικός", "Terminali", "Terminal"), Icons.Filled.AirplanemodeActive, Modifier.weight(1f))
        }
        DurationPill("12 min", Modifier.align(Alignment.TopStart).offset(x = fullWidth / 3f - 20.dp, y = 8.dp))
        DurationPill("43 min", Modifier.align(Alignment.TopStart).offset(x = fullWidth * 2f / 3f - 20.dp, y = 8.dp))
    }
}

@Composable
private fun ItineraryStep(time: String, title: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(2.dp, SyrmosColorTokens.metroBlue.copy(alpha = 0.85f)), modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = SyrmosColorTokens.metroBlue, modifier = Modifier.size(18.dp)) }
        }
        Text(time, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DurationPill(text: String, modifier: Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, SyrmosColorTokens.metroBlue.copy(alpha = 0.2f))) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = SyrmosColorTokens.metroBlue, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NextAirportServices(lang: AppLanguage, dayOffset: Int, departures: List<ProjectedDeparture>, suburbanDepartures: List<ProjectedDeparture>, nowMinutes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(airportText(lang, "Next services from the airport", "Επόμενα δρομολόγια από το αεροδρόμιο", "Shërbimet e radhës nga aeroporti", "Prossimi servizi dall'aeroporto"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val next = departures.firstOrNull()
            val main = if (next == null) "-" else if (dayOffset == 0) formatMinutes((next.timeMinutes - nowMinutes).coerceAtLeast(0), lang) else next.time
            val following = departures.getOrNull(1)?.time
            ServiceTile("M3", Icons.Filled.Train, main, "Syntagma", following?.let { airportText(lang, "Then $it", "Έπειτα $it", "Pastaj $it", "Poi $it") } ?: airportText(lang, "No later departure in the current schedule", "Δεν υπάρχει επόμενη αναχώρηση στο τρέχον ωράριο", "Nuk ka nisje tjetër në orarin aktual", "Nessuna partenza successiva nell'orario attuale"), airportText(lang, "Scheduled", "Προγραμματισμένο", "Programuar", "Programmato"), SyrmosColorTokens.metroBlue, Modifier.weight(1f))
            val nextA1 = suburbanDepartures.firstOrNull()
            val mainA1 = if (nextA1 == null) "-" else if (dayOffset == 0) formatMinutes((nextA1.timeMinutes - nowMinutes).coerceAtLeast(0), lang) else nextA1.time
            val followingA1 = suburbanDepartures.getOrNull(1)?.time
            ServiceTile("A1", Icons.Filled.Train, mainA1, airportText(lang, "Piraeus", "Πειραιάς", "Pireus", "Pireo"), followingA1?.let { airportText(lang, "Then $it", "Έπειτα $it", "Pastaj $it", "Poi $it") } ?: airportText(lang, "No later departure in the current schedule", "Δεν υπάρχει επόμενη αναχώρηση στο τρέχον ωράριο", "Nuk ka nisje tjetër në orarin aktual", "Nessuna partenza successiva nell'orario attuale"), airportText(lang, "Scheduled", "Προγραμματισμένο", "Programuar", "Programmato"), SyrmosColorTokens.suburban, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ServiceTile(route: String, icon: ImageVector, main: String, destination: String, detail: String, status: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(route, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.weight(1f))
                Text(status, style = MaterialTheme.typography.labelSmall, color = color)
            }
            Text(main, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (route == "M3") SyrmosColorTokens.warning else color, maxLines = 1)
            Text(destination, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AirportDepartureRows(lang: AppLanguage, dayOffset: Int, departures: List<ProjectedDeparture>, suburbanDepartures: List<ProjectedDeparture>) {
    val metroRows = departures.take(3).map { AirportRow("M3", "Syntagma", airportText(lang, "Scheduled metro departure", "Προγραμματισμένη αναχώρηση μετρό", "Nisje e programuar e metrosë", "Partenza metro programmata"), it.time, SyrmosColorTokens.metroBlue) }
    val suburbanRows = suburbanDepartures.take(2).map { AirportRow("A1", airportText(lang, "Piraeus", "Πειραιάς", "Pireus", "Pireo"), airportText(lang, "Scheduled suburban departure", "Προγραμματισμένη αναχώρηση προαστιακού", "Nisje e programuar e trenit periferik", "Partenza suburbano programmata"), it.time, SyrmosColorTokens.suburban) }
    val busRows = listOf(
        AirportRow("X95", "Syntagma", airportText(lang, "24-hour express bus. Check OASA for current times.", "24ωρο λεωφορείο express. Ελέγξτε τον ΟΑΣΑ για τα τρέχοντα δρομολόγια.", "Autobus express 24 orë. Kontrollo OASA për oraret aktuale.", "Bus express 24 ore. Controlla OASA per gli orari attuali."), "24/7", SyrmosColorTokens.warning),
        AirportRow("X93", "Kifisos", airportText(lang, "24-hour express bus. Check OASA for current times.", "24ωρο λεωφορείο express. Ελέγξτε τον ΟΑΣΑ για τα τρέχοντα δρομολόγια.", "Autobus express 24 orë. Kontrollo OASA për oraret aktuale.", "Bus express 24 ore. Controlla OASA per gli orari attuali."), "24/7", SyrmosColorTokens.warning),
        AirportRow("X96", airportText(lang, "Piraeus", "Πειραιάς", "Pireus", "Pireo"), airportText(lang, "24-hour express bus. Check OASA for current times.", "24ωρο λεωφορείο express. Ελέγξτε τον ΟΑΣΑ για τα τρέχοντα δρομολόγια.", "Autobus express 24 orë. Kontrollo OASA për oraret aktuale.", "Bus express 24 ore. Controlla OASA per gli orari attuali."), "24/7", SyrmosColorTokens.warning),
        AirportRow("X97", airportText(lang, "Elliniko", "Ελληνικό", "Elliniko", "Elliniko"), airportText(lang, "24-hour express bus. Check OASA for current times.", "24ωρο λεωφορείο express. Ελέγξτε τον ΟΑΣΑ για τα τρέχοντα δρομολόγια.", "Autobus express 24 orë. Kontrollo OASA për oraret aktuale.", "Bus express 24 ore. Controlla OASA per gli orari attuali."), "24/7", SyrmosColorTokens.warning),
    )
    val rows = metroRows + suburbanRows + busRows
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.take(9).forEach { row ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = row.color, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(row.route, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.destination, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(row.time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = row.color)
                }
            }
        }
    }
}

@Composable
private fun AirportAlert(lang: AppLanguage) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SyrmosColorTokens.disruption.copy(alpha = 0.06f)).border(1.dp, SyrmosColorTokens.disruption.copy(alpha = 0.16f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, null, tint = SyrmosColorTokens.disruption)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(airportText(lang, "Service alerts", "Ειδοποιήσεις υπηρεσίας", "Njoftime shërbimi", "Avvisi di servizio"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                airportText(lang, "Check official operator notices before leaving. Syrmos does not infer a clear status when no alert feed is available.", "Ελέγξτε τις επίσημες ανακοινώσεις πριν φύγετε. Το Syrmos δεν συμπεραίνει ότι όλα λειτουργούν κανονικά όταν δεν υπάρχει ροή ειδοποιήσεων.", "Kontrollo njoftimet zyrtare para nisjes. Syrmos nuk supozon se gjithçka është në rregull kur nuk ka burim njoftimesh.", "Controlla gli avvisi ufficiali prima di partire. Syrmos non presume che tutto sia regolare quando non è disponibile un feed di avvisi."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AirportCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

private data class AirportRow(val route: String, val destination: String, val detail: String, val time: String, val color: Color)

// MARK: - Multi-airport hubs (mirrors iOS AirportData.swift)

enum class AirportCity { ATHENS, THESSALONIKI }

private data class AirportL10n(val en: String, val el: String, val sq: String, val it: String) {
    fun t(lang: AppLanguage): String = airportText(lang, en, el, sq, it)
}

private data class AirportConnectionData(
    val badge: String,
    val color: Color,
    val isBus: Boolean,
    val title: AirportL10n,
    val detail: AirportL10n,
)

private data class AirportMetroLegData(
    val stationId: String,
    val lineId: String,
    val badge: String,
    val color: Color,
    val stationName: AirportL10n,
    val towards: AirportL10n,
)

private data class AirportHubData(
    val city: AirportCity,
    val code: String,
    val name: String,
    val cityName: AirportL10n,
    val subtitle: AirportL10n,
    val gradient: List<Color>,
    val pills: List<Pair<String, ImageVector>>,
    val hasDirectRail: Boolean,
    val connections: List<AirportConnectionData>,
    val metroLegs: List<AirportMetroLegData>,
)

private fun airportHub(city: AirportCity): AirportHubData = when (city) {
    AirportCity.ATHENS -> AirportHubData(
        city = city,
        code = "ATH",
        name = "Eleftherios Venizelos",
        cityName = AirportL10n("Athens", "Αθήνα", "Athinë", "Atene"),
        subtitle = AirportL10n(
            "Routes, scheduled departures and trip planning",
            "Διαδρομές, προγραμματισμένες αναχωρήσεις και σχεδιασμός",
            "Linja, nisje të programuara dhe planifikim udhëtimi",
            "Percorsi, partenze programmate e pianificazione",
        ),
        gradient = listOf(Color(0xFF0B3D71), Color(0xFF155E9F), Color(0xFF45398F)),
        pills = listOf(
            "M3" to Icons.Filled.Train,
            "A1" to Icons.Filled.Train,
            "X95" to Icons.Filled.DirectionsBus,
            "24/7" to Icons.Filled.AccessTime,
        ),
        hasDirectRail = true,
        connections = emptyList(),
        metroLegs = emptyList(),
    )
    AirportCity.THESSALONIKI -> AirportHubData(
        city = city,
        code = "SKG",
        name = "Makedonia",
        cityName = AirportL10n("Thessaloniki", "Θεσσαλονίκη", "Selanik", "Salonicco"),
        subtitle = AirportL10n(
            "Metro plus a shuttle, or a direct bus to the terminal",
            "Μετρό και λεωφορείο, ή απευθείας λεωφορείο στον τερματικό",
            "Metro plus autobus, ose autobus i drejtpërdrejtë te terminali",
            "Metro più navetta, o bus diretto al terminal",
        ),
        gradient = listOf(Color(0xFF0B5563), Color(0xFF0E7C8B), Color(0xFF1E5FA0)),
        pills = listOf(
            "L2" to Icons.Filled.Train,
            "X3" to Icons.Filled.DirectionsBus,
            "1X" to Icons.Filled.DirectionsBus,
            "24/7" to Icons.Filled.AccessTime,
        ),
        hasDirectRail = false,
        connections = listOf(
            AirportConnectionData(
                badge = "L2 + X3",
                color = Color(0xFF0070FF),
                isBus = false,
                title = AirportL10n("Metro Line 2 + X3 shuttle", "Μετρό Γραμμή 2 + λεωφορείο Χ3", "Metro Linja 2 + autobusi X3", "Metro Linea 2 + navetta X3"),
                detail = AirportL10n(
                    "Ride Line 2 to Mikra, then the X3 shuttle to the terminal (about 10 min).",
                    "Με τη Γραμμή 2 ως τη Μίκρα, μετά το λεωφορείο Χ3 στον τερματικό (περίπου 10 λεπτά).",
                    "Merr Linjën 2 deri te Mikra, pastaj autobusin X3 te terminali (rreth 10 min).",
                    "Con la Linea 2 fino a Mikra, poi la navetta X3 al terminal (circa 10 min).",
                ),
            ),
            AirportConnectionData(
                badge = "L1 + 2X",
                color = Color(0xFFFF0000),
                isBus = false,
                title = AirportL10n("Metro Line 1 + 2X shuttle", "Μετρό Γραμμή 1 + λεωφορείο 2Χ", "Metro Linja 1 + autobusi 2X", "Metro Linea 1 + navetta 2X"),
                detail = AirportL10n(
                    "Ride Line 1 to Nea Elvetia, then the 2X shuttle to the terminal.",
                    "Με τη Γραμμή 1 ως τη Νέα Ελβετία, μετά το λεωφορείο 2Χ στον τερματικό.",
                    "Merr Linjën 1 deri te Nea Elvetia, pastaj autobusin 2X te terminali.",
                    "Con la Linea 1 fino a Nea Elvetia, poi la navetta 2X al terminal.",
                ),
            ),
            AirportConnectionData(
                badge = "1X / 1N",
                color = Color(0xFF5B6770),
                isBus = true,
                title = AirportL10n("Direct airport bus", "Απευθείας λεωφορείο αεροδρομίου", "Autobus i drejtpërdrejtë", "Bus diretto aeroporto"),
                detail = AirportL10n(
                    "1X links the terminal with the city centre, the New Railway Station and KTEL Makedonia. 1N runs overnight.",
                    "Το 1Χ συνδέει τον τερματικό με το κέντρο, τον Νέο Σιδηροδρομικό Σταθμό και τα ΚΤΕΛ Μακεδονία. Το 1Ν λειτουργεί τη νύχτα.",
                    "1X lidh terminalin me qendrën, Stacionin e Ri Hekurudhor dhe KTEL Makedonia. 1N punon natën.",
                    "1X collega il terminal con il centro, la nuova stazione ferroviaria e KTEL Makedonia. 1N opera di notte.",
                ),
            ),
            AirportConnectionData(
                badge = "79",
                color = Color(0xFF5B6770),
                isBus = true,
                title = AirportL10n("Bus 79", "Λεωφορείο 79", "Autobusi 79", "Autobus 79"),
                detail = AirportL10n(
                    "Connects the terminal with the eastern bus station (IKEA / Pylaia).",
                    "Συνδέει τον τερματικό με τον ανατολικό σταθμό υπεραστικών (IKEA / Πυλαία).",
                    "Lidh terminalin me stacionin lindor të autobusëve (IKEA / Pylaia).",
                    "Collega il terminal con la stazione bus orientale (IKEA / Pylaia).",
                ),
            ),
        ),
        metroLegs = listOf(
            AirportMetroLegData(
                stationId = "TM2_MIK",
                lineId = "TM2",
                badge = "L2",
                color = Color(0xFF0070FF),
                stationName = AirportL10n("Mikra", "Μίκρα", "Mikra", "Mikra"),
                towards = AirportL10n("X3 shuttle to the terminal", "Λεωφορείο Χ3 στον τερματικό", "Autobusi X3 te terminali", "Navetta X3 al terminal"),
            ),
            AirportMetroLegData(
                stationId = "TM1_NEL",
                lineId = "TM1",
                badge = "L1",
                color = Color(0xFFFF0000),
                stationName = AirportL10n("Nea Elvetia", "Νέα Ελβετία", "Nea Elvetia", "Nea Elvetia"),
                towards = AirportL10n("2X shuttle to the terminal", "Λεωφορείο 2Χ στον τερματικό", "Autobusi 2X te terminali", "Navetta 2X al terminal"),
            ),
        ),
    )
}

private fun airportText(lang: AppLanguage, en: String, el: String, sq: String, it: String): String = when (lang) {
    AppLanguage.GREEK -> el
    AppLanguage.ALBANIAN -> sq
    AppLanguage.ITALIAN -> it
    else -> en
}

private fun String.isAirportLabel(): Boolean {
    val normalized = lowercase()
    return normalized.contains("airport") || normalized.contains("aeroport") || normalized.contains("αεροδρόμιο")
}

private fun airportRouteColor(route: String): Color = when (route) {
    "M3" -> SyrmosColorTokens.metroBlue
    "A1" -> SyrmosColorTokens.suburban
    "X95", "X93", "X96", "X97" -> SyrmosColorTokens.warning
    else -> SyrmosColorTokens.suburban
}

private fun clockMinutes(value: String): Int? {
    val parts = value.split(":").mapNotNull { it.toIntOrNull() }
    return if (parts.size == 2) parts[0] * 60 + parts[1] else null
}

private fun clockString(rawMinutes: Int): String {
    val minutes = ((rawMinutes % 1440) + 1440) % 1440
    return "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
}

private fun formatMinutes(minutes: Int, lang: AppLanguage): String {
    if (minutes <= 1) return airportText(lang, "Now", "Τώρα", "Tani", "Ora")
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}min"
}

private fun airportDayLabel(offset: Int, lang: AppLanguage): String {
    if (offset == 0) return airportText(lang, "TODAY", "ΣΗΜ", "SOT", "OGGI")
    return when (lang to airportTodayPlus(offset).dayOfWeek) {
        AppLanguage.GREEK to DayOfWeek.MONDAY -> "ΔΕΥ"; AppLanguage.GREEK to DayOfWeek.TUESDAY -> "ΤΡΙ"; AppLanguage.GREEK to DayOfWeek.WEDNESDAY -> "ΤΕΤ"; AppLanguage.GREEK to DayOfWeek.THURSDAY -> "ΠΕΜ"; AppLanguage.GREEK to DayOfWeek.FRIDAY -> "ΠΑΡ"; AppLanguage.GREEK to DayOfWeek.SATURDAY -> "ΣΑΒ"; AppLanguage.GREEK to DayOfWeek.SUNDAY -> "ΚΥΡ"
        AppLanguage.ALBANIAN to DayOfWeek.MONDAY -> "HËN"; AppLanguage.ALBANIAN to DayOfWeek.TUESDAY -> "MAR"; AppLanguage.ALBANIAN to DayOfWeek.WEDNESDAY -> "MËR"; AppLanguage.ALBANIAN to DayOfWeek.THURSDAY -> "ENJ"; AppLanguage.ALBANIAN to DayOfWeek.FRIDAY -> "PRE"; AppLanguage.ALBANIAN to DayOfWeek.SATURDAY -> "SHT"; AppLanguage.ALBANIAN to DayOfWeek.SUNDAY -> "DIE"
        AppLanguage.ITALIAN to DayOfWeek.MONDAY -> "LUN"; AppLanguage.ITALIAN to DayOfWeek.TUESDAY -> "MAR"; AppLanguage.ITALIAN to DayOfWeek.WEDNESDAY -> "MER"; AppLanguage.ITALIAN to DayOfWeek.THURSDAY -> "GIO"; AppLanguage.ITALIAN to DayOfWeek.FRIDAY -> "VEN"; AppLanguage.ITALIAN to DayOfWeek.SATURDAY -> "SAB"; AppLanguage.ITALIAN to DayOfWeek.SUNDAY -> "DOM"
        else -> airportTodayPlus(offset).dayOfWeek.name.take(3)
    }
}

private fun airportDateLabel(offset: Int, lang: AppLanguage): String {
    val date = airportTodayPlus(offset)
    return "${airportDayLabel(offset, lang)} ${date.dayOfMonth}/${date.monthNumber}"
}

private fun airportTodayPlus(offset: Int): LocalDate {
    var date = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens")).date
    repeat(offset) { date = airportNextDay(date) }
    return date
}

private fun airportNextDay(date: LocalDate): LocalDate {
    val days = when (date.monthNumber) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if ((date.year % 4 == 0 && date.year % 100 != 0) || date.year % 400 == 0) 29 else 28
    }
    return when {
        date.dayOfMonth < days -> LocalDate(date.year, date.monthNumber, date.dayOfMonth + 1)
        date.monthNumber < 12 -> LocalDate(date.year, date.monthNumber + 1, 1)
        else -> LocalDate(date.year + 1, 1, 1)
    }
}
