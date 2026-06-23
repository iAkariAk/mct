package mct.util.io

import okio.FileSystem
import okio.Path
import okio.openZip

actual suspend fun FileSystem.openZipReadOnly(path: Path): FileSystem =
    openZip(path)