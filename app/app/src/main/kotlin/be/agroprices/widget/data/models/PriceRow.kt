package be.agroprices.widget.data.models

/**
 * price == null means "no data available" (e.g. mais outside the harvest
 * season, where fegra.be publishes no quote at all).
 */
data class PriceRow(
    val price: Double?,
    val previousPrice: Double?,
) {
    companion object {
        val EMPTY = PriceRow(price = null, previousPrice = null)
    }
}

data class PricesSnapshot(
    val grainFetchedAt: Long?,
    val beefFetchedAt: Long?,
    val tarwe: PriceRow,
    val gerst: PriceRow,
    val mais: PriceRow,
    val koeienDu: PriceRow,
    val koeienDe: PriceRow,
) {
    companion object {
        val EMPTY = PricesSnapshot(
            grainFetchedAt = null,
            beefFetchedAt = null,
            tarwe = PriceRow.EMPTY,
            gerst = PriceRow.EMPTY,
            mais = PriceRow.EMPTY,
            koeienDu = PriceRow.EMPTY,
            koeienDe = PriceRow.EMPTY,
        )
    }
}
