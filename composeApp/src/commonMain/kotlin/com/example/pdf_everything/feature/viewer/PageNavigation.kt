package com.example.pdf_everything.feature.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.ui.design_system.Spacing

/* ═══════════════════════════════════════════════════════════════════════
 *  Page Navigation — per spec §15, §16
 *
 *  Features:
 *    - Current page / total display
 *    - Go-to-page input field (click on page number)
 *    - Previous / next / first / last page buttons
 *    - Keyboard shortcut support (Page Up/Down, Home/End, Ctrl+G)
 * ═══════════════════════════════════════════════════════════════════════ */

@Composable
fun PageNavigationBar(
    currentPage: Int,
    totalPages: Int,
    onGoToPage: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onFirstPage: () -> Unit,
    onLastPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGoToPage by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // First page
            IconButton(
                onClick = onFirstPage,
                enabled = currentPage > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Text("⏮", style = MaterialTheme.typography.labelSmall)
            }

            // Previous page
            IconButton(
                onClick = onPreviousPage,
                enabled = currentPage > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Text("◀", style = MaterialTheme.typography.labelSmall)
            }

            // Page number / go-to-page
            if (showGoToPage) {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it },
                    modifier = Modifier.width(56.dp).height(32.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val pageNum = pageInput.toIntOrNull()
                            if (pageNum != null && pageNum in 1..totalPages) {
                                onGoToPage(pageNum - 1) // 0-indexed internally
                            }
                            showGoToPage = false
                            pageInput = ""
                        }
                    )
                )
                Text("/ $totalPages", style = MaterialTheme.typography.labelMedium)
            } else {
                TextButton(
                    onClick = { showGoToPage = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Next page
            IconButton(
                onClick = onNextPage,
                enabled = currentPage < totalPages - 1,
                modifier = Modifier.size(28.dp)
            ) {
                Text("▶", style = MaterialTheme.typography.labelSmall)
            }

            // Last page
            IconButton(
                onClick = onLastPage,
                enabled = currentPage < totalPages - 1,
                modifier = Modifier.size(28.dp)
            ) {
                Text("⏭", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Keyboard shortcut handler for page navigation.
 * Call from the screen-level key event handler.
 *
 * Supported shortcuts:
 *   Page Up   → previous page
 *   Page Down → next page
 *   Home      → first page
 *   End       → last page
 *   Ctrl+G    → open go-to-page dialog
 */
object PageNavigationShortcuts {
    fun handleKeyEvent(
        key: String,
        isCtrlDown: Boolean,
        onPreviousPage: () -> Unit,
        onNextPage: () -> Unit,
        onFirstPage: () -> Unit,
        onLastPage: () -> Unit,
        onGoToPage: () -> Unit
    ): Boolean {
        return when {
            key == "PageUp" && !isCtrlDown -> { onPreviousPage(); true }
            key == "PageDown" && !isCtrlDown -> { onNextPage(); true }
            key == "Home" && !isCtrlDown -> { onFirstPage(); true }
            key == "End" && !isCtrlDown -> { onLastPage(); true }
            (key == "g" || key == "G") && isCtrlDown -> { onGoToPage(); true }
            else -> false
        }
    }
}