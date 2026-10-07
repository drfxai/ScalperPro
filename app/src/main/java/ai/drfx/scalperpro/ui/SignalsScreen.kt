package ai.drfx.scalperpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.signals.SignalStatus

@Composable
internal fun SignalsScreen(
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                PremiumScreenBrush
            )
    ) {
        PageHeader(
            title = "Signals",
            subtitle =
                "Auditable lifecycle · provider-gated",
            onHome = onHome
        )

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = 14.dp,
                    vertical = 4.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            item {
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        PremiumColors.Cyan
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            )
                    ) {
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded
                                    .Notifications,
                                contentDescription =
                                    null,
                                tint =
                                    PremiumColors
                                        .Cyan
                            )
                            Column {
                                Text(
                                    "Auditable Signal Lifecycle",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                                Text(
                                    "Every state transition stays explicit.",
                                    color =
                                        PremiumColors
                                            .TextMuted
                                )
                            }
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            listOf(
                                "DRAFT",
                                "WAITING",
                                "TRIGGERED"
                            ).forEach {
                                PremiumTag(
                                    text = it,
                                    accent =
                                        PremiumColors
                                            .Cyan,
                                    selected =
                                        it ==
                                            "TRIGGERED",
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )
                            }
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            listOf(
                                "ACTIVE",
                                "TP / BE",
                                "CLOSED"
                            ).forEach {
                                PremiumTag(
                                    text = it,
                                    accent =
                                        PremiumColors
                                            .Purple,
                                    selected =
                                        it ==
                                            "ACTIVE",
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )
                            }
                        }

                        Text(
                            SignalStatus.entries
                                .joinToString(
                                    " → "
                                ) {
                                    it.name
                                },
                            color =
                                PremiumColors
                                    .TextSecondary,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }

            item {
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        PremiumColors.Gold
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        PremiumStatusPill(
                            text =
                                "Live provider not configured",
                            accent =
                                PremiumColors.Gold
                        )
                        Text(
                            "Scalper Pro will not display fabricated entries, stops, targets, win rates, or performance.",
                            color =
                                PremiumColors
                                    .TextSecondary
                        )
                        Text(
                            "When a trusted provider is connected, signal cards will use the same premium layout with timestamp, direction, entry, invalidation, targets and lifecycle state.",
                            color =
                                PremiumColors
                                    .TextMuted,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }

            item {
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        PremiumColors.Purple
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    16.dp
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            ),
                        verticalAlignment =
                            Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription =
                                null,
                            tint =
                                PremiumColors
                                    .PurpleBright
                        )
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    7.dp
                                )
                        ) {
                            Text(
                                "Performance Integrity",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                                fontWeight =
                                    FontWeight.Bold
                            )
                            Text(
                                "Signal outcomes are append-only audit events.",
                                color =
                                    PremiumColors
                                        .TextSecondary
                            )
                            Text(
                                "Closed losses remain part of win rate, expectancy, profit factor, drawdown and average-R statistics.",
                                color =
                                    PremiumColors
                                        .TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
