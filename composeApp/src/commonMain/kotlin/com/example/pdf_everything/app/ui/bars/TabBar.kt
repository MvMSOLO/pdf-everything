package com.example.pdf_everything.app.ui.bars

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onKeyEvent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.core.shortcuts.ShortcutRegistry
import com.example.pdf_everything.core.shortcuts.ShortcutAction

/**
 * Multi-document tab bar per spec §22.
 *
 * Renders one tab per open document, supports:
 *  - Click to switch tab
 *  - Close button per tab (×)
 *  - Dirty indicator (dot prefix)
 *  - Keyboard shortcut dispatch via ShortcutRegistry
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val tabs = appState.tabs
    val activeIndex = appState.activeTabIndex

    if (tabs.isEmpty()) return

    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == activeIndex
                val bgColor = if (isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    Color.Transparent
                val textColor = if (isActive)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant

                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(bgColor)
                        .clickable { appState.switchTab(index) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .height(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Dirty indicator
                    if (tab.isDirty) {
                        Text(
                            text = "●",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = tab.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Close button
                    IconButton(
                        onClick = { appState.closeTab(index) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Text(
                            text = "×",
                            fontSize = 14.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                }

                if (index < tabs.size - 1) {
                    Spacer(Modifier.width(2.dp))
                }
            }
        }
    }
}

/**
 * Keyboard event handler that dispatches via ShortcutRegistry.
 * Wire this into the top-level Window via onPreviewKeyEvent.
 */
fun shortcutKeyEventHandler(
    registry: ShortcutRegistry,
    onAction: (ShortcutAction) -> Unit
): (KeyEvent) -> Boolean = { event ->
    if (event.type == KeyEventType.KeyDown) {
        val isCtrl = event.isCtrlPressed
        val isAlt = event.isAltPressed
        val isShift = event.isShiftPressed
        val keyName = event.key.name

        val action = registry.resolve(isCtrl, isAlt, isShift, keyName)
        if (action != null) {
            onAction(action)
            true  // consumed
        } else {
            false  // not consumed
        }
    } else {
        false
    }
}
