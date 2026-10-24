package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val VIEW = """{ "title": "Step stool", "world": "stool", "answers": "stool.fixture.json" }"""

private fun step(title: String, parts: List<String>, screws: Int, ask46: String = "q46", ask710: String = "q710") = """{
  "title": "$title", "parts": [${parts.joinToString(",") { "\"$it\"" }}], "screws": $screws,
  "adult": "drives the drill", "child_4_6": "holds the board", "child_7_10": "marks the spot",
  "ask_4_6": "$ask46", "ask_7_10": "$ask710", "shows": "the outline on the blueprint", "learns": "a plan is a list of parts" }"""

private fun answer(steps: List<String> = listOf(step("Lay out", emptyList(), 0, "Which board is longest?"), step("Join", listOf("side", "top"), 4))) = """{
  "fixture": "made-up stool", "title": "Step stool", "load_lb": 150,
  "parts": [
    { "id": "side", "name": "Side board", "x": 0, "y": 0, "z": 0, "dx": 19, "dy": 235, "dz": 229 },
    { "id": "top", "name": "Top step", "x": 0, "y": 0, "z": 229, "dx": 305, "dy": 235, "dz": 19 } ],
  "steps": [${steps.joinToString(",")}],
  "end_questions": ["What do you know now?", "What would you change?"] }"""

private fun planOf(text: String = answer()) = readCardsPlan(Json.parseToJsonElement(text).jsonObject)

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + (this["children"]?.jsonArray ?: emptyList()).flatMap { it.jsonObject.nodes() }

private fun JsonObject.texts(): List<String> = nodes().mapNotNull { (it["props"] as? JsonObject)?.get("value")?.jsonPrimitive?.content }

private val screen = readCardsScreen(VIEW)

class CardsScreenTest {
    @Test
    fun there_is_one_card_per_build_step_and_one_for_the_ending() {
        val root = cardsSpec(screen, planOf())["root"]!!.jsonObject
        val cards = root.nodes().filter { it["id"]!!.jsonPrimitive.content.startsWith("card.") && it["id"]!!.jsonPrimitive.content.count { c -> c == '.' } == 1 }
        assertEquals(listOf("card.0", "card.1", "card.end"), cards.map { it["id"]!!.jsonPrimitive.content })
    }

    @Test
    fun a_card_shows_only_when_the_card_binding_names_it() {
        val root = cardsSpec(screen, planOf())["root"]!!.jsonObject
        assertEquals("{card}=1", root.nodes().first { it["id"]!!.jsonPrimitive.content == "card.1" }["visible"]!!.jsonPrimitive.content)
    }

    @Test
    fun a_card_names_its_parts_with_inch_sizes_its_screws_and_both_ages_questions() {
        val texts = cardsSpec(screen, planOf())["root"]!!.jsonObject.nodes().first { it["id"]!!.jsonPrimitive.content == "card.1" }.texts()
        assertTrue("Step 2 of 2: Join" in texts)
        assertTrue("Side board: 9 1/4 x 9 x 3/4 in" in texts)
        assertTrue("Screws: 4" in texts)
        assertTrue("Ask, ages 4 to 6: q46" in texts)
        assertTrue("Ask, ages 7 to 10: q710" in texts)
    }

    @Test
    fun the_ending_never_names_a_prize_and_states_the_engines_checked_load() {
        val texts = cardsSpec(screen, planOf())["root"]!!.jsonObject.nodes().first { it["id"]!!.jsonPrimitive.content == "card.end" }.texts()
        assertTrue(texts.any { "tested to hold 150 lb" in it })
        assertTrue(cardsSpec(screen, planOf())["root"]!!.jsonObject.texts().none { Regex("\\b(earn|win|prize|reward)", RegexOption.IGNORE_CASE).containsMatchIn(it) })
    }

    @Test
    fun a_stool_with_no_checked_load_makes_no_weight_claim() {
        val plan = planOf(answer().replace("\"load_lb\": 150,", ""))
        assertTrue(cardsSpec(screen, plan)["root"]!!.jsonObject.texts().none { "tested to hold" in it })
    }

    @Test
    fun the_answers_children_write_to_their_own_binding_paths() {
        val inputs = cardsSpec(screen, planOf())["root"]!!.jsonObject.nodes().filter { it["type"]!!.jsonPrimitive.content == "textinput" }
        assertEquals(listOf("{child.name}", "{answers.first}", "{answers.second}"), inputs.map { (it["props"] as JsonObject)["value"]!!.jsonPrimitive.content })
    }

    @Test
    fun a_part_used_in_two_steps_is_refused_by_name() {
        val twice = answer(listOf(step("a", listOf("side", "top"), 1), step("b", listOf("side"), 1)))
        assertTrue("side" in assertFailsWith<CardsFormatException> { planOf(twice) }.message!!)
    }

    @Test
    fun a_part_used_in_no_step_is_refused_by_name() {
        val missing = answer(listOf(step("a", listOf("side"), 1)))
        assertTrue("top" in assertFailsWith<CardsFormatException> { planOf(missing) }.message!!)
    }

    @Test
    fun a_step_with_no_question_for_an_age_is_refused() {
        val bare = answer(listOf(step("a", listOf("side", "top"), 1, ask46 = "")))
        assertTrue("question" in assertFailsWith<CardsFormatException> { planOf(bare) }.message!!)
    }

    @Test
    fun the_blueprint_is_one_light_letter_page_of_views_in_inches_and_the_parts_list() {
        val spec = cardsBlueprintSpec(screen, planOf())
        assertEquals("light", spec["theme"]!!.jsonPrimitive.content)
        val texts = spec["root"]!!.jsonObject.texts()
        assertTrue("Side board: 9 1/4 x 9 x 3/4 in" in texts)
        assertTrue(spec["root"]!!.jsonObject.nodes().any { it["id"]!!.jsonPrimitive.content == "blueprint.front" })
        assertTrue(Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), spec).contains("12 in"))
    }

    @Test
    fun every_part_is_named_in_every_view_so_a_small_rectangle_never_reads_as_a_hole() {
        val views = cardsBlueprintSpec(screen, planOf())["root"]!!.jsonObject.nodes().filter { it["type"]?.jsonPrimitive?.content == "plan" }
        assertEquals(3, views.size)
        views.forEach { view ->
            val labels = (view["props"] as JsonObject)["shapes"]!!.jsonArray.map { it.jsonObject }.filter { it["kind"]!!.jsonPrimitive.content == "label" }.map { it["text"]!!.jsonPrimitive.content }
            assertTrue("Side board" in labels && "Top step" in labels)
        }
    }

    @Test
    fun a_vertical_dimension_label_ends_beside_its_line_and_never_on_it() {
        val front = cardsBlueprintSpec(screen, planOf())["root"]!!.jsonObject.nodes().first { it["id"]!!.jsonPrimitive.content == "blueprint.front" }
        val label = (front["props"] as JsonObject)["shapes"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == "dim.front.height.text" }
        assertEquals("end", label["align"]!!.jsonPrimitive.content)
        assertTrue(label["dx_dp"]!!.jsonPrimitive.content.toInt() < 0)
    }

    @Test
    fun each_card_shows_the_whole_stool_with_this_steps_boards_highlighted_and_the_rest_ghosted() {
        val root = cardsSpec(screen, planOf())["root"]!!.jsonObject
        fun props(id: String) = root.nodes().first { it["id"]!!.jsonPrimitive.content == id }["props"] as JsonObject
        assertEquals(listOf("side", "top"), props("card.1.stool")["highlight"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(emptyList(), props("card.0.stool")["highlight"]!!.jsonArray.toList())
        assertEquals(listOf("side", "top"), props("card.0.stool")["boxes"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
    }
}
