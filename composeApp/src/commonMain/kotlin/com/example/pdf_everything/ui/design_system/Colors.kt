package com.example.pdf_everything.ui.design_system

import androidx.compose.ui.graphics.Color

/* ═══════════════════════════════════════════════════════════════════════
 *  PDF Everything — Design Token Colors
 *
 *  Spec §44‑§47: Professional PDF editor palette with high contrast,
 *  WCAG AA compliance, dark & light modes.
 * ═══════════════════════════════════════════════════════════════════════ */

// ── Primary (Blue) ──────────────────────────────────────────────────────

val Blue50  = Color(0xFFF0F7FF)
val Blue100 = Color(0xFFE0EFFF)
val Blue200 = Color(0xFFBFD9FF)
val Blue300 = Color(0xFF80B3FF)
val Blue400 = Color(0xFF4D94FF)
val Blue500 = Color(0xFF1A6FFF)  // Primary
val Blue600 = Color(0xFF0052CC)  // Primary dark
val Blue700 = Color(0xFF003D99)
val Blue800 = Color(0xFF002966)
val Blue900 = Color(0xFF001433)

// ── Secondary (Teal) ────────────────────────────────────────────────────

val Teal50  = Color(0xFFF0FAFA)
val Teal100 = Color(0xFFE0F5F5)
val Teal200 = Color(0xFFB3E8E8)
val Teal300 = Color(0xFF80D8D8)
val Teal400 = Color(0xFF4DC8C8)
val Teal500 = Color(0xFF26A69A)  // Secondary
val Teal600 = Color(0xFF00897B)  // Secondary dark
val Teal700 = Color(0xFF00695C)
val Teal800 = Color(0xFF004D40)
val Teal900 = Color(0xFF002520)

// ── Error / Destructive (Red) ──────────────────────────────────────────

val Red50   = Color(0xFFFFEBEE)
val Red100  = Color(0xFFFFCDD2)
val Red200  = Color(0xFFEF9A9A)
val Red300  = Color(0xFFE57373)
val Red400  = Color(0xFFEF5350)
val Red500  = Color(0xFFE53935)  // Error
val Red600  = Color(0xFFC62828)
val Red700  = Color(0xFFB71C1C)
val Red800  = Color(0xFF8B0000)
val Red900  = Color(0xFF4A0000)

// ── Warning (Amber) ────────────────────────────────────────────────────

val Amber50  = Color(0xFFFFF8E1)
val Amber100 = Color(0xFFFFECB3)
val Amber200 = Color(0xFFFFE082)
val Amber300 = Color(0xFFFFD54F)
val Amber400 = Color(0xFFFFCA28)
val Amber500 = Color(0xFFFFC107)  // Warning
val Amber600 = Color(0xFFFF8F00)
val Amber700 = Color(0xFFFF6F00)
val Amber800 = Color(0xFFE65100)
val Amber900 = Color(0xFFFFBF00)

// ── Success (Green) ────────────────────────────────────────────────────

val Green50  = Color(0xFFE8F5E9)
val Green100 = Color(0xFFC8E6C9)
val Green200 = Color(0xFFA5D6A7)
val Green300 = Color(0xFF81C784)
val Green400 = Color(0xFF66BB6A)
val Green500 = Color(0xFF4CAF50)  // Success
val Green600 = Color(0xFF388E3C)
val Green700 = Color(0xFF2E7D32)
val Green800 = Color(0xFF1B5E20)
val Green900 = Color(0xFF0D3B0E)

// ── Neutral / Gray ─────────────────────────────────────────────────────

val Gray50   = Color(0xFFFAFAFA)
val Gray100  = Color(0xFFF5F5F5)
val Gray200  = Color(0xFFEEEEEE)
val Gray300  = Color(0xFFE0E0E0)
val Gray400  = Color(0xFFBDBDBD)
val Gray500  = Color(0xFF9E9E9E)
val Gray600  = Color(0xFF757575)
val Gray700  = Color(0xFF616161)
val Gray800  = Color(0xFF424242)
val Gray900  = Color(0xFF212121)

// ── Surface / Background ──────────────────────────────────────────────

val LightBackground   = Color(0xFFFFFBFE)
val LightSurface      = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF5F3F8)
val LightOnBackground = Color(0xFF1C1B1F)
val LightOnSurface    = Color(0xFF1C1B1F)
val LightOutline      = Color(0xFF79747E)
val LightOutlineVariant = Color(0xFFCAC4D0)

val DarkBackground    = Color(0xFF1C1B1F)
val DarkSurface       = Color(0xFF2B2930)
val DarkSurfaceVariant = Color(0xFF332D41)
val DarkOnBackground  = Color(0xFFE6E1E5)
val DarkOnSurface     = Color(0xFFE6E1E5)
val DarkOutline       = Color(0xFF938F99)
val DarkOutlineVariant = Color(0xFF49454F)

// ── PDF-specific accents ──────────────────────────────────────────────

val PdfPagePaper      = Color(0xFFFFFFFF)   // Paper-white for page rendering bg
val PdfPageShadow     = Color(0x33000000)   // Drop shadow around pages
val PdfHighlightYellow = Color(0xFFFFFF00)
val PdfHighlightGreen  = Color(0xFF00FF00)
val PdfHighlightPink   = Color(0xFFFF69B4)
val PdfAnnotationBlue  = Color(0xFF2196F3)
val PdfFormFocus       = Color(0xFF1A6FFF)
val PdfSelection       = Color(0x334D94FF)   // 20% opacity selection rect