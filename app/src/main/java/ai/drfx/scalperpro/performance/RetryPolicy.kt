package ai.drfx.scalperpro.performance

import kotlin.math.min

data class RetryPolicy(
    val maxAttempts: Int = 4,
    val initialDelayMillis: Long = 500L,
    val maxDelayMillis: Long = 8_000L,
    val multiplier: Double = 2.0
) {
    init {
        require(maxAttempts in 1..10)
        require(initialDelayMillis >= 0L)
        require(maxDelayMillis >= initialDelayMillis)
        require(multiplier >= 1.0)
    }

    fun delayBeforeAttempt(
        attemptNumber: Int
    ): Long {
        require(attemptNumber in 2..maxAttempts)

        var delay = initialDelayMillis.toDouble()
        repeat(attemptNumber - 2) {
            delay *= multiplier
        }

        return min(delay.toLong(), maxDelayMillis)
    }
}

enum class RenderingQuality {
    HIGH,
    BALANCED,
    LOW_POWER
}

data class RuntimePerformancePolicy(
    val renderingQuality: RenderingQuality,
    val pause3dWhenBackgrounded: Boolean = true,
    val reduceParticlesOnBatterySaver: Boolean = true,
    val networkRetryPolicy: RetryPolicy = RetryPolicy()
)
