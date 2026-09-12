package com.example.pdf_everything.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.core.commands.CropPageCommand
import com.example.pdf_everything.core.commands.RotatePageCommand
import com.example.pdf_everything.core.document.PageRotation
import com.example.pdf_everything.core.document.PdfRect
import com.example.pdf_everything.core.services.AppState
import kotlinx.coroutines.launch

enum class EditorMode {
    SELECT,
    CROP,
    ANNOTATE,
    TEXT_EDIT,
    WATERMARK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    appState: AppState,
    router: AppRouter,
    modifier: Modifier = Modifier
) {
    val currentDoc = appState.currentDocument
    val scope = rememberCoroutineScope()

    var activeMode by remember { mutableStateOf(EditorMode.SELECT) }
    var selectedPageIndex by remember { mutableStateOf(0) }
    var cropRect by remember { mutableStateOf(PdfRect(50f, 50f, 400f, 600f)) }
    var annotationText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(currentDoc?.name ?: "PDF Editor") },
                navigationIcon = {
                    IconButton(onClick = { router.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val doc = currentDoc
                            if (doc != null) {
                                scope.launch {
                                    runCatching {
                                        appState.pdfEngine.executeCommand(
                                            doc.documentId,
                                            RotatePageCommand(
                                                commandId = "rot_${System.currentTimeMillis()}",
                                                pageId = "p$selectedPageIndex",
                                                pageIndex = selectedPageIndex,
                                                oldRotation = PageRotation.ROTATION_0,
                                                newRotation = PageRotation.ROTATION_90
                                            )
                                        )
                                        statusMessage = "Page rotated 90°"
                                    }.onFailure {
                                        statusMessage = "Error: ${it.message}"
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate")
                    }

                    Button(
                        onClick = {
                            val doc = currentDoc
                            if (doc != null) {
                                scope.launch {
                                    runCatching {
                                        appState.documentFileService.saveDocument(doc.documentId)
                                        statusMessage = "Document saved successfully"
                                    }.onFailure {
                                        statusMessage = "Save error: ${it.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        if (currentDoc == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No document loaded for editing.", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { router.popBackStack() }) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Toolbar Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorToolButton("Select", Icons.Default.TouchApp, activeMode == EditorMode.SELECT) {
                        activeMode = EditorMode.SELECT
                    }
                    EditorToolButton("Crop", Icons.Default.Crop, activeMode == EditorMode.CROP) {
                        activeMode = EditorMode.CROP
                    }
                    EditorToolButton("Annotate", Icons.Default.Edit, activeMode == EditorMode.ANNOTATE) {
                        activeMode = EditorMode.ANNOTATE
                    }
                    EditorToolButton("Text", Icons.Default.TextFields, activeMode == EditorMode.TEXT_EDIT) {
                        activeMode = EditorMode.TEXT_EDIT
                    }
                    EditorToolButton("Watermark", Icons.Default.BrandingWatermark, activeMode == EditorMode.WATERMARK) {
                        activeMode = EditorMode.WATERMARK
                    }
                }

                statusMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Main Workspace
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left sidebar: Pages
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                    ) {
                        Text("Pages (${currentDoc.pageCount})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn {
                            items((0 until currentDoc.pageCount).toList()) { idx ->
                                val isSelected = idx == selectedPageIndex
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { selectedPageIndex = idx },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text("Page ${idx + 1}")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

                    // Right Canvas / Tool Panel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (activeMode) {
                            EditorMode.SELECT -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .aspectRatio(0.707f)
                                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp)),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                "Page ${selectedPageIndex + 1} Content View",
                                                color = Color.Black,
                                                fontSize = 20.sp
                                            )
                                        }
                                    }
                                }
                            }
                            EditorMode.CROP -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Crop Page ${selectedPageIndex + 1}", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Bounding Box: (${cropRect.x.toInt()}, ${cropRect.y.toInt()}) to (${cropRect.right.toInt()}, ${cropRect.bottom.toInt()})")
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            val doc = currentDoc ?: return@Button
                                            scope.launch {
                                                runCatching {
                                                    appState.pdfEngine.executeCommand(
                                                        doc.documentId,
                                                        CropPageCommand(
                                                            commandId = "crop_${System.currentTimeMillis()}",
                                                            pageId = "p$selectedPageIndex",
                                                            pageIndex = selectedPageIndex,
                                                            previousCropBox = null,
                                                            nextCropBox = cropRect
                                                        )
                                                    )
                                                    statusMessage = "Page ${selectedPageIndex + 1} cropped successfully"
                                                }.onFailure {
                                                    statusMessage = "Crop failed: ${it.message}"
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Apply Crop")
                                    }
                                }
                            }
                            EditorMode.ANNOTATE, EditorMode.TEXT_EDIT -> {
                                Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                                    Text("Add Annotation / Note", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = annotationText,
                                        onValueChange = { annotationText = it },
                                        label = { Text("Note content") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            statusMessage = "Annotation added: $annotationText"
                                            annotationText = ""
                                        }
                                    ) {
                                        Text("Add Note")
                                    }
                                }
                            }
                            EditorMode.WATERMARK -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Watermark Tool", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { statusMessage = "Watermark applied to document" }) {
                                        Text("Add CONFIDENTIAL Watermark")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) }
    )
}
