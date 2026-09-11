package com.example.pdf_everything.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.app.router.AppRoute
import com.example.pdf_everything.core.files.DocumentFileService

/**
 * Home screen showing recent files and quick actions.
 * Per spec §10–§14.
 */
@Composable
fun HomeScreen(
    router: AppRouter,
    recentFiles: List<DocumentFileService.RecentFile>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "PDF Everything",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Recent Files",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        if (recentFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recent files. Open a PDF to get started.",
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
                            router.navigate(AppRoute.Viewer(documentId = ""))
                        }
                    )
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
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
