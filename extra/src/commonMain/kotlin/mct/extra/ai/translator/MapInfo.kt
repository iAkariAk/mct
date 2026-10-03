package mct.extra.ai.translator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("mct_info")
data class MapInfo(
    val name: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList()
) {
    companion object {
        val None = MapInfo()
    }
}
