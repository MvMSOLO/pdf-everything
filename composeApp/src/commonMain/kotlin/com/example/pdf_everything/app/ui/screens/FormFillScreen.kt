package com.example.pdf_everything.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.core.document.FormField
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.core.services.EngineResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillScreen(
    appState: AppState,
    router: AppRouter,
    modifier: Modifier = Modifier
) {
    val currentDoc = appState.currentDocument
    val scope = rememberCoroutineScope()

    var formFields by remember { mutableStateOf<List<FormField>>(emptyList()) }
    var fieldValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isFormFlattened by remember { mutableStateOf(false) }

    LaunchedEffect(currentDoc) {
        val doc = currentDoc
        if (doc != null) {
            runCatching {
                val res = appState.pdfEngine.getFormFields(doc.documentId)
                if (res is EngineResult.Success) {
                    val fields = res.value
                    formFields = fields
                    fieldValues = fields.associate { it.fieldName to (it.value ?: "") }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Form Filler: ${currentDoc?.name ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { router.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val doc = currentDoc
                            if (doc != null) {
                                scope.launch {
                                    runCatching {
                                        fieldValues.forEach { (fieldName, value) ->
                                            appState.pdfEngine.setFormFieldValue(doc.documentId, fieldName, value)
                                        }
                                        appState.documentFileService.saveDocument(doc.documentId)
                                        statusMessage = "Form values saved successfully"
                                    }.onFailure {
                                        statusMessage = "Error saving form: ${it.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save Form")
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
                Text("No document loaded.", fontSize = 18.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                statusMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Interactive Form Fields (${formFields.size})",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedButton(
                        onClick = {
                            val doc = currentDoc ?: return@OutlinedButton
                            scope.launch {
                                runCatching {
                                    appState.pdfEngine.optimize(
                                        documentId = doc.documentId,
                                        compressImages = false,
                                        imageQuality = 100,
                                        removeUnusedObjects = false,
                                        flattenForms = true
                                    )
                                    isFormFlattened = true
                                    statusMessage = "Form fields successfully flattened into static document body."
                                }
                            }
                        }
                    ) {
                        Text(if (isFormFlattened) "Flattened" else "Flatten Form")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (formFields.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No AcroForm fields detected in this document.")
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(formFields) { field ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = field.fieldName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = fieldValues[field.fieldName] ?: "",
                                        onValueChange = { newVal ->
                                            fieldValues = fieldValues + (field.fieldName to newVal)
                                        },
                                        label = { Text("Value") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
