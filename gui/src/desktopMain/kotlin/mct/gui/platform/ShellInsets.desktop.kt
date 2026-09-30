package mct.gui.platform

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/** The window manager owns the screen edges on the desktop; nothing to leave for it here. */
@Composable
actual fun shellBottomInsetPadding(): PaddingValues = PaddingValues()
