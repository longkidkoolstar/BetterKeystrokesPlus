package streamproof;

import keystrokesmod.client.tweaker.transformers.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Restore before vanilla's held-item synchronization; mining selection precedes dig calculations. */
public final class AutoToolTransformer implements Transformer {
    public String[] getClassName() { return new String[]{"net.minecraft.client.multiplayer.PlayerControllerMP"}; }
    public void transform(ClassNode node,String name) {
        int attacks=0,blocks=0;
        for(Object object:node.methods) {
            MethodNode method=(MethodNode)object;
            boolean attack=method.desc.equals("(Lwn;Lpk;)V") || method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V");
            boolean block=(method.name.equals("b") || method.name.equals("c")) && method.desc.equals("(Lcj;Lcq;)Z")
                || (method.name.equals("func_180511_b") || method.name.equals("clickBlock") || method.name.equals("func_180512_c") || method.name.equals("onPlayerDamageBlock"))
                    && method.desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z");
            if(!attack && !block) continue;
            boolean installed=false;
            for(AbstractInsnNode ins:method.instructions.toArray()) if(ins instanceof MethodInsnNode && ((MethodInsnNode)ins).owner.equals("streamproof/AutoToolHooks")) installed=true;
            if(installed) continue;
            InsnList add=new InsnList(); add.add(new VarInsnNode(Opcodes.ALOAD,1));
            if(attack) { add.add(new VarInsnNode(Opcodes.ALOAD,2)); attacks++; } else blocks++;
            add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"streamproof/AutoToolHooks",attack ? "beforeAttack" : "beforeBlock",
                attack ? "(Ljava/lang/Object;Ljava/lang/Object;)V" : "(Ljava/lang/Object;)V",false));
            method.instructions.insert(add);
        }
        System.out.println("[BetterKeystrokes+] Auto Tool action hooks: "+attacks+" attack / "+blocks+" block");
    }
}
