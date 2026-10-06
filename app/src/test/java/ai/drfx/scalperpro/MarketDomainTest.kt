package ai.drfx.scalperpro

import ai.drfx.scalperpro.market.DefaultWatchlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDomainTest {
    @Test
    fun defaultWatchlistHasUniqueNonBlankSymbols() {
        val symbols = DefaultWatchlist.instruments.map { it.symbol }
        assertTrue(symbols.all { it.isNotBlank() })
        assertEquals(symbols.size, symbols.distinct().size)
    }
}
