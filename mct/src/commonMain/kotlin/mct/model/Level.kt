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
    @SerialName("Data") val data: LevelData,
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
)
