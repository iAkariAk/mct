package mct.gui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mct.LoggerLevel
import mct.extra.translator.CustomizedPrompts

// ── 可复用组件 ───────────────────────────────────────────────

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun PathRow(
    label: String, placeholder: String,
    value: String, onValueChange: (String) -> Unit,
    onBrowse: () -> Unit
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(placeholder) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            FilledTonalButton(onClick = onBrowse) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("浏览")
            }
        }
    }
}

@Composable
fun ModeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActionButton(label: String, running: Boolean, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled && !running,
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text("运行中...")
        } else {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

// ── Enums ──────────────────────────────────────────────────────

enum class Tab(val label: String) {
    Extract("提取文本"),
    Translate("AI 翻译"),
    Backfill("回填存档")
}

enum class RunMode(val key: String, val label: String) {
    Region("region", "Region (.mca 区域文件)"),
    Datapack("datapack", "Datapack (数据包)")
}

// ── State holders ──────────────────────────────────────────────

data class ExtractState(
    val input: String = "",
    val output: String = "extractions.json",
    val mode: RunMode = RunMode.Region,
    val disableFilter: Boolean = false,
    val regionPatternPath: String = "",
    val mcfPatternPath: String = "",
    val mcjPatternPath: String = "",
)

data class TranslateState(
    val input: String = "extractions.json",
    val output: String = "replacements.json",
    val mappingOutput: String = "mappings.json",
    val termOutput: String = "terms.json",
    val apiUrl: String = "",
    val apiToken: String = "",
    val model: String = "gpt-4o",
    val useStreamApi: Boolean = false,
    val existingTermPath: String = "",
    val literatureStyle: String = CustomizedPrompts.literatureStyle,
)

data class BackfillState(
    val input: String = "",
    val replacements: String = "replacements.json",
    val mode: RunMode = RunMode.Region,
)

// ── 通用组件 ─────────────────────────────────────────────────

@Composable
fun ConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        modifier = modifier,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

// ── 结构化日志 ────────────────────────────────────────────────

data class LogEntry(
    val level: LoggerLevel?,
    val message: String,
)

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
            LoggerLevel.Sign -> Color(0xFF9C27B0)
        }
        val badgeLetter = level.name.first().uppercase()
        val textColor = when (level) {
            LoggerLevel.Info -> Color(0xFFC8E6C9)
            LoggerLevel.Warning -> Color(0xFFFFF3E0)
            LoggerLevel.Error -> Color(0xFFFFCDD2)
            LoggerLevel.Debug -> Color(0xFFB0BEC5)
            LoggerLevel.Sign -> Color(0xFFE1BEE7)
        }
        withStyle(SpanStyle(
            color = Color.White, background = badgeColor,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        )) { append(" $badgeLetter ") }
        append(" ")
        withStyle(SpanStyle(color = textColor)) { append(entry.message) }
    }
}