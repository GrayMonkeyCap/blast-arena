package com.lifeledger.core.designsystem.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Every design-system atom is previewed against both themes at once — a light/dark bug in
 * a shared atom (e.g. a hardcoded colour) would otherwise only surface once some feature
 * screen happens to preview in the "wrong" theme.
 */
@Preview(name = "Light", group = "theme", showBackground = true, backgroundColor = 0xFFFBF6EE)
@Preview(name = "Dark", group = "theme", showBackground = true, backgroundColor = 0xFF19140F, uiMode = 0x20)
annotation class LightDarkPreview
