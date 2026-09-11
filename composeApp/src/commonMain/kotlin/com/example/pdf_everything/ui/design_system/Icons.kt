package com.example.pdf_everything.ui.design_system

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Edit

/**
 * Centralized icon references for the PDF Everything app.
 * Per spec §44 — use Material Icons as the default icon set.
 */
object PdfIcons {
    // Navigation
    val Back         = Icons.AutoMirrored.Filled.ArrowBack
    val Menu         = Icons.Filled.Menu
    val Home         = Icons.Filled.Home

    // File operations
    val Open         = Icons.Filled.FolderOpen
    val Save         = Icons.Filled.Save
    val Print        = Icons.Filled.Print
    val Share        = Icons.Filled.Share
    val Close        = Icons.Filled.Close
    val Delete       = Icons.Filled.Delete

    // Edit
    val Undo         = Icons.AutoMirrored.Filled.Undo
    val Redo         = Icons.AutoMirrored.Filled.Redo
    val Edit         = Icons.Filled.Edit
    val EditText     = Icons.Filled.TextFields
    val Highlight    = Icons.Filled.Highlight
    val Add          = Icons.Filled.Add

    // View
    val Pdf          = Icons.Filled.PictureAsPdf
    val Search       = Icons.Filled.Search
    val ZoomIn       = Icons.Filled.ZoomIn
    val ZoomOut      = Icons.Filled.ZoomOut
    val Visibility   = Icons.Filled.Visibility
    val Info         = Icons.Filled.Info

    // Outline / Bookmarks
    val Bookmark     = Icons.Outlined.Bookmark
    val Star         = Icons.Filled.Star
    val ArrowDown    = Icons.Filled.KeyboardArrowDown
    val ArrowUp      = Icons.Filled.KeyboardArrowUp

    // View modes (spec §15)
    val SinglePage   = Icons.Filled.Description
    val Continuous   = Icons.Filled.List
    val TwoPage      = Icons.Filled.Book
    val Organizer    = Icons.Filled.Apps
    val Presentation = Icons.Filled.PlayArrow

    // Fullscreen
    val Fullscreen       = Icons.Filled.Fullscreen
    val FullscreenExit   = Icons.Filled.FullscreenExit

    // Navigation arrows
    val ArrowLeft    = Icons.Filled.KeyboardArrowLeft
    val ArrowRight   = Icons.Filled.KeyboardArrowRight

    // Settings
    val Settings     = Icons.Filled.Settings
    val More         = Icons.Filled.MoreVert
    val EditOutline  = Icons.Outlined.Edit
}