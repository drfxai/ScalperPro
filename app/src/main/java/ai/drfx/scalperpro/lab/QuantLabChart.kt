package ai.drfx.scalperpro.lab

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tradingview.lightweightcharts.api.chart.models.color.SolidColor
import com.tradingview.lightweightcharts.api.chart.models.color.toIntColor
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.LineSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.gridLineOptions
import com.tradingview.lightweightcharts.api.options.models.gridOptions
import com.tradingview.lightweightcharts.api.options.models.layoutOptions
import com.tradingview.lightweightcharts.api.options.models.timeScaleOptions
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.SeriesType
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.LineData
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.view.ChartsView
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun QuantLabChart(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ChartsView(context).apply {
                var configured = false
                subscribeOnChartStateChange { state ->
                    if (state is ChartsView.State.Ready && !configured) {
                        configured = true
                        setupQuantLabChart(this)
                    }
                }
            }
        }
    )
}

private fun setupQuantLabChart(
    chartsView: ChartsView
) {
    val chartApi = chartsView.api
    val candles = sampleCandles()
    val ema = movingAverage(candles, 20)

    chartApi.applyOptions {
        layout = layoutOptions {
            background = SolidColor(Color.parseColor("#080B14").toIntColor())
            textColor = Color.parseColor("#AEB9D0").toIntColor()
            attributionLogo = false
        }
        grid = gridOptions {
            vertLines = gridLineOptions {
                color = Color.argb(30, 120, 140, 190).toIntColor()
            }
            horzLines = gridLineOptions {
                color = Color.argb(38, 120, 140, 190).toIntColor()
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
    }

    chartApi.addSeries(
        type = SeriesType.LINE,
        options = LineSeriesOptions(
            title = "EMA 20",
            color = Color.parseColor("#8B5CF6").toIntColor(),
            lineWidth = LineWidth.TWO,
            pointMarkersVisible = false
        ),
        paneIndex = 0
    ) { series ->
        series.setData(ema)
    }

    chartApi.timeScale.fitContent()
}

private fun sampleCandles(): List<CandlestickData> {
    var previousClose = 2350f
    val start = 1_798_761_600L

    return (0 until 140).map { index ->
        val open = previousClose
        val wave =
            sin(index / 5.5).toFloat() * 4.2f +
                sin(index / 17.0).toFloat() * 2.1f
        val close = open + wave * 0.38f + if (index % 13 == 0) 2.2f else -0.15f
        val wick = 1.5f + abs(sin(index / 4.0)).toFloat() * 1.8f
        val high = maxOf(open, close) + wick
        val low = minOf(open, close) - wick
        previousClose = close

        CandlestickData(
            time = Time.Utc(start + index * 900L),
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
        val from = maxOf(0, index - length + 1)
        val window = candles.subList(from, index + 1)
        LineData(
            time = candle.time,
            value = window.map { it.close }.average().toFloat()
        )
    }
