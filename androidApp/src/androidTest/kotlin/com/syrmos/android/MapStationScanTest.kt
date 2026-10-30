package com.syrmos.android

import android.util.Log
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives every tab through the semantics tree rather than through
 * `adb shell input`, which MIUI refuses with INJECT_EVENTS. Reports the
 * per-tab text geometry and flags any label squeezed into a vertical
 * sliver, which is how the chip-atomicity regression shows up.
 */
@RunWith(AndroidJUnit4::class)
class MapStationScanTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val tag = "SyrmosScan"

    private fun walk(node: SemanticsNode, screen: String, counter: IntArray) {
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }.orEmpty()
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ").orEmpty()
        val label = if (text.isNotBlank()) text else desc
        if (label.isNotBlank()) {
            counter[0]++
            val b = node.boundsInRoot
            val w = b.width
            val h = b.height
            // A label wider than it is tall is normal. A multi-character label
            // taller than it is wide has been wrapped one glyph per line.
            if (label.trim().length > 3 && h > w * 1.5f) {
                Log.e(tag, "SQUEEZED [$screen] w=${w.toInt()} h=${h.toInt()} '$label'")
            }
        }
        node.children.forEach { walk(it, screen, counter) }
    }

    private fun dump(screen: String) {
        val counter = intArrayOf(0)
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        walk(root, screen, counter)
        Log.i(tag, "SCREEN [$screen] labelled nodes=${counter[0]}")
    }

    @Test
    fun scan_every_tab_and_report_map_stations() {
        composeRule.waitForIdle()
        val lang = LocalizationManager.language.value
        Log.i(tag, "language=$lang")
        dump("home")

        // Walk the tabs by their localized content description, which is what
        // SyrmosApp sets on each navigation icon.
        val tabs = listOf(
            "explore" to L.EXPLORE.text(lang),
            "map" to L.MAP.text(lang),
            "more" to L.MORE_TAB.text(lang),
        )

        tabs.forEach { (key, title) ->
            Log.i(tag, "opening tab $key ('$title')")
            runCatching {
                composeRule.onNodeWithContentDescription(title).performClick()
                composeRule.waitForIdle()
                // The map loads seed + live data asynchronously; give it room.
                Thread.sleep(if (key == "map") 12_000L else 4_000L)
                composeRule.waitForIdle()
                dump(key)
            }.onFailure { Log.e(tag, "tab $key FAILED: ${it.message}") }
        }
        Log.i(tag, "scan complete")
    }
}
