package ai.factoredui.application

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SEASON = """{"clock": {"step": 1.0, "unit": "hour", "until": 3}, "steps": [
    {"t": 2757, "stocks": {"air_temperature_f": 60.0}, "series": {"outside_air_f": 30.0}},
    {"t": 2758, "stocks": {"air_temperature_f": 61.0}, "series": {"outside_air_f": 31.0}},
    {"t": 2759, "stocks": {"air_temperature_f": 62.0}, "series": {"outside_air_f": 32.0}}]}"""

private const val LOADED_DRAWN = """{"sections": {"expansion": {"ok": true, "asked": true, "extent_mm": [1000, 100], "objects": [{"id": "wall-a", "system": "walls", "type": "wall", "x_mm": 0, "y_mm": 0, "width_mm": 1000, "depth_mm": 100, "level": 0, "elevation_mm": 0, "fields": {"height_mm": 3000}}], "instances": [], "supplied": {}}}}"""

private const val LOADED = """{"sections": {"expansion": {"ok": true, "asked": true, "extent_mm": [10, 10], "objects": [], "instances": [], "supplied": {}}}}"""

private fun nodesIn(element: JsonElement): List<JsonObject> = when (element) {
    is JsonObject -> listOfNotNull(element.takeIf { it["id"] is JsonPrimitive && it["type"] is JsonPrimitive }) + element.values.flatMap(::nodesIn)
    is JsonArray -> element.flatMap(::nodesIn)
    else -> emptyList()
}

private fun JsonObject.idText() = (this["id"] as JsonPrimitive).content

private fun JsonObject.propText(name: String) = ((this["props"] as JsonObject)[name] as? JsonPrimitive)?.content

class WorldsServerTest {
    private val engine = FakeEngine()
    private val bundle: Path = Files.createTempDirectory("bundle").also { Files.writeString(it.resolve("app.js"), "app") }
    private val client = HttpClient.newHttpClient()
    private val worldsRoot: Path = Files.createTempDirectory("worlds")

    private fun addWorld(directory: String, id: String, screens: Map<String, String> = emptyMap()): Path {
        val folder = Files.createDirectories(worldsRoot.resolve(directory))
        val file = folder.resolve("world.json").also { Files.writeString(it, """{ "id": "$id", "title": "$id", "systems": {}, "uses": [] }""") }
        screens.forEach { (name, text) -> Files.writeString(Files.createDirectories(folder.resolve("views")).resolve(name), text) }
        return file
    }

    private fun greenhouseScreen() = checkNotNull(WorldsServerTest::class.java.getResource("/greenhouse-day.screen.json")).readText()

    private fun engineLists(vararg worlds: Pair<String, Path>) {
        engine.worldsAnswer = """{"worlds": [${worlds.joinToString(",") { """{"id": "${it.first}", "path": ${JsonPrimitive(it.second.toString())}}""" }}]}"""
    }

    private fun answeringAsTheEngineDoes() {
        engine.runAnswer = { request -> if ((request["command"] as JsonPrimitive).content == "simulate") SEASON else LOADED }
    }

    private val servers = mutableListOf<HttpServer>()

    private fun serving(): Int {
        val server = startWorldsServer(WorldsOptions(engine.address, bundle, "127.0.0.1", 0))
        servers += server
        return server.address.port
    }

    private fun fetch(port: Int, path: String): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).build(), HttpResponse.BodyHandlers.ofString())

    private fun specFetched(port: Int, query: String = ""): List<JsonObject> {
        val answer = fetch(port, "/spec.json$query")
        assertEquals(200, answer.statusCode(), answer.body())
        return nodesIn(Json.parseToJsonElement(answer.body()))
    }

    private fun twoWorlds(): Int {
        val greenhouse = addWorld("greenhouse", "greenhouse-world", mapOf("day.screen.json" to greenhouseScreen()))
        val house = addWorld("house", "small-house")
        engineLists("greenhouse-world" to greenhouse, "small-house" to house)
        answeringAsTheEngineDoes()
        return serving()
    }

    @AfterTest
    fun stopAll() {
        servers.forEach { it.stop(0) }
        engine.stop()
    }

    @Test
    fun a_request_that_names_no_world_draws_the_first_world_the_engine_lists_by_its_first_view() {
        val nodes = specFetched(twoWorlds())
        assertTrue(nodes.any { it.idText() == "chart" })
    }

    @Test
    fun a_request_for_a_world_with_no_screen_draws_its_overview() {
        val nodes = specFetched(twoWorlds(), "?world=small-house")
        assertTrue(nodes.any { it.idText() == "systems" })
        assertTrue(nodes.none { it.idText() == "chart" })
    }

    @Test
    fun the_world_is_chosen_by_each_request_and_not_remembered_between_them() {
        val port = twoWorlds()
        val seen = listOf("greenhouse-world", "small-house", "greenhouse-world").map { world -> specFetched(port, "?world=$world").any { it.idText() == "chart" } }
        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun the_chart_is_drawn_from_the_rows_of_the_engines_simulation_of_that_world() {
        val port = twoWorlds()
        specFetched(port, "?world=greenhouse-world")
        val simulated = engine.posted.filter { (it["command"] as JsonPrimitive).content == "simulate" }
        assertEquals(1, simulated.size)
        assertEquals(JsonPrimitive(worldsRoot.resolve("greenhouse").resolve("world.json").toString()), simulated.single()["config"])
        val chart = specFetched(port, "?world=greenhouse-world").single { it.idText() == "chart" }
        assertEquals(3, ((chart["props"] as JsonObject)["rows"] as JsonArray).size)
    }

    @Test
    fun a_view_named_in_the_request_is_the_one_drawn() {
        val nodes = specFetched(twoWorlds(), "?world=greenhouse-world&view=overview")
        assertTrue(nodes.any { it.idText() == "systems" })
        assertTrue(nodes.none { it.idText() == "chart" })
    }

    @Test
    fun the_bar_is_one_select_that_lists_every_world_by_a_short_name_and_holds_the_chosen_one() {
        val spec = Json.parseToJsonElement(fetch(twoWorlds(), "/spec.json?world=small-house").body()) as JsonObject
        val select = nodesIn(spec).single { it.idText() == "bar.world" }
        val options = ((select["props"] as JsonObject)["options"] as JsonArray).map { it as JsonObject }
        assertEquals(listOf("Greenhouse", "Small house"), options.map { (it["label"] as JsonPrimitive).content })
        assertEquals(JsonPrimitive("{nav.world}"), (select["props"] as JsonObject)["value"])
        assertEquals(JsonPrimitive("/spec.json?world=small-house"), (spec["state"] as JsonObject)["nav.world"])
    }

    @Test
    fun the_page_names_the_current_world_by_its_own_title_once() {
        val nodes = specFetched(twoWorlds(), "?world=small-house")
        assertEquals(listOf("small-house"), nodes.filter { it.idText() == "bar.title" }.map { it.propText("value") })
    }

    @Test
    fun a_world_that_gives_a_short_title_is_listed_by_it() {
        val file = addWorld("van", "van-world")
        Files.writeString(file, """{ "id": "van-world", "short_title": "The van", "title": "Which vehicle replaces the Civic", "systems": {}, "uses": [] }""")
        engineLists("van-world" to file)
        answeringAsTheEngineDoes()
        val port = serving()
        val select = specFetched(port).single { it.idText() == "bar.world" }
        assertEquals(listOf("The van"), ((select["props"] as JsonObject)["options"] as JsonArray).map { ((it as JsonObject)["label"] as JsonPrimitive).content })
        assertEquals(listOf("Which vehicle replaces the Civic"), specFetched(port).filter { it.idText() == "bar.title" }.map { it.propText("value") })
    }

    @Test
    fun on_an_overview_with_a_drawing_the_bar_is_the_first_thing_in_the_dock() {
        val port = twoWorlds()
        engine.runAnswer = { LOADED_DRAWN }
        val spec = Json.parseToJsonElement(fetch(port, "/spec.json?world=small-house").body()) as JsonObject
        val dock = nodesIn(spec).single { it.idText() == "dock" }
        assertEquals("bar", ((dock["children"] as JsonArray).first() as JsonObject).idText())
    }

    @Test
    fun on_an_overview_with_no_drawing_the_bar_is_the_first_thing_on_the_scrolling_page() {
        val spec = Json.parseToJsonElement(fetch(twoWorlds(), "/spec.json?world=small-house").body()) as JsonObject
        val root = spec["root"] as JsonObject
        assertEquals(JsonPrimitive("scrollview"), root["type"])
        assertEquals("bar", ((root["children"] as JsonArray).first() as JsonObject).idText())
    }
    @Test
    fun many_worlds_stay_one_select_and_do_not_grow_the_bar() {
        val worlds = listOf("a", "b", "c", "d", "e").map { it to addWorld(it, it) }
        engineLists(*worlds.toTypedArray())
        answeringAsTheEngineDoes()
        val nodes = specFetched(serving())
        assertEquals(1, nodes.count { it.idText() == "bar.world" })
        assertEquals(5, (((nodes.single { it.idText() == "bar.world" }["props"] as JsonObject)["options"]) as JsonArray).size)
    }

    @Test
    fun choosing_a_world_opens_the_spec_whose_address_is_the_options_value_through_the_hosts_open_spec_action() {
        val nodes = specFetched(twoWorlds(), "?world=small-house")
        val select = nodes.single { it.idText() == "bar.world" }
        val action = select["action"] as JsonObject
        assertEquals(JsonPrimitive("open_spec"), action["action"])
        assertEquals(JsonPrimitive("{nav.world}"), (action["params"] as JsonObject)["spec"])
        val values = (((select["props"] as JsonObject)["options"]) as JsonArray).map { ((it as JsonObject)["value"] as JsonPrimitive).content }
        assertEquals(listOf("/spec.json?world=greenhouse-world", "/spec.json?world=small-house"), values)
    }

    @Test
    fun a_world_with_more_than_one_view_lists_them_and_a_world_with_one_lists_none() {
        val port = twoWorlds()
        assertEquals(listOf("views.day", "views.overview"), specFetched(port, "?world=greenhouse-world").map { it.idText() }.filter { it.startsWith("views.") })
        assertEquals(emptyList(), specFetched(port, "?world=small-house").map { it.idText() }.filter { it.startsWith("views.") })
    }

    @Test
    fun a_view_chip_keeps_the_world_and_names_the_view() {
        val chip = specFetched(twoWorlds(), "?world=greenhouse-world").single { it.idText() == "views.overview" }
        val spec = ((chip["action"] as JsonObject)["params"] as JsonObject)["spec"]
        assertEquals(JsonPrimitive("/spec.json?world=greenhouse-world&view=overview"), spec)
    }

    @Test
    fun a_world_id_with_a_slash_is_encoded_in_the_option_and_found_again() {
        val file = addWorld("odd", "folder/odd world")
        engineLists("folder/odd world" to file)
        answeringAsTheEngineDoes()
        val port = serving()
        val select = specFetched(port).single { it.idText() == "bar.world" }
        val target = ((((select["props"] as JsonObject)["options"]) as JsonArray).single() as JsonObject)["value"] as JsonPrimitive
        assertEquals("/spec.json?world=" + URLEncoder.encode("folder/odd world", Charsets.UTF_8), target.content)
        assertTrue(specFetched(port, "?world=" + URLEncoder.encode("folder/odd world", Charsets.UTF_8)).any { it.idText() == "systems" })
    }

    @Test
    fun a_world_the_engine_does_not_list_is_named_on_a_page_that_still_has_the_bar() {
        val nodes = specFetched(twoWorlds(), "?world=nowhere")
        assertTrue(nodes.any { it.idText() == "bar.world" })
        val words = nodes.mapNotNull { it.propText("value") }
        assertTrue(words.any { it.contains("nowhere") }, words.toString())
    }

    @Test
    fun an_engine_that_lists_no_worlds_is_said_on_the_page() {
        engineLists()
        val words = specFetched(serving()).mapNotNull { it.propText("value") }
        assertTrue(words.any { it.contains("no worlds") }, words.toString())
    }

    @Test
    fun an_engine_that_is_down_makes_a_502_that_names_it() {
        val port = twoWorlds()
        val address = engine.address.toString()
        engine.stop()
        val answer = fetch(port, "/spec.json")
        assertEquals(502, answer.statusCode())
        assertTrue(answer.body().contains(address), answer.body())
    }

    @Test
    fun a_screen_the_engines_rows_cannot_fill_is_said_on_the_page_with_the_bar_kept() {
        val port = twoWorlds()
        engine.runAnswer = { """{"clock": {}}""" }
        val nodes = specFetched(port, "?world=greenhouse-world")
        assertTrue(nodes.any { it.idText() == "bar.world" })
        assertTrue(nodes.none { it.idText() == "chart" })
        assertNotNull(nodes.firstOrNull { it.propText("value")?.contains("steps") == true })
    }

    private fun post(port: Int, path: String, body: String): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun an_action_the_page_sends_is_acknowledged_as_not_acted_on_and_says_why_instead_of_a_404() {
        val answer = post(twoWorlds(), "/action", """{"action":"react","params":{"kind":"lost_me"}}""")
        assertEquals(200, answer.statusCode())
        val body = Json.parseToJsonElement(answer.body()) as JsonObject
        assertEquals(JsonPrimitive(false), body["handled"])
        assertEquals(JsonPrimitive("react"), body["action"])
        assertTrue((body["reason"] as JsonPrimitive).content.contains("takes no actions"), answer.body())
    }

    @Test
    fun an_action_request_that_is_not_an_object_with_an_action_name_is_a_400_that_says_what_it_expected() {
        val port = twoWorlds()
        listOf("not json", """{"params":{}}""", """["react"]""").forEach { body ->
            val answer = post(port, "/action", body)
            assertEquals(400, answer.statusCode(), body)
            assertTrue((Json.parseToJsonElement(answer.body()) as JsonObject).getValue("reason").toString().contains("action"), answer.body())
        }
    }

    @Test
    fun a_get_of_the_action_route_is_refused_as_the_wrong_method() {
        assertEquals(405, fetch(twoWorlds(), "/action").statusCode())
    }

    @Test
    fun the_wasm_bundle_is_served_beside_the_spec() {
        val port = twoWorlds()
        assertEquals("app", fetch(port, "/app.js").body())
        assertNull(fetch(port, "/app.js").headers().firstValue("Location").orElse(null))
    }
}
