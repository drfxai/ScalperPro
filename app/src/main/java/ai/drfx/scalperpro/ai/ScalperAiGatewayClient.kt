package ai.drfx.scalperpro.ai

import ai.drfx.scalperpro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class AiLabStageResult(
    val role: String,
    val title: String,
    val provider: String,
    val model: String,
    val text: String
)

data class AiLabWorkflowResult(
    val taskType: String,
    val stages: List<AiLabStageResult>,
    val finalText: String
)

data class AiChatReply(
    val provider: String,
    val model: String,
    val text: String
)

sealed interface AiChatExecutionResult {
    data class Success(
        val reply: AiChatReply
    ) : AiChatExecutionResult

    data class Unavailable(
        val reason: String
    ) : AiChatExecutionResult

    data class Failure(
        val code: String,
        val message: String
    ) : AiChatExecutionResult
}

sealed interface AiLabExecutionResult {
    data class Success(
        val workflow: AiLabWorkflowResult
    ) : AiLabExecutionResult

    data class Unavailable(
        val reason: String
    ) : AiLabExecutionResult

    data class Failure(
        val code: String,
        val message: String
    ) : AiLabExecutionResult
}

object ScalperAiGatewayConfig {
    val baseUrl: String
        get() =
            BuildConfig.SCALPER_AI_GATEWAY_BASE_URL
                .trim()
                .trimEnd('/')

    val configured: Boolean
        get() = baseUrl.startsWith("https://")
}

class ScalperAiGatewayClient(
    private val baseUrl: String =
        ScalperAiGatewayConfig.baseUrl
) {
    suspend fun runLabWorkflow(
        taskType: LabTaskType,
        routingMode: AiRoutingMode,
        message: String,
        context: String = ""
    ): AiLabExecutionResult =
        withContext(Dispatchers.IO) {
            val requestId = UUID.randomUUID().toString()
            if (!baseUrl.startsWith("https://")) {
                return@withContext AiLabExecutionResult.Unavailable(
                    "Trusted Scalper AI Gateway is not configured in this build."
                )
            }

            if (message.isBlank()) {
                return@withContext AiLabExecutionResult.Failure(
                    code = "EMPTY_MESSAGE",
                    message = "Describe what you want the AI team to build."
                )
            }

            val payload = JSONObject()
                .put(
                    "taskType",
                    taskType.toGatewayValue()
                )
                .put(
                    "mode",
                    routingMode.toGatewayValue()
                )
                .put(
                    "message",
                    message.take(MAX_MESSAGE_CHARS)
                )
                .put(
                    "context",
                    context.take(MAX_CONTEXT_CHARS)
                )

            val connection = try {
                (
                    URL(
                        baseUrl + "/v1/ai/lab"
                    ).openConnection() as
                        HttpURLConnection
                    ).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty(
                        "content-type",
                        "application/json; charset=utf-8"
                    )
                    setRequestProperty(
                        "accept",
                        "application/json"
                    )
                    setRequestProperty(
                        "x-request-id",
                        requestId
                    )
                }
            } catch (throwable: Throwable) {
                return@withContext AiLabExecutionResult.Failure(
                    code = "GATEWAY_CONNECTION_INIT",
                    message =
                        throwable.message ?:
                            "Unable to prepare AI Gateway connection."
                )
            }

            try {
                connection.outputStream.use { output ->
                    output.write(
                        payload
                            .toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )
                }

                val status = connection.responseCode
                val reader =
                    if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }?.bufferedReader(
                        Charsets.UTF_8
                    )

                val body = reader
                    ?.use {
                        readLimited(
                            it,
                            MAX_RESPONSE_CHARS
                        )
                    }
                    .orEmpty()

                if (status !in 200..299) {
                    val errorJson =
                        runCatching {
                            JSONObject(body)
                        }.getOrNull()

                    return@withContext AiLabExecutionResult.Failure(
                        code =
                            errorJson
                                ?.optString("code")
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "HTTP_$status",
                        message =
                            errorJson
                                ?.optString("error")
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "Scalper AI Gateway returned HTTP $status."
                    )
                }

                return@withContext parseWorkflow(
                    body
                )
            } catch (throwable: Throwable) {
                return@withContext AiLabExecutionResult.Failure(
                    code = "GATEWAY_REQUEST_FAILED",
                    message =
                        throwable.message ?:
                            "AI Gateway request failed."
                )
            } finally {
                connection.disconnect()
            }
        }

    suspend fun chat(
        routingMode: AiRoutingMode,
        message: String
    ): AiChatExecutionResult =
        withContext(Dispatchers.IO) {
            val requestId = UUID.randomUUID().toString()
            if (!baseUrl.startsWith("https://")) {
                return@withContext AiChatExecutionResult.Unavailable(
                    "Trusted Scalper AI Gateway is not configured in this build."
                )
            }

            if (message.isBlank()) {
                return@withContext AiChatExecutionResult.Failure(
                    code = "EMPTY_MESSAGE",
                    message = "Enter a question for Scalper AI."
                )
            }

            val payload = JSONObject()
                .put(
                    "mode",
                    routingMode.toGatewayValue()
                )
                .put(
                    "message",
                    message.take(MAX_MESSAGE_CHARS)
                )

            val connection = try {
                (
                    URL(
                        baseUrl + "/v1/ai/chat"
                    ).openConnection() as
                        HttpURLConnection
                    ).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 90_000
                    doOutput = true
                    setRequestProperty(
                        "content-type",
                        "application/json; charset=utf-8"
                    )
                    setRequestProperty(
                        "accept",
                        "application/json"
                    )
                    setRequestProperty(
                        "x-request-id",
                        requestId
                    )
                }
            } catch (throwable: Throwable) {
                return@withContext AiChatExecutionResult.Failure(
                    code = "GATEWAY_CONNECTION_INIT",
                    message =
                        throwable.message ?:
                            "Unable to prepare AI Gateway connection."
                )
            }

            try {
                connection.outputStream.use { output ->
                    output.write(
                        payload
                            .toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )
                }

                val status = connection.responseCode
                val reader =
                    if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }?.bufferedReader(
                        Charsets.UTF_8
                    )

                val body = reader
                    ?.use {
                        readLimited(
                            it,
                            MAX_RESPONSE_CHARS
                        )
                    }
                    .orEmpty()

                if (status !in 200..299) {
                    val errorJson =
                        runCatching {
                            JSONObject(body)
                        }.getOrNull()

                    return@withContext AiChatExecutionResult.Failure(
                        code =
                            errorJson
                                ?.optString("code")
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "HTTP_$status",
                        message =
                            errorJson
                                ?.optString("error")
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "Scalper AI Gateway returned HTTP $status."
                    )
                }

                val json = JSONObject(body)
                return@withContext AiChatExecutionResult.Success(
                    AiChatReply(
                        provider =
                            json.optString(
                                "provider"
                            ),
                        model =
                            json.optString(
                                "model"
                            ),
                        text =
                            json.optString(
                                "text"
                            )
                    )
                )
            } catch (throwable: Throwable) {
                return@withContext AiChatExecutionResult.Failure(
                    code = "GATEWAY_REQUEST_FAILED",
                    message =
                        throwable.message ?:
                            "AI Gateway request failed."
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun parseWorkflow(
        body: String
    ): AiLabExecutionResult {
        return try {
            val root = JSONObject(body)
            val stagesJson =
                root.optJSONArray("stages")

            val stages = buildList {
                if (stagesJson != null) {
                    for (
                        index in
                        0 until stagesJson.length()
                    ) {
                        val item =
                            stagesJson
                                .optJSONObject(
                                    index
                                ) ?:
                                continue

                        add(
                            AiLabStageResult(
                                role =
                                    item.optString(
                                        "role"
                                    ),
                                title =
                                    item.optString(
                                        "title"
                                    ),
                                provider =
                                    item.optString(
                                        "provider"
                                    ),
                                model =
                                    item.optString(
                                        "model"
                                    ),
                                text =
                                    item.optString(
                                        "text"
                                    )
                            )
                        )
                    }
                }
            }

            if (stages.isEmpty()) {
                AiLabExecutionResult.Failure(
                    code =
                        "GATEWAY_EMPTY_WORKFLOW",
                    message =
                        "AI Gateway returned no specialist stages."
                )
            } else {
                AiLabExecutionResult.Success(
                    AiLabWorkflowResult(
                        taskType =
                            root.optString(
                                "taskType"
                            ),
                        stages = stages,
                        finalText =
                            root.optString(
                                "final"
                            )
                    )
                )
            }
        } catch (throwable: Throwable) {
            AiLabExecutionResult.Failure(
                code =
                    "GATEWAY_RESPONSE_PARSE",
                message =
                    throwable.message ?:
                        "Unable to parse AI Gateway response."
            )
        }
    }

    private fun readLimited(
        reader: BufferedReader,
        maxChars: Int
    ): String {
        val output = StringBuilder()
        val buffer = CharArray(8_192)

        while (output.length < maxChars) {
            val remaining =
                maxChars - output.length

            val count = reader.read(
                buffer,
                0,
                minOf(
                    buffer.size,
                    remaining
                )
            )

            if (count <= 0) break
            output.append(
                buffer,
                0,
                count
            )
        }

        return output.toString()
    }

    private fun LabTaskType.toGatewayValue(): String =
        name.lowercase()

    private fun AiRoutingMode.toGatewayValue(): String =
        when (this) {
            AiRoutingMode.GEMINI_DIRECT ->
                "gemini"
            AiRoutingMode.NINE_ROUTER_SMART ->
                "9router-smart"
            AiRoutingMode.NINE_ROUTER_COMBO ->
                "9router-combo"
        }

    private companion object {
        const val MAX_MESSAGE_CHARS =
            30_000
        const val MAX_CONTEXT_CHARS =
            6_000
        const val MAX_RESPONSE_CHARS =
            2_000_000
    }
}
