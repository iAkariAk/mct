package mct.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.benwoodworth.knbt.NbtCompound

@Suppress("ConstPropertyName")
object DataVersions {
    const val `26_1-snapshot-6` = 4774
}

/**
 * Minecraft 存档 Level.dat 根标签
 */
@Serializable
@SerialName("")
data class LevelRoot(
    /** 存档基础数据 */
    @SerialName("Data") val data: LevelData
)

@Serializable
data class LevelData(
    // --- 核心版本信息 ---
    /** 保存此存档基础数据存储文件的游戏的 数据版本 */
    @SerialName("DataVersion") val dataVersion: Int,
    /** 存档区块文件的版本 */
    @SerialName("version") val version: Int,
    /** 存储此存档时游戏的详细版本信息 */
    @SerialName("Version") val versionInfo: NbtCompound,

    // --- 基础存档状态 ---
    /** 存档的显示名称 */
    @SerialName("LevelName") val levelName: String,
    /** 上次保存此存档的 UNIX 时间戳 */
    @SerialName("LastPlayed") val lastPlayed: Long,
    /** 存档是否被正确初始化 */
    @SerialName("initialized") val initialized: Boolean,
    /** 存档是否被修改过的客户端或服务端保存过 */
    @SerialName("WasModded") val wasModded: Boolean,
    /** 存档是否启用命令 */
    @SerialName("allowCommands") val allowCommands: Boolean,

    // --- 游戏规则与模式 ---
    /** 存档的默认游戏模式 (0:生存, 1:创造, 2:冒险, 3:旁观) */
    @SerialName("GameType") val gameType: Int,
    /** 存档是否为极限模式 */
    @SerialName("hardcore") val hardcore: Boolean,
    /** 存档的游戏难度 (0-3) */
    @SerialName("Difficulty") val difficulty: Byte,
    /** 此存档难度是否被锁定 */
    @SerialName("DifficultyLocked") val difficultyLocked: Boolean,
    /** 存档的游戏规则列表 (注意：Key 为 GameRules 而非 game_rules) */
    @SerialName("GameRules") val gameRules: Map<String, String>,
)
