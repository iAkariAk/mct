package mct.region.anvil.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.benwoodworth.knbt.NbtCompound


@Serializable
data class BlockEntity(
    @SerialName("id")
    val id: String,

    @SerialName("x")
    val x: Int,

    @SerialName("y")
    val y: Int,

    @SerialName("z")
    val z: Int,

    /**
     * 世界生成阶段使用
     * true = 仅数据占位，尚未反序列化为真正的方块实体
     */
    @SerialName("keepPacked")
    val keepPacked: Boolean = false,

    /**
     * 不同方块实体的额外数据
     * 例如 Items, CustomName, Lock 等
     *
     * 这里使用 JsonElement 以支持动态扩展
     */
    @SerialName("pointer")
    val data: NbtCompound? = null,
) {
    companion object
}