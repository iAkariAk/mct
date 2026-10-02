@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shared "function area": one card that shows a catalogue of functions and morphs into the page
 * behind whichever one is selected.
 *
 * It is the single presentation primitive for a surface a user can switch around inside — the
 * project workspace's sections and the toolbox's tools are both this, so a new destination only has
 * to name its functions and their pages. The morph is Material's "fade through": the two contents
 * are not spatially related, so nothing slides, and a slide would spend most of the animation
 * clipped by the card's own rounded edge.
 *
 * [selected] is the function whose page is shown, or `null` for the catalogue. Keeping it in the
 * caller's state rather than inside this composable is what lets the selection survive a tab
 * switch.
 */
@Composable
fun <K> FunctionArea(
    selected: K?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    catalogue: @Composable () -> Unit,
    page: @Composable (key: K, onBack: () -> Unit) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    FunctionSurface(modifier) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                    scaleIn(animationSpec = motionScheme.defaultSpatialSpec(), initialScale = 0.94f)
                val exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.98f)
                enter togetherWith exit
            },
            modifier = Modifier.fillMaxSize(),
            label = "function-area",
        ) { key ->
            if (key == null) catalogue() else page(key, onBack)
        }
    }
}

/**
 * The tonal card every function area sits on, and the surface the single-function tabs use as well,
 * so a page reads the same wherever it is shown.
 */
@Composable
fun FunctionSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}

/**
 * Header of a function's page: the way back to the catalogue, the function's name, and what it does.
 */
@Composable
fun FunctionPageHeader(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    backLabel: String = "返回功能区",
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HoverHint(backLabel) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backLabel)
            }
        }
        Surface(modifier = Modifier.size(40.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Accent of a function card; the neutral one is for a function with nothing to show yet. */
enum class FunctionAccent { Primary, Secondary, Tertiary, Neutral }

@Composable
private fun FunctionAccent.containerColor(): Color = when (this) {
    FunctionAccent.Primary -> MaterialTheme.colorScheme.primaryContainer
    FunctionAccent.Secondary -> MaterialTheme.colorScheme.secondaryContainer
    FunctionAccent.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
    FunctionAccent.Neutral -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun FunctionAccent.contentColor(): Color = when (this) {
    FunctionAccent.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    FunctionAccent.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
    FunctionAccent.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
    FunctionAccent.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Everything one function card shows.
 *
 * [detail] is the machine-readable line under the description — a file path, or the fields a tool
 * asks for — and [badge] a short count or state. Both are optional: a card with neither is the same
 * card, just shorter.
 */
@Immutable
data class FunctionCardSpec<K>(
    val key: K,
    val title: String,
    val supporting: String,
    val icon: ImageVector,
    val detail: String? = null,
    val badge: String? = null,
    val accent: FunctionAccent = FunctionAccent.Secondary,
)

/** A titled group of functions inside a catalogue. */
@Immutable
data class FunctionGroupSpec<K>(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val cards: List<FunctionCardSpec<K>>,
)

/**
 * A grouping of function cards: the group's header, then the grid.
 *
 * [twoColumnWidth] is where a second column starts to fit; a card keeps its content height with
 * [minCardHeight] as the floor. A caller that shares the available height between rows — the project
 * dashboard — uses [FunctionGrid] directly instead, because it has to derive the row height from the
 * column count itself.
 */
@Composable
fun <K> FunctionGroupBlock(
    spec: FunctionGroupSpec<K>,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    twoColumnWidth: Dp = 560.dp,
    minCardHeight: Dp = 112.dp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(spec.title, spec.icon)
        if (spec.description.isNotBlank()) {
            Text(
                spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The width is what decides the column count, and a catalogue is normally inside a scrolling
        // column: only the height is unbounded there, so measuring the width here is safe.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            FunctionGrid(
                cards = spec.cards,
                onSelect = onSelect,
                columns = if (maxWidth >= twoColumnWidth) 2 else 1,
                minCardHeight = minCardHeight,
            )
        }
    }
}

/**
 * Grid of function cards.
 *
 * One column until a card can carry its title, its detail line and its badge without ellipsising the
 * grown ones, two after that, and never a third — a third column wraps every card's title. The
 * column count is the caller's, because a caller that fills the available height has to derive the
 * row height from the same number.
 */
@Composable
fun <K> FunctionGrid(
    cards: List<FunctionCardSpec<K>>,
    onSelect: (K) -> Unit,
    columns: Int,
    modifier: Modifier = Modifier,
    minCardHeight: Dp = 112.dp,
    rowHeight: Dp? = null,
) {
    val spacing = 12.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        cards.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                row.forEach { card ->
                    val height = rowHeight?.let { Modifier.height(it) } ?: Modifier.heightIn(min = minCardHeight)
                    FunctionCard(card, onSelect, Modifier.weight(1f).then(height))
                }
                // Keep a short row's cards at their column width instead of stretching them.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun <K> FunctionCard(
    card: FunctionCardSpec<K>,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onSelect(card.key) },
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.large,
                color = card.accent.containerColor(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(card.icon, contentDescription = null, tint = card.accent.contentColor())
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                // The badge shares the title line instead of holding a column of its own: as a
                // sibling of the text block it cost ~64dp on every card, which is what left the
                // description wrapping mid-word on a narrow window.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        card.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (card.badge != null) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                card.badge,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    card.supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.detail != null) {
                    Text(
                        card.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
