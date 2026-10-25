package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private const val PAGE_GAP_DP = 12
private const val PAGE_PADDING_DP = 16

/** A page's root: one scrolling view holding the page's column, so a page longer than the screen can be scrolled and the world bar goes on top of it. */
internal fun scrollingRoot(children: List<JsonObject>, background: String): JsonObject =
    specNode("root", "scrollview", mapOf("background" to background), listOf(specNode("page", "column", mapOf("gap" to PAGE_GAP_DP, "padding" to PAGE_PADDING_DP), children)))
