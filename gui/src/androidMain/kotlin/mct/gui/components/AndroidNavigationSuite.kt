package mct.gui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveComponentOverrideApi
import androidx.compose.material3.adaptive.navigationsuite.LocalNavigationSuiteScaffoldOverride
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldOverride
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldOverrideScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Corner radius of the floating bottom bar. */
private val BarShape = RoundedCornerShape(24.dp)

/** Gap between the rounded bar and the window edges. */
private val BarMargin = 12.dp

private val NoInsets = WindowInsets(0, 0, 0, 0)

/**
 * Renders the navigation suite as a rounded, floating bar on phones instead of a full-bleed one.
 *
 * The scaffold has no shape parameter, so this goes through
 * [LocalNavigationSuiteScaffoldOverride] — the library's documented override point. The items still
 * come from the library's own [NavigationSuite], so the indicators, motion and colours stay
 * Material's; only the placement changes.
 *
 * A rail (a wide window) keeps the library's shape: it sits against a window edge, where a corner
 * radius would only cut a notch out of it.
 */
@OptIn(ExperimentalMaterial3AdaptiveComponentOverrideApi::class)
object RoundedNavigationSuiteOverride : NavigationSuiteScaffoldOverride {
    @Composable
    override fun NavigationSuiteScaffoldOverrideScope.NavigationSuiteScaffold() {
        val isBar = layoutType == NavigationSuiteType.ShortNavigationBarCompact ||
            layoutType == NavigationSuiteType.ShortNavigationBarMedium ||
            layoutType == NavigationSuiteType.NavigationBar

        Surface(modifier = modifier, color = containerColor, contentColor = contentColor) {
            NavigationSuiteScaffoldLayout(
                navigationSuite = {
                    NavigationSuite(
                        layoutType = layoutType,
                        colors = navigationSuiteColors,
                        // Padding before the clip keeps the bar's own bottom inset *inside* the
                        // rounded shape, so the surface still covers the gesture area.
                        modifier = if (isBar) Modifier.padding(BarMargin).clip(BarShape) else Modifier,
                        content = navigationSuiteItems,
                    )
                },
                navigationSuiteType = layoutType,
                state = state,
                content = {
                    // The same inset the default override consumes: the bar has already absorbed the
                    // bottom one, so the page above must not be padded for it again.
                    val consumed = if (state.currentValue == NavigationSuiteScaffoldValue.Visible ||
                        state.isAnimating
                    ) {
                        if (isBar) ShortNavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom) else NoInsets
                    } else {
                        NoInsets
                    }
                    Box(Modifier.consumeWindowInsets(consumed)) { content() }
                },
            )
        }
    }
}
