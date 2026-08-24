package be.agroprices.widget.data

import android.util.Log
import be.agroprices.widget.cache.WidgetPreferences
import be.agroprices.widget.data.models.PriceRow
import be.agroprices.widget.data.models.PricesSnapshot

private const val TAG = "AgroWidget"

/**
 * Fetches grain prices (fegra.be) and beef prices (GitHub Pages beef.json)
 * independently -- a failure in one source falls back to its last cached
 * values without blocking the other source's fresh data. The combined
 * result is cached again so the widget always has something to show.
 */
class PriceRepository(
    private val fegraClient: FegraClient,
    private val beefClient: BeefClient,
    private val preferences: WidgetPreferences,
) {

    suspend fun refresh(): PricesSnapshot {
        val cached = preferences.load()
        val now = System.currentTimeMillis()

        var tarwe = cached?.tarwe ?: PriceRow.EMPTY
        var gerst = cached?.gerst ?: PriceRow.EMPTY
        var mais = cached?.mais ?: PriceRow.EMPTY
        var grainFetchedAt = cached?.grainFetchedAt

        try {
            val rows = FegraCsvParser.parse(fegraClient.downloadCsv())
            tarwe = FegraCsvParser.findLatestAndPrevious(rows, FegraCsvParser.COLUMN_TARWE)
            gerst = FegraCsvParser.findLatestAndPrevious(rows, FegraCsvParser.COLUMN_GERST)
            mais = FegraCsvParser.findWithFallback(rows, FegraCsvParser.MAIS_COLUMN_CANDIDATES)
            grainFetchedAt = now
        } catch (e: Exception) {
            Log.w(TAG, "Granen ophalen bij fegra.be mislukt, val terug op cache", e)
        }

        var koeienDu = cached?.koeienDu ?: PriceRow.EMPTY
        var koeienDe = cached?.koeienDe ?: PriceRow.EMPTY
        var beefFetchedAt = cached?.beefFetchedAt

        try {
            val summary = BeefJsonParser.parse(beefClient.downloadJson())
            koeienDu = summary.du
            koeienDe = summary.de
            beefFetchedAt = now
        } catch (e: Exception) {
            Log.w(TAG, "Runderkarkasprijzen ophalen mislukt, val terug op cache", e)
        }

        val snapshot = PricesSnapshot(
            grainFetchedAt = grainFetchedAt,
            beefFetchedAt = beefFetchedAt,
            tarwe = tarwe,
            gerst = gerst,
            mais = mais,
            koeienDu = koeienDu,
            koeienDe = koeienDe,
        )
        preferences.save(snapshot)
        return snapshot
    }
}
