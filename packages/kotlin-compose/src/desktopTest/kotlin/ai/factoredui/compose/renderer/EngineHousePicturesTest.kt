package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.structure.StructureDrawing
import ai.factoredui.compose.structure.fitDrawing
import ai.factoredui.compose.structure.mmToCanvas
import ai.factoredui.compose.structure.partBox
import ai.factoredui.compose.structure.projectFaces
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val REVIEW_DIR = File(System.getProperty("user.dir"), "../../reviews/scene-structure-2026-10-09")

private fun distinctColourCount(image: BufferedImage): Int = image.getRGB(0, 0, image.width, image.height, null, 0, image.width).toSet().size

class EngineHousePicturesTest {

    private fun writePicture(name: String, view: String, picked: List<String> = emptyList(), house: Map<String, Any?> = emptyMap()) {
        val png = driveScreen(engineHouseSpec(), engineHouseStore(view, picked, house), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { it.frame().png }
        savePicture(name, png)
    }

    private fun savePicture(name: String, png: ByteArray) {
        val image = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(HOUSE_SCREEN_WIDTH to HOUSE_SCREEN_HEIGHT, image.width to image.height, name)
        assertTrue(distinctColourCount(image) > 1, "$name is a single flat colour: nothing was drawn")
        REVIEW_DIR.mkdirs()
        File(REVIEW_DIR, name).writeBytes(png)
    }

    @Test
    fun the_engine_house_is_pictured_in_plan_iso_south_and_west_and_with_its_piers_picked() {
        writePicture("01-plan.png", "plan")
        writePicture("02-iso.png", "iso")
        writePicture("03-south.png", "south")
        writePicture("04-west.png", "west")
        writePicture("05-iso-highlight-pier.png", "iso", bomRowIds("pier"))
    }

    @Test
    fun the_iso_with_the_panels_hidden_shows_the_beams_coloured_by_utilisation_on_their_grey_piers() {
        writePicture("06-iso-top-hidden-utilisation.png", "iso", house = mapOf("hide_top" to "1"))
    }

    @Test
    fun tapping_a_beam_in_the_iso_with_the_panels_hidden_opens_its_popup() {
        val scene = readSceneView(engineHouseText())
        val beam = scene.footprints!!.items.first { it.id == "e14" }
        driveScreen(engineHouseSpec(), engineHouseStore("iso", house = mapOf("hide_top" to "1")), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            val area = assertNotNull(screen.boundsOf("house"))
            val fit = fitDrawing(scene, StructureDrawing.ISO, (area.right - area.left).value.toDouble(), (area.bottom - area.top).value.toDouble())
            val top = projectFaces(partBox(scene, beam)).top.map { (u, v) -> mmToCanvas(fit, u, v) }
            screen.tapAt(area.left.value + (top.sumOf { it.first } / top.size).toFloat(), area.top.value + (top.sumOf { it.second } / top.size).toFloat())
            assertEquals(listOf("Interior 2-ply LVL beam", "e14 · 32%"), screen.textsUnder(popupTag("house")))
            savePicture("07-iso-beam-popup.png", screen.frame().png)
        }
    }
}
