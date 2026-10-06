package ai.drfx.scalperpro.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.vision.ChartImageDescriptor
import ai.drfx.scalperpro.vision.ChartVisionContext
import ai.drfx.scalperpro.vision.ChartVisionRequest
import ai.drfx.scalperpro.vision.ChartVisionRequestValidator

@Composable
internal fun ChartVisionScreen(
    onHome: () -> Unit
) {
    val context = LocalContext.current
    var symbol by remember { mutableStateOf("XAUUSD") }
    var timeframe by remember { mutableStateOf("15m") }
    var image by remember { mutableStateOf<ChartImageDescriptor?>(null) }
    var status by remember {
        mutableStateOf("Select a chart image to prepare the multimodal analysis request.")
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            status = "No image selected."
            return@rememberLauncherForActivityResult
        }

        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty()
        var sizeBytes = 0L

        resolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeColumn >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeColumn)) {
                sizeBytes = cursor.getLong(sizeColumn)
            }
        }

        image = ChartImageDescriptor(
            id = uri.toString(),
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sha256 = null
        )

        status = "Image selected. Validate the request before analysis."
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("AI Chart Vision", onHome)

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
                        Text("Context-first chart analysis", fontWeight = FontWeight.Bold)
                        Text(
                            "Chart Vision is designed to combine the screenshot with symbol, timeframe, market data, higher-timeframe context, news, calendar and risk settings.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "The screenshot alone is never treated as sufficient evidence for an authoritative trading signal.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Symbol") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = timeframe,
                    onValueChange = { timeframe = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Timeframe") },
                    singleLine = true
                )
            }

            item {
                Button(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                ) {
                    Text("Select chart image")
                }
            }

            image?.let { descriptor ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Selected image", fontWeight = FontWeight.Bold)
                            Text("MIME: " + descriptor.mimeType)
                            Text("Size: " + descriptor.sizeBytes + " bytes")
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            val request = ChartVisionRequest(
                                image = descriptor,
                                context = ChartVisionContext(
                                    symbol = symbol.trim(),
                                    timeframe = timeframe.trim(),
                                    recentCandles = emptyList(),
                                    volatilityLabel = null,
                                    marketSession = null,
                                    userRisk = null
                                )
                            )
                            val validation = ChartVisionRequestValidator.validate(request)
                            status = if (validation.valid) {
                                "Request contract is valid. Live AI analysis still requires the trusted Scalper AI Gateway plus current market/news context."
                            } else {
                                validation.errors.joinToString(separator = "\n")
                            }
                        }
                    ) {
                        Text("Validate analysis request")
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        status,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Structured output", fontWeight = FontWeight.Bold)
                        Text("Market Structure • Trend • Support / Resistance • Liquidity")
                        Text("BOS / CHoCH • FVG • Momentum • Volatility • Session")
                        Text("News Context • Possible Scenario • Invalidation • Risk Notes")
                    }
                }
            }
        }
    }
}
