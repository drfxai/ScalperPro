package ai.drfx.scalperpro

import ai.drfx.scalperpro.ai.AiProviderDashboardState
import ai.drfx.scalperpro.ai.AiProviderKind
import ai.drfx.scalperpro.ai.AiProviderState
import ai.drfx.scalperpro.ai.AiRoutingMode
import ai.drfx.scalperpro.ai.AiRoutingPolicy
import ai.drfx.scalperpro.ai.ProviderHealth
import ai.drfx.scalperpro.diagnostics.DiagnosticBuffer
import ai.drfx.scalperpro.diagnostics.DiagnosticEvent
import ai.drfx.scalperpro.diagnostics.DiagnosticRedactor
import ai.drfx.scalperpro.diagnostics.DiagnosticSeverity
import ai.drfx.scalperpro.notifications.NotificationContext
import ai.drfx.scalperpro.notifications.NotificationPolicy
import ai.drfx.scalperpro.notifications.NotificationPreferences
import ai.drfx.scalperpro.notifications.NotificationType
import ai.drfx.scalperpro.offline.CacheFreshness
import ai.drfx.scalperpro.offline.CachedSnapshot
import ai.drfx.scalperpro.performance.RetryPolicy
import ai.drfx.scalperpro.search.LocalSearchIndex
import ai.drfx.scalperpro.search.SearchCategory
import ai.drfx.scalperpro.search.SearchDocument
import ai.drfx.scalperpro.security.SecurityDefaults
import ai.drfx.scalperpro.security.UploadValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformHardeningTest {
    @Test
    fun globalSearchReturnsCategoryAndSource() {
        val index = LocalSearchIndex(
            listOf(
                SearchDocument(
                    id = "pine-repaint",
                    title = "Pine repaint protection",
                    body = "Lookahead and request security review",
                    category = SearchCategory.TRADERPEDIA,
                    source = "Scalper Academy",
                    destination = "Learn"
                )
            )
        )

        val result = index.search("pine lookahead").single()
        assertEquals(SearchCategory.TRADERPEDIA, result.document.category)
        assertEquals("Scalper Academy", result.document.source)
    }

    @Test
    fun quietHoursSuppressNormalNotificationButNotCriticalRiskAlert() {
        val preferences = NotificationPreferences(
            quietHoursEnabled = true,
            quietHoursStartMinuteOfDay = 23 * 60,
            quietHoursEndMinuteOfDay = 7 * 60
        )

        assertFalse(
            NotificationPolicy.shouldDeliver(
                preferences,
                NotificationContext(
                    type = NotificationType.WATCHLIST_NEWS,
                    minuteOfDay = 60
                )
            )
        )

        assertTrue(
            NotificationPolicy.shouldDeliver(
                preferences,
                NotificationContext(
                    type = NotificationType.RISK_ALERT,
                    minuteOfDay = 60,
                    isCriticalRiskAlert = true
                )
            )
        )
    }

    @Test
    fun diagnosticsRedactSecretsAndRemainBounded() {
        val buffer = DiagnosticBuffer(capacity = 2)

        repeat(3) { index ->
            buffer.add(
                DiagnosticEvent(
                    timestampEpochMillis = index.toLong(),
                    severity = DiagnosticSeverity.INFO,
                    eventName = "test",
                    sessionId = null,
                    requestId = null,
                    attributes = mapOf(
                        "authorization" to "Bearer secret-token",
                        "message" to "token=abc123"
                    ),
                    durationMillis = null,
                    result = "OK",
                    errorCode = null
                )
            )
        }

        val snapshot = buffer.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals("[REDACTED]", snapshot.last().attributes["authorization"])
        assertFalse(snapshot.last().attributes["message"].orEmpty().contains("abc123"))
        assertFalse(
            DiagnosticRedactor.redactText("Bearer abc.def").contains("abc.def")
        )
    }

    @Test
    fun cacheFreshnessNeverPretendsExpiredDataIsFresh() {
        val snapshot = CachedSnapshot(
            value = "quote",
            capturedAtEpochMillis = 1_000L,
            source = "test",
            maximumFreshAgeMillis = 1_000L,
            maximumUsableAgeMillis = 5_000L
        )

        assertEquals(CacheFreshness.FRESH, snapshot.freshness(2_000L))
        assertEquals(CacheFreshness.STALE, snapshot.freshness(4_000L))
        assertEquals(CacheFreshness.EXPIRED, snapshot.freshness(7_000L))
    }

    @Test
    fun retryPolicyIsBounded() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialDelayMillis = 500L,
            maxDelayMillis = 2_000L,
            multiplier = 2.0
        )

        assertEquals(500L, policy.delayBeforeAttempt(2))
        assertEquals(1_000L, policy.delayBeforeAttempt(3))
        assertEquals(2_000L, policy.delayBeforeAttempt(4))
        assertEquals(2_000L, policy.delayBeforeAttempt(5))
    }

    @Test
    fun imageUploadPolicyRejectsUnsupportedTypes() {
        val result = UploadValidator.validate(
            mimeType = "application/x-msdownload",
            sizeBytes = 1024L,
            policy = SecurityDefaults.chartImageUpload
        )

        assertFalse(result.accepted)
    }

    @Test
    fun crossProviderFallbackRequiresExplicitEnablement() {
        fun dashboard(fallback: Boolean) = AiProviderDashboardState(
            routingMode = AiRoutingMode.GEMINI_DIRECT,
            fallbackEnabled = fallback,
            gemini = AiProviderState(
                provider = AiProviderKind.GEMINI,
                health = ProviderHealth.AVAILABLE,
                modelOrRoute = "gemini-3.8-flash",
                maskedCredentialId = "••••ABCD",
                quota = null
            ),
            nineRouter = AiProviderState(
                provider = AiProviderKind.NINE_ROUTER,
                health = ProviderHealth.AVAILABLE,
                modelOrRoute = "smart",
                maskedCredentialId = "••••WXYZ",
                quota = null
            ),
            lastProviderUsed = null,
            lastErrorCode = null
        )

        assertFalse(
            AiRoutingPolicy.fallbackAllowed(
                dashboard(false),
                AiProviderKind.GEMINI,
                AiProviderKind.NINE_ROUTER
            )
        )
        assertTrue(
            AiRoutingPolicy.fallbackAllowed(
                dashboard(true),
                AiProviderKind.GEMINI,
                AiProviderKind.NINE_ROUTER
            )
        )
    }
}
