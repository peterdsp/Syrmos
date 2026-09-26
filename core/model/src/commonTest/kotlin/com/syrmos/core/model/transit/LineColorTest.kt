package com.syrmos.core.model.transit

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The seed carries each operator's own shade; the palette must snap to the
 * nearest colour so M2 and M3 are red and blue on Android exactly as on iOS,
 * not green by type.
 */
class LineColorTest {

    @Test
    fun exactHexWins() {
        assertEquals(LineColor.BLUE, LineColor.fromHexOrType("#0072CE", "metro"))
        assertEquals(LineColor.BLUE, LineColor.fromHexOrType("#0072ce", "metro"))
    }

    @Test
    fun seedShadesSnapToTheNearestPaletteColour() {
        assertEquals(LineColor.BLUE, LineColor.fromHexOrType("#0083C9", "metro"), "M3 seed shade")
        assertEquals(LineColor.RED, LineColor.fromHexOrType("#E61E2A", "metro"), "M2 seed shade")
        assertEquals(LineColor.RED, LineColor.fromHexOrType("#EE2625", "suburban"), "A1 seed shade")
        assertEquals(LineColor.GREEN, LineColor.fromHexOrType("#00843D", "metro"), "M1 exact")
        assertEquals(LineColor.TRAM_ORANGE, LineColor.fromHexOrType("#F5A623", "tram"))
    }

    @Test
    fun unparseableHexFallsBackByType() {
        assertEquals(LineColor.GREEN, LineColor.fromHexOrType("", "metro"))
        assertEquals(LineColor.TRAM_ORANGE, LineColor.fromHexOrType("orange", "tram"))
        assertEquals(LineColor.SUBURBAN_PURPLE, LineColor.fromHexOrType("#12", "suburban"))
        assertEquals(LineColor.SCENIC_OCHRE, LineColor.fromHexOrType("#GGGGGG", "scenic"))
        assertEquals(LineColor.GREEN, LineColor.fromHexOrType("x", "unknown"))
    }
}
