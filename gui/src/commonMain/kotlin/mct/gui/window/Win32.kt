package mct.gui.window

import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType

/** Signature of a window procedure: `LRESULT CALLBACK (HWND, UINT, WPARAM, LPARAM)`. */
private val WINDOW_PROCEDURE_TYPE: MethodType = MethodType.methodType(
    Long::class.javaPrimitiveType,
    Long::class.javaPrimitiveType,
    Int::class.javaPrimitiveType,
    Long::class.javaPrimitiveType,
    Long::class.javaPrimitiveType,
)

/**
 * The user32/dwmapi calls needed to give a custom-chrome window a native frame back.
 *
 * Compose draws its own title bar on an undecorated window, which AWT implements as a `WS_POPUP`
 * window without `WS_CAPTION`. The window manager then treats it as a plain popup and skips every
 * frame animation — maximize, restore, minimize, snap (compose-multiplatform#3388). Restoring the
 * caption and frame styles while answering `WM_NCCALCSIZE` with a zero-sized non-client area makes
 * the window ordinary to DWM again, without drawing a system title bar over Compose.
 *
 * Only this file touches `java.lang.foreign`; callers get a plain typed API. Calls go through
 * [MethodHandle.invokeWithArguments] rather than `invokeExact`, whose descriptor Kotlin derives from
 * the types at the call site, which makes a pointer-typed argument easy to get subtly wrong.
 */
internal object Win32 {

    /**
     * Owns the upcall stub and the `MARGINS` block. Both must stay valid for as long as Windows may
     * call back into them — the life of the process — so they are not scoped to a single window.
     */
    private val arena: Arena = Arena.ofShared()

    private val linker: Linker = Linker.nativeLinker()
    private val user32: SymbolLookup = SymbolLookup.libraryLookup("user32", arena)
    private val dwmapi: SymbolLookup = SymbolLookup.libraryLookup("dwmapi", arena)

    private val getWindowLongPtr: MethodHandle = user32.downcall("GetWindowLongPtrW", ADDRESS, JAVA_LONG, JAVA_INT)

    private val setWindowLongPtr: MethodHandle =
        user32.downcall("SetWindowLongPtrW", ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS)

    private val callWindowProc: MethodHandle =
        user32.downcall("CallWindowProcW", JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG)

    private val setWindowPos: MethodHandle = user32.downcall(
        "SetWindowPos", JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
    )

    private val extendFrameIntoClientArea: MethodHandle =
        dwmapi.downcall("DwmExtendFrameIntoClientArea", JAVA_INT, JAVA_LONG, ADDRESS)

    private val isZoomed: MethodHandle = user32.downcall("IsZoomed", JAVA_INT, JAVA_LONG)

    private val monitorFromWindow: MethodHandle = user32.downcall("MonitorFromWindow", ADDRESS, JAVA_LONG, JAVA_INT)

    private val getMonitorInfo: MethodHandle = user32.downcall("GetMonitorInfoW", JAVA_INT, ADDRESS, ADDRESS)

    /** Scratch for `MONITORINFO`; window messages are delivered on one thread at a time. */
    private val monitorInfo: MemorySegment = arena.allocate(MONITOR_INFO_SIZE)

    /** Whether the window is maximized, per the platform rather than AWT's view of it. */
    fun isMaximized(hwnd: Long): Boolean = (isZoomed.invokeWithArguments(hwnd) as Int) != 0

    /**
     * Work area — the desktop minus taskbar and dock — of the monitor [hwnd] sits on, as
     * `[left, top, right, bottom]`, or `null` when the monitor cannot be resolved.
     */
    fun monitorWorkArea(hwnd: Long): IntArray? {
        // MONITOR_DEFAULTTONEAREST: maximizing on a monitor that was just unplugged still resolves.
        val monitor = monitorFromWindow.invokeWithArguments(hwnd, MONITOR_DEFAULTTONEAREST) as MemorySegment
        if (monitor.address() == 0L) return null
        monitorInfo.set(JAVA_INT, 0L, MONITOR_INFO_SIZE.toInt())
        if ((getMonitorInfo.invokeWithArguments(monitor, monitorInfo) as Int) == 0) return null
        return IntArray(4) { monitorInfo.get(JAVA_INT, MONITOR_INFO_WORK_OFFSET + it * JAVA_INT.byteSize()) }
    }

    private const val MONITOR_INFO_SIZE = 40L
    private const val MONITOR_INFO_WORK_OFFSET = 20L
    private const val MONITOR_DEFAULTTONEAREST = 2

    /**
     * `MARGINS` for `DwmExtendFrameIntoClientArea`. `-1` on every side asks DWM to treat the whole
     * window as glass, which is what keeps the native border, drop shadow and Windows 11 rounded
     * corners drawn around content that Compose renders itself.
     */
    private val windowMargins: MemorySegment = arena.allocate(4L * JAVA_INT.byteSize()).apply {
        set(JAVA_INT, 0L, -1)
        set(JAVA_INT, 4L, -1)
        set(JAVA_INT, 8L, -1)
        set(JAVA_INT, 12L, -1)
    }

    fun getStyle(hwnd: Long): Long = (getWindowLongPtr.invokeWithArguments(hwnd, GWL_STYLE) as MemorySegment).address()

    fun setStyle(hwnd: Long, style: Long) {
        setWindowLongPtr.invokeWithArguments(hwnd, GWL_STYLE, MemorySegment.ofAddress(style))
    }

    /** Replace the window procedure, returning the previous one. */
    fun setWindowProcedure(hwnd: Long, procedure: MemorySegment): Long =
        (setWindowLongPtr.invokeWithArguments(hwnd, GWL_WNDPROC, procedure) as MemorySegment).address()

    fun restoreWindowProcedure(hwnd: Long, previous: Long) {
        setWindowLongPtr.invokeWithArguments(hwnd, GWL_WNDPROC, MemorySegment.ofAddress(previous))
    }

    fun callWindowProc(previous: Long, hwnd: Long, message: Int, wParam: Long, lParam: Long): Long =
        callWindowProc.invokeWithArguments(previous, hwnd, message, wParam, lParam) as Long

    /** Ask for a non-client area recalculation without moving, resizing or reordering the window. */
    fun refreshFrame(hwnd: Long) {
        setWindowPos.invokeWithArguments(
            hwnd, hwnd, 0, 0, 0, 0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
        )
    }

    /** Let DWM draw the native frame (border, shadow, rounded corners) over the whole window. */
    fun extendFrameIntoClientArea(hwnd: Long) {
        extendFrameIntoClientArea.invokeWithArguments(hwnd, windowMargins)
    }

    /** Turn `handleMessage` on [owner] into a callable native pointer. */
    fun windowProcedure(procedure: MethodHandle): MemorySegment =
        linker.upcallStub(procedure, FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG), arena)

    val windowProcedureType: MethodType get() = WINDOW_PROCEDURE_TYPE

    private fun SymbolLookup.downcall(
        name: String,
        result: MemoryLayout,
        vararg parameters: MemoryLayout,
    ): MethodHandle = linker.downcallHandle(find(name).orElseThrow(), FunctionDescriptor.of(result, *parameters))
}

private const val GWL_STYLE = -16
private const val GWL_WNDPROC = -4

private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002
private const val SWP_NOZORDER = 0x0004
private const val SWP_NOACTIVATE = 0x0010
private const val SWP_FRAMECHANGED = 0x0020
