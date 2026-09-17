@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mct.gui.components.*
import mct.gui.model.ProjectSection
import mct.gui.model.label
import mct.gui.services.PROJECT_FILE
import mct.gui.state.ProjectConfigEditor
import mct.gui.state.ProjectController
import mct.gui.state.ProjectEngineKind
import mct.model.patch.PathKind

/**
 * The project configuration page: a GUI view of `mct.toml`.
 *
 * Every field is one of the CLI's own configuration values, labelled with its TOML key and hinted
 * with the comment the CLI writes for it, in Chinese. Fields read their own slice of the editor
 * state, so typing here recomposes that field and nothing else. Groups follow the file's own order,
 * so the page reads like the document it edits.
 */
@Composable
fun ProjectConfigSection(controller: ProjectController, modifier: Modifier = Modifier) {
    val editor = controller.editor ?: return

    Column(modifier = modifier.fillMaxSize()) {
        ConfigSectionHeader(controller, editor)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (!editor.isLoaded) {
            ConfigPlaceholder(controller, editor)
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ProjectInfoGroup(editor) }
            item { ProjectFilesGroup(editor) }
            item { ProjectPatternsGroup(editor) }
            item { ProjectMapInfoGroup(editor) }
            item { ProjectAiGroup(editor) }
            item { ProjectEngineGroup(editor) }
            item { ProjectPatchGroup(editor) }
        }
    }
}

@Composable
private fun ConfigSectionHeader(controller: ProjectController, editor: ProjectConfigEditor) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HoverHint("返回功能区") {
            IconButton(onClick = { controller.showSection(ProjectSection.Dashboard) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回功能区")
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("项目配置", style = MaterialTheme.typography.titleLarge)
                if (editor.isDirty) {
                    UnsavedChip()
                }
            }
            Text(
                "$PROJECT_FILE — 注释以字段下方的提示显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HoverHint("放弃未保存的修改，重新读取文件") {
            IconButton(
                onClick = { controller.refreshData(preserveEdits = false) },
                enabled = editor.isDirty && !editor.isSaving,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "放弃修改", modifier = Modifier.size(20.dp))
            }
        }
        HoverHint("在资源管理器中打开 $PROJECT_FILE") {
            IconButton(onClick = { controller.revealProjectFile(PROJECT_FILE) }) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = "打开 $PROJECT_FILE",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Button(
            onClick = controller::saveConfig,
            enabled = editor.isDirty && !editor.isSaving,
            shapes = ButtonDefaults.shapes(),
        ) {
            if (editor.isSaving) {
                LoadingIndicator(modifier = Modifier.size(18.dp), color = LocalContentColor.current)
            } else {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("保存")
        }
    }
}

@Composable
private fun ConfigPlaceholder(controller: ProjectController, editor: ProjectConfigEditor) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            val error = editor.loadError
            if (error == null) {
                LoadingIndicator()
                Text(
                    "正在读取 $PROJECT_FILE ...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text("无法读取 $PROJECT_FILE", style = MaterialTheme.typography.titleMedium)
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = controller::refreshData, shapes = ButtonDefaults.shapes()) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoGroup(editor: ProjectConfigEditor) {
    ConfigGroup("项目", Icons.Outlined.Info) {
        ConfigTextField(
            label = "项目名称",
            hint = "name — 项目名称",
            value = editor.name,
            onValueChange = { value -> editor.update { it.copy(name = value) } },
        )
        ConfigNullableTextField(
            label = "项目版本",
            hint = "version — 项目版本",
            value = editor.version,
            onValueChange = { value -> editor.update { it.copy(version = value) } },
        )
        ConfigNullableTextField(
            label = "项目描述",
            hint = "description — 项目描述",
            value = editor.description,
            onValueChange = { value -> editor.update { it.copy(description = value) } },
        )
    }
}

@Composable
private fun ProjectFilesGroup(editor: ProjectConfigEditor) {
    ConfigGroup("文件", Icons.Outlined.FolderOpen) {
        ConfigTextField(
            label = "映射文件",
            hint = "mappings — 翻译映射 JSON 文件路径（原文 -> 译文）",
            value = editor.mappings,
            onValueChange = { value -> editor.update { it.copy(mappings = value) } },
        )
        ConfigNullableTextField(
            label = "MTLX 文件",
            hint = "mtlx — MTLX 文件路径（留空表示不使用 MTLX）",
            value = editor.mtlx,
            onValueChange = { value -> editor.update { it.copy(mtlx = value) } },
            placeholder = "留空表示不使用 MTLX",
        )
        ConfigTextField(
            label = "术语表",
            hint = "terms — 术语表 JSON 文件路径",
            value = editor.terms,
            onValueChange = { value -> editor.update { it.copy(terms = value) } },
        )
        ConfigSwitchRow(
            label = "美化 JSON 输出",
            hint = "pretty_json — 输出格式化的 JSON",
            checked = editor.prettyJson,
            onCheckedChange = { value -> editor.update { it.copy(prettyJson = value) } },
        )
    }
}

@Composable
private fun ProjectPatternsGroup(editor: ProjectConfigEditor) {
    ConfigGroup("提取规则 patterns", Icons.AutoMirrored.Outlined.Rule) {
        Text(
            "每一类的规则文件之外，还可以决定是否保留该类的内置规则。从磁盘选择的文件会写成绝对路径："
                    + "这些路径由 CLI 按其自身工作目录解析，而不是项目目录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        editor.patternSlots.forEachIndexed { index, slot ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ConfigPatternSlotField(slot)
        }
    }
}

@Composable
private fun ProjectMapInfoGroup(editor: ProjectConfigEditor) {
    ConfigGroup("地图信息 map_info", Icons.Outlined.Map) {
        Text(
            "map_info — 提供地图信息，帮助 LLM 更好地理解上下文",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MapInfoFields(
            value = editor.mapInfo.value,
            onValueChange = { value -> editor.update { it.copy(mapInfo = value) } },
        )
    }
}

@Composable
private fun ProjectAiGroup(editor: ProjectConfigEditor) {
    ConfigGroup("AI 翻译 ai", Icons.Outlined.AutoAwesome) {
        ConfigTextField(
            label = "AI 接口地址",
            hint = "ai.api_url — OpenAI 兼容的 API 地址（例如 https://api.openai.com/v1/ 或 https://api.deepseek.com/v1/）",
            value = editor.aiApiUrl,
            onValueChange = { value -> editor.updateAi { it.copy(apiUrl = value) } },
        )
        ConfigTextField(
            label = "API Token",
            hint = "ai.token — API 访问令牌；可用 @XXX 引用同名环境变量",
            value = editor.aiToken,
            onValueChange = { value -> editor.updateAi { it.copy(token = value) } },
            monospace = true,
        )
        ConfigTextField(
            label = "模型",
            hint = "ai.model — 模型名称（例如 gpt-5-luna、deepseek-flash）",
            value = editor.aiModel,
            onValueChange = { value -> editor.updateAi { it.copy(model = value) } },
        )
        ConfigSwitchRow(
            label = "使用流式 API",
            hint = "ai.use_stream_api — 使用流式 API（可缓解部分服务商的空响应问题；默认 true）",
            checked = editor.aiUseStreamApi,
            onCheckedChange = { value -> editor.updateAi { it.copy(useStreamApi = value) } },
        )
        ConfigIntField(
            label = "分块 token 上限",
            hint = "ai.token_threshold — 每次翻译请求的最大 token 数",
            value = editor.aiTokenThreshold,
            onValueChange = { value -> editor.updateAi { it.copy(tokenThreshold = value) } },
        )
        ConfigTextField(
            label = "目标语言",
            hint = "ai.target_language — 目标语言（例如 简体中文、English、日本語）",
            value = editor.aiTargetLanguage,
            onValueChange = { value -> editor.updateAi { it.copy(targetLanguage = value) } },
        )
        ConfigSliderField(
            label = "温度 temperature",
            hint = "ai.temperature — 模型温度（0.0–2.0；留空使用模型默认值，通常为 1.0）",
            value = editor.aiTemperature,
            onValueChange = { value -> editor.updateAi { it.copy(temperature = value) } },
        )
        ConfigSwitchRow(
            label = "渐变文本激进处理",
            hint = "ai.handle_gradient — 激进处理渐变文本",
            checked = editor.aiHandleGradient,
            onCheckedChange = { value -> editor.updateAi { it.copy(handleGradientAggressively = value) } },
        )
        ConfigTextField(
            label = "文学风格提示词",
            hint = "ai.literature_style — 自定义文学风格翻译提示词",
            value = editor.aiLiteratureStyle,
            onValueChange = { value -> editor.updateAi { it.copy(literatureStyle = value) } },
            singleLine = false,
            minLines = 4,
            monospace = true,
        )
        ConfigNullableTextField(
            label = "额外提示词",
            hint = "ai.extra_prompts — 追加在所有提示词末尾；填写不当会破坏 AI 翻译",
            value = editor.aiExtraPrompts,
            onValueChange = { value -> editor.updateAi { it.copy(extraPrompts = value) } },
            singleLine = false,
            minLines = 3,
            monospace = true,
            placeholder = "留空表示不追加",
        )
        ConfigSwitchRow(
            label = "HTTP 日志",
            hint = "ai.http_logging — 输出 HTTP 日志用于调试（默认 false）",
            checked = editor.aiHttpLogging,
            onCheckedChange = { value -> editor.updateAi { it.copy(enableHttpLogging = value) } },
        )
        ConfigSwitchRow(
            label = "输出思考过程",
            hint = "ai.thinking_output — 输出 LLM 思考过程（默认 false）",
            checked = editor.aiThinkingOutput,
            onCheckedChange = { value -> editor.updateAi { it.copy(enableThinkingOutput = value) } },
        )
        ConfigSwitchRow(
            label = "静态检查",
            hint = "ai.static_checking — 启用静态检查（可能让LLM思考链更长）（默认 false）",
            checked = editor.aiStaticChecking,
            onCheckedChange = { value -> editor.updateAi { it.copy(staticChecking = value) } },
        )
    }
}

@Composable
private fun ProjectEngineGroup(editor: ProjectConfigEditor) {
    ConfigGroup("翻译引擎 translation", Icons.Outlined.Translate) {
        ConfigButtonGroupRow(
            label = "引擎",
            hint = "translation.engine — 翻译文本所使用的引擎",
            entries = ProjectEngineKind.entries,
            selected = editor.engineKind,
            entryLabel = { it.label },
            onSelected = editor::selectEngineKind,
        )
        if (editor.engineKind.value == ProjectEngineKind.Api) {
            ConfigTextField(
                label = "服务地址",
                hint = "translation.engine.api_url — MTranServer 服务地址",
                value = editor.apiUrl,
                onValueChange = editor::setApiUrl,
            )
            ConfigNullableTextField(
                label = "服务 Token",
                hint = "translation.engine.token — 可选的服务访问令牌",
                value = editor.apiToken,
                onValueChange = editor::setApiToken,
                monospace = true,
            )
            ConfigIntField(
                label = "最大重试次数",
                hint = "translation.engine.config.max_retry — 每次请求的最大重试次数",
                value = editor.apiMaxRetry,
                onValueChange = { value -> editor.updateApiConfig { it.copy(maxRetry = value) } },
            )
            ConfigNullableTextField(
                label = "源语言代码",
                hint = "translation.engine.config.source — 源语言代码；留空自动识别",
                value = editor.apiSource,
                onValueChange = { value -> editor.updateApiConfig { it.copy(source = value) } },
                placeholder = "留空自动识别",
            )
            ConfigTextField(
                label = "目标语言代码",
                hint = "translation.engine.config.target — 目标语言代码（默认 zh_cn）",
                value = editor.apiTarget,
                onValueChange = { value -> editor.updateApiConfig { it.copy(target = value) } },
            )
        }
        ConfigIntField(
            label = "并发度",
            hint = "translation.concurrency — 并发翻译分块（注意：AI 引擎下并发会让术语失效）",
            value = editor.concurrency,
            onValueChange = { value -> editor.updateTranslation { it.copy(concurrency = value) } },
        )
        ConfigSwitchRow(
            label = "按提取类型并发",
            hint = "translation.concurrent_by_kind — 按提取类型并发翻译（注意：AI 引擎下并发会让术语失效）",
            checked = editor.concurrentByKind,
            onCheckedChange = { value -> editor.updateTranslation { it.copy(concurrentByKind = value) } },
        )
    }
}

@Composable
private fun ProjectPatchGroup(editor: ProjectConfigEditor) {
    ConfigGroup("补丁 patch", Icons.Outlined.Difference) {
        ConfigNullableTextField(
            label = "补丁名称",
            hint = "patch.name — 生成的补丁名称（默认与项目名相同）",
            value = editor.patchName,
            onValueChange = { value -> editor.updatePatch { it.copy(name = value) } },
            placeholder = "留空使用项目名",
        )
        ConfigButtonGroupRow(
            label = "补丁类型",
            hint = "patch.kind — 补丁类型：立即求值在生成补丁时计算替换组，应用时求值则留到应用补丁时",
            entries = PathKind.entries,
            selected = editor.patchKind,
            entryLabel = { it.label },
            onSelected = { value -> editor.updatePatch { it.copy(kind = value) } },
        )
    }
}
