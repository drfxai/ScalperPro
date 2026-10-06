package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.ai.AiChatExecutionResult
import ai.drfx.scalperpro.ai.AiRoutingMode
import ai.drfx.scalperpro.ai.ScalperAiGatewayClient
import ai.drfx.scalperpro.ai.ScalperAiGatewayConfig
import kotlinx.coroutines.launch

private data class ChatBubble(
    val role: String,
    val text: String,
    val meta: String? = null
)

@Composable
internal fun AiScreen(
    onHome: () -> Unit
) {
    val client =
        remember {
            ScalperAiGatewayClient()
        }
    val scope = rememberCoroutineScope()

    var message by remember {
        mutableStateOf("")
    }
    var routingMode by remember {
        mutableStateOf(
            AiRoutingMode.GEMINI_DIRECT
        )
    }
    var running by remember {
        mutableStateOf(false)
    }

    val conversation =
        remember {
            mutableStateListOf<ChatBubble>()
        }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Scalper AI",
            onHome
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "AI Trading Education Assistant",
                            fontWeight =
                                FontWeight.Bold
                        )
                        Text(
                            "Ask about Pine Script, indicator design, strategy planning, MQL5, chart logic or trading concepts.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .secondary
                        )
                        Text(
                            if (
                                ScalperAiGatewayConfig
                                    .configured
                            ) {
                                "Trusted Gateway: CONFIGURED"
                            } else {
                                "Trusted Gateway: NOT CONFIGURED"
                            },
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .tertiary
                        )
                        Text(
                            "Provider secrets remain on the trusted backend and are never stored in the APK."
                        )
                    }
                }
            }

            item {
                Text(
                    "AI Route",
                    fontWeight =
                        FontWeight.Bold
                )
                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        AiRoutingMode.entries
                    ) { mode ->
                        AssistChip(
                            onClick = {
                                routingMode = mode
                            },
                            label = {
                                Text(
                                    when (mode) {
                                        AiRoutingMode
                                            .GEMINI_DIRECT ->
                                            "Gemini"
                                        AiRoutingMode
                                            .NINE_ROUTER_SMART ->
                                            "9Router Smart"
                                        AiRoutingMode
                                            .NINE_ROUTER_COMBO ->
                                            "9Router Combo"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            if (conversation.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(
                                16.dp
                            ),
                            verticalArrangement =
                                Arrangement
                                    .spacedBy(
                                        6.dp
                                    )
                        ) {
                            Text(
                                "Try asking",
                                fontWeight =
                                    FontWeight.Bold
                            )
                            Text(
                                "• Explain repainting in Pine Script."
                            )
                            Text(
                                "• Design a beginner XAUUSD indicator."
                            )
                            Text(
                                "• Convert this strategy idea into clear rules."
                            )
                            Text(
                                "• Explain how an EA differs from a TradingView strategy."
                            )
                        }
                    }
                }
            }

            items(
                items = conversation
            ) { bubble ->
                Card(
                    Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(4.dp)
                    ) {
                        Text(
                            bubble.role,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                if (
                                    bubble.role ==
                                        "Scalper AI"
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .secondary
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                }
                        )
                        Text(bubble.text)

                        bubble.meta?.let {
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

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = {
                    message = it
                },
                placeholder = {
                    Text(
                        "Ask Scalper AI..."
                    )
                },
                modifier =
                    Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled =
                        !running &&
                            message.isNotBlank(),
                    onClick = {
                        val prompt =
                            message.trim()

                        if (
                            prompt.isEmpty()
                        ) {
                            return@Button
                        }

                        conversation +=
                            ChatBubble(
                                role = "You",
                                text = prompt
                            )

                        message = ""
                        running = true

                        scope.launch {
                            val result =
                                client.chat(
                                    routingMode =
                                        routingMode,
                                    message = prompt
                                )

                            when (result) {
                                is AiChatExecutionResult
                                    .Success -> {
                                    conversation +=
                                        ChatBubble(
                                            role =
                                                "Scalper AI",
                                            text =
                                                result
                                                    .reply
                                                    .text,
                                            meta =
                                                result
                                                    .reply
                                                    .provider +
                                                    " • " +
                                                    result
                                                        .reply
                                                        .model
                                        )
                                }

                                is AiChatExecutionResult
                                    .Unavailable -> {
                                    conversation +=
                                        ChatBubble(
                                            role =
                                                "Scalper AI",
                                            text =
                                                result
                                                    .reason,
                                            meta =
                                                "Gateway unavailable"
                                        )
                                }

                                is AiChatExecutionResult
                                    .Failure -> {
                                    conversation +=
                                        ChatBubble(
                                            role =
                                                "Scalper AI",
                                            text =
                                                result
                                                    .message,
                                            meta =
                                                result
                                                    .code
                                        )
                                }
                            }

                            running = false
                        }
                    }
                ) {
                    Text(
                        if (running) {
                            "Thinking..."
                        } else {
                            "Send"
                        }
                    )
                }

                Text(
                    routingMode.name,
                    modifier =
                        Modifier.padding(
                            top = 12.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .tertiary
                )
            }

            Text(
                "Educational analysis only. Scalper AI does not promise profitable outcomes.",
                color =
                    MaterialTheme
                        .colorScheme
                        .tertiary
            )
        }
    }
}
