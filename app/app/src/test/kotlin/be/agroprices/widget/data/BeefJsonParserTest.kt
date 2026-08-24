package be.agroprices.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeefJsonParserTest {

    @Test
    fun `parses DU and DE prices`() {
        val json = """
            {
              "generatedAt": "2026-08-24T18:03:51.731Z",
              "weekLabel": "week 33 2026",
              "DU": { "price": 714.84, "previousPrice": 717.18 },
              "DE": { "price": 857.01, "previousPrice": 855.83 }
            }
        """.trimIndent()

        val summary = BeefJsonParser.parse(json)

        assertEquals(714.84, summary.du.price)
        assertEquals(717.18, summary.du.previousPrice)
        assertEquals(857.01, summary.de.price)
        assertEquals(855.83, summary.de.previousPrice)
    }

    @Test
    fun `missing category yields empty row instead of throwing`() {
        val json = """{ "generatedAt": "2026-08-24T18:03:51.731Z", "weekLabel": "week 33 2026", "DU": { "price": 714.84, "previousPrice": 717.18 } }"""

        val summary = BeefJsonParser.parse(json)

        assertNull(summary.de.price)
        assertNull(summary.de.previousPrice)
    }

    @Test
    fun `null price fields yield null, not zero`() {
        val json = """
            {
              "DU": { "price": null, "previousPrice": null },
              "DE": { "price": 857.01, "previousPrice": 855.83 }
            }
        """.trimIndent()

        val summary = BeefJsonParser.parse(json)

        assertNull(summary.du.price)
    }
}
