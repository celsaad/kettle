package com.casco.kettle.shell.nav

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleColors
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

/**
 * The sheet's grabber and ×, drawn over the top of the RN surface where RN's `ModalHeader` leaves its
 * strip empty inside the shell (src/components/modal-header.tsx). They sit where RN's sat: a row
 * [ROW_DP] tall, [Spacing.three] down, centred in the same [MaxContentWidth] column. The two share no
 * code, so change both.
 *
 * Plain views rather than Compose: the grabber's drag moves this view with the finger, and only a raw
 * screen coordinate stays still under a moving host.
 *
 * Neither dismisses anything itself. [requestBack] is native back, which the sheet turns into RN's.
 * [onDrag] follows the finger down the screen, and [onRelease] gets the fling velocity in px/s.
 */
@SuppressLint("ViewConstructor") // Built in code, never inflated.
class SheetChrome(
    context: Context,
    private val requestBack: () -> Unit,
    private val onDrag: (dy: Float) -> Unit,
    private val onRelease: (dy: Float, velocity: Float) -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density

    private fun dp(value: Float) = (value * density).toInt()

    /** How far a 48dp target reaches past the 32dp circle it holds, on each side. */
    private val targetOverhang get() = (dp(TARGET_DP) - dp(CIRCLE_DP)) / 2

    private val bar = View(context)

    private val grabber =
        FrameLayout(context).apply {
            id = generateViewId()
            contentDescription = context.getString(R.string.common_dragHandle)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            addView(bar, LayoutParams(dp(BAR_WIDTH_DP), dp(BAR_HEIGHT_DP), Gravity.CENTER))
        }

    private val close =
        TextView(context).apply {
            id = generateViewId()
            text = CLOSE_GLYPH
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CLOSE_TEXT_SP)
            contentDescription = context.getString(R.string.common_close)
            setOnClickListener { requestBack() }
        }

    init {
        addView(grabber, LayoutParams(dp(GRABBER_WIDTH_DP), dp(TARGET_DP), Gravity.CENTER_HORIZONTAL or Gravity.TOP))
        addView(close, LayoutParams(dp(TARGET_DP), dp(TARGET_DP), Gravity.END or Gravity.TOP))
        // × first, then the grabber, then the content: the order #155 asks TalkBack to read them in.
        close.accessibilityTraversalBefore = grabber.id
        ViewCompat.setAccessibilityDelegate(close, ButtonRole)
        grabTalkBackActions()
        listenForDrags()
    }

    /** Lines the controls up in a sheet [sheetWidth] px wide, before RN's content draws under them. */
    fun place(sheetWidth: Int) {
        val column = minOf(sheetWidth, dp(MaxContentWidth.value))
        val inset = (sheetWidth - column) / 2 + dp(Spacing.three.value)
        // The row's centre, where RN's grabber and button centred.
        val rowCentre = dp(Spacing.three.value) + dp(ROW_DP) / 2
        val top = rowCentre - dp(TARGET_DP) / 2
        grabber.layoutParams = (grabber.layoutParams as LayoutParams).apply { topMargin = top }
        // Set back rather than edited in place: an end margin changed on attached params never resolves
        // to a right one, and on a device the × sat flush with the screen's edge.
        close.layoutParams =
            (close.layoutParams as LayoutParams).apply {
                topMargin = top
                // The 32dp circle's edge where RN's was, its 48dp target reaching past it.
                marginEnd = inset - targetOverhang
            }
    }

    /** The visible part of the strip: RN's header height, the drag gesture's whole target. */
    val stripHeight: Int get() = dp(Spacing.three.value) + dp(ROW_DP) + dp(Spacing.three.value - 2)

    /** The surface the content is read from next, after the × and the grabber. */
    fun readBefore(content: View) {
        grabber.accessibilityTraversalBefore = content.id
    }

    /** The pairing RN's header uses (border, backgroundElement, textSecondary), so its contrast holds. */
    fun paint(colors: KettleColors) {
        bar.background =
            GradientDrawable().apply {
                cornerRadius = dp(BAR_HEIGHT_DP).toFloat() / 2
                setColor(colors.border.toArgb())
            }
        close.setTextColor(colors.textSecondary.toArgb())
        close.background =
            InsetDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colors.backgroundElement.toArgb())
                    setStroke(dp(1f), colors.border.toArgb())
                },
                targetOverhang,
            )
    }

    /**
     * TalkBack's sheet actions on the grabber, as Material's drag handle offers them. The sheet has one
     * height, always fully open, so there is nothing to expand to, and collapsing it is closing it.
     */
    private fun grabTalkBackActions() {
        for (action in listOf(AccessibilityActionCompat.ACTION_DISMISS, AccessibilityActionCompat.ACTION_COLLAPSE)) {
            ViewCompat.replaceAccessibilityAction(grabber, action, null) { _, _ ->
                requestBack()
                true
            }
        }
    }

    // No click to perform: a tap on the grabber does nothing on purpose, and TalkBack's way is the actions.
    @SuppressLint("ClickableViewAccessibility")
    private fun listenForDrags() = grabber.setOnTouchListener(::drag)

    private var downAt = 0f
    private var velocity: VelocityTracker? = null

    /**
     * Raw coordinates, since this view moves with the finger. No tap does anything: a stray one would
     * drop an edit, which is why the scrim doesn't dismiss either.
     */
    private fun drag(
        view: View,
        event: MotionEvent,
    ): Boolean {
        val dy = (event.rawY - downAt).coerceAtLeast(0f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = event.rawY
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                onDrag(dy)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = velocity
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(MILLIS_PER_SECOND)
                val speed = if (event.actionMasked == MotionEvent.ACTION_UP) tracker?.yVelocity ?: 0f else 0f
                tracker?.recycle()
                velocity = null
                onRelease(if (event.actionMasked == MotionEvent.ACTION_UP) dy else 0f, speed)
            }
        }
        view.parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    /** A TextView with a click listener announces as one; this makes it a button, as RN's was. */
    private object ButtonRole : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat,
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Button::class.java.name
        }
    }

    private companion object {
        /** #155: both controls are 48dp targets, past Kettle's 44 minimum. */
        const val TARGET_DP = 48f
        const val CIRCLE_DP = 32f

        /** modal-header.tsx's row, which RN's button set the height of. */
        const val ROW_DP = 32f
        const val BAR_WIDTH_DP = 40f
        const val BAR_HEIGHT_DP = 5f

        /** Wider than the bar, so the drag is easy to start; the same height as the × target. */
        const val GRABBER_WIDTH_DP = 120f
        const val CLOSE_TEXT_SP = 20f
        const val CLOSE_GLYPH = "×"
        const val MILLIS_PER_SECOND = 1000
    }
}
