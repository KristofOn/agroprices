package be.agroprices.widget.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import be.agroprices.widget.AgroWidgetProvider
import be.agroprices.widget.R
import be.agroprices.widget.data.models.PriceRow
import be.agroprices.widget.data.models.PricesSnapshot
import be.agroprices.widget.data.models.Trend
import be.agroprices.widget.data.models.computeTrend
import java.util.Locale

/**
 * Loose over the weekly beef-price cycle so that one or two missed
 * GitHub Actions runs don't immediately flag data as stale; a longer
 * silence still becomes visible.
 */
private const val STALE_THRESHOLD_MILLIS = 9L * 24 * 60 * 60 * 1000

object WidgetRenderer {

    fun render(context: Context, snapshot: PricesSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_agroprices)

        bindRow(context, views, R.id.price_tarwe, R.id.trend_tarwe, snapshot.tarwe, R.string.price_unit_grain)
        bindRow(context, views, R.id.price_gerst, R.id.trend_gerst, snapshot.gerst, R.string.price_unit_grain)
        bindRow(context, views, R.id.price_mais, R.id.trend_mais, snapshot.mais, R.string.price_unit_grain)
        bindRow(context, views, R.id.price_koeien_du, R.id.trend_koeien_du, snapshot.koeienDu, R.string.price_unit_beef)
        bindRow(context, views, R.id.price_koeien_de, R.id.trend_koeien_de, snapshot.koeienDe, R.string.price_unit_beef)

        views.setTextViewText(R.id.last_updated, buildUpdatedText(snapshot))

        val refreshIntent = Intent(context, AgroWidgetProvider::class.java).apply {
            action = AgroWidgetProvider.ACTION_MANUAL_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }

    fun updateAllWidgets(context: Context, snapshot: PricesSnapshot) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, AgroWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        val views = render(context, snapshot)
        for (id in ids) {
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindRow(
        context: Context,
        views: RemoteViews,
        priceViewId: Int,
        trendViewId: Int,
        row: PriceRow,
        unitRes: Int,
    ) {
        val price = row.price
        if (price == null) {
            views.setTextViewText(priceViewId, context.getString(R.string.no_data))
            views.setImageViewResource(trendViewId, R.drawable.ic_flat)
            views.setInt(trendViewId, "setColorFilter", ContextCompat.getColor(context, R.color.trend_no_data))
            return
        }

        val unit = context.getString(unitRes)
        views.setTextViewText(priceViewId, "${formatPrice(price)} $unit")

        val (iconRes, colorRes) = when (computeTrend(price, row.previousPrice)) {
            Trend.UP -> R.drawable.ic_arrow_up to R.color.trend_up
            Trend.DOWN -> R.drawable.ic_arrow_down to R.color.trend_down
            Trend.FLAT -> R.drawable.ic_flat to R.color.trend_flat
        }
        views.setImageViewResource(trendViewId, iconRes)
        views.setInt(trendViewId, "setColorFilter", ContextCompat.getColor(context, colorRes))
    }

    private fun formatPrice(price: Double): String = String.format(Locale.getDefault(), "%.2f", price)

    private fun buildUpdatedText(snapshot: PricesSnapshot): String {
        val now = System.currentTimeMillis()
        val grainStale = snapshot.grainFetchedAt == null || now - snapshot.grainFetchedAt > STALE_THRESHOLD_MILLIS
        val beefStale = snapshot.beefFetchedAt == null || now - snapshot.beefFetchedAt > STALE_THRESHOLD_MILLIS
        return when {
            grainStale && beefStale -> "verouderde data"
            grainStale -> "granen verouderd"
            beefStale -> "runderkarkas verouderd"
            else -> "bijgewerkt"
        }
    }
}
