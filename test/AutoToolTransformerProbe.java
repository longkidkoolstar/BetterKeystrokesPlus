import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import streamproof.AutoToolTransformer;

public final class AutoToolTransformerProbe {
    private static int hooks(ClassNode node) {
        int count=0;
        for(MethodNode m:node.methods) for(AbstractInsnNode ins:m.instructions.toArray())
            if(ins instanceof MethodInsnNode && ((MethodInsnNode)ins).owner.equals("streamproof/AutoToolHooks"))count++;
        return count;
    }
    public static void main(String[] args) throws Exception {
        try(ZipFile jar=new ZipFile(args[0])) {
            ClassNode node=new ClassNode(); new ClassReader(PatchJar.read(jar.getInputStream(jar.getEntry("bda.class")))).accept(node,0);
            AutoToolTransformer transformer=new AutoToolTransformer(); transformer.transform(node,"net.minecraft.client.multiplayer.PlayerControllerMP");
            if(hooks(node)!=3)throw new AssertionError("Expected exactly attack/clickBlock/damageBlock hooks");
            int attacks=0,blocks=0;
            for(MethodNode m:node.methods) {
                MethodInsnNode hook=null; int hookAt=-1,syncAt=-1,index=0;
                for(AbstractInsnNode ins:m.instructions.toArray()) {
                    if(ins instanceof MethodInsnNode) {
                        MethodInsnNode call=(MethodInsnNode)ins;
                        if(call.owner.equals("streamproof/AutoToolHooks")) { hook=call; hookAt=index; }
                        if(call.owner.equals("bda") && call.name.equals("n") && call.desc.equals("()V") && syncAt<0)syncAt=index;
                    }
                    index++;
                }
                if(hook==null) continue;
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,m);
                if(hook.name.equals("beforeAttack")) {
                    if(!m.desc.equals("(Lwn;Lpk;)V") || syncAt<0 || hookAt>=syncAt)throw new AssertionError("Restore must precede held-item sync and attack packet");
                    attacks++;
                } else {
                    if(!m.desc.equals("(Lcj;Lcq;)Z") || !(m.name.equals("b") || m.name.equals("c")))throw new AssertionError("Unrelated block method patched");
                    blocks++;
                }
            }
            if(attacks!=1 || blocks!=2)throw new AssertionError("Incorrect action coverage");
            transformer.transform(node,"net.minecraft.client.multiplayer.PlayerControllerMP");
            if(hooks(node)!=3)throw new AssertionError("Repeated transform duplicated hooks");
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
            ClassNode reloaded=new ClassNode();new ClassReader(writer.toByteArray()).accept(reloaded,ClassReader.EXPAND_FRAMES);
            for(MethodNode m:reloaded.methods)if(m.desc.equals("(Lwn;Lpk;)V") || (m.name.equals("b") || m.name.equals("c")) && m.desc.equals("(Lcj;Lcq;)Z"))
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(reloaded.name,m);
            System.out.println("PASS Auto Tool action transformer: actual 1.8.9 restore-before-attack sync, two mining entries, idempotence and serialized bytecode");
        }
    }
}
