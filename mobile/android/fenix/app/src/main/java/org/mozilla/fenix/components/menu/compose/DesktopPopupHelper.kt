/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.menu.compose

import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import com.google.android.material.R as materialR
import mozilla.components.compose.browser.toolbar.R as toolbarR
import mozilla.components.support.ktx.android.util.dpToPx
import org.mozilla.fenix.R

enum class DesktopDropdownAnchor {
    WINDOW_EDGE,
    URL_BAR_START,
}

const val DESKTOP_DROPDOWN_WIDTH_DP = 320
const val DESKTOP_PANEL_WIDTH_DP = 360
const val DESKTOP_DROPDOWN_HORIZONTAL_MARGIN_DP = 8
const val DESKTOP_DROPDOWN_VERTICAL_MARGIN_DP = 8
const val DESKTOP_DROPDOWN_TOP_OVERLAP_DP = 7
const val DESKTOP_DROPDOWN_FALLBACK_TOOLBAR_HEIGHT_DP = 56
const val DESKTOP_DROPDOWN_DIM_AMOUNT = 0f
const val DESKTOP_DROPDOWN_CORNER_DP = 10

private const val DROPDOWN_ENTER_DURATION_MS = 150L
private const val DROPDOWN_ENTER_FROM_SCALE_X = 0.96f
private const val DROPDOWN_ENTER_FROM_SCALE_Y = 0.88f
private const val DROPDOWN_ENTER_OFFSET_Y_DP = 8
private const val MIN_DROPDOWN_HEIGHT_DP = 240
private const val RESERVED_BOTTOM_MARGIN_DP = 24
private const val DEFAULT_RESERVED_HEIGHT_DP = 80

/**
 * Resolves the top margin in pixels for a top-anchored dropdown menu so that it sits directly
 * below the top toolbar (including any tab strip or address bar above it).
 */
fun Fragment.resolveDropdownTopMarginPx(topInset: Int): Int {
    val root = activity?.findViewById<View>(android.R.id.content)
    val toolbar =
        activity?.findViewById<View>(R.id.composable_toolbar)
            ?: activity?.findViewById<View>(R.id.toolbar)
            ?: root?.let {
                findToolbarViewByClassName(it, "ScrollableToolbarComposeView")
                    ?: findToolbarViewByClassName(it, "BrowserToolbar")
            }

    val topOverlapPx = DESKTOP_DROPDOWN_TOP_OVERLAP_DP.dpToPx(resources.displayMetrics)

    if (toolbar != null && toolbar.isShown && toolbar.height > 0) {
        val location = IntArray(2)
        toolbar.getLocationOnScreen(location)
        // Only anchor below toolbars located in the upper half of the screen.
        if (location[1] < resources.displayMetrics.heightPixels / 2) {
            val bottomOnScreen = location[1] + toolbar.height
            val fallbackBottom = topInset + toolbar.height
            val anchorBottom = maxOf(bottomOnScreen, fallbackBottom)
            return maxOf(anchorBottom - topOverlapPx, topInset, 0)
        }
    }

    val homepageToolbarBottom = root?.getTag(R.id.composable_toolbar) as? Int
    if (
        homepageToolbarBottom != null &&
        homepageToolbarBottom > 0 &&
        homepageToolbarBottom < resources.displayMetrics.heightPixels / 2
    ) {
        return maxOf(homepageToolbarBottom - topOverlapPx, topInset, 0)
    }

    return maxOf(
        topInset + DESKTOP_DROPDOWN_FALLBACK_TOOLBAR_HEIGHT_DP.dpToPx(resources.displayMetrics) - topOverlapPx,
        topInset,
        0,
    )
}

private fun findToolbarViewByClassName(view: View, simpleName: String): View? {
    if (view.javaClass.simpleName == simpleName && view.height > 0 && view.isShown) {
        return view
    }
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findToolbarViewByClassName(view.getChildAt(index), simpleName)?.let { return it }
        }
    }
    return null
}

/**
 * Calculates a maximum height in [Dp] for a top-anchored dropdown panel so it does not overflow
 * the bottom edge of the screen.
 */
fun Fragment.calculateDesktopDropdownMaxHeight(extraReservedDp: Int = DEFAULT_RESERVED_HEIGHT_DP): Dp {
    val density = resources.displayMetrics.density
    val topMarginDp = (resolveDropdownTopMarginPx(0) / density).toInt()
    val reservedDp = if (topMarginDp > 0) topMarginDp + RESERVED_BOTTOM_MARGIN_DP else extraReservedDp
    return (resources.configuration.screenHeightDp - reservedDp)
        .coerceAtLeast(MIN_DROPDOWN_HEIGHT_DP)
        .dp
}

/**
 * Removes background dimming and applies the desktop dropdown window animation on a [Window].
 */
fun Window.applyDesktopDropdownWindowAppearance() {
    setDimAmount(DESKTOP_DROPDOWN_DIM_AMOUNT)
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    setWindowAnimations(R.style.DesktopDropdownWindowAnimation)
}

/**
 * Removes background dimming and applies the desktop dropdown window animation.
 */
fun Dialog.applyDesktopDropdownWindowAppearance() {
    window?.applyDesktopDropdownWindowAppearance()
}

/**
 * Configures a [com.google.android.material.bottomsheet.BottomSheetDialog] as a desktop-style
 * dropdown menu anchored below the top toolbar at [gravity].
 */
fun Dialog.configureAsDesktopDropdown(
    fragment: Fragment,
    gravity: Int = Gravity.TOP or Gravity.END,
    widthDp: Int = DESKTOP_DROPDOWN_WIDTH_DP,
    anchor: DesktopDropdownAnchor = DesktopDropdownAnchor.WINDOW_EDGE,
    onShowExtra: ((Dialog) -> Unit)? = null,
) {
    applyDesktopDropdownWindowAppearance()

    setOnShowListener {
        applyDesktopDropdownWindowAppearance()
        onShowExtra?.invoke(this)
        val sheet = findViewById<View>(materialR.id.design_bottom_sheet) ?: return@setOnShowListener
        // Replace Material container insets listener with a pass-through so detaching
        // BottomSheetBehavior does not re-trigger bottom-sheet inset handling.
        findViewById<View>(materialR.id.container)?.let { container ->
            ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets -> insets }
        }
        sheet.background = Color.TRANSPARENT.toDrawable()
        sheet.elevation = 0f

        updateDesktopDropdownLayout(
            fragment = fragment,
            gravity = gravity,
            widthDp = widthDp,
            anchor = anchor,
        )
        ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets ->
            applyDesktopDropdownLayout(
                sheet = sheet,
                fragment = fragment,
                gravity = gravity,
                widthDp = widthDp,
                anchor = anchor,
                insets = insets,
            )
            insets
        }

        animateDesktopDropdownIn(sheet, gravity)
    }
}

/**
 * Re-applies desktop dropdown width, gravity, and margins (e.g., on configuration changes).
 */
fun Dialog.updateDesktopDropdownLayout(
    fragment: Fragment,
    gravity: Int = Gravity.TOP or Gravity.END,
    widthDp: Int = DESKTOP_DROPDOWN_WIDTH_DP,
    anchor: DesktopDropdownAnchor = DesktopDropdownAnchor.WINDOW_EDGE,
) {
    val sheet = findViewById<View>(materialR.id.design_bottom_sheet) ?: return
    applyDesktopDropdownLayout(
        sheet = sheet,
        fragment = fragment,
        gravity = gravity,
        widthDp = widthDp,
        anchor = anchor,
        insets = ViewCompat.getRootWindowInsets(sheet),
    )
}

private fun Fragment.resolveDropdownStartMarginPx(
    anchor: DesktopDropdownAnchor,
    targetWidthPx: Int,
    defaultMarginPx: Int,
): Int {
    if (anchor != DesktopDropdownAnchor.URL_BAR_START) return defaultMarginPx
    val root = activity?.findViewById<View>(android.R.id.content) ?: return defaultMarginPx
    val urlPillStart =
        root.getTag(toolbarR.id.mozac_compose_browser_toolbar_url_pill_start) as? Int
            ?: return defaultMarginPx
    val maxStart =
        (resources.displayMetrics.widthPixels - targetWidthPx - defaultMarginPx)
            .coerceAtLeast(defaultMarginPx)
    return urlPillStart.coerceIn(defaultMarginPx, maxStart)
}

private fun applyDesktopDropdownLayout(
    sheet: View,
    fragment: Fragment,
    gravity: Int,
    widthDp: Int,
    anchor: DesktopDropdownAnchor,
    insets: WindowInsetsCompat?,
) {
    val params = sheet.layoutParams as? CoordinatorLayout.LayoutParams ?: return
    params.behavior = null

    val metrics = fragment.resources.displayMetrics
    val systemBars = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
    val topInset = systemBars?.top ?: 0
    val bottomInset = systemBars?.bottom ?: 0
    val horizontalMargin = DESKTOP_DROPDOWN_HORIZONTAL_MARGIN_DP.dpToPx(metrics)
    val verticalMargin = DESKTOP_DROPDOWN_VERTICAL_MARGIN_DP.dpToPx(metrics)
    val maxWidth = (metrics.widthPixels - horizontalMargin * 2).coerceAtLeast(0)
    val targetWidth = widthDp.dpToPx(metrics).coerceAtMost(maxWidth)
    val horizontalGravity =
        Gravity.getAbsoluteGravity(gravity, sheet.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK
    val isStartAligned =
        (gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == Gravity.START ||
            horizontalGravity == Gravity.LEFT

    params.width = targetWidth
    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
    params.gravity = gravity
    params.topMargin = fragment.resolveDropdownTopMarginPx(topInset)
    params.bottomMargin = bottomInset + verticalMargin
    params.marginStart =
        if (isStartAligned) {
            fragment.resolveDropdownStartMarginPx(anchor, targetWidth, horizontalMargin)
        } else {
            horizontalMargin
        }
    params.marginEnd = horizontalMargin
    sheet.layoutParams = params
}

fun animateDesktopDropdownIn(sheet: View, gravity: Int = Gravity.TOP or Gravity.END) {
    sheet.animate().cancel()
    val offsetPx = DROPDOWN_ENTER_OFFSET_Y_DP.dpToPx(sheet.resources.displayMetrics).toFloat()
    sheet.alpha = 0f
    sheet.scaleX = DROPDOWN_ENTER_FROM_SCALE_X
    sheet.scaleY = DROPDOWN_ENTER_FROM_SCALE_Y
    sheet.translationY = -offsetPx

    sheet.doOnLayout {
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, it.layoutDirection)
        val isLeftAligned = (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT
        it.pivotX = if (isLeftAligned) 0f else it.width.toFloat()
        it.pivotY = 0f
        it.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(DROPDOWN_ENTER_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
