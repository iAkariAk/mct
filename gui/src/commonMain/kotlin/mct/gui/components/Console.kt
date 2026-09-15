package mct.gui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import mct.LoggerLevel
import mct.gui.model.LogEntry
import mct.gui.util.PathLink

/** Badge and message colors of one log level. */
@Immutable
data class ConsoleLevelColors(
    val badge: Color,
    val onBadge: Color,
    val message: Color,
    /** [message] pulled toward the surface text color, for text sitting on a match highlight. */
    val matched: Color,
)

/** Everything a console line takes from the active color scheme. */
@Immutable
data class ConsoleColors(
    val info: ConsoleLevelColors,
    val warning: ConsoleLevelColors,
    val error: ConsoleLevelColors,
    val debug: ConsoleLevelColors,
    /** Color of banner lines that carry no level. */
    val plain: Color,
    val link: TextLinkStyles,
    /** Tint behind a match that is not the focused one. */
    val matchBackground: Color,
    val currentMatchBackground: Color,
    val currentMatchForeground: Color,
) {
    fun level(level: LoggerLevel): ConsoleLevelColors = when (level) {
        LoggerLevel.Info -> info
        LoggerLevel.Warning -> warning
        LoggerLevel.Error -> error
        LoggerLevel.Debug -> debug
    }
}

/**
 * Console palette derived from the active scheme, so light and dark themes both stay readable.
 *
 * Levels use the Material accent/`on`-accent role pairs (contrast-checked per scheme) instead of
 * the dark-only literals this replaces. Matches are a translucent tint of the accent, which leaves
 * the level color of the matched text legible; the focused match swaps in the accent itself.
 */
@Composable
fun rememberConsoleColors(): ConsoleColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { consoleColors(scheme) }
}

/**
 * How far a matched color is pulled toward the surface text color. A saturated level color (error
 * red, warning amber) loses legibility once the accent tint sits behind it; mixing the foreground
 * keeps the hue recognisable while restoring contrast.
 */
private const val MATCH_FOREGROUND_MIX = 0.45f

private fun consoleColors(scheme: ColorScheme) = ConsoleColors(
    info = scheme.levelColors(scheme.primary, scheme.onPrimary, scheme.onSurface),
    warning = scheme.levelColors(scheme.tertiary, scheme.onTertiary, scheme.tertiary),
    error = scheme.levelColors(scheme.error, scheme.onError, scheme.error),
    debug = scheme.levelColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, scheme.onSurfaceVariant),
    plain = scheme.onSurface,
    link = TextLinkStyles(
        style = SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline),
        hoveredStyle = SpanStyle(background = scheme.primary.copy(alpha = 0.12f)),
        pressedStyle = SpanStyle(background = scheme.primary.copy(alpha = 0.24f)),
    ),
    matchBackground = scheme.primary.copy(alpha = 0.22f),
    currentMatchBackground = scheme.primary,
    currentMatchForeground = scheme.onPrimary,
)

private fun ColorScheme.levelColors(badge: Color, onBadge: Color, message: Color) = ConsoleLevelColors(
    badge = badge,
    onBadge = onBadge,
    message = message,
    matched = lerp(message, onSurface, MATCH_FOREGROUND_MIX),
)

/**
 * Renders one console line: level badge, message, find-bar highlights and clickable paths.
 *
 * [hits] are the ranges matched inside [LogEntry.message] and [currentHit] is the focused one;
 * both are message-relative, the badge prefix is accounted for here.
 */
fun consoleLine(
    entry: LogEntry,
    colors: ConsoleColors,
    links: List<PathLink>,
    hits: List<IntRange>,
    currentHit: IntRange?,
    onOpenPath: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val level = entry.level
    val messageStart: Int
    val messageColor: Color
    val matchedColor: Color
    if (level == null) {
        messageStart = 0
        messageColor = colors.plain
        matchedColor = colors.plain
    } else {
        val levelColors = colors.level(level)
        withStyle(
            SpanStyle(
                color = levelColors.onBadge,
                background = levelColors.badge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        ) { append(" ${level.name.first().uppercase()} ") }
        append(" ")
        messageStart = length
        messageColor = levelColors.message
        matchedColor = levelColors.matched
    }

    withStyle(SpanStyle(color = messageColor)) { append(entry.message) }

    hits.forEach { range ->
        val style = if (range == currentHit) {
            SpanStyle(background = colors.currentMatchBackground, color = colors.currentMatchForeground)
        } else {
            SpanStyle(background = colors.matchBackground, color = matchedColor)
        }
        addStyle(style, messageStart + range.first, messageStart + range.last + 1)
    }

    links.forEach { link ->
        addLink(
            LinkAnnotation.Clickable(
                tag = link.path,
                styles = colors.link,
                linkInteractionListener = { onOpenPath(link.path) },
            ),
            messageStart + link.range.first,
            messageStart + link.range.last + 1,
        )
    }
}
