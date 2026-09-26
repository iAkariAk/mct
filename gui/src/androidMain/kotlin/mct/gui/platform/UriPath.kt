package mct.gui.platform

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath

/**
 * The real filesystem path behind a file the user picked.
 *
 * Android's pickers hand back a `content://` URI, which every service in this app would treat as a
 * literal path. With all-files access granted, the document id that URI carries is enough to
 * reconstruct the `/storage/…` path the document actually lives at, which is what the CLI-backed
 * services need.
 *
 * Returns `null` for authorities whose documents have no filesystem path (MediaStore's synthetic
 * entries, a cloud provider's tree): the caller then falls back to the raw URI and the run reports
 * the path as missing instead of writing somewhere unexpected.
 */
fun PlatformFile.uriToRealPath(): String? {
    val raw = absolutePath()
    if (!raw.startsWith("content://")) return raw
    val uri = Uri.parse(raw)

    val documentId = runCatching {
        if (uri.pathSegments.firstOrNull() == "tree") DocumentsContract.getTreeDocumentId(uri)
        else DocumentsContract.getDocumentId(uri)
    }.getOrNull() ?: return null

    return when (uri.authority) {
        // The file manager's own provider: `primary:Dir/file` or `1234-5678:Dir/file`.
        "com.android.externalstorage.documents" -> {
            val volume = documentId.substringBefore(':', "")
            val relative = documentId.substringAfter(':', "")
            val root = when {
                volume.isEmpty() || volume.equals("primary", ignoreCase = true) ->
                    Environment.getExternalStorageDirectory().absolutePath
                else -> "/storage/$volume"
            }
            if (relative.isEmpty()) root else "$root/$relative"
        }

        // Downloads come in three shapes: `raw:/storage/...` for a real file, an opaque numeric id,
        // and `msf:<id>` (MediaStore-backed), so the last two need one query for `_data`. This
        // provider's own uri is what `_id` is matched against.
        "com.android.providers.downloads.documents" -> {
            val raw = documentId.removePrefix("raw:").takeIf { it.startsWith("/") }
            if (raw != null) {
                raw
            } else {
                runCatching {
                    val id = documentId.removePrefix("msf:")
                    appContext.contentResolver
                        .query(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            arrayOf("_data"),
                            "_id=?",
                            arrayOf(id),
                            null,
                        )
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }.getOrNull()
            }
        }

        // Photos/videos/audio: the document id is `<kind>:<media id>`, so the path is one query away.
        "com.android.providers.media.documents" -> runCatching {
            val parts = documentId.split(':', limit = 2)
            val collection = when (parts[0]) {
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
            appContext.contentResolver
                .query(collection, arrayOf("_data"), "_id=?", arrayOf(parts[1]), null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

        else -> null
    }
}
