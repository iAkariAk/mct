package mct.gui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import mct.LoggerLevel
import mct.gui.model.LogEntry

fun coloredLogAnnotatedString(logLines: List<LogEntry>) = buildAnnotatedString {
    for ((i, entry) in logLines.withIndex()) {
        if (i > 0) append("\n")
        val level = entry.level ?: run {
            withStyle(SpanStyle(color = Color(0xFFE0E0E0))) { append(entry.message) }
            return@buildAnnotatedString
        }
        val badgeColor = when (level) {
            LoggerLevel.Info -> Color(0xFF4CAF50)
            LoggerLevel.Warning -> Color(0xFFFFA726)
            LoggerLevel.Error -> Color(0xFFEF5350)
            LoggerLevel.Debug -> Color(0xFF78909C)
        }
        val badgeLetter = level.name.first().uppercase()
        val textColor = when (level) {
            LoggerLevel.Info -> Color(0xFFC8E6C9)
            LoggerLevel.Warning -> Color(0xFFFFF3E0)
            LoggerLevel.Error -> Color(0xFFFFCDD2)
            LoggerLevel.Debug -> Color(0xFFB0BEC5)
        }
        withStyle(
            SpanStyle(
                color = Color.White, background = badgeColor,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            )
        ) { append(" $badgeLetter ") }
        append(" ")
        withStyle(SpanStyle(color = textColor)) { append(entry.message) }
    }
}
