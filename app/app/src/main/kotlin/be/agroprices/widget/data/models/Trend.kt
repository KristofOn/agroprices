package be.agroprices.widget.data.models

enum class Trend { UP, DOWN, FLAT }

/**
 * A missing previous price (e.g. only one data point ever cached) is
 * rendered as FLAT rather than as a distinct "unknown" state -- there is
 * nothing meaningfully different to show the user, and it keeps the row
 * rendering logic in WidgetRenderer to three cases instead of four.
 */
fun computeTrend(price: Double, previousPrice: Double?): Trend {
    if (previousPrice == null) return Trend.FLAT
    return when {
        price > previousPrice -> Trend.UP
        price < previousPrice -> Trend.DOWN
        else -> Trend.FLAT
    }
}
