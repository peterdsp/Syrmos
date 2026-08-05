package com.syrmos.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.model.weather.CurrentWeather
import com.syrmos.core.model.weather.WeatherCondition
import com.syrmos.core.model.weather.WeatherSnapshot
import kotlin.math.roundToInt

/**
 * Weather travel-context card, in the vibrant gradient style of the shared
 * weather-app design: a condition-tinted gradient, a big temperature, the
 * emoji glyph, "feels like", and the place. Pure surfacing of [WeatherSnapshot].
 */
@Composable
fun WeatherCard(snapshot: WeatherSnapshot, lang: AppLanguage) {
    val w = snapshot.current
    val colors = gradientFor(w.condition, w.isDay)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(colors))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = snapshot.placeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.95f),
                )
                Text(
                    text = conditionLabel(w.condition, lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Text(text = glyph(w.condition, w.isDay), fontSize = 40.sp)
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "${w.temperatureC.roundToInt()}°",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            if (snapshot.highC != null && snapshot.lowC != null) {
                Text(
                    text = "H:${snapshot.highC!!.roundToInt()}°  L:${snapshot.lowC!!.roundToInt()}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        if (snapshot.hourly.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                snapshot.hourly.take(6).forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(h.hourLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                        Text(glyph(h.condition, w.isDay), fontSize = 18.sp)
                        Text("${h.temperatureC.roundToInt()}°", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WeatherStat(feelsLikeLabel(lang), "${w.apparentC.roundToInt()}°")
            WeatherStat(humidityLabel(lang), "${w.humidity}%")
            WeatherStat(windLabel(lang), "${w.windKph.roundToInt()} km/h")
        }
    }
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

private fun gradientFor(condition: WeatherCondition, isDay: Boolean): List<Color> {
    if (!isDay) return listOf(Color(0xFF1A237E), Color(0xFF311B92))
    return when (condition) {
        WeatherCondition.CLEAR -> listOf(Color(0xFF2196F3), Color(0xFF4FC3F7))
        WeatherCondition.PARTLY_CLOUDY -> listOf(Color(0xFF42A5F5), Color(0xFF90CAF9))
        WeatherCondition.CLOUDY, WeatherCondition.FOG -> listOf(Color(0xFF607D8B), Color(0xFF90A4AE))
        WeatherCondition.DRIZZLE, WeatherCondition.RAIN, WeatherCondition.SHOWERS ->
            listOf(Color(0xFF455A64), Color(0xFF546E7A))
        WeatherCondition.THUNDERSTORM -> listOf(Color(0xFF37474F), Color(0xFF455A64))
        WeatherCondition.SNOW -> listOf(Color(0xFF78909C), Color(0xFFB0BEC5))
        WeatherCondition.UNKNOWN -> listOf(Color(0xFF42A5F5), Color(0xFF90CAF9))
    }
}

private fun glyph(condition: WeatherCondition, isDay: Boolean): String = when (condition) {
    WeatherCondition.CLEAR -> if (isDay) "☀️" else "🌙"
    WeatherCondition.PARTLY_CLOUDY -> if (isDay) "⛅" else "☁️"
    WeatherCondition.CLOUDY -> "☁️"
    WeatherCondition.FOG -> "🌫️"
    WeatherCondition.DRIZZLE, WeatherCondition.SHOWERS -> "🌦️"
    WeatherCondition.RAIN -> "🌧️"
    WeatherCondition.SNOW -> "❄️"
    WeatherCondition.THUNDERSTORM -> "⛈️"
    WeatherCondition.UNKNOWN -> "🌡️"
}

internal fun conditionLabel(condition: WeatherCondition, lang: AppLanguage): String = when (condition) {
    WeatherCondition.CLEAR -> tri(lang, "Clear", "Αίθριος", "Kthjellët", "Sereno")
    WeatherCondition.PARTLY_CLOUDY -> tri(lang, "Partly cloudy", "Λίγα σύννεφα", "Pjesërisht me re", "Parzialmente nuvoloso")
    WeatherCondition.CLOUDY -> tri(lang, "Cloudy", "Συννεφιά", "Me re", "Nuvoloso")
    WeatherCondition.FOG -> tri(lang, "Fog", "Ομίχλη", "Mjegull", "Nebbia")
    WeatherCondition.DRIZZLE -> tri(lang, "Drizzle", "Ψιχάλα", "Shi i imët", "Pioviggine")
    WeatherCondition.RAIN -> tri(lang, "Rain", "Βροχή", "Shi", "Pioggia")
    WeatherCondition.SHOWERS -> tri(lang, "Showers", "Μπόρες", "Rrebeshe", "Rovesci")
    WeatherCondition.SNOW -> tri(lang, "Snow", "Χιόνι", "Borë", "Neve")
    WeatherCondition.THUNDERSTORM -> tri(lang, "Thunderstorm", "Καταιγίδα", "Stuhi", "Temporale")
    WeatherCondition.UNKNOWN -> tri(lang, "Weather", "Καιρός", "Moti", "Meteo")
}

private fun feelsLikeLabel(lang: AppLanguage) = tri(lang, "Feels like", "Αίσθηση", "Ndihet si", "Percepita")
private fun humidityLabel(lang: AppLanguage) = tri(lang, "Humidity", "Υγρασία", "Lagështia", "Umidità")
private fun windLabel(lang: AppLanguage) = tri(lang, "Wind", "Άνεμος", "Era", "Vento")

private fun tri(lang: AppLanguage, en: String, el: String, sq: String, it: String) = when (lang) {
    AppLanguage.GREEK -> el
    AppLanguage.ALBANIAN -> sq
    AppLanguage.ITALIAN -> it
    else -> en
}
