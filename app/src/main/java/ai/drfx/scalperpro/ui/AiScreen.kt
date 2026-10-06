package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AiScreen(onHome: () -> Unit) {
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Scalper AI", onHome)
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Provider architecture", fontWeight = FontWeight.Bold)
                Text("Gemini Direct: gemini-3.8-flash")
                Text("9Router: Smart / Combo compatible")
                Text("Security: provider secrets remain on the trusted backend.")
                Text("Status: BACKEND NOT CONFIGURED", color = MaterialTheme.colorScheme.tertiary)
            }
        }

        TextField(
            value = message,
            onValueChange = { message = it },
            placeholder = { Text("Describe a setup or ask for analysis…") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Text(
            "The Android client intentionally does not accept raw provider API keys until a trusted Scalper AI Gateway is deployed.",
            modifier = Modifier.padding(16.dp)
        )
    }
}
