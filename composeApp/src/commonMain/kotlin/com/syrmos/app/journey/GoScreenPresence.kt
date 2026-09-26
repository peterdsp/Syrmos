package com.syrmos.app.journey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the GO screen is currently on top of a tab's stack. The app shell
 * reads it to hide the floating Ariadne launcher there: GO owns the bottom of
 * the screen (controls, timeline captions) and iOS GO shows no launcher either.
 * Set by `GoJourneyScreenRoute` for the lifetime of its composition.
 */
object GoScreenPresence {
    var onScreen by mutableStateOf(false)
        internal set
}
