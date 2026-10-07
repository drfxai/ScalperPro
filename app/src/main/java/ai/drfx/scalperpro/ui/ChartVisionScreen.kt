package ai.drfx.scalperpro.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    val context =
        LocalContext.current

    var symbol by remember {
        mutableStateOf("XAUUSD")
    }
    var timeframe by remember {
        mutableStateOf("15m")
    }
    var session by remember {
        mutableStateOf("London")
    }
    var volatility by remember {
        mutableStateOf("Unknown")
    }
    var image by remember {
        mutableStateOf<
            ChartImageDescriptor?
            >(null)
    }
    var preview by remember {
        mutableStateOf<
            ImageBitmap?
            >(null)
    }
    var status by remember {
        mutableStateOf(
            "Upload a chart screenshot to prepare a structured AI analysis request."
        )
    }

    val picker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .PickVisualMedia()
        ) {
            uri ->
            if (uri == null) {
                status =
                    "No image selected."
                return@rememberLauncherForActivityResult
            }

            val resolver =
                context.contentResolver
            val mimeType =
                resolver
                    .getType(uri)
                    .orEmpty()
            var sizeBytes = 0L

            resolver.query(
                uri,
                arrayOf(
                    OpenableColumns.SIZE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val sizeColumn =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE
                    )

                if (
                    sizeColumn >= 0 &&
                    cursor.moveToFirst() &&
                    !cursor.isNull(
                        sizeColumn
                    )
                ) {
                    sizeBytes =
                        cursor.getLong(
                            sizeColumn
                        )
                }
            }

            image =
                ChartImageDescriptor(
                    id = uri.toString(),
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    sha256 = null
                )

            preview =
                loadChartPreview(
                    context,
                    uri
                )

            status =
                "Chart selected. Review symbol/timeframe, then run Analyze."
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                PremiumScreenBrush
            )
    ) {
        PageHeader(
            title = "AI Chart Vision",
            subtitle =
                "Upload chart · get structured analysis",
            onHome = onHome
        )

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = 14.dp,
                    vertical = 4.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            item {
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        PremiumColors.Purple
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp
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
                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        9.dp
                                    ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded
                                        .BarChart,
                                    contentDescription =
                                        null,
                                    tint =
                                        PremiumColors
                                            .Cyan
                                )
                                Column {
                                    Text(
                                        "AI Chart Vision",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleLarge,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                    Text(
                                        "Context-first multimodal workflow",
                                        color =
                                            PremiumColors
                                                .TextMuted,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                            }

                            PremiumStatusPill(
                                text =
                                    "AI Powered",
                                accent =
                                    PremiumColors
                                        .Purple
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    1.7f
                                )
                                .background(
                                    PremiumColors
                                        .BackgroundSoft,
                                    RoundedCornerShape(
                                        18.dp
                                    )
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            if (preview != null) {
                                Image(
                                    bitmap =
                                        preview!!,
                                    contentDescription =
                                        "Selected chart preview",
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),
                                    contentScale =
                                        ContentScale
                                            .Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment =
                                        Alignment
                                            .CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp
                                        )
                                ) {
                                    Icon(
                                        Icons.Rounded
                                            .Timeline,
                                        contentDescription =
                                            null,
                                        tint =
                                            PremiumColors
                                                .Cyan,
                                        modifier =
                                            Modifier.padding(
                                                6.dp
                                            )
                                    )
                                    Text(
                                        "Chart preview",
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )
                                    Text(
                                        "PNG / JPG up to your validator limit",
                                        color =
                                            PremiumColors
                                                .TextMuted,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            item {
                                VisionField(
                                    title =
                                        "Symbol",
                                    value = symbol,
                                    onValueChange = {
                                        symbol = it
                                    }
                                )
                            }
                            item {
                                VisionField(
                                    title =
                                        "Timeframe",
                                    value =
                                        timeframe,
                                    onValueChange = {
                                        timeframe =
                                            it
                                    }
                                )
                            }
                            item {
                                VisionField(
                                    title =
                                        "Session",
                                    value = session,
                                    onValueChange = {
                                        session = it
                                    }
                                )
                            }
                            item {
                                VisionField(
                                    title =
                                        "Volatility",
                                    value =
                                        volatility,
                                    onValueChange = {
                                        volatility =
                                            it
                                    }
                                )
                            }
                        }
                    }
                }
            }

            items(
                listOf(
                    AnalysisRow(
                        "Market Structure",
                        "Higher highs/lows, BOS/CHoCH and structural context.",
                        PremiumColors.Cyan
                    ),
                    AnalysisRow(
                        "Trend",
                        "Trend direction, moving-average context and timeframe alignment.",
                        PremiumColors.Green
                    ),
                    AnalysisRow(
                        "Support / Resistance",
                        "Important reaction zones and invalidation levels.",
                        PremiumColors.Purple
                    ),
                    AnalysisRow(
                        "Momentum",
                        "RSI / momentum / volume context where evidence exists.",
                        PremiumColors.Cyan
                    ),
                    AnalysisRow(
                        "News Context",
                        "Trusted news/calendar context is added only when a provider is connected.",
                        PremiumColors.Cyan
                    ),
                    AnalysisRow(
                        "Possible Scenario",
                        "Conditional scenario, never a guaranteed price prediction.",
                        PremiumColors.Gold
                    ),
                    AnalysisRow(
                        "Invalidation",
                        "Explicit conditions that invalidate the current scenario.",
                        PremiumColors.Red
                    ),
                    AnalysisRow(
                        "Risk Notes",
                        "Position-size and event-risk reminders for educational use.",
                        PremiumColors.Gold
                    )
                )
            ) {
                row ->
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent = row.accent
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        14.dp,
                                    vertical =
                                        12.dp
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector =
                                if (
                                    row.title ==
                                        "Risk Notes"
                                ) {
                                    Icons.Rounded
                                        .Shield
                                } else {
                                    Icons.Rounded
                                        .AutoAwesome
                                },
                            contentDescription =
                                null,
                            tint = row.accent
                        )
                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                row.title,
                                fontWeight =
                                    FontWeight.Bold
                            )
                            Text(
                                if (image == null) {
                                    "Awaiting chart upload · " +
                                        row.description
                                } else {
                                    "Ready for AI analysis · " +
                                        row.description
                                },
                                color =
                                    PremiumColors
                                        .TextSecondary,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                        Text(
                            "⌄",
                            color =
                                PremiumColors
                                    .TextMuted
                        )
                    }
                }
            }

            item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    Button(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts
                                        .PickVisualMedia
                                        .ImageOnly
                                )
                            )
                        },
                        modifier =
                            Modifier.weight(1f),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        PremiumColors
                                            .SurfaceStrong
                                )
                    ) {
                        Icon(
                            Icons.Rounded
                                .CloudUpload,
                            contentDescription =
                                null
                        )
                        Text(
                            " Upload"
                        )
                    }

                    Button(
                        enabled = image != null,
                        onClick = {
                            val descriptor =
                                image

                            if (descriptor == null) {
                                status =
                                    "Select a chart image first."
                                return@Button
                            }

                            val request =
                                ChartVisionRequest(
                                    image =
                                        descriptor,
                                    context =
                                        ChartVisionContext(
                                            symbol =
                                                symbol
                                                    .trim(),
                                            timeframe =
                                                timeframe
                                                    .trim(),
                                            recentCandles =
                                                emptyList(),
                                            volatilityLabel =
                                                volatility,
                                            marketSession =
                                                session,
                                            userRisk =
                                                null
                                        )
                                )

                            val validation =
                                ChartVisionRequestValidator
                                    .validate(
                                        request
                                    )

                            status =
                                if (
                                    validation.valid
                                ) {
                                    "Request is valid. Live AI analysis requires the trusted Scalper AI Gateway plus current market/news context."
                                } else {
                                    validation.errors
                                        .joinToString(
                                            "\n"
                                        )
                                }
                        },
                        modifier =
                            Modifier.weight(1f),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        PremiumColors
                                            .Purple
                                )
                    ) {
                        Icon(
                            Icons.Rounded
                                .AutoAwesome,
                            contentDescription =
                                null
                        )
                        Text(
                            " Analyze"
                        )
                    }

                    Button(
                        enabled = image != null,
                        onClick = {
                            status =
                                "Save Note is prepared for Journal integration; no fabricated analysis was saved."
                        },
                        modifier =
                            Modifier.weight(1f),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        PremiumColors
                                            .SurfaceStrong
                                )
                    ) {
                        Icon(
                            Icons.Rounded.Save,
                            contentDescription =
                                null
                        )
                        Text(
                            " Save"
                        )
                    }
                }
            }

            item {
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        PremiumColors.Purple
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            status,
                            color =
                                PremiumColors
                                    .TextSecondary
                        )
                        image?.let {
                            descriptor ->
                            Text(
                                descriptor.mimeType +
                                    " · " +
                                    descriptor.sizeBytes +
                                    " bytes",
                                color =
                                    PremiumColors
                                        .TextMuted,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                        Text(
                            "AI is supportive, not guaranteed financial advice. Use structured analysis as educational decision support.",
                            color =
                                PremiumColors
                                    .TextMuted,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }
        }
    }
}

private data class AnalysisRow(
    val title: String,
    val description: String,
    val accent: androidx.compose.ui.graphics.Color
)

@Composable
private fun VisionField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange =
            onValueChange,
        label = {
            Text(title)
        },
        singleLine = true,
        modifier =
            Modifier
                .fillMaxWidth(0.54f)
    )
}

private fun loadChartPreview(
    context: Context,
    uri: Uri
): ImageBitmap? =
    runCatching {
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        context
            .contentResolver
            .openInputStream(uri)
            ?.use {
                stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    bounds
                )
            }

        var sample = 1
        val largest =
            maxOf(
                bounds.outWidth,
                bounds.outHeight
            )

        while (
            largest / sample >
                1400
        ) {
            sample *= 2
        }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
            }

        val bitmap =
            context
                .contentResolver
                .openInputStream(uri)
                ?.use {
                    stream ->
                    BitmapFactory
                        .decodeStream(
                            stream,
                            null,
                            options
                        )
                }

        bitmap?.asImageBitmap()
    }.getOrNull()
