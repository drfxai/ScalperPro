package ai.drfx.scalperpro.ai

enum class AiProviderKind {
    GEMINI,
    NINE_ROUTER
}

enum class AiRoutingMode {
    GEMINI_DIRECT,
    NINE_ROUTER_SMART,
    NINE_ROUTER_COMBO
}

enum class ProviderHealth {
    CONFIGURED,
    NOT_CONFIGURED,
    AVAILABLE,
    RATE_LIMITED,
    QUOTA_EXHAUSTED,
    UNAVAILABLE,
    UNKNOWN
}

data class ProviderQuota(
    val remaining: Long?,
    val limit: Long?,
    val resetAtEpochMillis: Long?
)

data class AiProviderState(
    val provider: AiProviderKind,
    val health: ProviderHealth,
    val modelOrRoute: String?,
    val maskedCredentialId: String?,
    val quota: ProviderQuota?
)

data class AiProviderDashboardState(
    val routingMode: AiRoutingMode,
    val fallbackEnabled: Boolean,
    val gemini: AiProviderState,
    val nineRouter: AiProviderState,
    val lastProviderUsed: AiProviderKind?,
    val lastErrorCode: String?
)

object AiRoutingPolicy {
    fun fallbackAllowed(
        dashboard: AiProviderDashboardState,
        source: AiProviderKind,
        target: AiProviderKind
    ): Boolean {
        if (!dashboard.fallbackEnabled) return false
        if (source == target) return false

        return when (target) {
            AiProviderKind.GEMINI ->
                dashboard.gemini.health != ProviderHealth.NOT_CONFIGURED
            AiProviderKind.NINE_ROUTER ->
                dashboard.nineRouter.health != ProviderHealth.NOT_CONFIGURED
        }
    }
}

object DefaultAiProviderDashboard {
    val state = AiProviderDashboardState(
        routingMode = AiRoutingMode.GEMINI_DIRECT,
        fallbackEnabled = false,
        gemini = AiProviderState(
            provider = AiProviderKind.GEMINI,
            health = ProviderHealth.NOT_CONFIGURED,
            modelOrRoute = "gemini-3.8-flash",
            maskedCredentialId = null,
            quota = null
        ),
        nineRouter = AiProviderState(
            provider = AiProviderKind.NINE_ROUTER,
            health = ProviderHealth.NOT_CONFIGURED,
            modelOrRoute = null,
            maskedCredentialId = null,
            quota = null
        ),
        lastProviderUsed = null,
        lastErrorCode = null
    )
}
