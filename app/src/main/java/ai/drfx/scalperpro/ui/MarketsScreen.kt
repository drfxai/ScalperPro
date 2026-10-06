package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    data class Loaded(val quotes: List<MarketQuote>) : MarketUiState
    data class Unavailable(val message: String) : MarketUiState
    data class Error(val message: String) : MarketUiState
}

@Composable
internal fun MarketsScreen(
    onHome: () -> Unit,
    onNews: () -> Unit
) {
    val repository = remember { MarketRepository(UnconfiguredMarketDataProvider()) }

    var state by remember(repository) { mutableStateOf<MarketUiState>(MarketUiState.Loading) }

    LaunchedEffect(repository) {
        state = when (val result = repository.loadWatchlist(DefaultWatchlist.instruments)) {
            is MarketDataResult.Success -> MarketUiState.Loaded(result.value)
            is MarketDataResult.Unavailable -> MarketUiState.Unavailable(result.reason)
            is MarketDataResult.Failure -> MarketUiState.Error(result.code + ": " + result.message)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Markets", onHome)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onNews) { Text("News & Calendar") }
        }

        when (val current = state) {
            MarketUiState.Loading -> Text("Loading market provider…", modifier = Modifier.padding(16.dp))
            is MarketUiState.Loaded -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(current.quotes) { quote ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(quote.instrument.symbol, fontWeight = FontWeight.Bold)
                                    Text(quote.instrument.displayName)
                                    Text("Source: " + (quote.sourceId ?: "unknown"))
                                }
                                Column {
                                    Text(quote.last?.toString() ?: "—", fontWeight = FontWeight.SemiBold)
                                    Text(quote.freshness.name)
                                }
                            }
                        }
                    }
                }
            }
            is MarketUiState.Unavailable -> {
                ProviderUnavailableCard("Live market data unavailable", current.message)
                WatchlistStructure()
            }
            is MarketUiState.Error -> {
                ProviderUnavailableCard("Market provider error", current.message)
                WatchlistStructure()
            }
        }
    }
}

@Composable
private fun WatchlistStructure() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(DefaultWatchlist.instruments) { instrument ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(instrument.symbol, fontWeight = FontWeight.Bold)
                        Text(instrument.displayName)
                    }
                    Text("PROVIDER REQUIRED", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
internal fun ProviderUnavailableCard(title: String, message: String) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(message)
            Text(
                "Scalper Pro does not fabricate live market values.",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
