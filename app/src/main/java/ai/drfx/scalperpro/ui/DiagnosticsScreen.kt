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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.diagnostics.AppDiagnostics
import ai.drfx.scalperpro.diagnostics.DiagnosticEvent

@Composable
internal fun DiagnosticsScreen(
    onHome: () -> Unit
) {
    var events by remember {
        mutableStateOf(AppDiagnostics.snapshot())
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Diagnostics", onHome)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    events = AppDiagnostics.snapshot()
                }
            ) {
                Text("Refresh")
            }

            Button(
                onClick = {
                    AppDiagnostics.clear()
                    events = emptyList()
                }
            ) {
                Text("Clear")
            }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Structured and bounded", fontWeight = FontWeight.Bold)
                Text(
                    "Diagnostic attributes are redacted for tokens, API keys, credentials and authorization values before entering the bounded buffer.",
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "Maximum in-memory history: 500 events.",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (events.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "No diagnostic events are currently buffered.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(
                items = events.reversed(),
                key = { event ->
                    event.timestampEpochMillis.toString() +
                        ":" + event.eventName +
                        ":" + (event.requestId ?: "")
                }
            ) { event ->
                DiagnosticEventCard(event)
            }
        }
    }
}

@Composable
private fun DiagnosticEventCard(
    event: DiagnosticEvent
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                event.severity.name + " • " + event.eventName,
                fontWeight = FontWeight.Bold
            )
            Text("Timestamp: " + event.timestampEpochMillis)
            event.requestId?.let {
                Text("Request: " + it)
            }
            event.durationMillis?.let {
                Text("Duration: " + it + " ms")
            }
            event.result?.let {
                Text("Result: " + it)
            }
            event.errorCode?.let {
                Text(
                    "Error: " + it,
                    color = MaterialTheme.colorScheme.error
                )
            }
            event.attributes.forEach { (key, value) ->
                Text(key + ": " + value)
            }
        }
    }
}
