package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.stackedscene.StackedDrawing
import ai.factoredui.compose.stackedscene.fitDrawing
import ai.factoredui.compose.stackedscene.mmToCanvas
import ai.factoredui.compose.stackedscene.partBox
import ai.factoredui.compose.stackedscene.projectFaces
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val REVIEW_DIR = File(System.getProperty("user.dir"), "../../reviews/stacked-scene-2026-10-10")

private fun distinctColourCount(image: BufferedImage): Int = image.getRGB(0, 0, image.width, image.height, null, 0, image.width).toSet().size

class StackedBoardPicturesTest {

    private fun writePicture(name: String, view: String, picked: List<String> = emptyList(), board: Map<String, Any?> = emptyMap()) {
        val png = driveScreen(stackedBoardSpec(), stackedBoardStore(view, picked, board), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { it.frame().png }
        savePicture(name, png)
    }

    private fun savePicture(name: String, png: ByteArray) {
        val image = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(BOARD_SCREEN_WIDTH to BOARD_SCREEN_HEIGHT, image.width to image.height, name)
        assertTrue(distinctColourCount(image) > 1, "$name is a single flat colour: nothing was drawn")
        REVIEW_DIR.mkdirs()
        File(REVIEW_DIR, name).writeBytes(png)
    }

    @Test
    fun the_board_is_pictured_in_plan_iso_south_and_west_and_with_its_blocks_picked() {
        writePicture("01-plan.png", "plan")
        writePicture("02-iso.png", "iso")
        writePicture("03-south.png", "south")
        writePicture("04-west.png", "west")
        writePicture("05-iso-highlight-blocks.png", "iso", groupObjectIds("blocks"))
    }

    @Test
    fun the_iso_with_the_plates_hidden_shows_the_bars_in_their_scale_colours_on_neutral_blocks() {
        writePicture("06-iso-top-hidden.png", "iso", board = mapOf("hide_top" to "1"))
    }

    @Test
    fun tapping_a_bar_in_the_iso_with_the_plates_hidden_opens_its_popup() {
        val scene = readSceneView(stackedBoardText())
        val bar = scene.footprints!!.items.first { it.id == "r2" }
        driveScreen(stackedBoardSpec(), stackedBoardStore("iso", board = mapOf("hide_top" to "1")), BOARD_SCREEN_WIDTH, BOARD_SCREEN_HEIGHT).use { screen ->
            val area = assertNotNull(screen.boundsOf("board"))
            val fit = fitDrawing(scene, StackedDrawing.ISO, (area.right - area.left).value.toDouble(), (area.bottom - area.top).value.toDouble())
            val top = projectFaces(partBox(scene, bar), 0).top.map { (u, v) -> mmToCanvas(fit, u, v) }
            screen.tapAt(area.left.value + (top.sumOf { it.first } / top.size).toFloat(), area.top.value + (top.sumOf { it.second } / top.size).toFloat())
            assertEquals(listOf("Wide bar", "r2 · 90% · busy"), screen.textsUnder(popupTag("board")))
            savePicture("07-iso-bar-popup.png", screen.frame().png)
        }
    }
}
