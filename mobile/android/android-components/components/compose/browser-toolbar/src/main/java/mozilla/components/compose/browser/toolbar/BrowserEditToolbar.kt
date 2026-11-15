/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.base.theme.acornPrivateColorScheme
import mozilla.components.compose.base.theme.privateColorPalette
import mozilla.components.compose.browser.toolbar.concept.Action
import mozilla.components.compose.browser.toolbar.concept.Action.ActionButtonRes
import mozilla.components.compose.browser.toolbar.concept.Action.SearchSelectorAction
import mozilla.components.compose.browser.toolbar.concept.Action.SearchSelectorAction.ContentDescription.StringResContentDescription
import mozilla.components.compose.browser.toolbar.concept.Action.SearchSelectorAction.Icon.DrawableIcon
import mozilla.components.compose.browser.toolbar.concept.BrowserToolbarTestTags.ADDRESSBAR_EDIT_MODE
import mozilla.components.compose.browser.toolbar.concept.BrowserToolbarTestTags.ADDRESSBAR_EDIT_MODE_HORIZONTAL_DIVIDER
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Bottom
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Top
import mozilla.components.compose.browser.toolbar.ui.BrowserActionsColorScheme
import mozilla.components.compose.browser.toolbar.ui.BrowserToolbarQuery
import mozilla.components.compose.browser.toolbar.ui.InlineAutocompleteTextField
import mozilla.components.compose.browser.toolbar.ui.NO_TOOLBAR_PADDING_DP
import mozilla.components.compose.browser.toolbar.ui.TOOLBAR_PADDING_DP
import mozilla.components.compose.browser.toolbar.ui.adaptiveHorizontalPadding
import mozilla.components.compose.browser.toolbar.ui.trackUrlPillStartAnchor
import mozilla.components.compose.browser.toolbar.ui.urlPillOuterPadding
import mozilla.components.concept.toolbar.AutocompleteResult
import mozilla.components.ui.icons.R as iconsR

private const val DESKTOP_EDIT_URL_CORNER_DP = 10
private const val DESKTOP_EDIT_URL_BORDER_DP = 1.5f

/**
 * Sub-component of the [BrowserToolbar] responsible for allowing the user to edit the current URL ("edit mode").
 *
 * @param query The current query.
 * @param hint Hint to show in the absence of a query.
 * @param suggestion [AutocompleteResult] to show as an inline autocomplete suggestion for the current [query].
 * @param isQueryPrefilled Whether [query] is prefilled and not user entered.
 * @param usePrivateModeQueries Whether queries should be done in private / incognito mode.
 * @param gravity [ToolbarGravity] for where the toolbar is being placed on the screen.
 * @param backgroundColor Color of the background.
 * @param outlineColor Color of the divider.
 * @param showDivider Whether to show the divider on the edge of the toolbar facing the webpage.
 * @param editActionsStart List of [Action]s to be displayed at the start of the URL of the edit toolbar.
 * @param editActionsEnd List of [Action]s to be displayed at the end of the URL of the edit toolbar.
 * @param useDesktopEditToolbar Whether to keep surrounding browser actions visible and style the URL bar like desktop.
 * @param browserActionsStart List of [Action]s shown before the desktop URL box when [useDesktopEditToolbar] is true.
 * @param browserActionsEnd List of [Action]s shown after the desktop URL box when [useDesktopEditToolbar] is true.
 * @param browserActionsColor Optional `onSurface` color override for surrounding browser actions in desktop mode.
 * @param onDesktopUrlPillBoundsChanged Callback reporting the desktop URL input box bounds in window coordinates.
 * @param onDismissDesktopSearch Optional callback invoked when Escape is pressed to exit desktop edit mode.
 * @param onUrlEdit Will be called when the URL value changes. An updated text value comes as a parameter of the
 *   callback.
 * @param onUrlCommitted Will be called when the user has finished editing and wants to initiate loading the entered
 *   URL. The committed text value comes as a parameter of the callback.
 * @param onInteraction Callback for handling [BrowserToolbarEvent]s on user interactions.
 */
@Composable
fun BrowserEditToolbar(
    query: String,
    hint: String,
    suggestion: AutocompleteResult? = null,
    isQueryPrefilled: Boolean = false,
    usePrivateModeQueries: Boolean = false,
    gravity: ToolbarGravity = Top,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    outlineColor: Color = DividerDefaults.color,
    showDivider: Boolean = true,
    editActionsStart: List<Action> = emptyList(),
    editActionsEnd: List<Action> = emptyList(),
    useDesktopEditToolbar: Boolean = false,
    browserActionsStart: List<Action> = emptyList(),
    browserActionsEnd: List<Action> = emptyList(),
    browserActionsColor: Color? = null,
    onDesktopUrlPillBoundsChanged: ((IntRect) -> Unit)? = null,
    onDismissDesktopSearch: (() -> Unit)? = null,
    onUrlEdit: (BrowserToolbarQuery) -> Unit = {},
    onUrlCommitted: (String) -> Unit = {},
    onInteraction: (BrowserToolbarEvent) -> Unit,
) {
    Surface(color = backgroundColor) {
        Box(
            modifier =
                Modifier.fillMaxWidth().semantics {
                    testTag = ADDRESSBAR_EDIT_MODE
                    testTagsAsResourceId = true
                }
        ) {
            if (useDesktopEditToolbar) {
                Row(
                    modifier = Modifier.adaptiveHorizontalPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (browserActionsStart.isNotEmpty()) {
                        BrowserActionsColorScheme(browserActionsColor) {
                            ActionContainer(
                                actions = browserActionsStart,
                                onInteraction = onInteraction,
                            )
                        }
                    }

                    val urlBoxShape = RoundedCornerShape(DESKTOP_EDIT_URL_CORNER_DP.dp)
                    EditPillContent(
                        query = query,
                        hint = hint,
                        suggestion = suggestion,
                        isQueryPrefilled = isQueryPrefilled,
                        usePrivateModeQueries = usePrivateModeQueries,
                        editActionsStart = editActionsStart,
                        editActionsEnd = editActionsEnd,
                        onUrlEdit = onUrlEdit,
                        onUrlCommitted = onUrlCommitted,
                        onInteraction = onInteraction,
                        onDismiss = onDismissDesktopSearch,
                        modifier =
                            Modifier.urlPillOuterPadding(
                                    gravity = gravity,
                                    hasBrowserActionsStart = browserActionsStart.isNotEmpty(),
                                    hasBrowserActionsEnd = browserActionsEnd.isNotEmpty(),
                                )
                                .weight(1f)
                                .height(48.dp)
                                .trackUrlPillStartAnchor()
                                .onGloballyPositioned { coordinates ->
                                    if (coordinates.isAttached) {
                                        onDesktopUrlPillBoundsChanged?.invoke(
                                            coordinates.boundsInWindow().roundToIntRect(),
                                        )
                                    }
                                }
                                .clip(shape = urlBoxShape)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = urlBoxShape,
                                )
                                .border(
                                    width = DESKTOP_EDIT_URL_BORDER_DP.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = urlBoxShape,
                                )
                                .padding(
                                    start = if (editActionsStart.isEmpty()) TOOLBAR_PADDING_DP.dp else NO_TOOLBAR_PADDING_DP.dp,
                                    end = if (editActionsEnd.isEmpty()) TOOLBAR_PADDING_DP.dp else NO_TOOLBAR_PADDING_DP.dp,
                                ),
                    )

                    if (browserActionsEnd.isNotEmpty()) {
                        BrowserActionsColorScheme(browserActionsColor) {
                            ActionContainer(
                                actions = browserActionsEnd,
                                onInteraction = onInteraction,
                            )
                        }
                    }
                }
            } else {
                EditPillContent(
                    query = query,
                    hint = hint,
                    suggestion = suggestion,
                    isQueryPrefilled = isQueryPrefilled,
                    usePrivateModeQueries = usePrivateModeQueries,
                    editActionsStart = editActionsStart,
                    editActionsEnd = editActionsEnd,
                    onUrlEdit = onUrlEdit,
                    onUrlCommitted = onUrlCommitted,
                    onInteraction = onInteraction,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(all = 8.dp)
                            .height(48.dp)
                            .clip(shape = CircleShape)
                            .background(color = MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }

            if (showDivider) {
                HorizontalDivider(
                    modifier =
                        Modifier.semantics {
                                testTag = ADDRESSBAR_EDIT_MODE_HORIZONTAL_DIVIDER
                            }
                            .align(
                                when (gravity) {
                                    Top -> Alignment.BottomCenter
                                    Bottom -> Alignment.TopCenter
                                }
                            ),
                    color = outlineColor,
                )
            }
        }
    }
}

@Composable
private fun EditPillContent(
    query: String,
    hint: String,
    suggestion: AutocompleteResult?,
    isQueryPrefilled: Boolean,
    usePrivateModeQueries: Boolean,
    editActionsStart: List<Action>,
    editActionsEnd: List<Action>,
    onUrlEdit: (BrowserToolbarQuery) -> Unit,
    onUrlCommitted: (String) -> Unit,
    onInteraction: (BrowserToolbarEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionContainer(
            actions = editActionsStart,
            onInteraction = onInteraction,
        )

        InlineAutocompleteTextField(
            query = query,
            hint = hint,
            suggestion = suggestion,
            showQueryAsPreselected = isQueryPrefilled,
            usePrivateModeQueries = usePrivateModeQueries,
            modifier = Modifier.weight(1f),
            onUrlEdit = onUrlEdit,
            onUrlCommitted = onUrlCommitted,
            onDismiss = onDismiss,
        )

        ActionContainer(
            actions = editActionsEnd,
            onInteraction = onInteraction,
        )
    }
}

@PreviewLightDark
@Composable
private fun BrowserEditToolbarPreview() {
    AcornTheme {
        BrowserEditToolbar(
            query = "http://www.mozilla.org",
            hint = "Search or enter address",
            gravity = Top,
            suggestion = null,
            editActionsStart =
                listOf(
                    SearchSelectorAction(
                        icon =
                            DrawableIcon(
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    iconsR.drawable.mozac_ic_search_24,
                                )!!
                            ),
                        contentDescription = StringResContentDescription(android.R.string.untitled),
                        menu = { emptyList() },
                        onClick = null,
                    )
                ),
            editActionsEnd =
                listOf(
                    ActionButtonRes(
                        drawableResId = iconsR.drawable.mozac_ic_microphone_24,
                        contentDescription = android.R.string.untitled,
                        onClick = object : BrowserToolbarEvent {},
                    ),
                    ActionButtonRes(
                        drawableResId = iconsR.drawable.mozac_ic_qr_code_24,
                        contentDescription = android.R.string.untitled,
                        onClick = object : BrowserToolbarEvent {},
                    ),
                ),
            onInteraction = {},
        )
    }
}

@Preview
@Composable
private fun BrowserEditToolbarPrivatePreview() {
    AcornTheme(
        colors = privateColorPalette,
        colorScheme = acornPrivateColorScheme(),
    ) {
        BrowserEditToolbar(
            query = "http://www.mozilla.org",
            hint = "Search or enter address",
            gravity = Top,
            suggestion = null,
            editActionsStart =
                listOf(
                    SearchSelectorAction(
                        icon =
                            DrawableIcon(
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    iconsR.drawable.mozac_ic_search_24,
                                )!!
                            ),
                        contentDescription = StringResContentDescription(android.R.string.untitled),
                        menu = { emptyList() },
                        onClick = null,
                    )
                ),
            editActionsEnd =
                listOf(
                    ActionButtonRes(
                        drawableResId = iconsR.drawable.mozac_ic_microphone_24,
                        contentDescription = android.R.string.untitled,
                        onClick = object : BrowserToolbarEvent {},
                    ),
                    ActionButtonRes(
                        drawableResId = iconsR.drawable.mozac_ic_qr_code_24,
                        contentDescription = android.R.string.untitled,
                        onClick = object : BrowserToolbarEvent {},
                    ),
                ),
            onInteraction = {},
        )
    }
}
