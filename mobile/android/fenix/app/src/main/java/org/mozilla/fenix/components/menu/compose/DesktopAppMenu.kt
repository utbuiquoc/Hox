/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.menu.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import mozilla.components.compose.base.text.value
import mozilla.components.compose.menu.data.BannerMenuItem
import mozilla.components.compose.menu.data.ExpandableMenuItem
import mozilla.components.compose.menu.data.MenuAttribution
import mozilla.components.compose.menu.data.MenuItem
import mozilla.components.compose.menu.data.MenuItemActionButton
import mozilla.components.compose.menu.data.MenuItemBadge
import mozilla.components.compose.menu.data.StandardMenuItem
import mozilla.components.compose.menu.store.MenuEvent
import mozilla.components.compose.menu.store.MenuStore
import mozilla.components.compose.menu.ui.MenuItemIcon
import mozilla.components.compose.menu.ui.MenuItemIconBitmap
import mozilla.components.compose.menu.ui.MenuItemIconDrawable
import mozilla.components.compose.menu.ui.MenuItemIconRes
import mozilla.components.compose.menu.ui.MenuItemState
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.ui.icons.R as iconsR

private val ROW_HEIGHT = 40.dp
private val ROW_HORIZONTAL_PADDING = 14.dp
private val ICON_SIZE = 18.dp
private val LABEL_SIZE = 14.sp
private val SUMMARY_SIZE = 12.sp
private val CHEVRON_SIZE = 16.dp

/**
 * Renders the menu as a compact desktop-style single-column dropdown list, matching the Firefox
 * desktop app menu: short icon + label rows separated by group dividers, instead of the mobile
 * bottom-sheet presentation (sticky navigation grid, shortcut grid and promotional banners).
 *
 * Items are read from the same [MenuStore] as the mobile menu so every action, navigation and
 * telemetry event keeps working unchanged.
 */
@Composable
fun DesktopAppMenu(
    store: MenuStore,
    modifier: Modifier = Modifier,
) {
    val menuState by store.observeAsComposableState { it }
    val onInteraction: (MenuEvent) -> Unit = remember(store) { { store.dispatch(it) } }

    // Sticky navigation and promotional banners belong to mobile layouts; desktop omits them.
    val groups =
        menuState.menuGroups
            .filterNot { it.isSticky }
            .map { group -> group.items.filterNot { it is BannerMenuItem } }
            .filter { it.isNotEmpty() }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        groups.forEachIndexed { index, items ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            items.forEach { item ->
                DesktopMenuItem(item = item, onInteraction = onInteraction)
            }
        }

        menuState.attribution?.let { attribution ->
            if (groups.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            DesktopAttributionRow(attribution = attribution)
        }
    }
}

@Composable
private fun DesktopMenuItem(
    item: MenuItem,
    onInteraction: (MenuEvent) -> Unit,
) {
    when (item) {
        is StandardMenuItem -> DesktopStandardMenuItem(item = item, onInteraction = onInteraction)

        is ExpandableMenuItem -> {
            var expanded by remember(item) { mutableStateOf(false) }
            if (!(expanded && item.hideOnExpand)) {
                DesktopRow(
                    title = item.title.text.value,
                    summary = item.summary?.text?.value,
                    summaryState = item.summary?.state ?: MenuItemState.DEFAULT,
                    icon = item.icon,
                    state = item.state,
                    enabled = item.state != MenuItemState.DISABLED,
                    onClick = {
                        expanded = !expanded
                        onInteraction(item.onClickEvent)
                    },
                    trailing = {
                        item.badge?.let { badge ->
                            DesktopBadge(badge = badge)
                        }
                        Chevron(expanded = expanded)
                    },
                )
            }
            if (expanded) {
                item.subMenuItems.forEach { sub ->
                    DesktopStandardMenuItem(
                        item = sub,
                        onInteraction = onInteraction,
                        indent = !item.hideOnExpand,
                    )
                }
            }
        }

        is BannerMenuItem -> Unit
    }
}

@Composable
private fun DesktopStandardMenuItem(
    item: StandardMenuItem,
    onInteraction: (MenuEvent) -> Unit,
    indent: Boolean = false,
) {
    val isSwitch = item.role == Role.Switch
    DesktopRow(
        title = item.title.text.value,
        summary = item.summary?.text?.value,
        summaryState = item.summary?.state ?: MenuItemState.DEFAULT,
        icon = item.icon,
        state = item.state,
        enabled = item.state != MenuItemState.DISABLED,
        indent = indent,
        onClick = { item.onClickEvent?.let(onInteraction) },
        trailing = {
            item.badge?.let { badge ->
                if (!isSwitch) {
                    DesktopBadge(badge = badge)
                }
            }
            if (isSwitch) {
                Switch(
                    checked = item.state == MenuItemState.ACTIVE,
                    onCheckedChange = { item.onClickEvent?.let(onInteraction) },
                )
            }
            item.trailingIcon?.let { iconRes ->
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.actionButton?.let { actionButton ->
                DesktopActionButton(
                    actionButton = actionButton,
                    onInteraction = onInteraction,
                )
            }
        },
    )
}

@Composable
private fun DesktopRow(
    title: String,
    icon: MenuItemIcon?,
    state: MenuItemState,
    enabled: Boolean,
    onClick: () -> Unit,
    summary: String? = null,
    summaryState: MenuItemState = MenuItemState.DEFAULT,
    indent: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    val labelColor = stateTextColor(state)
    val summaryColor =
        when (summaryState) {
            MenuItemState.ACTIVE -> MaterialTheme.colorScheme.primary
            MenuItemState.WARNING -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(
                    start = if (indent) ROW_HORIZONTAL_PADDING + 12.dp else ROW_HORIZONTAL_PADDING,
                    end = ROW_HORIZONTAL_PADDING,
                    top = if (summary != null) 4.dp else 0.dp,
                    bottom = if (summary != null) 4.dp else 0.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = icon.painterCompat(),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = labelColor.copy(alpha = contentAlpha),
                fontSize = LABEL_SIZE,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    color = summaryColor.copy(alpha = contentAlpha),
                    fontSize = SUMMARY_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun DesktopBadge(badge: MenuItemBadge) {
    val containerColor =
        when (badge.state) {
            MenuItemState.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
            MenuItemState.WARNING -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        when (badge.state) {
            MenuItemState.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
            MenuItemState.WARNING -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = badge.text.value,
        modifier =
            Modifier
                .background(color = containerColor, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        color = contentColor,
        fontSize = 11.sp,
        maxLines = 1,
    )
}

@Composable
private fun DesktopActionButton(
    actionButton: MenuItemActionButton,
    onInteraction: (MenuEvent) -> Unit,
) {
    val description = actionButton.contentDescription.value
    val isEnabled = actionButton.state != MenuItemState.DISABLED
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (actionButton.showDivider) {
            VerticalDivider(
                modifier = Modifier.height(20.dp).padding(end = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        IconButton(
            onClick = { onInteraction(actionButton.onClickEvent) },
            enabled = isEnabled,
            modifier =
                Modifier
                    .size(28.dp)
                    .semantics { contentDescription = description },
        ) {
            Icon(
                painter = painterResource(id = actionButton.icon),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = stateTextColor(actionButton.state),
            )
        }
    }
}

@Composable
private fun DesktopAttributionRow(attribution: MenuAttribution) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attribution.icon?.let { icon ->
            Icon(
                painter = icon.painterCompat(),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = attribution.title.value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = SUMMARY_SIZE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    Icon(
        painter = painterResource(id = iconsR.drawable.mozac_ic_chevron_down_24),
        contentDescription = null,
        modifier =
            Modifier.size(CHEVRON_SIZE).graphicsLayer {
                rotationZ = if (expanded) 180f else 0f
            },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun stateTextColor(state: MenuItemState): Color =
    when (state) {
        MenuItemState.ACTIVE -> MaterialTheme.colorScheme.primary
        MenuItemState.WARNING -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

@Composable
private fun MenuItemIcon.painterCompat(): Painter =
    when (this) {
        is MenuItemIconRes -> painterResource(iconRes)
        is MenuItemIconDrawable -> remember(this) { BitmapPainter(icon.toBitmap().asImageBitmap()) }
        is MenuItemIconBitmap -> remember(this) { BitmapPainter(icon.asImageBitmap()) }
    }
