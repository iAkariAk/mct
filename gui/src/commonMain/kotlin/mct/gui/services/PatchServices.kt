package mct.gui.services

import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mct.Env
import mct.MCTError
import mct.MCTWorkspace
import mct.gui.model.*
import mct.kit.TranslationMapping
import mct.model.patch.Patch
import mct.patch.PatchResult
import mct.patch.applyPatch
import mct.patch.createPatch
import mct.serializer.MCTJson
import mct.util.io.readCbor
import mct.util.io.readJson
import mct.util.io.writeCbor
import mct.util.io.writeJson
import okio.Path.Companion.toPath

/**
 * 用存档当前内容与翻译映射创建 MCT 补丁文件。
 *
 * 立即求值会在此刻生成全部替换组，延迟求值则把规则与映射写进补丁、在应用时再现。
 */
context(env: Env)
suspend fun createPatchFile(
    input: String,
    mappingPath: String,
    output: String,
    kind: PatchKind,
    format: PatchFormat,
    validation: Boolean,
    patterns: PatternState,
) = withContext(Dispatchers.IO) {
    env.logger.info { "正在打开存档: $input" }
    either<MCTError, Unit> {
        val workspace = MCTWorkspace(input.toPath(), env)
        val mapping = env.fs.read(mappingPath.toPath()) { readUtf8() }
            .let { MCTJson.decodeFromString<TranslationMapping>(it) }
        env.logger.info { "已加载 ${mapping.size} 条映射: $mappingPath" }

        env.logger.info { "正在创建补丁（${kind.label} / ${format.label}）..." }
        val patch = workspace.createPatch(composePattern(patterns), mapping, kind.value, validation)

        val target = output.toPath()
        when (format) {
            PatchFormat.Json -> target.writeJson(patch, pretty = GuiSettings.prettyOutput)
            PatchFormat.Cbor -> target.writeCbor(patch)
        }
        env.logger.info { "补丁已写入: $output" }
        env.logger.info { "补丁创建完成。" }
    }.onLeft { env.logger.error { it.message } }
}

/**
 * 把补丁应用到存档，并按 [strategy] 决定校验不一致时的处理方式。
 */
context(env: Env)
suspend fun applyPatchFile(
    input: String,
    patchPath: String,
    format: PatchFormat,
    strategy: PatchStrategy,
) = withContext(Dispatchers.IO) {
    env.logger.info { "正在打开存档: $input" }
    either<MCTError, Unit> {
        val workspace = MCTWorkspace(input.toPath(), env)
        val patch = when (format) {
            PatchFormat.Json -> patchPath.toPath().readJson<Patch>()
            PatchFormat.Cbor -> patchPath.toPath().readCbor<Patch>()
        }
        env.logger.info { "正在应用补丁: $patchPath（${strategy.label}）" }

        when (val result = workspace.applyPatch(patch, strategy.value)) {
            is PatchResult.Success -> {
                if (result.warning.isEmpty()) {
                    env.logger.info { "补丁应用成功。" }
                } else {
                    env.logger.warning { "补丁已应用，但以下 ${result.warning.size} 处内容与创建时不同：" }
                    result.warning.forEach { (path, reason) ->
                        env.logger.warning { "$path: 期望 ${reason.expected}，实际 ${reason.actual}" }
                    }
                }
            }

            is PatchResult.ValidationFailure -> {
                env.logger.error { "补丁校验失败，共 ${result.unmatched.size} 处与创建时不同：" }
                result.unmatched.forEach { (path, reason) ->
                    env.logger.error { "$path: 期望 ${reason.expected}，实际 ${reason.actual}" }
                }
            }
        }
    }.onLeft { env.logger.error { it.message } }
}
