package mct.util.io

import okio.FileSystem
import okio.Path

actual suspend fun FileSystem.openZipReadOnly(path: Path): FileSystem = openZipReadOnlyCommon(path)