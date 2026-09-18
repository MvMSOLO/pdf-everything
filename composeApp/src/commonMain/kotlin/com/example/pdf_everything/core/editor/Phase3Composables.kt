package com.example.pdf_everything.core.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consume
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.pdf_everything.RenderMemoryCache
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.PdfObject
import com.example.pdf_everything.core.document.RectF
import com.example.pdf_everything.pdf_engine.api.PdfDocumentInfo
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.requestImageOpen

@Composable
fun Phase3EditorScreen(
    modifier: Modifier,
    engine: PdfEngine,
    info: PdfDocumentInfo,
    document: Document,
    editor: EditorController,
    selectedPage: Int,
    zoom: Float,
    cache: RenderMemoryCache,
    revision: Long,
    onChanged: () -> Unit,
    onPageChanged: (Int) -> Unit
) {
    // revision is deliberately observed so selection/edit mutations refresh the editor surface.
    revision.hashCode()
    val page = document.pages.getOrNull(selectedPage)
    if (page == null) return
    var insertTextMode by remember(selectedPage) { mutableStateOf(false) }
    val selected = editor.selectionModel.current
    val selectedObject = selected.selectedIds.firstOrNull()?.let { id -> page.objects.firstOrNull { it.id == id } }
    Row(modifier) {
        Column(Modifier.width(270.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Edit", style = MaterialTheme.typography.titleLarge)
            Text("Page ${selectedPage + 1} / ${info.pageCount}", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onPageChanged((selectedPage - 1).coerceAtLeast(0)) }) { Text("‹") }
                Button(onClick = { onPageChanged((selectedPage + 1).coerceAtMost(info.pageCount - 1)) }) { Text("›") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button(onClick = { if (editor.undo()) onChanged() }, enabled = editor.history.canUndo) { Text("Undo") }
                Button(onClick = { if (editor.redo()) onChanged() }, enabled = editor.history.canRedo) { Text("Redo") }
            }
            Text("Objects", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = true)) {
                items(page.objects, key = { it.id }) { obj ->
                    val active = obj.id in selected.selectedIds
                    Surface(
                        Modifier.fillMaxWidth().border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).clickable {
                            editor.selectionModel.selectObject(selectedPage, obj.id, obj.bounds, obj.editable, obj.editable)
                            onChanged()
                        },
                        tonalElevation = if (active) 3.dp else 0.dp
                    ) {
                        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(objectLabel(obj), modifier = Modifier.weight(1f))
                            Text(obj.id.takeLast(6), fontSize = 10.sp)
                        }
                    }
                }
            }
            Button(onClick = {
                val ids = page.objects.map { it.id }
                if (ids.isNotEmpty()) { editor.selectionModel.selectObjects(selectedPage, ids, unionBounds(page.objects), true, true); onChanged() }
            }, enabled = page.objects.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Select all objects") }
            Button(onClick = { insertTextMode = !insertTextMode }, modifier = Modifier.fillMaxWidth()) { Text(if (insertTextMode) "Click page to place text" else "Add text") }
            Button(onClick = { requestImageOpen { source ->
                if (editor.insertImage(selectedPage, sourceString(source), RectF(42f, 90f, 242f, 240f))) onChanged()
            } }, modifier = Modifier.fillMaxWidth()) { Text("Insert image") }
        }
        Divider(Modifier.fillMaxHeight().width(1.dp))
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextRangeTools(editor, selectedPage, page.text, onChanged)
            ObjectInspector(editor, selectedPage, selectedObject, onChanged)
            EditorPageCanvas(engine, info, document, selectedPage, zoom, cache, editor, insertTextMode, onChanged)
        }
    }
}

@Composable
private fun TextRangeTools(editor: EditorController, pageIndex: Int, text: String, onChanged: () -> Unit) {
    var start by remember(pageIndex, text.length) { mutableStateOf("0") }
    var end by remember(pageIndex, text.length) { mutableStateOf(text.length.toString()) }
    val selection = editor.selectionModel.current
    val selectedText = if (selection.type == com.example.pdf_everything.core.selection.SelectionType.TextRange) {
        val s = selection.textStart ?: 0
        val e = selection.textEnd ?: 0
        text.substring(s.coerceIn(0, text.length), e.coerceIn(0, text.length))
    } else ""
    Surface(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Text selection", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(value = start, onValueChange = { start = it.filter(Char::isDigit) }, modifier = Modifier.width(85.dp), singleLine = true, label = { Text("Start") })
            OutlinedTextField(value = end, onValueChange = { end = it.filter(Char::isDigit) }, modifier = Modifier.width(85.dp), singleLine = true, label = { Text("End") })
            Button(onClick = { editor.selectTextRange(pageIndex, start.toIntOrNull() ?: 0, end.toIntOrNull() ?: text.length); onChanged() }) { Text("Select") }
            Button(onClick = { if (editor.copySelection()) onChanged() }, enabled = selectedText.isNotEmpty()) { Text("Copy") }
            Button(onClick = { if (editor.cutSelection()) { editor.selectionModel.clear(); onChanged() } }, enabled = selectedText.isNotEmpty()) { Text("Cut") }
            Text(if (selectedText.isEmpty()) "No text selected" else "${selectedText.length} chars", fontSize = 11.sp)
        }
    }
}

@Composable
private fun ObjectInspector(editor: EditorController, pageIndex: Int, obj: PdfObject?, onChanged: () -> Unit) {
    when (obj) {
        is PdfObject.TextObject -> {
            var value by remember(obj.id, obj.text) { mutableStateOf(obj.text) }
            var fontSize by remember(obj.id, obj.fontSize) { mutableStateOf(obj.fontSize.toString()) }
            var fontName by remember(obj.id, obj.fontName) { mutableStateOf(obj.fontName ?: "") }
            var weight by remember(obj.id, obj.fontWeight) { mutableStateOf(obj.fontWeight) }
            var alignment by remember(obj.id, obj.alignment) { mutableStateOf(obj.alignment) }
            var color by remember(obj.id, obj.color) { mutableStateOf(obj.color ?: "#111111") }
            Surface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Text") })
                        Button(onClick = { if (editor.editText(pageIndex, obj.id, value)) onChanged() }) { Text("Apply") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = fontName, onValueChange = { fontName = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Font") })
                        OutlinedTextField(value = fontSize, onValueChange = { fontSize = it.filter { c -> c.isDigit() || c == '.' } }, modifier = Modifier.width(90.dp), singleLine = true, label = { Text("Size") })
                        OutlinedTextField(value = weight, onValueChange = { weight = it }, modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Weight") })
                        OutlinedTextField(value = alignment, onValueChange = { alignment = it }, modifier = Modifier.width(100.dp), singleLine = true, label = { Text("Align") })
                        OutlinedTextField(value = color, onValueChange = { color = it.take(20) }, modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Color") })
                        Button(onClick = {
                            val updated = obj.copy(fontName = fontName.ifBlank { null }, fontSize = fontSize.toFloatOrNull()?.coerceIn(4f, 144f) ?: obj.fontSize, fontWeight = weight, alignment = alignment, color = color)
                            if (editor.dispatch(com.example.pdf_everything.core.commands.UpdateObjectCommand(pageIndex, obj, updated))) onChanged()
                        }) { Text("Style") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { if (editor.moveObject(pageIndex, obj.id, -10f, 0f)) onChanged() }) { Text("←") }
                        Button(onClick = { if (editor.moveObject(pageIndex, obj.id, 10f, 0f)) onChanged() }) { Text("→") }
                        Button(onClick = { if (editor.moveObject(pageIndex, obj.id, 0f, -10f)) onChanged() }) { Text("↑") }
                        Button(onClick = { if (editor.moveObject(pageIndex, obj.id, 0f, 10f)) onChanged() }) { Text("↓") }
                        Button(onClick = { if (editor.resizeObject(pageIndex, obj.id, RectF(obj.bounds.left, obj.bounds.top, obj.bounds.right + 10f, obj.bounds.bottom + 6f))) onChanged() }) { Text("Resize") }
                        Button(onClick = { if (editor.rotateObject(pageIndex, obj.id, 90f)) onChanged() }) { Text("↻") }
                        Button(onClick = { if (editor.deleteSelection()) { editor.selectionModel.clear(); onChanged() } }) { Text("Delete") }
                    }
                }
            }
        }
        is PdfObject.ImageObject -> {
            var opacity by remember(obj.id, obj.opacity) { mutableStateOf((obj.opacity * 100f).toInt().toString()) }
            Surface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Image", modifier = Modifier.weight(1f))
                        Button(onClick = { requestImageOpen { source -> if (editor.replaceImage(pageIndex, obj.id, sourceString(source))) onChanged() } }) { Text("Replace") }
                        Button(onClick = { if (editor.moveObject(pageIndex, obj.id, -10f, 0f)) onChanged() }) { Text("←") }
                        Button(onClick = { if (editor.moveObject(pageIndex, obj.id, 10f, 0f)) onChanged() }) { Text("→") }
                        Button(onClick = { if (editor.resizeObject(pageIndex, obj.id, RectF(obj.bounds.left, obj.bounds.top, obj.bounds.right + 10f, obj.bounds.bottom + 10f))) onChanged() }) { Text("Resize") }
                        Button(onClick = { if (editor.rotateObject(pageIndex, obj.id, 90f)) onChanged() }) { Text("↻") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = opacity, onValueChange = { opacity = it.filter(Char::isDigit).take(3) }, modifier = Modifier.width(100.dp), singleLine = true, label = { Text("Opacity %") })
                        Button(onClick = { if (editor.setImageOpacity(pageIndex, obj.id, (opacity.toFloatOrNull() ?: 100f) / 100f)) onChanged() }) { Text("Apply") }
                        Button(onClick = { if (editor.flipImage(pageIndex, obj.id, true)) onChanged() }) { Text("Flip H") }
                        Button(onClick = { if (editor.flipImage(pageIndex, obj.id, false)) onChanged() }) { Text("Flip V") }
                        Button(onClick = { if (editor.cropImage(pageIndex, obj.id, RectF(obj.bounds.left, obj.bounds.top, obj.bounds.right - obj.bounds.width * 0.1f, obj.bounds.bottom - obj.bounds.height * 0.1f))) onChanged() }) { Text("Crop") }
                        Button(onClick = { if (editor.deleteSelection()) { editor.selectionModel.clear(); onChanged() } }) { Text("Delete") }
                    }
                }
            }
        }
        is PdfObject.VectorObject, is PdfObject.PathObject, is PdfObject.ShapeObject, is PdfObject.AnnotationObject -> {
            Surface(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(objectLabel(obj), modifier = Modifier.weight(1f))
                    Button(onClick = { if (editor.moveObject(pageIndex, obj.id, -10f, 0f)) onChanged() }) { Text("←") }
                    Button(onClick = { if (editor.moveObject(pageIndex, obj.id, 10f, 0f)) onChanged() }) { Text("→") }
                    Button(onClick = { if (editor.resizeObject(pageIndex, obj.id, RectF(obj.bounds.left, obj.bounds.top, obj.bounds.right + 10f, obj.bounds.bottom + 10f))) onChanged() }) { Text("Resize") }
                    Button(onClick = { if (editor.rotateObject(pageIndex, obj.id, 90f)) onChanged() }) { Text("↻") }
                    Button(onClick = { if (editor.deleteSelection()) { editor.selectionModel.clear(); onChanged() } }) { Text("Delete") }
                }
            }
        }
        else -> Text("Select an editable object")
    }
}

@Composable
private fun EditorPageCanvas(
    engine: PdfEngine,
    info: PdfDocumentInfo,
    document: Document,
    pageIndex: Int,
    zoom: Float,
    cache: RenderMemoryCache,
    editor: EditorController,
    insertTextMode: Boolean,
    onChanged: () -> Unit
) {
    val pageInfo = info.pages[pageIndex]
    val page = document.pages[pageIndex]
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val canvasWidth = (maxWidth.value * zoom).coerceAtMost(1100f).coerceAtLeast(320f)
        val canvasHeight = canvasWidth * pageInfo.height / pageInfo.width.coerceAtLeast(1f)
        Box(Modifier.width(canvasWidth.dp).height(canvasHeight.dp).pointerInput(insertTextMode, pageIndex) {
            if (insertTextMode) detectTapGestures { offset ->
                val scaleForPage = canvasWidth / pageInfo.width.coerceAtLeast(1f)
                val x = (offset.x / scaleForPage).coerceIn(0f, pageInfo.width - 24f)
                val y = (offset.y / scaleForPage).coerceIn(0f, pageInfo.height - 24f)
                if (editor.insertText(pageIndex, "Text", RectF(x, y, minOf(x + 220f, pageInfo.width - 8f), minOf(y + 36f, pageInfo.height - 8f)), null, 14f, "#111111")) onChanged()
            }
        }) {
            PdfPageImageForEditor(engine, pageInfo, canvasWidth.toInt(), cache)
            val scale = canvasWidth / pageInfo.width.coerceAtLeast(1f)
            page.objects.forEach { obj ->
                val active = obj.id in editor.selectionModel.current.selectedIds
                val left = obj.bounds.left * scale
                val top = obj.bounds.top * scale
                val width = obj.bounds.width * scale
                val height = obj.bounds.height * scale
                Box(
                    Modifier.offset(left.dp, top.dp).width(width.coerceAtLeast(8f).dp).height(height.coerceAtLeast(8f).dp)
                        .border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(alpha = 0.65f), RoundedCornerShape(2.dp))
                        .pointerInput(obj.id, pageIndex, scale) {
                            detectDragGestures(
                                onDragStart = { editor.selectionModel.selectObject(pageIndex, obj.id, obj.bounds, obj.editable, obj.editable); onChanged() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (editor.moveObject(pageIndex, obj.id, dragAmount.x / scale, dragAmount.y / scale)) onChanged()
                                }
                            )
                        }
                        .clickable {
                            editor.selectionModel.selectObject(pageIndex, obj.id, obj.bounds, obj.editable, obj.editable)
                            onChanged()
                        }, contentAlignment = Alignment.TopStart
                ) {
                    if (obj is PdfObject.TextObject) Text(obj.text, fontSize = (obj.fontSize * scale).coerceIn(6f, 34f).sp, maxLines = 5)
                    if (obj is PdfObject.ImageObject) Text("IMAGE", modifier = Modifier.padding(3.dp), fontSize = 9.sp)
                    if (active) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(14.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                .pointerInput(obj.id, pageIndex, scale) {
                                    detectDragGestures { change, amount ->
                                        change.consume()
                                        val newRight = (obj.bounds.right + amount.x / scale).coerceAtLeast(obj.bounds.left + 8f)
                                        val newBottom = (obj.bounds.bottom + amount.y / scale).coerceAtLeast(obj.bounds.top + 8f)
                                        if (editor.resizeObject(pageIndex, obj.id, obj.bounds.copy(right = newRight, bottom = newBottom))) onChanged()
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
private fun PdfPageImageForEditor(engine: PdfEngine, page: com.example.pdf_everything.pdf_engine.api.PdfPageInfo, width: Int, cache: RenderMemoryCache) {
    com.example.pdf_everything.core.editor.Phase3RenderedImage(engine, page, width, cache)
}

private fun objectLabel(obj: PdfObject): String = when (obj) {
    is PdfObject.TextObject -> "Text: ${obj.text.take(28)}"
    is PdfObject.ImageObject -> "Image"
    is PdfObject.VectorObject -> "Vector"
    is PdfObject.PathObject -> "Path"
    is PdfObject.ShapeObject -> "Shape: ${obj.shape}"
    is PdfObject.AnnotationObject -> "Annotation: ${obj.subtype}"
    is PdfObject.FormWidgetObject -> "Form: ${obj.fieldType}"
    is PdfObject.UnknownObject -> "Unsupported"
}

private fun unionBounds(objects: List<PdfObject>): RectF {
    val left = objects.minOf { it.bounds.left }
    val top = objects.minOf { it.bounds.top }
    val right = objects.maxOf { it.bounds.right }
    val bottom = objects.maxOf { it.bounds.bottom }
    return RectF(left, top, right, bottom)
}

private fun sourceString(source: com.example.pdf_everything.core.document.DocumentSource): String = when (source) {
    is com.example.pdf_everything.core.document.DocumentSource.FilePath -> source.path
    is com.example.pdf_everything.core.document.DocumentSource.ContentUri -> source.uri
}
