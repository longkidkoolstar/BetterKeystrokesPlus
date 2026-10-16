package streamproof;

import keystrokesmod.client.tweaker.transformers.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Observe vanilla's hit marker after its main-thread packet handler. No packet edits. */
public final class BowTelemetryTransformer implements Transformer {
    public String[] getClassName() { return new String[]{"net.minecraft.client.network.NetHandlerPlayClient"}; }
    public void transform(ClassNode node,String name) {
        int count=0;
        for (Object object:node.methods) {
            MethodNode method=(MethodNode)object;
            if (!(method.desc.equals("(Lgm;)V") || method.desc.equals("(Lnet/minecraft/network/play/server/S2BPacketChangeGameState;)V")))continue;
            for(AbstractInsnNode ins:method.instructions.toArray())if(ins.getOpcode()==Opcodes.RETURN) {
                InsnList add=new InsnList(); add.add(new VarInsnNode(Opcodes.ALOAD,1));
                add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"streamproof/BowAim","observeGameState","(Ljava/lang/Object;)V",false));
                method.instructions.insertBefore(ins,add);count++;
            }
        }
        if(count>0)System.setProperty("streamproof.bowTelemetryHook","true");
        System.out.println("[BetterKeystrokes+] Bow diagnostic marker hooks: "+count);
    }
}
