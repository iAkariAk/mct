package mct.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import mct.gui.model.Tab
import mct.gui.util.renderWithUnit

/**
 * Navigation rail with tab selection and token consumption display.
 */
@Composable
fun NavigationRailPanel(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    totalTokenConsume: Int,
    lastTokenConsume: Int,
    uriHandler: UriHandler,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        header = {
            Spacer(Modifier.height(8.dp))
            IconButton(onClick = {
                uriHandler.openUri("https://github.com/iAkariAk/mct")
            }) {
                Icon(
                    Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        },
        modifier = modifier.padding(end = 4.dp),
        containerColor = Color.Transparent,
    ) {
        NavigationRailItem(
            selected = selectedTab == Tab.Extract,
            onClick = { onTabSelected(Tab.Extract) },
            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            label = { Text(Tab.Extract.label, style = MaterialTheme.typography.labelSmall) })
        NavigationRailItem(
            selected = selectedTab == Tab.Translate,
            onClick = { onTabSelected(Tab.Translate) },
            icon = { Icon(Icons.Outlined.Translate, contentDescription = null) },
            label = { Text(Tab.Translate.label, style = MaterialTheme.typography.labelSmall) })
        NavigationRailItem(
            selected = selectedTab == Tab.TermExtract,
            onClick = { onTabSelected(Tab.TermExtract) },
            icon = { Icon(Icons.Outlined.Bookmark, contentDescription = null) },
            label = { Text(Tab.TermExtract.label, style = MaterialTheme.typography.labelSmall) })
        NavigationRailItem(
            selected = selectedTab == Tab.Backfill,
            onClick = { onTabSelected(Tab.Backfill) },
            icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
            label = { Text(Tab.Backfill.label, style = MaterialTheme.typography.labelSmall) })
        NavigationRailItem(
            selected = selectedTab == Tab.Toolbox,
            onClick = { onTabSelected(Tab.Toolbox) },
            icon = { Icon(Icons.Outlined.Handyman, contentDescription = null) },
            label = { Text(Tab.Toolbox.label, style = MaterialTheme.typography.labelSmall) })
        Spacer(Modifier.weight(1f))
        if (totalTokenConsume > 0) {
            TokenDisplay(totalTokenConsume = totalTokenConsume, lastTokenConsume = lastTokenConsume)
        }
    }
}

@Composable
private fun TokenDisplay(totalTokenConsume: Int, lastTokenConsume: Int) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            totalTokenConsume.renderWithUnit(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (lastTokenConsume > 0) {
            Text(
                "+${lastTokenConsume.renderWithUnit()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = .6f),
            )
        }
    }
}
