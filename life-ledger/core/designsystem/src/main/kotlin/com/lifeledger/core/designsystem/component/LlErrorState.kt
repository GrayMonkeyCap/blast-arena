package com.lifeledger.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lifeledger.core.designsystem.R
import com.lifeledger.core.designsystem.icon.LlIcons

/**
 * Failure counterpart to [LlEmptyState] — a parse crash, a corrupt export, a permission
 * revoked mid-session. [onRetry] is optional because some failures (e.g. permission denied)
 * need the user to leave the screen rather than retry in place.
 */
@Composable
fun LlErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    LlEmptyState(
        icon = LlIcons.Error,
        title = title,
        message = message,
        modifier = modifier,
        action = onRetry?.let { retry -> LlStateAction(stringResource(R.string.ll_action_retry), retry) },
    )
}
