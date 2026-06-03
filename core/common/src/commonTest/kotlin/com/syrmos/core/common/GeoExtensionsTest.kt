package com.syrmos.core.common

import com.syrmos.core.common.extensions.distanceInMeters
import kotlin.test.Test
import kotlin.test.assertTrue

class GeoExtensionsTest {

    @Test
    fun distanceInMeters_same_point_is_zero() {
        val d = distanceInMeters(37.9755, 23.7353, 37.9755, 23.7353)
        assertTrue(d == 0, "Same point should be 0m, got ${d}m")
    }

    @Test
    fun distanceInMeters_syntagma_to_monastiraki() {
        // Syntagma (37.9755, 23.7353) to Monastiraki (37.9763, 23.7256)
        // Real walking distance ~800m, straight line ~900m
        val d = distanceInMeters(37.9755, 23.7353, 37.9763, 23.7256)
        assertTrue(d in 700..1200, "Syntagma-Monastiraki should be ~900m, got ${d}m")
    }

    @Test
    fun distanceInMeters_piraeus_to_kifisia() {
        // Piraeus (37.9475, 23.6431) to Kifisia (38.0856, 23.8011)
        // ~20km straight line
        val d = distanceInMeters(37.9475, 23.6431, 38.0856, 23.8011)
        assertTrue(d in 18000..22000, "Piraeus-Kifisia should be ~20km, got ${d}m")
    }

    @Test
    fun distanceInMeters_is_symmetric() {
        val d1 = distanceInMeters(37.9755, 23.7353, 37.9844, 23.7282)
        val d2 = distanceInMeters(37.9844, 23.7282, 37.9755, 23.7353)
        assertTrue(d1 == d2, "Distance should be symmetric: $d1 vs $d2")
    }
}
