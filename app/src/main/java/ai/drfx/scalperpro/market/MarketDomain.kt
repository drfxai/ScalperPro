package ai.drfx.scalperpro.market

enum class AssetClass { FOREX, METALS, CRYPTO, INDEX, COMMODITY }
enum class DataFreshness { LIVE, DELAYED, STALE, UNAVAILABLE }

data class MarketInstrument(
    val symbol: String,
    val displayName: String,
    val assetClass: AssetClass
)

data class MarketQuote(
    val instrument: MarketInstrument,
    val bid: Double? = null,
    val ask: Double? = null,
    val last: Double? = null,
    val changePercent: Double? = null,
    val timestampEpochMillis: Long? = null,
    val freshness: DataFreshness = DataFreshness.UNAVAILABLE,
    val sourceId: String? = null
)

data class Candle(
    val symbol: String,
    val timeframe: String,
    val openTimeEpochMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double? = null
)

sealed interface MarketDataResult<out T> {
    data class Success<T>(val value: T, val providerId: String) : MarketDataResult<T>
    data class Unavailable(val reason: String) : MarketDataResult<Nothing>
    data class Failure(val code: String, val message: String) : MarketDataResult<Nothing>
}

interface MarketDataProvider {
    val providerId: String
    suspend fun quotes(instruments: List<MarketInstrument>): MarketDataResult<List<MarketQuote>>
    suspend fun candles(
        instrument: MarketInstrument,
        timeframe: String,
        limit: Int
    ): MarketDataResult<List<Candle>>
}

class UnconfiguredMarketDataProvider : MarketDataProvider {
    override val providerId: String = "unconfigured"

    override suspend fun quotes(
        instruments: List<MarketInstrument>
    ): MarketDataResult<List<MarketQuote>> =
        MarketDataResult.Unavailable("No live market-data provider is configured.")

    override suspend fun candles(
        instrument: MarketInstrument,
        timeframe: String,
        limit: Int
    ): MarketDataResult<List<Candle>> =
        MarketDataResult.Unavailable("Historical candle provider is not configured.")
}

class MarketRepository(private val provider: MarketDataProvider) {
    suspend fun loadWatchlist(
        instruments: List<MarketInstrument>
    ): MarketDataResult<List<MarketQuote>> = provider.quotes(instruments)

    suspend fun loadCandles(
        instrument: MarketInstrument,
        timeframe: String,
        limit: Int = 300
    ): MarketDataResult<List<Candle>> {
        require(limit in 1..5000)
        return provider.candles(instrument, timeframe, limit)
    }
}

object DefaultWatchlist {
    val instruments = listOf(
        MarketInstrument("XAUUSD", "Gold / US Dollar", AssetClass.METALS),
        MarketInstrument("EURUSD", "Euro / US Dollar", AssetClass.FOREX),
        MarketInstrument("GBPUSD", "British Pound / US Dollar", AssetClass.FOREX),
        MarketInstrument("BTCUSD", "Bitcoin / US Dollar", AssetClass.CRYPTO),
        MarketInstrument("US100", "Nasdaq 100", AssetClass.INDEX)
    )
}
