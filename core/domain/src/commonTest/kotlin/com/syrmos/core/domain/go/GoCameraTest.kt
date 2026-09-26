package com.syrmos.core.domain.go

import kotlin.test.Test
import kotlin.test.assertEquals

/** Twin of the camera cases in iOS `JourneyGuidanceTests`. */
class GoCameraTest {
    @Test
    fun followCentersOnStopChangeAndManualPanHoldsTheView() {
        assertEquals(GoCameraAction.CENTER_CURRENT, GoCamera.reduce(GoCameraIntent.FOLLOW, GoCameraEvent.CURRENT_STOP_CHANGED).action)
        val panned = GoCamera.reduce(GoCameraIntent.FOLLOW, GoCameraEvent.USER_PANNED)
        assertEquals(GoCameraStep(GoCameraIntent.MANUAL, GoCameraAction.NONE), panned)
        assertEquals(GoCameraAction.NONE, GoCamera.reduce(GoCameraIntent.MANUAL, GoCameraEvent.CURRENT_STOP_CHANGED).action)
    }

    @Test
    fun fitKeepsTheWholeRouteUntilFollowIsTapped() {
        assertEquals(GoCameraStep(GoCameraIntent.FIT, GoCameraAction.FIT_ROUTE), GoCamera.reduce(GoCameraIntent.MANUAL, GoCameraEvent.FIT_TAPPED))
        assertEquals(GoCameraAction.NONE, GoCamera.reduce(GoCameraIntent.FIT, GoCameraEvent.CURRENT_STOP_CHANGED).action)
        assertEquals(GoCameraStep(GoCameraIntent.FOLLOW, GoCameraAction.CENTER_CURRENT), GoCamera.reduce(GoCameraIntent.FIT, GoCameraEvent.FOLLOW_TAPPED))
    }

    @Test
    fun geometryChangeReframesOnlyWhenTheIntentAsksForIt() {
        assertEquals(GoCameraAction.CENTER_CURRENT, GoCamera.reduce(GoCameraIntent.FOLLOW, GoCameraEvent.GEOMETRY_CHANGED).action)
        assertEquals(GoCameraAction.FIT_ROUTE, GoCamera.reduce(GoCameraIntent.FIT, GoCameraEvent.GEOMETRY_CHANGED).action)
        assertEquals(GoCameraAction.NONE, GoCamera.reduce(GoCameraIntent.MANUAL, GoCameraEvent.GEOMETRY_CHANGED).action)
    }
}
