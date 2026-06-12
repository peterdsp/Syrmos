package com.syrmos.core.designsystem.component

import org.jetbrains.compose.resources.DrawableResource
import syrmos.core.designsystem.generated.resources.Res
import syrmos.core.designsystem.generated.resources.metro_m1_left_to_piraeus
import syrmos.core.designsystem.generated.resources.metro_m1_right_to_kifissia
import syrmos.core.designsystem.generated.resources.metro_m2_left_to_anthoupoli
import syrmos.core.designsystem.generated.resources.metro_m2_right_to_elliniko
import syrmos.core.designsystem.generated.resources.metro_m3_left_to_dimotiko_theatro
import syrmos.core.designsystem.generated.resources.metro_m3_right_to_airport
import syrmos.core.designsystem.generated.resources.metro_m3_right_to_doukissis_plakentias
import syrmos.core.designsystem.generated.resources.train_p1_left_to_piraeus
import syrmos.core.designsystem.generated.resources.train_p1_right_to_airport
import syrmos.core.designsystem.generated.resources.train_p1a_left_to_ano_liosia
import syrmos.core.designsystem.generated.resources.train_p1a_right_to_airport
import syrmos.core.designsystem.generated.resources.train_p2_left_to_piraeus
import syrmos.core.designsystem.generated.resources.train_p2_right_to_kiato
import syrmos.core.designsystem.generated.resources.train_p3_left_to_athens
import syrmos.core.designsystem.generated.resources.train_p3_right_to_chalkida
import syrmos.core.designsystem.generated.resources.tram_t6_left_to_syntagma
import syrmos.core.designsystem.generated.resources.tram_t6_right_to_pikrodafni
import syrmos.core.designsystem.generated.resources.tram_t7_left_to_akti_posidonos
import syrmos.core.designsystem.generated.resources.tram_t7_right_to_asklipiio_voulas

/**
 * Maps a (lineId, destination text) pair to the matching directional vehicle
 * SVG bundled in commonMain/composeResources/drawable. Mirrors the iOS
 * TimetablesIcons helper so both platforms pick the same artwork.
 *
 * Returns null when no asset matches; the caller falls back to the
 * LineColorIndicator dot so the UI never goes empty.
 */
object VehicleIcons {
    fun resourceFor(lineId: String, destination: String, isAirport: Boolean = false): DrawableResource? {
        val dir = destination.lowercase()
        return when (lineId) {
            "M1" -> if ("piraeus" in dir) Res.drawable.metro_m1_left_to_piraeus else Res.drawable.metro_m1_right_to_kifissia
            "M2" -> if ("anthoupoli" in dir) Res.drawable.metro_m2_left_to_anthoupoli else Res.drawable.metro_m2_right_to_elliniko
            "M3" -> when {
                isAirport -> Res.drawable.metro_m3_right_to_airport
                "dimotiko" in dir || "dimarheio" in dir || "piraeus" in dir -> Res.drawable.metro_m3_left_to_dimotiko_theatro
                else -> Res.drawable.metro_m3_right_to_doukissis_plakentias
            }
            "T6" -> if ("syntagma" in dir) Res.drawable.tram_t6_left_to_syntagma else Res.drawable.tram_t6_right_to_pikrodafni
            "T7" -> if ("akti" in dir || "posidonos" in dir || "piraeus" in dir)
                Res.drawable.tram_t7_left_to_akti_posidonos
            else Res.drawable.tram_t7_right_to_asklipiio_voulas
            "A1" -> if ("piraeus" in dir) Res.drawable.train_p1_left_to_piraeus else Res.drawable.train_p1_right_to_airport
            "A2" -> if ("liosia" in dir) Res.drawable.train_p1a_left_to_ano_liosia else Res.drawable.train_p1a_right_to_airport
            "A3" -> if ("athens" in dir || "αθήνα" in dir) Res.drawable.train_p3_left_to_athens else Res.drawable.train_p3_right_to_chalkida
            "A4" -> if ("piraeus" in dir) Res.drawable.train_p2_left_to_piraeus else Res.drawable.train_p2_right_to_kiato
            else -> null
        }
    }
}
