/**
 * Refer to https://zh.minecraft.wiki/w/%E5%8C%BA%E5%9F%9F%E6%96%87%E4%BB%B6%E6%A0%BC%E5%BC%8F#%E6%96%87%E4%BB%B6%E7%BB%93%E6%9E%84
 */
package mct.region.anvil.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.benwoodworth.knbt.NbtCompound

/**
 * 区域文件中的区块根标签
 */
@Serializable
@SerialName("")
data class TerrainChunkData(
    /** 区块 X 坐标 */
    @SerialName("xPos")
    val xPos: Int,

    /** 最低子区块 Y 坐标（不参与加载） */
    @SerialName("yPos")
    val yPos: Int? = null,

    /** 区块 Z 坐标 */
    @SerialName("zPos")
    val zPos: Int,
    /** 最后保存时间 */
    @SerialName("LastUpdate")
    val lastUpdate: Long,
    /** 区块生成状态 */
    @SerialName("Status")
    val status: String,

    /** 玩家在此区块停留总时间（游戏刻） */
    @SerialName("InhabitedTime")
    val inhabitedTime: Long,

    /** 1.18 高度扩展升级重生成数据 */
    @SerialName("below_zero_retrogen")
    val belowZeroRetrogen: BelowZeroRetrogen? = null,

    /** 新旧区块平滑过渡混合数据 */
    @SerialName("blending_data")
    val blendingData: BlendingData? = null,

    /** 区块内所有方块实体 */
    @SerialName("block_entities")
    val blockEntities: List<BlockEntity> = emptyList(),

    /** 方块计划刻 */
    @SerialName("block_ticks")
    val blockTicks: List<Tick> = emptyList(),

    /** 区块雕刻标记（生成完毕后删除） */
    @SerialName("carving_mask")
    val carvingMask: LongArray? = null,

    /** 初始实体（生成完毕后删除） */
    @SerialName("entities")
    val entities: List<Entity> = emptyList(),

    /** 流体计划刻 */
    @SerialName("fluid_ticks")
    val fluidTicks: List<Tick> = emptyList(),

    /** 区块高度图信息 */
    @SerialName("Heightmaps")
    val heightmaps: Heightmaps? = null,


    /** 是否完成光照计算 */
    @SerialName("isLightOn")
    val isLightOn: Boolean? = null,


    /** 后处理更新列表 */
    @SerialName("PostProcessing")
    val postProcessing: List<List<Short>> = emptyList(),

    /** 子区块数据 */
    @SerialName("sections")
    val sections: List<Section> = emptyList(),
    /** 是否强制保存 */
    @SerialName("shouldSave")
    val shouldSave: Boolean? = null,


    /** 结构数据 */
    @SerialName("structures")
    val structures: Structures? = null,

    /** 区块升级数据 */
    @SerialName("UpgradeData")
    val upgradeData: UpgradeData? = null,
) : ChunkData {
    companion object
}

/**
 * 1.18 高度扩展升级重生成数据
 */
@Serializable
data class BelowZeroRetrogen(
    /**
     * 缺失基岩标记
     * 256位（4个long），按(0,0)->(15,15)扫描
     */
    @SerialName("missing_bedrock")
    val missingBedrock: LongArray? = null,

    /**
     * 重新生成方块的目标状态
     * 不包含 "empty"
     */
    @SerialName("target_status")
    val targetStatus: String
) {
    companion object
}


/**
 * 新旧区块混合数据
 */
@Serializable
data class BlendingData(

    /**
     * 高度值列表（16个）
     * 若为空则运行时重新生成
     */
    @SerialName("heights")
    val heights: List<Double> = emptyList(),

    /** 最高子区块 Y 坐标 */
    @SerialName("max_section")
    val maxSection: Int,

    /** 最低子区块 Y 坐标 */
    @SerialName("min_section")
    val minSection: Int
) {
    companion object
}


/**
 * 区块高度图数据
 */
@Serializable
data class Heightmaps(

    @SerialName("MOTION_BLOCKING")
    val motionBlocking: LongArray? = null,

    @SerialName("MOTION_BLOCKING_NO_LEAVES")
    val motionBlockingNoLeaves: LongArray? = null,

    @SerialName("OCEAN_FLOOR")
    val oceanFloor: LongArray? = null,

    @SerialName("OCEAN_FLOOR_WG")
    val oceanFloorWg: LongArray? = null,

    @SerialName("WORLD_SURFACE")
    val worldSurface: LongArray? = null,

    @SerialName("WORLD_SURFACE_WG")
    val worldSurfaceWg: LongArray? = null
) {
    companion object
}


/**
 * 子区块数据
 */
@Serializable
data class Section(

    /** 生物群系调色板 */
    @SerialName("biome")
    val biome: PaletteContainer<String>? = null,

    /** 方块状态调色板 */
    @SerialName("block_states")
    val blockStates: PaletteContainer<BlockState>? = null,

    /** 方块光照 */
    @SerialName("BlockLight")
    val blockLight: ByteArray? = null,

    /** 天空光照 */
    @SerialName("SkyLight")
    val skyLight: ByteArray? = null,

    /** 子区块 Y 坐标 */
    @SerialName("Y")
    val y: Byte
) {
    companion object
}


/**
 * 通用调色板容器
 */
@Serializable
data class PaletteContainer<T>(

    /** 位压缩数据 */
    @SerialName("pointer")
    val data: LongArray? = null,

    /** 调色板列表 */
    @SerialName("palette")
    val palette: List<T>? = null
) {
    companion object
}


/**
 * 方块状态
 */
@Serializable
data class BlockState(

    @SerialName("Name")
    val name: String,

    @SerialName("Properties")
    val properties: Map<String, String>? = null
) {
    companion object
}


/**
 * 区块结构数据
 */
@Serializable
data class Structures(

    /**
     * key = 结构命名空间ID
     * value = 区块坐标 long-array
     */
    @SerialName("References")
    val references: Map<String, LongArray> = emptyMap(),

    /**
     * 尚未生成完毕的结构
     * key = 结构命名空间ID
     */
    @SerialName("starts")
    val starts: Map<String, StructureStart> = emptyMap()
) {
    companion object
}


/**
 * 单个结构开始数据
 */
@Serializable
data class StructureStart(

    @SerialName("Children")
    val children: List<StructurePiece> = emptyList(),

    @SerialName("ChunkX")
    val chunkX: Int? = null,

    @SerialName("ChunkZ")
    val chunkZ: Int? = null,

    @SerialName("id")
    val id: String,

    @SerialName("references")
    val references: Int? = null
) {
    companion object
}


/**
 * 区块升级数据
 */
@Serializable
data class UpgradeData(
    /**
     * key = 子区块序号
     * value = YZX编码位置数组
     */
    @SerialName("Indices")
    val indices: Map<String, IntArray>? = null,

    @SerialName("neighbor_block_ticks")
    val neighborBlockTicks: List<Tick> = emptyList(),

    @SerialName("neighbor_fluid_ticks")
    val neighborFluidTicks: List<Tick> = emptyList(),

    /**
     * 更新方向位标记
     * 位顺序：北、东北、东、东南、南、西南、西、西北
     */
    @SerialName("Sides")
    val sides: Byte
) {
    companion object
}

/**
 * 结构片段
 */
@Serializable
data class StructurePiece(
    @SerialName("id")
    val id: String,
    @SerialName("pointer")
    val data: NbtCompound? = null
) {
    companion object
}


/**
 * 计划刻数据
 */
@Serializable
data class Tick(
    @SerialName("i")
    val blockId: String? = null,

    @SerialName("x")
    val x: Int? = null,

    @SerialName("y")
    val y: Int? = null,

    @SerialName("z")
    val z: Int? = null,

    @SerialName("t")
    val delay: Int? = null,

    @SerialName("p")
    val priority: Int? = null
) {
    companion object
}

