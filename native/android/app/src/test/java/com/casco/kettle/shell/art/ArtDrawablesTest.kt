package com.casco.kettle.shell.art

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.vectorResource
import androidx.core.content.res.ResourcesCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Every drawable `:art` generated, read back by Android. Lint checks a vector's XML but not its
 * `pathData`, so a path string Android can't read would otherwise pass the build and fail on the
 * first screen to draw it. `:art` holds the SVG side; this holds what it wrote.
 */
@RunWith(RobolectricTestRunner::class)
class ArtDrawablesTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    private fun paths(node: VectorNode): List<VectorPath> =
        when (node) {
            is VectorGroup -> node.flatMap(::paths)
            is VectorPath -> listOf(node)
        }

    @Test
    fun everyDrawingKeepsTheSharedCanvasAndStroke() {
        // Every shipped id with a drawing, from art.json: the coverage is jest's to hold.
        assertTrue(artDrawables.size > 1)
        for ((id, drawable) in artDrawables) {
            val vector = ImageVector.vectorResource(null, resources, drawable)
            assertEquals(id, 120f to 96f, vector.viewportWidth to vector.viewportHeight)
            val paths = paths(vector.root)
            assertTrue(id, paths.isNotEmpty())
            for (path in paths) assertTrue(id, path.pathData.isNotEmpty() && path.strokeLineWidth == 3f)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun everyDrawingInflatesAndDrawsThroughTheFramework() {
        // The strict reader, and the one that matters at runtime: Compose's parser shrugged off a
        // malformed arc (`A1.2.3`) that the platform's refuses, which is how this test was proved.
        val canvas = Canvas(Bitmap.createBitmap(120, 96, Bitmap.Config.ARGB_8888))
        for ((id, drawable) in artDrawables) {
            val inflated = checkNotNull(ResourcesCompat.getDrawable(resources, drawable, null)) { id }
            inflated.setBounds(0, 0, canvas.width, canvas.height)
            inflated.draw(canvas)
        }
    }

    @Test
    fun aDrawingSharedByTwoIdsIsOneDrawable() {
        // The one-to-many case art.json keeps: the cooldown is the cardio drawing, not a copy of it.
        assertEquals(artDrawables.getValue("easy-cardio"), artDrawables.getValue("gym-cooldown"))
    }
}
