package be.agroprices.widget.data

import be.agroprices.widget.data.models.PriceRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses the CSV export from fegra.be's `POST /Home/DownloadPrices`
 * endpoint. Two format quirks that differ from the beef-price CSV
 * (backend/lib/parseBeefCsv.js) and are easy to get wrong:
 *
 *  - The response bytes are UTF-16LE, not UTF-8 (decoding is the caller's
 *    responsibility -- see FegraClient).
 *  - Decimal separator is a comma ("205,5"), not a dot.
 *
 * Observed header (24/08/2026):
 *   "Date";"STANDAARD TARWE gecertificeerd";"STANDAARD TARWE  niet gecertificeerd ";
 *   "WINTERGERST gecertificeerd";"WINTERGERST niet gecertificeerd";
 *   "BELGISCHE MAÏS gecertificeerd";"BELGISCHE MAÏS niet gecertificeerd";
 *   "VOCHTIGE MAÏS 30%";"+ of - €/T /% vocht"
 *
 * Column names are matched with internal whitespace normalized (the source
 * has inconsistent double spaces / trailing spaces around some names).
 */
object FegraCsvParser {

    const val COLUMN_TARWE = "STANDAARD TARWE gecertificeerd"
    const val COLUMN_GERST = "WINTERGERST gecertificeerd"

    /** Tried in order; the first column with any data at all is used. */
    val MAIS_COLUMN_CANDIDATES = listOf(
        "BELGISCHE MAÏS niet gecertificeerd",
        "BELGISCHE MAÏS gecertificeerd",
        "VOCHTIGE MAÏS 30%",
    )

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    data class CsvRow(val date: LocalDate, val values: Map<String, Double?>)

    private fun normalize(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    private fun splitRow(line: String): List<String> =
        line.split(';').map { it.trim().removeSurrounding("\"") }

    fun parse(csvText: String): List<CsvRow> {
        val lines = csvText.split(Regex("\r\n|\n|\r")).map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val header = splitRow(lines[0]).map { normalize(it) }
        val dateIdx = header.indexOfFirst { it.equals("Date", ignoreCase = true) }
        if (dateIdx == -1) {
            throw IllegalArgumentException("Onverwachte fegra CSV-header: geen 'Date'-kolom gevonden: $header")
        }

        val rows = mutableListOf<CsvRow>()
        for (i in 1 until lines.size) {
            val fields = splitRow(lines[i])
            if (fields.size <= dateIdx) continue

            val date = runCatching { LocalDate.parse(fields[dateIdx], DATE_FORMATTER) }.getOrNull() ?: continue

            val values = mutableMapOf<String, Double?>()
            for (colIdx in header.indices) {
                if (colIdx == dateIdx) continue
                val raw = fields.getOrNull(colIdx)?.trim()
                values[header[colIdx]] = if (raw.isNullOrEmpty()) null else raw.replace(',', '.').toDoubleOrNull()
            }
            rows.add(CsvRow(date, values))
        }

        return rows.sortedBy { it.date }
    }

    /** Latest and second-latest non-null values for a single column, newest-first comparison. */
    fun findLatestAndPrevious(rows: List<CsvRow>, columnName: String): PriceRow {
        val normalized = normalize(columnName)
        val pairs = rows.mapNotNull { row ->
            val value = row.values.entries.firstOrNull { normalize(it.key) == normalized }?.value
            if (value == null) null else row.date to value
        }
        if (pairs.isEmpty()) return PriceRow.EMPTY
        val latest = pairs.last().second
        val previous = if (pairs.size >= 2) pairs[pairs.size - 2].second else null
        return PriceRow(price = latest, previousPrice = previous)
    }

    /** Tries each candidate column in order; the first with any data wins (no mixing across columns). */
    fun findWithFallback(rows: List<CsvRow>, columnCandidates: List<String>): PriceRow {
        for (candidate in columnCandidates) {
            val result = findLatestAndPrevious(rows, candidate)
            if (result.price != null) return result
        }
        return PriceRow.EMPTY
    }
}
