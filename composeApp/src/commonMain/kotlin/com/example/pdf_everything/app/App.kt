package com.example.pdf_everything.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.pdf_everything.app.router.*
import com.example.pdf_everything.app.ui.screens.*
import com.example.pdf_everything.app.ui.bars.*
import com.example.pdf_everything.core.services.AppState

/**
 * Top-level composable for the entire application.
 *
 * Per spec: single AppBar at the App level that is VISIBLE on Home/Settings/About
 * but HIDDEN on Viewer/Editor routes (which have their own bars inside
 * ViewerScreen / EditorScreen).
 *
 * Undo/Redo wired to [AppState.commandDispatcher].
 * No fake/stub buttons per spec §0.
 */
@Composable
fun App(appState: AppState) {
    val router = remember { AppRouter() }
    val currentRoute by router.state

    val showAppBars = currentRoute.currentRoute !is AppRoute.Viewer
            && currentRoute.currentRoute !is AppRoute.Editor
            && currentRoute.currentRoute !is AppRoute.FormFill

    val canUndo by appState.commandDispatcher::canUndo
    val canRedo by appState.commandDispatcher::canRedo

    Scaffold(
        topBar = {
            if (showAppBars) {
                PdfTopBar(
                    router = router,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = {
                        appState.currentDocument?.let { doc ->
                            val result = appState.commandDispatcher.undo(doc)
                            appState.updateDocument(result)
                        }
                    },
                    onRedo = {
                        appState.currentDocument?.let { doc ->
                            val result = appState.commandDispatcher.redo(doc)
                            appState.updateDocument(result)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showAppBars) {
                PdfBottomBar(router = router)
            }
        }
    ) { innerPadding ->
        when (val route = currentRoute.currentRoute) {
            is AppRoute.Home -> {
                HomeScreen(
                    router = router,
                    recentFiles = appState.documentFileService.recentFiles,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.Viewer -> {
                ViewerScreen(
                    document = appState.currentDocument,
                    pdfEngine = appState.pdfEngine,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.Editor -> {
                // Phase 2 placeholder — show ViewerScreen in editor mode
                ViewerScreen(
                    document = appState.currentDocument,
                    pdfEngine = appState.pdfEngine,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.FormFill -> {
                // Phase 2 placeholder
                Text("Form Fill – coming in Phase 2", modifier = Modifier.padding(innerPadding))
            }
            is AppRoute.Settings -> {
                Text("Settings – coming in Phase 2", modifier = Modifier.padding(innerPadding))
            }
            is AppRoute.About -> {
                Text("About – coming in Phase 2", modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
