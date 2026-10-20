package ai.factoredui.application

private class Material(val colour: String, val kind: String)

private val MATERIALS = mapOf(
    "walls" to Material("#cdbf9f", "panel"),
    "glazing" to Material("#4f9fd1", "glass"),
    "doors" to Material("#8a5a3c", "timber"),
    "floor" to Material("#b98a5a", "timber"),
    "loft_floor" to Material("#b98a5a", "timber"),
    "joists" to Material("#c79b6a", "timber"),
    "posts" to Material("#a97b4d", "timber"),
    "foundation" to Material("#8c9299", "plain"),
    "roof" to Material("#6b7078", "plain"),
)

/** How a system's pieces look in the shaded world: its material's colour and kind where the page knows it, otherwise its colour in the palette. */
internal fun systemLook(order: List<String>, system: String): Map<String, Any?> {
    val material = MATERIALS[system.replace(' ', '_')]
    return if (material == null) mapOf("stroke" to colourOfSystem(order, system)) else mapOf("stroke" to material.colour, "material" to material.kind)
}
