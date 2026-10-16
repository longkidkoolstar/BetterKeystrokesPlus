import java.awt.EventQueue;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import streamproof.Overlay;

public class OverlayProbe {
    static void set(String n,Object v)throws Exception { Field f=Overlay.class.getDeclaredField(n);f.setAccessible(true);f.set(null,v); }
    static Object get(String n)throws Exception { Field f=Overlay.class.getDeclaredField(n);f.setAccessible(true);return f.get(null); }
    static void call(String n,Class<?>[] types,Object...args)throws Exception { Method m=Overlay.class.getDeclaredMethod(n,types);m.setAccessible(true);m.invoke(null,args); }
    public static void main(String[] args)throws Exception {
        Pbuffer context=null;
        try {
            if(Overlay.begin()!=0)throw new AssertionError("Disabled draw should pass through");
            Overlay.active=true;
            if(Overlay.begin()!=-1)throw new AssertionError("Unready draw should be suppressed");
            set("screenX",-20000);set("screenY",-20000);set("screenW",32);set("screenH",32);
            call("ensureWindow",new Class<?>[0]);
            EventQueue.invokeAndWait(new Runnable(){public void run(){}});
            if(!(Boolean)get("ready"))throw new AssertionError("Native capture exclusion unavailable: "+Overlay.status);
            System.out.println("PASS native excluded window: Windows accepted and confirmed affinity 0x11; window stayed hidden");
            context=new Pbuffer(32,32,new PixelFormat(),null);context.makeCurrent();
            call("ensureFramebuffer",new Class<?>[]{int.class,int.class},32,32);
            int original=GL11.glGetInteger(0x8CA6);
            GL11.glClearColor(0,0,1,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            set("frameReady",true);
            GL11.glColorMask(true,true,true,false);
            int token=Overlay.begin();
            if(token<=0 || GL11.glGetInteger(0x8CA6)==original)throw new AssertionError("Draw did not redirect");
            GL11.glClearColor(1,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if((pixel.get(0)&255)!=255 || (pixel.get(2)&255)!=0)throw new AssertionError("Overlay color wrong");
            Overlay.end(token);
            ByteBuffer mask=BufferUtils.createByteBuffer(16);GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK,mask);
            if(mask.get(3)!=0)throw new AssertionError("Original alpha write mask was not restored");
            GL11.glColorMask(true,true,true,true);
            if(GL11.glGetInteger(0x8CA6)!=original)throw new AssertionError("Framebuffer not restored");
            pixel.clear();GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if((pixel.get(0)&255)!=0 || (pixel.get(2)&255)!=255)throw new AssertionError("Overlay leaked into main image");
            System.out.println("PASS OpenGL separation: overlay red, main image stayed blue, original framebuffer restored");
            // Exercise the actual readiness-label drawing entrypoint, including failure cleanup.
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            streamproof.BowHud.paint(new streamproof.BowHud.Painter() { public void draw() {
                GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glClearColor(0,1,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            }});
            if(GL11.glIsEnabled(GL11.GL_DEPTH_TEST) || GL11.glGetInteger(0x8CA6)!=original)throw new AssertionError("Readiness state leaked");
            pixel.clear();GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if((pixel.get(2)&255)!=255 || (pixel.get(1)&255)!=0)throw new AssertionError("Readiness leaked to clean game");
            int labelToken=Overlay.begin();pixel.clear();GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if((pixel.get(1)&255)!=255)throw new AssertionError("Readiness was not drawn in overlay");
            GL11.glClearColor(1,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);Overlay.end(labelToken);
            try { streamproof.BowHud.paint(new streamproof.BowHud.Painter() { public void draw() throws ReflectiveOperationException { throw new ReflectiveOperationException("fixture"); }}); }
            catch(ReflectiveOperationException expected) {}
            if(GL11.glGetInteger(0x8CA6)!=original)throw new AssertionError("Readiness failure did not restore framebuffer");
            System.out.println("PASS readiness HUD route: green label layer excluded from clean game, GL state and error cleanup restored");
            set("renderFrame",false);
            if(Overlay.begin()!=-1)throw new AssertionError("Overlay should skip draws between refresh frames");
            set("renderFrame",true);
            // Exercise the exact packed BGRA row conversion with different colors and rows.
            ByteBuffer raw=BufferUtils.createByteBuffer(16);
            raw.putInt(0xFF0000FF).putInt(0xFF00FF00).putInt(0xFFFF0000).putInt(0xFFFFFFFF);
            raw.rewind();
            int[] converted=new int[4];
            Method copy=Overlay.class.getDeclaredMethod("copyRows",java.nio.IntBuffer.class,int[].class,int.class,int.class);copy.setAccessible(true);
            copy.invoke(null,raw.asIntBuffer(),converted,2,2);
            if(converted[0]!=0xFFFF0000 || converted[1]!=0xFFFFFFFF || converted[2]!=0xFF0000FF || converted[3]!=0xFF00FF00)
                throw new AssertionError("Packed color conversion or vertical flip incorrect");
            // Use the new production readback path against a red overlay; suppress UI publishing.
            ((java.util.concurrent.atomic.AtomicBoolean)get("paintQueued")).set(true);
            Overlay.endFrame();
            java.awt.image.BufferedImage published=(java.awt.image.BufferedImage)get("pending");
            if(published==null || published.getRGB(0,0)!=0xFFFF0000)throw new AssertionError("Production BGRA readback pixel incorrect");
            System.out.println("PASS optimized readback: packed colors, row orientation, reused image output, draw skipping between refreshes");
            call("release",new Class<?>[0]);
        } finally {
            Overlay.stop();EventQueue.invokeAndWait(new Runnable(){public void run(){}});
            if(context!=null)context.destroy();
        }
    }
}
