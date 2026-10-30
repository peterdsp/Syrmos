package com.syrmos.core.common.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The station dots on the Android map were drawn at their logical-point sizes
 * as raw pixels, so on a 2.75x phone a 22-point dot became an 8dp tap target
 * and riders could not open the station sheet. A marker's bitmap is also its
 * hit rect, so the bitmap, not the dot, is what must clear the minimum.
 */
class MapTapTargetTest {

    @Test fun the_minimum_tap_target_is_at_least_the_android_and_apple_floor() {
        assertTrue(MapDesignTokens.MIN_TAP_TARGET >= 48, "48dp Android / 44pt Apple")
    }

    @Test fun every_zoom_bucket_clears_the_minimum_tap_target() {
        val buckets = listOf(
            MapDesignTokens.DOT_COUNTRY + 5,
            MapDesignTokens.DOT_CITY + 9,
            MapDesignTokens.DOT_SELECTED + 12,
        )
        for (dot in buckets) {
            for (selected in listOf(false, true)) {
                val size = MapDesignTokens.pinBitmapSize(dot, selected)
                assertTrue(
                    size >= MapDesignTokens.MIN_TAP_TARGET,
                    "dot=$dot selected=$selected produced $size, below the tap minimum",
                )
            }
        }
    }

    @Test fun a_small_dot_is_padded_up_rather_than_grown() {
        // The country dot is tiny by design. The bitmap grows; the dot does not.
        val size = MapDesignTokens.pinBitmapSize(MapDesignTokens.DOT_COUNTRY, selected = false)
        assertEquals(MapDesignTokens.MIN_TAP_TARGET.toDouble(), size)
    }

    @Test fun a_selected_dot_reserves_room_for_its_halo() {
        // Big enough that the halo, not the floor, decides the size.
        val dot = 60
        assertEquals(60.0 + 60.0, MapDesignTokens.pinBitmapSize(dot, selected = true))
        assertEquals(60.0 + 4.0, MapDesignTokens.pinBitmapSize(dot, selected = false))
    }

    @Test fun selecting_a_dot_never_shrinks_its_bitmap() {
        for (dot in 1..80) {
            assertTrue(
                MapDesignTokens.pinBitmapSize(dot, selected = true) >=
                    MapDesignTokens.pinBitmapSize(dot, selected = false),
                "dot=$dot",
            )
        }
    }
}
