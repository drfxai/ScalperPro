package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.risk.InstrumentRiskCalculator
import ai.drfx.scalperpro.risk.InstrumentRiskSpec
import ai.drfx.scalperpro.risk.RiskCalculationRequest
import ai.drfx.scalperpro.risk.RiskCalculationResult
import java.util.Locale

@Composable
internal fun RiskScreen(
    onHome: () -> Unit
) {
    var balance by remember { mutableStateOf("10000") }
    var riskPercent by remember { mutableStateOf("1") }
    var entry by remember { mutableStateOf("2000") }
    var stop by remember { mutableStateOf("1990") }
    var target by remember { mutableStateOf("2020") }
    var result by remember { mutableStateOf<RiskCalculationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val xauSpec = remember {
        InstrumentRiskSpec(
            symbol = "XAUUSD",
            pnlPerPriceUnitPerQuantity = 100.0,
            quantityStep = 0.01,
            minQuantity = 0.01,
            maxQuantity = 100.0
        )
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Risk Manager", onHome)

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
                        Text("Deterministic sizing", fontWeight = FontWeight.Bold)
                        Text(
                            "The calculator uses explicit instrument metadata, not an LLM.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Current instrument profile: XAUUSD, 100 account-currency units per 1.0 price move per 1.0 quantity. Verify broker contract specifications before live use.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Balance") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = riskPercent,
                    onValueChange = { riskPercent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Risk %") },
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
                    label = { Text("Target (optional)") },
                    singleLine = true
                )
            }

            item {
                Button(
                    onClick = {
                        try {
                            val request = RiskCalculationRequest(
                                balance = balance.toDouble(),
                                riskPercent = riskPercent.toDouble(),
                                entryPrice = entry.toDouble(),
                                stopPrice = stop.toDouble(),
                                targetPrice = target.toDoubleOrNull(),
                                instrument = xauSpec
                            )
                            result = InstrumentRiskCalculator.calculate(request)
                            error = null
                        } catch (throwable: Throwable) {
                            result = null
                            error = throwable.message ?: "Invalid calculation input."
                        }
                    }
                ) {
                    Text("Calculate")
                }
            }

            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            result?.let { calculation ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Result", fontWeight = FontWeight.Bold)
                            Text("Maximum planned loss: " + format(calculation.maxLossAmount))
                            Text("Stop distance: " + format(calculation.stopDistance))
                            Text("Quantity: " + format(calculation.quantity))
                            Text("Estimated loss at stop: " + format(calculation.estimatedLossAtStop))
                            Text(
                                "Reward / Risk: " +
                                    (calculation.rewardRisk?.let { format(it) } ?: "—")
                            )
                            if (!calculation.withinInstrumentBounds) {
                                Text(
                                    "Raw quantity was outside configured instrument bounds and was clamped.",
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun format(value: Double): String =
    String.format(Locale.US, "%.4f", value)
