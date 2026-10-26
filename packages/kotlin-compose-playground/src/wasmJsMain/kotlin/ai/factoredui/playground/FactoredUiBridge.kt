package ai.factoredui.playground

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.StateBridge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop

/** Puts `window.factoredUi` on the page for as long as the screen is shown: setState(path, json), getState(path), onChange(path, fn) -> id, off(id). */
@Composable
fun InstallFactoredUiBridge(context: RenderContext) {
    val scope = rememberCoroutineScope()
    DisposableEffect(context) {
        val bridge = StateBridge(context)
        val watches = HashMap<Int, Job>()
        var nextWatch = 0
        installFactoredUi(
            setState = { path, json -> (bridge.setState(path.toString(), json.toString()) ?: "").toJsString() },
            getState = { path -> bridge.getState(path.toString()).toJsString() },
            onChange = { path, listener ->
                val id = nextWatch++
                watches[id] = scope.launch { bridge.changes(path.toString()).drop(1).collect { json -> callListener(listener, json.toJsString()) } }
                id.toJsNumber()
            },
            off = { id -> watches.remove(id.toInt())?.cancel() },
        )
        onDispose {
            watches.values.forEach { it.cancel() }
            removeFactoredUi()
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun installFactoredUi(
    setState: (JsString, JsString) -> JsString,
    getState: (JsString) -> JsString,
    onChange: (JsString, JsAny) -> JsNumber,
    off: (JsNumber) -> Unit,
): Unit = js(
    """
    (() => {
      const text = (value) => typeof value === 'string' ? value : JSON.stringify(value);
      window.factoredUi = {
        setState: (path, json) => setState(String(path), text(json)),
        getState: (path) => getState(String(path)),
        onChange: (path, listener) => onChange(String(path), listener),
        off: (id) => off(id),
      };
    })()
    """,
)

private fun removeFactoredUi(): Unit = js("delete window.factoredUi")

@Suppress("UNUSED_PARAMETER")
private fun callListener(listener: JsAny, json: JsString): Unit = js("listener(json)")
