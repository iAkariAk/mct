package mct.extra.ai.translator

import kotlinx.serialization.Serializable

@Serializable
data class MapInfo(
    val name: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList()
) {
    fun render() = if (name == null && description == null) null
    else buildString {
        appendLine("## 地图信息")
        name?.let { appendLine("- 地图名: $it") }
        description?.let { appendLine("- 地图简介: $it") }
        authors.takeIf { it.isNotEmpty() }?.let {
            append("- 地图作者: ")
            authors.joinTo(this, ", ")
        }
    }

    companion object {
        val None = MapInfo()
    }
}