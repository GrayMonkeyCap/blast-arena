package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.designsystem.theme.categoricalColorFor

/**
 * A colour + initials avatar for a merchant with no logo (which, offline, is every
 * merchant). The colour is a pure function of [name] so the same merchant always renders
 * the same avatar across the app without persisting anything.
 */
@Composable
fun LlMerchantAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val background = categoricalColorFor(name, LlTheme.colors.categorical)
    val initials = initialsOf(name)
    Box(
        modifier = modifier
            .size(size)
            .background(color = background, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** First letter of up to the first two words — "Amazon Pay" -> "AP", "Swiggy" -> "S". */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
