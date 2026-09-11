package com.example.pdf_everything.app.ui.bars

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.app.router.AppRouter
import com.example.pdf_everything.app.router.AppRoute
import com.example.pdf_everything.ui.design_system.PdfIcons

@Composable
fun PdfBottomBar(router: AppRouter) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { router.navigate(AppRoute.Home) }) {
                Icon(PdfIcons.Home, contentDescription = "Home")
            }
            IconButton(onClick = { router.navigate(AppRoute.Settings) }) {
                Icon(PdfIcons.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = { router.navigate(AppRoute.About) }) {
                Icon(PdfIcons.Info, contentDescription = "About")
            }
        }
    }
}