/**
 * Refer to https://zh.minecraft.wiki/w/%E6%96%B9%E5%9D%97%E5%AE%9E%E4%BD%93%E6%95%B0%E6%8D%AE%E6%A0%BC%E5%BC%8F
 */

package mct.region.anvil.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("")
data class EntitiesChunkData(
    /**
     * 保存此实体数据时的游戏数据版本。
     * 若不存在则游戏视为 -1。
     */
    @SerialName("DataVersion")
    val dataVersion: Int? = null,

    /**
     * 区块坐标 [x, z]。
     * 必须与文件位置一致。
     */
    @SerialName("Position")
    val position: IntArray,

    /**
     * 本区块内的所有实体。
     */
    @SerialName("Entities")
    val entities: List<Entity>
) : ChunkData {
    companion object
}

@Serializable
data class Entity(
    /**
     * 实体ID，例如 "minecraft:zombie"
     */
    @SerialName("id")
    val id: String,

    /**
     * 实体UUID
     */
    @SerialName("UUID")
    val uuid: IntArray? = null,

    /**
     * 位置 [x, y, z]
     */
    @SerialName("Pos")
    val pos: DoubleArray? = null,

    /**
     * 运动向量 [x, y, z]
     */
    @SerialName("Motion")
    val motion: DoubleArray? = null,

    /**
     * 旋转 [yaw, pitch]
     */
    @SerialName("Rotation")
    val rotation: FloatArray? = null,

    /**
     * 自定义名称
     */
    @SerialName("CustomName")
    val customName: String? = null,

    /**
     * 是否静音
     */
    @SerialName("Silent")
    val silent: Boolean? = null,

    /**
     * 是否无敌
     */
    @SerialName("Invulnerable")
    val invulnerable: Boolean? = null,

    /**
     * 乘客
     *
     * 如果该实体是乘客，
     * 它不会单独存在于区块 Entities 列表中，
     * 而是嵌套在根实体内。
     */
    @SerialName("Passengers")
    val passengers: List<Entity>? = null
) {
    companion object
}
