package com.dwarpallabs.linkguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Link-safety colours, which Material's scheme has no slots for. `onXTint` is the text/icon colour on `xTint`. */
@Immutable
data class StatusColors(
    val safe: Color, val safeTint: Color, val onSafeTint: Color,
    val caution: Color, val cautionTint: Color, val onCautionTint: Color,
    val danger: Color, val dangerTint: Color, val onDangerTint: Color,
)

private val DayStatus = StatusColors(
    Safe, SafeTint, OnSafeTint,
    Caution, CautionTint, OnCautionTint,
    Danger, DangerTint, OnDangerTint,
)

private val NightStatus = StatusColors(
    NightSafe, NightSafeTint, NightSafe,
    NightCaution, NightCautionTint, NightCaution,
    NightDanger, NightDangerTint, NightDanger,
)

val LocalStatusColors = staticCompositionLocalOf { DayStatus }

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Paper, primaryContainer = IndigoTint, onPrimaryContainer = Indigo,
    secondary = Indigo, onSecondary = Paper, secondaryContainer = IndigoTint, onSecondaryContainer = Ink,
    tertiary = Indigo, onTertiary = Paper, tertiaryContainer = IndigoTint, onTertiaryContainer = Ink,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink, surfaceVariant = PaperRaised, onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Paper, surfaceContainerLow = PaperRaised, surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperRaised, surfaceContainerHighest = PaperRaised,
    outline = Outline, outlineVariant = OutlineSoft,
    error = Danger, onError = Paper, errorContainer = DangerTint, onErrorContainer = OnDangerTint,
)

private val DarkColors = darkColorScheme(
    primary = NightIndigo, onPrimary = NightBackground, primaryContainer = NightIndigoTint, onPrimaryContainer = NightIndigo,
    secondary = NightIndigo, onSecondary = NightBackground, secondaryContainer = NightIndigoTint, onSecondaryContainer = NightInk,
    tertiary = NightIndigo, onTertiary = NightBackground, tertiaryContainer = NightIndigoTint, onTertiaryContainer = NightInk,
    background = NightBackground, onBackground = NightInk,
    surface = NightBackground, onSurface = NightInk, surfaceVariant = NightSurface, onSurfaceVariant = NightInkMuted,
    surfaceContainerLowest = NightBackground, surfaceContainerLow = NightSurface, surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurface, surfaceContainerHighest = NightSurface,
    outline = NightOutline, outlineVariant = NightOutlineSoft,
    error = NightDanger, onError = NightBackground, errorContainer = NightDangerTint, onErrorContainer = NightDanger,
)

/**
 * No Material You dynamic colour: wallpaper-derived colours could make a warning look like
 * decoration, so safety colours are identical on every phone.
 */
@Composable
fun LinkguardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) NightStatus else DayStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content
        )
    }
}

object LinkguardTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}
