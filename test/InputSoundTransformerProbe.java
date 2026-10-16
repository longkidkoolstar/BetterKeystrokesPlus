import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import streamproof.InputSoundTransformer;
public final class InputSoundTransformerProbe {
    public static void main(String[] args)throws Exception {
        try(ZipFile jar=new ZipFile(args[0])) {
            ClassNode node=new ClassNode();new ClassReader(PatchJar.read(jar.getInputStream(jar.getEntry("ave.class")))).accept(node,0);
            new InputSoundTransformer().transform(node,"net.minecraft.client.Minecraft");
            int count=0;
            for(MethodNode m:node.methods) for(AbstractInsnNode ins:m.instructions.toArray()) if(ins instanceof MethodInsnNode && ((MethodInsnNode)ins).owner.equals("streamproof/InputSounds")) {
                if(!m.desc.equals("()V") || !(m.name.equals("aw") || m.name.equals("ax")))throw new AssertionError("Unexpected click method "+m.name);
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,m);count++;
            }
            if(count!=2)throw new AssertionError("Expected real left/right click entries: "+count);
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(w);new ClassReader(w.toByteArray());
            System.out.println("PASS Minecraft input sound hooks: two actual 1.8.9 click entries, verified bytecode");
        }
    }
}
