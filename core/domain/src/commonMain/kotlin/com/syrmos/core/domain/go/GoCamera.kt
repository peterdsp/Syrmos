package com.syrmos.core.domain.go

/** What the GO route map's camera is doing for the rider. */
enum class GoCameraIntent {
    /** Keep the current stop centred as the journey advances. */
    FOLLOW,
    /** Keep the whole route in view. */
    FIT,
    /** The rider panned or zoomed; leave the view alone. */
    MANUAL,
}

/** Things that can happen to the camera. */
enum class GoCameraEvent { USER_PANNED, FIT_TAPPED, FOLLOW_TAPPED, CURRENT_STOP_CHANGED, GEOMETRY_CHANGED }

/** What the map should do now. */
enum class GoCameraAction { NONE, FIT_ROUTE, CENTER_CURRENT }

data class GoCameraStep(val intent: GoCameraIntent, val action: GoCameraAction)

/**
 * The GO map camera with explicit intent (master plan, GO contract), shared by
 * iOS (`GoCamera` in GoJourneyView.swift) and Android: only a real event moves
 * the camera, and only when the intent calls for it. An identical redraw is not
 * an event, so a manual pan survives ticks, folds and recomposition.
 */
object GoCamera {
    fun reduce(intent: GoCameraIntent, event: GoCameraEvent): GoCameraStep = when (event) {
        GoCameraEvent.USER_PANNED -> GoCameraStep(GoCameraIntent.MANUAL, GoCameraAction.NONE)
        GoCameraEvent.FIT_TAPPED -> GoCameraStep(GoCameraIntent.FIT, GoCameraAction.FIT_ROUTE)
        GoCameraEvent.FOLLOW_TAPPED -> GoCameraStep(GoCameraIntent.FOLLOW, GoCameraAction.CENTER_CURRENT)
        GoCameraEvent.CURRENT_STOP_CHANGED ->
            if (intent == GoCameraIntent.FOLLOW) GoCameraStep(intent, GoCameraAction.CENTER_CURRENT)
            else GoCameraStep(intent, GoCameraAction.NONE)
        GoCameraEvent.GEOMETRY_CHANGED -> when (intent) {
            GoCameraIntent.FOLLOW -> GoCameraStep(intent, GoCameraAction.CENTER_CURRENT)
            GoCameraIntent.FIT -> GoCameraStep(intent, GoCameraAction.FIT_ROUTE)
            GoCameraIntent.MANUAL -> GoCameraStep(intent, GoCameraAction.NONE)
        }
    }
}
