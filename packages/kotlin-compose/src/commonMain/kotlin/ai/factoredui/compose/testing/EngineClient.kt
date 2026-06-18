package ai.factoredui.compose.testing

interface EngineClient {
    suspend fun query(path: String): Any?
    suspend fun post(path: String, body: Map<String, Any?>): Any?
}

class FakeEngineClient(
    private val state: MutableMap<String, Any?> = mutableMapOf(),
) : EngineClient {
    override suspend fun query(path: String): Any? = state[path]
    override suspend fun post(path: String, body: Map<String, Any?>): Any? {
        state[path] = body
        return body
    }
    fun seed(path: String, value: Any?) { state[path] = value }
}
