package mct.gui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import mct.gui.state.ProjectController

/** One project, keyed by its directory so two projects never share a destination. */
@Serializable
private data class ProjectKey(val directory: String) : NavKey

/** The overview destination; always the base of the stack. */
@Serializable
private data object ProjectListKey : NavKey

/**
 * The project tab: the overview, and the open project pushed over it.
 *
 * One pane at a time, always full width. The workspace is not a detail pane of the list — it is a
 * whole working surface with its own header, function area, action bar and section pages, so a
 * side-by-side layout would leave both halves too narrow to use. Opening a project covers the
 * overview instead, and the workspace header's back button returns to it; that is the same
 * interaction at every window size.
 *
 * The back stack is driven by [ProjectController.opened], the single source of truth the rest of the
 * workspace already reads, so the function cards, the section pages and the destinations can never
 * disagree about which project is open.
 */
@Composable
fun ProjectNavigationHost(
    controller: ProjectController,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val openedDirectory = controller.opened?.directory
    // The overview is the base of the stack and is never removed: `NavDisplay` requires a non-empty
    // stack. A project is pushed on top of it and popped when it is closed.
    val backStack = remember { mutableStateListOf<NavKey>(ProjectListKey) }

    LaunchedEffect(openedDirectory) {
        val key = openedDirectory?.let(::ProjectKey)
        val current = backStack.getOrNull(1)
        when {
            key == null && current != null -> backStack.removeAt(1)
            key != null && current != key -> {
                if (current != null) backStack.removeAt(1)
                backStack.add(key)
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { controller.close() },
        entryProvider = entryProvider {
            entry(key = ProjectListKey) {
                ProjectOverviewPage(controller, isRunning)
            }
            entry<ProjectKey> { key ->
                // An entry outlives the project it was opened for, because the stack pops
                // asynchronously; rendering from the controller instead of from the key keeps a
                // being-closed project from drawing over the overview.
                val project = controller.opened
                if (project != null && project.directory == key.directory) {
                    ProjectWorkspacePage(controller, project, isRunning)
                }
            }
        },
    )

    // Rendered by the host rather than inside the overview page, so the dialog floats over the whole
    // tab instead of being clipped to the page's padded column. It is a sibling of the `NavDisplay`
    // because a dialog is not a destination.
    if (controller.isInitDialogVisible) {
        ProjectInitDialog(
            controller = controller,
            isRunning = isRunning,
            onDismiss = controller::hideInitDialog,
        )
    }
}
