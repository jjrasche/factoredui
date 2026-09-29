package ai.factoredui.compose.scene3d

import androidx.compose.ui.input.pointer.PointerEvent
import java.awt.event.MouseEvent

actual fun PointerEvent.isPanModifierPressed(): Boolean =
    (nativeEvent as? MouseEvent)?.isControlDown == true
