import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.app.App
import com.example.pdf_everything.core.services.PlatformService

fun main() = application {
    val platformService = PlatformService()

    // Register file association on first launch (best-effort)
    platformService.registerFileAssociation()

    Window(
        onCloseRequest = ::exitApplication,
        title = "PDF Everything",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        )
    ) {
        App(
            platformService = platformService,
            onOpenFileRequest = {
                // File picking is handled inside PlatformService.pickOpenFile()
                // which shows a Swing JFileChooser on Desktop
            }
        )
    }
}