package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.scalperpro.news.NewsDataResult
import ai.drfx.scalperpro.news.NewsRepository
import ai.drfx.scalperpro.news.UnconfiguredNewsDataProvider

private data class NewsUiState(
    val newsMessage: String,
    val calendarMessage: String
)

@Composable
internal fun NewsScreen(onHome: () -> Unit) {
    val repository = remember { NewsRepository(UnconfiguredNewsDataProvider()) }

    val state by produceState(
        initialValue = NewsUiState(
            newsMessage = "Loading news provider…",
            calendarMessage = "Loading calendar provider…"
        ),
        key1 = repository
    ) {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        val news = repository.latestNews()
        val calendar = repository.calendar(now - day, now + 7L * day)

        value = NewsUiState(
            newsMessage = when (news) {
                is NewsDataResult.Success -> news.value.size.toString() + " items loaded from " + news.providerId
                is NewsDataResult.Unavailable -> news.reason
                is NewsDataResult.Failure -> news.code + ": " + news.message
            },
            calendarMessage = when (calendar) {
                is NewsDataResult.Success -> calendar.value.size.toString() + " events loaded from " + calendar.providerId
                is NewsDataResult.Unavailable -> calendar.reason
                is NewsDataResult.Failure -> calendar.code + ": " + calendar.message
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("News Intelligence", onHome)

        Card(Modifier.padding(16.dp)) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("News Feed", fontWeight = FontWeight.Bold)
                Text(state.newsMessage)
                Text(
                    "Central Banks • Inflation • Employment • GDP • Geopolitics • Commodities • Forex • Crypto • Stocks • Breaking",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Card(Modifier.padding(horizontal = 16.dp)) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Economic Calendar", fontWeight = FontWeight.Bold)
                Text(state.calendarMessage)
                Text(
                    "FACT and AI ASSESSMENT remain separate when a provider is connected.",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
