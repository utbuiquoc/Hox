/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.trackingprotection

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.content
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import org.mozilla.fenix.components.menu.compose.DESKTOP_DROPDOWN_CORNER_DP
import org.mozilla.fenix.components.menu.compose.DesktopDropdownAnchor
import org.mozilla.fenix.components.menu.compose.calculateDesktopDropdownMaxHeight
import org.mozilla.fenix.components.menu.compose.configureAsDesktopDropdown
import org.mozilla.fenix.components.menu.compose.updateDesktopDropdownLayout
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.theme.FirefoxTheme

private const val PROTECTIONS_DROPDOWN_WIDTH_DP = 380

/** [BottomSheetDialog] showing the global protections dashboard. */
class ProtectionsDashboardFragment : BottomSheetDialogFragment() {
    private val args by navArgs<ProtectionsDashboardFragmentArgs>()
    private val trackersBlockedFeature = ViewBoundFeatureWrapper<TrackersBlockedFeature>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            configureAsDesktopDropdown(
                fragment = this@ProtectionsDashboardFragment,
                gravity = Gravity.TOP or Gravity.START,
                widthDp = PROTECTIONS_DROPDOWN_WIDTH_DP,
                anchor = DesktopDropdownAnchor.URL_BAR_START,
            )
        }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dialog?.updateDesktopDropdownLayout(
            fragment = this,
            gravity = Gravity.TOP or Gravity.START,
            widthDp = PROTECTIONS_DROPDOWN_WIDTH_DP,
            anchor = DesktopDropdownAnchor.URL_BAR_START,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        val appStore = requireComponents.appStore
        val blockedTrackersState by appStore.observeAsComposableState { state ->
            state.blockedTrackersState
        }

        FirefoxTheme {
            Surface(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .heightIn(max = calculateDesktopDropdownMaxHeight()),
                shape = RoundedCornerShape(DESKTOP_DROPDOWN_CORNER_DP.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp,
            ) {
                ProtectionsDashboardContent(
                    totalTrackersBlocked = blockedTrackersState.trackersBlockedCount,
                    trackersBlockedThisWeek = blockedTrackersState.trackersBlockedThisWeek,
                    earliestTrackingDate = blockedTrackersState.earliestTrackingDate,
                ) {
                    dismiss()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        trackersBlockedFeature.set(
            feature =
                TrackersBlockedFeature(
                    browserStore = requireComponents.core.store,
                    appStore = requireComponents.appStore,
                    currentSessionId = args.customTabSessionId,
                    trackingProtectionUseCases = requireComponents.useCases.trackingProtectionUseCases,
                ),
            owner = viewLifecycleOwner,
            view = view,
        )
    }
}
