package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.R
import com.lifeledger.core.designsystem.theme.LlTheme

/**
 * Full-width loading placeholder for a screen or section that has nothing to show yet.
 * Deliberately plain — a single centred spinner — because Life Ledger's data loads off
 * the on-device SMS store, not a network call, so this state is normally on screen for
 * well under a second and shouldn't compete for attention with a fancier skeleton.
 */
@Composable
fun LlLoading(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.ll_loading_cd)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(LlTheme.spacing.xxl)
            .semantics { contentDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
