package com.dwarpallabs.linkguard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Scale from docs/design-system.md §4. System font (Noto covers every Indian script), letter
// spacing 0 so Indic conjuncts never break, line height ≥ 1.45× so vowel signs never clip.
// Every Material slot is set so nothing falls back to Material's smaller defaults.
private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

private val Display = style(28, 40, FontWeight.Bold)
private val Title = style(24, 34, FontWeight.SemiBold)
private val Heading = style(20, 30, FontWeight.SemiBold)

val Typography = Typography(
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display, // host on the warning screen
    headlineLarge = Title,
    headlineMedium = Title,
    headlineSmall = Title, // screen and warning titles
    titleLarge = Heading,
    titleMedium = Heading, // section headings, card titles
    titleSmall = style(18, 28, FontWeight.SemiBold),
    bodyLarge = style(18, 28, FontWeight.Normal), // default body text
    bodyMedium = style(16, 26, FontWeight.Normal), // descriptions; the minimum for anything a user must read
    bodySmall = style(14, 22, FontWeight.Normal), // captions only
    labelLarge = style(18, 26, FontWeight.SemiBold), // buttons
    labelMedium = style(16, 24, FontWeight.Normal),
    labelSmall = style(14, 22, FontWeight.Normal),
)
