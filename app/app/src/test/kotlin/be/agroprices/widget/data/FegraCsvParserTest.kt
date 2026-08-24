package be.agroprices.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FegraCsvParserTest {

    // Real sample captured from a live fegra.be response (24/08/2026),
    // including the double-space/trailing-space quirks in the header and
    // the comma decimal separator. Mais columns are empty here, matching
    // the real out-of-season state observed on that date.
    private val sampleCsv = """
        "Date";"STANDAARD TARWE gecertificeerd";"STANDAARD TARWE  niet gecertificeerd ";"WINTERGERST gecertificeerd";"WINTERGERST niet gecertificeerd";"BELGISCHE MA${'Ï'}S gecertificeerd";"BELGISCHE MA${'Ï'}S niet gecertificeerd";"VOCHTIGE MA${'Ï'}S 30%";"+ of - ${'€'}/T /% vocht"
        "20/08/2026";"205,5";"200,5";"195";"190";;;;
        "21/08/2026";"210";"205";"199";"194";;;;
        "24/08/2026";"209";"204";"198";"193";;;;
    """.trimIndent()

    @Test
    fun `parses rows and decodes comma decimals`() {
        val rows = FegraCsvParser.parse(sampleCsv)
        assertEquals(3, rows.size)
        assertEquals(LocalDate.of(2026, 8, 24), rows.last().date)
        assertEquals(209.0, rows.last().values[FegraCsvParser.COLUMN_TARWE])
    }

    @Test
    fun `sorts rows ascending by date regardless of input order`() {
        val shuffled = """
            "Date";"STANDAARD TARWE gecertificeerd";
            "24/08/2026";"209";
            "20/08/2026";"205,5";
            "21/08/2026";"210";
        """.trimIndent()
        val rows = FegraCsvParser.parse(shuffled)
        assertEquals(
            listOf(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 24)),
            rows.map { it.date },
        )
    }

    @Test
    fun `findLatestAndPrevious returns the last two non-null values for a column`() {
        val rows = FegraCsvParser.parse(sampleCsv)
        val result = FegraCsvParser.findLatestAndPrevious(rows, FegraCsvParser.COLUMN_TARWE)
        assertEquals(209.0, result.price)
        assertEquals(210.0, result.previousPrice)
    }

    @Test
    fun `column name matching normalizes internal whitespace`() {
        val rows = FegraCsvParser.parse(sampleCsv)
        // Header has double space + trailing space: "STANDAARD TARWE  niet gecertificeerd "
        val result = FegraCsvParser.findLatestAndPrevious(rows, "STANDAARD TARWE niet gecertificeerd")
        assertEquals(204.0, result.price)
    }

    @Test
    fun `mais out of season yields no data via all three candidates`() {
        val rows = FegraCsvParser.parse(sampleCsv)
        val result = FegraCsvParser.findWithFallback(rows, FegraCsvParser.MAIS_COLUMN_CANDIDATES)
        assertNull(result.price)
        assertNull(result.previousPrice)
    }

    @Test
    fun `findWithFallback picks the first candidate column that has any data`() {
        val csv = """
            "Date";"BELGISCHE MA${'Ï'}S gecertificeerd";"BELGISCHE MA${'Ï'}S niet gecertificeerd";"VOCHTIGE MA${'Ï'}S 30%";
            "20/09/2026";"150";;"97";
            "21/09/2026";"152";;"98";
        """.trimIndent()
        val rows = FegraCsvParser.parse(csv)
        val result = FegraCsvParser.findWithFallback(rows, FegraCsvParser.MAIS_COLUMN_CANDIDATES)
        // "niet gecertificeerd" is first in the candidate list but empty here,
        // so it must fall through to "gecertificeerd" rather than mixing columns.
        assertEquals(152.0, result.price)
        assertEquals(150.0, result.previousPrice)
    }

    @Test
    fun `single data point has no previous price`() {
        val csv = """
            "Date";"STANDAARD TARWE gecertificeerd";
            "24/08/2026";"209";
        """.trimIndent()
        val rows = FegraCsvParser.parse(csv)
        val result = FegraCsvParser.findLatestAndPrevious(rows, FegraCsvParser.COLUMN_TARWE)
        assertEquals(209.0, result.price)
        assertNull(result.previousPrice)
    }
}
