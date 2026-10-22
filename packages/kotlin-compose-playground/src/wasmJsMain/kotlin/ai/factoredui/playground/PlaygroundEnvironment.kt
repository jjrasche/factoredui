package ai.factoredui.playground

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.schema.ActionAnswer
import ai.factoredui.compose.schema.readActionAnswer
import ai.factoredui.compose.renderer.themeNamed
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val DEFAULT_SPEC_URL = "specs/example-form.json"

val DEMO_STATUSES = listOf(
    "idle",
    "loading",
    "ready",
    "error",
)

private val specJson = Json { ignoreUnknownKeys = true }

fun parseSpec(text: String): Result<Spec> =
    runCatching { specJson.decodeFromString(Spec.serializer(), text) }

suspend fun loadSpecText(url: String): String =
    HttpClient().get(url).bodyAsText()

fun specUrlParam(): String? =
    rawSpecParam().ifEmpty { null }

private val composerHttp = HttpClient()

// Dev-harness host: forward a spec button's action to the server that served the
// spec (?spec=.../spec → POST .../action). scene3d POSTs its own select/camera.
private fun jsonOfParam(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject { value.forEach { (key, inner) -> put(key.toString(), jsonOfParam(inner)) } }
    is List<*> -> JsonArray(value.map(::jsonOfParam))
    else -> JsonPrimitive(value.toString())
}

private suspend fun answerOf(name: String, body: String, actionUrl: String): ActionAnswer =
    runCatching {
        val response = composerHttp.post(actionUrl) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        readActionAnswer(name, response.status.value, response.bodyAsText())
    }.getOrElse { ActionAnswer(emptyMap(), "action '$name' could not reach the server: ${it.message ?: it::class.simpleName}") }

private fun forwardAction(name: String, actionUrl: String, onAnswer: (ActionAnswer) -> Unit): ActionHandler = handler@{ params ->
    val body = buildJsonObject {
        put("action", name)
        put("params", jsonOfParam(params))
    }.toString()
    onAnswer(answerOf(name, body, actionUrl))
}

// The scene-composer manipulation verbs the server (:8765) registers in actions.py.
// Kept in sync as ONE list so a spec button can dispatch any verb the simulator exposes.
val SCENE3D_VERBS = listOf(
    "select-entity", "camera-update", "move-entity", "update-light", "apply-pose",
    "set-joint-angle", "refine-pose", "preview", "sit", "apply-interaction-pose",
    "render-pose", "play-reaction", "play-motion", "settle", "drop", "add-prop", "select-setting",
)

private const val CHARACTER_PERSIST_BASE = "http://127.0.0.1:8770/character"

private fun persistDialAction(): ActionHandler = handler@{ params ->
    val field = params["field"] as? String ?: return@handler
    val value = (params["value"] as? String)?.toFloatOrNull() ?: return@handler
    val characterId = (params["character_id"] as? String)?.takeIf { it.isNotBlank() } ?: return@handler
    val body = buildJsonObject {
        put("diff", buildJsonObject { put(field, value) })
    }.toString()
    runCatching {
        composerHttp.post("$CHARACTER_PERSIST_BASE/$characterId") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
    Unit
}

private fun openSpecAction(): ActionHandler = handler@{ params ->
    val spec = params["spec"] as? String ?: return@handler
    reloadWithSpec(spec)
}

fun playgroundActions(actionUrl: String? = null, onAnswer: (ActionAnswer) -> Unit = {}): ActionRegistry {
    val base = mapOf<String, ActionHandler>(
        "open_spec" to openSpecAction(),
        "submit" to { params -> println("[playground] submit($params)") },
        "persist-dial" to persistDialAction(),
        "on-focus-change" to { params ->
            println("[playground] focus: node=${params["node_id"]} magnitude=${params["relevance_magnitude"]}")
        },
    )
    if (actionUrl == null) return base
    return ForwardingActions(base + SCENE3D_VERBS.associateWith { verb -> forwardAction(verb, actionUrl, onAnswer) }) { name -> forwardAction(name, actionUrl, onAnswer) }
}

private class ForwardingActions(private val known: Map<String, ActionHandler>, private val forward: (String) -> ActionHandler) : Map<String, ActionHandler> by known {
    override fun get(key: String): ActionHandler = known[key] ?: forward(key)
}

private fun rawActionParam(): String =
    js("(new URLSearchParams(window.location.search).get('action')) || ''")

fun actionUrlParam(): String? = rawActionParam().ifEmpty { null } ?: deriveFromSpec("/action") ?: deriveFromSpecFile("action")

fun worldStreamUrlParam(): String? = deriveFromSpec("/world/stream")

private fun deriveFromSpec(suffix: String): String? {
    val spec = specUrlParam() ?: return null
    return if (spec.endsWith("/spec")) spec.removeSuffix("/spec") + suffix else null
}

private fun deriveFromSpecFile(sibling: String): String? {
    val path = specUrlParam()?.substringBefore('?') ?: return null
    return if (path.endsWith("/spec.json")) path.removeSuffix("spec.json") + sibling else null
}

fun placeholderSpec(message: String): Spec = Spec(
    specVersion = 1,
    rendererMin = 1,
    root = SpecNode(
        id = "placeholder",
        type = SpecNodeType.TEXT,
        props = mapOf("value" to SpecValue.StringValue(message)),
    ),
)

private fun rawSpecParam(): String =
    js("(new URLSearchParams(window.location.search).get('spec')) || ''")

private fun reloadWithSpec(spec: String) {
    js("window.location.assign(window.location.pathname + '?spec=' + encodeURIComponent(spec) + '&full=1')")
}

private fun themeParam(): String =
    js("(new URLSearchParams(window.location.search).get('theme')) || ''")

private fun prefersDark(): Boolean =
    js("(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) ? true : false")

fun hostTheme(): SpecTheme = themeNamed(themeParam().ifEmpty { null }) ?: if (prefersDark()) SpecTheme.DARK else SpecTheme.LIGHT

fun fullscreenParam(): Boolean =
    js("(new URLSearchParams(window.location.search).get('full')) ? true : false")
