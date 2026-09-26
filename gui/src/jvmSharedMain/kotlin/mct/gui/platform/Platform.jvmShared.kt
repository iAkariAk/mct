package mct.gui.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

/**
 * The platform seams both JVM targets share. `desktopMain` and `androidMain` each add the handful
 * of declarations whose implementation genuinely differs (see `Platform.desktop.kt` /
 * `Platform.android.kt`); everything here is byte-for-byte the same on both, and Android runs the
 * same `java.*` APIs the desktop does.
 */

actual val platformFileSystem: FileSystem get() = FileSystem.SYSTEM
actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

/**
 * The language-file download client: JSON decoding, matching timeouts, and a bounded retry so a
 * flaky CDN response does not fail the whole download. The CIOfollows the policy the download
 * already had when its call site built the client inline.
 */
actual fun createDownloadClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
    install(HttpRequestRetry) {
        maxRetries = 3
        exponentialDelay()
    }
}

/**
 * Copy one entry out of a zip.
 *
 * `okio.openZip` lives in okio's `zlibMain`, not in its common metadata, so this cannot be written
 * in commonMain even though both JVM targets have it.
 */
actual fun copyZipEntry(zipPath: Path, entryName: String, target: Path) {
    platformFileSystem.openZip(zipPath).use { zip ->
        zip.source(entryName.toPath()).buffer().use { source ->
            platformFileSystem.sink(target).use(source::readAll)
        }
    }
}
