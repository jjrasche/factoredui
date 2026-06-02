package ai.factoredui.playground

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.renderer.RenderContext
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun PlaygroundApp() {
    val context = remember { RenderContext(actions = playgroundActions(actionUrlParam())) }
    val specFlow = remember { MutableStateFlow(placeholderSpec("Loading spec…")) }
    var editorText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    fun applySpec(text: String) {
        parseSpec(text).fold(
            onSuccess = { spec -> specFlow.value = spec; parseError = null },
            onFailure = { failure -> parseError = failure.message ?: "spec parse failed" },
        )
    }

    LaunchedEffect(Unit) {
        val url = specUrlParam() ?: DEFAULT_SPEC_URL
        runCatching { loadSpecText(url) }.fold(
            onSuccess = { text -> editorText = text; applySpec(text) },
            onFailure = { failure -> parseError = "fetch failed for $url — ${failure.message}" },
        )
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                SpecEditorPanel(
                    editorText = editorText,
                    parseError = parseError,
                    onEditorChange = { editorText = it },
                    onReload = { applySpec(editorText) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                RenderedSpecPanel(
                    specFlow = specFlow,
                    context = context,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                BindingControlPanel(
                    context = context,
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                )
            }
        }
    }
}
