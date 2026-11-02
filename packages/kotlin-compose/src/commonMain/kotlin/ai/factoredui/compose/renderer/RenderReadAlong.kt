package ai.factoredui.compose.renderer

import ai.factoredui.compose.media.ReadAlongProps
import ai.factoredui.compose.media.ReadAlongWord
import ai.factoredui.compose.media.litWordIndex
import ai.factoredui.compose.media.readReadAlongProps
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.asLayoutProps
import ai.factoredui.compose.schema.bindingPath
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val DEFAULT_MAX_HEIGHT_DP = 420
private const val MARKER_SCALE = 0.65f
private const val PARAGRAPH_BREAK = "\n\n"
private const val LIT_LINE_BAND = 0.3f

private class RunText(val text: AnnotatedString, val starts: IntArray, val ends: IntArray, val hitStarts: IntArray)

private fun runTextOf(words: List<ReadAlongWord>, markerStyle: SpanStyle): RunText {
    val starts = IntArray(words.size)
    val ends = IntArray(words.size)
    val hitStarts = IntArray(words.size)
    val text = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            if (word.startsParagraph && index > 0) append(PARAGRAPH_BREAK)
            hitStarts[index] = length
            word.marker?.let { label ->
                pushStyle(markerStyle)
                append(label)
                pop()
                append(" ")
            }
            starts[index] = length
            append(word.text)
            ends[index] = length
            append(" ")
        }
    }
    return RunText(text, starts, ends, hitStarts)
}

private fun runAt(run: RunText, offset: Int): Int? {
    if (run.hitStarts.isEmpty()) return null
    return run.hitStarts.indexOfLast { it <= offset }.coerceAtLeast(0)
}

private fun litColoursOf(theme: SpecTheme, given: String?): Pair<Color, Color> {
    val background = given?.let { theme.tokenColor(it) ?: it.parseColor() } ?: theme.accent
    if (given == null) return background to theme.onAccent
    val inkContrast = contrastBetween(background, theme.ink)
    val groundContrast = contrastBetween(background, theme.ground)
    return background to if (inkContrast >= groundContrast) theme.ink else theme.ground
}

private fun contrastBetween(first: Color, second: Color): Float {
    val light = maxOf(first.luminanceValue(), second.luminanceValue())
    val dark = minOf(first.luminanceValue(), second.luminanceValue())
    return (light + 0.05f) / (dark + 0.05f)
}

private fun Color.luminanceValue(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
internal fun RenderReadAlong(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = readReadAlongProps(resolvedProps, node.props)
    val theme = LocalSpecTheme.current
    val positionPath = node.props["position"]?.bindingPath()
    val position = (resolvedProps["position"] as? Number)?.toDouble() ?: 0.0
    val lit = litWordIndex(props.words, position)
    val bodySize = props.sizeSp?.sp ?: theme.type.body.size.sp
    val marker = SpanStyle(fontSize = bodySize * MARKER_SCALE, baselineShift = BaselineShift.Superscript, color = theme.muted)
    val run = remember(props.words, bodySize, theme) { runTextOf(props.words, marker) }
    val (highlight, litInk) = litColoursOf(theme, props.highlightColor)
    val shown = remember(run, lit, highlight, litInk) { withLitWord(run, lit, highlight, litInk) }
    RecordDrawn {
        text = run.text.text
        shownProps = mapOf("lit_index" to lit, "lit_text" to lit?.let { props.words[it].text }, "word_count" to props.words.size)
        notices = props.notices
    }
    ReadAlongSurface(node, props, run, shown, lit, positionPath, bodySize, context)
}

private fun withLitWord(run: RunText, lit: Int?, highlight: Color, litInk: Color): AnnotatedString {
    if (lit == null) return run.text
    val style = SpanStyle(background = highlight, color = litInk)
    return AnnotatedString(run.text.text, run.text.spanStyles + AnnotatedString.Range(style, run.starts[lit], run.ends[lit]), run.text.paragraphStyles)
}

@Composable
private fun ReadAlongSurface(
    node: SpecNode,
    props: ReadAlongProps,
    run: RunText,
    shown: AnnotatedString,
    lit: Int?,
    positionPath: String?,
    bodySize: androidx.compose.ui.unit.TextUnit,
    context: RenderContext,
) {
    val theme = LocalSpecTheme.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var following by remember { mutableStateOf(true) }
    val layoutProps = node.props.asLayoutProps()
    LaunchedEffect(scroll) {
        scroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false }
    }
    LaunchedEffect(lit, following, props.follows, layout, scroll.viewportSize()) {
        val drawn = layout ?: return@LaunchedEffect
        val word = lit ?: return@LaunchedEffect
        if (!props.follows || !following) return@LaunchedEffect
        scrollToLine(scroll, drawn, run.starts[word])
    }
    val sized = when {
        layoutProps.flex > 0f -> Modifier.fillMaxSize()
        layoutProps.height != null -> Modifier.fillMaxWidth().height(layoutProps.height!!.dp)
        else -> Modifier.fillMaxWidth().heightIn(max = DEFAULT_MAX_HEIGHT_DP.dp)
    }
    Box(modifier = sized.nodeTag(node.id)) {
        BasicText(
            text = shown,
            style = androidx.compose.ui.text.TextStyle(color = theme.ink, fontSize = bodySize, lineHeight = bodySize * 1.5f),
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll).pointerInput(run, positionPath) {
                detectTapGestures { tap ->
                    val offset = layout?.getOffsetForPosition(tap) ?: return@detectTapGestures
                    val index = runAt(run, offset) ?: return@detectTapGestures
                    following = true
                    tapWord(props, props.words[index], index, positionPath, node.id, context, scope)
                }
            },
        )
    }
}

private fun androidx.compose.foundation.ScrollState.viewportSize(): Int = viewportSize

private suspend fun scrollToLine(scroll: androidx.compose.foundation.ScrollState, layout: TextLayoutResult, offset: Int) {
    val line = layout.getLineForOffset(offset.coerceIn(0, layout.layoutInput.text.length))
    val top = layout.getLineTop(line)
    val bottom = layout.getLineBottom(line)
    val viewport = scroll.viewportSize
    if (viewport <= 0) return
    val visibleTop = scroll.value
    val visibleBottom = visibleTop + viewport
    if (top >= visibleTop && bottom <= visibleBottom) return
    scroll.animateScrollTo((top - viewport * LIT_LINE_BAND).toInt().coerceIn(0, scroll.maxValue))
}

private fun tapWord(props: ReadAlongProps, word: ReadAlongWord, index: Int, positionPath: String?, nodeId: String, context: RenderContext, scope: kotlinx.coroutines.CoroutineScope) {
    positionPath?.let { context.setBinding(it, word.at) }
    val action: ActionRef = props.onWordTap ?: return
    val params: Map<String, SpecValue> = action.params + mapOf(
        "index" to SpecValue.NumberValue(index.toDouble()),
        "at" to SpecValue.NumberValue(word.at),
        "marker" to SpecValue.StringValue(word.marker.orEmpty()),
        "verse" to SpecValue.StringValue(word.marker.orEmpty()),
    )
    scope.launch { context.dispatch(nodeId, action.copy(params = params)) }
}
