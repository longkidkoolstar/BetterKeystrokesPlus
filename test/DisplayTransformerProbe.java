import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import streamproof.DisplayTransformer;

public class DisplayTransformerProbe {
    public static void main(String[] args)throws Exception {
        try(ZipFile jar=new ZipFile(args[0])) {
            ClassNode node=new ClassNode();new ClassReader(PatchJar.read(jar.getInputStream(jar.getEntry("ave.class")))).accept(node,0);
            int original=0;for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)i;if(c.owner.equals("org/lwjgl/opengl/Display")&&c.name.equals("update")&&c.desc.equals("()V"))original++;
            }
            new DisplayTransformer().transform(node,"net.minecraft.client.Minecraft");
            int patched=0;for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)i;if(c.owner.equals("streamproof/NativeCapture")&&c.name.equals("updateDisplay"))patched++;
            }
            if(original<1 || patched!=original || !Boolean.getBoolean("streamproof.displayHook"))throw new AssertionError("Presentation hook coverage incomplete");
            System.out.println("PASS Minecraft 1.8.9 display transformer: "+patched+" display boundary patched on actual client bytecode");
        }
    }
}
