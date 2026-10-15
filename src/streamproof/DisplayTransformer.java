package streamproof;

import keystrokesmod.client.tweaker.transformers.Transformer;
import org.objectweb.asm.tree.*;

/** Route Minecraft's display update through a reversible Java/OpenGL presentation boundary. */
public final class DisplayTransformer implements Transformer {
    public String[] getClassName() { return new String[]{"net.minecraft.client.Minecraft"}; }
    public void transform(ClassNode node,String name) {
        int count=0;
        for(Object item:node.methods) {
            MethodNode method=(MethodNode)item;
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(!(instruction instanceof MethodInsnNode))continue;
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.owner.equals("org/lwjgl/opengl/Display") && call.name.equals("update") && call.desc.equals("()V")) {
                    call.owner="streamproof/NativeCapture";call.name="updateDisplay";count++;
                }
            }
        }
        if(count>0)System.setProperty("streamproof.displayHook","true");
        System.out.println("[StreamProof] v5 Minecraft display boundaries patched: "+count);
    }
}
