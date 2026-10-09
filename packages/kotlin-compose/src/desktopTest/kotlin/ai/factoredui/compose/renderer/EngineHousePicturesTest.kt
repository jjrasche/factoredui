package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.readSceneView
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val REVIEW_DIR = File(System.getProperty("user.dir"), "../../reviews/scene-structure-2026-10-09")

private fun distinctColourCount(image: BufferedImage): Int = image.getRGB(0, 0, image.width, image.height, null, 0, image.width).toSet().size

class EngineHousePicturesTest {

    private fun writePicture(name: String, view: String, picked: List<String> = emptyList()) {
        val png = driveScreen(engineHouseSpec(), engineHouseStore(view, picked), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { it.frame().png }
        val image = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(HOUSE_SCREEN_WIDTH to HOUSE_SCREEN_HEIGHT, image.width to image.height, name)
        assertTrue(distinctColourCount(image) > 1, "$name is a single flat colour: nothing was drawn")
        REVIEW_DIR.mkdirs()
        File(REVIEW_DIR, name).writeBytes(png)
    }

    @Test
    fun the_engine_house_is_pictured_in_plan_iso_south_and_west_and_with_its_piers_picked() {
        val piers = engineHouseLines(readSceneView(engineHouseText())).first { it.typeId == "cast_pier" }.ids
        writePicture("01-plan.png", "plan")
        writePicture("02-iso.png", "iso")
        writePicture("03-south.png", "south")
        writePicture("04-west.png", "west")
        writePicture("05-iso-highlight-pier.png", "iso", piers)
    }
}
