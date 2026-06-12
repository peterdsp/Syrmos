package com.syrmos.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.theme.ArrivalFar
import com.syrmos.core.designsystem.theme.ArrivalModerate
import com.syrmos.core.designsystem.theme.ArrivalSoon
import com.syrmos.core.model.transit.LineColor
import org.jetbrains.compose.resources.painterResource

@Composable
fun DepartureCard(
    lineName: String,
    lineColor: LineColor,
    direction: String,
    minutesAway: Int,
    departureTime: String,
    modifier: Modifier = Modifier,
    lineId: String? = null,
    isAirport: Boolean = false,
) {
    val vehicleResource = lineId?.let { VehicleIcons.resourceFor(it, direction, isAirport) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vehicleResource != null) {
                    Image(
                        painter = painterResource(vehicleResource),
                        contentDescription = "$lineName $direction",
                        modifier = Modifier.width(44.dp).height(28.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    LineColorIndicator(lineColor = lineColor, size = 16.dp)
                }
                Column {
                    Text(
                        text = lineName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = direction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        minutesAway <= 0 -> "Now"
                        minutesAway == 1 -> "1 min"
                        else -> "$minutesAway min"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        minutesAway <= 2 -> ArrivalSoon
                        minutesAway <= 5 -> ArrivalModerate
                        else -> ArrivalFar
                    },
                )
                Text(
                    text = departureTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
