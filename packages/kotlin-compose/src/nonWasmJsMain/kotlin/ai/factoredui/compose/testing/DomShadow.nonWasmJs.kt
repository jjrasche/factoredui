package ai.factoredui.compose.testing

import java.util.concurrent.ConcurrentHashMap

actual object DomShadow {
    private val store = ConcurrentHashMap<String, Entry>()

    actual fun emit(id: String, role: String, attrs: Map<String, String?>) {
        store[id] = Entry(id = id, role = role, attrs = attrs.filterValues { it != null }.mapValues { it.value!! })
    }

    actual fun remove(id: String) { store.remove(id) }

    fun snapshot(): List<Entry> = store.values.toList()

    fun byRole(role: String): List<Entry> = store.values.filter { it.role == role }

    data class Entry(val id: String, val role: String, val attrs: Map<String, String>)
}
