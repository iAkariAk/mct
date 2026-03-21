package mct.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.benwoodworth.knbt.NbtCompound

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
    /** 存档区块文件的版本 (如 Anvil 为 19133) */
    @SerialName("version") val version: Int = 19133,
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

    // --- 出生点数据 (根据数据模型，这些字段在 Data 下平铺) ---
    /** 世界出生点 X 坐标 */
    @SerialName("SpawnX") val spawnX: Int,
    /** 世界出生点 Y 坐标 */
    @SerialName("SpawnY") val spawnY: Int,
    /** 世界出生点 Z 坐标 */
    @SerialName("SpawnZ") val spawnZ: Int,
    /** 世界出生点看向的偏航角 */
    @SerialName("SpawnAngle") val spawnAngle: Float,

    // --- 时间与天气 ---
    /** 存档的游戏刻总时间 */
    @SerialName("Time") val time: Long,
    /** 存档的游戏天数时间 (DayTime) */
    @SerialName("DayTime") val dayTime: Long,
    /** 是否正在降雨 */
    @SerialName("raining") val raining: Boolean,
    /** 距离天气变化的游戏刻时间 */
    @SerialName("rainTime") val rainTime: Int,
    /** 是否正处于雷暴 */
    @SerialName("thundering") val thundering: Boolean,
    /** 距离雷暴变化的游戏刻时间 */
    @SerialName("thunderTime") val thunderTime: Int,
    /** 晴天剩余时间 */
    @SerialName("clearWeatherTime") val clearWeatherTime: Int,

    // --- 世界边界 (World Border) ---
    @SerialName("BorderSize") val borderSize: Double,
    @SerialName("BorderCenterX") val borderCenterX: Double,
    @SerialName("BorderCenterZ") val borderCenterZ: Double,
    @SerialName("BorderDamagePerBlock") val borderDamagePerBlock: Double,
    @SerialName("BorderSafeZone") val borderSafeZone: Double,
    @SerialName("BorderWarningBlocks") val borderWarningBlocks: Double,
    @SerialName("BorderWarningTime") val borderWarningTime: Double,
    @SerialName("BorderSizeLerpTarget") val borderSizeLerpTarget: Double,
    @SerialName("BorderSizeLerpTime") val borderSizeLerpTime: Long,

    // --- 复杂子结构 ---
    /** 世界生成设置 (种子、维度等) */
    @SerialName("WorldGenSettings") val worldGenSettings: NbtCompound,
    /** 数据包启用/禁用状态 */
    @SerialName("DataPacks") val dataPacks: NbtCompound,
    /** 末影龙战斗状态数据 */
    @SerialName("DragonFight") val dragonFight: NbtCompound? = null,
    /** 自定义 Boss 栏 */
    @SerialName("CustomBossEvents") val customBossEvents: Map<String, NbtCompound> = emptyMap(),
    /** 计划任务事件 */
    @SerialName("ScheduledEvents") val scheduledEvents: List<NbtCompound> = emptyList(),
    /** 单人游戏玩家数据 */
    @SerialName("Player") val player: NbtCompound? = null,
    /** 打开过此存档的服务端标识 */
    @SerialName("ServerBrands") val serverBrands: List<String> = emptyList(),

    // --- 商人数据 ---
    /** 流浪商人生成概率 */
    @SerialName("WanderingTraderSpawnChance") val wanderingTraderSpawnChance: Int,
    /** 距下次尝试生成流浪商人的时间 */
    @SerialName("WanderingTraderSpawnDelay") val wanderingTraderSpawnDelay: Int,
    /** 上次生成的流浪商人 UUID (Int Array) */
    @SerialName("WanderingTraderId") val wanderingTraderId: IntArray? = null
)
