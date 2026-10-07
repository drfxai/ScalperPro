package ai.drfx.scalperpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.news.NewsDataResult
import ai.drfx.scalperpro.news.NewsRepository
import ai.drfx.scalperpro.news.UnconfiguredNewsDataProvider

private data class NewsUiState(
    val newsMessage: String,
    val calendarMessage: String,
    val providerReady: Boolean
)

@Composable
internal fun NewsScreen(
    onHome: () -> Unit
) {
    val repository =
        remember {
            NewsRepository(
                UnconfiguredNewsDataProvider()
            )
        }

    var selectedFilter by remember {
        mutableStateOf("Forex")
    }

    var state by remember(repository) {
        mutableStateOf(
            NewsUiState(
                newsMessage =
                    "Checking news provider…",
                calendarMessage =
                    "Checking calendar provider…",
                providerReady = false
            )
        )
    }

    LaunchedEffect(repository) {
        val now =
            System.currentTimeMillis()
        val day =
            24L * 60L * 60L * 1000L

        val newsResult =
            repository.latestNews()
        val calendarResult =
            repository.calendar(
                now - day,
                now + 7L * day
            )

        val newsReady =
            newsResult is
                NewsDataResult.Success
        val calendarReady =
            calendarResult is
                NewsDataResult.Success

        state =
            NewsUiState(
                newsMessage =
                    when (newsResult) {
                        is NewsDataResult
                            .Success ->
                            newsResult.value
                                .size
                                .toString() +
                                " live news items loaded"
                        is NewsDataResult
                            .Unavailable ->
                            newsResult.reason
                        is NewsDataResult
                            .Failure ->
                            newsResult.code +
                                ": " +
                                newsResult.message
                    },
                calendarMessage =
                    when (calendarResult) {
                        is NewsDataResult
                            .Success ->
                            calendarResult.value
                                .size
                                .toString() +
                                " calendar events loaded"
                        is NewsDataResult
                            .Unavailable ->
                            calendarResult.reason
                        is NewsDataResult
                            .Failure ->
                            calendarResult.code +
                                ": " +
                                calendarResult.message
                    },
                providerReady =
                    newsReady &&
                        calendarReady
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                PremiumScreenBrush
            )
    ) {
        PageHeader(
            title =
                "News Intelligence",
            subtitle =
                "Fact, context, and AI explanation",
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
                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    items(
                        listOf(
                            "Forex",
                            "Gold",
                            "Crypto",
                            "Macro",
                            "Central Banks",
                            "High Impact"
                        )
                    ) {
                        label ->
                        PremiumTag(
                            text = label,
                            accent =
                                when (label) {
                                    "Gold" ->
                                        PremiumColors.Gold
                                    "Crypto" ->
                                        PremiumColors.Purple
                                    "High Impact" ->
                                        PremiumColors.Red
                                    else ->
                                        PremiumColors.Cyan
                                },
                            selected =
                                selectedFilter ==
                                    label,
                            modifier =
                                Modifier.clickable {
                                    selectedFilter =
                                        label
                                }
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
                                16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                11.dp
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
                                        10.dp
                                    ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Surface(
                                    color =
                                        PremiumColors
                                            .Purple
                                            .copy(
                                                alpha = 0.18f
                                            ),
                                    shape =
                                        CircleShape
                                ) {
                                    Icon(
                                        Icons.Rounded
                                            .CalendarMonth,
                                        contentDescription =
                                            null,
                                        tint =
                                            PremiumColors
                                                .PurpleBright,
                                        modifier =
                                            Modifier.padding(
                                                10.dp
                                            )
                                    )
                                }
                                Column {
                                    Text(
                                        "Today’s Events",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleMedium,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                    Text(
                                        "Key economic events that can move the markets",
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
                            Text(
                                if (
                                    state
                                        .providerReady
                                ) {
                                    "LIVE"
                                } else {
                                    "PROVIDER REQUIRED"
                                },
                                color =
                                    if (
                                        state
                                            .providerReady
                                    ) {
                                        PremiumColors
                                            .Green
                                    } else {
                                        PremiumColors
                                            .Gold
                                    },
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Text(
                            state.calendarMessage,
                            color =
                                PremiumColors
                                    .TextSecondary
                        )

                        if (
                            !state.providerReady
                        ) {
                            Text(
                                "Calendar rows will appear here only after a trusted data provider is configured. Scalper Pro will not invent event times, impact levels, or values.",
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

            item {
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        PremiumColors.Cyan
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            )
                    ) {
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Article,
                                contentDescription =
                                    null,
                                tint =
                                    PremiumColors
                                        .Cyan
                            )
                            Column {
                                Text(
                                    "Featured Intelligence",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                                Text(
                                    selectedFilter +
                                        " · structured news analysis",
                                    color =
                                        PremiumColors
                                            .TextMuted
                                )
                            }
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                )
                        ) {
                            PremiumCard(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),
                                accent =
                                    PremiumColors
                                        .Cyan
                            ) {
                                Column(
                                    modifier =
                                        Modifier.padding(
                                            12.dp
                                        ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            6.dp
                                        )
                                ) {
                                    Text(
                                        "FACT",
                                        color =
                                            PremiumColors
                                                .Cyan,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                    Text(
                                        if (
                                            state
                                                .providerReady
                                        ) {
                                            "Provider facts are normalized here before AI interpretation."
                                        } else {
                                            "Waiting for a verified news provider. No factual claims are fabricated."
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
                            }

                            PremiumCard(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),
                                accent =
                                    PremiumColors
                                        .Purple
                            ) {
                                Column(
                                    modifier =
                                        Modifier.padding(
                                            12.dp
                                        ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            6.dp
                                        )
                                ) {
                                    Text(
                                        "AI ANALYSIS",
                                        color =
                                            PremiumColors
                                                .PurpleBright,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                    Text(
                                        if (
                                            state
                                                .providerReady
                                        ) {
                                            "AI context, implications, uncertainty and beginner explanation appear separately from facts."
                                        } else {
                                            "Analysis stays disabled until trusted facts are available."
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
                            }
                        }

                        PremiumCard(
                            modifier =
                                Modifier.fillMaxWidth(),
                            accent =
                                PremiumColors.Gold
                        ) {
                            Row(
                                modifier =
                                    Modifier.padding(
                                        12.dp
                                    ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        10.dp
                                    ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded
                                        .TrendingUp,
                                    contentDescription =
                                        null,
                                    tint =
                                        PremiumColors
                                            .Gold
                                )
                                Column {
                                    Text(
                                        "Why it matters",
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                    Text(
                                        "When live news is connected, this section translates macro impact into plain-language trading context without presenting it as guaranteed direction.",
                                        color =
                                            PremiumColors
                                                .TextSecondary,
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

            item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        "Latest News",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription =
                                "Search",
                            tint =
                                PremiumColors
                                    .TextMuted
                        )
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription =
                                "Sources",
                            tint =
                                PremiumColors
                                    .TextMuted
                        )
                    }
                }
            }

            items(
                listOf(
                    "Market-moving headlines",
                    "Central-bank updates",
                    "Gold / FX / Crypto context"
                )
            ) {
                label ->
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        PremiumColors
                            .Border
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    3.dp
                                )
                        ) {
                            Text(
                                label,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                            Text(
                                if (
                                    state
                                        .providerReady
                                ) {
                                    state.newsMessage
                                } else {
                                    "Waiting for provider · no fabricated headline"
                                },
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
                            "—",
                            color =
                                PremiumColors
                                    .TextMuted,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Text(
                    state.newsMessage,
                    color =
                        PremiumColors
                            .TextMuted,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    modifier =
                        Modifier.padding(
                            bottom = 12.dp
                        )
                )
            }
        }
    }
}
