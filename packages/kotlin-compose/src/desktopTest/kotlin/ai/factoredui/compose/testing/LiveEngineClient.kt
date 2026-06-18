package ai.factoredui.compose.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LiveEngineClient(private val baseUrl: String) : EngineClient {

    private val http = HttpClient(OkHttp)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun query(path: String): Any? = runCatching {
        val body = http.get("$baseUrl$path").bodyAsText()
        json.parseToJsonElement(body)
    }.getOrNull()

    override suspend fun post(path: String, body: Map<String, Any?>): Any? = runCatching {
        val jsonBody = buildJsonObject {
            body.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Double -> put(k, v)
                    is Float -> put(k, v.toDouble())
                    is Int -> put(k, v.toLong())
                    is Boolean -> put(k, v)
                    else -> put(k, v?.toString() ?: "null")
                }
            }
        }
        val resp = http.post("$baseUrl$path") {
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
            setBody(jsonBody.toString())
        }
        json.parseToJsonElement(resp.bodyAsText())
    }.getOrNull()

    fun close() { http.close() }
}

fun liveEngineClient(baseUrl: String): LiveEngineClient = LiveEngineClient(baseUrl)
