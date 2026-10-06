package ai.drfx.scalperpro.news

enum class NewsCategory {
    CENTRAL_BANKS, INFLATION, EMPLOYMENT, GDP, GEOPOLITICS,
    COMMODITIES, FOREX, CRYPTO, STOCKS, BREAKING
}

enum class EventImpact { LOW, MEDIUM, HIGH, UNKNOWN }

data class NewsItem(
    val id: String,
    val headline: String,
    val summary: String?,
    val source: String,
    val publishedAtEpochMillis: Long,
    val category: NewsCategory,
    val affectedAssets: List<String> = emptyList()
)

data class EconomicEvent(
    val id: String,
    val country: String,
    val currency: String,
    val event: String,
    val timestampEpochMillis: Long,
    val impact: EventImpact,
    val actual: String? = null,
    val forecast: String? = null,
    val previous: String? = null,
    val revision: String? = null,
    val source: String,
    val affectedAssets: List<String> = emptyList()
)

data class NewsAssessment(
    val factualSummary: String,
    val aiAssessment: String,
    val potentialAffectedAssets: List<String>,
    val riskLevel: EventImpact
)

sealed interface NewsDataResult<out T> {
    data class Success<T>(val value: T, val providerId: String) : NewsDataResult<T>
    data class Unavailable(val reason: String) : NewsDataResult<Nothing>
    data class Failure(val code: String, val message: String) : NewsDataResult<Nothing>
}

interface NewsDataProvider {
    val providerId: String
    suspend fun latestNews(limit: Int): NewsDataResult<List<NewsItem>>
    suspend fun economicCalendar(
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): NewsDataResult<List<EconomicEvent>>
}

class UnconfiguredNewsDataProvider : NewsDataProvider {
    override val providerId: String = "unconfigured"

    override suspend fun latestNews(limit: Int): NewsDataResult<List<NewsItem>> =
        NewsDataResult.Unavailable("No licensed news provider is configured.")

    override suspend fun economicCalendar(
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): NewsDataResult<List<EconomicEvent>> =
        NewsDataResult.Unavailable("No economic-calendar provider is configured.")
}

class NewsRepository(private val provider: NewsDataProvider) {
    suspend fun latestNews(limit: Int = 50): NewsDataResult<List<NewsItem>> {
        require(limit in 1..200)
        return provider.latestNews(limit)
    }

    suspend fun calendar(
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): NewsDataResult<List<EconomicEvent>> {
        require(toEpochMillis > fromEpochMillis)
        return provider.economicCalendar(fromEpochMillis, toEpochMillis)
    }
}
