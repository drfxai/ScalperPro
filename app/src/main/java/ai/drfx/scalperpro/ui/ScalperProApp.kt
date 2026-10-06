package ai.drfx.scalperpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DarkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.drfx.scalperpro.core.MarketQuote
import ai.drfx.scalperpro.core.Signal
import ai.drfx.scalperpro.core.StrategyCodeGenerator
import ai.drfx.scalperpro.core.StrategySpec
import ai.drfx.scalperpro.galaxy.GalaxyGraphView

private enum class Destination { Home, Markets, Signals, AI, Lab, Learn, About }

private val DarkScheme: DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFFF3C96B),
    background = Color(0xFF05060A),
    surface = Color(0xFF0C0E16),
    surfaceVariant = Color(0xFF151827)
)

private val LightScheme: LightColorScheme = lightColorScheme(
    primary = Color(0xFF6D4AFF),
    secondary = Color(0xFF067E93),
    tertiary = Color(0xFF9B6A10),
    background = Color(0xFFF5F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EBF3)
)

@Composable
fun ScalperProApp() {
    var darkTheme by remember { mutableStateOf(true) }
    var destination by remember { mutableStateOf(Destination.Home) }
    val colors = if (darkTheme) DarkScheme else LightScheme

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        val items = listOf(
                            Destination.Markets to "Markets",
                            Destination.Signals to "Signals",
                            Destination.AI to "AI",
                            Destination.Lab to "Lab",
                            Destination.Learn to "Learn"
                        )
                        items.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item.first,
                                onClick = { destination = item.first },
                                icon = { Text(if (destination == item.first) "●" else "○") },
                                label = { Text(item.second) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (destination) {
                        Destination.Home -> HomeScreen(
                            onNavigate = { destination = it },
                            darkTheme = darkTheme,
                            onToggleTheme = { darkTheme = !darkTheme },
                            onAbout = { destination = Destination.About }
                        )
                        Destination.Markets -> MarketsScreen { destination = Destination.Home }
                        Destination.Signals -> SignalsScreen { destination = Destination.Home }
                        Destination.AI -> AiScreen { destination = Destination.Home }
                        Destination.Lab -> LabScreen { destination = Destination.Home }
                        Destination.Learn -> LearnScreen { destination = Destination.Home }
                        Destination.About -> AboutScreen { destination = Destination.Home }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAbout: () -> Unit
) {
    var selectedNode by remember { mutableStateOf("SCALPER AI CORE") }
    var prompt by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Color(0xFF05060A))) {
        AndroidView(
            factory = { context -> GalaxyGraphView(context) { selectedNode = it } },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Scalper Pro", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("AI TRADING INTELLIGENCE • V1.0.0", color = Color(0xFF8DE8FA))
                }
                Row {
                    TextButton(onClick = onToggleTheme) { Text(if (darkTheme) "Light" else "Dark") }
                    TextButton(onClick = onAbout) { Text("About") }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x8A111527)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    "Selected: " + selectedNode + "   •   AUTO ORBIT: ON",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val quick = listOf("Analyze Chart", "Build Strategy", "Generate Pine", "Generate MQL5", "News Summary")
                items(quick) { action ->
                    AssistChip(onClick = {
                        prompt = action
                        when (action) {
                            "Build Strategy", "Generate Pine", "Generate MQL5" -> onNavigate(Destination.Lab)
                            else -> onNavigate(Destination.AI)
                        }
                    }, label = { Text(action) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xE8111420)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text("Ask Scalper AI...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = { onNavigate(Destination.AI) }) { Text("🎙") }
                    TextButton(onClick = { onNavigate(Destination.AI) }) { Text("＋") }
                    Button(onClick = { onNavigate(Destination.AI) }) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, onHome: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Scalper Pro V1.0.0", color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = onHome) { Text("Galaxy") }
    }
}

@Composable
private fun MarketsScreen(onHome: () -> Unit) {
    val quotes = listOf(
        MarketQuote("XAUUSD", "—", 0.0),
        MarketQuote("EURUSD", "—", 0.0),
        MarketQuote("BTCUSD", "—", 0.0),
        MarketQuote("US100", "—", 0.0)
    )
    Column(Modifier.fillMaxSize()) {
        PageHeader("Markets", onHome)
        Text(
            "Live market provider is not configured in this build. Values are never fabricated.",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.tertiary
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(quotes) { q ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column { Text(q.symbol, fontWeight = FontWeight.Bold); Text("Provider required") }
                        Text(q.price)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalsScreen(onHome: () -> Unit) {
    val signals = listOf(
        Signal("XAUUSD", "WAITING", "Provider required", "—", "—", "—", "NOT LIVE"),
        Signal("EURUSD", "WAITING", "Provider required", "—", "—", "—", "NOT LIVE")
    )
    Column(Modifier.fillMaxSize()) {
        PageHeader("Signals", onHome)
        Text(
            "Signal history is designed to be auditable. No live signal source is configured yet.",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(signals) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.symbol + " • " + s.side, fontWeight = FontWeight.Bold)
                        Text("Entry: " + s.entry)
                        Text("Status: " + s.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiScreen(onHome: () -> Unit) {
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Scalper AI", onHome)
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Provider architecture", fontWeight = FontWeight.Bold)
                Text("Gemini Direct: gemini-3.8-flash")
                Text("9Router: Smart / Combo compatible")
                Text("Security: provider secrets belong on the trusted backend, never in the APK.")
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

@Composable
private fun LabScreen(onHome: () -> Unit) {
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
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun LearnScreen(onHome: () -> Unit) {
    val topics = listOf(
        "Forex Basics", "Market Structure", "Technical Analysis", "Price Action",
        "SMC", "ICT", "Risk Management", "Trading Psychology", "Macro",
        "News Trading", "Gold", "Crypto", "Pine Script", "MQL5", "Quantitative Trading"
    )
    Column(Modifier.fillMaxSize()) {
        PageHeader("Traderpedia & Academy", onHome)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(topics) { topic ->
                Card(Modifier.fillMaxWidth()) {
                    Text(topic, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(onHome: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("About", onHome)
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scalper Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Version 1.0.0")
                Text("Developed by DrFXAi")
                Text("Telegram: DrFXAi")
                Text("YouTube: DrFXAi")
                Text("GitHub: DrFXAi/ScalperPro")
                Text("AI Trading Intelligence")
            }
        }
    }
}
