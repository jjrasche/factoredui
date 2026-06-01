package ai.factoredui.playground

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(documentBody()) {
        PlaygroundApp()
    }
}

private fun documentBody(): HTMLElement = js("document.body")
