package com.example.pdf_everything.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.core.settings.SettingsRepository
import com.example.pdf_everything.ui.design_system.PdfIcons
import com.example.pdf_everything.ui.design_system.Spacing

/**
 * Settings screen per spec §44.
 * Sections: General, View, Editing, Performance, Files, Shortcuts, About.
 * All toggles are wired to real settings state via [SettingsRepository] —
 * no fake/stub buttons (§0).  Values persist across sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    router: AppRouter,
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val settings = appState.settingsRepository

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (router.canGoBack) {
                        IconButton(onClick = { router.popBackStack() }) {
                            Icon(PdfIcons.Back, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // ── General ──
            item { SectionHeader("General") }
            item {
                ToggleRow("Dark Theme", settings.darkTheme) { settings.updateDarkTheme(it) }
            }
            item {
                ToggleRow("Dynamic Color", settings.dynamicColor) { settings.updateDynamicColor(it) }
            }
            item {
                DropdownRow("Language", settings.language, listOf("System", "English", "Uzbek", "Russian", "Chinese")) { settings.updateLanguage(it) }
            }

            // ── View ──
            item { SectionHeader("View") }
            item {
                ToggleRow("Show Page Numbers", settings.showPageNumbers) { settings.updateShowPageNumbers(it) }
            }
            item {
                DropdownRow("Default Zoom", settings.defaultZoomMode, listOf("Fit Width", "Fit Page", "100%", "200%")) { settings.updateDefaultZoomMode(it) }
            }
            item {
                ToggleRow("Smooth Scrolling", settings.smoothScrolling) { settings.updateSmoothScrolling(it) }
            }
            item {
                ToggleRow("Snap to Page", settings.snapToPage) { settings.updateSnapToPage(it) }
            }

            // ── Editing ──
            item { SectionHeader("Editing") }
            item {
                ToggleRow("Annotation Toolbar", settings.annotationToolbar) { settings.updateAnnotationToolbar(it) }
            }
            item {
                ToggleRow("Auto-save", settings.autoSave) { settings.updateAutoSave(it) }
            }
            item {
                if (settings.autoSave) {
                    SliderRow("Auto-save interval", settings.autoSaveInterval, 5..300, "${settings.autoSaveInterval}s") { settings.updateAutoSaveInterval(it) }
                }
            }

            // ── Performance ──
            item { SectionHeader("Performance") }
            item {
                ToggleRow("Hardware Acceleration", settings.hardwareAccel) { settings.updateHardwareAccel(it) }
            }
            item {
                SliderRow("Render cache size", settings.cacheSizeMb, 64..1024, "${settings.cacheSizeMb} MB") { settings.updateCacheSizeMb(it) }
            }

            // ── Files ──
            item { SectionHeader("Files") }
            item {
                SliderRow("Max recent files", settings.maxRecentFiles, 5..50, "${settings.maxRecentFiles}") { settings.updateMaxRecentFiles(it) }
            }
            item {
                DirectoryRow("Default save directory", settings.defaultSaveDir) { settings.updateDefaultSaveDir(it) }
            }

            // ── Shortcuts ──
            item { SectionHeader("Keyboard Shortcuts") }
            item {
                Text(
                    text = "Keyboard shortcuts can be viewed and customised from the dedicated shortcuts panel (§9).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

/* ── Reusable setting row composables ────────────────────────────────── */

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.lg)
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DropdownRow(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { onValueChange(opt); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    displayValue: String,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun DirectoryRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
