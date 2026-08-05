package com.lifeledger.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/*
 * Life Ledger is a private financial memory, not a fintech dashboard — the palette reads
 * like a ledger book rather than a trading app. Light theme is warm paper and ink; dark
 * theme is a deep neutral (never pure black, so cards and shadows still read), and both
 * share a single confident terracotta-ink accent rather than the blue/purple that every
 * other finance app defaults to.
 */

// -- Light: paper & ink -------------------------------------------------------------------

private val PaperBackground = Color(0xFFFBF6EE)
private val PaperSurface = Color(0xFFF7F1E4)
private val PaperSurfaceVariant = Color(0xFFEFE4D0)
private val InkPrimary = Color(0xFF8A4A2A)
private val InkPrimaryContainer = Color(0xFFF6DCC5)
private val InkOnPrimaryContainer = Color(0xFF351704)
private val InkSecondary = Color(0xFF6F6153)
private val InkSecondaryContainer = Color(0xFFEDE1CD)
private val InkOnSecondaryContainer = Color(0xFF261A0C)
private val InkTertiary = Color(0xFF5B6B3C)
private val InkTertiaryContainer = Color(0xFFDCE8B8)
private val InkOnTertiaryContainer = Color(0xFF1A2404)
private val InkOnSurface = Color(0xFF231A11)
private val InkOnSurfaceVariant = Color(0xFF52453A)
private val InkOutline = Color(0xFF847666)
private val InkOutlineVariant = Color(0xFFD6C7B2)

// -- Dark: deep neutral, never pure black -------------------------------------------------

private val NightBackground = Color(0xFF19140F)
private val NightSurface = Color(0xFF211B15)
private val NightSurfaceVariant = Color(0xFF362C22)
private val EmberPrimary = Color(0xFFE3A279)
private val EmberOnPrimary = Color(0xFF4A2410)
private val EmberPrimaryContainer = Color(0xFF663A1D)
private val EmberOnPrimaryContainer = Color(0xFFFFDBC2)
private val EmberSecondary = Color(0xFFD6C3AB)
private val EmberOnSecondary = Color(0xFF3B2E1F)
private val EmberSecondaryContainer = Color(0xFF534333)
private val EmberOnSecondaryContainer = Color(0xFFF2E3CC)
private val EmberTertiary = Color(0xFFC1CE9C)
private val EmberOnTertiary = Color(0xFF2C3712)
private val EmberTertiaryContainer = Color(0xFF424E27)
private val EmberOnTertiaryContainer = Color(0xFFDCEAB4)
private val NightOnSurface = Color(0xFFEDE1D3)
private val NightOnSurfaceVariant = Color(0xFFD1C3B3)
private val NightOutline = Color(0xFF9A8C7C)
private val NightOutlineVariant = Color(0xFF4B3F32)

private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

val LifeLedgerLightColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = Color.White,
    primaryContainer = InkPrimaryContainer,
    onPrimaryContainer = InkOnPrimaryContainer,
    secondary = InkSecondary,
    onSecondary = Color.White,
    secondaryContainer = InkSecondaryContainer,
    onSecondaryContainer = InkOnSecondaryContainer,
    tertiary = InkTertiary,
    onTertiary = Color.White,
    tertiaryContainer = InkTertiaryContainer,
    onTertiaryContainer = InkOnTertiaryContainer,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = PaperBackground,
    onBackground = InkOnSurface,
    surface = PaperBackground,
    onSurface = InkOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = InkOutline,
    outlineVariant = InkOutlineVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F3E7),
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = Color(0xFFF1E9DA),
    surfaceContainerHighest = PaperSurfaceVariant,
)

val LifeLedgerDarkColorScheme = darkColorScheme(
    primary = EmberPrimary,
    onPrimary = EmberOnPrimary,
    primaryContainer = EmberPrimaryContainer,
    onPrimaryContainer = EmberOnPrimaryContainer,
    secondary = EmberSecondary,
    onSecondary = EmberOnSecondary,
    secondaryContainer = EmberSecondaryContainer,
    onSecondaryContainer = EmberOnSecondaryContainer,
    tertiary = EmberTertiary,
    onTertiary = EmberOnTertiary,
    tertiaryContainer = EmberTertiaryContainer,
    onTertiaryContainer = EmberOnTertiaryContainer,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = NightBackground,
    onBackground = NightOnSurface,
    surface = NightBackground,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    surfaceContainerLowest = Color(0xFF130F0B),
    surfaceContainerLow = Color(0xFF211B15),
    surfaceContainer = Color(0xFF261F18),
    surfaceContainerHigh = Color(0xFF302822),
    surfaceContainerHighest = Color(0xFF3C332C),
)

/**
 * Semantic colours the M3 [androidx.compose.material3.ColorScheme] has no slot for: the
 * direction of money, and a 12-way categorical palette for charts. Kept in one class so
 * `LlAmountText`, `LlCategoryIcon` and every chart share exactly the same source of truth.
 */
@Immutable
data class LifeLedgerColors(
    val incomeGreen: Color,
    val onIncomeGreen: Color,
    val expenseRed: Color,
    val onExpenseRed: Color,
    val investmentBlue: Color,
    val onInvestmentBlue: Color,
    val neutralGrey: Color,
    val onNeutralGrey: Color,
    val warning: Color,
    val onWarning: Color,
    /**
     * Twelve hues for category breakdowns and multi-series charts. Chosen from a Tol/Okabe-Ito
     * derived qualitative set so adjacent slices stay distinguishable under the common forms
     * of colour-blindness, then tuned per-theme for contrast against paper vs. night surfaces.
     */
    val categorical: List<Color>,
)

val lifeLedgerLightExtras = LifeLedgerColors(
    incomeGreen = Color(0xFF2E7D46),
    onIncomeGreen = Color.White,
    expenseRed = Color(0xFFB3261E),
    onExpenseRed = Color.White,
    investmentBlue = Color(0xFF375A8C),
    onInvestmentBlue = Color.White,
    neutralGrey = Color(0xFF6F6153),
    onNeutralGrey = Color.White,
    warning = Color(0xFFA5670A),
    onWarning = Color.White,
    categorical = listOf(
        Color(0xFFC1622D), // terracotta
        Color(0xFFCC9A2E), // amber
        Color(0xFF7C8C3D), // olive
        Color(0xFF3D8C63), // forest
        Color(0xFF3D8C8C), // muted teal
        Color(0xFF3D6E9C), // slate blue
        Color(0xFF5B5EA6), // indigo
        Color(0xFF8C4C8C), // plum
        Color(0xFFA85C7C), // rose
        Color(0xFFA63D3D), // rust
        Color(0xFF7A6F62), // warm grey
        Color(0xFF5C4433), // umber
    ),
)

val lifeLedgerDarkExtras = LifeLedgerColors(
    incomeGreen = Color(0xFF8FD99F),
    onIncomeGreen = Color(0xFF073917),
    expenseRed = Color(0xFFF2938C),
    onExpenseRed = Color(0xFF5C0A05),
    investmentBlue = Color(0xFFA6C4EF),
    onInvestmentBlue = Color(0xFF122E4E),
    neutralGrey = Color(0xFFCBBCA9),
    onNeutralGrey = Color(0xFF362C22),
    warning = Color(0xFFF0BB63),
    onWarning = Color(0xFF432D00),
    categorical = listOf(
        Color(0xFFE08B54), // terracotta
        Color(0xFFE0BC5F), // amber
        Color(0xFFA9BC6E), // olive
        Color(0xFF6FBB94), // forest
        Color(0xFF6FBBBB), // muted teal
        Color(0xFF74A0D1), // slate blue
        Color(0xFF9698D4), // indigo
        Color(0xFFC084C0), // plum
        Color(0xFFD08FAC), // rose
        Color(0xFFD07C7C), // rust
        Color(0xFFB4A895), // warm grey
        Color(0xFF9A8368), // umber
    ),
)

/** Deterministic colour for a free-text name — merchant avatars, unmapped tags. */
fun categoricalColorFor(seed: String, palette: List<Color>): Color {
    if (palette.isEmpty()) return Color.Gray
    val index = seed.hashCode().absoluteValue % palette.size
    return palette[index]
}
