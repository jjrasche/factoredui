package ai.factoredui.compose.renderer

internal actual fun pageOrigin(): String = readPageOrigin()

private fun readPageOrigin(): String = js("(window.location.origin) || ''")
