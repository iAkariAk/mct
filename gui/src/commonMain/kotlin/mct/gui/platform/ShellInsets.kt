package mct.gui.platform

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/**
 * Padding the shell must leave at the bottom of the window for the platform's own chrome.
 *
 * Desktop draws its window chrome itself and the window manager owns the screen edges, so it is
 * zero. Android is edge to edge: `enableEdgeToEdge()` gives the app the whole window, and the
 * navigation area floats [mct.gui.components.NavigationArea] clear of the gesture bar with its own
 * margin, so the bottom inset has to be left to it here instead — inside the layout, where the
 * floating bar can be laid out above it.
 */
@Composable
expect fun shellBottomInsetPadding(): PaddingValues
