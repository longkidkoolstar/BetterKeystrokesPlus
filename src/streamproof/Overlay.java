package streamproof;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JWindow;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.EXTFramebufferObject;

public final class Overlay {
    public static volatile boolean active;
    public static volatile String status = "Off";
    public static int refreshRate = 30;
    public static boolean nativeMode;
    private static volatile boolean ready, failed;
    private static volatile long generation;
    private static final AtomicBoolean queued = new AtomicBoolean();
    private static volatile BufferedImage pending;
    private static JWindow window;
    private static Surface surface;
    private static NativeLibrary user32;
    private static int fbo, texture, depth, width, height;
    private static boolean allocatedNative;
    private static ByteBuffer pixels;
    private static IntBuffer packedPixels;
    private static BufferedImage[] images;
    private static boolean frameReady;
    private static boolean renderFrame = true;
    private static int nesting;
    private static final java.util.ArrayDeque<byte[]> colorMasks = new java.util.ArrayDeque<byte[]>();
    private static final ByteBuffer maskBuffer = BufferUtils.createByteBuffer(16);
    private static long lastPublish;
    private static int reportedFrames;
    private static volatile int screenX, screenY, screenW, screenH;
    private static final int FB = 0x8D40, FB_BIND = 0x8CA6;

    private Overlay() { }
    public static void resetFailure() { failed = false; generation++; reportedFrames = 0; status = "Starting excluded overlay..."; }
    public static boolean hasFailed() { return failed; }
    public static boolean prepare() {
        resetFailure();
        active = true;
        if(nativeMode && !Boolean.getBoolean("streamproof.displayHook")) {
            active=false;status="OBS native display hook missing; see game log";return false;
        }
        startFrame();
        try {
            if (!nativeMode && !failed && !ready && !Display.isFullscreen() && Display.isActive())
                EventQueue.invokeAndWait(new Runnable() { public void run() { } });
            if (!failed) startFrame();
        } catch (Throwable t) { fail(t); }
        if (failed || !frameReady) {
            String reason = status;
            stop();
            status = reason;
            return false;
        }
        System.out.println("[StreamProof] v5 ready: " + width + "x" + height + ", mode " + (nativeMode?"OBS GPU clean-read experiment":"excluded desktop window"));
        return true;
    }
    public static void stop() {
        if(nativeMode && GLContext.getCapabilities()!=null)NativeCapture.afterPresent();
        active = false; ready = false; frameReady = false; generation++; pending = null;
        EventQueue.invokeLater(new Runnable() { public void run() { disposeWindow(); } });
        status = "Off";
    }
    private static void disposeWindow() {
        if (window != null) { window.dispose(); window = null; surface = null; }
    }
    static Function fn(String name) {
        if (user32 == null) user32 = NativeLibrary.getInstance("user32");
        // JNA 3.4 (Minecraft's launcher library) and the bundled newer JNA use
        // different numeric values. Reading the runtime field avoids javac's
        // inlining of the newer constant into a client running the older JNA.
        try {
            int convention = Function.class.getField("ALT_CONVENTION").getInt(null);
            return user32.getFunction(name, convention);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot determine native calling convention", e);
        }
    }
    private static void fail(Throwable t) {
        failed = true; ready = false; frameReady = false;
        status = "Overlay error: " + t.getClass().getSimpleName() + " (see game log)";
        System.err.println("[StreamProof] Overlay stopped; redirected visuals suppressed: " + t);
        t.printStackTrace();
        EventQueue.invokeLater(new Runnable() { public void run() { disposeWindow(); } });
    }
    public static void startFrame() {
        frameReady = false;
        renderFrame = false;
        if (!active) { if (fbo != 0) release(); return; }
        if (failed) return;
        try {
            if (!nativeMode && Display.isFullscreen()) {
                status = "Use windowed mode (F11), then enable again";
                failed = true; hideWindow(); return;
            }
            if (!Display.isActive() || !Display.isVisible()) {
                status = "Focus Minecraft, then enable again";
                hideWindow(); return;
            }
            // Preserve the last overlay between refreshes. Do not clear and redraw
            // every game frame when only 30 overlay images per second are shown.
            if (ready && fbo != 0 && allocatedNative==nativeMode && width==Display.getWidth() && height==Display.getHeight() && !refreshDue(System.nanoTime())) {
                frameReady = true;
                return;
            }
            if (!System.getProperty("os.name", "").startsWith("Windows")) throw new IllegalStateException("Windows required");
            if (!GLContext.getCapabilities().GL_EXT_framebuffer_object) throw new IllegalStateException("EXT framebuffer support required");
            if(nativeMode) {
                if(!GLContext.getCapabilities().OpenGL30)throw new IllegalStateException("OBS native mode requires OpenGL 3.0");
                ready=true;
            }
            Pointer game = nativeMode?null:fn("GetForegroundWindow").invokePointer(new Object[0]);
            if(!nativeMode) {
            if (game == null) { status = "Cannot locate focused Minecraft window"; hideWindow(); return; }
            Memory rect = new Memory(16), point = new Memory(8);
            point.clear();
            if (fn("GetClientRect").invokeInt(new Object[]{game, rect}) == 0 || fn("ClientToScreen").invokeInt(new Object[]{game, point}) == 0)
                throw new IllegalStateException("Cannot locate Minecraft client area");
            screenX = point.getInt(0); screenY = point.getInt(4);
            screenW = rect.getInt(8); screenH = rect.getInt(12);
            ensureWindow();
            }
            if (!ready) return;
            int w = Display.getWidth(), h = Display.getHeight();
            if (w < 1 || h < 1 || w > 8192 || h > 8192) throw new IllegalStateException("Unsupported display dimensions");
            ensureFramebuffer(w, h);
            int saved = GL11.glGetInteger(FB_BIND);
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
            try {
                EXTFramebufferObject.glBindFramebufferEXT(FB, fbo);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glColorMask(true, true, true, true); GL11.glDepthMask(true);
                GL11.glClearColor(0,0,0,0); GL11.glClearDepth(1);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            } finally { EXTFramebufferObject.glBindFramebufferEXT(FB, saved); GL11.glPopAttrib(); }
            frameReady = true; renderFrame = true; status = nativeMode?"OBS native TEST: Game Capture only":"Experimental overlay active (F9 toggles)";
        } catch (Throwable t) { fail(t); }
    }
    private static void hideWindow() {
        pending = null;
        EventQueue.invokeLater(new Runnable() { public void run() { if (window != null) window.setVisible(false); } });
    }
    private static void ensureWindow() {
        if (ready || !queued.compareAndSet(false, true)) return;
        final long version = generation;
        EventQueue.invokeLater(new Runnable() { public void run() {
            try {
                if (!active || version != generation) return;
                disposeWindow();
                window = new JWindow();
                window.setName("Stream Proof Experimental Overlay");
                window.setFocusableWindowState(false); window.setAutoRequestFocus(false);
                window.setAlwaysOnTop(true); window.setBackground(new Color(0,0,0,0));
                surface = new Surface(); window.setContentPane(surface);
                window.setBounds(screenX, screenY, Math.max(screenW,1), Math.max(screenH,1));
                window.addNotify();
                Pointer hwnd = Native.getWindowPointer(window);
                // Make the native window layered, click-through, non-activating, and absent from Alt-Tab.
                int style = fn("GetWindowLongW").invokeInt(new Object[]{hwnd, -20});
                fn("SetWindowLongW").invokeInt(new Object[]{hwnd, -20, style | 0x80000 | 0x20 | 0x80 | 0x08000000});
                if (fn("SetWindowDisplayAffinity").invokeInt(new Object[]{hwnd, 0x11}) == 0)
                    throw new IllegalStateException("Windows rejected capture exclusion, error " + Native.getLastError());
                Memory affinity = new Memory(4);
                if (fn("GetWindowDisplayAffinity").invokeInt(new Object[]{hwnd, affinity}) == 0 || affinity.getInt(0) != 0x11)
                    throw new IllegalStateException("Capture exclusion could not be confirmed");
                if (active && version == generation) ready = true;
                else disposeWindow();
            } catch (Throwable t) { fail(t); }
            finally { queued.set(false); }
        } });
    }
    private static void ensureFramebuffer(int w, int h) {
        if (fbo != 0 && width == w && height == h && allocatedNative==nativeMode) return;
        release();
        int saved = GL11.glGetInteger(FB_BIND);
        int tex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int rb = GL11.glGetInteger(0x8CA7);
        try {
            width = w; height = h;
            allocatedNative=nativeMode;
            fbo = EXTFramebufferObject.glGenFramebuffersEXT();
            texture = GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
            EXTFramebufferObject.glBindFramebufferEXT(FB, fbo);
            EXTFramebufferObject.glFramebufferTexture2DEXT(FB, 0x8CE0, GL11.GL_TEXTURE_2D, texture, 0);
            depth = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(0x8D41, depth);
            EXTFramebufferObject.glRenderbufferStorageEXT(0x8D41, 0x81A6, w, h);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(FB, 0x8D00, 0x8D41, depth);
            if (EXTFramebufferObject.glCheckFramebufferStatusEXT(FB) != 0x8CD5) throw new IllegalStateException("Overlay framebuffer incomplete");
            if(!nativeMode) {
            pixels = BufferUtils.createByteBuffer(w * h * 4);
            packedPixels = pixels.asIntBuffer();
            images = new BufferedImage[3];
            for (int i=0;i<images.length;i++) images[i] = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB_PRE);
            }
        } finally {
            EXTFramebufferObject.glBindFramebufferEXT(FB, saved);
            EXTFramebufferObject.glBindRenderbufferEXT(0x8D41, rb);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        }
    }
    private static void release() {
        if(NativeCapture.hasResources())NativeCapture.release();
        if (texture != 0) GL11.glDeleteTextures(texture);
        if (depth != 0) EXTFramebufferObject.glDeleteRenderbuffersEXT(depth);
        if (fbo != 0) EXTFramebufferObject.glDeleteFramebuffersEXT(fbo);
        texture = depth = fbo = 0; pixels = null; packedPixels = null; images = null;
    }
    /** -1 suppresses the draw, 0 leaves it unchanged, positive tokens restore its framebuffer. */
    public static int begin() {
        if (!active) return 0;
        if (!frameReady || failed || !ready) return -1;
        if (!renderFrame) return -1;
        try {
            int old = GL11.glGetInteger(FB_BIND);
            maskBuffer.clear(); GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, maskBuffer);
            byte[] mask = new byte[]{maskBuffer.get(0),maskBuffer.get(1),maskBuffer.get(2),maskBuffer.get(3)};
            EXTFramebufferObject.glBindFramebufferEXT(FB, fbo);
            colorMasks.push(mask);
            GL11.glColorMask(true,true,true,true);
            nesting++;
            return old + 1;
        } catch (Throwable t) { fail(t); return -1; }
    }
    public static void end(int token) {
        if (token <= 0) return;
        EXTFramebufferObject.glBindFramebufferEXT(FB, token - 1);
        byte[] mask = colorMasks.pop();
        GL11.glColorMask(mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);
        nesting--;
    }
    /** Keep vanilla nametags in the game image; replacement tags are drawn on the overlay. */
    public static void cancelEvent(net.minecraftforge.fml.common.eventhandler.Event event, boolean cancel) {
        if (!active) event.setCanceled(cancel);
    }
    public static void endFrame() {
        if (!active || !frameReady || !renderFrame || failed || nesting != 0) return;
        lastPublish = System.nanoTime();
        if(nativeMode)return;
        int saved = GL11.glGetInteger(FB_BIND);
        try {
            EXTFramebufferObject.glBindFramebufferEXT(FB, fbo);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            pixels.clear();
            GL11.glReadPixels(0,0,width,height,0x80E1,GL11.GL_UNSIGNED_BYTE,pixels); // BGRA matches native Windows ARGB ints
            BufferedImage image = freeImage();
            if (image == null) return;
            int[] data = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
            copyRows(packedPixels,data,width,height);
            pending = image;
            if (reportedFrames < 3) {
                reportedFrames++;
                System.out.println("[StreamProof] Overlay image " + width + "x" + height + ", pooled BGRA readback, " + refreshRate + " Hz");
            }
            publish();
        } catch (Throwable t) { fail(t); }
        finally {
            GL11.glPopClientAttrib();
            EXTFramebufferObject.glBindFramebufferEXT(FB, saved);
        }
    }
    static void presentNative() {
        if(!active || !nativeMode || !frameReady || failed || fbo==0)return;
        try { NativeCapture.present(texture,width,height); }
        catch(Throwable t) { fail(t); }
    }
    private static boolean refreshDue(long now) {
        return now-lastPublish >= 1000000000L/Math.max(10,Math.min(240,refreshRate));
    }
    static void copyRows(IntBuffer source,int[] target,int w,int h) {
        for (int y=0;y<h;y++) {
            source.position((h-1-y)*w);
            source.get(target,y*w,w);
        }
    }
    private static BufferedImage freeImage() {
        BufferedImage displayed = surface == null ? null : surface.image;
        for (BufferedImage image : images) if (image != pending && image != displayed) return image;
        return null;
    }
    private static final AtomicBoolean paintQueued = new AtomicBoolean();
    private static void publish() {
        if (!paintQueued.compareAndSet(false,true)) return;
        final long version = generation;
        EventQueue.invokeLater(new Runnable() { public void run() {
            try {
                if (!active || !ready || failed || version != generation || window == null || pending == null) return;
                // Focus may change after the render thread queues this update.
                if (!Display.isActive()) { window.setVisible(false); return; }
                if (window.getX()!=screenX || window.getY()!=screenY || window.getWidth()!=screenW || window.getHeight()!=screenH)
                    window.setBounds(screenX,screenY,Math.max(1,screenW),Math.max(1,screenH));
                surface.image = pending;
                if (!window.isVisible()) window.setVisible(true);
                surface.repaint();
            } finally { paintQueued.set(false); }
        } });
    }
    private static final class Surface extends JComponent {
        volatile BufferedImage image;
        Surface() { setOpaque(false); setDoubleBuffered(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics copy = g.create();
            try {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D)copy;
                g2.setComposite(java.awt.AlphaComposite.Src);
                g2.setColor(new Color(0,0,0,0)); g2.fillRect(0,0,getWidth(),getHeight());
                if (image != null) g2.drawImage(image,0,0,getWidth(),getHeight(),null);
            } finally { copy.dispose(); }
        }
    }
}
