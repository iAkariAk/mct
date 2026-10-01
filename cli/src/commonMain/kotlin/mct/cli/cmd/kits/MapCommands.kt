package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import io.github.yuroyami.kiteimage.KiteBitmap
import io.github.yuroyami.kiteimage.KiteImage
import mct.MCTError
import mct.cli.*
import mct.map.MapColors
import mct.map.MapFile
import mct.map.MapFile.Companion.MAP_SIZE
import mct.util.io.extension
import mct.util.io.readBytes
import mct.util.io.stem
import mct.util.io.writeBytes
import okio.Path

class MapCommands : BaseCommand("map", "View or edit a map file") {
    init {
        subcommands(ViewCommand(), EditCommand())
    }
}

private enum class ImageFormat {
    PNG, BMP, JPEG, GIF;

    companion object {
        fun fromExt(ext: String): ImageFormat = when (ext) {
            "png" -> PNG
            "bmp" -> BMP
            "jpg", "jpeg" -> JPEG
            "gif" -> GIF
            else -> panic("Unknown image format $ext")
        }
    }

    fun encode(bitmap: KiteBitmap): ByteArray = when (this) {
        PNG -> KiteImage.encodePng(bitmap)
        BMP -> KiteImage.encodeBmp(bitmap)
        JPEG -> KiteImage.encodeJpeg(bitmap)
        GIF -> KiteImage.encodeGif(bitmap)
    }

    fun decode(data: ByteArray) = when (this) {
        PNG -> KiteImage.decode(data)
        BMP -> KiteImage.decode(data)
        JPEG -> KiteImage.decode(data)
        GIF -> KiteImage.decode(data)
    }
}


private class ViewCommand : RegexMultiInputCommand("view", "View a map file") {
    val format by option("--format", "-f").enum<ImageFormat> {
        it.name.lowercase()
    }.required()

    override fun Path.correspondToOutput(outputDir: Path): Path =
        outputDir / "$stem.${format.name.lowercase()}"


    override fun output(inputFile: Path, outputFile: Path) {
        val mapFile = MapFile.decodeFromFile(inputFile)
        val argb = mapFile.data.colors.toRGBArray(0xFF.toByte())
        val bitmap = KiteBitmap(MAP_SIZE, MAP_SIZE, argb)
        val bytes = format.encode(bitmap)
        outputFile.writeBytes(bytes)
    }
}

private class EditCommand : BaseCommand("edit", "Edit a map file by inputting image") {
    val input by option("--input", "-i", help = "The path to map file").path().required()
    val image by option("--image", "-m", help = "The path to map image").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val format = ImageFormat.fromExt(image.extension)
        val bitmap = format.decode(image.readBytes())
        var mapFile = MapFile.decodeFromFile(input)
        val argb = bitmap.argb
        enforce(bitmap.width == MAP_SIZE && bitmap.height == MAP_SIZE) {
            "The size of the image must be ${MAP_SIZE}x${MAP_SIZE}"
        }
        val colors = MapColors.fromRGBArray(argb)
        mapFile = mapFile.copy(data = mapFile.data.copy(colors = colors))
        mapFile.encodeToFile(input)
    }
}