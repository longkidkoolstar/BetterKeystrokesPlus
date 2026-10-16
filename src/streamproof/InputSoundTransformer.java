package streamproof;

import keystrokesmod.client.tweaker.transformers.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Observes real Minecraft click attempts, including held FastPlace repetition. */
public final class InputSoundTransformer implements Transformer {
    public String[] getClassName() { return new String[]{"net.minecraft.client.Minecraft"}; }
    public void transform(ClassNode node,String name) {
        int hooks=0;
        for(Object object:node.methods) {
            MethodNode m=(MethodNode)object;
            if(!m.desc.equals("()V")) continue;
            int button=m.name.equals("aw") || m.name.equals("func_147116_af") || m.name.equals("clickMouse") ? 0
                : m.name.equals("ax") || m.name.equals("func_147121_ag") || m.name.equals("rightClickMouse") ? 1 : -1;
            if(button<0) continue;
            boolean installed=false;
            for(AbstractInsnNode ins:m.instructions.toArray()) if(ins instanceof MethodInsnNode && ((MethodInsnNode)ins).owner.equals("streamproof/InputSounds"))installed=true;
            if(installed) continue;
            InsnList add=new InsnList();add.add(new InsnNode(button==0 ? Opcodes.ICONST_0 : Opcodes.ICONST_1));
            add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"streamproof/InputSounds","click","(I)V",false));m.instructions.insert(add);hooks++;
        }
        System.out.println("[BetterKeystrokes+] Mechvibes game click hooks: "+hooks);
    }
}
