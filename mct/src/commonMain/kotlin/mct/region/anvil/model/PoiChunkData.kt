/**
 * Refer to https://zh.minecraft.wiki/w/%E5%85%B4%E8%B6%A3%E7%82%B9%E5%AD%98%E5%82%A8%E6%A0%BC%E5%BC%8F
 */

package mct.region.anvil.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 兴趣点存储文件是一种区块数据，
 * 以区域文件格式为载体。
 *
 * 存储于 <维度根目录>/poi 内。
 *
 * 主世界：
 * <存档根目录>/poi
 *
 * 下界：
 * <存档根目录>/DIM-1/poi
 *
 * 新版本：
 * <存档根目录>/dimensions/minecraft/<dimension>/poi
 */
@Serializable
@SerialName("")
data class PoiChunkData(
    /**
     * 保存此兴趣点存储文件的游戏的数据版本。
     *
     * 如果不存在则游戏认为此项为 1945（19w14b）。
     */
    @SerialName("DataVersion")
    val dataVersion: Int? = null,

    /**
     * 所有子区块的数据。
     *
     * 如果不存在则游戏认为所有子区块中不存在数据。
     */
    @SerialName("Sections")
    val sections: Map<String, PoiSection>? = null
) : ChunkData {
    companion object
}

/**
 * 子区块数据。
 *
 * key 为子区块垂直坐标。
 * 可以为负值。
 */
@Serializable
data class PoiSection(

    /**
     * 子区块内的所有兴趣点记录。
     *
     * 必需字段。
     */
    @SerialName("Records")
    val records: List<PoiRecord> = emptyList(),

    /**
     * 此兴趣点数据是否有效。
     *
     * 当区块加载时，
     * 如果此项为 false，
     * 游戏会强制刷新数据以有效化。
     *
     * 如果不存在游戏默认为 false。
     */
    @SerialName("Valid")
    val valid: Boolean? = null
) {
    companion object
}


/**
 * 一项兴趣点记录。
 */
@Serializable
@SerialName("")
data class PoiRecord(

    /**
     * 此兴趣点剩余的认领数。
     *
     * 值为 0：
     * - 无法被认领
     * - 或认领名额已满
     *
     * 如果不存在游戏默认为 0。
     */
    @SerialName("free_tickets")
    val freeTickets: Int? = null,

    /**
     * 兴趣点位置。
     *
     * int-array，长度为 3。
     * 分别为 X, Y, Z。
     */
    @SerialName("pos")
    val pos: IntArray,

    /**
     * 命名空间 ID。
     *
     * 兴趣点类型。
     */
    @SerialName("type")
    val type: String
) {
    companion object
}
