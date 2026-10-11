package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SpecSectionException(message: String) : IllegalArgumentException(message)

private val sectionDecoder = Json { ignoreUnknownKeys = true }

/** The spec in [text], or with a [section] the spec at that top-level key of a file holding other sections beside it. */
fun decodeSpecFile(text: String, section: String?): Spec {
    if (section == null) return sectionDecoder.decodeFromString(Spec.serializer(), text)
    val file = sectionDecoder.parseToJsonElement(text) as? JsonObject ?: throw SpecSectionException("a file with sections must be a JSON object")
    val spec = file[section] ?: throw SpecSectionException("the file has no section '$section'; its top-level keys are ${file.keys.joinToString()}")
    if (spec !is JsonObject || "root" !in spec) throw SpecSectionException("section '$section' must be a spec object with a root, not $spec")
    return sectionDecoder.decodeFromJsonElement(Spec.serializer(), spec)
}
