package ai.drfx.scalperpro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object PremiumColors {
    val Background = Color(0xFF03050B)
    val BackgroundSoft = Color(0xFF080B14)
    val Surface = Color(0xE6111622)
    val SurfaceStrong = Color(0xF2161C2A)
    val Border = Color(0xFF29324A)
    val Purple = Color(0xFF9B5CFF)
    val PurpleBright = Color(0xFFB967FF)
    val Cyan = Color(0xFF22D3EE)
    val CyanSoft = Color(0xFF69E8FF)
    val Teal = Color(0xFF24E2C2)
    val Gold = Color(0xFFF3C96B)
    val Red = Color(0xFFFF5C7A)
    val Green = Color(0xFF2CE09B)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xFFB4BCD2)
    val TextMuted = Color(0xFF7E88A5)
}

internal val PremiumScreenBrush =
    Brush.verticalGradient(
        listOf(
            Color(0xFF02040A),
            Color(0xFF050713),
            Color(0xFF02040A)
        )
    )

@Composable
internal fun PremiumCard(
    modifier: Modifier = Modifier,
    accent: Color = PremiumColors.Purple,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.72f),
                            PremiumColors.Border,
                            PremiumColors.Cyan.copy(alpha = 0.34f)
                        )
                    )
                ),
                shape
            ),
        color = PremiumColors.Surface,
        contentColor = PremiumColors.TextPrimary,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content
    )
}

@Composable
internal fun PremiumSectionTitle(
    title: String,
    subtitle: String? = null,
    accent: Color = PremiumColors.Cyan
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.TextPrimary
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = accent
            )
        }
    }
}

@Composable
internal fun PremiumTag(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = PremiumColors.Purple,
    selected: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    accent.copy(alpha = 0.22f)
                } else {
                    Color(0xFF0B0F19)
                }
            )
            .border(
                1.dp,
                if (selected) {
                    accent.copy(alpha = 0.85f)
                } else {
                    PremiumColors.Border
                },
                shape
            )
            .padding(
                horizontal = 13.dp,
                vertical = 8.dp
            )
    ) {
        Text(
            text,
            color =
                if (selected) {
                    PremiumColors.TextPrimary
                } else {
                    PremiumColors.TextSecondary
                },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun PremiumStatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                accent.copy(alpha = 0.12f)
            )
            .border(
                1.dp,
                accent.copy(alpha = 0.42f),
                shape
            )
            .padding(
                horizontal = 12.dp,
                vertical = 7.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(7.dp)
    ) {
        Text(
            "●",
            color = accent,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text,
            color = PremiumColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun PremiumMetricRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.padding(
            horizontal = 2.dp,
            vertical = 2.dp
        ),
        horizontalArrangement =
            Arrangement.spacedBy(10.dp),
        content = content
    )
}
