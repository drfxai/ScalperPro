package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.code.CodeArtifact
import ai.drfx.scalperpro.code.Mql5StudioEngine
import ai.drfx.scalperpro.code.PineStudioEngine
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

internal enum class LabMode {
    SPECIFICATION,
    PINE,
    MQL5
}

@Composable
internal fun LabScreen(
    onHome: () -> Unit,
    initialMode: LabMode = LabMode.SPECIFICATION
) {
    val specification = remember {
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

    val validation = remember(specification) {
        StrategySpecificationValidator.validate(specification)
    }
    val pine = remember(specification) {
        PineStudioEngine.generate(specification)
    }
    val mql5 = remember(specification) {
        Mql5StudioEngine.generate(specification)
    }

    var mode by remember(initialMode) { mutableStateOf(initialMode) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Strategy Lab", onHome)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { mode = LabMode.SPECIFICATION }) { Text("Spec") }
            Button(onClick = { mode = LabMode.PINE }) { Text("Pine") }
            Button(onClick = { mode = LabMode.MQL5 }) { Text("MQL5") }
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
                        Text(specification.name, fontWeight = FontWeight.Bold)
                        Text(
                            specification.symbol + " • " +
                                specification.primaryTimeframe + " • Risk " +
                                specification.risk.riskPercentPerTrade + "%"
                        )
                        Text(
                            if (validation.valid) {
                                "Strategy Specification: VALID"
                            } else {
                                "Strategy Specification: INVALID"
                            },
                            color = if (validation.valid) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        validation.errors.forEach { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            when (mode) {
                LabMode.SPECIFICATION -> {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Single Source of Truth", fontWeight = FontWeight.Bold)
                                Text("Market: " + specification.market.name)
                                Text("Direction: " + specification.directionPermission.name)
                                Text("Stop: " + specification.stop.method.name + " " + specification.stop.value)
                                Text("Target: " + specification.takeProfit.method.name + " " + specification.takeProfit.value)
                                Text("Cooldown bars: " + specification.cooldownBars)
                                Text("Pyramiding: " + specification.pyramiding)
                                specification.entryConditions.forEachIndexed { index, condition ->
                                    Text(
                                        "Entry " + (index + 1) + ": " +
                                            condition.description + " → " + condition.expression
                                    )
                                }
                            }
                        }
                    }
                }

                LabMode.PINE -> {
                    item { CodeArtifactCard("Pine Script Studio", pine) }
                }

                LabMode.MQL5 -> {
                    item { CodeArtifactCard("MQL5 Studio", mql5) }
                }
            }
        }
    }
}

@Composable
private fun CodeArtifactCard(
    title: String,
    artifact: CodeArtifact
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

            if (artifact.findings.isNotEmpty()) {
                artifact.findings.forEach { finding ->
                    Text(
                        finding.severity + " • " + finding.code + " • " + finding.message
                    )
                }
            }

            Text(
                artifact.source,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
