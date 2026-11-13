/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.menu.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.BottomSheetHandle

/**
 * The menu dialog bottom sheet.
 *
 * @param modifier [Modifier] to be applied to [BottomSheetHandle].
 * @param onRequestDismiss Invoked when accessibility services or UI automation requests dismissal of the bottom sheet.
 * @param menuHandleState Configuration of the handle to use for the menu layout.
 * @param snackbarHostState The [SnackbarHostState] to display snackbars in.
 * @param cornerShape The shape of the bottom sheet's top corners.
 * @param maxHeight Optional cap on the panel height. When set, the panel hugs its content up to this
 * height and scrolls beyond it, so it can be shown as a top-anchored dropdown instead of a full sheet.
 * @param content The children composable to be laid out.
 */
@Composable
fun MenuDialogBottomSheet(
    modifier: Modifier = Modifier,
    onRequestDismiss: () -> Unit,
    menuHandleState: MenuHandleState = MenuHandleState(contentDescription = "", visible = false),
    snackbarHostState: SnackbarHostState,
    cornerShape: Shape = RoundedCornerShape(DESKTOP_DROPDOWN_CORNER_DP.dp),
    maxHeight: Dp = Dp.Unspecified,
    borderColor: Color? = MaterialTheme.colorScheme.outlineVariant,
    elevation: Dp = 0.dp,
    verticalPadding: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Box {
        Column(
            modifier =
                Modifier
                    .then(if (maxHeight != Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier)
                    .then(if (elevation > 0.dp) Modifier.shadow(elevation, cornerShape) else Modifier)
                    .then(if (borderColor != null) Modifier.border(1.dp, borderColor, cornerShape) else Modifier)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = cornerShape,
                    )
                    .padding(vertical = verticalPadding)
                    .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            if (menuHandleState.visible) {
                MenuBottomSheetHandle(
                    modifier = modifier,
                    onRequestDismiss = onRequestDismiss,
                    contentDescription = menuHandleState.contentDescription,
                    isMenuDragBarDark = menuHandleState.useDarkBackground,
                    cornerShape = cornerShape,
                )
            }

            content()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MenuBottomSheetHandle(
    modifier: Modifier = Modifier,
    onRequestDismiss: () -> Unit,
    contentDescription: String,
    isMenuDragBarDark: Boolean = false,
    cornerShape: Shape =
        MaterialTheme.shapes.large.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        ),
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color =
                        if (isMenuDragBarDark) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Transparent
                        },
                    shape = cornerShape,
                )
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetHandle(
            onRequestDismiss = onRequestDismiss,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

/**
 * Configuration of the handle to use for the menu layout.
 *
 * @property contentDescription Custom content description for a11y services.
 * @property useDarkBackground Whether to use a dark background (screen wide) for the handle. Defaults to `false`.
 * @property visible Whether the handle should be visible. Defaults to `true`.
 */
data class MenuHandleState(
    val contentDescription: String,
    val useDarkBackground: Boolean = false,
    val visible: Boolean = false,
)
