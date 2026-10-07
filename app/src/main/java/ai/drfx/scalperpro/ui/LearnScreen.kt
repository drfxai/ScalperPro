package ai.drfx.scalperpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.learning.LearningCatalog
import ai.drfx.scalperpro.learning.LearningCategory
import ai.drfx.scalperpro.learning.ToolDeepLink

@Composable
internal fun LearnScreen(
    onHome: () -> Unit,
    onOpenTool: (ToolDeepLink) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                PremiumScreenBrush
            )
    ) {
        PageHeader(
            title =
                "Traderpedia & Academy",
            subtitle =
                "Connected learning · open the tool you are studying",
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
                        PremiumColors.Cyan
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    16.dp
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            ),
                        verticalAlignment =
                            Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.School,
                            contentDescription =
                                null,
                            tint =
                                PremiumColors.Cyan
                        )
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    7.dp
                                )
                        ) {
                            Text(
                                "Connected learning",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge,
                                fontWeight =
                                    FontWeight.Bold
                            )
                            Text(
                                "Lessons can open the matching Strategy, Risk, Pine, MQL5, Backtest, News, Market or Journal workflow.",
                                color =
                                    PremiumColors.Cyan
                            )
                            Text(
                                LearningCategory
                                    .entries
                                    .joinToString(
                                        " · "
                                    ) {
                                        it.name
                                            .replace(
                                                '_',
                                                ' '
                                            )
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
            }

            items(
                items =
                    LearningCatalog
                        .articles,
                key = {
                    article ->
                    article.id
                }
            ) {
                article ->
                PremiumCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                    accent =
                        when (
                            article.category
                        ) {
                            LearningCategory
                                .PINE_SCRIPT ->
                                PremiumColors
                                    .Purple
                            LearningCategory
                                .MQL5 ->
                                PremiumColors
                                    .Gold
                            LearningCategory
                                .RISK ->
                                PremiumColors
                                    .Cyan
                            else ->
                                PremiumColors
                                    .Teal
                        }
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
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
                                    .MenuBook,
                                contentDescription =
                                    null,
                                tint =
                                    PremiumColors
                                        .Cyan
                            )
                            Column {
                                Text(
                                    article.title,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                                Text(
                                    article.category
                                        .name
                                        .replace(
                                            '_',
                                            ' '
                                        ),
                                    color =
                                        PremiumColors.Cyan,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelMedium
                                )
                            }
                        }

                        Text(
                            article.summary,
                            color =
                                PremiumColors
                                    .TextSecondary
                        )

                        article.toolDeepLink
                            ?.let {
                                deepLink ->
                                Button(
                                    onClick = {
                                        onOpenTool(
                                            deepLink
                                        )
                                    },
                                    colors =
                                        ButtonDefaults
                                            .buttonColors(
                                                containerColor =
                                                    PremiumColors
                                                        .Purple
                                            )
                                ) {
                                    Text(
                                        "Open " +
                                            deepLink
                                                .name
                                                .replace(
                                                    '_',
                                                    ' '
                                                )
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
