package ai.drfx.scalperpro.notifications

enum class NotificationType {
    ECONOMIC_EVENT,
    SIGNAL,
    SIGNAL_TP_SL,
    BACKTEST_COMPLETED,
    AI_ANALYSIS_COMPLETED,
    WATCHLIST_NEWS,
    RISK_ALERT
}

data class NotificationPreferences(
    val enabled: Set<NotificationType> = NotificationType.entries.toSet(),
    val highImpactEventsOnly: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinuteOfDay: Int = 23 * 60,
    val quietHoursEndMinuteOfDay: Int = 7 * 60
) {
    init {
        require(quietHoursStartMinuteOfDay in 0..1439)
        require(quietHoursEndMinuteOfDay in 0..1439)
    }
}

data class NotificationContext(
    val type: NotificationType,
    val minuteOfDay: Int,
    val isHighImpactEvent: Boolean = false,
    val isCriticalRiskAlert: Boolean = false
)

object NotificationPolicy {
    fun shouldDeliver(
        preferences: NotificationPreferences,
        context: NotificationContext
    ): Boolean {
        require(context.minuteOfDay in 0..1439)

        if (context.type !in preferences.enabled) return false

        if (
            preferences.highImpactEventsOnly &&
            context.type == NotificationType.ECONOMIC_EVENT &&
            !context.isHighImpactEvent
        ) {
            return false
        }

        if (context.isCriticalRiskAlert) return true
        if (!preferences.quietHoursEnabled) return true

        return !isInsideQuietHours(
            minute = context.minuteOfDay,
            start = preferences.quietHoursStartMinuteOfDay,
            end = preferences.quietHoursEndMinuteOfDay
        )
    }

    private fun isInsideQuietHours(
        minute: Int,
        start: Int,
        end: Int
    ): Boolean {
        if (start == end) return true

        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }
}
