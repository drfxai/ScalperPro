package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.learning.LearningCatalog
import ai.drfx.scalperpro.search.LocalSearchIndex
import ai.drfx.scalperpro.search.SearchCategory
import ai.drfx.scalperpro.search.SearchDocument

@Composable
internal fun SearchScreen(
    onHome: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val index = remember {
        val learningDocuments = LearningCatalog.articles.map { article ->
            SearchDocument(
                id = "learn:" + article.id,
                title = article.title,
                body = article.summary,
                category = SearchCategory.TRADERPEDIA,
                source = "Scalper Academy",
                destination = "Learn"
            )
        }

        val moduleDocuments = listOf(
            SearchDocument(
                id = "module:markets",
                title = "Markets",
                body = "Forex Gold Crypto Indices watchlist market intelligence",
                category = SearchCategory.MODULE,
                source = "Scalper Pro",
                destination = "Markets"
            ),
            SearchDocument(
                id = "module:signals",
                title = "Live Signals",
                body = "Auditable signal lifecycle performance risk alerts",
                category = SearchCategory.MODULE,
                source = "Scalper Pro",
                destination = "Signals"
            ),
            SearchDocument(
                id = "module:chart-vision",
                title = "AI Chart Vision",
                body = "Chart screenshot multimodal analysis market structure liquidity news context",
                category = SearchCategory.MODULE,
                source = "Scalper Pro",
                destination = "ChartVision"
            ),
            SearchDocument(
                id = "module:strategy",
                title = "Strategy Lab",
                body = "Strategy Specification Pine MQL5 backtest",
                category = SearchCategory.MODULE,
                source = "Scalper Pro",
                destination = "Lab"
            ),
            SearchDocument(
                id = "module:risk",
                title = "Risk Manager",
                body = "Position size risk reward open risk exposure",
                category = SearchCategory.MODULE,
                source = "Scalper Pro",
                destination = "Risk"
            ),
            SearchDocument(
                id = "module:journal",
                title = "Trader Journal",
                body = "Trade review statistics psychology session performance",
                category = SearchCategory.MODULE,
                source = "Scalper Pro",
                destination = "Journal"
            )
        )

        LocalSearchIndex(learningDocuments + moduleDocuments)
    }

    val results = remember(query) {
        index.search(query)
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Global Search", onHome)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = { Text("Search Scalper Pro") },
            singleLine = true
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (query.isBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "Search across Traderpedia and currently indexed Scalper Pro modules. Provider-backed news, signals, journal persistence and strategy storage will join this index as those repositories are connected.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            items(
                items = results,
                key = { result -> result.document.id }
            ) { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            result.document.title,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            result.document.category.name,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(result.document.body)
                        Text(
                            "Source: " + result.document.source,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}
