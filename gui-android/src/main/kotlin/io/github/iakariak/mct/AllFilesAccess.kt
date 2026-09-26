package io.github.iakariak.mct

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Asks for all-files access on the first frame.
 *
 * The app works on real paths anywhere the user picks, so it needs access to the whole shared
 * storage rather than per-file grants: the picked `content://` URI is converted back to its
 * `/storage/…` path, and every service then opens that path directly. On API 30+ that is
 * `MANAGE_EXTERNAL_STORAGE`, which has no runtime-permission dialog and must be granted from the
 * settings screen this opens; below 30 the old read/write pair is the equivalent.
 *
 * Runs once per process: the settings screen is a separate task, so re-requesting on every
 * recomposition would bounce the user back to it.
 */
@Composable
fun AllFilesAccessGate() {
    val context = LocalContext.current
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* A denial surfaces as a missing path when a run starts; nothing to retry here. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val perApp = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                runCatching { context.startActivity(perApp) }.onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
            }
        } else {
            legacyLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            )
        }
    }
}
