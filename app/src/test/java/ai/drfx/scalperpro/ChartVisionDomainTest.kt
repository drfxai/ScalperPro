package ai.drfx.scalperpro

import ai.drfx.scalperpro.vision.ChartImageDescriptor
import ai.drfx.scalperpro.vision.ChartVisionContext
import ai.drfx.scalperpro.vision.ChartVisionRequest
import ai.drfx.scalperpro.vision.ChartVisionRequestValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartVisionDomainTest {
    @Test
    fun validImageContractPasses() {
        val request = ChartVisionRequest(
            image = ChartImageDescriptor(
                id = "chart-1",
                mimeType = "image/png",
                sizeBytes = 1_000_000,
                sha256 = null
            ),
            context = ChartVisionContext(
                symbol = "XAUUSD",
                timeframe = "15m",
                recentCandles = emptyList(),
                volatilityLabel = null,
                marketSession = null,
                userRisk = null
            )
        )

        assertTrue(ChartVisionRequestValidator.validate(request).valid)
    }

    @Test
    fun oversizedImageIsRejected() {
        val request = ChartVisionRequest(
            image = ChartImageDescriptor(
                id = "chart-2",
                mimeType = "image/jpeg",
                sizeBytes = ChartVisionRequestValidator.MAX_IMAGE_BYTES + 1,
                sha256 = null
            ),
            context = ChartVisionContext(
                symbol = "EURUSD",
                timeframe = "1h",
                recentCandles = emptyList(),
                volatilityLabel = null,
                marketSession = null,
                userRisk = null
            )
        )

        assertFalse(ChartVisionRequestValidator.validate(request).valid)
    }
}
