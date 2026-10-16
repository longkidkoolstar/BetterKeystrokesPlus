import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public class VerifyBuild {
    public static void main(String[] args) throws Exception {
        int classes=0, methods=0, redirects=0, healthSettings=0, healthReads=0, bowModules=0, rodModules=0, rodEvents=0, bowHud=0, telemetry=0;
        int toolTransformer=0,toolAttach=0,toolEvents=0,toolFinish=0,toolRender=0;
        int soundModules=0,soundTransformers=0,keyObservers=0,placementObservers=0,safeWalkObservers=0;
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
                    if(c.name.endsWith("/player/AutoTool") && PatchJar.subscribed(m))toolEvents++;
                    if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);methods++;
                    for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode) {
                        MethodInsnNode call=(MethodInsnNode)i;
                        if(call.owner.equals("streamproof/Overlay") && call.name.equals("begin")) {
                            if(c.name.equals("streamproof/BowHud"))bowHud++; else redirects++;
                        }
                        if(c.name.endsWith("/ASMTransformerClass") && call.owner.equals("streamproof/BowTelemetryTransformer") && call.name.equals("<init>"))telemetry++;
                        if(c.name.endsWith("/ASMTransformerClass") && call.owner.equals("streamproof/AutoToolTransformer") && call.name.equals("<init>"))toolTransformer++;
                        if(c.name.endsWith("/ASMTransformerClass") && call.owner.equals("streamproof/InputSoundTransformer") && call.name.equals("<init>"))soundTransformers++;
                        if(c.name.endsWith("/ModuleManager") && call.owner.equals("streamproof/InputSounds") && call.name.equals("<init>"))soundModules++;
                        if(call.owner.equals("net/minecraft/client/settings/KeyBinding") && call.name.equals("func_74510_a")) {
                            AbstractInsnNode next=call.getNext();
                            if(!(next instanceof MethodInsnNode) || !((MethodInsnNode)next).owner.equals("streamproof/InputSounds")
                                || !((MethodInsnNode)next).name.equals("automatedKey") || call.getPrevious().getOpcode()!=Opcodes.DUP2)
                                throw new AssertionError("Missing non-mutating key observer in "+c.name+"."+m.name);
                        }
                        if(call.owner.equals("streamproof/InputSounds")) {
                            if(call.name.equals("automatedKey")) { keyObservers++; if(c.name.endsWith("/SafeWalk"))safeWalkObservers++; }
                            if(call.name.equals("placeMouse")) { if(!c.name.endsWith("/AutoPlace"))throw new AssertionError("Duplicate generic placement observer"); placementObservers++; }
                        }
                        if(c.name.endsWith("/player/AutoTool") && call.owner.equals("streamproof/AutoToolHooks")) {
                            if(call.name.equals("attach"))toolAttach++;
                            if(call.name.equals("finish"))toolFinish++;
                            if(call.name.equals("render"))toolRender++;
                        }
                        if(c.name.endsWith("/player/AutoTool") && m.name.equals("onRenderTick") && !call.owner.equals("streamproof/AutoToolHooks"))
                            throw new AssertionError("Legacy Auto Tool render logic still active");
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
        if(redirects!=19 || bowHud!=1 || telemetry!=1)throw new AssertionError("Expected 19 legacy drawing wrappers, HUD and telemetry hook: "+redirects+" / "+bowHud+" / "+telemetry);
        if(healthSettings!=3 || healthReads<10 || bowModules!=1 || rodModules!=1 || rodEvents!=4)throw new AssertionError("Missing feature hooks: "+healthSettings+" / "+healthReads+" / "+bowModules+" / "+rodModules+" / "+rodEvents);
        if(toolTransformer!=1 || toolAttach!=1 || toolEvents!=4 || toolFinish!=3 || toolRender!=1)
            throw new AssertionError("Missing Auto Tool integration: "+toolTransformer+" / "+toolAttach+" / "+toolEvents+" / "+toolFinish+" / "+toolRender);
        if(soundModules!=1 || soundTransformers!=1 || keyObservers<20 || placementObservers!=2 || safeWalkObservers!=1)
            throw new AssertionError("Missing sound integration: "+soundModules+" / "+soundTransformers+" / "+keyObservers+" / "+placementObservers+" / "+safeWalkObservers);
        System.out.println("PASS bytecode analysis: "+classes+" modified/new classes, "+methods+" methods, "+redirects+" render wrappers");
        System.out.println("PASS feature integration: "+healthSettings+" health selectors, "+healthReads+" health reads, one Bow Aim module, one Rod Aim module");
        System.out.println("PASS Auto Tool integration: existing module/config settings, four input/tick events, lifecycle cleanup and action transformer");
        System.out.println("PASS Mechvibes integration: one opt-in module, Minecraft click transformer, "+keyObservers+" non-mutating key observers, SafeWalk and AutoPlace coverage");
    }
}
