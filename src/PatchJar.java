import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchJar implements Opcodes {
    static int wrappers;
    static final String OVERLAY = "streamproof/Overlay";
    public static void main(String[] args) throws Exception {
        try (ZipFile input = new ZipFile(args[0]); ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]))) {
            Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().matches("META-INF/.*\\.(SF|RSA|DSA)")) continue;
                byte[] bytes = read(input.getInputStream(e));
                if (e.getName().startsWith("keystrokesmod/") && e.getName().endsWith(".class")) bytes = patch(bytes);
                out.putNextEntry(new ZipEntry(e.getName())); out.write(bytes); out.closeEntry();
            }
            final Path root = Paths.get(args[2]);
            List<Path> files = new ArrayList<>();
            Files.walk(root.resolve("streamproof")).filter(Files::isRegularFile).forEach(files::add);
            for (Path p : files) {
                out.putNextEntry(new ZipEntry(root.relativize(p).toString().replace('\\','/')));
                out.write(Files.readAllBytes(p)); out.closeEntry();
            }
        }
        System.out.println("Patched render entrypoints: " + wrappers);
    }
    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n=in.read(b))>=0) out.write(b,0,n); return out.toByteArray();
    }
    static byte[] patch(byte[] bytes) {
        ClassNode c = new ClassNode(); new ClassReader(bytes).accept(c,0); boolean changed = false;
        if(c.name.equals("keystrokesmod/client/module/modules/player/AutoTool")) { patchAutoTool(c); changed=true; }
        for (MethodNode m : new ArrayList<MethodNode>(c.methods)) {
            if(c.name.equals("keystrokesmod/client/tweaker/ASMTransformerClass") && m.name.equals("<init>")) {
                for(AbstractInsnNode i:m.instructions.toArray()) if(i.getOpcode()==RETURN) {
                    InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,0));
                    add.add(new TypeInsnNode(NEW,"streamproof/DisplayTransformer"));add.add(new InsnNode(DUP));
                    add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/DisplayTransformer","<init>","()V",false));
                    add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addTransformer","(Lkeystrokesmod/client/tweaker/transformers/Transformer;)V",false));
                    add.add(new VarInsnNode(ALOAD,0));
                    add.add(new TypeInsnNode(NEW,"streamproof/BowTelemetryTransformer")); add.add(new InsnNode(DUP));
                    add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/BowTelemetryTransformer","<init>","()V",false));
                    add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addTransformer","(Lkeystrokesmod/client/tweaker/transformers/Transformer;)V",false));
                    add.add(new VarInsnNode(ALOAD,0));
                    add.add(new TypeInsnNode(NEW,"streamproof/AutoToolTransformer")); add.add(new InsnNode(DUP));
                    add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/AutoToolTransformer","<init>","()V",false));
                    add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addTransformer","(Lkeystrokesmod/client/tweaker/transformers/Transformer;)V",false));
                    add.add(new VarInsnNode(ALOAD,0));
                    add.add(new TypeInsnNode(NEW,"streamproof/InputSoundTransformer")); add.add(new InsnNode(DUP));
                    add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/InputSoundTransformer","<init>","()V",false));
                    add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addTransformer","(Lkeystrokesmod/client/tweaker/transformers/Transformer;)V",false));
                    m.instructions.insertBefore(i,add);changed=true;
                }
            }
            if (c.name.equals("keystrokesmod/client/main/Raven") && m.name.equals("init") && m.desc.equals("()V")) {
                m.instructions.insert(new MethodInsnNode(INVOKESTATIC,"streamproof/StreamProof","initialize","()V",false));
                changed=true;
            }
            if (c.name.equals("keystrokesmod/client/utils/Utils$Player") && m.name.equals("sendMessageToSelf") && m.desc.equals("(Ljava/lang/String;)V")) {
                m.name = "sp$sendMessageToSelf";
                MethodNode quiet = new MethodNode(ASM9, ACC_PUBLIC|ACC_STATIC, "sendMessageToSelf", m.desc, null, null);
                quiet.visitCode(); quiet.visitFieldInsn(GETSTATIC,OVERLAY,"active","Z");
                Label normal = new Label(); quiet.visitJumpInsn(IFEQ,normal); quiet.visitInsn(RETURN);
                quiet.visitLabel(normal);quiet.visitFrame(F_SAME,0,null,0,null);quiet.visitVarInsn(ALOAD,0);
                quiet.visitMethodInsn(INVOKESTATIC,c.name,m.name,m.desc,false);quiet.visitInsn(RETURN);quiet.visitMaxs(0,0);quiet.visitEnd();
                c.methods.add(quiet);changed=true;
            }
            if (c.name.equals("keystrokesmod/client/module/ModuleManager") && m.name.equals("<init>")) {
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i instanceof FieldInsnNode && i.getOpcode()==PUTSTATIC && ((FieldInsnNode)i).name.equals("initialized")) {
                        InsnList add = new InsnList(); add.add(new VarInsnNode(ALOAD,0));
                        add.add(new TypeInsnNode(NEW,"streamproof/StreamProof")); add.add(new InsnNode(DUP));
                        add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/StreamProof","<init>","()V",false));
                        add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addModule","(Lkeystrokesmod/client/module/Module;)V",false));
                        add.add(new VarInsnNode(ALOAD,0));
                        add.add(new TypeInsnNode(NEW,"streamproof/BowAim")); add.add(new InsnNode(DUP));
                        add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/BowAim","<init>","()V",false));
                        add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addModule","(Lkeystrokesmod/client/module/Module;)V",false));
                        add.add(new VarInsnNode(ALOAD,0));
                        add.add(new TypeInsnNode(NEW,"streamproof/RodAim")); add.add(new InsnNode(DUP));
                        add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/RodAim","<init>","()V",false));
                        add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addModule","(Lkeystrokesmod/client/module/Module;)V",false));
                        add.add(new VarInsnNode(ALOAD,0));
                        add.add(new TypeInsnNode(NEW,"streamproof/InputSounds")); add.add(new InsnNode(DUP));
                        add.add(new MethodInsnNode(INVOKESPECIAL,"streamproof/InputSounds","<init>","()V",false));
                        add.add(new MethodInsnNode(INVOKESPECIAL,c.name,"addModule","(Lkeystrokesmod/client/module/Module;)V",false));
                        m.instructions.insertBefore(i.getPrevious(),add); changed=true;
                    }
                }
            }
            if (c.name.equals("keystrokesmod/client/module/Module") && m.name.equals("enable")) {
                String name=m.name; m.name="sp$enable";
                MethodNode guard=new MethodNode(ASM9,ACC_PUBLIC,name,"()V",null,null);
                guard.visitCode(); guard.visitVarInsn(ALOAD,0);
                guard.visitMethodInsn(INVOKESTATIC,"streamproof/StreamProof","allowEnable","(Lkeystrokesmod/client/module/Module;)Z",false);
                Label yes=new Label(); guard.visitJumpInsn(IFNE,yes); guard.visitInsn(RETURN); guard.visitLabel(yes);
                guard.visitFrame(F_SAME,0,null,0,null); guard.visitVarInsn(ALOAD,0);
                guard.visitMethodInsn(INVOKEVIRTUAL,c.name,m.name,"()V",false); guard.visitInsn(RETURN); guard.visitMaxs(0,0); guard.visitEnd();
                c.methods.add(guard); changed=true;
            }
            if (c.name.endsWith("/Nametags") || c.name.endsWith("/ExplicitB9NameTags")) {
                for(AbstractInsnNode i:m.instructions.toArray()) if(i instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)i;
                    if(call.name.equals("setCanceled") && call.desc.equals("(Z)V")) {
                        call.setOpcode(INVOKESTATIC); call.owner=OVERLAY; call.name="cancelEvent";
                        call.desc="(Lnet/minecraftforge/fml/common/eventhandler/Event;Z)V"; call.itf=false; changed=true;
                    }
                }
            }
            boolean healthModule=c.name.endsWith("/Nametags") || c.name.endsWith("/ExplicitB9NameTags") || c.name.endsWith("/PlayerESP");
            if (healthModule && m.name.equals("<init>")) {
                for (AbstractInsnNode i:m.instructions.toArray()) if (i.getOpcode()==RETURN) {
                    InsnList add=new InsnList(); add.add(new VarInsnNode(ALOAD,0));
                    add.add(new MethodInsnNode(INVOKESTATIC,"streamproof/HealthData","attach","(Lkeystrokesmod/client/module/Module;)V",false));
                    m.instructions.insertBefore(i,add); changed=true;
                }
            }
            if (healthModule || c.name.equals("keystrokesmod/client/utils/Utils$HUD")) {
                for (AbstractInsnNode i:m.instructions.toArray()) if (i instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)i;
                    String reader=call.name.equals("func_110143_aJ") ? "health" : call.name.equals("func_110138_aP") ? "maximum"
                        : call.name.equals("func_110139_bj") ? "absorption" : null;
                    if (reader==null || !call.desc.equals("()F") || call.getOpcode()!=INVOKEVIRTUAL) continue;
                    boolean esp=c.name.endsWith("Utils$HUD");
                    if (esp && reader.equals("absorption")) continue;
                    if (!esp) m.instructions.insertBefore(call,new VarInsnNode(ALOAD,0));
                    call.setOpcode(INVOKESTATIC); call.owner="streamproof/HealthData";
                    call.name=esp ? (reader.equals("health") ? "espHealth" : "espMaximum") : reader;
                    call.desc=esp ? "(Ljava/lang/Object;)F" : "(Ljava/lang/Object;Lkeystrokesmod/client/module/Module;)F";
                    call.itf=false; changed=true;
                }
            }
            for(AbstractInsnNode ins:m.instructions.toArray()) if(ins instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)ins;
                String observer=call.owner.equals("net/minecraft/client/settings/KeyBinding") && call.name.equals("func_74510_a") && call.desc.equals("(IZ)V")
                    ? "automatedKey" : c.name.endsWith("/AutoPlace") && call.owner.equals("keystrokesmod/client/utils/Utils$Client")
                        && call.name.equals("setMouseButtonState") && call.desc.equals("(IZ)V") ? "placeMouse" : null;
                if(observer!=null) {
                    m.instructions.insertBefore(ins,new InsnNode(DUP2));
                    m.instructions.insert(ins,new MethodInsnNode(INVOKESTATIC,"streamproof/InputSounds",observer,"(IZ)V",false)); changed=true;
                }
            }
            if (shouldWrap(c.name,m)) {
                wrap(c,m); wrappers++; changed=true;
                System.out.println("Redirect: "+c.name+"."+m.name);
            }
        }
        if(!changed)return bytes;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(writer); return writer.toByteArray();
    }
    static void patchAutoTool(ClassNode c) {
        int constructors=0,renders=0,finishes=0;
        for(MethodNode m:c.methods) {
            if(m.name.equals("<init>")) for(AbstractInsnNode ins:m.instructions.toArray()) if(ins.getOpcode()==RETURN) {
                InsnList add=new InsnList(); add.add(new VarInsnNode(ALOAD,0));
                add.add(new MethodInsnNode(INVOKESTATIC,"streamproof/AutoToolHooks","attach","(Lkeystrokesmod/client/module/Module;)V",false));
                m.instructions.insertBefore(ins,add); constructors++;
            }
            if(m.name.equals("onRenderTick") && m.desc.equals("(Lnet/minecraftforge/fml/common/gameevent/TickEvent$RenderTickEvent;)V")) {
                toolDelegate(m,"render",true); renders++;
            }
            if(m.name.equals("finishMining") && m.desc.equals("()V")) { toolDelegate(m,"finish",false); finishes++; }
        }
        if(constructors!=1 || renders!=1 || finishes!=1) throw new IllegalStateException("Unsupported Auto Tool layout");
        toolEntry(c,"onDisable",null,"finish");
        toolEntry(c,"onEnable",null,"finish");
        toolEntry(c,"sp$toolTick","net/minecraftforge/fml/common/gameevent/TickEvent$ClientTickEvent","tick");
        toolEntry(c,"sp$toolMouse","net/minecraftforge/client/event/MouseEvent","mouse");
        toolEntry(c,"sp$toolKey","net/minecraftforge/fml/common/gameevent/InputEvent$KeyInputEvent","key");
    }
    static void toolEntry(ClassNode c,String name,String event,String target) {
        String desc=event==null ? "()V" : "(L"+event+";)V";
        for(MethodNode old:c.methods) if(old.name.equals(name) && old.desc.equals(desc)) throw new IllegalStateException("Existing Auto Tool hook: "+name);
        MethodNode method=new MethodNode(ASM9,ACC_PUBLIC,name,desc,null,null);
        if(event!=null) {
            AnnotationNode annotation=new AnnotationNode("Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;");
            annotation.values=new ArrayList<>(); annotation.values.add("priority");
            annotation.values.add(new String[]{"Lnet/minecraftforge/fml/common/eventhandler/EventPriority;","HIGHEST"});
            method.visibleAnnotations=new ArrayList<>(); method.visibleAnnotations.add(annotation);
        }
        toolDelegate(method,target,event!=null); c.methods.add(method);
    }
    static void toolDelegate(MethodNode method,String target,boolean event) {
        method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables=null;
        method.instructions.add(new VarInsnNode(ALOAD,0));
        if(event) method.instructions.add(new VarInsnNode(ALOAD,1));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC,"streamproof/AutoToolHooks",target,
            "(Lkeystrokesmod/client/module/Module;"+(event ? method.desc.substring(1) : ")V"),false));
        method.instructions.add(new InsnNode(RETURN)); method.maxStack=2; method.maxLocals=event ? 2 : 1;
    }
    static boolean subscribed(MethodNode m) {
        if(m.visibleAnnotations!=null)for(AnnotationNode a:m.visibleAnnotations)if(a.desc.endsWith("/SubscribeEvent;"))return true;
        return false;
    }
    static boolean shouldWrap(String owner,MethodNode m) {
        if ((m.access&(ACC_STATIC|ACC_ABSTRACT|ACC_NATIVE))!=0 || !m.desc.endsWith(")V"))return false;
        if(m.name.equals("func_73863_a") && m.desc.equals("(IIF)V"))return true;
        if(!subscribed(m))return false;
        if(m.desc.contains("/RenderWorldLastEvent;") && !owner.endsWith("/Freecam"))return true;
        if(m.desc.contains("/RenderGameOverlayEvent;"))return true;
        if(m.desc.contains("/RenderLivingEvent$Specials$Pre;") && owner.endsWith("/Nametags"))return true;
        if(m.desc.contains("/TickEvent$RenderTickEvent;")) {
            return owner.endsWith("/HUD") || owner.endsWith("/BridgeInfo") || owner.endsWith("/BedwarsOverlay")
                || owner.endsWith("/DebugInfoRenderer") || owner.endsWith("/KeyStrokeRenderer") || owner.endsWith("/SafeWalk");
        }
        return false;
    }
    static void wrap(ClassNode c,MethodNode body) {
        String original=body.name; body.name="sp$draw$"+original;
        MethodNode wrapper=new MethodNode(ASM9,body.access,original,body.desc,body.signature,body.exceptions.toArray(new String[0]));
        wrapper.visibleAnnotations=body.visibleAnnotations; body.visibleAnnotations=null;
        wrapper.invisibleAnnotations=body.invisibleAnnotations; body.invisibleAnnotations=null;
        Type[] args=Type.getArgumentTypes(body.desc);
        List<Object> locals=new ArrayList<>(); locals.add(c.name); int slot=1;
        for(Type t:args) {
            switch(t.getSort()) {
                case Type.BOOLEAN:case Type.BYTE:case Type.CHAR:case Type.SHORT:case Type.INT:locals.add(INTEGER);break;
                case Type.FLOAT:locals.add(FLOAT);break;
                case Type.LONG:locals.add(LONG);break;
                case Type.DOUBLE:locals.add(DOUBLE);break;
                default:locals.add(t.getInternalName());
            }
            slot+=t.getSize();
        }
        int token=slot, error=slot+1; locals.add(INTEGER);
        Label start=new Label(),finish=new Label(),handler=new Label(),skip=new Label();
        wrapper.visitCode();
        wrapper.visitMethodInsn(INVOKESTATIC,OVERLAY,"begin","()I",false);wrapper.visitVarInsn(ISTORE,token);
        wrapper.visitVarInsn(ILOAD,token);wrapper.visitJumpInsn(IFLT,skip);
        wrapper.visitLabel(start);wrapper.visitVarInsn(ALOAD,0);
        int argSlot=1;for(Type t:args){wrapper.visitVarInsn(t.getOpcode(ILOAD),argSlot);argSlot+=t.getSize();}
        wrapper.visitMethodInsn((body.access&ACC_PRIVATE)!=0?INVOKESPECIAL:INVOKEVIRTUAL,c.name,body.name,body.desc,false);
        wrapper.visitLabel(finish);wrapper.visitVarInsn(ILOAD,token);
        wrapper.visitMethodInsn(INVOKESTATIC,OVERLAY,"end","(I)V",false);
        wrapper.visitLabel(skip);wrapper.visitFrame(F_FULL,locals.size(),locals.toArray(),0,new Object[0]);wrapper.visitInsn(RETURN);
        wrapper.visitLabel(handler);wrapper.visitFrame(F_FULL,locals.size(),locals.toArray(),1,new Object[]{"java/lang/Throwable"});
        wrapper.visitVarInsn(ASTORE,error);wrapper.visitVarInsn(ILOAD,token);
        wrapper.visitMethodInsn(INVOKESTATIC,OVERLAY,"end","(I)V",false);wrapper.visitVarInsn(ALOAD,error);wrapper.visitInsn(ATHROW);
        wrapper.visitTryCatchBlock(start,finish,handler,null);wrapper.visitMaxs(0,0);wrapper.visitEnd();c.methods.add(wrapper);
    }
}
