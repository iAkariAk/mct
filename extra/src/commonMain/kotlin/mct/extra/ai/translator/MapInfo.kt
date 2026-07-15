package mct.extra.ai.translator

import kotlinx.serialization.Serializable

@Serializable
data class MapInfo(
    val name: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList()
) {
    companion object {
        val None = MapInfo()
    }
}
