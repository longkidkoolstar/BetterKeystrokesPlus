import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public class VerifyBuild {
    public static void main(String[] args) throws Exception {
        int classes=0, methods=0, redirects=0, healthSettings=0, healthReads=0, bowModules=0, rodModules=0, rodEvents=0;
        try(ZipFile jar=new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> es=jar.entries();
            while(es.hasMoreElements()) {
                ZipEntry e=es.nextElement();
                if(e.getName().startsWith("streamproof/") && (e.getName().contains("Probe") || e.getName().contains("Benchmark")))
                    throw new AssertionError("Test class was packaged in runtime JAR: "+e.getName());
                if(!e.getName().endsWith(".class") || !(e.getName().startsWith("keystrokesmod/") || e.getName().startsWith("streamproof/")))continue;
                ClassNode c=new ClassNode();new ClassReader(PatchJar.read(jar.getInputStream(e))).accept(c,ClassReader.EXPAND_FRAMES);
                boolean changed=c.name.startsWith("streamproof/") || c.name.endsWith("/Module") || c.name.endsWith("/ModuleManager") || c.name.endsWith("/Raven") || c.name.endsWith("/ASMTransformerClass");
                for(MethodNode m:c.methods) {
                    if(m.name.startsWith("sp$"))changed=true;
                    for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode
                        && ((MethodInsnNode)i).owner.startsWith("streamproof/"))changed=true;
                }
                if(!changed)continue;
                classes++;
                for(MethodNode m:c.methods) {
                    if (c.name.equals("streamproof/RodAim") && PatchJar.subscribed(m))rodEvents++;
                    if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);methods++;
                    for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode) {
                        MethodInsnNode call=(MethodInsnNode)i;
                        if(call.owner.equals("streamproof/Overlay") && call.name.equals("begin"))redirects++;
                        if(call.owner.equals("streamproof/HealthData")) {
                            if(call.name.equals("attach"))healthSettings++; else healthReads++;
                        }
                        if(c.name.endsWith("/ModuleManager") && call.owner.equals("streamproof/BowAim") && call.name.equals("<init>"))bowModules++;
                        if(c.name.endsWith("/ModuleManager") && call.owner.equals("streamproof/RodAim") && call.name.equals("<init>"))rodModules++;
                        if((c.name.endsWith("/Nametags") || c.name.endsWith("/ExplicitB9NameTags") || c.name.endsWith("Utils$HUD"))
                            && (call.name.equals("func_110143_aJ") || call.name.equals("func_110138_aP") || call.name.equals("func_110139_bj")))
                            throw new AssertionError("Unpatched health read in "+c.name+"."+m.name);
                    }
                }
            }
        }
        if(redirects!=19)throw new AssertionError("Expected 19 drawing wrappers, got "+redirects);
        if(healthSettings!=3 || healthReads<10 || bowModules!=1 || rodModules!=1 || rodEvents!=4)throw new AssertionError("Missing feature hooks: "+healthSettings+" / "+healthReads+" / "+bowModules+" / "+rodModules+" / "+rodEvents);
        System.out.println("PASS bytecode analysis: "+classes+" modified/new classes, "+methods+" methods, "+redirects+" render wrappers");
        System.out.println("PASS feature integration: "+healthSettings+" health selectors, "+healthReads+" health reads, one Bow Aim module, one Rod Aim module");
    }
}
