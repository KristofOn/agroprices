package be.agroprices.widget.cache

import android.content.Context
import androidx.core.content.edit
import be.agroprices.widget.data.models.PriceRow
import be.agroprices.widget.data.models.PricesSnapshot
import org.json.JSONObject

/** Persists the last known good snapshot so the widget is never blank, even before the first fetch completes. */
class WidgetPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PricesSnapshot? {
        val json = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { deserialize(json) }.getOrNull()
    }

    fun save(snapshot: PricesSnapshot) {
        prefs.edit { putString(KEY_SNAPSHOT, serialize(snapshot)) }
    }

    private fun serialize(s: PricesSnapshot): String = JSONObject().apply {
        put("grainFetchedAt", s.grainFetchedAt ?: JSONObject.NULL)
        put("beefFetchedAt", s.beefFetchedAt ?: JSONObject.NULL)
        put("tarwe", rowToJson(s.tarwe))
        put("gerst", rowToJson(s.gerst))
        put("mais", rowToJson(s.mais))
        put("koeienDu", rowToJson(s.koeienDu))
        put("koeienDe", rowToJson(s.koeienDe))
    }.toString()

    private fun rowToJson(row: PriceRow): JSONObject = JSONObject().apply {
        put("price", row.price ?: JSONObject.NULL)
        put("previousPrice", row.previousPrice ?: JSONObject.NULL)
    }

    private fun deserialize(json: String): PricesSnapshot {
        val obj = JSONObject(json)
        return PricesSnapshot(
            grainFetchedAt = obj.optNullableLong("grainFetchedAt"),
            beefFetchedAt = obj.optNullableLong("beefFetchedAt"),
            tarwe = rowFromJson(obj.getJSONObject("tarwe")),
            gerst = rowFromJson(obj.getJSONObject("gerst")),
            mais = rowFromJson(obj.getJSONObject("mais")),
            koeienDu = rowFromJson(obj.getJSONObject("koeienDu")),
            koeienDe = rowFromJson(obj.getJSONObject("koeienDe")),
        )
    }

    private fun rowFromJson(obj: JSONObject): PriceRow = PriceRow(
        price = obj.optNullableDouble("price"),
        previousPrice = obj.optNullableDouble("previousPrice"),
    )

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }

    companion object {
        private const val PREFS_NAME = "agro_widget_prefs"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
