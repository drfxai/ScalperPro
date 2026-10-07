package ai.drfx.scalperpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.market.DefaultWatchlist
import ai.drfx.scalperpro.market.MarketDataResult
import ai.drfx.scalperpro.market.MarketQuote
import ai.drfx.scalperpro.market.MarketRepository
import ai.drfx.scalperpro.market.UnconfiguredMarketDataProvider

private sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Loaded(
        val quotes: List<MarketQuote>
    ) : MarketUiState
    data class Unavailable(
        val message: String
    ) : MarketUiState
    data class Error(
        val message: String
    ) : MarketUiState
}

@Composable
internal fun MarketsScreen(
    onHome: () -> Unit,
    onNews: () -> Unit
) {
    val repository =
        remember {
            MarketRepository(
                UnconfiguredMarketDataProvider()
            )
        }

    var state by remember(repository) {
        mutableStateOf<MarketUiState>(
            MarketUiState.Loading
        )
    }

    LaunchedEffect(repository) {
        state =
            when (
                val result =
                    repository
                        .loadWatchlist(
                            DefaultWatchlist
                                .instruments
                        )
            ) {
                is MarketDataResult
                    .Success ->
                    MarketUiState.Loaded(
                        result.value
                    )
                is MarketDataResult
                    .Unavailable ->
                    MarketUiState.Unavailable(
                        result.reason
                    )
                is MarketDataResult
                    .Failure ->
                    MarketUiState.Error(
                        result.code +
                            ": " +
                            result.message
                    )
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                PremiumScreenBrush
            )
    ) {
        PageHeader(
            title = "Markets",
            subtitle =
                "Live context · no fabricated prices",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment =
                            Alignment.CenterVertically
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
                                    .ShowChart,
                                contentDescription =
                                    null,
                                tint =
                                    PremiumColors
                                        .Cyan
                            )
                            Column {
                                Text(
                                    "Market Intelligence",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                                Text(
                                    "Forex · Gold · Crypto · Indices",
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

                        PremiumTag(
                            text =
                                "News & Calendar",
                            accent =
                                PremiumColors
                                    .Purple,
                            modifier =
                                Modifier.clickable(
                                    onClick = onNews
                                )
                        )
                    }
                }
            }

            when (
                val currentState = state
            ) {
                MarketUiState.Loading -> {
                    item {
                        ProviderUnavailableCard(
                            title =
                                "Checking market provider",
                            message =
                                "Verifying whether a trusted live provider is available."
                        )
                    }
                }

                is MarketUiState.Loaded -> {
                    items(
                        currentState.quotes
                    ) {
                        quote ->
                        PremiumCard(
                            modifier =
                                Modifier.fillMaxWidth(),
                            accent =
                                PremiumColors.Cyan
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            16.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement
                                        .SpaceBetween,
                                verticalAlignment =
                                    Alignment
                                        .CenterVertically
                            ) {
                                Column(
                                    verticalArrangement =
                                        Arrangement
                                            .spacedBy(
                                                3.dp
                                            )
                                ) {
                                    Text(
                                        quote.instrument
                                            .symbol,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleLarge,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                    Text(
                                        quote.instrument
                                            .displayName,
                                        color =
                                            PremiumColors
                                                .TextSecondary
                                    )
                                    Text(
                                        "Source · " +
                                            (
                                                quote.sourceId
                                                    ?: "unknown"
                                                ),
                                        color =
                                            PremiumColors
                                                .TextMuted,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                                Column(
                                    horizontalAlignment =
                                        Alignment.End
                                ) {
                                    Text(
                                        quote.last
                                            ?.toString()
                                            ?: "—",
                                        fontWeight =
                                            FontWeight.Bold,
                                        color =
                                            PremiumColors
                                                .TextPrimary
                                    )
                                    Text(
                                        quote.freshness
                                            .name,
                                        color =
                                            PremiumColors
                                                .Green,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                is MarketUiState.Unavailable -> {
                    item {
                        ProviderUnavailableCard(
                            title =
                                "Live market data unavailable",
                            message =
                                currentState.message
                        )
                    }
                    items(
                        DefaultWatchlist
                            .instruments
                    ) {
                        instrument ->
                        MarketPlaceholderRow(
                            symbol =
                                instrument.symbol,
                            name =
                                instrument
                                    .displayName
                        )
                    }
                }

                is MarketUiState.Error -> {
                    item {
                        ProviderUnavailableCard(
                            title =
                                "Market provider error",
                            message =
                                currentState.message
                        )
                    }
                    items(
                        DefaultWatchlist
                            .instruments
                    ) {
                        instrument ->
                        MarketPlaceholderRow(
                            symbol =
                                instrument.symbol,
                            name =
                                instrument
                                    .displayName
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketPlaceholderRow(
    symbol: String,
    name: String
) {
    PremiumCard(
        modifier =
            Modifier.fillMaxWidth(),
        accent =
            PremiumColors.Border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    symbol,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.Bold
                )
                Text(
                    name,
                    color =
                        PremiumColors
                            .TextSecondary
                )
            }
            PremiumStatusPill(
                text =
                    "Provider required",
                accent =
                    PremiumColors.Gold
            )
        }
    }
}

@Composable
internal fun ProviderUnavailableCard(
    title: String,
    message: String
) {
    PremiumCard(
        modifier =
            Modifier.fillMaxWidth(),
        accent =
            PremiumColors.Gold
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    7.dp
                )
        ) {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Article,
                    contentDescription =
                        null,
                    tint =
                        PremiumColors.Gold
                )
                Text(
                    title,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.Bold
                )
            }
            Text(
                message,
                color =
                    PremiumColors
                        .TextSecondary
            )
            Text(
                "Scalper Pro does not fabricate live market values.",
                color =
                    PremiumColors.Gold,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }
    }
}
