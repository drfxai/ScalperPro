package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.ai.DefaultAiProviderDashboard
import ai.drfx.scalperpro.notifications.NotificationPreferences
import ai.drfx.scalperpro.notifications.NotificationType

@Composable
internal fun SettingsScreen(
    onHome: () -> Unit
) {
    val aiState = remember { DefaultAiProviderDashboard.state }
    var preferences by remember {
        mutableStateOf(NotificationPreferences())
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", onHome)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("AI Providers", fontWeight = FontWeight.Bold)
                        Text("Routing mode: " + aiState.routingMode.name)
                        Text(
                            "Fallback: " +
                                if (aiState.fallbackEnabled) "ENABLED" else "DISABLED"
                        )
                        Text(
                            "Gemini: " + aiState.gemini.health.name +
                                " • " + (aiState.gemini.modelOrRoute ?: "no model")
                        )
                        Text(
                            "9Router: " + aiState.nineRouter.health.name +
                                " • " + (aiState.nineRouter.modelOrRoute ?: "no route")
                        )
                        Text(
                            "Provider secrets are intentionally not entered or displayed in the Android client. Configuration belongs to the trusted backend.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Notifications", fontWeight = FontWeight.Bold)

                        NotificationType.entries.forEach { type ->
                            val enabled = type in preferences.enabled

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(type.name.replace('_', ' '))
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { checked ->
                                        val updated = preferences.enabled.toMutableSet()
                                        if (checked) {
                                            updated += type
                                        } else {
                                            updated -= type
                                        }
                                        preferences = preferences.copy(
                                            enabled = updated.toSet()
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("High-impact events only")
                            Switch(
                                checked = preferences.highImpactEventsOnly,
                                onCheckedChange = { value ->
                                    preferences = preferences.copy(
                                        highImpactEventsOnly = value
                                    )
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Quiet hours")
                            Switch(
                                checked = preferences.quietHoursEnabled,
                                onCheckedChange = { value ->
                                    preferences = preferences.copy(
                                        quietHoursEnabled = value
                                    )
                                }
                            )
                        }

                        Text(
                            "Current settings are session-local until DataStore persistence is wired.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Offline / degraded mode", fontWeight = FontWeight.Bold)
                        Text(
                            "Cached data must be labeled FRESH, STALE or EXPIRED. Expired market data is never presented as live.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
