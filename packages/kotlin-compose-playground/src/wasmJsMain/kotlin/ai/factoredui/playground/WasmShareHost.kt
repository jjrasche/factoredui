package ai.factoredui.playground

import ai.factoredui.compose.share.ShareHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun queryAsJson(): String =
    js("JSON.stringify(Object.fromEntries(new URLSearchParams(window.location.search).entries()))")

private fun replaceSearch(search: String) {
    js("window.history.replaceState(null, '', window.location.pathname + search + window.location.hash)")
}

private fun encodedComponent(text: String): String = js("encodeURIComponent(text)")

private fun storedText(key: String): String =
    js("(() => { try { return window.localStorage.getItem(key) || ''; } catch (e) { return ''; } })()")

private fun keptInStorage(key: String, text: String): Boolean =
    js("(() => { try { if (text === '') { window.localStorage.removeItem(key); } else { window.localStorage.setItem(key, text); } return true; } catch (e) { return false; } })()")

private fun encodedPair(name: String, value: String): String =
    "${encodedComponent(name)}=${encodedComponent(value).replace("%2C", ",").replace("%3A", ":")}"

object WasmShareHost : ShareHost {
    override fun readQuery(): Map<String, String> =
        Json.parseToJsonElement(queryAsJson()).jsonObject.mapValues { (_, value) -> value.jsonPrimitive.content }

    override fun replaceQuery(query: Map<String, String>) {
        replaceSearch(if (query.isEmpty()) "" else query.entries.joinToString("&", prefix = "?") { (name, value) -> encodedPair(name, value) })
    }

    override fun readStore(key: String): String? = storedText(key).ifEmpty { null }

    override fun writeStore(key: String, value: String?) {
        if (!keptInStorage(key, value.orEmpty())) error("browser storage refused the write")
    }
}
