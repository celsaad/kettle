package com.casco.kettle.shell.nav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.casco.kettle.shell.theme.KettleColors

/**
 * The views the sheet is made of, around the one RN surface in [content], and their shape (#179): the
 * surface itself, never reparented, a picture of the tab behind it under a scrim, the sheet's own
 * background, the picture of a sheet on its way out, and the [SheetChrome] over the top.
 *
 * The surface is shaped through its layout params, never a new parent, so RN lays its modal out at
 * the sheet's size in the same frame: a size change commits synchronously (#178).
 */
class SheetStage(
    private val content: FrameLayout,
    val surface: View,
    requestBack: () -> Unit,
    onDrag: (dy: Float) -> Unit,
    onRelease: (dy: Float, velocity: Float) -> Unit,
) {
    private val density = content.resources.displayMetrics.density

    /** The tab the sheet covers, as it last looked. */
    val backdrop = picture(content.context)
    val scrim = cover(content.context).apply { setBackgroundColor(SCRIM) }

    /** The sheet's own background, under the surface, for the moments the surface is see-through. */
    val sheetBack = cover(content.context)

    /** A sheet's last frame: leaving the screen, or leaving the sheet for the next one. */
    val leaving = picture(content.context)
    val chrome = SheetChrome(content.context, requestBack, onDrag, onRelease)

    /** Everything that moves as the sheet: its background, the surface and the chrome. */
    val sheetViews = listOf(sheetBack, surface, chrome)

    /** What takes the sheet's shape: the surface and the background under it. The chrome sits on top. */
    val shapedViews = listOf(surface, sheetBack)

    /** The top corners' radius now: flattened as the sheet grows into the runner. */
    private var corner = 0f

    private val roundedTop =
        object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                // Taller than the view by the radius, so only the top corners round.
                outline.setRoundRect(0, 0, view.width, view.height + corner.toInt(), corner)
            }
        }

    init {
        if (surface.id == View.NO_ID) surface.id = View.generateViewId()
        var index = content.indexOfChild(surface)
        for (view in listOf(backdrop, scrim, sheetBack, chrome)) {
            content.addView(view, index++, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        // Over everything, the native destinations included, since it leaves over a live tab of either.
        content.addView(leaving, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        // Before the surface in the tree, so TalkBack reads it first, and drawn over it by Z. No outline,
        // so the Z casts no shadow.
        chrome.translationZ = CHROME_Z_DP * density
        chrome.outlineProvider = null
        chrome.visibility = View.GONE
        chrome.readBefore(surface)
        for (view in listOf(surface, sheetBack, leaving)) view.outlineProvider = roundedTop
    }

    /** The frame's height: off the bottom of the screen from the sheet's top. */
    val frameHeight: Float get() = content.height.toFloat()

    /** Where the sheet's top sits: the status bar plus [TOP_GAP_DP]. */
    val top: Int
        get() {
            val insets = ViewCompat.getRootWindowInsets(content)
            val statusBar = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            return statusBar + (TOP_GAP_DP * density).toInt()
        }

    val radius: Float get() = RADIUS_DP * density

    /** The sheet's shape: [top] down, capped at [MAX_WIDTH_DP] and centred, the top corners rounded. */
    fun layOutAsSheet() {
        val width = minOf(content.width, (MAX_WIDTH_DP * density).toInt())
        for (view in shapedViews) view.layoutParams = sheetParams(width, MATCH_PARENT)
        chrome.layoutParams = sheetParams(width, chrome.stripHeight)
        chrome.place(width)
        round(radius)
        clip(true)
        reset(surface, sheetBack, chrome)
    }

    /** Full frame, as on a tab or the runner. */
    fun layOutFull() {
        for (view in shapedViews) {
            view.layoutParams =
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        clip(false)
        reset(surface, sheetBack, chrome)
    }

    /** [picture] where the sheet is, over the live surface, unscaled. */
    fun showLeaving(picture: Bitmap) {
        leaving.setImageBitmap(picture)
        leaving.layoutParams = sheetParams(picture.width, MATCH_PARENT)
        leaving.clipToOutline = true
        reset(leaving)
        show(leaving)
    }

    fun clearLeaving() {
        hide(leaving)
        leaving.setImageDrawable(null)
    }

    fun round(radius: Float) {
        corner = radius
        for (view in listOf(surface, sheetBack, leaving)) view.invalidateOutline()
    }

    fun clip(on: Boolean) {
        surface.clipToOutline = on
        sheetBack.clipToOutline = on
    }

    /** The colors of the scheme the app is in now, which Settings can change under an open sheet. */
    fun paint(colors: KettleColors) {
        chrome.paint(colors)
        sheetBack.setBackgroundColor(colors.background.toArgb())
        // Behind the tab's picture, and all there is when a steered open took none.
        backdrop.setBackgroundColor(colors.background.toArgb())
    }

    /** Shrinks the sheet towards its bottom centre, as Material's predictive back for a sheet does. */
    fun scale(scale: Float) {
        for (view in sheetViews) {
            view.pivotX = surface.width / 2f
            // The chrome's top is the surface's, so the surface's height is the sheet's bottom for both.
            view.pivotY = surface.height.toFloat()
            view.scaleX = scale
            view.scaleY = scale
        }
    }

    fun translate(dy: Float) = sheetViews.forEach { it.translationY = dy }

    private fun sheetParams(
        width: Int,
        height: Int,
    ) = FrameLayout.LayoutParams(width, height, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = top }

    private companion object {
        const val TOP_GAP_DP = 12f
        const val MAX_WIDTH_DP = 640f
        const val RADIUS_DP = 16f
        const val CHROME_Z_DP = 1f

        /** Material's scrim: black at 32%. Decorative, with nothing set on it, so no contrast pairing. */
        val SCRIM = Color.argb(82, 0, 0, 0)
    }
}

/** Shown unscaled from the top left, so the tab bar coming back mid-close doesn't rescale it. */
private fun picture(context: Context) =
    ImageView(context).apply {
        scaleType = ImageView.ScaleType.MATRIX
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

private fun cover(context: Context) =
    View(context).apply {
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

internal fun show(vararg views: View) = views.forEach { it.visibility = View.VISIBLE }

internal fun hide(vararg views: View) = views.forEach { it.visibility = View.GONE }

/** No leftover motion from the last transition, drag or back gesture. */
internal fun reset(vararg views: View) =
    views.forEach {
        it.translationX = 0f
        it.translationY = 0f
        it.alpha = 1f
        it.scaleX = 1f
        it.scaleY = 1f
    }
