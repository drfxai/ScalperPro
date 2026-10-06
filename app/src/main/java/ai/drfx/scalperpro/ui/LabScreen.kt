package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.core.StrategyCodeGenerator
import ai.drfx.scalperpro.core.StrategySpec

@Composable
internal fun LabScreen(onHome: () -> Unit) {
    val spec = remember {
        StrategySpec(
            name = "Gold EMA RSI",
            symbol = "XAUUSD",
            timeframe = "15m",
            trendRule = "EMA50 > EMA200",
            entryRule = "RSI crosses above 50",
            stopRule = "1.5 ATR",
            targetRule = "3 ATR",
            session = "London",
            riskPercent = 1.0
        )
    }
    var codeType by remember { mutableStateOf("Pine") }
    val code = if (codeType == "Pine") StrategyCodeGenerator.pine(spec) else StrategyCodeGenerator.mql5(spec)

    Column(Modifier.fillMaxSize()) {
        PageHeader("Strategy Lab", onHome)
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { codeType = "Pine" }) { Text("Pine") }
            Button(onClick = { codeType = "MQL5" }) { Text("MQL5") }
        }
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(spec.name, fontWeight = FontWeight.Bold)
                Text(spec.symbol + " • " + spec.timeframe + " • Risk " + spec.riskPercent + "%")
                Spacer(Modifier.height(8.dp))
                Text(code)
            }
        }
    }
}
