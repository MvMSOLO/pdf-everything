import App
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.pdf_everything.setDesktopStartupArgument

fun main(args: Array<String>) = application {
    setDesktopStartupArgument(args)
    Window(
        onCloseRequest = ::exitApplication,
        title = "PDF Everything",
    ) {
        App()
    }
}
