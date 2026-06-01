package ai.factoredui.playground

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.adapter.ActionRegistry
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

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

fun playgroundActions(): ActionRegistry = mapOf<String, ActionHandler>(
    "submit" to { params -> println("[playground] submit($params)") },
)

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
