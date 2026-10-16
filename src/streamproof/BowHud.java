package streamproof;

import org.lwjgl.opengl.GL11;

/** One capture-routed drawing entrypoint, also usable by the hidden GPU probe. */
public final class BowHud {
    private BowHud() {}
    public interface Painter { void draw() throws ReflectiveOperationException; }
    public static void paint(Painter painter) throws ReflectiveOperationException {
        int token=Overlay.begin(); if (token<0)return;
        boolean pushed=false,projection=false,model=false; int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); pushed=true;
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); projection=true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); model=true;
            painter.draw();
        } finally {
            if(model) { GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); }
            if(projection) { GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); }
            if(pushed)GL11.glPopAttrib(); GL11.glMatrixMode(mode); Overlay.end(token);
        }
    }
}
