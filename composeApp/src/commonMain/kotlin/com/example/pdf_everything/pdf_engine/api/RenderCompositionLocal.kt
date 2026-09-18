package com.example.pdf_everything.pdf_engine.api

import androidx.compose.runtime.compositionLocalOf

/** Single app-scoped render scheduler shared by viewer/editor/cut/form workspaces. */
val LocalPdfRenderScheduler = compositionLocalOf<RenderScheduler?> { null }
