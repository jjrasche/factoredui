package ai.factoredui.compose.stackedscene

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WIDTH = 800.0
private const val HEIGHT = 600.0

private val BASE = CanvasFit(scale = 0.05, vScale = 0.1, offsetX = 100.0, offsetY = 40.0, westMm = -3000.0, northMm = 9000.0)

private fun assertClose(expected: Double, actual: Double, label: String, tolerance: Double = 1e-9) =
    assertTrue(abs(actual - expected) < tolerance, "$label: $actual vs $expected")

private fun drawnAt(camera: SceneCamera, x: Double, y: Double): Pair<Double, Double> = mmToCanvas(cameraFit(BASE, camera, WIDTH, HEIGHT), x, y)

class SceneCameraTest {

    @Test
    fun the_default_camera_draws_the_fitted_picture_unchanged() =
        assertEquals(BASE, cameraFit(BASE, SceneCamera(), WIDTH, HEIGHT))

    @Test
    fun a_zoom_scales_the_picture_about_the_centre_of_the_drawing_area_and_keeps_a_stretch_of_heights() {
        val plain = drawnAt(SceneCamera(), 2000.0, 4000.0)
        val zoomed = drawnAt(SceneCamera(zoom = 2.0), 2000.0, 4000.0)
        assertClose(WIDTH / 2 + 2 * (plain.first - WIDTH / 2), zoomed.first, "x")
        assertClose(HEIGHT / 2 + 2 * (plain.second - HEIGHT / 2), zoomed.second, "y")
        val fit = cameraFit(BASE, SceneCamera(zoom = 2.0), WIDTH, HEIGHT)
        assertClose(BASE.vScale / BASE.scale, fit.vScale / fit.scale, "stretch")
    }

    @Test
    fun a_pan_shifts_the_picture_by_that_share_of_the_drawing_areas_width_and_height() {
        val plain = drawnAt(SceneCamera(), 2000.0, 4000.0)
        val panned = drawnAt(SceneCamera(panX = 0.25, panY = -0.1), 2000.0, 4000.0)
        assertClose(plain.first + 200.0, panned.first, "x")
        assertClose(plain.second - 60.0, panned.second, "y")
    }

    @Test
    fun a_ctrl_drag_pans_by_the_share_of_the_drawing_area_it_travels() {
        val panned = pannedBy(SceneCamera(panX = 0.1), 80.0, -30.0, WIDTH, HEIGHT)
        assertClose(0.2, panned.panX, "pan x")
        assertClose(-0.05, panned.panY, "pan y")
        assertEquals(SceneCamera(panX = 0.1), pannedBy(SceneCamera(panX = 0.1), 80.0, -30.0, 0.0, HEIGHT), "a drawing area with no size cannot be panned")
    }

    @Test
    fun each_wheel_notch_toward_the_screen_zooms_in_by_a_quarter_and_each_away_zooms_out() {
        assertEquals(1.25, ZOOM_PER_NOTCH)
        assertClose(1.5625, zoomedAbout(SceneCamera(), -2.0, 400.0, 300.0, WIDTH, HEIGHT).zoom, "two in")
        assertClose(0.8, zoomedAbout(SceneCamera(), 1.0, 400.0, 300.0, WIDTH, HEIGHT).zoom, "one out")
    }

    @Test
    fun the_zoom_is_held_between_a_quarter_and_sixteen_and_the_wheel_never_writes_past_it() {
        assertEquals(0.25 to 16.0, MIN_ZOOM to MAX_ZOOM)
        assertClose(16.0, zoomedAbout(SceneCamera(zoom = 15.0), -5.0, 400.0, 300.0, WIDTH, HEIGHT).zoom, "in")
        assertClose(0.25, zoomedAbout(SceneCamera(zoom = 0.3), 5.0, 400.0, 300.0, WIDTH, HEIGHT).zoom, "out")
        val wheeled = (1..40).fold(SceneCamera()) { camera, _ -> zoomedAbout(camera, -1.0, 130.0, 470.0, WIDTH, HEIGHT) }
        assertEquals(16.0, wheeled.zoom)
        assertClose(16.0, clampedZoom(100.0), "clamp high")
        assertClose(0.25, clampedZoom(0.01), "clamp low")
    }

    @Test
    fun the_drawn_point_under_the_pointer_stays_under_it_as_the_wheel_zooms() {
        listOf(SceneCamera(), SceneCamera(zoom = 3.0, panX = 0.2, panY = -0.3), SceneCamera(zoom = 15.5, panX = -1.0)).forEach { before ->
            val pointerX = 130.0
            val pointerY = 470.0
            val world = canvasToMm(cameraFit(BASE, before, WIDTH, HEIGHT), pointerX, pointerY)
            val after = zoomedAbout(before, -3.0, pointerX, pointerY, WIDTH, HEIGHT)
            val drawn = mmToCanvas(cameraFit(BASE, after, WIDTH, HEIGHT), world.first, world.second)
            assertClose(pointerX, drawn.first, "x from $before", 1e-6)
            assertClose(pointerY, drawn.second, "y from $before", 1e-6)
        }
    }
}
