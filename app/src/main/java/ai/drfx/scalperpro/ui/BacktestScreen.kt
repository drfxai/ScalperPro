package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BacktestScreen(
    onHome: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Backtest Core", onHome)

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
                        Text("Deterministic engine", fontWeight = FontWeight.Bold)
                        Text(
                            "Core simulation supports stops, targets, position sizing, spread, slippage and commission.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Same strategy + same dataset produces the same result."
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
                        Text("Metrics", fontWeight = FontWeight.Bold)
                        Text("Net return • Win rate • Profit factor • Max drawdown")
                        Text("Expectancy • Average R • Recovery factor")
                        Text("Consecutive wins/losses • Exposure • Trade duration")
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Production dependencies", fontWeight = FontWeight.Bold)
                        Text(
                            "Historical market-data provider: NOT CONFIGURED",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "Strategy Specification expression interpreter: IN DEVELOPMENT",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "Backtest results are not fabricated while those production inputs are unavailable."
                        )
                    }
                }
            }
        }
    }
}
