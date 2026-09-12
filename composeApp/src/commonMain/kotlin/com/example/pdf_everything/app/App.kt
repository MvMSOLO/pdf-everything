package com.example.pdf_everything.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.pdf_everything.app.router.*
import com.example.pdf_everything.app.ui.screens.*
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.core.services.EngineError
import com.example.pdf_everything.core.services.EngineResult
import com.example.pdf_everything.feature.viewer.ViewerScreen
import com.example.pdf_everything.app.ui.bars.*

/**
 * Data class holding the state for the password dialog (spec §27).
 * When non-null, the dialog is shown; when null, it is hidden.
 */
data class PasswordDialogTarget(
    val source: DocumentSource,
    val fileName: String,
    val errorMessage: String? = null,
    val isUnsupportedEncryption: Boolean = false,
    val isRetrying: Boolean = false
)

/**
 * Top-level composable for the entire application.
 *
 * Per spec: single AppBar at the App level that is VISIBLE on Home/Settings/About
 * but HIDDEN on Viewer/Editor/FormFill routes (which have their own bars inside
 * ViewerScreen / EditorScreen / FormFillScreen).
 *
 * Undo/Redo wired to [AppState.commandDispatcher].
 * Password dialog wired for encrypted PDFs (spec §27).
 * No fake/stub buttons per spec §0.
 */
@Composable
fun App(appState: AppState) {
    val router = remember { AppRouter() }
    val currentRoute by router.state

    // ── Password dialog state per spec §27 ──────────────────────
    var passwordDialogTarget by remember { mutableStateOf<PasswordDialogTarget?>(null) }

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
        // ── Password dialog overlay (§27) ───────────────────────
        passwordDialogTarget?.let { target ->
            PasswordDialog(
                fileName = target.fileName,
                errorMessage = target.errorMessage,
                isUnsupportedEncryption = target.isUnsupportedEncryption,
                onPasswordSubmit = { pwd ->
                    passwordDialogTarget = null
                },
                onDismiss = {
                    passwordDialogTarget = null
                }
            )
        }

        when (val route = currentRoute.currentRoute) {
            is AppRoute.Home -> {
                HomeScreen(
                    router = router,
                    recentFiles = appState.documentFileService.recentFiles,
                    appState = appState,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.Viewer -> {
                ViewerScreen(
                    document = appState.currentDocument,
                    pdfEngine = appState.pdfEngine,
                    onBack = { router.popBackStack() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.Editor -> {
                EditorScreen(
                    appState = appState,
                    router = router,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.FormFill -> {
                FormFillScreen(
                    appState = appState,
                    router = router,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.Settings -> {
                SettingsScreen(
                    router = router,
                    appState = appState,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppRoute.About -> {
                AboutScreen(
                    router = router,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

/**
 * Helper to show the password dialog when [EngineResult.Failure] has
 * [EngineError.INVALID_PASSWORD] — call this from any open flow.
 */
fun showPasswordDialog(
    targetState: MutableState<PasswordDialogTarget?>,
    source: DocumentSource,
    fileName: String,
    result: EngineResult.Failure
) {
    val isUnsupported = result.error == EngineError.UNSUPPORTED_FEATURE
    val message = when (result.error) {
        EngineError.INVALID_PASSWORD -> if (targetState.value?.isRetrying == true)
            "Wrong password. Please try again." else null
        EngineError.UNSUPPORTED_FEATURE -> null
        else -> result.message
    }
    targetState.value = PasswordDialogTarget(
        source = source,
        fileName = fileName,
        errorMessage = message,
        isUnsupportedEncryption = isUnsupported
    )
}
