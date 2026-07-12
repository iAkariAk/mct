package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.panic
import mct.cli.path
import mct.cli.util.downloadAndSha1
import mct.util.IO
import mct.util.io.readJson
import mct.util.io.writeJson
import mct.util.removePrefixOrNull
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

class OfficialLang : BaseCommand("official", "Download official lang files") {
    init {
        subcommands(DownloadLang(), CombineTermTable())
    }
}

private class DownloadLang : BaseCommand("download", "Download all lang files") {
    val mcVersion by option("--mcversion", "-mv").default("latest")
    val output by option("--output", "-o").path().required()
    val concurrency by option("--concurrency", "-C").int().default(20)

    context(_: Raise<MCTError>)
    override suspend fun App() = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }


        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }

        install(HttpRequestRetry) {
            maxRetries = 5

            exponentialDelay()
        }
    }.use { client ->
        coroutineScope {
            val dispatcher = Dispatchers.IO.limitedParallelism(concurrency)

            val totalVersionManifest =
                client.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").body<JsonObject>()
            val id = if (mcVersion == "latest") {
                val latest = totalVersionManifest["latest"]!!.jsonObject
                (latest["snapshot"] ?: latest["release"]!!).jsonPrimitive.content
            } else panic("Version $mcVersion unfounded")
            val versions = totalVersionManifest["versions"]!!.jsonArray
            val version = versions.find { it.jsonObject["id"]!!.jsonPrimitive.content == id }?.jsonObject
                ?: panic("Version $mcVersion unfound")
            logger.info { "Got version: $id" }
            val versionDir = output / id
            val cacheDir = versionDir / "caches"
            fs.createDirectories(cacheDir)

            val versionUrl = version["url"]!!.jsonPrimitive.content
            logger.debug { "Get version manifest from $versionUrl" }
            val versionManifest = client.get(versionUrl).body<JsonObject>()
            val clientJar = versionManifest["downloads"]!!.jsonObject["client"]!!.jsonObject
            val clientJarSha1 = clientJar["sha1"]!!.jsonPrimitive.content
            val clientJarUrl = clientJar["url"]!!.jsonPrimitive.content
            val clientJarFile = cacheDir / "client.jar"

            fun langFile(code: String) = versionDir / "$code.json"

            launch(dispatcher) {
                client.downloadAndValidate(clientJarUrl, clientJarFile, clientJarSha1)
                logger.info { "Downloaded client.jar with sha1: $clientJarSha1" }

                fs.openZip(clientJarFile).use { zfs ->
                    zfs.source("/assets/minecraft/lang/en_us.json".toPath()).buffer().use { source ->
                        fs.sink(langFile("en_us")).use { sink ->
                            source.readAll(sink)
                        }
                    }
                }
                logger.info { "Got en_us.json from client.jar" }
            }

            val assetIndexUrl = versionManifest["assetIndex"]!!.jsonObject["url"]!!.jsonPrimitive.content
            val assetIndex = client.get(assetIndexUrl).body<JsonObject>()
            val assets = assetIndex["objects"]!!.jsonObject
            assets.asSequence()
                .mapNotNull { (fileName, asset) ->
                    fileName.removePrefixOrNull("minecraft/lang/")?.let { langFileName ->
                        langFileName.toString() to asset.jsonObject
                    }
                }.forEach { (fileName, asset) ->
                    val assetSha1 = asset.jsonObject["hash"]!!.jsonPrimitive.content
                    val assetUrl = "https://resources.download.minecraft.net/${assetSha1.take(2)}/$assetSha1"
                    launch(dispatcher) {
                        client.downloadAndValidate(assetUrl, versionDir / fileName, assetSha1)
                        logger.info { "Downloaded $fileName." }
                    }
                }
        }

        logger.info { "Done" }
    }

    private suspend fun HttpClient.downloadAndValidate(url: String, target: Path, sha1: String) {
        val actual = downloadAndSha1(url, target)
        check(actual == sha1) {
            "Expected sha1 was $sha1, but got $actual"
        }
    }
}

private class CombineTermTable : BaseCommand("combine", "Combine two lang file to MCT term table") {
    val from by option("--from", "-f").path().required()
    val to by option("--to", "-t").path().required()
    val output by option("--output", "-o").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val lang1 = from.readJson<JsonObject>()
        val lang2 = to.readJson<JsonObject>()
        val terms = lang2.mapKeys { (key, _) -> lang1[key]?.jsonPrimitive?.content ?: key }.let(::JsonObject)
        output.writeJson(terms)

    }
}



