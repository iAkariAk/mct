package mct.gui.window

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles

// Window messages.
private const val WM_DESTROY = 0x0002
private const val WM_NCCALCSIZE = 0x0083

/**
 * `NCCALCSIZE_PARAMS`: three `RECT`s followed by a `WINDOWPOS` pointer. Only the first `RECT` — the
 * proposed window rect, which the app overwrites with the client rect it wants — is ever touched.
 */
private const val NCCALCSIZE_PARAMS_SIZE = 56L

// Window style bits.
private const val WS_POPUP = 0x80000000L
private const val WS_CAPTION = 0x00C00000L
private const val WS_THICKFRAME = 0x00040000L
private const val WS_SYSMENU = 0x00080000L

/** Whether the native frame technique is available: it is a Windows-only, 64-bit-only path. */
val isNativeWindowFrameSupported: Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true) &&
        System.getProperty("sun.arch.data.model") == "64"

/**
 * Gives a Compose-drawn undecorated window a native frame, so the platform animates maximize,
 * restore, minimize and snap again.
 *
 * Compose asks AWT for `undecorated = true`, which AWT implements by clearing `WS_CAPTION` and
 * `WS_THICKFRAME` and setting `WS_POPUP`. Windows treats such a window as a popup and skips every
 * frame animation (compose-multiplatform#3388) — the reason this application tried to animate the
 * window bounds by hand. Restoring the frame styles and answering `WM_NCCALCSIZE` with a zero-sized
 * non-client area makes DWM see an ordinary window while Compose keeps drawing the whole title bar.
 *
 * Transparency is preserved: the window keeps `WS_EX_LAYERED`, and `DwmExtendFrameIntoClientArea`
 * receives full-glass margins so the native border, shadow and rounded corners come back with it.
 *
 * A no-op outside Windows; any failure leaves the window exactly as Compose created it.
 */
fun applyNativeWindowFrame(window: ComposeWindow) {
    if (!isNativeWindowFrameSupported) return
    runCatching { NativeWindowFrame(window).install() }
        .onFailure { println("原生窗口边框不可用，最大/最小化将没有系统动画: ${it.message}") }
}

private class NativeWindowFrame(private val window: ComposeWindow) {

    private val hwnd: Long = window.windowHandle

    /** The procedure replaced by [stub]; messages this frame does not handle go back to it. */
    private var previousProcedure = 0L

    private val stub: MemorySegment = Win32.windowProcedure(
        MethodHandles.lookup().bind(this, "handleMessage", Win32.windowProcedureType),
    )

    /**
     * Install the frame. Order matters: the procedure has to be reachable before the style change,
     * because that change generates the `WM_NCCALCSIZE` that suppresses the non-client area.
     */
    fun install() {
        previousProcedure = Win32.setWindowProcedure(hwnd, stub)
        restoreFrameStyles()
        Win32.refreshFrame(hwnd)
        Win32.extendFrameIntoClientArea(hwnd)
        window.addComponentListener(maximizedBounds)
        maximizedBounds.update()
    }

    /**
     * Keeps `maximizedBounds` at the work area of the monitor the window is on.
     *
     * A window with `WS_THICKFRAME` that the platform maximizes on its own grows by the frame
     * thickness on every side, which this window would draw over the taskbar because its non-client
     * area is zero. Pinning the bounds to the work area makes the maximized window cover exactly the
     * usable desktop, matching what a decorated window looks like.
     */
    private val maximizedBounds = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) = update()
        override fun componentMoved(e: ComponentEvent) = update()

        fun update() {
            val configuration = window.graphicsConfiguration ?: return
            val bounds = configuration.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
            val workArea = Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom,
            )
            if (window.maximizedBounds != workArea) window.maximizedBounds = workArea
        }
    }

    /**
     * `WS_POPUP` off, `WS_CAPTION` and `WS_THICKFRAME` on: to the window manager this is a normal
     * resizable window, which is what it animates and what makes it snappable. None of that frame is
     * ever painted, because [handleMessage] reports a zero-sized non-client area; the client area
     * stays the whole window, so Compose keeps drawing its own title bar edge to edge.
     */
    private fun restoreFrameStyles() {
        val style = Win32.getStyle(hwnd)
        val updated = (style and WS_POPUP.inv()) or WS_CAPTION or WS_THICKFRAME or WS_SYSMENU
        if (updated != style) Win32.setStyle(hwnd, updated)
    }

    @Suppress("unused") // Called by Windows through the upcall stub created in `stub`.
    private fun handleMessage(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long = when (message) {
        // `wParam == 0` (FALSE) asks for the client rect that a proposed size would produce rather
        // than resizing, so it is left to the default procedure.
        WM_NCCALCSIZE -> if (wParam == 0L) forward(hwnd, message, wParam, lParam) else resizeClientArea(hwnd, lParam)

        WM_DESTROY -> {
            window.removeComponentListener(maximizedBounds)
            Win32.restoreWindowProcedure(hwnd, previousProcedure)
            forward(hwnd, message, wParam, lParam)
        }

        else -> forward(hwnd, message, wParam, lParam)
    }

    /**
     * Report the client rect of the resized window.
     *
     * Floating: zero non-client area, so the client is the whole window and Compose draws edge to
     * edge, corners included. Maximized: the work area. The platform maximizes a `WS_THICKFRAME`
     * window to the work area *inflated by the frame thickness*, so a zero inset here would render
     * the content 7px past every screen edge and over the taskbar; insetting to the work area leaves
     * that overhang to DWM as the window frame, which is what a decorated window does.
     */
    private fun resizeClientArea(hwnd: Long, lParam: Long): Long {
        if (Win32.isMaximized(hwnd)) {
            val workArea = Win32.monitorWorkArea(hwnd)
            if (workArea != null) {
                val rect = MemorySegment.ofAddress(lParam).reinterpret(NCCALCSIZE_PARAMS_SIZE)
                workArea.forEachIndexed { index, value -> rect.set(JAVA_INT, index * JAVA_INT.byteSize(), value) }
                return 0L
            }
        }
        return 0L
    }

    private fun forward(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long =
        Win32.callWindowProc(previousProcedure, hwnd, message, wParam, lParam)
}
