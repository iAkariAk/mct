package io.github.iakariak.mct

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mct.gui.platform.GuiAndroidApp

/**
 * The app's single activity. It does nothing but hand the shared application shell to Compose: the
 * top bar, the theme and the back handling all live in `:gui`'s `androidMain`, so this module has no
 * composable of its own and needs no Compose compiler configuration for them.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app draws its own chrome (title bar and navigation suite), so it fills the window and
        // insets that content itself rather than letting the system reserve strips for the bars.
        enableEdgeToEdge()
        setContent {
            AllFilesAccessGate()
            GuiAndroidApp()
        }
    }
}
