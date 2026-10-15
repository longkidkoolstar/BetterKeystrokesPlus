package streamproof;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.lwjgl.opengl.Display;

/** Borderless desktop window, never exclusive fullscreen. Native changes are reversible. */
public final class WindowMode {
    private static Pointer target;
    private static int oldStyle,oldX,oldY,oldW,oldH;
    private WindowMode() { }
    public static boolean isEnabled() { return target != null; }
    public static void apply(boolean enabled) {
        if (enabled == isEnabled()) return;
        if (!enabled) { restore(); return; }
        if (Display.isFullscreen() || !Display.isActive()) return;
        Pointer hwnd = Overlay.fn("GetForegroundWindow").invokePointer(new Object[0]);
        if (hwnd != null) enter(hwnd);
    }
    static void enter(Pointer hwnd) {
        if (target != null) return;
        Memory rect = new Memory(16);
        if (Overlay.fn("GetWindowRect").invokeInt(new Object[]{hwnd,rect}) == 0)
            throw new IllegalStateException("Cannot save window bounds");
        int style = Overlay.fn("GetWindowLongW").invokeInt(new Object[]{hwnd,-16});
        Pointer monitor = Overlay.fn("MonitorFromWindow").invokePointer(new Object[]{hwnd,2});
        Memory info = new Memory(40); info.clear(); info.setInt(0,40);
        if (monitor == null || Overlay.fn("GetMonitorInfoW").invokeInt(new Object[]{monitor,info}) == 0)
            throw new IllegalStateException("Cannot locate monitor");
        oldStyle=style; oldX=rect.getInt(0); oldY=rect.getInt(4);
        oldW=rect.getInt(8)-oldX; oldH=rect.getInt(12)-oldY;
        target=hwnd;
        try {
            Overlay.fn("SetWindowLongW").invokeInt(new Object[]{hwnd,-16,(style & ~0x00CF0000) | 0x80000000});
            int x=info.getInt(4), y=info.getInt(8), w=info.getInt(12)-x, h=info.getInt(16)-y;
            if (Overlay.fn("SetWindowPos").invokeInt(new Object[]{hwnd,Pointer.NULL,x,y,w,h,0x0034}) == 0)
                throw new IllegalStateException("Cannot resize borderless window");
            System.out.println("[StreamProof] Borderless desktop window: " + w + "x" + h);
        } catch (RuntimeException error) { restore(); throw error; }
    }
    public static void restore() {
        if (target == null) return;
        Pointer hwnd=target;
        Overlay.fn("SetWindowLongW").invokeInt(new Object[]{hwnd,-16,oldStyle});
        if (Overlay.fn("SetWindowPos").invokeInt(new Object[]{hwnd,Pointer.NULL,oldX,oldY,oldW,oldH,0x0034}) == 0)
            throw new IllegalStateException("Cannot restore window size");
        target=null;
    }
}
