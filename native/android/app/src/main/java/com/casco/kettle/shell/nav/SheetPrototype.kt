package com.casco.kettle.shell.nav

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap

/**
 * PROTOTYPE for #177 — throwaway, lives only on research/native-sheet-prototype, never merged.
 *
 * Shows the one RN surface as an in-window sheet whenever RN reports a modal route: the surface is
 * never reparented, only given a top margin, rounded top corners and a slide, over a snapshot of the
 * tab screen it covered plus a scrim. Dismissal (button, back, swipe) always goes through RN; native
 * only animates the sheet's last captured frame away once RN reports it has left.
 *
 * Frames are captured on every touch-up and back press, since by the time RN reports a route change
 * it has already drawn the new one.
 */
@SuppressLint("ClickableViewAccessibility")
class SheetPrototype(
    private val content: FrameLayout,
    private val surface: View,
    private val requestBack: () -> Unit,
) {
    private val context = content.context
    private val density = context.resources.displayMetrics.density
    private val radius = 16 * density

    private val backdrop = image()
    private val scrim = decor().apply { setBackgroundColor(Color.argb(140, 0, 0, 0)) }
    private val leaving = image()
    private val handle = decor()

    private var lastFrame: Bitmap? = null
    private var shown = false
    private var animator: ValueAnimator? = null

    init {
        content.addView(backdrop, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(scrim, 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(leaving, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(handle, FrameLayout.LayoutParams((120 * density).toInt(), (28 * density).toInt()))
        handle.setOnTouchListener(::drag)
    }

    /** Before any tap or back reaches RN: the frame RN is about to replace. */
    fun capture() {
        if (surface.width == 0 || surface.height == 0) return
        val started = System.nanoTime()
        lastFrame = surface.drawToBitmap()
        Log.d(TAG, "captured in ${(System.nanoTime() - started) / 1_000_000}ms")
    }

    fun onRoute(pathname: String) {
        val modal = pathname.trimStart('/').substringBefore('/') in MODALS
        Log.d(TAG, "route $pathname modal=$modal shown=$shown")
        when {
            modal && !shown -> present()
            !modal && shown -> dismiss(animate = Tab.at(pathname) != null)
        }
    }

    private fun present() {
        shown = true
        animator?.cancel()
        backdrop.setImageBitmap(lastFrame)
        backdrop.visibility = View.VISIBLE
        scrim.visibility = View.VISIBLE
        val top = sheetTop()
        surface.layoutParams = (surface.layoutParams as FrameLayout.LayoutParams).apply { topMargin = top }
        surface.outlineProvider = roundedTop
        surface.clipToOutline = true
        (handle.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = top
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        handle.visibility = View.VISIBLE
        handle.requestLayout()
        val distance = content.height.toFloat()
        slide(from = distance, to = 0f) { surface.translationY = it }
    }

    private fun dismiss(animate: Boolean) {
        shown = false
        animator?.cancel()
        val top = (surface.layoutParams as FrameLayout.LayoutParams).topMargin
        val from = surface.translationY
        restoreSurface()
        handle.visibility = View.GONE
        backdrop.visibility = View.GONE
        val frame = lastFrame
        if (!animate || frame == null) {
            scrim.visibility = View.GONE
            return
        }
        // The live tab is already under it; only the sheet's picture moves away.
        leaving.setImageBitmap(frame)
        leaving.layoutParams = (leaving.layoutParams as FrameLayout.LayoutParams).apply { topMargin = top }
        leaving.outlineProvider = roundedTop
        leaving.clipToOutline = true
        leaving.visibility = View.VISIBLE
        slide(from = from, to = content.height.toFloat(), onEnd = {
            leaving.visibility = View.GONE
            scrim.visibility = View.GONE
        }) { value ->
            leaving.translationY = value
            scrim.alpha = 1 - value / content.height
        }
    }

    private fun restoreSurface() {
        surface.translationY = 0f
        surface.layoutParams = (surface.layoutParams as FrameLayout.LayoutParams).apply { topMargin = 0 }
        surface.clipToOutline = false
        surface.outlineProvider = ViewOutlineProvider.BACKGROUND
        scrim.alpha = 1f
    }

    private var dragFrom = 0f

    /** A drag on the strip over the grabber moves the sheet; letting go far enough asks RN to go back. */
    private fun drag(
        view: View,
        event: MotionEvent,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                capture()
                dragFrom = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = (event.rawY - dragFrom).coerceAtLeast(0f)
                surface.translationY = dy
                view.translationY = dy
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.translationY = 0f
                if (event.actionMasked == MotionEvent.ACTION_UP && surface.translationY > content.height / 4) {
                    requestBack()
                } else {
                    slide(from = surface.translationY, to = 0f) { surface.translationY = it }
                }
            }
        }
        return true
    }

    private fun sheetTop(): Int {
        val insets = ViewCompat.getRootWindowInsets(content)
        val status = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        return status + (12 * density).toInt()
    }

    private fun slide(
        from: Float,
        to: Float,
        onEnd: () -> Unit = {},
        apply: (Float) -> Unit,
    ) {
        animator =
            ValueAnimator.ofFloat(from, to).apply {
                duration = 280
                addUpdateListener { apply(it.animatedValue as Float) }
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
                    },
                )
                start()
            }
    }

    private val roundedTop =
        object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                // Taller than the view by the radius, so only the top corners round.
                outline.setRoundRect(0, 0, view.width, view.height + radius.toInt(), radius)
            }
        }

    private fun image() =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_START
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

    private fun decor() =
        View(context).apply {
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

    private companion object {
        const val TAG = "SheetPrototype"

        // src/app/_layout.tsx's modal routes, minus the runner, which stays RN-presented (out of scope).
        val MODALS =
            setOf(
                "import", "exercise-editor", "workout-editor", "analytics", "exercise-progress",
                "program-detail", "program-editor", "program-guide", "session-editor", "settings", "support",
            )
    }
}
