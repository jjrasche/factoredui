package ai.factoredui.compose.share

import ai.factoredui.compose.schema.BindingResolver
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

@Serializable
enum class ShareFormat {
    @SerialName("text") TEXT,
    @SerialName("number") NUMBER,
    @SerialName("bool") BOOL,
    @SerialName("list") LIST,
    @SerialName("numbers") NUMBERS,
}

/** One binding the page keeps in its address (`query`), in browser storage (`store`), or both; the address wins. */
@Serializable
data class ShareEntry(
    val path: String,
    val query: String? = null,
    val store: String? = null,
    val format: ShareFormat = ShareFormat.TEXT,
    val decimals: Int? = null,
    @SerialName("keep_empty") val keepEmpty: Boolean = false,
)

/** The page's address and browser storage, behind one seam so the rule is the same on every host. */
interface ShareHost {
    fun readQuery(): Map<String, String>
    fun replaceQuery(query: Map<String, String>)
    fun readStore(key: String): String?
    fun writeStore(key: String, value: String?)
}

object NoShareHost : ShareHost {
    override fun readQuery(): Map<String, String> = emptyMap()
    override fun replaceQuery(query: Map<String, String>) = Unit
    override fun readStore(key: String): String? = null
    override fun writeStore(key: String, value: String?) = Unit
}

sealed interface ShareParse {
    data class Value(val value: Any) : ShareParse
    data class Malformed(val reason: String) : ShareParse
}

data class SharedStart(val values: Map<String, Any?>, val notices: List<String>)

private val ID_PATTERN = Regex("""[A-Za-z0-9_.:#-]+""")

fun ShareEntry.parse(raw: String): ShareParse = when (format) {
    ShareFormat.TEXT -> ShareParse.Value(raw)
    ShareFormat.NUMBER -> numberOf(raw)?.let { ShareParse.Value(it) } ?: ShareParse.Malformed("'$raw' is not a number")
    ShareFormat.BOOL -> boolOf(raw)?.let { ShareParse.Value(it) } ?: ShareParse.Malformed("'$raw' is not 1 or 0")
    ShareFormat.LIST -> listValue(raw)
    ShareFormat.NUMBERS -> numbersValue(raw)
}

private fun numberOf(raw: String): Double? = raw.toDoubleOrNull()?.takeIf { it.isFinite() }

private fun boolOf(raw: String): Boolean? = when (raw) {
    "1", "true" -> true
    "0", "false" -> false
    else -> null
}

private fun listValue(raw: String): ShareParse {
    if (raw.isEmpty()) return ShareParse.Value(emptyList<String>())
    val parts = raw.split(",")
    val bad = parts.firstOrNull { !ID_PATTERN.matches(it) }
    return if (bad == null) ShareParse.Value(parts) else ShareParse.Malformed("'$raw' is not a comma list of ids")
}

private fun numbersValue(raw: String): ShareParse {
    if (raw.isEmpty()) return ShareParse.Value(emptyList<Double>())
    val parts = raw.split(",").map { numberOf(it) }
    return if (parts.all { it != null }) ShareParse.Value(parts.filterNotNull()) else ShareParse.Malformed("'$raw' is not a comma list of numbers")
}

fun ShareEntry.render(value: Any?): String? = when {
    value == null -> null
    format == ShareFormat.TEXT -> (value as? String)?.takeIf { it.isNotEmpty() }
    format == ShareFormat.NUMBER -> (value as? Number)?.toDouble()?.takeIf { it.isFinite() }?.let { numberText(it, decimals) }
    format == ShareFormat.BOOL -> (value as? Boolean)?.let { if (it) "1" else "0" }
    format == ShareFormat.LIST -> (value as? List<*>)?.let { items -> items.joinToString(",") { it.toString() }.takeUnless { items.isEmpty() && !keepEmpty } }
    else -> renderNumbers(value)
}

private fun ShareEntry.renderNumbers(value: Any?): String? {
    val items = (value as? List<*>)?.map { (it as? Number)?.toDouble()?.takeIf { number -> number.isFinite() } } ?: return null
    if (items.any { it == null }) return null
    return items.filterNotNull().joinToString(",") { numberText(it, decimals) }.takeUnless { items.isEmpty() && !keepEmpty }
}

private fun numberText(value: Double, decimals: Int?): String {
    if (decimals == null) return if (value == floor(value) && abs(value) < WHOLE_NUMBER_LIMIT) value.toLong().toString() else value.toString()
    val factor = 10.0.pow(decimals)
    val scaled = round(abs(value) * factor).toLong()
    val unit = factor.toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val fraction = (scaled % unit).toString().padStart(decimals, '0').trimEnd('0')
    return sign + (scaled / unit) + if (fraction.isEmpty()) "" else ".$fraction"
}

private const val WHOLE_NUMBER_LIMIT = 1e15

/** The values the page starts with: the address first, the store for what the address lacks, nothing for the rest. */
fun resolveShared(entries: List<ShareEntry>, host: ShareHost): SharedStart {
    val address = runCatching { host.readQuery() }.getOrDefault(emptyMap())
    val values = mutableMapOf<String, Any?>()
    val notices = mutableListOf<String>()
    entries.forEach { entry ->
        val found = readFromAddress(entry, address, notices) ?: readFromStore(entry, host, notices)
        if (found != null) values[entry.path] = found.value
    }
    return SharedStart(values, notices)
}

private fun readFromAddress(entry: ShareEntry, address: Map<String, String>, notices: MutableList<String>): ShareParse.Value? {
    val name = entry.query ?: return null
    val raw = address[name] ?: return null
    return when (val parsed = entry.parse(raw)) {
        is ShareParse.Value -> parsed
        is ShareParse.Malformed -> { notices += "address parameter '$name' was ignored: ${parsed.reason}"; null }
    }
}

private fun readFromStore(entry: ShareEntry, host: ShareHost, notices: MutableList<String>): ShareParse.Value? {
    val key = entry.store ?: return null
    val raw = runCatching { host.readStore(key) }.getOrNull() ?: return null
    return when (val parsed = entry.parse(raw)) {
        is ShareParse.Value -> parsed
        is ShareParse.Malformed -> { notices += "stored value '$key' was ignored: ${parsed.reason}"; null }
    }
}

/** Writes the shared bindings out as they change: the address and the store, each only when its text differs from the last write. */
class ShareWriter(private val entries: List<ShareEntry>, private val host: ShareHost) {
    private var lastQuery: Map<String, String>? = null
    private val lastStored = mutableMapOf<String, String?>()
    private val failedKeys = mutableSetOf<String>()

    fun onData(data: Map<String, Any?>): List<String> {
        val notices = mutableListOf<String>()
        writeQuery(data, notices)
        writeStores(data, notices)
        return notices
    }

    private fun rendered(entry: ShareEntry, data: Map<String, Any?>): String? =
        entry.render(BindingResolver.resolveBinding("{${entry.path}}", data))

    private fun writeQuery(data: Map<String, Any?>, notices: MutableList<String>) {
        val names = entries.mapNotNull { it.query }.toSet()
        val wanted = entries.filter { it.query != null }.mapNotNull { entry -> rendered(entry, data)?.let { entry.query!! to it } }.toMap()
        if (wanted == lastQuery) return
        runCatching { host.replaceQuery(runCatching { host.readQuery() }.getOrDefault(emptyMap()).filterKeys { it !in names } + wanted) }
            .onFailure { notices += "the address could not be updated: ${it.message}" }
        lastQuery = wanted
    }

    private fun writeStores(data: Map<String, Any?>, notices: MutableList<String>) {
        entries.filter { it.store != null }.forEach { entry ->
            val key = entry.store!!
            val text = rendered(entry, data)
            if (lastStored[key] == text) return@forEach
            lastStored[key] = text
            runCatching { host.writeStore(key, text) }
                .onFailure { if (failedKeys.add(key)) notices += "'$key' could not be kept in this browser: ${it.message}" }
        }
    }
}
