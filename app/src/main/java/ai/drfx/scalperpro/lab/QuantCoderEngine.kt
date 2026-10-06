package ai.drfx.scalperpro.lab

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class QuantRuntimeInput(
    val title: String,
    val defaultValue: String?
)

data class QuantRuntimeReport(
    val ok: Boolean,
    val title: String,
    val pineVersion: Int,
    val overlay: Boolean,
    val isStrategy: Boolean,
    val strategyOrders: Int,
    val lineCount: Int,
    val nodeCount: Int,
    val plots: Int,
    val shapes: Int,
    val candles: Int,
    val inputs: List<QuantRuntimeInput>,
    val warnings: List<String>,
    val parseErrors: List<String>,
    val unsupported: List<String>,
    val plotActivity: List<Int>,
    val shapeActivity: List<Int>,
    val runtimeMs: Long,
    val engine: String,
    val error: String?
)

@SuppressLint("SetJavaScriptEnabled")
class QuantCoderEngine(
    context: Context
) {
    private val webView = WebView(context.applicationContext)
    private var ready = false
    private val pending = ArrayDeque<() -> Unit>()

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = false
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                ready = true
                while (pending.isNotEmpty()) {
                    pending.removeFirst().invoke()
                }
            }
        }
        webView.loadUrl("file:///android_asset/quantlab/engine.html")
    }

    fun analyze(
        source: String,
        onResult: (QuantRuntimeReport) -> Unit
    ) {
        val action = {
            val quotedSource = JSONObject.quote(source)
            webView.evaluateJavascript(
                "window.scalperQuantAnalyze($quotedSource)"
            ) { raw ->
                onResult(parseReport(raw))
            }
        }

        if (ready) {
            action()
        } else {
            pending.addLast(action)
        }
    }

    fun destroy() {
        pending.clear()
        webView.stopLoading()
        webView.destroy()
    }

    private fun parseReport(raw: String?): QuantRuntimeReport {
        return try {
            val decoded = JSONTokener(raw ?: "null").nextValue()
            val payload = when (decoded) {
                is String -> decoded
                is JSONObject -> decoded.toString()
                else -> "{}"
            }

            val json = JSONObject(payload)
            QuantRuntimeReport(
                ok = json.optBoolean("ok", false),
                title = json.optString("title", "Custom"),
                pineVersion = json.optInt("pineVersion", 0),
                overlay = json.optBoolean("overlay", true),
                isStrategy = json.optBoolean("isStrategy", false),
                strategyOrders = json.optInt("strategyOrders", 0),
                lineCount = json.optInt("lineCount", 0),
                nodeCount = json.optInt("nodeCount", 0),
                plots = json.optInt("plots", 0),
                shapes = json.optInt("shapes", 0),
                candles = json.optInt("candles", 0),
                inputs = parseInputs(json.optJSONArray("inputs")),
                warnings = parseStrings(json.optJSONArray("warnings")),
                parseErrors = parseStrings(json.optJSONArray("parseErrors")),
                unsupported = parseStrings(json.optJSONArray("unsupported")),
                plotActivity = parseInts(json.optJSONArray("plotActivity")),
                shapeActivity = parseInts(json.optJSONArray("shapeActivity")),
                runtimeMs = json.optLong("runtimeMs", 0L),
                engine = json.optString("engine", "DrFXQuant Quant Coder"),
                error = json.optString("error").takeIf { it.isNotBlank() }
            )
        } catch (throwable: Throwable) {
            QuantRuntimeReport(
                ok = false,
                title = "Custom",
                pineVersion = 0,
                overlay = true,
                isStrategy = false,
                strategyOrders = 0,
                lineCount = 0,
                nodeCount = 0,
                plots = 0,
                shapes = 0,
                candles = 0,
                inputs = emptyList(),
                warnings = emptyList(),
                parseErrors = emptyList(),
                unsupported = emptyList(),
                plotActivity = emptyList(),
                shapeActivity = emptyList(),
                runtimeMs = 0L,
                engine = "DrFXQuant Quant Coder",
                error = throwable.message ?: "QUANT_RUNTIME_PARSE_ERROR"
            )
        }
    }

    private fun parseStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optString(index))
            }
        }
    }

    private fun parseInts(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }
    }

    private fun parseInputs(array: JSONArray?): List<QuantRuntimeInput> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    QuantRuntimeInput(
                        title = item.optString("title", "Input"),
                        defaultValue = if (item.isNull("defaultValue")) {
                            null
                        } else {
                            item.opt("defaultValue")?.toString()
                        }
                    )
                )
            }
        }
    }
}
