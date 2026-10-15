package streamproof;

import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Exercises local composition and the OBS 30.1 GL copy sequence, without starting OBS or Minecraft. */
public class NativeCaptureProbe {
    static int[] target(int size) {
        int tex=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,tex,0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Test FBO incomplete");
        return new int[]{fbo,tex};
    }
    static int pixel(int x,int y) {
        ByteBuffer bytes=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        return ((bytes.get(0)&255)<<16)|((bytes.get(1)&255)<<8)|(bytes.get(2)&255);
    }
    public static void main(String[] args)throws Exception {
        final java.awt.Frame[] frame=new java.awt.Frame[1];
        final java.awt.Canvas canvas=new java.awt.Canvas();
        java.awt.EventQueue.invokeAndWait(new Runnable(){public void run(){
            frame[0]=new java.awt.Frame("Hidden native capture probe");
            canvas.setPreferredSize(new java.awt.Dimension(32,32));frame[0].add(canvas);
            frame[0].pack();frame[0].setLocation(-20000,-20000);
        }});
        Display.setParent(canvas);Display.setDisplayMode(new DisplayMode(32,32));
        Display.create(new PixelFormat().withAlphaBits(8));
        int[] overlay=null,capture=null;
        try {
            System.out.println("Native-mode test context: double buffering="+GL11.glGetBoolean(GL11.GL_DOUBLEBUFFER));
            overlay=target(32);capture=target(32);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,overlay[0]);
            GL11.glViewport(0,0,32,32);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(8,8,16,16);
            GL11.glClearColor(1,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL11.glReadBuffer(GL11.GL_BACK);GL11.glDrawBuffer(GL11.GL_BACK);
            int backError=GL11.glGetError();
            if(backError!=0)throw new AssertionError("Pbuffer cannot test GL_BACK; error="+backError);
            GL11.glClearColor(0,0,1,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            if(pixel(16,16)!=0x0000FF)throw new AssertionError("Test backbuffer could not be initialized");
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,capture[1]);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            NativeCapture.present(overlay[1],32,32);
            int clean=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            if(clean==0)throw new AssertionError("Clean read buffer was not selected");
            int error=GL11.glGetError();if(error!=0)throw new AssertionError("Presentation GL error "+error);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            if(!GL11.glIsEnabled(GL11.GL_TEXTURE_2D) || GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=capture[1])throw new AssertionError("Secondary texture-unit state not restored");
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);GL11.glReadBuffer(GL11.GL_BACK);
            if(pixel(16,16)!=0xFF0000 || pixel(2,2)!=0x0000FF)throw new AssertionError("Local overlay composition incorrect");
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,clean);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            // Same read/draw binding behavior used by OBS 30.1 gl_copy_backbuffer.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,capture[0]);
            GL11.glReadBuffer(GL11.GL_BACK);
            int expected=GL11.glGetError();
            if(expected!=GL11.GL_INVALID_OPERATION)throw new AssertionError("Unexpected OBS-compatible read selector result "+expected);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0,0,32,32,0,0,32,32,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,capture[0]);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if(pixel(16,16)!=0x0000FF || pixel(2,2)!=0x0000FF)throw new AssertionError("Clean OBS copy contains the overlay");
            NativeCapture.afterPresent();
            if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=0 || GL11.glGetInteger(GL11.GL_READ_BUFFER)!=GL11.GL_BACK)
                throw new AssertionError("Read framebuffer/selector not restored");
            System.out.println("PASS GPU native-mode experiment: local backbuffer has red overlay; OBS 30.1 copy sequence sees clean blue image; read state restored");
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);GL11.glDrawBuffer(GL11.GL_BACK);
            NativeCapture.present(overlay[1],32,32);
            NativeCapture.updateDisplay();
            if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=0 || GL11.glGetError()!=0)
                throw new AssertionError("Display swap/read-state restoration failed");
            java.lang.reflect.Method ensure=Overlay.class.getDeclaredMethod("ensureFramebuffer",int.class,int.class);ensure.setAccessible(true);
            java.lang.reflect.Field pixels=Overlay.class.getDeclaredField("pixels");pixels.setAccessible(true);
            java.lang.reflect.Field images=Overlay.class.getDeclaredField("images");images.setAccessible(true);
            Overlay.nativeMode=true;ensure.invoke(null,32,32);
            if(pixels.get(null)!=null || images.get(null)!=null)throw new AssertionError("Native mode allocated CPU image/readback buffers");
            Overlay.nativeMode=false;ensure.invoke(null,32,32);
            if(pixels.get(null)==null || images.get(null)==null)throw new AssertionError("Desktop mode did not reinitialize its readback buffers");
            java.lang.reflect.Method release=Overlay.class.getDeclaredMethod("release");release.setAccessible(true);release.invoke(null);
            System.out.println("PASS display swap and mode change: swap succeeds with clean read FBO, state restored; GPU mode avoids CPU image/readback allocations");
        } finally {
            NativeCapture.release();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
            if(overlay!=null){GL30.glDeleteFramebuffers(overlay[0]);GL11.glDeleteTextures(overlay[1]);}
            if(capture!=null){GL30.glDeleteFramebuffers(capture[0]);GL11.glDeleteTextures(capture[1]);}
            Display.destroy();Display.setParent(null);
            java.awt.EventQueue.invokeAndWait(new Runnable(){public void run(){frame[0].dispose();}});
        }
    }
}
