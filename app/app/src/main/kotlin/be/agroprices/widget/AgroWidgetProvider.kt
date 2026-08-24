package be.agroprices.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import be.agroprices.widget.cache.WidgetPreferences
import be.agroprices.widget.ui.WidgetRenderer

class AgroWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateWorker.schedulePeriodic(context)

        // Render immediately from cache so a newly-added widget is never
        // blank while waiting for the first network fetch to complete.
        val cached = WidgetPreferences(context).load()
        if (cached != null) {
            val views = WidgetRenderer.render(context, cached)
            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        } else {
            WidgetUpdateWorker.requestManualRefresh(context)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateWorker.schedulePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MANUAL_REFRESH) {
            WidgetUpdateWorker.requestManualRefresh(context)
        }
    }

    companion object {
        const val ACTION_MANUAL_REFRESH = "be.agroprices.widget.ACTION_MANUAL_REFRESH"
    }
}
