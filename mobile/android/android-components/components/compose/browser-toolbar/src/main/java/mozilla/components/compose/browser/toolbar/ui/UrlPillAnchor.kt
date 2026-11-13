/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import mozilla.components.compose.browser.toolbar.R

/**
 * Publishes the start offset (in pixels relative to the host `android.R.id.content` view)
 * of the toolbar's URL pill so start-anchored desktop popups can align beneath it.
 */
@Composable
internal fun Modifier.trackUrlPillStartAnchor(): Modifier = composed {
    val hostView = LocalView.current
    val layoutDirection = LocalLayoutDirection.current

    DisposableEffect(hostView) {
        onDispose {
            val contentRoot = hostView.rootView?.findViewById<View>(android.R.id.content)
            if (contentRoot?.getTag(R.id.mozac_compose_browser_toolbar_url_pill_owner) === hostView) {
                contentRoot.setTag(R.id.mozac_compose_browser_toolbar_url_pill_owner, null)
                contentRoot.setTag(R.id.mozac_compose_browser_toolbar_url_pill_start, null)
            }
        }
    }

    onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached || !hostView.isAttachedToWindow) {
            return@onGloballyPositioned
        }
        val contentRoot =
            hostView.rootView?.findViewById<View>(android.R.id.content)
                ?: return@onGloballyPositioned
        val windowLocation = IntArray(2)
        hostView.rootView.getLocationOnScreen(windowLocation)
        val contentLocation = IntArray(2)
        contentRoot.getLocationOnScreen(contentLocation)
        val boundsInWindow = coordinates.boundsInWindow()
        val pillStartInContentPx =
            if (layoutDirection == LayoutDirection.Rtl) {
                val pillRightOnScreen = windowLocation[0] + boundsInWindow.right
                (contentLocation[0] + contentRoot.width - pillRightOnScreen).toInt()
            } else {
                val pillLeftOnScreen = windowLocation[0] + boundsInWindow.left
                (pillLeftOnScreen - contentLocation[0]).toInt()
            }
        if (pillStartInContentPx >= 0) {
            contentRoot.setTag(R.id.mozac_compose_browser_toolbar_url_pill_owner, hostView)
            contentRoot.setTag(
                R.id.mozac_compose_browser_toolbar_url_pill_start,
                pillStartInContentPx,
            )
        }
    }
}
