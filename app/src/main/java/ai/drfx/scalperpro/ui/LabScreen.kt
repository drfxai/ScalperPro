package ai.drfx.scalperpro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.ai.AiLabExecutionResult
import ai.drfx.scalperpro.ai.AiRoutingMode
import ai.drfx.scalperpro.ai.LabTaskType
import ai.drfx.scalperpro.ai.ScalperAgentTeam
import ai.drfx.scalperpro.ai.ScalperAgentWorkflowPlanner
import ai.drfx.scalperpro.ai.ScalperAiGatewayClient
import ai.drfx.scalperpro.ai.ScalperAiGatewayConfig
import ai.drfx.scalperpro.code.CodeArtifact
import ai.drfx.scalperpro.code.Mql5StudioEngine
import ai.drfx.scalperpro.code.PineIndicatorGenerator
import ai.drfx.scalperpro.code.PineStaticAnalyzer
import ai.drfx.scalperpro.code.PineStudioEngine
import ai.drfx.scalperpro.indicator.IndicatorTemplateCatalog
import ai.drfx.scalperpro.lab.BacktestLabDemo
import ai.drfx.scalperpro.lab.LocalHistoricalDataLoader
import ai.drfx.scalperpro.lab.QuantCoderEngine
import ai.drfx.scalperpro.lab.QuantLabCatalog
import ai.drfx.scalperpro.lab.QuantLabChart
import ai.drfx.scalperpro.lab.QuantRuntimeReport
import ai.drfx.scalperpro.market.Candle
import ai.drfx.scalperpro.market.HistoricalCsvParseResult
import ai.drfx.scalperpro.strategy.DirectionPermission
import ai.drfx.scalperpro.strategy.MarketType
import ai.drfx.scalperpro.strategy.StopDefinition
import ai.drfx.scalperpro.strategy.StopMethod
import ai.drfx.scalperpro.strategy.StrategyCondition
import ai.drfx.scalperpro.strategy.StrategyRisk
import ai.drfx.scalperpro.strategy.StrategySpecification
import ai.drfx.scalperpro.strategy.StrategySpecificationValidator
import ai.drfx.scalperpro.strategy.TakeProfitDefinition
import ai.drfx.scalperpro.strategy.TakeProfitMethod
import kotlinx.coroutines.launch
import java.util.Locale

internal enum class LabMode {
    AI_BUILDER,
    INDICATOR,
    SPECIFICATION,
    BACKTEST,
    PINE,
    MQL5,
    CHART
}

@Composable
internal fun LabScreen(
    onHome: () -> Unit,
    initialMode: LabMode = LabMode.AI_BUILDER
) {
    val context = LocalContext.current
    val quantEngine = remember { QuantCoderEngine(context) }
    val aiGatewayClient = remember { ScalperAiGatewayClient() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(quantEngine) {
        onDispose { quantEngine.destroy() }
    }

    val indicatorSpecification = remember {
        IndicatorTemplateCatalog.emaRsiTrend
    }

    val strategySpecification = remember {
        StrategySpecification(
            name = "Gold EMA RSI",
            market = MarketType.METALS,
            symbol = "XAUUSD",
            primaryTimeframe = "15m",
            entryConditions = listOf(
                StrategyCondition(
                    description = "EMA trend confirmation",
                    expression = "ta.ema(close, 50) > ta.ema(close, 200)"
                ),
                StrategyCondition(
                    description = "RSI momentum confirmation",
                    expression = "ta.rsi(close, 14) > 50"
                )
            ),
            session = null,
            risk = StrategyRisk(
                riskPercentPerTrade = 1.0,
                maxConcurrentPositions = 1,
                maxDailyLossPercent = 3.0
            ),
            stop = StopDefinition(
                method = StopMethod.ATR_MULTIPLE,
                value = 1.5
            ),
            takeProfit = TakeProfitDefinition(
                method = TakeProfitMethod.FIXED_R_MULTIPLE,
                value = 2.0
            ),
            cooldownBars = 2,
            pyramiding = 0,
            directionPermission = DirectionPermission.LONG_ONLY
        )
    }

    val strategyValidation = remember(strategySpecification) {
        StrategySpecificationValidator.validate(strategySpecification)
    }
    val indicatorPine = remember(indicatorSpecification) {
        PineIndicatorGenerator.generate(indicatorSpecification)
    }
    val strategyPine = remember(strategySpecification) {
        PineStudioEngine.generate(strategySpecification)
    }
    val mql5 = remember(strategySpecification) {
        Mql5StudioEngine.generate(strategySpecification)
    }

    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var builderPrompt by remember {
        mutableStateOf(
            "Build a beginner-friendly XAUUSD 15m trend indicator with EMA 50/200, RSI confirmation, alerts and no repainting."
        )
    }
    var taskType by remember { mutableStateOf(LabTaskType.INDICATOR_BUILD) }
    var aiRoutingMode by remember { mutableStateOf(AiRoutingMode.GEMINI_DIRECT) }
    var aiRunning by remember { mutableStateOf(false) }
    var aiExecution by remember { mutableStateOf<AiLabExecutionResult?>(null) }
    var pineEditor by remember { mutableStateOf(indicatorPine.source) }
    var runtimeReport by remember { mutableStateOf<QuantRuntimeReport?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                PremiumScreenBrush
            )
    ) {
        PageHeader(
            title = "Strategy Lab",
            subtitle =
                "Idea → Strategy → Code → Backtest",
            onHome = onHome
        )

        PremiumCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp
                ),
            accent =
                PremiumColors.Purple
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 9.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                items(
                    listOf(
                        LabMode.SPECIFICATION to
                            "Strategy",
                        LabMode.PINE to
                            "Pine",
                        LabMode.MQL5 to
                            "MQL5",
                        LabMode.BACKTEST to
                            "Backtest",
                        LabMode.AI_BUILDER to
                            "AI Builder",
                        LabMode.INDICATOR to
                            "Indicator",
                        LabMode.CHART to
                            "Chart"
                    )
                ) {
                    item ->
                    PremiumTag(
                        text = item.second,
                        accent =
                            when (
                                item.first
                            ) {
                                LabMode.PINE ->
                                    PremiumColors.Teal
                                LabMode.MQL5 ->
                                    PremiumColors.Gold
                                LabMode.BACKTEST ->
                                    PremiumColors.Cyan
                                else ->
                                    PremiumColors.Purple
                            },
                        selected =
                            mode == item.first,
                        modifier =
                            Modifier.clickable {
                                mode = item.first
                            }
                    )
                }
            }
        }

        Spacer(
            Modifier.height(8.dp)
        )

        when (mode) {
            LabMode.AI_BUILDER -> AiBuilderPanel(
                prompt = builderPrompt,
                onPromptChange = { builderPrompt = it },
                taskType = taskType,
                onTaskTypeChange = { taskType = it },
                routingMode = aiRoutingMode,
                onRoutingModeChange = { aiRoutingMode = it },
                gatewayConfigured = ScalperAiGatewayConfig.configured,
                aiRunning = aiRunning,
                aiExecution = aiExecution,
                onRunAiTeam = {
                    aiRunning = true
                    aiExecution = null

                    coroutineScope.launch {
                        val result =
                            aiGatewayClient.runLabWorkflow(
                                taskType = taskType,
                                routingMode = aiRoutingMode,
                                message = builderPrompt,
                                context =
                                    "Scalper Pro Quant Lab. " +
                                        "Beginner-focused educational tool. " +
                                        "Target Pine Script v6. " +
                                        "Never claim compile verification without an external compiler result."
                            )

                        aiExecution = result
                        aiRunning = false

                        if (
                            result is
                                AiLabExecutionResult.Success
                        ) {
                            result.workflow.stages
                                .lastOrNull {
                                    it.role == "pine"
                                }
                                ?.text
                                ?.let(::extractCodeBlock)
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?.let {
                                    pineEditor = it
                                    runtimeReport = null
                                }
                        }
                    }
                },
                onOpenGeneratedPine = {
                    val result =
                        aiExecution as?
                            AiLabExecutionResult.Success

                    val generated =
                        result
                            ?.workflow
                            ?.stages
                            ?.lastOrNull {
                                it.role == "pine"
                            }
                            ?.text
                            ?.let(::extractCodeBlock)

                    if (!generated.isNullOrBlank()) {
                        pineEditor = generated
                        runtimeReport = null
                        mode = LabMode.PINE
                    }
                },
                onOpenIndicator = {
                    pineEditor = indicatorPine.source
                    mode = LabMode.INDICATOR
                },
                onOpenStrategy = {
                    pineEditor = strategyPine.source
                    mode = LabMode.SPECIFICATION
                }
            )

            LabMode.INDICATOR -> IndicatorForgePanel(
                artifact = indicatorPine,
                onSendToPine = {
                    pineEditor = indicatorPine.source
                    runtimeReport = null
                    mode = LabMode.PINE
                },
                onOpenChart = { mode = LabMode.CHART }
            )

            LabMode.SPECIFICATION -> StrategyForgePanel(
                specification = strategySpecification,
                isValid = strategyValidation.valid,
                errors = strategyValidation.errors,
                onSendToPine = {
                    pineEditor = strategyPine.source
                    runtimeReport = null
                    mode = LabMode.PINE
                },
                onOpenChart = { mode = LabMode.CHART },
                onOpenBacktest = {
                    mode = LabMode.BACKTEST
                }
            )

            LabMode.BACKTEST -> BacktestLabPanel(
                specification =
                    strategySpecification,
                onBackToStrategy = {
                    mode =
                        LabMode.SPECIFICATION
                },
                onOpenPine = {
                    pineEditor =
                        strategyPine.source
                    runtimeReport = null
                    mode = LabMode.PINE
                }
            )

            LabMode.PINE -> PineLabPanel(
                code = pineEditor,
                onCodeChange = {
                    pineEditor = it
                    runtimeReport = null
                },
                report = runtimeReport,
                onAnalyze = {
                    quantEngine.analyze(pineEditor) { report ->
                        runtimeReport = report
                    }
                },
                onOpenChart = { mode = LabMode.CHART }
            )

            LabMode.MQL5 -> CodeArtifactPanel(
                title = "MQL5 / MetaTrader Studio",
                artifact = mql5,
                note = "Generated code is static-reviewed only until a real MetaEditor compile worker is connected."
            )

            LabMode.CHART -> ChartLabPanel(
                report = runtimeReport,
                onBackToPine = { mode = LabMode.PINE }
            )
        }
    }
}

@Composable
private fun AiBuilderPanel(
    prompt: String,
    onPromptChange: (String) -> Unit,
    taskType: LabTaskType,
    onTaskTypeChange: (LabTaskType) -> Unit,
    routingMode: AiRoutingMode,
    onRoutingModeChange: (AiRoutingMode) -> Unit,
    gatewayConfigured: Boolean,
    aiRunning: Boolean,
    aiExecution: AiLabExecutionResult?,
    onRunAiTeam: () -> Unit,
    onOpenGeneratedPine: () -> Unit,
    onOpenIndicator: () -> Unit,
    onOpenStrategy: () -> Unit
) {
    val plan = remember(taskType) {
        ScalperAgentWorkflowPlanner.plan(taskType)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "AI Specialist Team",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Inspired by the multi-agent supervisor architecture from DrFXQuant Hyperion. Each specialist has one job, so planning, Pine engineering and QA stay separate.",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Describe what you want to build") },
                minLines = 4
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(
                        LabTaskType.INDICATOR_BUILD,
                        LabTaskType.STRATEGY_BUILD,
                        LabTaskType.PINE_REVIEW,
                        LabTaskType.PINE_REPAIR,
                        LabTaskType.MQL5_TRANSLATE
                    )
                ) { type ->
                    AssistChip(
                        onClick = { onTaskTypeChange(type) },
                        label = { Text(type.name.replace('_', ' ')) }
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
                    Text("Agent Plan", fontWeight = FontWeight.Bold)
                    plan.steps.forEach { step ->
                        val agent = ScalperAgentTeam.agents.firstOrNull {
                            it.role == step.role
                        }
                        Text(
                            step.index.toString() + ". " +
                                (agent?.title ?: step.role.name) +
                                " — " + step.objective
                        )
                    }
                }
            }
        }

        item {
            Text("AI Route", fontWeight = FontWeight.Bold)

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AiRoutingMode.entries) { mode ->
                    AssistChip(
                        onClick = {
                            onRoutingModeChange(mode)
                        },
                        label = {
                            Text(
                                when (mode) {
                                    AiRoutingMode.GEMINI_DIRECT ->
                                        "Gemini Direct"
                                    AiRoutingMode.NINE_ROUTER_SMART ->
                                        "9Router Smart"
                                    AiRoutingMode.NINE_ROUTER_COMBO ->
                                        "9Router Combo"
                                }
                            )
                        }
                    )
                }
            }

            Text(
                if (gatewayConfigured) {
                    "Trusted AI Gateway: CONFIGURED"
                } else {
                    "Trusted AI Gateway: NOT CONFIGURED"
                },
                color =
                    if (gatewayConfigured) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
            )

            Text(
                "Selected route: " + routingMode.name,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        item {
            Button(
                enabled =
                    !aiRunning &&
                        prompt.isNotBlank(),
                onClick = onRunAiTeam
            ) {
                Text(
                    if (aiRunning) {
                        "AI Team Running..."
                    } else {
                        "Run AI Specialist Team"
                    }
                )
            }
        }

        aiExecution?.let { execution ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        when (execution) {
                            is AiLabExecutionResult.Unavailable -> {
                                Text(
                                    "Gateway unavailable",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    execution.reason,
                                    color =
                                        MaterialTheme.colorScheme.tertiary
                                )
                            }

                            is AiLabExecutionResult.Failure -> {
                                Text(
                                    "AI workflow failed",
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        MaterialTheme.colorScheme.error
                                )
                                Text(
                                    execution.code +
                                        " • " +
                                        execution.message
                                )
                            }

                            is AiLabExecutionResult.Success -> {
                                Text(
                                    "AI Specialist Results",
                                    fontWeight = FontWeight.Bold
                                )

                                execution.workflow.stages
                                    .forEachIndexed {
                                            index,
                                            stage ->
                                        Text(
                                            (index + 1).toString() +
                                                ". " +
                                                stage.title +
                                                " • " +
                                                stage.provider +
                                                " / " +
                                                stage.model,
                                            fontWeight =
                                                FontWeight.SemiBold
                                        )

                                        val preview =
                                            stage.text.take(1_400)

                                        Text(
                                            preview +
                                                if (
                                                    stage.text.length >
                                                        preview.length
                                                ) {
                                                    "…"
                                                } else {
                                                    ""
                                                }
                                        )
                                    }

                                if (
                                    execution.workflow.stages
                                        .any {
                                            it.role == "pine"
                                        }
                                ) {
                                    Button(
                                        onClick =
                                            onOpenGeneratedPine
                                    ) {
                                        Text(
                                            "Open AI Pine in Lab"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenIndicator) {
                    Text("Open Indicator Forge")
                }
                Button(onClick = onOpenStrategy) {
                    Text("Open Strategy Forge")
                }
            }
        }

        item {
            Text(
                "Live multi-agent execution uses the trusted Scalper AI Gateway. The Android app stores no Gemini or 9Router secrets; without a configured HTTPS gateway it remains safely unavailable.",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun IndicatorForgePanel(
    artifact: CodeArtifact,
    onSendToPine: () -> Unit,
    onOpenChart: () -> Unit
) {
    var selectedToolId by remember {
        mutableStateOf("indicator-forge")
    }
    var selectedTimeframe by remember {
        mutableStateOf("15m")
    }

    val selectedTool =
        QuantLabCatalog.tools
            .firstOrNull {
                it.id == selectedToolId
            }

    val timeframe =
        QuantLabCatalog.timeframes
            .firstOrNull {
                it.code == selectedTimeframe
            }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "Indicator Forge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        IndicatorTemplateCatalog.emaRsiTrend.beginnerExplanation,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Repaint policy: " +
                            IndicatorTemplateCatalog.emaRsiTrend.repaintPolicy.name
                    )
                    Text(
                        "Designed TFs: " +
                            IndicatorTemplateCatalog.emaRsiTrend.designedTimeframes.joinToString(" • ")
                    )
                }
            }
        }

        item {
            Text("Toolbox", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    QuantLabCatalog.tools.filter {
                        it.category.name in setOf(
                            "INDICATOR",
                            "PINE",
                            "QA",
                            "BACKTEST"
                        )
                    }
                ) { tool ->
                    AssistChip(
                        onClick = {
                            selectedToolId =
                                tool.id
                        },
                        label = {
                            Text(
                                if (
                                    selectedToolId ==
                                        tool.id
                                ) {
                                    "✓ " + tool.title
                                } else {
                                    tool.title
                                }
                            )
                        }
                    )
                }
            }
        }

        item {
            Text("Timeframes", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QuantLabCatalog.timeframes) { item ->
                    AssistChip(
                        onClick = {
                            selectedTimeframe =
                                item.code
                        },
                        label = {
                            Text(
                                if (
                                    selectedTimeframe ==
                                        item.code
                                ) {
                                    "✓ " + item.code
                                } else {
                                    item.code
                                }
                            )
                        }
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "Selected Lab Context",
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        selectedTool?.title ?:
                            "Indicator Forge",
                        color =
                            MaterialTheme
                                .colorScheme
                                .secondary
                    )
                    selectedTool?.let {
                        Text(it.description)
                    }
                    timeframe?.let {
                        Text(
                            it.code +
                                " • " +
                                it.useCase
                        )
                    }
                    Text(
                        "Selections are design context; generated code changes only when the underlying Indicator Specification or AI plan changes.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .tertiary
                    )
                }
            }
        }

        item {
            CodeArtifactPanel(
                title = "Generated Pine Indicator",
                artifact = artifact,
                note = "Next step: run TradingView QA and the local DrFXQuant Quant Runtime before treating the visual result as verified."
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSendToPine) { Text("Open Pine Lab") }
                Button(onClick = onOpenChart) { Text("Open Chart") }
            }
        }
    }
}

@Composable
private fun StrategyForgePanel(
    specification: StrategySpecification,
    isValid: Boolean,
    errors: List<String>,
    onSendToPine: () -> Unit,
    onOpenChart: () -> Unit,
    onOpenBacktest: () -> Unit
) {
    var selectedArchetypeId by remember {
        mutableStateOf("trend")
    }
    var selectedTimeframe by remember {
        mutableStateOf(
            specification.primaryTimeframe
        )
    }

    val selectedArchetype =
        QuantLabCatalog.strategyArchetypes
            .firstOrNull {
                it.id == selectedArchetypeId
            }

    val selectedTf =
        QuantLabCatalog.timeframes
            .firstOrNull {
                it.code == selectedTimeframe
            }

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
                    Text(
                        "Strategy Forge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        specification.name + " • " +
                            specification.symbol + " • " +
                            specification.primaryTimeframe
                    )
                    Text(
                        if (isValid) "Strategy Specification: VALID"
                        else "Strategy Specification: INVALID",
                        color = if (isValid) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    errors.forEach {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Entry rules: " +
                            specification.entryConditions.joinToString(" + ") {
                                it.description
                            }
                    )
                    Text("Stop: " + specification.stop.method.name)
                    Text("Target: " + specification.takeProfit.method.name)
                    Text("Direction: " + specification.directionPermission.name)
                }
            }
        }

        item {
            Text("Strategy Library", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QuantLabCatalog.strategyArchetypes) { item ->
                    AssistChip(
                        onClick = {
                            selectedArchetypeId =
                                item.id
                        },
                        label = {
                            Text(
                                if (
                                    selectedArchetypeId ==
                                        item.id
                                ) {
                                    "✓ " + item.title
                                } else {
                                    item.title
                                }
                            )
                        }
                    )
                }
            }
        }

        item {
            Text(
                "Timeframes",
                fontWeight = FontWeight.Bold
            )
            LazyRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                items(
                    QuantLabCatalog.timeframes
                ) { item ->
                    AssistChip(
                        onClick = {
                            selectedTimeframe =
                                item.code
                        },
                        label = {
                            Text(
                                if (
                                    selectedTimeframe ==
                                        item.code
                                ) {
                                    "✓ " + item.code
                                } else {
                                    item.code
                                }
                            )
                        }
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "Strategy Reference",
                        fontWeight =
                            FontWeight.Bold
                    )
                    selectedArchetype?.let {
                        Text(
                            it.title,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .secondary
                        )
                        Text(it.summary)
                        Text(
                            "Useful tools: " +
                                it.usefulIndicators
                                    .joinToString(" • ")
                        )
                        Text(
                            "Caution: " +
                                it.caution,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .tertiary
                        )
                    }
                    selectedTf?.let {
                        Text(
                            "Timeframe: " +
                                it.code +
                                " • " +
                                it.useCase
                        )
                    }
                    Text(
                        "Reference selections guide planning. The stored Strategy Specification remains the execution source of truth.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .tertiary
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Quality gates", fontWeight = FontWeight.Bold)
                    QuantLabCatalog.pineQualityRules.forEach { rule ->
                        Text(
                            rule.severity + " • " +
                                rule.title + " — " +
                                rule.description
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onOpenBacktest) {
                    Text("Run Backtest")
                }
                Button(onClick = onSendToPine) {
                    Text("Generate Pine")
                }
                Button(onClick = onOpenChart) {
                    Text("Chart")
                }
            }
        }
    }
}

@Composable
private fun BacktestLabPanel(
    specification: StrategySpecification,
    onBackToStrategy: () -> Unit,
    onOpenPine: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope =
        rememberCoroutineScope()

    var importedCandles by remember {
        mutableStateOf<List<Candle>?>(
            null
        )
    }
    var importMessage by remember {
        mutableStateOf<String?>(null)
    }
    var importWarnings by remember {
        mutableStateOf<List<String>>(
            emptyList()
        )
    }
    var importBusy by remember {
        mutableStateOf(false)
    }

    val csvLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocument()
        ) { uri ->
            if (uri != null) {
                importBusy = true
                importMessage = null
                importWarnings =
                    emptyList()

                coroutineScope.launch {
                    val parsed =
                        LocalHistoricalDataLoader
                            .loadCsv(
                                context =
                                    context,
                                uri = uri,
                                symbol =
                                    specification
                                        .symbol,
                                timeframe =
                                    specification
                                        .primaryTimeframe
                            )

                    when (parsed) {
                        is HistoricalCsvParseResult
                            .Success -> {
                            importedCandles =
                                parsed.candles
                            importWarnings =
                                parsed.warnings
                            importMessage =
                                "Loaded " +
                                    parsed.candles
                                        .size +
                                    " candles locally."
                        }

                        is HistoricalCsvParseResult
                            .Failure -> {
                            importMessage =
                                parsed.errors
                                    .joinToString(
                                        "\n"
                                    )
                        }
                    }

                    importBusy = false
                }
            }
        }

    val report =
        remember(
            specification,
            importedCandles
        ) {
            BacktestLabDemo.run(
                specification =
                    specification,
                candlesOverride =
                    importedCandles,
                datasetLabelOverride =
                    if (
                        importedCandles !=
                            null
                    ) {
                        "Local CSV dataset"
                    } else {
                        null
                    }
            )
        }

    LazyColumn(
        contentPadding =
            PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Deterministic Strategy Backtest",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        specification.name +
                            " • " +
                            specification.symbol +
                            " • " +
                            specification.primaryTimeframe
                    )
                    Text(
                        report.datasetLabel +
                            " • " +
                            report.candleCount +
                            " candles",
                        color =
                            MaterialTheme
                                .colorScheme
                                .secondary
                    )
                    Text(
                        if (
                            importedCandles !=
                                null
                        ) {
                            "Local file only — this importer does not upload the CSV."
                        } else {
                            "Research dataset only — not live market history."
                        },
                        color =
                            MaterialTheme
                                .colorScheme
                                .tertiary
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "Historical Data",
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        "Import OHLC CSV locally to test the Strategy Specification without sending the file to a server.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .secondary
                    )
                    Text(
                        "Required columns: time/timestamp, open, high, low, close. Optional: volume.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .tertiary
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        Button(
                            enabled =
                                !importBusy,
                            onClick = {
                                csvLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "application/vnd.ms-excel"
                                    )
                                )
                            }
                        ) {
                            Text(
                                if (importBusy) {
                                    "Importing..."
                                } else {
                                    "Import CSV"
                                }
                            )
                        }

                        if (
                            importedCandles !=
                                null
                        ) {
                            TextButton(
                                onClick = {
                                    importedCandles =
                                        null
                                    importMessage =
                                        "Using deterministic sample dataset."
                                    importWarnings =
                                        emptyList()
                                }
                            ) {
                                Text("Use Sample")
                            }
                        }
                    }

                    importMessage?.let {
                        Text(
                            it,
                            color =
                                if (
                                    importedCandles !=
                                        null
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .secondary
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .tertiary
                                }
                        )
                    }

                    importWarnings.forEach {
                        Text(
                            "Warning • " + it,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .tertiary
                        )
                    }
                }
            }
        }

        if (
            report.compatibility
                .errors
                .isNotEmpty()
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                5.dp
                            )
                    ) {
                        Text(
                            "Compatibility blocked",
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                        report.compatibility
                            .errors
                            .forEach {
                                Text(
                                    it,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error
                                )
                            }
                    }
                }
            }
        }

        if (
            report.compatibility
                .warnings
                .isNotEmpty()
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                5.dp
                            )
                    ) {
                        Text(
                            "Model notes",
                            fontWeight =
                                FontWeight.Bold
                        )
                        report.compatibility
                            .warnings
                            .forEach {
                                Text(
                                    it,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .tertiary
                                )
                            }
                    }
                }
            }
        }

        report.result?.let { result ->
            val metrics =
                result.metrics

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            "Performance",
                            fontWeight =
                                FontWeight.Bold
                        )
                        Text(
                            "Net return: " +
                                formatBacktestNumber(
                                    metrics
                                        .netReturnPercent
                                ) +
                                "%"
                        )
                        Text(
                            "Trades: " +
                                metrics.tradeCount +
                                " • Win rate: " +
                                formatBacktestNumber(
                                    metrics
                                        .winRatePercent
                                ) +
                                "%"
                        )
                        Text(
                            "Profit factor: " +
                                (
                                    metrics
                                        .profitFactor
                                        ?.let(
                                            ::formatBacktestNumber
                                        )
                                        ?: "N/A"
                                    )
                        )
                        Text(
                            "Max DD: " +
                                formatBacktestNumber(
                                    metrics
                                        .maxDrawdownPercent
                                ) +
                                "% • Expectancy: " +
                                formatBacktestNumber(
                                    metrics
                                        .expectancyR
                                ) +
                                "R"
                        )
                        Text(
                            "Avg R: " +
                                formatBacktestNumber(
                                    metrics.averageR
                                ) +
                                " • Exposure: " +
                                formatBacktestNumber(
                                    metrics
                                        .exposurePercent
                                ) +
                                "%"
                        )
                        Text(
                            "Ending balance: " +
                                formatBacktestNumber(
                                    result.endingBalance
                                )
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                5.dp
                            )
                    ) {
                        Text(
                            "Execution assumptions",
                            fontWeight =
                                FontWeight.Bold
                        )
                        Text(
                            "Spread: " +
                                report.costs
                                    .spreadPriceUnits +
                                " price units"
                        )
                        Text(
                            "Slippage: " +
                                report.costs
                                    .slippagePriceUnits +
                                " price units / side"
                        )
                        Text(
                            "Commission/unit/side: " +
                                report.costs
                                    .commissionPerUnitPerSide
                        )
                        Text(
                            "If stop and target are both touched in the same candle, the engine resolves STOP first for a conservative ambiguous-bar assumption.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .tertiary
                        )
                    }
                }
            }

            item {
                Text(
                    "Recent trades",
                    fontWeight =
                        FontWeight.Bold
                )
            }

            items(
                result.trades
                    .takeLast(12)
                    .reversed()
            ) { trade ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                3.dp
                            )
                    ) {
                        Text(
                            trade.side.name +
                                " • " +
                                trade.exitReason.name,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                        Text(
                            "Entry " +
                                formatBacktestNumber(
                                    trade.entryPrice
                                ) +
                                " → Exit " +
                                formatBacktestNumber(
                                    trade.exitPrice
                                )
                        )
                        Text(
                            "Result " +
                                formatBacktestNumber(
                                    trade.rMultiple
                                ) +
                                "R • " +
                                formatBacktestNumber(
                                    trade.netPnl
                                )
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick =
                        onBackToStrategy
                ) {
                    Text("Back to Strategy")
                }
                Button(
                    onClick = onOpenPine
                ) {
                    Text("Open Pine")
                }
            }
        }
    }
}

private fun formatBacktestNumber(
    value: Double
): String =
    String.format(
        Locale.US,
        "%.2f",
        value
    )

@Composable
private fun PineLabPanel(
    code: String,
    onCodeChange: (String) -> Unit,
    report: QuantRuntimeReport?,
    onAnalyze: () -> Unit,
    onOpenChart: () -> Unit
) {
    val findings = remember(code) {
        PineStaticAnalyzer.analyze(code)
    }

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
                    Text(
                        "Pine Script Laboratory",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Generate → review → local Quant Runtime → chart → deterministic strategy backtest.",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pine source") },
                minLines = 16,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAnalyze) {
                    Text("Run Quant Runtime")
                }
                Button(onClick = onOpenChart) {
                    Text("Open Chart Sandbox")
                }
            }
        }

        if (findings.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("TradingView QA", fontWeight = FontWeight.Bold)
                        findings.forEach { finding ->
                            Text(
                                finding.severity + " • " +
                                    finding.code + " • " +
                                    finding.message
                            )
                        }
                    }
                }
            }
        }

        report?.let { runtime ->
            item {
                QuantRuntimeReportCard(runtime)
            }
        }
    }
}

@Composable
private fun QuantRuntimeReportCard(
    report: QuantRuntimeReport
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "DrFXQuant Runtime Report",
                fontWeight = FontWeight.Bold
            )
            if (!report.ok) {
                Text(
                    report.error ?: "Runtime failed.",
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }

            Text(
                report.title + " • Pine v" +
                    report.pineVersion + " • " +
                    if (report.overlay) "overlay" else "separate pane"
            )
            Text(
                "Lines " + report.lineCount +
                    " • AST nodes " + report.nodeCount +
                    " • runtime " + report.runtimeMs + "ms"
            )
            Text(
                "Plots " + report.plots +
                    " • Shapes " + report.shapes +
                    " • Labels " + report.labelSeries.size +
                    " • Inputs " + report.inputs.size
            )
            Text(
                "Plot activity: " +
                    report.plotActivity.joinToString(prefix = "[", postfix = "]")
            )

            if (report.isStrategy && report.strategyOrders > 0) {
                Text(
                    "This legacy Quant Runtime visualizes chart logic but does not simulate strategy orders. Scalper Backtest handles strategy execution separately.",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (report.parseErrors.isNotEmpty()) {
                Text(
                    "Parse errors: " + report.parseErrors.size,
                    color = MaterialTheme.colorScheme.error
                )
                report.parseErrors.take(8).forEach { Text(it) }
            }

            if (report.unsupported.isNotEmpty()) {
                Text(
                    "Unsupported constructs: " + report.unsupported.size,
                    color = MaterialTheme.colorScheme.tertiary
                )
                report.unsupported.take(8).forEach { Text(it) }
            }

            if (report.parseErrors.isEmpty() && report.unsupported.isEmpty()) {
                Text(
                    "Local compatibility check completed without reported skips.",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun ChartLabPanel(
    report: QuantRuntimeReport?,
    onBackToPine: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "Interactive Chart Sandbox",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "TradingView Lightweight Charts 5.2.0 is embedded as the chart layer. The current sandbox uses deterministic sample candles while provider-backed datasets are still disconnected.",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                QuantLabChart(
                    report = report,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp)
                )
            }
        }

        item {
            Text(
                if (report?.ok == true) {
                    val markerCount =
                        report.shapeSeries.sumOf {
                            it.indices.size
                        } +
                            report.labelSeries.size

                    "Runtime plots and " +
                        markerCount +
                        " signal/label markers are mapped into the chart. Line, box, fill and advanced drawing primitives remain a later renderer step."
                } else {
                    "Run Quant Runtime from Pine Lab first to replace the sample EMA with generated script plot and signal output."
                },
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        item {
            TextButton(onClick = onBackToPine) {
                Text("Back to Pine Lab")
            }
        }
    }
}

@Composable
private fun CodeArtifactPanel(
    title: String,
    artifact: CodeArtifact,
    note: String
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                "Verification: " + artifact.verificationStatus.name,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(note)
            artifact.findings.forEach { finding ->
                Text(
                    finding.severity + " • " +
                        finding.code + " • " +
                        finding.message
                )
            }
            Text(
                artifact.source,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}


private fun extractCodeBlock(
    text: String
): String {
    val fenceStart =
        text.indexOf("```")

    if (fenceStart < 0) {
        return text.trim()
    }

    val firstLineEnd =
        text.indexOf('\n', fenceStart)

    if (firstLineEnd < 0) {
        return text.trim()
    }

    val fenceEnd =
        text.indexOf(
            "```",
            firstLineEnd + 1
        )

    if (fenceEnd < 0) {
        return text.trim()
    }

    return text
        .substring(
            firstLineEnd + 1,
            fenceEnd
        )
        .trim()
}
