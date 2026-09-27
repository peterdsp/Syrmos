package com.syrmos.core.domain.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The assistant's unsent question. Owned by the long-lived view model rather
 * than the screen so it survives the assistant moving between its hosts (a
 * docked pane on a paired window, a full-screen sheet on a single column) and
 * activity recreation (12.1 #14). [take] hands the text over for sending and
 * clears it in one step, so a question is never sent twice or left behind.
 */
class AssistantDraft {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    fun update(value: String) { _text.value = value }

    /** The current text, trimmed, with the draft cleared; empty when there was nothing to send. */
    fun take(): String {
        val out = _text.value.trim()
        _text.value = ""
        return out
    }
}
