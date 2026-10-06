package ai.drfx.scalperpro.admin

enum class ContentSourceType {
    MARKET_DATA,
    NEWS,
    ECONOMIC_CALENDAR,
    SIGNALS,
    EDUCATION
}

data class ContentSourceConfig(
    val id: String,
    val type: ContentSourceType,
    val displayName: String,
    val enabled: Boolean,
    val endpointReference: String?,
    val secretReferenceId: String?
)

data class FeatureFlag(
    val key: String,
    val enabled: Boolean,
    val minimumVersionCode: Int? = null
)

data class AdminConfiguration(
    val sources: List<ContentSourceConfig>,
    val featureFlags: List<FeatureFlag>
)

object AdminConfigurationValidator {
    fun validate(
        configuration: AdminConfiguration
    ): List<String> {
        val errors = mutableListOf<String>()

        val duplicateSourceIds = configuration.sources
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateSourceIds.isNotEmpty()) {
            errors += "Duplicate source ids: " + duplicateSourceIds.joinToString()
        }

        val duplicateFlags = configuration.featureFlags
            .groupBy { it.key }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateFlags.isNotEmpty()) {
            errors += "Duplicate feature flags: " + duplicateFlags.joinToString()
        }

        configuration.sources
            .filter { it.enabled }
            .forEach { source ->
                if (source.displayName.isBlank()) {
                    errors += "Enabled source " + source.id + " requires a display name."
                }
            }

        return errors
    }
}
