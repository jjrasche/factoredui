package ai.factoredui.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SpecEditorPanel(
    editorText: String,
    parseError: String?,
    onEditorChange: (String) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(12.dp)) {
        PanelHeader("spec json")
        OutlinedTextField(
            value = editorText,
            onValueChange = onEditorChange,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onReload) { Text("Reload") }
        if (parseError != null) {
            Spacer(Modifier.height(8.dp))
            Text(parseError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
fun RenderedSpecPanel(
    specFlow: StateFlow<Spec>,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp)) {
        PanelHeader("rendered spec")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RenderSpec(specFlow = specFlow, context = context)
        }
    }
}

@Composable
fun BindingControlPanel(
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    var path by remember { mutableStateOf("status") }
    var bindingValue by remember { mutableStateOf("") }
    Column(
        modifier.padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PanelHeader("bindings")
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = bindingValue,
            onValueChange = { bindingValue = it },
            label = { Text("value") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { context.setBinding(path, bindingValue) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Set binding")
        }
        Divider()
        PanelHeader("quick-set {status}")
        DEMO_STATUSES.forEach { status ->
            Button(
                onClick = { context.setBinding("status", status) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(status, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
