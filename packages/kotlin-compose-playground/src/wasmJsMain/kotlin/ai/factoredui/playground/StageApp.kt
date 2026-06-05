package ai.factoredui.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

fun stageParam(): Boolean =
    js("(new URLSearchParams(window.location.search).get('app')) === 'stage'")

enum class StageContext(val label: String, val specUrl: String?, val promptUrl: String?) {
    STORY("Story", "specs/story-spine.json", null),
    CHARACTER("Character", "specs/character.json", "http://127.0.0.1:8770/character/prompt"),
    COMPOSER("Composer", "specs/composer.json", "http://127.0.0.1:8765/director/prompt"),
    REVIEW("Review", null, null),
}

private val PERSONALITY_FIELDS = listOf(
    "laban_weight", "laban_time", "laban_space", "laban_flow",
    "amplitude", "suppression_tendency", "recovery_rate", "emotional_regulation",
)

private val ATTR_FIELDS = listOf(
    "apparent_age", "build", "height_impression", "hair", "skin", "eyes",
    "distinguishing_features", "grooming", "wardrobe", "posture", "baseline_affect",
    "role", "origin", "motivation", "fear", "relationships", "voice", "name",
)

private fun emptyAttributes(): Map<String, Any?> = ATTR_FIELDS.associateWith { "" }

private val NEUTRAL_PERSONALITY: Map<String, Float> = mapOf(
    "laban_weight" to 0.0f, "laban_time" to 0.0f, "laban_space" to 0.0f, "laban_flow" to 0.0f,
    "amplitude" to 1.0f, "suppression_tendency" to 0.5f, "recovery_rate" to 0.5f, "emotional_regulation" to 0.5f,
)

private fun readPersonality(data: Map<String, Any?>): Map<String, Float> {
    val personality = (data["character"] as? Map<*, *>)?.get("personality") as? Map<*, *> ?: return emptyMap()
    return PERSONALITY_FIELDS.associateWith { field -> (personality[field] as? Number)?.toFloat() ?: 0f }
}

private fun nearestEffort(personality: Map<String, Float>): String {
    val strong = (personality["laban_weight"] ?: 0f) >= 0f
    val sudden = (personality["laban_time"] ?: 0f) >= 0f
    val direct = (personality["laban_space"] ?: 0f) >= 0f
    return when {
        strong && sudden && direct -> "Punch"
        strong && sudden && !direct -> "Slash"
        strong && !sudden && direct -> "Press"
        strong && !sudden && !direct -> "Wring"
        !strong && sudden && direct -> "Dab"
        !strong && sudden && !direct -> "Flick"
        !strong && !sudden && direct -> "Glide"
        else -> "Float"
    }
}

private fun emitPersonalityTrainingRow(field: String, old: Float, current: Float) {
    val stamp = nowMillis()
    val row = buildJsonObject {
        put("event_type", "input")
        put("component_path", "character.personality.$field")
        put("character_id", "heigl")
        put("correlation_id", "char::heigl::${stamp.toLong()}")
        put("old", old)
        put("new", current)
        put("occurred_at", stamp)
    }.toString()
    pushStageTrainingRow(row)
}

private suspend fun postCharacterPrompt(
    url: String,
    text: String,
    personality: Map<String, Float>,
    applyFloat: (String, Float) -> Unit,
    applyString: (String, String) -> Unit,
) {
    val payload = buildJsonObject {
        put("text", text)
        put("personality", buildJsonObject { personality.forEach { (key, value) -> put(key, value) } })
    }.toString()
    val body = runCatching {
        HttpClient().post(url) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.bodyAsText()
    }.getOrElse { failure ->
        pushStageLog("character endpoint not reachable yet: ${failure.message}")
        return
    }
    publishStageLastAction(body)
    runCatching {
        val response = Json.parseToJsonElement(body).jsonObject
        response["diff"]?.jsonObject?.forEach { (field, value) ->
            val content = (value as? JsonPrimitive)?.content ?: return@forEach
            val numeric = content.toFloatOrNull()
            if (numeric != null) applyFloat(field, numeric) else applyString("characterAttr.$field", content)
        }
        (response["narration"] as? JsonPrimitive)?.content?.let { applyString("characterNarration", it) }
        (response["render_prompt"] as? JsonPrimitive)?.content?.let { applyString("characterRenderPrompt", it) }
        val question = (response["question"] as? JsonPrimitive)?.content
        applyString("characterQuestion", if (question == null || question == "null") "" else question)
    }.onFailure { pushStageLog("character response parse: ${it.message}") }
}

private const val CHARACTER_READ_BASE = "http://127.0.0.1:8770/character"
private const val CHARACTER_CREATE_URL = "http://127.0.0.1:8770/character"

private suspend fun createCharacter(name: String, context: RenderContext) {
    context.setBinding("createStatus", "creating ${name}…")
    val payload = buildJsonObject { put("name", name) }.toString()
    val responseBody = runCatching {
        HttpClient().post(CHARACTER_CREATE_URL) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.bodyAsText()
    }.getOrElse {
        context.setBinding("createStatus", "create endpoint not live yet")
        pushStageLog("create character failed: ${it.message}")
        return
    }
    val createdId = runCatching {
        (Json.parseToJsonElement(responseBody).jsonObject["id"] as? JsonPrimitive)?.content
    }.getOrNull()
    if (createdId.isNullOrBlank()) {
        context.setBinding("createStatus", "create returned no id")
        return
    }
    context.setBinding("createStatus", "")
    context.setBinding("characterId", createdId)
}

private suspend fun loadCharacter(id: String, context: RenderContext) {
    val body = runCatching {
        HttpClient().get("$CHARACTER_READ_BASE/$id").bodyAsText()
    }.getOrElse { failure ->
        pushStageLog("character read failed for $id: ${failure.message}")
        return
    }
    runCatching {
        val model = Json.parseToJsonElement(body).jsonObject
        context.setBinding("characterAttr", emptyAttributes())
        context.setBinding("characterRenderPrompt", "")
        context.setBinding("characterNarration", "")
        context.setBinding("characterQuestion", "")
        (model["display_name"] as? JsonPrimitive)?.content?.let { context.setBinding("characterDisplayName", it) }
        (model["appearance_description"] as? JsonPrimitive)?.content?.let { context.setBinding("characterAppearance", it) }
        val personality = model["personality"] as? JsonObject
        val floats = if (personality == null) {
            NEUTRAL_PERSONALITY
        } else {
            PERSONALITY_FIELDS.associateWith { field ->
                (personality[field] as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f
            }
        }
        context.setBinding("character.personality", floats)
        val visual = model["visual"] as? JsonObject
        val referenceImages = visual?.get("reference_images")?.jsonArray
        val identityImage = referenceImages?.firstOrNull()?.jsonObject
            ?.let { (it["url"] as? JsonPrimitive)?.content } ?: ""
        context.setBinding("characterImage", identityImage)
        publishCharacterGates(context, personality, referenceImages?.size ?: 0, visual)
    }.onFailure { pushStageLog("character read parse for $id: ${it.message}") }
}

private fun publishCharacterGates(
    context: RenderContext,
    personality: JsonObject?,
    referenceImageCount: Int,
    visual: JsonObject?,
) {
    val poseCount = personality?.get("defining_poses")?.jsonArray?.size ?: 0
    val loraPath = (visual?.get("lora") as? JsonObject)?.let { (it["path"] as? JsonPrimitive)?.content } ?: ""
    val hasAnyView = referenceImageCount > 0 || poseCount > 0
    context.setBinding("gate1view", if (hasAnyView) "1 view · done" else "1 view · open")
    context.setBinding("gate4views", if (poseCount >= 4) "4 views · done" else "4 views · open")
    context.setBinding("gatePoses", if (poseCount > 0) "poses · $poseCount" else "poses · open")
    context.setBinding("gateMesh", if (loraPath.isNotEmpty()) "3D mesh · done" else "3D mesh · open")
}

private const val RENDER_BASE = "http://127.0.0.1:8775"
private const val RENDER_POLL_ATTEMPTS = 40

private suspend fun requestRender(characterId: String, context: RenderContext) {
    context.setBinding("renderStatus", "requesting render…")
    val livePrompt = (context.data["characterRenderPrompt"] as? String)?.takeIf { it.isNotBlank() }
    val payload = buildJsonObject {
        if (livePrompt != null) put("render_prompt", livePrompt) else put("character_id", characterId)
        put("view", "front")
        put("kind", "image")
    }.toString()
    val jobId = runCatching {
        val accepted = HttpClient().post("$RENDER_BASE/render") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.bodyAsText()
        (Json.parseToJsonElement(accepted).jsonObject["job_id"] as? JsonPrimitive)?.content
    }.getOrElse {
        context.setBinding("renderStatus", "render leg not live yet")
        pushStageLog("render request failed: ${it.message}")
        return
    }
    if (jobId.isNullOrBlank()) {
        context.setBinding("renderStatus", "render returned no job id")
        return
    }
    pollRenderUntilSettled(jobId, context)
}

private suspend fun pollRenderUntilSettled(jobId: String, context: RenderContext) {
    repeat(RENDER_POLL_ATTEMPTS) {
        delay(1500)
        val statusBody = runCatching {
            HttpClient().get("$RENDER_BASE/render/status/$jobId").bodyAsText()
        }.getOrNull() ?: return
        val status = runCatching { Json.parseToJsonElement(statusBody).jsonObject }.getOrNull() ?: return
        val state = (status["status"] as? JsonPrimitive)?.content ?: ""
        val detail = (status["detail"] as? JsonPrimitive)?.content ?: state
        when (state) {
            "done" -> {
                (status["url"] as? JsonPrimitive)?.content?.let { context.setBinding("characterImage", it) }
                context.setBinding("renderStatus", "")
                return
            }
            "failed" -> {
                val error = (status["error"] as? JsonPrimitive)?.content ?: "unknown"
                context.setBinding("renderStatus", "render failed: $error")
                return
            }
            else -> context.setBinding("renderStatus", detail)
        }
    }
    context.setBinding("renderStatus", "render still running…")
}

private suspend fun postPrompt(url: String, text: String) {
    val payload = buildJsonObject { put("text", text) }.toString()
    val body = HttpClient().post(url) {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }.bodyAsText()
    publishStageLastAction(body)
}

@Composable
fun StageApp() {
    val context = remember {
        RenderContext(
            actions = playgroundActions(actionUrlParam()),
            observability = StageDebugObservability(),
            initialData = mapOf(
                "omnibox" to mapOf("text" to ""),
                "characterId" to "heigl",
                "createStatus" to "",
                "characterDisplayName" to "heigl",
                "characterAppearance" to "",
                "characterEffort" to "—",
                "characterNotes" to "",
                "characterNarration" to "",
                "characterQuestion" to "",
                "characterRenderPrompt" to "",
                "characterAttr" to emptyAttributes(),
                "characterImage" to "",
                "autoRegenerate" to false,
                "renderStatus" to "",
                "gate1view" to "1 view",
                "gate4views" to "4 views",
                "gatePoses" to "poses",
                "gateMesh" to "3D mesh",
                "character" to mapOf(
                    "personality" to mapOf(
                        "laban_weight" to 0.0f,
                        "laban_time" to 0.0f,
                        "laban_space" to 0.0f,
                        "laban_flow" to 0.0f,
                        "amplitude" to 0.5f,
                        "suppression_tendency" to 0.4f,
                        "recovery_rate" to 0.5f,
                        "emotional_regulation" to 0.5f,
                    ),
                ),
            ),
        )
    }
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(StageContext.STORY) }
    val specFlow = remember { MutableStateFlow(placeholderSpec("Loading…")) }

    LaunchedEffect(Unit) {
        var previous: Map<String, Float> = emptyMap()
        val pending = mutableMapOf<String, Pair<Float, Float>>()
        var flushJob: Job? = null
        var loadedCharacterId: String? = null
        context.dataFlow.collect { data ->
            publishStageBindings(data.toString())
            val selectedCharacterId = data["characterId"] as? String
            if (selectedCharacterId != null && selectedCharacterId != loadedCharacterId) {
                loadedCharacterId = selectedCharacterId
                previous = emptyMap()
                pending.clear()
                flushJob?.cancel()
                loadCharacter(selectedCharacterId, context)
                return@collect
            }
            val personality = readPersonality(data)
            if (personality.isNotEmpty()) {
                val effort = nearestEffort(personality)
                if ((data["characterEffort"] as? String) != effort) {
                    context.setBinding("characterEffort", effort)
                }
                if (previous.isNotEmpty()) {
                    personality.forEach { (field, value) ->
                        val old = previous[field]
                        if (old != null && old != value) {
                            val firstOld = pending[field]?.first ?: old
                            pending[field] = firstOld to value
                            flushJob?.cancel()
                            flushJob = scope.launch {
                                delay(400)
                                pending.forEach { (changedField, change) ->
                                    if (change.first != change.second) {
                                        emitPersonalityTrainingRow(changedField, change.first, change.second)
                                    }
                                }
                                pending.clear()
                            }
                        }
                    }
                }
                previous = personality
            }
        }
    }

    LaunchedEffect(active) {
        val url = active.specUrl
        if (url == null) {
            specFlow.value = placeholderSpec("${active.label} — coming soon")
        } else {
            runCatching { loadSpecText(url) }.fold(
                onSuccess = { text ->
                    parseSpec(text).fold(
                        onSuccess = { specFlow.value = it },
                        onFailure = { specFlow.value = placeholderSpec("parse failed: ${it.message}") },
                    )
                },
                onFailure = { specFlow.value = placeholderSpec("load failed for $url: ${it.message}") },
            )
        }
    }

    StageTheme {
        Surface(Modifier.fillMaxSize(), color = StageTokens.canvas) {
            Row(Modifier.fillMaxSize()) {
                StageNavRail(active = active, onSelect = { pushStageLog("nav -> ${it.label}"); active = it })
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    StageOmnibox(
                        routed = active.promptUrl != null,
                        continuous = active == StageContext.CHARACTER,
                        onSubmit = { entered ->
                            pushStageLog("omnibox[${active.label}] -> \"$entered\"")
                            val ctx = active
                            if (entered.isNotBlank()) {
                                scope.launch {
                                    when (ctx) {
                                        StageContext.CHARACTER -> ctx.promptUrl?.let { url ->
                                            postCharacterPrompt(
                                                url,
                                                entered,
                                                readPersonality(context.data),
                                                applyFloat = { field, value -> context.setBinding("character.personality.$field", value) },
                                                applyString = { key, value -> context.setBinding(key, value) },
                                            )
                                            if (context.data["autoRegenerate"] == true) {
                                                val renderingCharacter = context.data["characterId"] as? String ?: "heigl"
                                                scope.launch { requestRender(renderingCharacter, context) }
                                            }
                                        }
                                        else -> ctx.promptUrl?.let { url -> postPrompt(url, entered) }
                                    }
                                }
                            }
                        },
                    )
                    if (active == StageContext.CHARACTER) {
                        StageNewCharacter(onCreate = { name ->
                            if (name.isNotBlank()) scope.launch { createCharacter(name.trim(), context) }
                        })
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(StageTokens.gapMd)) {
                        RenderSpec(specFlow = specFlow, context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun StageNavRail(active: StageContext, onSelect: (StageContext) -> Unit) {
    Column(
        Modifier.width(180.dp).fillMaxHeight().background(StageTokens.surface).padding(StageTokens.gapMd),
        verticalArrangement = Arrangement.spacedBy(StageTokens.gapXs),
    ) {
        Text(
            "the Stage",
            color = StageTokens.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = StageTokens.gapMd),
        )
        StageContext.values().forEach { ctx ->
            StageNavItem(label = ctx.label, selected = ctx == active, onClick = { onSelect(ctx) })
        }
    }
}

@Composable
private fun StageNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) StageTokens.accentMuted else Color.Transparent
    val foreground = if (selected) StageTokens.textPrimary else StageTokens.textMuted
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(StageTokens.radius)).background(background)
            .clickable { onClick() }
            .padding(horizontal = StageTokens.gapMd, vertical = StageTokens.gapSm),
    ) {
        Text(label, color = foreground, fontSize = 14.sp)
    }
}

@Composable
private fun StageNewCharacter(onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = StageTokens.gapMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StageTokens.gapSm),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("New character name — e.g. grandfather", color = StageTokens.textMuted) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onCreate(name); name = "" }) { Text("Create") }
    }
}

@Composable
private fun StageOmnibox(routed: Boolean, continuous: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var flushJob by remember { mutableStateOf<Job?>(null) }
    val placeholder = when {
        continuous -> "Speak — describe them; pause and they take shape…"
        routed -> "Say what happens…"
        else -> "Type here — routing for this screen is coming"
    }
    Row(Modifier.fillMaxWidth().background(StageTokens.canvas).padding(StageTokens.gapMd)) {
        OutlinedTextField(
            value = text,
            onValueChange = { entered ->
                text = entered
                if (continuous && entered.isNotBlank() && flushJob?.isActive != true) {
                    flushJob = scope.launch {
                        delay(10_000)
                        onSubmit(text)
                    }
                }
            },
            placeholder = { Text(placeholder, color = StageTokens.textMuted) },
            singleLine = !continuous,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                flushJob?.cancel()
                onSubmit(text)
                if (!continuous) text = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
