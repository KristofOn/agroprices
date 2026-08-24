package be.agroprices.widget.data

import be.agroprices.widget.data.models.PriceRow
import org.json.JSONObject

data class BeefSummary(
    val du: PriceRow,
    val de: PriceRow,
)

/**
 * Parses backend/scrape-beef.js's output, e.g.:
 *   {
 *     "generatedAt": "2026-08-24T18:03:51.731Z",
 *     "weekLabel": "week 33 2026",
 *     "DU": { "price": 714.84, "previousPrice": 717.18 },
 *     "DE": { "price": 857.01, "previousPrice": 855.83 }
 *   }
 * generatedAt/weekLabel are not needed here: PriceRepository stamps its own
 * fetch timestamp for staleness tracking (see WidgetRenderer).
 */
object BeefJsonParser {

    fun parse(jsonText: String): BeefSummary {
        val obj = JSONObject(jsonText)
        return BeefSummary(du = parseRow(obj, "DU"), de = parseRow(obj, "DE"))
    }

    private fun parseRow(obj: JSONObject, key: String): PriceRow {
        val rowObj = obj.optJSONObject(key) ?: return PriceRow.EMPTY
        return PriceRow(
            price = rowObj.optNullableDouble("price"),
            previousPrice = rowObj.optNullableDouble("previousPrice"),
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }
}
