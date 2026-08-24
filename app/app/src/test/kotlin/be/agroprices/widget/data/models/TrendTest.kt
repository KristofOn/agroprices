package be.agroprices.widget.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendTest {

    @Test
    fun `higher price than previous is UP`() {
        assertEquals(Trend.UP, computeTrend(price = 210.0, previousPrice = 205.0))
    }

    @Test
    fun `lower price than previous is DOWN`() {
        assertEquals(Trend.DOWN, computeTrend(price = 200.0, previousPrice = 205.0))
    }

    @Test
    fun `equal price is FLAT`() {
        assertEquals(Trend.FLAT, computeTrend(price = 205.0, previousPrice = 205.0))
    }

    @Test
    fun `missing previous price is FLAT`() {
        assertEquals(Trend.FLAT, computeTrend(price = 205.0, previousPrice = null))
    }
}
