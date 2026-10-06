package ai.drfx.scalperpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.drfx.scalperpro.diagnostics.AppDiagnostics
import ai.drfx.scalperpro.galaxy.GalaxyGraphView
import ai.drfx.scalperpro.learning.ToolDeepLink

internal enum class Destination {
    Home,
    Markets,
    News,
    Signals,
    AI,
    ChartVision,
    Lab,
    Backtest,
    Risk,
    Journal,
    Learn,
    Search,
    Settings,
    Diagnostics,
    About
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFFF3C96B),
    background = Color(0xFF05060A),
    surface = Color(0xFF0C0E16),
    surfaceVariant = Color(0xFF151827)
)

private val LightScheme = lightColorScheme(
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
    var labMode by remember { mutableStateOf(LabMode.SPECIFICATION) }
    val colors = if (darkTheme) DarkScheme else LightScheme

    fun openLab(mode: LabMode) {
        labMode = mode
        destination = Destination.Lab
    }

    LaunchedEffect(destination) {
        AppDiagnostics.record(
            eventName = "ui.navigation",
            attributes = mapOf(
                "destination" to destination.name
            ),
            result = "DISPLAYED"
        )
    }

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        listOf(
                            Destination.Markets to "Markets",
                            Destination.Signals to "Signals",
                            Destination.AI to "AI",
                            Destination.Lab to "Lab",
                            Destination.Learn to "Learn"
                        ).forEach { item ->
                            val target = item.first
                            val label = item.second
                            NavigationBarItem(
                                selected = destination == target,
                                onClick = {
                                    if (target == Destination.Lab) {
                                        labMode = LabMode.AI_BUILDER
                                    }
                                    destination = target
                                },
                                icon = { Text(if (destination == target) "●" else "○") },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (destination) {
                        Destination.Home -> HomeScreen(
                            onNavigate = { destination = it },
                            onOpenLab = { openLab(it) },
                            darkTheme = darkTheme,
                            onToggleTheme = { darkTheme = !darkTheme },
                            onAbout = { destination = Destination.About }
                        )
                        Destination.Markets -> MarketsScreen(
                            onHome = { destination = Destination.Home },
                            onNews = { destination = Destination.News }
                        )
                        Destination.News -> NewsScreen {
                            destination = Destination.Home
                        }
                        Destination.Signals -> SignalsScreen {
                            destination = Destination.Home
                        }
                        Destination.AI -> AiScreen {
                            destination = Destination.Home
                        }
                        Destination.ChartVision -> ChartVisionScreen {
                            destination = Destination.Home
                        }
                        Destination.Lab -> LabScreen(
                            onHome = { destination = Destination.Home },
                            initialMode = labMode
                        )
                        Destination.Backtest -> BacktestScreen {
                            destination = Destination.Home
                        }
                        Destination.Risk -> RiskScreen {
                            destination = Destination.Home
                        }
                        Destination.Journal -> JournalScreen {
                            destination = Destination.Home
                        }
                        Destination.Learn -> LearnScreen(
                            onHome = { destination = Destination.Home },
                            onOpenTool = { deepLink ->
                                when (deepLink) {
                                    ToolDeepLink.STRATEGY_LAB -> openLab(LabMode.SPECIFICATION)
                                    ToolDeepLink.PINE_STUDIO -> openLab(LabMode.PINE)
                                    ToolDeepLink.MQL5_STUDIO -> openLab(LabMode.MQL5)
                                    ToolDeepLink.RISK_MANAGER -> destination = Destination.Risk
                                    ToolDeepLink.BACKTEST -> destination = Destination.Backtest
                                    ToolDeepLink.NEWS -> destination = Destination.News
                                    ToolDeepLink.MARKETS -> destination = Destination.Markets
                                    ToolDeepLink.JOURNAL -> destination = Destination.Journal
                                }
                            }
                        )
                        Destination.Search -> SearchScreen {
                            destination = Destination.Home
                        }
                        Destination.Settings -> SettingsScreen(
                            onHome = { destination = Destination.Home },
                            onDiagnostics = { destination = Destination.Diagnostics }
                        )
                        Destination.Diagnostics -> DiagnosticsScreen {
                            destination = Destination.Home
                        }
                        Destination.About -> AboutScreen {
                            destination = Destination.Home
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    onOpenLab: (LabMode) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAbout: () -> Unit
) {
    var selectedNode by remember { mutableStateOf("SCALPER AI CORE") }
    var prompt by remember { mutableStateOf("") }

    fun openSelectedNode() {
        when {
            selectedNode.contains("Pine", ignoreCase = true) ||
                selectedNode.contains("Indicator", ignoreCase = true) ||
                selectedNode.contains("Repaint", ignoreCase = true) ->
                onOpenLab(LabMode.PINE)

            selectedNode.contains("Strategy", ignoreCase = true) ||
                selectedNode.contains("Pattern", ignoreCase = true) ->
                onOpenLab(LabMode.SPECIFICATION)

            selectedNode.contains("Chart", ignoreCase = true) ||
                selectedNode.contains("Runtime", ignoreCase = true) ->
                onOpenLab(LabMode.CHART)

            selectedNode.contains("MQL5", ignoreCase = true) ||
                selectedNode.contains("MetaTrader", ignoreCase = true) ||
                selectedNode.contains("EA", ignoreCase = true) ->
                onOpenLab(LabMode.MQL5)

            selectedNode.contains("AI", ignoreCase = true) ||
                selectedNode.contains("Planner", ignoreCase = true) ||
                selectedNode.contains("Architect", ignoreCase = true) ->
                onOpenLab(LabMode.AI_BUILDER)

            selectedNode.contains("News", ignoreCase = true) ->
                onNavigate(Destination.News)

            selectedNode.contains("Market", ignoreCase = true) ||
                selectedNode.contains("Gold", ignoreCase = true) ||
                selectedNode.contains("Forex", ignoreCase = true) ->
                onNavigate(Destination.Markets)

            else -> onNavigate(Destination.AI)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF05060A))) {
        AndroidView(
            factory = { context ->
                GalaxyGraphView(context) { node ->
                    selectedNode = node
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Scalper Pro",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "AI TRADING INTELLIGENCE • V1.0.0",
                        color = Color(0xFF8DE8FA)
                    )
                }
                Row {
                    TextButton(
                        onClick = { onNavigate(Destination.Search) }
                    ) {
                        Text("Search")
                    }
                    TextButton(
                        onClick = { onNavigate(Destination.Settings) }
                    ) {
                        Text("Settings")
                    }
                    TextButton(onClick = onToggleTheme) {
                        Text(if (darkTheme) "Light" else "Dark")
                    }
                    TextButton(onClick = onAbout) {
                        Text("About")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x8A111527)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(
                        "Selected: " + selectedNode,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "AUTO ORBIT: ON",
                            color = Color(0xFF8DE8FA)
                        )
                        TextButton(
                            onClick = {
                                prompt = "Open " + selectedNode
                                openSelectedNode()
                            }
                        ) {
                            Text("Open")
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    listOf(
                        "AI Builder",
                        "Indicator Forge",
                        "Strategy Forge",
                        "Pine Lab",
                        "Chart Lab",
                        "Generate MQL5",
                        "Analyze Chart",
                        "News Summary"
                    )
                ) { action ->
                    AssistChip(
                        onClick = {
                            prompt = action
                            when (action) {
                                "AI Builder" -> onOpenLab(LabMode.AI_BUILDER)
                                "Indicator Forge" -> onOpenLab(LabMode.INDICATOR)
                                "Strategy Forge" -> onOpenLab(LabMode.SPECIFICATION)
                                "Pine Lab" -> onOpenLab(LabMode.PINE)
                                "Chart Lab" -> onOpenLab(LabMode.CHART)
                                "Generate MQL5" -> onOpenLab(LabMode.MQL5)
                                "Analyze Chart" -> onNavigate(Destination.ChartVision)
                                "News Summary" -> onNavigate(Destination.News)
                                else -> onNavigate(Destination.AI)
                            }
                        },
                        label = { Text(action) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xE8111420)
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text("Ask Scalper AI...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(
                        onClick = { onNavigate(Destination.AI) }
                    ) {
                        Text("🎙")
                    }
                    TextButton(
                        onClick = { onNavigate(Destination.ChartVision) }
                    ) {
                        Text("＋")
                    }
                    Button(
                        onClick = { onNavigate(Destination.AI) }
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
internal fun PageHeader(
    title: String,
    onHome: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Scalper Pro V1.0.0",
                color = MaterialTheme.colorScheme.secondary
            )
        }
        TextButton(onClick = onHome) {
            Text("Galaxy")
        }
    }
}

@Composable
private fun AboutScreen(
    onHome: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("About", onHome)

        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Scalper Pro",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
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
