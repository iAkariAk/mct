package mct.gui.platform

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable

/**
 * The gesture/navigation bar, which the app owns because the activity is edge to edge.
 *
 * Only the bottom: the status bar's inset is the top bar's business, and it applies it itself.
 */
@Composable
actual fun shellBottomInsetPadding(): PaddingValues = WindowInsets.navigationBars.asPaddingValues()
