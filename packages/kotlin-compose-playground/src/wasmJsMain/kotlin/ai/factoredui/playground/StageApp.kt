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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import ai.factoredui.compose.observability.LoggingObservability
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun stageParam(): Boolean =
    js("(new URLSearchParams(window.location.search).get('app')) === 'stage'")

enum class StageContext(val label: String, val specUrl: String?, val promptUrl: String?) {
    STORY("Story", "specs/story-spine.json", null),
    CHARACTER("Character", null, null),
    COMPOSER("Composer", "specs/composer.json", "http://127.0.0.1:8765/director/prompt"),
    REVIEW("Review", null, null),
}

private suspend fun postPrompt(url: String, text: String) {
    val payload = buildJsonObject { put("text", text) }.toString()
    HttpClient().post(url) {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }
}

@Composable
fun StageApp() {
    val context = remember {
        RenderContext(
            actions = playgroundActions(actionUrlParam()),
            observability = LoggingObservability(),
            initialData = mapOf("omnibox" to mapOf("text" to "")),
        )
    }
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(StageContext.STORY) }
    val specFlow = remember { MutableStateFlow(placeholderSpec("Loading…")) }

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
                StageNavRail(active = active, onSelect = { active = it })
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    StageOmnibox(
                        enabled = active.promptUrl != null,
                        onSubmit = { entered ->
                            val target = active.promptUrl
                            if (target != null && entered.isNotBlank()) {
                                scope.launch { postPrompt(target, entered) }
                            }
                        },
                    )
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
private fun StageOmnibox(enabled: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val placeholder = if (enabled) "Say what happens…" else "Omnibox routing lands with this context…"
    Row(Modifier.fillMaxWidth().background(StageTokens.canvas).padding(StageTokens.gapMd)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder, color = StageTokens.textMuted) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                onSubmit(text)
                text = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
