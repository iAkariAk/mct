package mct.map

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.FSHolder
import mct.fs
import mct.model.DyeColor
import mct.model.text.TextComponent
import mct.serializer.NbtGzip
import mct.util.square
import mct.util.unreachable
import net.benwoodworth.knbt.decodeFromSource
import net.benwoodworth.knbt.encodeToSink
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import kotlin.jvm.JvmInline

// https://zh.minecraft.wiki/w/%E5%9C%B0%E5%9B%BE%E5%AD%98%E5%82%A8%E6%A0%BC%E5%BC%8F#%E5%AD%98%E5%82%A8%E8%A1%8C%E4%B8%BA
@Serializable
@SerialName("")
data class MapFile(
    @SerialName("data")
    val data: MapData,

    @SerialName("DataVersion")
    val dataVersion: Int = 1343
) {
    companion object {
        const val MAP_SIZE = 128

        context(_: FSHolder)
        fun decodeFromFile(path: Path) = fs.read(path, ::decodeFromSource)
        fun decodeFromFile(fs: FileSystem, path: Path) = fs.read(path, ::decodeFromSource)
        fun decodeFromSource(source: BufferedSource): MapFile = NbtGzip.decodeFromSource(source)
    }

    context(_: FSHolder)
    fun encodeToFile(path: Path) = fs.write(path, false, ::encodeToSink)
    fun encodeToFile(fs: FileSystem, path: Path) = fs.write(path, false, ::encodeToSink)
    fun encodeToSink(sink: BufferedSink) = NbtGzip.encodeToSink(this, sink)
}

@Serializable
data class MapData(
    @SerialName("banners")
    val banners: List<Banner> = emptyList(),

    @SerialName("colors")
    val colors: MapColors,

    @SerialName("dimension")
    val dimension: String,

    @SerialName("frames")
    val frames: List<Frame> = emptyList(),

    @SerialName("locked")
    val locked: Boolean,

    @SerialName("scale")
    val scale: Byte,

    @SerialName("trackingPosition")
    val trackingPosition: Boolean = true,

    @SerialName("unlimitedTracking")
    val unlimitedTracking: Boolean,

    @SerialName("xCenter")
    val xCenter: Int,

    @SerialName("zCenter")
    val zCenter: Int
)

@Suppress("ArrayInDataClass")
@Serializable
data class Banner(
    @SerialName("color")
    val color: DyeColor = white,

    @SerialName("name")
    val name: TextComponent<*>? = null,

    @SerialName("pos")
    val pos: IntArray
)

@Suppress("ArrayInDataClass")
@Serializable
data class Frame(
    @SerialName("entity_id")
    val entityId: Int,

    @SerialName("pos")
    val pos: IntArray,

    @SerialName("rotation")
    val rotation: Int
)

internal val MAP_BASE_COLORS = intArrayOf(
    0x000000, // 0 NONE
    0x7FB238, // 1 GRASS
    0xF7E9A3, // 2 SAND
    0xC7C7C7, // 3 WOOL
    0xFF0000, // 4 FIRE
    0xA0A0FF, // 5 ICE
    0xA7A7A7, // 6 METAL
    0x007C00, // 7 PLANT
    0xFFFFFF, // 8 SNOW
    0xA4A8B8, // 9 CLAY
    0x976D4D, // 10 DIRT
    0x707070, // 11 STONE
    0x4040FF, // 12 WATER
    0x8F7748, // 13 WOOD
    0xFFFCF5, // 14 QUARTZ
    0xD87F33, // 15 COLOR_ORANGE
    0xB24CD8, // 16 COLOR_MAGENTA
    0x6699D8, // 17 COLOR_LIGHT_BLUE
    0xE5E533, // 18 COLOR_YELLOW
    0x7FCC19, // 19 COLOR_LIGHT_GREEN
    0xF27FA5, // 20 COLOR_PINK
    0x4C4C4C, // 21 COLOR_GRAY
    0x999999, // 22 COLOR_LIGHT_GRAY
    0x4C7F99, // 23 COLOR_CYAN
    0x7F3FB2, // 24 COLOR_PURPLE
    0x334CB2, // 25 COLOR_BLUE
    0x664C33, // 26 COLOR_BROWN
    0x667F33, // 27 COLOR_GREEN
    0x993333, // 28 COLOR_RED
    0x191919, // 29 COLOR_BLACK
    0xFAEE4D, // 30 GOLD
    0x5CDBD5, // 31 DIAMOND
    0x4A80FF, // 32 LAPIS
    0x00D93A, // 33 EMERALD
    0x815631, // 34 PODZOL
    0x700200, // 35 NETHER
    0xD1B1A1, // 36 TERRACOTTA_WHITE
    0x9F5224, // 37 TERRACOTTA_ORANGE
    0x95576C, // 38 TERRACOTTA_MAGENTA
    0x706C8A, // 39 TERRACOTTA_LIGHT_BLUE
    0xBA8524, // 40 TERRACOTTA_YELLOW
    0x677535, // 41 TERRACOTTA_LIGHT_GREEN
    0xA04D4E, // 42 TERRACOTTA_PINK
    0x392923, // 43 TERRACOTTA_GRAY
    0x876B62, // 44 TERRACOTTA_LIGHT_GRAY
    0x575C5C, // 45 TERRACOTTA_CYAN
    0x7A4958, // 46 TERRACOTTA_PURPLE
    0x4C3E5C, // 47 TERRACOTTA_BLUE
    0x4C3223, // 48 TERRACOTTA_BROWN
    0x4C522A, // 49 TERRACOTTA_GREEN
    0x8E3C2E, // 50 TERRACOTTA_RED
    0x251610, // 51 TERRACOTTA_BLACK
    0xBD3031, // 52 CRIMSON_NYLIUM
    0x943F61, // 53 CRIMSON_STEM
    0x5C191D, // 54 CRIMSON_HYPHAE
    0x167E86, // 55 WARPED_NYLIUM
    0x3A8E8C, // 56 WARPED_STEM
    0x562C3E, // 57 WARPED_HYPHAE
    0x14B485, // 58 WARPED_WART_BLOCK
    0x646464, // 59 DEEPSLATE
    0xD8AF93, // 60 RAW_IRON
    0x7FA796, // 61 GLOW_LICHEN
)

private val MAP_BASE_COLORS_SPACE = IntArray(MAP_BASE_COLORS.size * 4) {
    val modifier = it / MAP_BASE_COLORS.size
    val baseColorRGB = MAP_BASE_COLORS[it % MAP_BASE_COLORS.size]
    applyModifier(baseColorRGB, modifier)
}

@Serializable
@JvmInline
value class MapColors(
    val raw: ByteArray
) {
    companion object {
        const val SIZE = 128 * 128

        fun fromRGBArray(array: IntArray): MapColors {
            check(array.size == SIZE)
            val raw = ByteArray(SIZE) {
                val rgb = array[it] and 0x00FFFFFF
                getBaseColorApproximately(rgb)
            }
            return MapColors(raw)
        }
    }

    internal inline fun getRGB(index: Int): Int = raw[index].getMapColorRGB()

    fun toRGBArray(padStart: Byte = 0): IntArray = IntArray(SIZE) {
        val rgb = getRGB(it)
        if (padStart == 0.toByte()) rgb else (padStart.toInt() shl 24) or rgb
    }
}

private fun getBaseColorApproximately(rgb: Int): Byte {
    val r1 = (rgb shr 16) and 0xFF
    val g1 = (rgb shr 8) and 0xFF
    val b1 = rgb and 0xFF

    var minDistanceIndex = 0
    var minDistance2 = Int.MAX_VALUE
    MAP_BASE_COLORS_SPACE.forEachIndexed { index, element ->
        val r2 = (element shr 16) and 0xFF
        val g2 = (element shr 8) and 0xFF
        val b2 = element and 0xFF

        val distance2 = square(r1 - r2) + square(g1 - g2) + square(b1 - b2)
        if (distance2 < minDistance2) {
            minDistance2 = distance2
            minDistanceIndex = index
        }
    }
    val baseColor = minDistanceIndex % MAP_BASE_COLORS.size
    val modifier = minDistanceIndex / MAP_BASE_COLORS.size
    return ((baseColor shl 2) or modifier).toByte()
}

internal inline fun Byte.getMapColorRGB(): Int {
    val baseColor = (toInt() and 0xFF) ushr 2
    val modifier = toInt() and 0b00000011
    check(modifier < 4) { "modifier should in 0..3" }

    val baseColorRGB = MAP_BASE_COLORS[baseColor]
    return applyModifier(baseColorRGB, modifier)
}

private fun applyModifier(baseColorRGB: Int, modifier: Int): Int {
    var r = (baseColorRGB shr 16) and 0xFF
    var g = (baseColorRGB shr 8) and 0xFF
    var b = baseColorRGB and 0xFF

    val factor = when (modifier) {
        0 -> 180 // LOW
        1 -> 220 // NORMA
        2 -> return baseColorRGB // HIGHT
        3 -> 135 // LOWEST
        else -> unreachable
    }

    r = r * factor / 255
    g = g * factor / 255
    b = b * factor / 255
    return r shl 16 or (g shl 8) or b
}
