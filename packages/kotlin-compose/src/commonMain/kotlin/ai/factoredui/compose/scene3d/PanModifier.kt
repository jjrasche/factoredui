package ai.factoredui.compose.scene3d

import androidx.compose.ui.input.pointer.PointerEvent

// PointerKeyboardModifiers exposes no accessors in this Compose version; read the native event instead.
expect fun PointerEvent.isPanModifierPressed(): Boolean
