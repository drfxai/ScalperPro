package ai.drfx.scalperpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var state by remember(repository) {
        mutableStateOf(
            NewsUiState(
                newsMessage = "Loading news provider…",
                calendarMessage = "Loading calendar provider…"
            )
        )
    }

    LaunchedEffect(repository) {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        val newsResult = repository.latestNews()
        val calendarResult = repository.calendar(now - day, now + 7L * day)

        state = NewsUiState(
            newsMessage = when (newsResult) {
                is NewsDataResult.Success -> newsResult.value.size.toString() + " items loaded from " + newsResult.providerId
                is NewsDataResult.Unavailable -> newsResult.reason
                is NewsDataResult.Failure -> newsResult.code + ": " + newsResult.message
            },
            calendarMessage = when (calendarResult) {
                is NewsDataResult.Success -> calendarResult.value.size.toString() + " events loaded from " + calendarResult.providerId
                is NewsDataResult.Unavailable -> calendarResult.reason
                is NewsDataResult.Failure -> calendarResult.code + ": " + calendarResult.message
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
