package mct.gui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import mct.gui.components.*
import mct.gui.model.ApiTranslateState
import mct.gui.model.TranslateState
import mct.gui.model.TranslationApiKind
import mct.gui.model.TranslationEngine
import mct.gui.util.ensureJsonExt

@Composable
fun TranslatePanel(
    state: TranslateState,
    onStateChange: (TranslateState) -> Unit,
    translationProgress: () -> Float,
    translationStatus: () -> String,
    isRunning: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit = {},
    onOptimizePrompt: () -> Unit = {},
) {
    var showToken by remember { mutableStateOf(false) }
    var showApiToken by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    // Read the latest state at invocation time so every field callback below keeps a stable
    // identity: a captured `state` would be invalidated by any edit, recomposing the whole panel.
    val currentState by rememberUpdatedState(state)
    val api = state.api
    val updateApi: ((ApiTranslateState) -> ApiTranslateState) -> Unit = { transform ->
        onStateChange(currentState.copy(api = transform(api)))
    }

    val inputPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(currentState.copy(input = it.absolutePath())) } }

    val mappingSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(currentState.copy(mappingOutput = ensureJsonExt(it.absolutePath()))) }
    }
    val outputSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(currentState.copy(output = ensureJsonExt(it.absolutePath()))) }
    }
    val termSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(currentState.copy(termOutput = ensureJsonExt(it.absolutePath()))) }
    }
    val termPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(currentState.copy(existingTermPath = it.absolutePath())) } }
    val cachesPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(currentState.copy(cachesPath = it.absolutePath())) } }

    val readyToRun = state.input.isNotBlank() && state.output.isNotBlank() &&
            state.mappingOutput.isNotBlank() && state.termOutput.isNotBlank() &&
            when (state.engine) {
                TranslationEngine.Ai -> state.model.isNotBlank() && state.apiToken.isNotBlank()
                TranslationEngine.Api -> api.url.isNotBlank() && api.targetLanguage.isNotBlank()
            }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow(
            "提取结果 JSON（来自步骤①）",
            "选择 extractions.json...",
            state.input,
            { onStateChange(currentState.copy(input = it)) }) {
            inputPicker.launch()
        }
        PathRow(
            "输出替换Mapping JSON",
            "选择保存位置...",
            state.mappingOutput,
            { onStateChange(currentState.copy(mappingOutput = it)) }) {
            mappingSaver.launch(suggestedName = "mappings", defaultExtension = "json")
        }
        PathRow("输出替换文件 JSON", "选择保存位置...", state.output, { onStateChange(currentState.copy(output = it)) }) {
            outputSaver.launch(suggestedName = "replacements", defaultExtension = "json")
        }
        PathRow(
            "输出术语表 JSON",
            "选择保存位置...",
            state.termOutput,
            { onStateChange(currentState.copy(termOutput = it)) }) {
            termSaver.launch(suggestedName = "terms", defaultExtension = "json")
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("翻译引擎", Icons.Outlined.Tune)
        EnumButtonGroup(
            entries = TranslationEngine.entries,
            selected = state.engine,
            label = { it.label },
            onSelected = { onStateChange(currentState.copy(engine = it)) },
        )

        when (state.engine) {
            TranslationEngine.Ai -> {
                SectionTitle("AI API 配置", Icons.Outlined.Settings)
                Text(
                    "支持 OpenAI 及所有兼容接口（如 APIHub、OneAPI、LobeHub 等）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ConfigTextField(
                    value = state.apiUrl,
                    onValueChange = { onStateChange(currentState.copy(apiUrl = it)) },
                    label = { Text("API 地址") },
                    placeholder = { Text("留空使用 OpenAI 官方；或填入 https://api.openai.com/v1/") }
                )

                Box {
                    ConfigTextField(
                        value = state.model,
                        onValueChange = { onStateChange(currentState.copy(model = it)) },
                        label = { Text("模型名称") },
                        readOnly = true,
                        placeholder = { Text("例如 gpt-4o, gpt-4o-mini, deepseek-v4-pro...") },
                        trailingIcon = if (state.availableModels.isNotEmpty()) {
                            {
                                IconButton(onClick = { modelMenuExpanded = true }) {
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "选择模型")
                                }
                            }
                        } else null,
                    )
                    if (state.availableModels.isNotEmpty()) {
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            state.availableModels.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        onStateChange(currentState.copy(model = m))
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                ConfigTextField(
                    value = state.apiToken,
                    onValueChange = { onStateChange(currentState.copy(apiToken = it)) },
                    label = { Text("API 密钥") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(if (showToken) "隐藏" else "显示")
                        }
                    }
                )
            }

            TranslationEngine.Api -> {
                SectionTitle("API 翻译服务", Icons.Outlined.Cloud)
                Text(
                    "调用本地或自建的翻译接口，不消耗 AI 额度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "接口类型",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                EnumButtonGroup(
                    entries = TranslationApiKind.entries,
                    selected = api.kind,
                    label = { it.label },
                    onSelected = { kind -> updateApi { it.copy(kind = kind) } },
                )

                when (api.kind) {
                    TranslationApiKind.MTranServer -> {
                        ConfigTextField(
                            value = api.url,
                            onValueChange = { url -> updateApi { it.copy(url = url) } },
                            label = { Text("API 服务地址") },
                            placeholder = { Text("例如 http://127.0.0.1:8989/") }
                        )

                        ConfigTextField(
                            value = api.token,
                            onValueChange = { token -> updateApi { it.copy(token = token) } },
                            label = { Text("访问令牌（可选）") },
                            placeholder = { Text("留空则不附带 Authorization") },
                            visualTransformation = if (showApiToken) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                TextButton(onClick = { showApiToken = !showApiToken }) {
                                    Text(if (showApiToken) "隐藏" else "显示")
                                }
                            }
                        )

                        ConfigTextField(
                            value = api.sourceLanguage,
                            onValueChange = { source -> updateApi { it.copy(sourceLanguage = source) } },
                            label = { Text("源语言代码") },
                            placeholder = { Text("留空自动检测，例如 en_us") }
                        )

                        ConfigTextField(
                            value = api.targetLanguage,
                            onValueChange = { target -> updateApi { it.copy(targetLanguage = target) } },
                            label = { Text("目标语言代码") },
                            placeholder = { Text("zh_cn") }
                        )

                        ConfigTextField(
                            value = api.maxRetry,
                            onValueChange = { retry -> updateApi { it.copy(maxRetry = retry) } },
                            label = { Text("最大重试次数") },
                            placeholder = { Text("20") }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("可选设置", Icons.Outlined.MoreHoriz)

        PathRow(
            "已有术语表 JSON（可选）",
            "留空则从头翻译...",
            state.existingTermPath,
            { onStateChange(currentState.copy(existingTermPath = it)) }) {
            termPicker.launch()
        }
        PathRow(
            "翻译缓存 JSON（可选）",
            "留空则无缓存...",
            state.cachesPath,
            { onStateChange(currentState.copy(cachesPath = it)) }) {
            cachesPicker.launch()
        }

        if (state.engine == TranslationEngine.Ai) {
            LiteratureStyleField(
                value = state.literatureStyle,
                onValueChange = { onStateChange(currentState.copy(literatureStyle = it)) },
                optimizing = state.isOptimizing,
                onOptimizeClick = onOptimizePrompt,
            )

            MapInfoFields(
                value = state.mapInfo,
                onValueChange = { onStateChange(currentState.copy(mapInfo = it)) },
            )

            ExtraPromptsField(
                value = state.extraPrompts,
                onValueChange = { onStateChange(currentState.copy(extraPrompts = it)) },
            )

            Spacer(Modifier.height(12.dp))
            TextSwitch(
                modifier = Modifier.fillMaxWidth(),
                checked = state.handleGradientAggressively,
                onCheckedChange = { onStateChange(currentState.copy(handleGradientAggressively = it)) },
                text = "启用激进的渐变色文本处理",
            )

            Spacer(Modifier.height(12.dp))
            ConfigTextField(
                value = state.targetLanguage,
                onValueChange = { onStateChange(currentState.copy(targetLanguage = it)) },
                label = { Text("目标语言") },
                placeholder = { Text("简体中文") }
            )
        }

        TranslationProgressCard(
            isRunning = isRunning,
            progress = translationProgress,
            status = translationStatus,
        )

        ActionButton(
            "开始翻译",
            isRunning,
            onRun,
            enabled = readyToRun,
            onCancel = onCancel,
        )
    }
}

/**
 * Progress readout for a running translation.
 *
 * Progress ticks arrive many times per second, so [progress] / [status] are read here
 * rather than passed as values: a value parameter would re-execute the whole panel on
 * every tick, while a read in this scope invalidates only this card.
 */
@Composable
private fun TranslationProgressCard(
    isRunning: Boolean,
    progress: () -> Float,
    status: () -> String,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    // Material recommends its own spec for animating a wavy indicator's progress: it keeps the
    // wave continuous instead of letting it snap between ticks.
    val animatedProgress = animateFloatAsState(
        targetValue = progress().coerceIn(0f, 1f),
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "translation-progress",
    )

    AnimatedVisibility(
        visible = isRunning,
        modifier = modifier,
        enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "翻译进度",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${(progress() * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // M3 Expressive wavy progress indicator. It supplies its own container height,
                // track, gap and stop indicator, and exposes progress to accessibility services.
                LinearWavyProgressIndicator(
                    progress = { animatedProgress.value },
                    modifier = Modifier.fillMaxWidth(),
                )
                val statusText = status()
                if (statusText.isNotBlank()) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
