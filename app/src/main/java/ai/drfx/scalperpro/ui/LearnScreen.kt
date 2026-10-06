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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LearnScreen(onHome: () -> Unit) {
    val topics = listOf(
        "Forex Basics", "Market Structure", "Technical Analysis", "Price Action",
        "SMC", "ICT", "Risk Management", "Trading Psychology", "Macro",
        "News Trading", "Gold", "Crypto", "Pine Script", "MQL5", "Quantitative Trading"
    )

    Column(Modifier.fillMaxSize()) {
        PageHeader("Traderpedia & Academy", onHome)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(topics) { topic ->
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        topic,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
