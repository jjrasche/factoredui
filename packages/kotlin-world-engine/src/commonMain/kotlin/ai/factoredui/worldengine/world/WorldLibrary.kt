package ai.factoredui.worldengine.world

fun interface WorldLibrary {
    fun readText(path: String): String?
}

class MapWorldLibrary(files: Map<String, String>) : WorldLibrary {
    private val filesByPath: Map<String, String> = files.mapKeys { (path, _) -> normalizeWorldPath(path) }

    val paths: List<String> get() = filesByPath.keys.sorted()

    override fun readText(path: String): String? = filesByPath[normalizeWorldPath(path)]
}

fun normalizeWorldPath(path: String): String {
    val segments = mutableListOf<String>()
    path.replace('\\', '/').split('/').forEach { segment -> appendSegment(segments, segment) }
    return segments.joinToString("/")
}

private fun appendSegment(segments: MutableList<String>, segment: String) {
    when {
        segment.isEmpty() || segment == "." -> Unit
        segment == ".." && segments.isNotEmpty() && segments.last() != ".." -> segments.removeAt(segments.lastIndex)
        else -> segments += segment
    }
}

fun resolveSiblingPath(path: String, relative: String): String {
    val directory = normalizeWorldPath(path).substringBeforeLast('/', "")
    return normalizeWorldPath(if (directory.isEmpty()) relative else "$directory/$relative")
}

fun fileNameOf(path: String): String = normalizeWorldPath(path).substringAfterLast('/')
