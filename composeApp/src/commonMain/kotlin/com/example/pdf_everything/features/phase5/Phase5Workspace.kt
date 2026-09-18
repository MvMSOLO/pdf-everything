package com.example.pdf_everything.features.phase5

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_everything.core.document.AnnotationColor
import com.example.pdf_everything.core.document.AnnotationComment
import com.example.pdf_everything.core.document.AnnotationStyle
import com.example.pdf_everything.core.document.AnnotationType
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentMetadata
import com.example.pdf_everything.core.document.FormField
import com.example.pdf_everything.core.document.FormFieldType
import com.example.pdf_everything.core.document.OutlineItem
import com.example.pdf_everything.core.document.PdfAnnotation
import com.example.pdf_everything.core.document.RectF
import com.example.pdf_everything.core.editor.EditorController
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import com.example.pdf_everything.pdf_engine.api.PdfDocumentInfo
import com.example.pdf_everything.pdf_engine.api.LocalPdfRenderScheduler
import com.example.pdf_everything.pdf_engine.api.RenderPriority
import com.example.pdf_everything.pdf_engine.api.PdfEngine

private enum class Phase5Tab { Annotations, Forms, Metadata, Bookmarks }

@Composable
fun Phase5Workspace(
    modifier: Modifier,
    document: Document,
    info: PdfDocumentInfo,
    selectedPage: Int,
    editor: EditorController,
    engine: PdfEngine,
    onChanged: (String) -> Unit,
    onPageChanged: (Int) -> Unit
) {
    var tab by remember { mutableStateOf(Phase5Tab.Annotations) }
    Row(modifier) {
        Column(Modifier.width(320.dp).fillMaxHeight().padding(12.dp)) {
            Text("Phase 5", style = MaterialTheme.typography.titleLarge)
            Text("Annotation / Forms / Metadata / Bookmarks", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(enabled = selectedPage > 0, onClick = { onPageChanged(selectedPage - 1) }) { Text("‹") }
                Button(enabled = selectedPage < info.pageCount - 1, onClick = { onPageChanged(selectedPage + 1) }) { Text("›") }
            }
            TabRow(selectedTabIndex = Phase5Tab.entries.indexOf(tab), modifier = Modifier.padding(top = 10.dp)) {
                Phase5Tab.entries.forEach { item -> Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.name.take(4)) }) }
            }
            when (tab) {
                Phase5Tab.Annotations -> AnnotationPanel(document, selectedPage, editor, onChanged)
                Phase5Tab.Forms -> FormPanel(document, editor, onChanged)
                Phase5Tab.Metadata -> MetadataPanel(document, editor, onChanged)
                Phase5Tab.Bookmarks -> BookmarkPanel(document, selectedPage, editor, onChanged, onPageChanged)
            }
        }
        Divider(Modifier.fillMaxHeight().width(1.dp))
        AnnotationPageSummary(document, selectedPage, engine)
    }
}

@Composable
private fun AnnotationPanel(document: Document, pageIndex: Int, editor: EditorController, onChanged: (String) -> Unit) {
    var type by remember { mutableStateOf(AnnotationType.Highlight) }
    var text by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var left by remember { mutableStateOf("72") }
    var top by remember { mutableStateOf("72") }
    var right by remember { mutableStateOf("300") }
    var bottom by remember { mutableStateOf("110") }
    val annotations = document.pages.getOrNull(pageIndex)?.annotations.orEmpty()
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Annotation tools", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(AnnotationType.Highlight, AnnotationType.Underline, AnnotationType.StrikeOut, AnnotationType.Note, AnnotationType.FreeText).forEach { candidate ->
                Button(onClick = { type = candidate }) { Text(candidate.name.take(5), fontSize = 9.sp) }
            }
        }
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Content / comment") }, modifier = Modifier.fillMaxWidth(), singleLine = false)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(value = left, onValueChange = { left = it.filter(Char::isDigit) }, label = { Text("L") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = top, onValueChange = { top = it.filter(Char::isDigit) }, label = { Text("T") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = right, onValueChange = { right = it.filter(Char::isDigit) }, label = { Text("R") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = bottom, onValueChange = { bottom = it.filter(Char::isDigit) }, label = { Text("B") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Button(onClick = {
            val bounds = RectF(left.toFloatOrNull() ?: 72f, top.toFloatOrNull() ?: 72f, right.toFloatOrNull() ?: 300f, bottom.toFloatOrNull() ?: 110f)
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val comment = text.takeIf { it.isNotBlank() }?.let { AnnotationComment(author = author, contents = it, subject = subject, createdAtEpochMs = now, modifiedAtEpochMs = now) }
            val annotation = PdfAnnotation(
                id = "ann-${now}-${kotlin.random.Random.nextLong().toString(16)}",
                pageIndex = pageIndex,
                type = type,
                bounds = bounds,
                style = AnnotationStyle(color = if (type == AnnotationType.Highlight) AnnotationColor(1f, 0.9f, 0f, 1f) else AnnotationColor()),
                comment = comment,
                contents = text,
                author = author,
                subject = subject
            )
            if (editor.addAnnotation(pageIndex, annotation)) onChanged("${type.name} annotation added")
        }, modifier = Modifier.fillMaxWidth()) { Text("Add ${type.name}") }
        Text("Existing: ${annotations.size}", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            items(annotations, key = { it.id }) { annotation ->
                Surface(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))) {
                    Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(annotation.type.name, fontSize = 11.sp)
                            Text(annotation.contents.ifBlank { annotation.author.ifBlank { "No content" } }, fontSize = 10.sp)
                            Text("${annotation.bounds.left.toInt()},${annotation.bounds.top.toInt()} → ${annotation.bounds.right.toInt()},${annotation.bounds.bottom.toInt()}", fontSize = 9.sp)
                        }
                        Button(onClick = { if (editor.deleteAnnotation(pageIndex, annotation.id)) onChanged("Annotation deleted") }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormPanel(document: Document, editor: EditorController, onChanged: (String) -> Unit) {
    val fields = document.formModel.fields
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("AcroForm fields: ${fields.size}", style = MaterialTheme.typography.titleMedium)
        if (fields.isEmpty()) Text("No supported form fields were discovered.", fontSize = 11.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(fields, key = { it.id }) { FormFieldEditor(it, editor, onChanged) } }
    }
}

@Composable
private fun FormFieldEditor(field: FormField, editor: EditorController, onChanged: (String) -> Unit) {
    var value by remember(field.id, field.value) { mutableStateOf(field.value) }
    Surface(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(field.name.ifBlank { field.fullyQualifiedName }, fontSize = 12.sp)
            Text("${field.type.name}${if (field.required) " • required" else ""}${if (field.readOnly) " • read-only" else ""}", fontSize = 9.sp)
            if (field.type == FormFieldType.Checkbox || field.type == FormFieldType.Radio) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(enabled = !field.readOnly, onClick = { if (editor.fillFormField(field.id, "Yes", listOf("Yes"))) onChanged("Form field updated") }) { Text("On") }
                    Button(enabled = !field.readOnly, onClick = { if (editor.fillFormField(field.id, "Off")) onChanged("Form field updated") }) { Text("Off") }
                }
            } else {
                OutlinedTextField(value = value, onValueChange = { value = it }, enabled = !field.readOnly, modifier = Modifier.fillMaxWidth(), singleLine = field.type != FormFieldType.MultilineText)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(enabled = !field.readOnly, onClick = { if (editor.fillFormField(field.id, value)) onChanged("Form field updated") }) { Text("Apply") }
                    Button(enabled = !field.readOnly, onClick = { if (editor.resetFormField(field.id)) { value = ""; onChanged("Form field reset") } }) { Text("Reset") }
                }
            }
            if (field.options.isNotEmpty()) Text("Options: ${field.options.joinToString { it.displayValue }}", fontSize = 9.sp)
        }
    }
}

@Composable
private fun MetadataPanel(document: Document, editor: EditorController, onChanged: (String) -> Unit) {
    var title by remember(document.metadata.title) { mutableStateOf(document.metadata.title) }
    var author by remember(document.metadata.author) { mutableStateOf(document.metadata.author) }
    var subject by remember(document.metadata.subject) { mutableStateOf(document.metadata.subject) }
    var keywords by remember(document.metadata.keywords) { mutableStateOf(document.metadata.keywords) }
    var creator by remember(document.metadata.creator) { mutableStateOf(document.metadata.creator) }
    var producer by remember(document.metadata.producer) { mutableStateOf(document.metadata.producer) }
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Document metadata", style = MaterialTheme.typography.titleMedium)
        val entries = listOf(title, author, subject, keywords, creator, producer)
        val setters = listOf<(String) -> Unit>({ title = it }, { author = it }, { subject = it }, { keywords = it }, { creator = it }, { producer = it })
        val labels = listOf("Title", "Author", "Subject", "Keywords", "Creator", "Producer")
        labels.indices.forEach { index -> OutlinedTextField(value = entries[index], onValueChange = setters[index], label = { Text(labels[index]) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        Button(onClick = { if (editor.setMetadata(DocumentMetadata(title, author, subject, keywords, creator, producer))) onChanged("Metadata updated") }, modifier = Modifier.fillMaxWidth()) { Text("Apply metadata") }
        Text("Metadata changes are command/history based and are ready for the save/serialization layer.", fontSize = 9.sp)
    }
}

@Composable
private fun BookmarkPanel(document: Document, pageIndex: Int, editor: EditorController, onChanged: (String) -> Unit, onPageChanged: (Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Bookmarks / outline", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("New bookmark") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(enabled = title.isNotBlank(), onClick = { if (editor.addBookmark(null, OutlineItem(title = title.trim(), pageIndex = pageIndex))) { title = ""; onChanged("Bookmark added") } }, modifier = Modifier.fillMaxWidth()) { Text("Add bookmark for page ${pageIndex + 1}") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { items(document.outline, key = { it.id }) { BookmarkRow(it, 0, editor, onChanged, onPageChanged) } }
    }
}

@Composable
private fun BookmarkRow(item: OutlineItem, depth: Int, editor: EditorController, onChanged: (String) -> Unit, onPageChanged: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = (depth * 12).dp)) {
        Surface(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))) {
            Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { item.pageIndex?.let(onPageChanged) }) { Text(item.title, fontSize = 11.sp); Text("Page ${item.pageIndex?.plus(1) ?: "—"}", fontSize = 9.sp) }
                Button(onClick = { if (editor.deleteBookmark(item.id)) onChanged("Bookmark deleted") }) { Text("Delete") }
            }
        }
        item.children.forEach { child -> BookmarkRow(child, depth + 1, editor, onChanged, onPageChanged) }
    }
}

@Composable
private fun AnnotationPageSummary(document: Document, pageIndex: Int, engine: PdfEngine) {
    val page = document.pages.getOrNull(pageIndex)
    val info = runCatching { engine.inspect() }.getOrNull()
    val pageInfo = info?.pages?.getOrNull(pageIndex)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Annotation preview — page ${pageIndex + 1}", style = MaterialTheme.typography.headlineSmall)
        Text("Model annotations: ${page?.annotations?.size ?: 0}")
        Text("Native PDF annotations: ${runCatching { engine.getAnnotations(pageIndex).size }.getOrDefault(0)}")
        if (pageInfo != null) AnnotationPreviewCanvas(engine, pageInfo, page?.annotations.orEmpty())
        Surface(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Annotation inventory", style = MaterialTheme.typography.titleMedium)
            page?.annotations.orEmpty().forEach { Text("• ${it.type.name}: ${it.contents.ifBlank { "untitled" }}", fontSize = 11.sp) }
            if (page?.annotations.isNullOrEmpty()) Text("No model annotations on this page.", fontSize = 11.sp)
        } }
    }
}

@Composable
private fun AnnotationPreviewCanvas(engine: PdfEngine, page: com.example.pdf_everything.pdf_engine.api.PdfPageInfo, annotations: List<PdfAnnotation>) {
    val width = 900
    val key = "phase5-preview-${page.documentVersion}-${page.pageId}-${width}"
    var bitmap by remember(key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(key) { mutableStateOf<String?>(null) }
    val scheduler = LocalPdfRenderScheduler.current
    LaunchedEffect(key, scheduler) {
        val viewport = com.example.pdf_everything.pdf_engine.api.RenderViewport(widthPx = width, scale = width / page.width, cacheKey = key)
        if (scheduler != null) {
            scheduler.request(
                page.index, viewport, RenderPriority.VISIBLE,
                onResult = { result -> bitmap = SkiaImage.makeFromEncoded(result.png).toComposeImageBitmap() },
                onError = { failed = it.message ?: "Render failed" }
            )
        } else {
            runCatching { withContext(Dispatchers.Default) { engine.renderPage(page.index, viewport) } }
                .onSuccess { result -> bitmap = SkiaImage.makeFromEncoded(result.png).toComposeImageBitmap() }
                .onFailure { failed = it.message ?: "Render failed" }
        }
    }
    Box(Modifier.fillMaxWidth().weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> {
                Image(bitmap!!, "PDF page with annotations", Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val sx = size.width / page.width.coerceAtLeast(1f)
                    val sy = size.height / page.height.coerceAtLeast(1f)
                    annotations.forEach { annotation ->
                        val left = annotation.bounds.left * sx
                        val top = annotation.bounds.top * sy
                        val w = annotation.bounds.width * sx
                        val h = annotation.bounds.height * sy
                        drawRect(color = androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.22f), topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h))
                        drawRect(color = MaterialTheme.colorScheme.primary, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }
                }
            }
            failed != null -> Text(failed!!, color = MaterialTheme.colorScheme.error)
            else -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}
