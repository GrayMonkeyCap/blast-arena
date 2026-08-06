package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Every refreshable list in the app (transactions, timeline, insights) wraps its content in
 * this rather than calling M3's `PullToRefreshBox` directly, so the indicator styling stays
 * uniform and call sites only need to know `isRefreshing` / `onRefresh`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}
