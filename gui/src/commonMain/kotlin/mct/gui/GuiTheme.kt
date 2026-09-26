package mct.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import mct.gui.model.GuiSettings
import mct.gui.util.ThemeState

/**
 * The application theme: the persisted dynamic colour scheme when [GuiSettings.isDynamicThemeEnabled],
 * the plain light/dark scheme otherwise, and the background surface the app is clipped into.
 *
 * [contentShape] is the corner radius of that surface. Desktop passes a square shape while its
 * window is maximized, because the radius would only carve transparent notches out of the desktop.
 */
@Composable
fun GuiTheme(
    contentShape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(isDark, GuiSettings.seedColorArgb) {
        ThemeState.restoreFromSettings(isDark)
    }

    val dynamicScheme = if (GuiSettings.isDynamicThemeEnabled) ThemeState.colorScheme else null
    val colorScheme = dynamicScheme ?: if (isDark) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().clip(contentShape),
            color = MaterialTheme.colorScheme.background
        ) { content() }
    }
}
