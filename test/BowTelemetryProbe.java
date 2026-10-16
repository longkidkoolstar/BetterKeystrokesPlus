import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import streamproof.BowTelemetryTransformer;

public final class BowTelemetryProbe {
    public static void main(String[] args) throws Exception {
        try(ZipFile jar=new ZipFile(args[0])) {
            ClassNode node=new ClassNode();new ClassReader(PatchJar.read(jar.getInputStream(jar.getEntry("bcy.class")))).accept(node,0);
            new BowTelemetryTransformer().transform(node,"net.minecraft.client.network.NetHandlerPlayClient");
            int hooks=0;
            for(MethodNode m:node.methods)for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)ins;
                if(c.owner.equals("streamproof/BowAim") && c.name.equals("observeGameState")) {
                    if(!m.desc.equals("(Lgm;)V"))throw new AssertionError("Hooked unrelated packet");
                    hooks++;
                }
            }
            if(hooks<1)throw new AssertionError("No diagnostic packet hook");
            for(MethodNode m:node.methods)if(m.desc.equals("(Lgm;)V"))new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,m);
            System.out.println("PASS diagnostic marker transformer: "+hooks+" hooks in actual 1.8.9 main-thread game-state handler");
        }
    }
}
