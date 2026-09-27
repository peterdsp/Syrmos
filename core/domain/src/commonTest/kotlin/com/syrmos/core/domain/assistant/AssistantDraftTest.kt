package com.syrmos.core.domain.assistant

import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantDraftTest {
    @Test
    fun the_draft_is_kept_until_taken() {
        val draft = AssistantDraft()
        draft.update("Piraeus to airport")
        assertEquals("Piraeus to airport", draft.text.value)
        assertEquals("Piraeus to airport", draft.take())
        assertEquals("", draft.text.value)
    }

    @Test
    fun taking_trims_and_a_blank_draft_yields_nothing() {
        val draft = AssistantDraft()
        draft.update("  airport at 21:30  ")
        assertEquals("airport at 21:30", draft.take())
        draft.update("   ")
        assertEquals("", draft.take())
    }
}
