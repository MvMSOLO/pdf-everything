package com.example.pdf_everything.app.ui.bars

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.ui.design_system.PdfIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfTopBar(
    router: AppRouter,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    TopAppBar(
        title = { Text("PDF Everything") },
        navigationIcon = {
            if (router.canGoBack) {
                IconButton(onClick = { router.popBackStack() }) {
                    Icon(PdfIcons.Back, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(PdfIcons.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(PdfIcons.Redo, contentDescription = "Redo")
            }
        }
    )
}