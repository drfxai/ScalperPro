package ai.drfx.scalperpro.lab

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.chart.models.color.toIntColor
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.LineSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.gridLineOptions
import com.tradingview.lightweightcharts.api.options.models.gridOptions
import com.tradingview.lightweightcharts.api.options.models.layoutOptions
import com.tradingview.lightweightcharts.api.options.models.timeScaleOptions
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.SeriesMarkerPosition
import com.tradingview.lightweightcharts.api.series.enums.SeriesMarkerShape
import com.tradingview.lightweightcharts.api.series.enums.SeriesType
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.LineData
import com.tradingview.lightweightcharts.api.series.models.SeriesMarker
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.view.ChartsView
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun QuantLabChart(
    report: QuantRuntimeReport? = null,
    modifier: Modifier = Modifier
) {
    key(report?.hashCode() ?: 0) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                ChartsView(context).apply {
                    var configured = false
                    subscribeOnChartStateChange { state ->
                        if (
                            state is ChartsView.State.Ready &&
                            !configured
                        ) {
                            configured = true
                            setupQuantLabChart(
                                chartsView = this,
                                report = report
                            )
                        }
                    }
                }
            }
        )
    }
}

private fun setupQuantLabChart(
    chartsView: ChartsView,
    report: QuantRuntimeReport?
) {
    val chartApi = chartsView.api
    val candles =
        report
            ?.candleData
            ?.takeIf { it.isNotEmpty() }
            ?.map { candle ->
                CandlestickData(
                    time = Time.Utc(
                        candle.timeEpochSeconds
                    ),
                    open = candle.open.toFloat(),
                    high = candle.high.toFloat(),
                    low = candle.low.toFloat(),
                    close = candle.close.toFloat()
                )
            }
            ?: sampleCandles()

    chartApi.applyOptions {
        layout = layoutOptions {
            background = SolidColor(
                Color.parseColor(
                    "#080B14"
                ).toIntColor()
            )
            textColor =
                Color.parseColor(
                    "#AEB9D0"
                ).toIntColor()
            attributionLogo = true
        }

        grid = gridOptions {
            vertLines = gridLineOptions {
                color = Color.argb(
                    30,
                    120,
                    140,
                    190
                ).toIntColor()
            }
            horzLines = gridLineOptions {
                color = Color.argb(
                    38,
                    120,
                    140,
                    190
                ).toIntColor()
            }
        }

        timeScale = timeScaleOptions {
            rightOffsetPixels = 18f
            maxBarSpacing = 18f
            minimumHeight = 32
        }
    }

    chartApi.addSeries(
        type = SeriesType.CANDLESTICK,
        options = CandlestickSeriesOptions(),
        paneIndex = 0
    ) { series ->
        series.setData(candles)

        val markers =
            runtimeMarkers(
                report = report,
                candles = candles
            )

        if (markers.isNotEmpty()) {
            series.setMarkers(markers)
        }
    }

    val runtimePlots =
        report
            ?.plotSeries
            ?.filter { !it.hidden }
            ?.take(6)
            .orEmpty()

    if (runtimePlots.isEmpty()) {
        chartApi.addSeries(
            type = SeriesType.LINE,
            options = LineSeriesOptions(
                title = "EMA 20 — sample",
                color = seriesColor(
                    "#8B5CF6",
                    0
                ),
                lineWidth = LineWidth.TWO,
                pointMarkersVisible = false
            ),
            paneIndex = 0
        ) { series ->
            series.setData(
                movingAverage(
                    candles,
                    20
                )
            )
        }
    } else if (report?.overlay == false) {
        chartApi.addPane(
            preserveEmptyPane = false
        ) { pane ->
            runtimePlots.forEachIndexed {
                    index,
                    plot ->
                val values =
                    runtimeLineData(
                        plot = plot,
                        candles = candles
                    )

                if (values.isNotEmpty()) {
                    pane.addSeries(
                        type = SeriesType.LINE,
                        options = LineSeriesOptions(
                            title = plot.title,
                            color = seriesColor(
                                plot.color,
                                index
                            ),
                            lineWidth = LineWidth.TWO,
                            pointMarkersVisible = false
                        )
                    ) { series ->
                        series.setData(values)
                    }
                }
            }
        }
    } else {
        runtimePlots.forEachIndexed {
                index,
                plot ->
            val values =
                runtimeLineData(
                    plot = plot,
                    candles = candles
                )

            if (values.isNotEmpty()) {
                chartApi.addSeries(
                    type = SeriesType.LINE,
                    options = LineSeriesOptions(
                        title = plot.title,
                        color = seriesColor(
                            plot.color,
                            index
                        ),
                        lineWidth = LineWidth.TWO,
                        pointMarkersVisible = false
                    ),
                    paneIndex = 0
                ) { series ->
                    series.setData(values)
                }
            }
        }
    }

    chartApi.timeScale.fitContent()
}

private fun runtimeMarkers(
    report: QuantRuntimeReport?,
    candles: List<CandlestickData>
): List<SeriesMarker> {
    if (report == null) {
        return emptyList()
    }

    val shapeMarkers =
        report.shapeSeries
            .flatMapIndexed {
                    seriesIndex,
                    shape ->
                shape.indices
                    .mapNotNull {
                            candleIndex ->
                        val candle =
                            candles.getOrNull(
                                candleIndex
                            ) ?:
                                return@mapNotNull null

                        SeriesMarker(
                            time = candle.time,
                            position =
                                if (shape.below) {
                                    SeriesMarkerPosition
                                        .BELOW_BAR
                                } else {
                                    SeriesMarkerPosition
                                        .ABOVE_BAR
                                },
                            color = seriesColor(
                                shape.color,
                                seriesIndex
                            ),
                            shape =
                                when (shape.glyph) {
                                    "▼" ->
                                        SeriesMarkerShape
                                            .ARROW_DOWN
                                    "●" ->
                                        SeriesMarkerShape
                                            .CIRCLE
                                    else ->
                                        SeriesMarkerShape
                                            .ARROW_UP
                                },
                            text =
                                shape.text
                                    .ifBlank {
                                        shape.title
                                    }
                                    .take(48),
                            id =
                                "runtime-shape-" +
                                    seriesIndex +
                                    "-" +
                                    candleIndex
                        )
                    }
            }

    val labelMarkers =
        report.labelSeries
            .mapNotNull {
                    label ->
                val candle =
                    candles.getOrNull(
                        label.index
                    ) ?:
                        return@mapNotNull null

                val isDown =
                    label.direction
                        .contains(
                            "down",
                            ignoreCase = true
                        )

                SeriesMarker(
                    time = candle.time,
                    position =
                        if (isDown) {
                            SeriesMarkerPosition
                                .ABOVE_BAR
                        } else {
                            SeriesMarkerPosition
                                .BELOW_BAR
                        },
                    color = seriesColor(
                        label.color,
                        label.index
                    ),
                    shape =
                        if (isDown) {
                            SeriesMarkerShape
                                .ARROW_DOWN
                        } else {
                            SeriesMarkerShape
                                .ARROW_UP
                        },
                    text =
                        label.text
                            .ifBlank {
                                "Label"
                            }
                            .take(48),
                    id =
                        "runtime-label-" +
                            label.index
                )
            }

    return (shapeMarkers + labelMarkers)
        .sortedBy { marker ->
            when (val time = marker.time) {
                is Time.Utc -> time.timestamp
                else -> 0L
            }
        }
        .takeLast(900)
}

private fun runtimeLineData(
    plot: QuantRuntimePlotSeries,
    candles: List<CandlestickData>
): List<LineData> {
    val limit = minOf(
        plot.values.size,
        candles.size
    )

    return buildList {
        for (index in 0 until limit) {
            val value =
                plot.values[index] ?:
                    continue

            if (!value.isFinite()) continue

            add(
                LineData(
                    time = candles[index].time,
                    value = value.toFloat()
                )
            )
        }
    }
}

private fun seriesColor(
    raw: String,
    index: Int
) =
    runCatching {
        Color.parseColor(raw)
    }.getOrElse {
        FALLBACK_COLORS[
            index % FALLBACK_COLORS.size
        ]
    }.toIntColor()

private fun sampleCandles(): List<CandlestickData> {
    var previousClose = 2350f
    val start = 1_798_761_600L

    return (0 until 140).map { index ->
        val open = previousClose
        val wave =
            sin(index / 5.5).toFloat() *
                4.2f +
                sin(index / 17.0)
                    .toFloat() *
                2.1f

        val close =
            open +
                wave * 0.38f +
                if (index % 13 == 0) {
                    2.2f
                } else {
                    -0.15f
                }

        val wick =
            1.5f +
                abs(
                    sin(index / 4.0)
                ).toFloat() *
                1.8f

        val high =
            maxOf(open, close) + wick
        val low =
            minOf(open, close) - wick

        previousClose = close

        CandlestickData(
            time = Time.Utc(
                start + index * 900L
            ),
            open = open,
            high = high,
            low = low,
            close = close
        )
    }
}

private fun movingAverage(
    candles: List<CandlestickData>,
    length: Int
): List<LineData> =
    candles.mapIndexed { index, candle ->
        val from =
            maxOf(
                0,
                index - length + 1
            )

        val window =
            candles.subList(
                from,
                index + 1
            )

        LineData(
            time = candle.time,
            value =
                window
                    .map { it.close }
                    .average()
                    .toFloat()
        )
    }

private val FALLBACK_COLORS = intArrayOf(
    Color.parseColor("#8B5CF6"),
    Color.parseColor("#22D3EE"),
    Color.parseColor("#F59E0B"),
    Color.parseColor("#10B981"),
    Color.parseColor("#EC4899"),
    Color.parseColor("#60A5FA")
)
