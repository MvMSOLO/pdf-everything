package com.example.pdf_everything.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.app.router.*
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.services.PlatformService
import com.example.pdf_everything.ui.design_system.*
import com.example.pdf_everything.ui.layout.*
import com.example.pdf_everything.feature.viewer.ViewerScreen

/* ═══════════════════════════════════════════════════════════════════════
 *  PDF Everything — App Shell
 *
 *  Per spec §35‑§43:
 *    - Top app bar with file name, undo/redo, zoom
 *    - Side panel for outline / thumbnails (expanded width)
 *    - Main content area (page viewer / editor)
 *    - Bottom bar with page indicator / status
 *    - Responsive layout (compact / medium / expanded)
 *    - Navigation via AppRouter
 * ═══════════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun App(
    platformService: PlatformService,
    onOpenFileRequest: (() -> Unit)? = null
) {
    PdfEverythingTheme {
        val router = remember { AppRouter() }
        val layout = rememberLayoutConfiguration()
        val snackbarHostState = remember { SnackbarHostState() }

        CompositionLocalProvider(LocalAppRouter provides router) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    PdfTopBar(
                        router = router,
                        layout = layout
                    )
                },
                bottomBar = {
                    if (router.currentRoute !is AppRoute.Home) {
                        PdfBottomBar(router = router)
                    }
                },
                content = { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        AppContent(
                            router = router,
                            layout = layout,
                            platformService = platformService,
                            onOpenFileRequest = onOpenFileRequest
                        )
                    }
                }
            )
        }
    }
}

// ── Top App Bar ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfTopBar(
    router: AppRouter,
    layout: LayoutConfiguration
) {
    val route = router.currentRoute

    TopAppBar(
        title = {
            Text(
                text = when (route) {
                    is AppRoute.Home -> "PDF Everything"
                    is AppRoute.Viewer -> "${route.documentId}"
                    is AppRoute.Editor -> "Editing: ${route.documentId}"
                    is AppRoute.FormFill -> "Form: ${route.documentId}"
                    is AppRoute.Settings -> "Settings"
                    is AppRoute.About -> "About"
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        },
        navigationIcon = {
            if (router.canGoBack) {
                PdfToolbarButton(
                    icon = PdfIcons.Back,
                    contentDescription = "Back",
                    onClick = { router.popBackStack() }
                )
            } else if (route is AppRoute.Home) {
                Icon(
                    imageVector = PdfIcons.Pdf,
                    contentDescription = "PDF Everything",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        actions = {
            when (route) {
                is AppRoute.Viewer, is AppRoute.Editor -> {
                    PdfToolbarButton(
                        icon = PdfIcons.Undo,
                        contentDescription = "Undo",
                        onClick = { /* TODO: wire to CommandDispatcher */ }
                    )
                    PdfToolbarButton(
                        icon = PdfIcons.Redo,
                        contentDescription = "Redo",
                        onClick = { /* TODO: wire to CommandDispatcher */ }
                    )
                    PdfToolbarButton(
                        icon = PdfIcons.Search,
                        contentDescription = "Search",
                        onClick = { /* TODO: wire to SearchEngine */ }
                    )
                    if (!layout.toolbarCollapsed) {
                        PdfToolbarButton(
                            icon = PdfIcons.ZoomIn,
                            contentDescription = "Zoom In",
                            onClick = { /* TODO */ }
                        )
                        PdfToolbarButton(
                            icon = PdfIcons.ZoomOut,
                            contentDescription = "Zoom Out",
                            onClick = { /* TODO */ }
                        )
                    }
                }
                is AppRoute.Home -> {
                    PdfToolbarButton(
                        icon = PdfIcons.Settings,
                        contentDescription = "Settings",
                        onClick = { router.navigate(AppRoute.Settings) }
                    )
                }
                else -> {}
            }
            PdfToolbarButton(
                icon = PdfIcons.More,
                contentDescription = "More options",
                onClick = { /* TODO: overflow menu */ }
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ── Bottom Bar ───────────────────────────────────────────────────────

@Composable
private fun PdfBottomBar(
    router: AppRouter
) {
    val route = router.currentRoute

    Surface(
        tonalElevation = Spacing.sm,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Page indicator (left)
            Text(
                text = when (route) {
                    is AppRoute.Viewer -> "Page 1 / 1"  // TODO: real page count
                    is AppRoute.Editor -> "Page 1 / 1"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Zoom indicator (center)
            Text(
                text = "100%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Status (right)
            when (route) {
                is AppRoute.Editor ->
                    PdfStatusChip(
                        text = "Editing",
                        color = Green500
                    )
                is AppRoute.Viewer ->
                    PdfStatusChip(
                        text = "Viewing",
                        color = Blue500
                    )
                else -> Spacer(Modifier.width(Spacing.sm))
            }
        }
    }
}

// ── Content routing ──────────────────────────────────────────────────

@Composable
private fun AppContent(
    router: AppRouter,
    layout: LayoutConfiguration,
    platformService: PlatformService,
    onOpenFileRequest: (() -> Unit)?
) {
    val route by remember { derivedStateOf { router.currentRoute } }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (targetState is AppRoute.Home) {
                slideInVertically(initialOffsetY = { it / 3 }) with
                    slideOutVertically(targetOffsetY = { -it / 3 })
            } else {
                slideInVertically(initialOffsetY = { -it / 3 }) with
                    slideOutVertically(targetOffsetY = { it / 3 })
            } using SizeTransform(clip = false)
        },
        label = "route_transition"
    ) { targetRoute ->
        when (targetRoute) {
            is AppRoute.Home -> HomeScreen(
                platformService = platformService,
                onOpenFile = {
                    onOpenFileRequest?.invoke()
                },
                onOpenRecent = { docId ->
                    router.navigate(AppRoute.Viewer(docId))
                },
                onNavigateToSettings = {
                    router.navigate(AppRoute.Settings)
                },
                onNavigateToAbout = {
                    router.navigate(AppRoute.About)
                }
            )

            is AppRoute.Viewer -> ViewerScreen(
                document = null,  // TODO: wire to DocumentRepository.load(targetRoute.documentId)
                pdfEngine = null,  // TODO: wire to platform PdfEngine instance
                onBack = { router.popBackStack() },
                onEdit = { docId -> router.navigate(AppRoute.Editor(docId)) }
            )

            is AppRoute.Editor -> EditorPlaceholder(
                documentId = targetRoute.documentId,
                layout = layout
            )

            is AppRoute.FormFill -> FormFillPlaceholder(
                documentId = targetRoute.documentId
            )

            is AppRoute.Settings -> SettingsPlaceholder()

            is AppRoute.About -> AboutPlaceholder()
        }
    }
}

// ── Home screen ──────────────────────────────────────────────────────

@Composable
private fun HomeScreen(
    platformService: PlatformService,
    onOpenFile: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        // App logo area
        Icon(
            imageVector = PdfIcons.Pdf,
            contentDescription = "PDF Everything",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = "PDF Everything",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "View, edit, and manage your PDFs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.xxl))

        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ElevatedButton(
                onClick = onOpenFile,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm)
            ) {
                Icon(PdfIcons.Open, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Open PDF")
            }

            OutlinedButton(
                onClick = { /* TODO: create blank PDF */ },
                modifier = Modifier.weight(1f).padding(start = Spacing.sm)
            ) {
                Icon(PdfIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("New PDF")
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        // Recent files section
        PdfSectionHeader(title = "Recent Files")

        // TODO: wire to DocumentFileService.recentFiles
        PdfEmptyState(
            icon = PdfIcons.Pdf,
            title = "No recent files",
            subtitle = "Open a PDF to get started",
            action = {
                TextButton(onClick = onOpenFile) {
                    Text("Browse files")
                }
            }
        )
    }
}

// ── Viewer placeholder ────────────────────────────────────────────────

@Composable
private fun ViewerPlaceholder(
    documentId: String,
    layout: LayoutConfiguration
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Side panel (outline / thumbnails) on expanded width
        if (layout.showSidePanel) {
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                tonalElevation = Spacing.sm,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md)
                ) {
                    PdfSectionHeader(title = "Outline")
                    Text(
                        text = "No outline available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    PdfSectionHeader(title = "Pages")
                    Text(
                        text = "No pages loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Main viewer area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            PdfEmptyState(
                icon = PdfIcons.Pdf,
                title = "PDF Viewer",
                subtitle = "Document: $documentId\n(Page rendering will be wired to PdfEngine)",
                modifier = Modifier.padding(Spacing.xxl)
            )
        }
    }
}

// ── Editor placeholder ────────────────────────────────────────────────

@Composable
private fun EditorPlaceholder(
    documentId: String,
    layout: LayoutConfiguration
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PdfEmptyState(
            icon = PdfIcons.Edit,
            title = "PDF Editor",
            subtitle = "Document: $documentId\nFull editing coming in Phase 2"
        )
    }
}

// ── Form fill placeholder ─────────────────────────────────────────────

@Composable
private fun FormFillPlaceholder(
    documentId: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PdfEmptyState(
            icon = PdfIcons.EditText,
            title = "Form Filler",
            subtitle = "Document: $documentId\nForm filling coming in Phase 2"
        )
    }
}

// ── Settings placeholder ─────────────────────────────────────────────

@Composable
private fun SettingsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PdfEmptyState(
            icon = PdfIcons.Settings,
            title = "Settings",
            subtitle = "Preferences will be available here"
        )
    }
}

// ── About placeholder ─────────────────────────────────────────────────

@Composable
private fun AboutPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = PdfIcons.Pdf,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "PDF Everything",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Version 1.0.0 (Phase 1)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "A professional PDF viewer and editor\nBuilt with Kotlin Multiplatform + Compose",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}