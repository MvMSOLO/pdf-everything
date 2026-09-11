package com.example.pdf_everything.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.app.router.AppRoute
import com.example.pdf_everything.core.files.DocumentFileService
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.ui.design_system.PdfIcons
import com.example.pdf_everything.ui.design_system.Spacing
import kotlinx.coroutines.launch

/**
 * Home screen showing recent files, quick actions, and the "Open PDF" FAB.
 * Per spec §7, §10–§14, §80.
 * No fake/stub buttons — all actions are wired to real functionality (§0).
 */
@Composable
fun HomeScreen(
    router: AppRouter,
    recentFiles: List<DocumentFileService.RecentFile>,
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            // §7 / §80: "Open PDF" FAB — wired to real file picker
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val result = appState.documentFileService.openFromPicker()
                        if (result is com.example.pdf_everything.core.services.EngineResult.Success) {
                            appState.openDocument(result.value)
                            router.navigate(AppRoute.Viewer(documentId = result.value.documentId))
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(PdfIcons.Open, contentDescription = "Open PDF")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xl)
        ) {
            Text(
                text = "PDF Everything",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Recent Files",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(Spacing.sm))
            if (recentFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent files. Tap the button below to open a PDF.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(recentFiles) { file ->
                        RecentFileItem(
                            recentFile = file,
                            onClick = {
                                // Pass the real source to openDocument, not an empty string
                                scope.launch {
                                    val result = appState.documentFileService.openDocument(file.source)
                                    if (result is com.example.pdf_everything.core.services.EngineResult.Success) {
                                        appState.openDocument(result.value)
                                        router.navigate(AppRoute.Viewer(documentId = result.value.documentId))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentFileItem(
    recentFile: DocumentFileService.RecentFile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                PdfIcons.Pdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = recentFile.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${recentFile.pageCount} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
