package com.example.pdf_everything.ui.design_system

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* ═══════════════════════════════════════════════════════════════════════
 *  PDF Everything — Material 3 Theme
 *
 *  Per spec §44‑§47:
 *    - Light & dark mode
 *    - Extended color scheme for PDF-specific accents
 *    - WCAG AA contrast ratios
 *    - Dynamic color on Android 12+ (handled via expect/actual)
 * ═══════════════════════════════════════════════════════════════════════ */

// ── Extended color tokens (PDF-specific) ──────────────────────────────

data class ExtendedColorScheme(
    val pdfPagePaper: Color     = PdfPagePaper,
    val pdfPageShadow: Color    = PdfPageShadow,
    val pdfHighlightYellow: Color = PdfHighlightYellow,
    val pdfHighlightGreen: Color  = PdfHighlightGreen,
    val pdfHighlightPink: Color   = PdfHighlightPink,
    val pdfAnnotationBlue: Color  = PdfAnnotationBlue,
    val pdfFormFocus: Color       = PdfFormFocus,
    val pdfSelection: Color       = PdfSelection
)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColorScheme() }

// ── Light color scheme ────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Teal500,
    onSecondary = Color.White,
    secondaryContainer = Teal100,
    onSecondaryContainer = Teal900,
    tertiary = Blue400,
    onTertiary = Color.White,
    tertiaryContainer = Blue50,
    onTertiaryContainer = Blue900,
    error = Red500,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Red900,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Gray700,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
    inversePrimary = Blue300
)

// ── Dark color scheme ─────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = Blue300,
    onPrimary = Blue900,
    primaryContainer = Blue700,
    onPrimaryContainer = Blue100,
    secondary = Teal300,
    onSecondary = Teal900,
    secondaryContainer = Teal700,
    onSecondaryContainer = Teal100,
    tertiary = Blue300,
    onTertiary = Blue900,
    tertiaryContainer = Blue800,
    onTertiaryContainer = Blue100,
    error = Red300,
    onError = Red900,
    errorContainer = Red700,
    onErrorContainer = Red100,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOutline,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    inversePrimary = Blue500
)

// ── Extended color overrides for dark mode ────────────────────────────

private val LightExtendedColors = ExtendedColorScheme()

private val DarkExtendedColors = ExtendedColorScheme(
    pdfPagePaper = Color(0xFF2B2B2B),
    pdfPageShadow = Color(0x4D000000),
    pdfSelection = Color(0x4D80B3FF)
)

// ── Dynamic color support (expect/actual) ──────────────────────────────

/**
 * Platform-specific dynamic color scheme resolver.
 * On Android 12+ this returns a Material You color scheme;
 * on Desktop it always returns null (falling back to static schemes).
 */
expect fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme?

// ── Theme composable ──────────────────────────────────────────────────

@Composable
fun PdfEverythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> resolveDynamicColorScheme(darkTheme) ?: if (darkTheme) DarkColorScheme else LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PdfEverythingTypography,
        content = {
            CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
                content()
            }
        }
    )
}

// ── Convenience accessor ──────────────────────────────────────────────

object PdfEverythingTheme {
    val extendedColors: ExtendedColorScheme
        @Composable
        get() = LocalExtendedColors.current
}