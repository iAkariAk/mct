package mct.region.anvil.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

sealed interface ChunkData {
    companion object
}

@Serializable
enum class ChunkDataKind(
    internal val gettingSerializer: SerializersModule.() -> KSerializer<*>
) {
    @SerialName("region")
    Terrain({ serializer<TerrainChunkData>() }),
    @SerialName("entities")
    Entities({ serializer<EntitiesChunkData>() }),
    @SerialName("poi")
    Poi({ serializer<PoiChunkData>() });

    inline fun isTypeOf(type: KType) = when (this) {
        Terrain -> type == typeOf<TerrainChunkData>()
        Entities -> type == typeOf<EntitiesChunkData>()
        Poi -> type == typeOf<PoiChunkData>()
    }

    inline fun <reified T : ChunkData> isTypeOf() = isTypeOf(typeOf<T>())


    companion object {
        inline fun <reified T : ChunkData> of() = when (typeOf<T>()) {
            typeOf<TerrainChunkData>() -> Terrain
            typeOf<EntitiesChunkData>() -> Entities
            typeOf<PoiChunkData>() -> Poi
            else -> error("Impossible to reach")
        }
    }
}