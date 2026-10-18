package com.dwarpallabs.linkguard.ui.theme

import androidx.compose.ui.graphics.Color

// Brand tokens. One brand colour, one ink, three status colours.
// Status colours mean link safety only; never use them for decoration.

val Indigo = Color(0xFF1E3A8A)
val IndigoTint = Color(0xFFEEF2FF)
val Ink = Color(0xFF111827)
val InkMuted = Color(0xFF4B5563)
val Outline = Color(0xFF6B7280)
val OutlineSoft = Color(0xFFE5E7EB)
val Paper = Color(0xFFFFFFFF)
val PaperRaised = Color(0xFFF3F4F6)

val Safe = Color(0xFF15803D)
val SafeTint = Color(0xFFDCFCE7)
val OnSafeTint = Color(0xFF166534)
val Caution = Color(0xFFB45309)
val CautionTint = Color(0xFFFEF3C7)
val OnCautionTint = Color(0xFF92400E)
val Danger = Color(0xFFB91C1C)
val DangerTint = Color(0xFFFEE2E2)
val OnDangerTint = Color(0xFF991B1B)

val NightBackground = Color(0xFF0B1220)
val NightSurface = Color(0xFF141C2F)
val NightInk = Color(0xFFF3F4F6)
val NightInkMuted = Color(0xFF9CA3AF)
val NightIndigo = Color(0xFFA5B4FC)
val NightOutlineSoft = Color(0xFF1F2A40)

// Night tints are the status colour at 16 % over NightSurface, pre-mixed so they stay opaque.
val NightIndigoTint = Color(0xFF2B3450)
val NightSafe = Color(0xFF4ADE80)
val NightSafeTint = Color(0xFF1D3B3C)
val NightCaution = Color(0xFFFBBF24)
val NightCautionTint = Color(0xFF39362D)
val NightDanger = Color(0xFFF87171)
val NightDangerTint = Color(0xFF382A3A)
