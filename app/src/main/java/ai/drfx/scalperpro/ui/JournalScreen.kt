package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.journal.JournalEntry
import ai.drfx.scalperpro.journal.JournalInsightEngine
import ai.drfx.scalperpro.journal.JournalStatistics
import java.util.Locale

@Composable
internal fun JournalScreen(
    onHome: () -> Unit
) {
    val entries = remember { mutableStateListOf<JournalEntry>() }

    var instrument by remember { mutableStateOf("XAUUSD") }
    var direction by remember { mutableStateOf("LONG") }
    var entry by remember { mutableStateOf("2000") }
    var exit by remember { mutableStateOf("2010") }
    var stop by remember { mutableStateOf("1995") }
    var target by remember { mutableStateOf("2010") }
    var rResult by remember { mutableStateOf("2") }
    var session by remember { mutableStateOf("London") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val stats = JournalStatistics.calculate(entries)
    val insights = JournalInsightEngine.derive(entries)

    Column(Modifier.fillMaxSize()) {
        PageHeader("Trader Journal", onHome)

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
                        Text("Journal integrity", fontWeight = FontWeight.Bold)
                        Text(
                            "Entries added on this screen are session-only in the current development build.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "Persistent encrypted Room storage and account sync remain a separate implementation task."
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = instrument,
                    onValueChange = { instrument = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Instrument") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = direction,
                    onValueChange = { direction = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Direction") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = entry,
                    onValueChange = { entry = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Entry") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = exit,
                    onValueChange = { exit = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exit") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = stop,
                    onValueChange = { stop = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stop") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Target") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = rResult,
                    onValueChange = { rResult = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Result in R") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = session,
                    onValueChange = { session = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Session") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") }
                )
            }

            item {
                Button(
                    onClick = {
                        try {
                            entries += JournalEntry(
                                instrument = instrument.trim(),
                                direction = direction.trim().uppercase(Locale.US),
                                entryPrice = entry.toDouble(),
                                exitPrice = exit.toDoubleOrNull(),
                                stopPrice = stop.toDoubleOrNull(),
                                targetPrice = target.toDoubleOrNull(),
                                rResult = rResult.toDoubleOrNull(),
                                strategyId = null,
                                strategyName = null,
                                session = session.trim().ifBlank { null },
                                openedAtEpochMillis = System.currentTimeMillis(),
                                closedAtEpochMillis = if (exit.toDoubleOrNull() != null) {
                                    System.currentTimeMillis()
                                } else {
                                    null
                                },
                                plannedRiskPercent = null,
                                beforeImage = null,
                                afterImage = null,
                                newsContext = null,
                                emotion = null,
                                mistakes = emptySet(),
                                notes = notes.trim().ifBlank { null }
                            )
                            error = null
                        } catch (throwable: Throwable) {
                            error = throwable.message ?: "Invalid journal values."
                        }
                    }
                ) {
                    Text("Add journal entry")
                }
            }

            error?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Statistics", fontWeight = FontWeight.Bold)
                        Text("Sample size: " + stats.sampleSize)
                        Text("Closed trades: " + stats.closedTrades)
                        Text(
                            "Win rate: " +
                                (stats.winRatePercent?.let { format(it) + "%" } ?: "—")
                        )
                        Text(
                            "Average R: " +
                                (stats.averageR?.let { format(it) } ?: "—")
                        )
                        Text("Best session: " + (stats.bestSession ?: "—"))
                        Text("Worst session: " + (stats.worstSession ?: "—"))
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("AI-review guardrails", fontWeight = FontWeight.Bold)
                        insights.forEach { insight ->
                            Text(
                                insight.code + " • n=" + insight.sampleSize +
                                    " • " + insight.message
                            )
                        }
                    }
                }
            }

            items(entries, key = { it.id }) { journalEntry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            journalEntry.instrument + " • " + journalEntry.direction,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Entry " + journalEntry.entryPrice +
                                " → Exit " + (journalEntry.exitPrice ?: "OPEN")
                        )
                        Text("R: " + (journalEntry.rResult ?: "—"))
                        Text("Session: " + (journalEntry.session ?: "—"))
                    }
                }
            }
        }
    }
}

private fun format(value: Double): String =
    String.format(Locale.US, "%.2f", value)
