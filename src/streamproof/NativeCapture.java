package streamproof;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.*;

/**
 * Experimental OBS 30.1 OpenGL read-framebuffer path. This does not exclude desktop
 * capture. OBS's current GL copy routine preserves the caller's READ framebuffer;
 * this presents a clean snapshot to it while showing the composite on the screen.
 */
public final class NativeCapture {
    private static int cleanFbo,cleanTexture,width,height;
    private static boolean armed;
    private static int savedRead,savedReadBuffer;
    private static boolean announced;
    private NativeCapture() { }
    public static void updateDisplay() {
        if(Overlay.active && Overlay.nativeMode) Overlay.presentNative();
        try { Display.update(); }
        finally { afterPresent(); }
    }
    static void present(int overlayTexture,int w,int h) {
        if(armed)afterPresent();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldMatrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        ensureClean(w,h);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true,true,true,true);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);
            GL11.glReadBuffer(GL11.GL_BACK);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,cleanFbo);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0,0,w,h,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glViewport(0,0,w,h);
            GL20.glUseProgram(0);
            int textureUnits=GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
            for(int unit=1;unit<textureUnits;unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,overlayTexture);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1,1,1,1);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glLoadIdentity();
            try {
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0,0);GL11.glVertex2f(-1,-1);
                GL11.glTexCoord2f(1,0);GL11.glVertex2f(1,-1);
                GL11.glTexCoord2f(1,1);GL11.glVertex2f(1,1);
                GL11.glTexCoord2f(0,1);GL11.glVertex2f(-1,1);
                GL11.glEnd();
            } finally {
                GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
                GL11.glMatrixMode(oldMatrixMode);
            }
        } finally {
            GL20.glUseProgram(oldProgram);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture);
            GL11.glPopAttrib();
        }
        savedRead=oldRead;savedReadBuffer=oldReadBuffer;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,cleanFbo);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        armed=true;
        if(!announced) {
            announced=true;
            System.out.println("[StreamProof] v5 GPU clean-read mode armed. OBS Game Capture ONLY; desktop/display capture is not hidden.");
        }
    }
    static void afterPresent() {
        if(!armed)return;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,savedRead);
        GL11.glReadBuffer(savedReadBuffer);
        armed=false;
    }
    private static void ensureClean(int w,int h) {
        if(cleanFbo!=0 && width==w && height==h)return;
        release();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldTex=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            width=w;height=h;
            cleanTexture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,cleanTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            cleanFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,cleanFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,cleanTexture,0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Clean capture framebuffer incomplete");
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTex);
        }
    }
    static void release() {
        afterPresent();
        if(cleanFbo!=0)GL30.glDeleteFramebuffers(cleanFbo);
        if(cleanTexture!=0)GL11.glDeleteTextures(cleanTexture);
        cleanFbo=cleanTexture=0;announced=false;
    }
    static boolean hasResources() { return cleanFbo!=0 || armed; }
}
