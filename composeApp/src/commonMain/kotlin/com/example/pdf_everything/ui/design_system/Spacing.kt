package com.example.pdf_everything.ui.design_system

import androidx.compose.ui.unit.dp

/**
 * Spacing & dimension tokens for consistent layout.
 * Per spec §44 — 4px base grid.
 */
object Spacing {
    val none   = 0.dp
    val xs     = 2.dp      // 0.5x
    val sm     = 4.dp      // 1x base
    val md     = 8.dp      // 2x
    val lg     = 12.dp     // 3x
    val xl     = 16.dp     // 4x
    val xxl    = 24.dp     // 6x
    val xxxl   = 32.dp     // 8x
    val huge   = 48.dp     // 12x
    val massive = 64.dp    // 16x
}

object CornerRadius {
    val none   = 0.dp
    val sm     = 4.dp
    val md     = 8.dp
    val lg     = 12.dp
    val xl     = 16.dp
    val full   = 50      // percent — use with clip(CircleShape) instead
}

object Elevation {
    val none   = 0.dp
    val sm     = 1.dp
    val md     = 2.dp
    val lg     = 4.dp
    val xl     = 8.dp
    val xxl    = 16.dp
}

object IconSize {
    val sm     = 16.dp
    val md     = 24.dp
    val lg     = 32.dp
    val xl     = 48.dp
}