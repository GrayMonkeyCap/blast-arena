package com.lifeledger.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Composition-local access to the semantic colours [LifeLedgerColors] adds on top of M3. */
val LocalLifeLedgerColors = staticCompositionLocalOf { lifeLedgerLightExtras }

/**
 * Root theme for every Life Ledger screen.
 *
 * Dynamic colour (Android 12+ wallpaper extraction) is opt-in and defaults to *on* per
 * platform convention, but the hand-built [LifeLedgerLightColorScheme] /
 * [LifeLedgerDarkColorScheme] — and their matching [LifeLedgerColors] extras — are the
 * fallback and the only palette below API 31, so the app still looks deliberate rather
 * than generic on most devices in the field.
 */
@Composable
fun LifeLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> LifeLedgerDarkColorScheme
        else -> LifeLedgerLightColorScheme
    }

    // Dynamic colour replaces the M3 slots but has no opinion on income/expense/investment —
    // those semantic extras always come from our own palette so a wallpaper-tinted phone
    // never turns "money out" the same colour as "money in".
    val extras = if (darkTheme) lifeLedgerDarkExtras else lifeLedgerLightExtras

    CompositionLocalProvider(
        LocalLifeLedgerColors provides extras,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LifeLedgerTypography,
            shapes = LifeLedgerShapes,
            content = content,
        )
    }
}

/**
 * Shorthand so components read `LlTheme.colors.incomeGreen` / `LlTheme.spacing.md` instead
 * of spelling out `LocalLifeLedgerColors.current` and `LocalSpacing.current` everywhere,
 * mirroring how `MaterialTheme.colorScheme` already reads.
 */
object LlTheme {
    val colors: LifeLedgerColors
        @Composable get() = LocalLifeLedgerColors.current

    val spacing: Spacing
        @Composable get() = LocalSpacing.current
}
