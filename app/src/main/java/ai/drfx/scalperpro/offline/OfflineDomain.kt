package ai.drfx.scalperpro.offline

enum class ConnectivityMode {
    ONLINE,
    DEGRADED,
    OFFLINE
}

enum class CacheFreshness {
    FRESH,
    STALE,
    EXPIRED
}

data class CachedSnapshot<T>(
    val value: T,
    val capturedAtEpochMillis: Long,
    val source: String,
    val maximumFreshAgeMillis: Long,
    val maximumUsableAgeMillis: Long
) {
    init {
        require(capturedAtEpochMillis >= 0L)
        require(maximumFreshAgeMillis >= 0L)
        require(maximumUsableAgeMillis >= maximumFreshAgeMillis)
    }

    fun freshness(
        nowEpochMillis: Long
    ): CacheFreshness {
        val age = (nowEpochMillis - capturedAtEpochMillis).coerceAtLeast(0L)

        return when {
            age <= maximumFreshAgeMillis -> CacheFreshness.FRESH
            age <= maximumUsableAgeMillis -> CacheFreshness.STALE
            else -> CacheFreshness.EXPIRED
        }
    }
}

data class DegradedDataState<T>(
    val mode: ConnectivityMode,
    val liveValue: T?,
    val cachedValue: CachedSnapshot<T>?,
    val userMessage: String
)

object DegradedDataPolicy {
    fun <T> resolve(
        mode: ConnectivityMode,
        liveValue: T?,
        cachedValue: CachedSnapshot<T>?,
        nowEpochMillis: Long
    ): DegradedDataState<T> {
        if (liveValue != null) {
            return DegradedDataState(
                mode = mode,
                liveValue = liveValue,
                cachedValue = cachedValue,
                userMessage = "Live data available."
            )
        }

        val cacheState = cachedValue?.freshness(nowEpochMillis)

        val message = when (cacheState) {
            CacheFreshness.FRESH -> "Live source unavailable. Showing recent cached data."
            CacheFreshness.STALE -> "Live source unavailable. Showing stale cached data."
            CacheFreshness.EXPIRED -> "Cached data is expired and should not be presented as current."
            null -> "No live or cached data is available."
        }

        return DegradedDataState(
            mode = mode,
            liveValue = null,
            cachedValue = cachedValue,
            userMessage = message
        )
    }
}
