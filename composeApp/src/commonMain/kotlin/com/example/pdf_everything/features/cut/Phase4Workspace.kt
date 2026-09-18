package com.example.pdf_everything.features.cut

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_everything.RenderMemoryCache
import com.example.pdf_everything.isDesktop
import com.example.pdf_everything.core.commands.buildSplitPlan
import com.example.pdf_everything.core.commands.buildSplitPlanFromBreaks
import com.example.pdf_everything.core.commands.cutPageIntoSections
import com.example.pdf_everything.core.editor.EditorController
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.RectF
import com.example.pdf_everything.pdf_engine.api.PdfDocumentInfo
import com.example.pdf_everything.pdf_engine.api.LocalPdfRenderScheduler
import com.example.pdf_everything.pdf_engine.api.RenderPriority
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.pdf_engine.api.RenderViewport
import org.jetbrains.skia.Image
import org.jetbrains.skia.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Phase4Workspace(
    document: Document,
    engine: PdfEngine,
    selectedPage: Int,
    cache: RenderMemoryCache,
    editor: EditorController,
    onChanged: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onImportPdf: ((replacePageIndex: Int?) -> Unit)?,
    startTool: CutTool = CutTool.Crop
) {
    var tool by remember(startTool) { mutableStateOf(startTool) }
    var crop by remember(document.id, selectedPage) { mutableStateOf(defaultCrop(document.pages[selectedPage])) }
    var margin by remember { mutableFloatStateOf(12f) }
    var deleteArea by remember(document.id, selectedPage) { mutableStateOf(defaultDeleteArea(document.pages[selectedPage])) }
    var lockAspect by remember(document.id, selectedPage) { mutableStateOf(false) }
    var includeBackgroundObjects by remember { mutableStateOf(true) }
    var splitN by remember { mutableStateOf("2") }
    var splitBreaks by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Int>() }
    var columns by remember { mutableIntStateOf(2) }

    LaunchedEffect(selectedPage, document.id) {
        crop = defaultCrop(document.pages.getOrNull(selectedPage) ?: return@LaunchedEffect)
        deleteArea = defaultDeleteArea(document.pages.getOrNull(selectedPage) ?: return@LaunchedEffect)
        selected.removeAll { it !in document.pages.indices }
        if (selected.isEmpty() && selectedPage in document.pages.indices) selected.add(selectedPage)
    }

    Scaffold { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val compact = maxWidth.value < 850f
            if (compact) {
                Column(Modifier.fillMaxSize()) {
                    CutTopBar(tool, selectedPage, { tool = it }, onImportPdf)
                    HorizontalDivider()
                    CutPagePreview(document, selectedPage, crop, deleteArea, tool, engine, cache, lockAspect, Modifier.weight(1f).fillMaxWidth(), onCropChanged = { crop = it }, onDeleteAreaChanged = { deleteArea = it })
                    HorizontalDivider()
                    CutControls(tool, document, selectedPage, crop, { crop = it }, lockAspect, { lockAspect = it }, margin, { margin = it }, includeBackgroundObjects, { includeBackgroundObjects = it }, deleteArea, { deleteArea = it }, splitN, { splitN = it }, splitBreaks, { splitBreaks = it }, selected, columns, { columns = it }, editor, onChanged, onPageChanged)
                    HorizontalDivider()
                    Text("Page Organizer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    OrganizerPanel(document, engine, cache, selectedPage, selected, editor, onChanged, onPageChanged, onImportPdf, Modifier.height(200.dp))
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(230.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CUT / ORGANIZE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        CutTool.entries.forEach { candidate ->
                            FilterChip(selected = tool == candidate, onClick = { tool = candidate }, label = { Text(toolLabel(candidate)) }, modifier = Modifier.fillMaxWidth())
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Button(onClick = { selected.clear(); selected.add(selectedPage) }, modifier = Modifier.fillMaxWidth()) { Text("Select current") }
                        OutlinedButton(onClick = { selected.clear(); selected.addAll(document.pages.indices) }, modifier = Modifier.fillMaxWidth()) { Text("Select all pages") }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        CutTopBar(tool, selectedPage, { tool = it }, onImportPdf)
                        HorizontalDivider()
                        CutPagePreview(document, selectedPage, crop, deleteArea, tool, engine, cache, lockAspect, Modifier.weight(1f).fillMaxWidth(), onCropChanged = { crop = it }, onDeleteAreaChanged = { deleteArea = it })
                        HorizontalDivider()
                        CutControls(tool, document, selectedPage, crop, { crop = it }, lockAspect, { lockAspect = it }, margin, { margin = it }, includeBackgroundObjects, { includeBackgroundObjects = it }, deleteArea, { deleteArea = it }, splitN, { splitN = it }, splitBreaks, { splitBreaks = it }, selected, columns, { columns = it }, editor, onChanged, onPageChanged)
                    }
                    Column(Modifier.width(300.dp).fillMaxHeight().padding(12.dp)) {
                        Text("Page Organizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OrganizerPanel(document, engine, cache, selectedPage, selected, editor, onChanged, onPageChanged, onImportPdf)
                    }
                }
            }
        }
    }
}

@Composable
private fun CutTopBar(tool: CutTool, selectedPage: Int, onTool: (CutTool) -> Unit, onImportPdf: ((Int?) -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(58.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(toolLabel(tool), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        CutTool.entries.forEach { candidate ->
            OutlinedButton(onClick = { onTool(candidate) }) { Text(toolLabel(candidate), fontSize = 11.sp) }
        }
        if (onImportPdf != null) {
            Button(onClick = { onImportPdf(null) }) { Text("Merge PDF") }
            if (tool == CutTool.Extract) OutlinedButton(onClick = { onImportPdf(selectedPage) }) { Text("Replace page") }
        }
    }
}

@Composable
private fun CutPagePreview(
    document: Document,
    selectedPage: Int,
    crop: RectF,
    deleteArea: RectF,
    tool: CutTool,
    engine: PdfEngine,
    cache: RenderMemoryCache,
    lockAspect: Boolean,
    modifier: Modifier,
    onCropChanged: (RectF) -> Unit,
    onDeleteAreaChanged: (RectF) -> Unit
) {
    val page = document.pages.getOrNull(selectedPage) ?: return
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val pageW = page.boxes.media.width.coerceAtLeast(1f)
        val pageH = page.boxes.media.height.coerceAtLeast(1f)
        val viewW = (maxWidth.value - 36f).coerceAtLeast(240f)
        val viewH = (maxHeight.value - 36f).coerceAtLeast(240f)
        val scale = minOf(viewW / pageW, viewH / pageH)
        val w = pageW * scale
        val h = pageH * scale
        Box(Modifier.width(w.dp).height(h.dp).border(1.dp, MaterialTheme.colorScheme.outline)) {
            SourcePagePreview(engine, page, cache, w.toInt().coerceIn(240, 2400), Modifier.fillMaxSize())
            if (tool == CutTool.Crop || tool == CutTool.Trim) {
                CropOverlay(page.boxes.media, crop, scale, lockAspect, onCropChanged)
            } else if (tool == CutTool.DeleteArea) {
                AreaOverlay(page.boxes.media, deleteArea, scale, onDeleteAreaChanged)
            }
        }
    }
}

@Composable
private fun CropOverlay(media: RectF, rect: RectF, scale: Float, lockAspect: Boolean, onChanged: (RectF) -> Unit) {
    val x = rect.left * scale
    val y = rect.top * scale
    val w = rect.width * scale
    val h = rect.height * scale
    fun clampRect(candidate: RectF): RectF = RectF(
        candidate.left.coerceIn(media.left, media.right - 12f),
        candidate.top.coerceIn(media.top, media.bottom - 12f),
        candidate.right.coerceIn(media.left + 12f, media.right),
        candidate.bottom.coerceIn(media.top + 12f, media.bottom)
    )
    fun resize(handle: String, dx: Float, dy: Float): RectF {
        val aspect = (rect.width / rect.height).coerceAtLeast(0.01f)
        if (!lockAspect) return when (handle) {
            "tl" -> rect.copy(left = rect.left + dx, top = rect.top + dy)
            "tr" -> rect.copy(right = rect.right + dx, top = rect.top + dy)
            "bl" -> rect.copy(left = rect.left + dx, bottom = rect.bottom + dy)
            "br" -> rect.copy(right = rect.right + dx, bottom = rect.bottom + dy)
            "top" -> rect.copy(top = rect.top + dy)
            "right" -> rect.copy(right = rect.right + dx)
            "bottom" -> rect.copy(bottom = rect.bottom + dy)
            else -> rect.copy(left = rect.left + dx)
        }
        return when (handle) {
            "left", "right" -> {
                val newW = if (handle == "left") rect.width - dx else rect.width + dx
                val h2 = newW.coerceAtLeast(12f) / aspect
                if (handle == "left") RectF(rect.right - newW, rect.bottom - h2, rect.right, rect.bottom)
                else RectF(rect.left, rect.bottom - h2, rect.left + newW, rect.bottom)
            }
            "top", "bottom" -> {
                val newH = if (handle == "top") rect.height - dy else rect.height + dy
                val w2 = newH.coerceAtLeast(12f) * aspect
                if (handle == "top") RectF(rect.right - w2, rect.bottom - newH, rect.right, rect.bottom)
                else RectF(rect.left, rect.top, rect.left + w2, rect.top + newH)
            }
            "tl" -> { val newW = (rect.width - dx).coerceAtLeast(12f); val newH = newW / aspect; RectF(rect.right - newW, rect.bottom - newH, rect.right, rect.bottom) }
            "tr" -> { val newW = (rect.width + dx).coerceAtLeast(12f); val newH = newW / aspect; RectF(rect.left, rect.bottom - newH, rect.left + newW, rect.bottom) }
            "bl" -> { val newW = (rect.width - dx).coerceAtLeast(12f); val newH = newW / aspect; RectF(rect.right - newW, rect.top, rect.right, rect.top + newH) }
            else -> { val newW = (rect.width + dx).coerceAtLeast(12f); val newH = newW / aspect; RectF(rect.left, rect.top, rect.left + newW, rect.top + newH) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.42f))
            drawRect(MaterialTheme.colorScheme.surface.copy(alpha = 0.02f), Offset(x, y), Size(w, h))
            drawRect(MaterialTheme.colorScheme.primary, Offset(x, y), Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
        }
        Box(
            Modifier.offset((x + w / 2f - 18f).toDpSafe(), (y + h / 2f - 18f).toDpSafe()).size(36.dp)
                .pointerInput(rect) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        val dx = amount.x / scale
                        val dy = amount.y / scale
                        val nx = (rect.left + dx).coerceIn(media.left, media.right - rect.width)
                        val ny = (rect.top + dy).coerceIn(media.top, media.bottom - rect.height)
                        onChanged(RectF(nx, ny, nx + rect.width, ny + rect.height))
                    }
                }
        )
        listOf(
            Offset(x, y) to "tl", Offset(x + w, y) to "tr", Offset(x, y + h) to "bl", Offset(x + w, y + h) to "br",
            Offset(x + w / 2f, y) to "top", Offset(x + w, y + h / 2f) to "right", Offset(x + w / 2f, y + h) to "bottom", Offset(x, y + h / 2f) to "left"
        ).forEach { (center, handle) ->
            val hs = if (handle.length == 2) 16.dp else 12.dp
            Box(
                Modifier.offset((center.x - hs.value / 2f).toDpSafe(), (center.y - hs.value / 2f).toDpSafe()).size(hs)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .pointerInput(rect, handle) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            onChanged(clampRect(resize(handle, amount.x / scale, amount.y / scale)))
                        }
                    }
            )
        }
    }
}

@Composable
private fun AreaOverlay(media: RectF, rect: RectF, scale: Float, onChanged: (RectF) -> Unit) {
    val x = rect.left * scale
    val y = rect.top * scale
    val w = rect.width * scale
    val h = rect.height * scale
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawRect(MaterialTheme.colorScheme.error.copy(alpha = 0.25f), Offset(x, y), Size(w, h)); drawRect(MaterialTheme.colorScheme.error, Offset(x, y), Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)) }
        Box(Modifier.offset((x + w - 9f).toDpSafe(), (y + h - 9f).toDpSafe()).size(18.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(5.dp)).pointerInput(rect) {
            detectDragGestures { change, amount -> change.consume(); onChanged(rect.copy(right = (rect.right + amount.x / scale).coerceIn(rect.left + 8f, media.right), bottom = (rect.bottom + amount.y / scale).coerceIn(rect.top + 8f, media.bottom))) }
        })
    }
}

@Composable
private fun CutControls(
    tool: CutTool,
    document: Document,
    selectedPage: Int,
    crop: RectF,
    onCrop: (RectF) -> Unit,
    lockAspect: Boolean,
    onLockAspect: (Boolean) -> Unit,
    margin: Float,
    onMargin: (Float) -> Unit,
    includeBackgroundObjects: Boolean,
    onIncludeBackgroundObjects: (Boolean) -> Unit,
    deleteArea: RectF,
    onDeleteArea: (RectF) -> Unit,
    splitN: String,
    onSplitN: (String) -> Unit,
    splitBreaks: String,
    onSplitBreaks: (String) -> Unit,
    selected: MutableList<Int>,
    columns: Int,
    onColumns: (Int) -> Unit,
    editor: EditorController,
    onChanged: () -> Unit,
    onPageChanged: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (tool) {
            CutTool.Crop -> {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onCrop(defaultCrop(document.pages[selectedPage])) }) { Text("Reset") }
                    Button(onClick = { onCrop(presetRect(document.pages[selectedPage], CropPreset.A4)) }) { Text("A4") }
                    Button(onClick = { onCrop(presetRect(document.pages[selectedPage], CropPreset.A5)) }) { Text("A5") }
                    Button(onClick = { onCrop(presetRect(document.pages[selectedPage], CropPreset.Letter)) }) { Text("Letter") }
                    Button(onClick = { onCrop(presetRect(document.pages[selectedPage], CropPreset.Portrait)) }) { Text("Portrait") }
                    Button(onClick = { onCrop(presetRect(document.pages[selectedPage], CropPreset.Landscape)) }) { Text("Landscape") }
                    Button(onClick = { if (editor.cropPages(if (selected.isEmpty()) listOf(selectedPage) else selected.toList(), crop)) onChanged() }) { Text("Apply Crop") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(checked = lockAspect, onCheckedChange = onLockAspect)
                    Text("Lock aspect ratio")
                    Text("Margins:")
                    Button(onClick = { onCrop(insetRect(crop, document.pages[selectedPage].boxes.media, 12f)) }) { Text("12") }
                    Button(onClick = { onCrop(insetRect(crop, document.pages[selectedPage].boxes.media, 24f)) }) { Text("24") }
                    Button(onClick = { onCrop(insetRect(crop, document.pages[selectedPage].boxes.media, 36f)) }) { Text("36") }
                }
                NumericCropFields(crop, onCrop)
                Text("Drag center/edge/corner handles. Outside the crop frame is preview-only until Apply.", fontSize = 11.sp)
            }
            CutTool.Trim -> {
                Text("Content trim", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeBackgroundObjects, onCheckedChange = onIncludeBackgroundObjects)
                    Text("Include background objects")
                }
                Slider(value = margin, onValueChange = onMargin, valueRange = 0f..72f, modifier = Modifier.fillMaxWidth())
                Text("Margin: ${margin.toInt()} pt")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val page = document.pages[selectedPage]
                        pageContentBounds(page, includeBackgroundObjects, margin)?.let(onCrop)
                    }) { Text("Auto detect") }
                    Button(onClick = { if (editor.trimPages(if (selected.isEmpty()) listOf(selectedPage) else selected.toList(), crop)) onChanged() }) { Text("Apply Trim") }
                    OutlinedButton(onClick = { onCrop(defaultCrop(document.pages[selectedPage])) }) { Text("Reset") }
                }
                Text("Adjust the detected frame manually before Apply.", fontSize = 11.sp)
            }
            CutTool.DeleteArea -> {
                NumericCropFields(deleteArea, onDeleteArea)
                Button(onClick = { if (editor.deleteArea(selectedPage, deleteArea)) onChanged() }) { Text("Delete Selected Area") }
                Text("This removes structured editable objects intersecting the area; it is not a secure redaction.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            CutTool.Split -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = splitN, onValueChange = { onSplitN(it.filter(Char::isDigit).take(4)) }, label = { Text("Pages per part") }, modifier = Modifier.width(150.dp), singleLine = true)
                    val autoPlan = buildSplitPlan(document.pageCount, splitN.toIntOrNull() ?: 0)
                    Text(if (autoPlan.isEmpty()) "Enter N" else "${autoPlan.size} parts: ${autoPlan.joinToString { "${it.first + 1}-${it.last + 1}" }}")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = splitBreaks, onValueChange = { onSplitBreaks(it.filter { ch -> ch.isDigit() || ch == ',' || ch == ' ' }.take(80)) }, label = { Text("Custom break pages") }, placeholder = { Text("e.g. 3, 5, 8") }, modifier = Modifier.width(240.dp), singleLine = true)
                    val breaks = splitBreaks.split(',').mapNotNull { it.trim().toIntOrNull() }.map { it - 1 }.filter { it in 0 until document.pageCount }.distinct().sorted()
                    val customPlan = buildSplitPlanFromBreaks(document.pageCount, breaks)
                    Text(if (breaks.isEmpty()) "Optional" else "${customPlan.size} custom parts")
                    OutlinedButton(onClick = {
                        val selectedBreaks = selected.distinct().sorted().dropLast(1)
                        onSplitBreaks(selectedBreaks.joinToString(",") { (it + 1).toString() })
                    }, enabled = selected.size > 1) { Text("Use selected") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Page sections:")
                    Button(onClick = { onColumns(2) }) { Text("2×1") }
                    Button(onClick = { onColumns(4) }) { Text("4×1") }
                    Button(onClick = { onColumns(6) }) { Text("6×1") }
                    Button(onClick = { onColumns(4) }) { Text("2×2") }
                    Text("${columns} panels", fontSize = 11.sp)
                }
                val sectionRows = if (columns == 4) 2 else 1
                val sectionDoc = cutPageIntoSections(document, selectedPage, if (columns == 4) 2 else columns, sectionRows)
                Text("Section-cut preview: ${sectionDoc.pageCount} panel(s), row-major reading order.", fontSize = 11.sp)
                Text("Split and section plans are preview operations; the current document is untouched until an Apply/Export action is available in the save pipeline.", fontSize = 11.sp)
            }
            CutTool.Extract -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (!selected.contains(selectedPage)) selected.add(selectedPage) }) { Text("Select current") }
                    Button(onClick = { selected.clear(); selected.addAll(document.pages.indices) }) { Text("All") }
                }
                val ordered = selected.distinct().sorted()
                val extracted = editor.extractDocument(ordered)
                Text(if (ordered.isEmpty()) "No pages selected" else "Preview: ${ordered.size} page(s) -> ${extracted?.name ?: "extract.pdf"}")
                Text("Extract keeps the original document intact until a later export/save action.", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NumericCropFields(rect: RectF, onChanged: (RectF) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val fields = listOf("X" to rect.left, "Y" to rect.top, "W" to rect.width, "H" to rect.height)
        fields.forEach { (label, value) ->
            var text by remember(label, rect) { mutableStateOf(value.toInt().toString()) }
            OutlinedTextField(value = text, onValueChange = { new ->
                text = new.filter { it.isDigit() || it == '.' }
                val n = text.toFloatOrNull() ?: return@OutlinedTextField
                val next = when (label) {
                    "X" -> rect.copy(left = n, right = n + rect.width)
                    "Y" -> rect.copy(top = n, bottom = n + rect.height)
                    "W" -> rect.copy(right = rect.left + n.coerceAtLeast(1f))
                    else -> rect.copy(bottom = rect.top + n.coerceAtLeast(1f))
                }
                onChanged(next)
            }, label = { Text(label) }, singleLine = true, modifier = Modifier.width(92.dp))
        }
    }
}

@Composable
private fun OrganizerPanel(document: Document, engine: PdfEngine, cache: RenderMemoryCache, selectedPage: Int, selected: MutableList<Int>, editor: EditorController, onChanged: () -> Unit, onPageChanged: (Int) -> Unit, onImportPdf: ((Int?) -> Unit)?, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { selected.clear(); selected.add(selectedPage) }) { Text("Sel") }
        Button(onClick = { if (editor.insertBlankPage(document.pageCount, document.pages.firstOrNull()?.boxes?.media?.width ?: 595f, document.pages.firstOrNull()?.boxes?.media?.height ?: 842f)) onChanged() }) { Text("Blank") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { if (selected.isNotEmpty() && editor.duplicatePages(selected)) onChanged() }) { Text("Duplicate") }
        Button(onClick = {
            val nextPage = (selected.minOrNull() ?: selectedPage).coerceAtMost((document.pageCount - selected.size - 1).coerceAtLeast(0))
            if (selected.isNotEmpty() && editor.deletePages(selected.toList())) {
                selected.clear()
                onPageChanged(nextPage)
                onChanged()
            }
        }) { Text("Delete") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { if (selected.isNotEmpty() && editor.rotatePages(selected)) onChanged() }) { Text("↻") }
        Button(onClick = { if (selected.isNotEmpty() && editor.rotatePages(selected, false)) onChanged() }) { Text("↺") }
        onImportPdf?.let { callback -> Button(onClick = { callback(null) }) { Text("Merge") } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { selected.clear(); selected.addAll(document.pages.indices.filter { it % 2 == 0 }) }) { Text("Odd") }
        OutlinedButton(onClick = { selected.clear(); selected.addAll(document.pages.indices.filter { it % 2 == 1 }) }) { Text("Even") }
        OutlinedButton(onClick = {
            val selectedPages = selected.distinct().sorted()
            if (selectedPages.size > 1) {
                val positions = selectedPages.mapIndexed { slot, index -> index to selectedPages.reversed()[slot] }.toMap()
                val order = document.pages.indices.map { index -> positions[index] ?: index }
                if (editor.reorderPages(order)) { selected.clear(); selected.addAll(selectedPages.reversed()); onChanged() }
            }
        }) { Text("Reverse") }
    }
    var pageLabel by remember(selectedPage, document.id) { mutableStateOf(document.pages.getOrNull(selectedPage)?.label.orEmpty()) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = pageLabel, onValueChange = { pageLabel = it.take(32) }, label = { Text("Page label") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { if (editor.setPageLabel(selectedPage, pageLabel)) onChanged() }) { Text("Set") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = {
            val moving = selected.distinct().sorted()
            val rest = document.pages.indices.filterNot { it in moving }
            if (moving.isNotEmpty() && moving != document.pages.indices.toList()) {
                val order = moving + rest
                if (editor.reorderPages(order)) { selected.clear(); selected.addAll(0 until moving.size); onPageChanged(0); onChanged() }
            }
        }) { Text("First") }
        Button(onClick = {
            val moving = selected.distinct().sorted()
            val rest = document.pages.indices.filterNot { it in moving }
            if (moving.isNotEmpty() && moving != document.pages.indices.toList()) {
                val order = rest + moving
                if (editor.reorderPages(order)) { val start = document.pageCount - moving.size; selected.clear(); selected.addAll(start until document.pageCount); onPageChanged(start); onChanged() }
            }
        }) { Text("Last") }
        onImportPdf?.let { callback ->
            OutlinedButton(onClick = { callback(selected.firstOrNull()) }, enabled = selected.size == 1) { Text("Replace") }
        }
    }
    LazyColumn(modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(document.pages, key = { _, page -> page.id }) { index, page ->
            OrganizerCard(page, index == selectedPage, selected.contains(index), engine, cache, onSelect = {
                if (selected.contains(index)) selected.remove(index) else selected.add(index)
                onPageChanged(index)
            }, onDragBy = { direction ->
                val target = (index + direction).coerceIn(0, document.pageCount - 1)
                if (target != index) {
                    val order = document.pages.indices.toMutableList().also { ids ->
                        val moved = ids.removeAt(index)
                        ids.add(target, moved)
                    }
                    if (editor.reorderPages(order)) { selected.clear(); selected.add(target); onPageChanged(target); onChanged() }
                }
            })
        }
    }
}

@Composable
private fun OrganizerCard(page: Page, active: Boolean, selected: Boolean, engine: PdfEngine, cache: RenderMemoryCache, onSelect: () -> Unit, onDragBy: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth().pointerInput(page.id, isDesktop) {
        if (isDesktop) {
            detectDragGestures { change, amount ->
                change.consume()
                if (kotlin.math.abs(amount.y) > 18f) onDragBy(if (amount.y > 0f) 1 else -1)
            }
        } else {
            detectDragGesturesAfterLongPress { change, amount ->
                change.consume()
                if (kotlin.math.abs(amount.y) > 18f) onDragBy(if (amount.y > 0f) 1 else -1)
            }
        }
    }.clickable(onClick = onSelect).border(if (active || selected) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${page.index + 1}", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
            val sourceIndex = page.sourcePageIndex
            if (sourceIndex != null && page.sourceDocumentId == "source") {
                Thumbnail(engine, sourceIndex, page.boxes.media.width, page.boxes.media.height, cache, Modifier.width(70.dp).height(90.dp))
            } else {
                Box(Modifier.width(70.dp).height(90.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("NEW") }
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(if (selected) "Selected" else "Page ${page.index + 1}", fontSize = 12.sp)
                Text(page.label ?: "${page.boxes.media.width.toInt()} × ${page.boxes.media.height.toInt()} pt", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun Thumbnail(engine: PdfEngine, pageIndex: Int, width: Float, height: Float, cache: RenderMemoryCache, modifier: Modifier) {
    val key = "phase4-thumb-$pageIndex-${width.toInt()}"
    var bytes by remember(key) { mutableStateOf(cache.thumbnail(key)?.png) }
    LaunchedEffect(key) {
        if (bytes == null) {
            val scheduler = LocalPdfRenderScheduler.current
            val viewport = RenderViewport(240, cacheKey = key)
            if (scheduler != null) {
                scheduler.request(pageIndex, viewport, RenderPriority.THUMBNAIL, thumbnail = true, onResult = { bytes = it.png })
            } else {
                runCatching { withContext(Dispatchers.Default) { engine.renderPage(pageIndex, viewport) } }
                    .onSuccess { cache.putThumbnail(key, it); bytes = it.png }
            }
        }
    }
    Box(modifier.background(Color.LightGray)) {
        bytes?.let { Image.makeFromEncoded(it).toComposeImageBitmap().let { bitmap -> androidx.compose.foundation.Image(bitmap, "Page ${pageIndex + 1}", Modifier.fillMaxSize()) } }
    }
}

@Composable
private fun SourcePagePreview(engine: PdfEngine, page: Page, cache: RenderMemoryCache, width: Int, modifier: Modifier) {
    val sourceIndex = page.sourcePageIndex ?: page.index
    if (page.sourceDocumentId == null || page.sourceDocumentId == "source") {
        Thumbnail(engine, sourceIndex, page.boxes.media.width, page.boxes.media.height, cache, modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("Merged page preview") }
    }
}

private fun defaultCrop(page: Page): RectF = page.boxes.crop
private fun defaultDeleteArea(page: Page): RectF {
    val m = page.boxes.media
    return RectF(m.left + m.width * .15f, m.top + m.height * .15f, m.right - m.width * .15f, m.bottom - m.height * .15f)
}
private fun presetRect(page: Page, preset: CropPreset): RectF {
    val media = page.boxes.media
    return when (preset) {
        CropPreset.Original, CropPreset.Free -> media
        CropPreset.A4 -> pageRectToAspectFit(page, 210f / 297f, false)
        CropPreset.A5 -> pageRectToAspectFit(page, 148f / 210f, false)
        CropPreset.Letter -> pageRectToAspectFit(page, 8.5f / 11f, false)
        CropPreset.Legal -> pageRectToAspectFit(page, 8.5f / 14f, false)
        CropPreset.Portrait -> pageRectToAspectFit(page, media.width / media.height, false)
        CropPreset.Landscape -> pageRectToAspectFit(page, media.height / media.width, true)
    }
}
private fun toolLabel(tool: CutTool): String = when (tool) {
    CutTool.Crop -> "Crop Page"
    CutTool.Trim -> "Content Trim"
    CutTool.Split -> "Split"
    CutTool.Extract -> "Extract"
    CutTool.DeleteArea -> "Delete Area"
}
private fun Float.toDpSafe() = (this).coerceAtLeast(-10000f).dp

private fun insetRect(rect: RectF, media: RectF, margin: Float): RectF {
    val dx = minOf(margin, rect.width / 2f - 1f).coerceAtLeast(0f)
    val dy = minOf(margin, rect.height / 2f - 1f).coerceAtLeast(0f)
    return RectF(
        (rect.left + dx).coerceIn(media.left, media.right - 1f),
        (rect.top + dy).coerceIn(media.top, media.bottom - 1f),
        (rect.right - dx).coerceIn(media.left + 1f, media.right),
        (rect.bottom - dy).coerceIn(media.top + 1f, media.bottom)
    )
}
