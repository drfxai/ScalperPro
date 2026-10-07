package ai.drfx.scalperpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.drfx.scalperpro.BuildConfig
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
    primary = PremiumColors.Purple,
    secondary = PremiumColors.Cyan,
    tertiary = PremiumColors.Gold,
    background = PremiumColors.Background,
    surface = PremiumColors.Surface,
    surfaceVariant = PremiumColors.SurfaceStrong,
    onBackground = PremiumColors.TextPrimary,
    onSurface = PremiumColors.TextPrimary
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF7047E8),
    secondary = Color(0xFF087F98),
    tertiary = Color(0xFF9B6A10),
    background = Color(0xFFF4F6FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEBEEF7)
)

@Composable
fun ScalperProApp() {
    var darkTheme by remember {
        mutableStateOf(true)
    }
    var destination by remember {
        mutableStateOf(
            Destination.Home
        )
    }
    var labMode by remember {
        mutableStateOf(
            LabMode.AI_BUILDER
        )
    }
    var aiDraft by remember {
        mutableStateOf("")
    }

    val colors =
        if (darkTheme) {
            DarkScheme
        } else {
            LightScheme
        }

    fun openLab(mode: LabMode) {
        labMode = mode
        destination = Destination.Lab
    }

    fun openAi(message: String = "") {
        aiDraft = message
        destination = Destination.AI
    }

    LaunchedEffect(destination) {
        AppDiagnostics.record(
            eventName = "ui.navigation",
            attributes = mapOf(
                "destination" to
                    destination.name
            ),
            result = "DISPLAYED"
        )
    }

    MaterialTheme(
        colorScheme = colors
    ) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                containerColor =
                    PremiumColors.Background,
                bottomBar = {
                    PremiumBottomBar(
                        destination =
                            destination,
                        onNavigate = {
                            target ->
                            if (
                                target ==
                                    Destination.Lab
                            ) {
                                labMode =
                                    LabMode
                                        .AI_BUILDER
                            }
                            destination =
                                target
                        }
                    )
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (destination) {
                        Destination.Home ->
                            HomeScreen(
                                onNavigate = {
                                    destination = it
                                },
                                onOpenLab = {
                                    openLab(it)
                                },
                                onAskAi = {
                                    openAi(it)
                                },
                                darkTheme =
                                    darkTheme,
                                onToggleTheme = {
                                    darkTheme =
                                        !darkTheme
                                },
                                onAbout = {
                                    destination =
                                        Destination
                                            .About
                                }
                            )

                        Destination.Markets ->
                            MarketsScreen(
                                onHome = {
                                    destination =
                                        Destination
                                            .Home
                                },
                                onNews = {
                                    destination =
                                        Destination
                                            .News
                                }
                            )

                        Destination.News ->
                            NewsScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.Signals ->
                            SignalsScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.AI ->
                            AiScreen(
                                onHome = {
                                    destination =
                                        Destination
                                            .Home
                                },
                                initialMessage =
                                    aiDraft,
                                onInitialMessageConsumed = {
                                    aiDraft = ""
                                }
                            )

                        Destination.ChartVision ->
                            ChartVisionScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.Lab ->
                            LabScreen(
                                onHome = {
                                    destination =
                                        Destination
                                            .Home
                                },
                                initialMode =
                                    labMode
                            )

                        Destination.Backtest ->
                            BacktestScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.Risk ->
                            RiskScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.Journal ->
                            JournalScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.Learn ->
                            LearnScreen(
                                onHome = {
                                    destination =
                                        Destination
                                            .Home
                                },
                                onOpenTool = {
                                    deepLink ->
                                    when (deepLink) {
                                        ToolDeepLink
                                            .STRATEGY_LAB ->
                                            openLab(
                                                LabMode
                                                    .SPECIFICATION
                                            )

                                        ToolDeepLink
                                            .PINE_STUDIO ->
                                            openLab(
                                                LabMode
                                                    .PINE
                                            )

                                        ToolDeepLink
                                            .MQL5_STUDIO ->
                                            openLab(
                                                LabMode
                                                    .MQL5
                                            )

                                        ToolDeepLink
                                            .RISK_MANAGER ->
                                            destination =
                                                Destination
                                                    .Risk

                                        ToolDeepLink
                                            .BACKTEST ->
                                            destination =
                                                Destination
                                                    .Backtest

                                        ToolDeepLink
                                            .NEWS ->
                                            destination =
                                                Destination
                                                    .News

                                        ToolDeepLink
                                            .MARKETS ->
                                            destination =
                                                Destination
                                                    .Markets

                                        ToolDeepLink
                                            .JOURNAL ->
                                            destination =
                                                Destination
                                                    .Journal
                                    }
                                }
                            )

                        Destination.Search ->
                            SearchScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.Settings ->
                            SettingsScreen(
                                onHome = {
                                    destination =
                                        Destination
                                            .Home
                                },
                                onDiagnostics = {
                                    destination =
                                        Destination
                                            .Diagnostics
                                }
                            )

                        Destination.Diagnostics ->
                            DiagnosticsScreen {
                                destination =
                                    Destination.Home
                            }

                        Destination.About ->
                            AboutScreen {
                                destination =
                                    Destination.Home
                            }
                    }
                }
            }
        }
    }
}

private data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun PremiumBottomBar(
    destination: Destination,
    onNavigate: (Destination) -> Unit
) {
    val items =
        listOf(
            BottomNavItem(
                Destination.Markets,
                "Markets",
                Icons.Rounded.ShowChart
            ),
            BottomNavItem(
                Destination.Signals,
                "Signals",
                Icons.Rounded.Notifications
            ),
            BottomNavItem(
                Destination.AI,
                "AI",
                Icons.Rounded.AutoAwesome
            ),
            BottomNavItem(
                Destination.Lab,
                "Lab",
                Icons.Rounded.Science
            ),
            BottomNavItem(
                Destination.Learn,
                "Learn",
                Icons.Rounded.MenuBook
            )
        )

    Surface(
        color = Color(0xFA080B13),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = 8.dp,
                    vertical = 7.dp
                ),
            horizontalArrangement =
                Arrangement.SpaceEvenly,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            items.forEach {
                item ->
                val selected =
                    destination ==
                        item.destination ||
                        (
                            destination ==
                                Destination.Home &&
                                item.destination ==
                                Destination.AI
                            )

                Column(
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .clickable {
                            onNavigate(
                                item.destination
                            )
                        }
                        .padding(
                            horizontal = 5.dp,
                            vertical = 3.dp
                        ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(
                                if (
                                    selected &&
                                    item.destination ==
                                        Destination.AI
                                ) {
                                    48.dp
                                } else {
                                    36.dp
                                }
                            )
                            .background(
                                if (selected) {
                                    PremiumColors
                                        .Purple
                                        .copy(
                                            alpha = 0.22f
                                        )
                                } else {
                                    Color.Transparent
                                },
                                CircleShape
                            )
                            .border(
                                if (selected) {
                                    1.dp
                                } else {
                                    0.dp
                                },
                                PremiumColors
                                    .Purple
                                    .copy(
                                        alpha =
                                            if (selected) {
                                                0.68f
                                            } else {
                                                0f
                                            }
                                    ),
                                CircleShape
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                item.icon,
                            contentDescription =
                                item.label,
                            tint =
                                if (selected) {
                                    if (
                                        item.destination ==
                                            Destination.AI
                                    ) {
                                        PremiumColors
                                            .PurpleBright
                                    } else {
                                        PremiumColors
                                            .TextPrimary
                                    }
                                } else {
                                    PremiumColors
                                        .TextMuted
                                },
                            modifier =
                                Modifier.size(
                                    if (
                                        selected &&
                                        item.destination ==
                                            Destination.AI
                                    ) {
                                        25.dp
                                    } else {
                                        20.dp
                                    }
                                )
                        )
                    }

                    Text(
                        item.label,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        color =
                            if (selected) {
                                PremiumColors
                                    .TextPrimary
                            } else {
                                PremiumColors
                                    .TextMuted
                            },
                        fontWeight =
                            if (selected) {
                                FontWeight
                                    .SemiBold
                            } else {
                                FontWeight
                                    .Normal
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    onOpenLab: (LabMode) -> Unit,
    onAskAi: (String) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAbout: () -> Unit
) {
    var selectedNode by remember {
        mutableStateOf(
            "SCALPER AI CORE"
        )
    }
    var prompt by remember {
        mutableStateOf("")
    }
    var autoOrbit by remember {
        mutableStateOf(true)
    }
    var galaxyView by remember {
        mutableStateOf<
            GalaxyGraphView?
            >(null)
    }

    fun openNode(
        name: String = selectedNode
    ) {
        selectedNode = name

        when {
            name.contains(
                "Pine",
                ignoreCase = true
            ) ->
                onOpenLab(
                    LabMode.PINE
                )

            name.contains(
                "Strategy",
                ignoreCase = true
            ) ->
                onOpenLab(
                    LabMode.SPECIFICATION
                )

            name.contains(
                "Backtest",
                ignoreCase = true
            ) ->
                onOpenLab(
                    LabMode.BACKTEST
                )

            name.contains(
                "MQL5",
                ignoreCase = true
            ) ->
                onOpenLab(
                    LabMode.MQL5
                )

            name.contains(
                "Learn",
                ignoreCase = true
            ) ->
                onNavigate(
                    Destination.Learn
                )

            name.contains(
                "News",
                ignoreCase = true
            ) ->
                onNavigate(
                    Destination.News
                )

            name.contains(
                "Market",
                ignoreCase = true
            ) ->
                onNavigate(
                    Destination.Markets
                )

            name.contains(
                "Signal",
                ignoreCase = true
            ) ->
                onNavigate(
                    Destination.Signals
                )

            name.contains(
                "AI",
                ignoreCase = true
            ) ->
                onOpenLab(
                    LabMode.AI_BUILDER
                )

            else ->
                onAskAi(
                    "Help me understand and use " +
                        name +
                        " in Scalper Pro."
                )
        }
    }

    val screenBackground =
        if (darkTheme) {
            PremiumScreenBrush
        } else {
            androidx.compose.ui.graphics
                .Brush.verticalGradient(
                    listOf(
                        Color(0xFFF6F8FC),
                        Color(0xFFEFF3FA)
                    )
                )
        }

    val primaryText =
        if (darkTheme) {
            PremiumColors.TextPrimary
        } else {
            Color(0xFF121522)
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                screenBackground
            )
            .padding(
                horizontal = 14.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 12.dp,
                    bottom = 10.dp
                ),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(1.dp)
            ) {
                Row {
                    Text(
                        "Scalper ",
                        color = primaryText,
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        "Pro",
                        color =
                            PremiumColors
                                .PurpleBright,
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
                Text(
                    "AI TRADING EDUCATION  /  V" +
                        BuildConfig
                            .VERSION_NAME +
                        "  ·  DrFXAi",
                    color =
                        PremiumColors
                            .TextMuted,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {
                HomeTopIcon(
                    icon =
                        Icons.Rounded.Search,
                    label = "Search"
                ) {
                    onNavigate(
                        Destination.Search
                    )
                }
                HomeTopIcon(
                    icon =
                        Icons.Rounded.Settings,
                    label = "Settings"
                ) {
                    onNavigate(
                        Destination.Settings
                    )
                }
                HomeTopIcon(
                    icon = Icons.Rounded.Info,
                    label = "About",
                    onClick = onAbout
                )
            }
        }

        PremiumCard(
            modifier =
                Modifier.fillMaxWidth(),
            accent =
                PremiumColors.Purple
        ) {
            Column(
                modifier = Modifier.padding(
                    14.dp
                ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
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
                            "Selected Node",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium,
                            color =
                                PremiumColors
                                    .TextMuted
                        )
                        Text(
                            selectedNode,
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            fontWeight =
                                FontWeight.Bold,
                            color = primaryText,
                            maxLines = 1,
                            overflow =
                                TextOverflow
                                    .Ellipsis
                        )
                        Text(
                            "Your AI Trading Education Hub",
                            color =
                                PremiumColors
                                    .TextSecondary,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                    TextButton(
                        onClick = {
                            openNode()
                        }
                    ) {
                        Text("Open  ›")
                    }
                }

                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    item {
                        PremiumStatusPill(
                            text =
                                if (autoOrbit) {
                                    "Auto Orbit"
                                } else {
                                    "Orbit Paused"
                                },
                            accent =
                                PremiumColors
                                    .Green
                        )
                    }
                    item {
                        PremiumStatusPill(
                            text = "AI Ready",
                            accent =
                                PremiumColors
                                    .Purple
                        )
                    }
                    item {
                        PremiumStatusPill(
                            text =
                                "Beginner Friendly",
                            accent =
                                PremiumColors
                                    .Gold
                        )
                    }
                }
            }
        }

        Spacer(
            Modifier.height(8.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 260.dp)
        ) {
            AndroidView(
                factory = {
                    context ->
                    GalaxyGraphView(
                        context
                    ) { node ->
                        selectedNode = node
                    }.also { view ->
                        galaxyView = view
                        view.setAutoOrbit(
                            autoOrbit
                        )
                        view.setLightTheme(
                            !darkTheme
                        )
                    }
                },
                update = { view ->
                    galaxyView = view
                    view.setAutoOrbit(
                        autoOrbit
                    )
                    view.setLightTheme(
                        !darkTheme
                    )
                },
                modifier =
                    Modifier.fillMaxSize()
            )

            GalaxyNodeOverlay(
                label = "Markets",
                symbol = "▥",
                accent =
                    PremiumColors.Cyan,
                modifier = Modifier
                    .align(
                        Alignment.TopCenter
                    )
                    .offset(y = 26.dp)
            ) {
                openNode("Markets")
            }

            GalaxyNodeOverlay(
                label = "News",
                symbol = "◎",
                accent =
                    PremiumColors.Gold,
                modifier = Modifier
                    .align(
                        Alignment.TopEnd
                    )
                    .offset(
                        x = (-12).dp,
                        y = 82.dp
                    )
            ) {
                openNode("News")
            }

            GalaxyNodeOverlay(
                label = "Signals",
                symbol = "▥",
                accent =
                    PremiumColors.Purple,
                modifier = Modifier
                    .align(
                        Alignment.CenterEnd
                    )
                    .offset(
                        x = (-4).dp,
                        y = (-18).dp
                    )
            ) {
                openNode("Signals")
            }

            GalaxyNodeOverlay(
                label = "Strategy",
                symbol = "⌘",
                accent =
                    PremiumColors.Teal,
                modifier = Modifier
                    .align(
                        Alignment.BottomEnd
                    )
                    .offset(
                        x = (-10).dp,
                        y = (-62).dp
                    )
            ) {
                openNode("Strategy")
            }

            GalaxyNodeOverlay(
                label = "Backtest",
                symbol = "▥",
                accent =
                    PremiumColors.Cyan,
                modifier = Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .offset(y = (-18).dp)
            ) {
                openNode("Backtest")
            }

            GalaxyNodeOverlay(
                label = "MQL5",
                symbol = "</>",
                accent =
                    PremiumColors.Gold,
                modifier = Modifier
                    .align(
                        Alignment.BottomStart
                    )
                    .offset(
                        x = 12.dp,
                        y = (-64).dp
                    )
            ) {
                openNode("MQL5")
            }

            GalaxyNodeOverlay(
                label = "Pine",
                symbol = "▲",
                accent =
                    PremiumColors.Purple,
                modifier = Modifier
                    .align(
                        Alignment.CenterStart
                    )
                    .offset(
                        x = 4.dp,
                        y = (-12).dp
                    )
            ) {
                openNode("Pine")
            }

            GalaxyNodeOverlay(
                label = "Learn",
                symbol = "◆",
                accent =
                    PremiumColors.Teal,
                modifier = Modifier
                    .align(
                        Alignment.TopStart
                    )
                    .offset(
                        x = 12.dp,
                        y = 88.dp
                    )
            ) {
                openNode("Learn")
            }

            PremiumCard(
                modifier = Modifier
                    .align(
                        Alignment.BottomStart
                    )
                    .padding(
                        start = 4.dp,
                        bottom = 4.dp
                    ),
                accent =
                    PremiumColors.Cyan
            ) {
                Text(
                    "32 MODULES  ·  86 CONNECTIONS\nDrag X/Y · Twist Z · Pinch to zoom",
                    modifier =
                        Modifier.padding(
                            horizontal = 11.dp,
                            vertical = 8.dp
                        ),
                    color =
                        PremiumColors
                            .TextSecondary,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )
            }

            Surface(
                modifier = Modifier
                    .align(
                        Alignment.BottomEnd
                    )
                    .padding(
                        end = 4.dp,
                        bottom = 4.dp
                    )
                    .clickable {
                        autoOrbit =
                            !autoOrbit
                        galaxyView
                            ?.setAutoOrbit(
                                autoOrbit
                            )
                    },
                color =
                    PremiumColors
                        .SurfaceStrong,
                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    Icon(
                        imageVector =
                            if (autoOrbit) {
                                Icons.Rounded
                                    .Pause
                            } else {
                                Icons.Rounded
                                    .PlayArrow
                            },
                        contentDescription =
                            null,
                        tint =
                            PremiumColors
                                .TextPrimary,
                        modifier =
                            Modifier.size(
                                17.dp
                            )
                    )
                    Text(
                        if (autoOrbit) {
                            "AUTO ORBIT"
                        } else {
                            "PLAY ORBIT"
                        },
                        color =
                            PremiumColors
                                .TextSecondary,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall
                    )
                }
            }

            IconButton(
                onClick = {
                    galaxyView
                        ?.resetCamera()
                },
                modifier = Modifier
                    .align(
                        Alignment.TopEnd
                    )
                    .padding(top = 4.dp)
                    .background(
                        PremiumColors
                            .SurfaceStrong,
                        CircleShape
                    )
                    .size(36.dp)
            ) {
                Icon(
                    Icons.Rounded
                        .CenterFocusStrong,
                    contentDescription =
                        "Reset galaxy",
                    tint =
                        PremiumColors
                            .TextSecondary,
                    modifier =
                        Modifier.size(18.dp)
                )
            }
        }

        Spacer(
            Modifier.height(8.dp)
        )

        PremiumCard(
            modifier =
                Modifier.fillMaxWidth(),
            accent =
                PremiumColors.Purple
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        10.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                PremiumColors
                                    .Purple
                                    .copy(
                                        alpha = 0.18f
                                    ),
                                CircleShape
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded
                                .AutoAwesome,
                            contentDescription =
                                null,
                            tint =
                                PremiumColors
                                    .Cyan
                        )
                    }

                    TextField(
                        value = prompt,
                        onValueChange = {
                            prompt = it
                        },
                        placeholder = {
                            Text(
                                "Ask Scalper AI...",
                                color =
                                    PremiumColors
                                        .TextMuted
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                horizontal =
                                    4.dp
                            ),
                        singleLine = true,
                        colors =
                            TextFieldDefaults
                                .colors(
                                    focusedContainerColor =
                                        Color.Transparent,
                                    unfocusedContainerColor =
                                        Color.Transparent,
                                    disabledContainerColor =
                                        Color.Transparent,
                                    focusedIndicatorColor =
                                        Color.Transparent,
                                    unfocusedIndicatorColor =
                                        Color.Transparent
                                )
                    )

                    IconButton(
                        onClick = {
                            onAskAi(prompt)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription =
                                "Voice",
                            tint =
                                PremiumColors
                                    .PurpleBright
                        )
                    }

                    IconButton(
                        enabled =
                            prompt.isNotBlank(),
                        onClick = {
                            onAskAi(
                                prompt.trim()
                            )
                        },
                        modifier =
                            Modifier.background(
                                PremiumColors
                                    .Purple,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Rounded.Send,
                            contentDescription =
                                "Send",
                            tint = Color.White
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    items(
                        listOf(
                            "Analyze Chart",
                            "Build Strategy",
                            "Generate Pine",
                            "Generate MQL5",
                            "News Summary"
                        )
                    ) {
                        action ->
                        PremiumTag(
                            text = action,
                            accent =
                                when (action) {
                                    "Analyze Chart" ->
                                        PremiumColors
                                            .Cyan
                                    "Generate Pine" ->
                                        PremiumColors
                                            .Teal
                                    "Generate MQL5" ->
                                        PremiumColors
                                            .Gold
                                    else ->
                                        PremiumColors
                                            .Purple
                                },
                            selected = false,
                            modifier =
                                Modifier.clickable {
                                    when (action) {
                                        "Analyze Chart" ->
                                            onNavigate(
                                                Destination
                                                    .ChartVision
                                            )
                                        "Build Strategy" ->
                                            onOpenLab(
                                                LabMode
                                                    .SPECIFICATION
                                            )
                                        "Generate Pine" ->
                                            onOpenLab(
                                                LabMode
                                                    .PINE
                                            )
                                        "Generate MQL5" ->
                                            onOpenLab(
                                                LabMode
                                                    .MQL5
                                            )
                                        "News Summary" ->
                                            onNavigate(
                                                Destination
                                                    .News
                                            )
                                    }
                                }
                        )
                    }
                }
            }
        }

        Spacer(
            Modifier.height(8.dp)
        )
    }
}

@Composable
private fun HomeTopIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .background(
                PremiumColors
                    .SurfaceStrong,
                CircleShape
            )
            .border(
                1.dp,
                PremiumColors.Border,
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint =
                PremiumColors
                    .TextSecondary,
            modifier =
                Modifier.size(19.dp)
        )
    }
}

@Composable
private fun GalaxyNodeOverlay(
    label: String,
    symbol: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    accent.copy(
                        alpha = 0.18f
                    ),
                    CircleShape
                )
                .border(
                    1.dp,
                    accent.copy(
                        alpha = 0.8f
                    ),
                    CircleShape
                ),
            contentAlignment =
                Alignment.Center
        ) {
            Text(
                symbol,
                color = accent,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .labelMedium
            )
        }

        Surface(
            color =
                Color(0xD9070A11),
            shape =
                RoundedCornerShape(
                    50
                )
        ) {
            Text(
                label,
                modifier =
                    Modifier.padding(
                        horizontal = 9.dp,
                        vertical = 3.dp
                    ),
                color =
                    PremiumColors
                        .TextPrimary,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun PageHeader(
    title: String,
    onHome: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            ),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Bold,
                color =
                    PremiumColors
                        .TextPrimary
            )
            Text(
                subtitle ?:
                    "Scalper Pro V" +
                    BuildConfig.VERSION_NAME,
                color =
                    PremiumColors.Cyan,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )
        }

        TextButton(
            onClick = onHome
        ) {
            Text(
                "Galaxy",
                color =
                    PremiumColors
                        .PurpleBright,
                fontWeight =
                    FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AboutScreen(
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                PremiumScreenBrush
            )
    ) {
        PageHeader(
            title = "About",
            onHome = onHome
        )

        PremiumCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            accent =
                PremiumColors.Purple
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        20.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                Row {
                    Text(
                        "Scalper ",
                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        "Pro",
                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            PremiumColors
                                .PurpleBright
                    )
                }
                Text(
                    "AI-Powered Trading Education, Strategy & Code Studio",
                    color =
                        PremiumColors
                            .Cyan
                )
                Text(
                    "Version " +
                        BuildConfig
                            .VERSION_NAME
                )
                Text(
                    "Developed by DrFXAi"
                )
                Text(
                    "Telegram · DrFXAi"
                )
                Text(
                    "YouTube · DrFXAi"
                )
                Text(
                    "GitHub · DrFXAi/ScalperPro"
                )
                Text(
                    "Learn → Idea → Strategy → Code → Backtest → AI Review",
                    color =
                        PremiumColors
                            .TextSecondary
                )
            }
        }
    }
}
