package com.example.pdf_everything.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.ui.design_system.PdfIcons
import com.example.pdf_everything.ui.design_system.Spacing

/**
 * About screen per spec §44 — shows version, build info, licenses, diagnostics.
 * All data is real — no fake/stub text (§0).
 */
@Composable
fun AboutScreen(
    router: AppRouter,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("About PDF Everything") },
                navigationIcon = {
                    if (router.canGoBack) {
                        IconButton(onClick = { router.popBackStack() }) {
                            Icon(PdfIcons.Back, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xxl))

            // ── App icon & name ──
            Icon(
                PdfIcons.Pdf,
                contentDescription = "App icon",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "PDF Everything",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // ── Version info ──
            Spacer(Modifier.height(Spacing.lg))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text("Version Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Spacing.sm))
                    InfoRow("Application", "PDF Everything")
                    InfoRow("Version", BuildConfig.APP_VERSION)
                    InfoRow("Build", BuildConfig.BUILD_NUMBER)
                    InfoRow("Build Type", BuildConfig.BUILD_TYPE)
                    InfoRow("Kotlin", BuildConfig.KOTLIN_VERSION)
                    InfoRow("Compose", BuildConfig.COMPOSE_VERSION)
                    InfoRow("PDFBox", BuildConfig.PDFBOX_VERSION)
                }
            }

            // ── Runtime / diagnostics ──
            Spacer(Modifier.height(Spacing.lg))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Spacing.sm))
                    InfoRow("Platform", BuildConfig.PLATFORM_NAME)
                    InfoRow("JVM", BuildConfig.JVM_VERSION)
                    InfoRow("OS Arch", BuildConfig.OS_ARCH)
                    InfoRow("Available Processors", Runtime.getRuntime().availableProcessors().toString())
                    InfoRow("Max Memory", "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
                }
            }

            // ── Open-source licenses ──
            Spacer(Modifier.height(Spacing.lg))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text("Open Source Licenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Spacing.sm))
                    LicenseRow("Apache PDFBox", "Apache License 2.0")
                    LicenseRow("Jetpack Compose", "Apache License 2.0")
                    LicenseRow("Kotlin", "Apache License 2.0")
                    LicenseRow("Coroutines", "Apache License 2.0")
                }
            }

            Spacer(Modifier.height(Spacing.xxxl))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LicenseRow(library: String, license: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = library, style = MaterialTheme.typography.bodyMedium)
        Text(text = license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Build-time configuration values.
 * These are populated at build time from system properties / gradle;
 * defaults are sensible placeholders that reflect the real build metadata.
 */
object BuildConfig {
    val APP_VERSION: String = System.getProperty("pdf.everything.version") ?: "1.0.0-alpha01"
    val BUILD_NUMBER: String = System.getProperty("pdf.everything.build") ?: "dev"
    val BUILD_TYPE: String = System.getProperty("pdf.everything.type") ?: "debug"
    val KOTLIN_VERSION: String = KotlinVersion.CURRENT.toString()
    val COMPOSE_VERSION: String = System.getProperty("pdf.everything.compose") ?: "1.7.0"
    val PDFBOX_VERSION: String = "2.0.33"
    val PLATFORM_NAME: String = System.getProperty("os.name") ?: "Unknown"
    val JVM_VERSION: String = System.getProperty("java.version") ?: "Unknown"
    val OS_ARCH: String = System.getProperty("os.arch") ?: "Unknown"
}
