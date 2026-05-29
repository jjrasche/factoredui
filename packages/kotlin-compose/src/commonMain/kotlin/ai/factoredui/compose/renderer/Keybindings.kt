package ai.factoredui.compose.renderer

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.ShortcutKey
import kotlinx.coroutines.launch

/**
 * Installs spec-level keyboard shortcuts around [content].
 *
 * A key-down that matches a bound [ShortcutKey] dispatches its [ActionRef]
 * through the same path a tap takes — so the resolved-params → capture flow
 * applies to keystrokes too, anchored to a `keybinding:<key>` component path.
 *
 * The handler fires on whatever platform delivers a key event; a device with no
 * keyboard simply never produces one, so there is no platform branch (and the
 * eventual KMP-Android tablet-with-keyboard case works unchanged).
 */
@Composable
internal fun KeybindingHost(
    keybindings: Map<ShortcutKey, ActionRef>,
    context: RenderContext,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val shortcut = toShortcutKey(event.key) ?: return@onPreviewKeyEvent false
                val action = keybindings[shortcut] ?: return@onPreviewKeyEvent false
                scope.launch { context.dispatch("keybinding:${shortcut.name.lowercase()}", action) }
                true
            },
    ) {
        content()
    }
}

/** Map a Compose key to the closed [ShortcutKey] set; null when unbound. */
internal fun toShortcutKey(key: Key): ShortcutKey? = when (key) {
    Key.Y -> ShortcutKey.Y
    Key.N -> ShortcutKey.N
    Key.Spacebar -> ShortcutKey.SPACE
    Key.Enter -> ShortcutKey.ENTER
    Key.Escape -> ShortcutKey.ESCAPE
    Key.DirectionUp -> ShortcutKey.ARROW_UP
    Key.DirectionDown -> ShortcutKey.ARROW_DOWN
    Key.DirectionLeft -> ShortcutKey.ARROW_LEFT
    Key.DirectionRight -> ShortcutKey.ARROW_RIGHT
    Key.Tab -> ShortcutKey.TAB
    else -> null
}
