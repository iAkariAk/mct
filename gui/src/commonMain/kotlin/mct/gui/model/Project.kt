package mct.gui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import mct.model.patch.PathKind

/** Working directory name the CLI creates a project in, i.e. the parent of a project root. */
@Serializable
@Immutable
data class ProjectHistoryEntry(
    val name: String,
    val directory: String,
    /** Epoch millis of the last open, used to order the overview list and to show "最近打开". */
    val lastOpenedAt: Long = 0L,
)

/** Persisted project history (`~/.mct/projects.json`). */
@Serializable
@Immutable
data class ProjectHistory(val entries: List<ProjectHistoryEntry> = emptyList())

/** Translation engine `mct project init --translation-engine` accepts (the CLI's only two choices). */
enum class ProjectTranslationEngine(val key: String, val label: String) {
    Ai("ai", "AI"),
    Api("api", "API"),
}

/**
 * A text table of a project: the two editable key→value files the CLI reads and writes, plus the
 * read-only pool of texts that have no translation yet.
 *
 * `ProjectController.editorOf` returns an editor only for the two tables, which is what the pages
 * use to decide whether editing is offered.
 */
enum class ProjectTextFile(
    val title: String,
    val description: String,
    val defaultPath: String,
    /** Hint under the page title; also tells how the file is produced. */
    val producer: String,
    /**
     * What a `null` value means here.
     *
     * In `mappings.json` a `null` is not "untranslated": it is an explicit instruction to keep the
     * original text, which is why such a text is missing from neither the mapping nor
     * `missing.json`. The pool does not have values at all, so its rows are simply still pending.
     */
    val emptyValueLabel: String,
) {
    Mappings(
        title = "现有映射",
        description = "源文本与目标文本的对应关系；译文留空表示保留原文",
        defaultPath = "mappings.json",
        producer = "mct project translate 写入；这里的修改会直接写回该文件",
        emptyValueLabel = "保留原文",
    ),
    Missing(
        title = "缺失的映射",
        description = "已提取、映射里还没有条目的文本",
        defaultPath = "missing.json",
        producer = "mct project update 写入：提取出但映射中还没有条目的文本",
        emptyValueLabel = "待翻译",
    ),
    Terms(
        title = "术语表",
        description = "专有名词的统一译名",
        defaultPath = "terms.json",
        producer = "mct project term 写入；翻译时作为既定译名使用",
        emptyValueLabel = "未填写",
    ),
}

/**
 * Which page the project function area shows. [Dashboard] is the function cards; the other entries
 * that carry a [textFile] render that file's list page.
 */
enum class ProjectSection(val textFile: ProjectTextFile? = null) {
    Dashboard(),
    Config(),
    Mappings(ProjectTextFile.Mappings),
    Missing(ProjectTextFile.Missing),
    Terms(ProjectTextFile.Terms),
}

/**
 * The project actions pinned under the function area, one per `mct project` subcommand the GUI
 * runs. [cancelLabel] is what the action collapses into while it is running.
 */
enum class ProjectAction(val label: String, val cancelLabel: String) {
    Update("更新", "取消更新"),
    Term("术语", "取消术语提取"),
    Translate("翻译", "取消翻译"),
    Build("构建", "取消构建"),
    Patch("补丁", "取消补丁"),
}

/** Contents of the "new project" dialog. Held in the controller so switching tabs does not lose it. */
@Immutable
data class ProjectInitForm(
    val directory: String = "",
    val name: String = "",
    val source: String = "",
    val engine: ProjectTranslationEngine = ProjectTranslationEngine.Ai,
)

/**
 * One row of the mapping views.
 *
 * `target == null` means the text has no mapping yet: that is what `missing.json` holds, so both
 * mapping views render the same list type.
 */
@Immutable
data class ProjectTextEntry(val source: String, val target: String?)

/**
 * A uniform view of one extraction-pattern category of `mct.toml`.
 *
 * The CLI stores these in three shapes (`PatternWithBuiltin<Set<String>>`, a bare `Set<String>` for
 * command regex and a `List<String>` for cext); the editor only ever needs the path list plus
 * whether the builtin set stays in play.
 */
@Immutable
data class ProjectPatternPaths(val paths: List<String> = emptyList(), val hasBuiltin: Boolean = true)

/**
 * One extraction-pattern category of `mct.toml`: its key in the file, a label, and the hint the
 * editor shows for it — the CLI's own comment on the key, in Chinese.
 */
enum class ProjectPatternSlot(
    val key: String,
    val label: String,
    val hint: String,
    val supportsBuiltin: Boolean = false,
) {
    Nbt(
        key = "nbt",
        label = "Region 方块实体",
        hint = "Region 数据指针规则 JSON 文件路径（提取方块实体、告示牌等处的文本）",
        supportsBuiltin = true,
    ),
    McJson(
        key = "mcjson",
        label = "MCJson 组件",
        hint = "MCJson 数据指针规则 JSON 文件路径",
        supportsBuiltin = true,
    ),
    Command(
        key = "command",
        label = "命令结构",
        hint = "命令提取规则 JSON 文件路径",
        supportsBuiltin = true,
    ),
    CommandData(
        key = "command_data",
        label = "命令 SNBT 数据",
        hint = "命令 SNBT 数据指针规则 JSON 文件路径（从命令参数中提取数据）",
        supportsBuiltin = true,
    ),
    CommandComponent(
        key = "command_component",
        label = "命令组件",
        hint = "命令组件规则 JSON 文件路径（从带组件的命令参数中提取数据）",
        supportsBuiltin = true,
    ),
    CommandRegex(
        key = "command_regex",
        label = "命令正则",
        hint = "命令正则规则 JSON 文件路径",
    ),
    Cext(
        key = "cext",
        label = "自定义提取器",
        hint = "自定义提取器（cext）规则 JSON 文件路径",
    ),
}

/** The two `patch.kind` values, labelled for the button group. */
val PathKind.label: String
    get() = when (this) {
        PathKind.Immediate -> "立即求值"
        PathKind.Deferred -> "补丁应用时求值"
    }
