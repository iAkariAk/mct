package mct.model

import kotlinx.serialization.Serializable

@Suppress("EnumEntryName")
@Serializable
enum class DyeColor(
    val color: Int,
    val fireworkColor: Int,
    val sheepColor: Int,
) {
    white(
        0xF9FFFE,
        0xF0F0F0,
        0xFFFFFF
    ),
    orange(
        0xF9801D,
        0xEB8844,
        0xFF681F
    ),
    magenta(
        0xC74EBD,
        0xC354CD,
        0xFF00FF
    ),
    light_blue(
        0x3AB3DA,
        0x6689D3,
        0x9AC0C
    ),
    yellow(
        0xFED83D,
        0xDECF2A,
        0xFFFF0
    ),
    lime(
        0x80C71F,
        0x41CD34,
        0xBFFF0
    ),
    pink(
        0xF38BAA,
        0xD88198,
        0xFF69B
    ),
    gray(
        0x474F52,
        0x434343,
        0x80808
    ),
    light_gray(
        0x9D9D97,
        0xABABAB,
        0xD3D3D
    ),
    cyan(
        0x169C9C,
        0x287697,
        0x00FFF
    ),
    purple(
        0x8932B8,
        0x7B2FBE,
        0xA020F
    ),
    blue(
        0x3C44AA,
        0x253192,
        0x0000F
    ),
    brown(
        0x835432,
        0x51301A,
        0x8B451
    ),
    green(
        0x5E7C16,
        0x3B511A,
        0x00FF0
    ),
    red(
        0xB02E26,
        0xB3312C,
        0xFF000
    ),
    black(
        0x1D1D21,
        0x1E1B1B,
        0x00000
    ),

}