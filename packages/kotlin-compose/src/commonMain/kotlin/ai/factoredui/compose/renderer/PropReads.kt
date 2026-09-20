package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode

private class ReadRecordingMap<V>(
    private val delegate: Map<String, V>,
    private val onRead: (String) -> Unit,
) : Map<String, V> by delegate {
    override fun get(key: String): V? {
        onRead(key)
        return delegate[key]
    }

    override fun containsKey(key: String): Boolean {
        onRead(key)
        return delegate.containsKey(key)
    }
}

// Off unless record() is running, so a live render pays nothing for the seam.
object PropReads {

    private val reads = mutableMapOf<String, MutableSet<String>>()
    private var recording = false

    fun <T> record(block: () -> T): Pair<T, Map<String, Set<String>>> {
        reads.clear()
        recording = true
        try {
            val result = block()
            return result to reads.mapValues { (_, keys) -> keys.toSet() }
        } finally {
            recording = false
        }
    }

    internal fun instrument(node: SpecNode): SpecNode =
        if (!recording) node else node.copy(props = watch(node.id, node.props))

    internal fun watchResolved(nodeId: String, props: Map<String, Any?>): Map<String, Any?> =
        if (!recording) props else watch(nodeId, props)

    private fun <V> watch(nodeId: String, props: Map<String, V>): Map<String, V> =
        ReadRecordingMap(props) { key -> reads.getOrPut(nodeId) { mutableSetOf() } += key }
}
