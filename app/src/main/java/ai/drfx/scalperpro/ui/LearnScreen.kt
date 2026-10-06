package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Column(Modifier.fillMaxSize()) {
        PageHeader("Traderpedia & Academy", onHome)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Connected learning", fontWeight = FontWeight.Bold)
                        Text(
                            "Lessons can open the corresponding Strategy, Risk, Pine, MQL5, Backtest, News, Markets or Journal workflow.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Catalog coverage: " +
                                LearningCategory.entries.joinToString(" • ") { it.name.replace('_', ' ') }
                        )
                    }
                }
            }

            items(
                items = LearningCatalog.articles,
                key = { article -> article.id }
            ) { article ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(article.title, fontWeight = FontWeight.Bold)
                        Text(
                            article.category.name.replace('_', ' '),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(article.summary)

                        article.toolDeepLink?.let { deepLink ->
                            Button(
                                onClick = { onOpenTool(deepLink) }
                            ) {
                                Text("Open " + deepLink.name.replace('_', ' '))
                            }
                        }
                    }
                }
            }
        }
    }
}
