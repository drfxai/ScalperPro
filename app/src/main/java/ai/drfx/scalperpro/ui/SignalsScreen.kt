package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.signals.SignalStatus

@Composable
internal fun SignalsScreen(onHome: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Signals", onHome)

        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Auditable Signal Lifecycle", fontWeight = FontWeight.Bold)
                Text(
                    SignalStatus.entries.joinToString(" → ") { it.name },
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "No live signal provider is configured. Scalper Pro will not display fabricated entries, stops, targets, or performance.",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Performance Integrity", fontWeight = FontWeight.Bold)
                Text("Signal outcomes are designed as append-only audit events.")
                Text("Closed losses remain part of win rate, expectancy, profit factor, drawdown, and average-R statistics.")
            }
        }
    }
}
