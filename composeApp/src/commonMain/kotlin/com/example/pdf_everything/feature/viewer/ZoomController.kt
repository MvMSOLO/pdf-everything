package com.example.pdf_everything.feature.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.ui.design_system.Spacing
import kotlin.math.roundToInt

/* ═══════════════════════════════════════════════════════════════════════
 *  Zoom Controller UI — per spec §17
 *
 *  Controls:
 *    - Fit page / fit width / fit height / actual size / custom %
 *    - Zoom in / zoom out buttons
 *    - Current zoom percentage display
 *    - Zoom slider (optional)
 *    - Quick zoom presets dropdown
 * ═══════════════════════════════════════════════════════════════════════ */

@Composable
fun ZoomControlsBar(
    viewportState: ViewportState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomModeChange: (ZoomMode) -> Unit,
    onZoomPercentageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showZoomMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Spacing.md),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = Spacing.sm,
        shadowElevation = Spacing.xs
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Zoom out
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(32.dp)
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }

            // Zoom percentage display (clickable to open presets)
            Box {
                TextButton(
                    onClick = { showZoomMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "${viewportState.zoomPercentage}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showZoomMenu,
                    onDismissRequest = { showZoomMenu = false }
                ) {
                    // Zoom presets
                    ZoomMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(zoomModeLabel(mode)) },
                            onClick = {
                                onZoomModeChange(mode)
                                showZoomMenu = false
                            }
                        )
                    }

                    HorizontalDivider()

                    // Percentage presets
                    listOf(50, 75, 100, 125, 150, 200, 300, 400).forEach { pct ->
                        DropdownMenuItem(
                            text = { Text("$pct%") },
                            onClick = {
                                onZoomPercentageChange(pct)
                                showZoomMenu = false
                            }
                        )
                    }
                }
            }

            // Zoom in
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }

            // Zoom mode quick buttons
            HorizontalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(vertical = 4.dp)
            )

            // Fit width
            IconButton(
                onClick = { onZoomModeChange(ZoomMode.FIT_WIDTH) },
                modifier = Modifier.size(28.dp)
            ) {
                Text("W", style = MaterialTheme.typography.labelSmall)
            }

            // Fit page
            IconButton(
                onClick = { onZoomModeChange(ZoomMode.FIT_PAGE) },
                modifier = Modifier.size(28.dp)
            ) {
                Text("P", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun zoomModeLabel(mode: ZoomMode): String = when (mode) {
    ZoomMode.FIT_PAGE -> "Fit Page"
    ZoomMode.FIT_WIDTH -> "Fit Width"
    ZoomMode.FIT_HEIGHT -> "Fit Height"
    ZoomMode.ACTUAL_SIZE -> "Actual Size (100%)"
    ZoomMode.CUSTOM -> "Custom"
}