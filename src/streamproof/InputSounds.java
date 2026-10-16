package streamproof;

import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/** Mirrors sound events to the optional companion, without changing game or OS input. */
public final class InputSounds extends Module {
    private static InputSounds instance;
    private final DescriptionSetting status=new DescriptionSetting("Start the Mechvibes bridge copy; enable here to connect");
    private InputSoundSender sender;
    private InputSoundState sound;
    private Object minecraft,player,world;
    private long lastHeartbeat;
    private boolean live;
    public InputSounds() {
        super("Mechvibes Bridge",ModuleCategory.client);
        withDescription("Local click/key sounds for actual game actions and automated keys; requires the companion copy.");
        registerSetting(status); instance=this;
    }
    @Override public void onEnable() {
        sender=new InputSoundSender(); sound=new InputSoundState(sender); sender.session(sound.session()); lastHeartbeat=0; refresh();
    }
    @Override public void onDisable() {
        live=false;
        if(sound!=null) sound.mode(false,null);
        if(sender!=null) sender.close();
        sender=null; sound=null; player=null; world=null;
    }
    @Override public void update() { refresh(); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void frame(TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.START && isEnabled()) refresh();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void keyboard(InputEvent.KeyInputEvent event) {
        if(!isEnabled() || sound==null) return;
        if(live && !Keyboard.isRepeatEvent()) sound.key(Keyboard.getEventKey(),Keyboard.getEventKeyState());
        refresh();
    }
    private void refresh() {
        if(sound==null) return;
        try {
            if(minecraft==null) minecraft=GameAccess.call(Class.forName("net.minecraft.client.Minecraft"),"func_71410_x|getMinecraft");
            Object p=GameAccess.get(minecraft,"field_71439_g|thePlayer"),w=GameAccess.get(minecraft,"field_71441_e|theWorld");
            boolean next=p!=null && w!=null && GameAccess.get(minecraft,"field_71462_r|currentScreen")==null
                && Boolean.TRUE.equals(GameAccess.get(minecraft,"field_71415_G|inGameHasFocus"))
                && Boolean.TRUE.equals(GameAccess.call(p,"func_70089_S|isEntityAlive"));
            if(p!=player || w!=world) { sound.mode(false,null); live=false; player=p; world=w; }
            long now=System.nanoTime();
            if(next!=live || lastHeartbeat==0 || now-lastHeartbeat>=100000000L) {
                boolean[] physical=null;
                if(next && !live) {
                    physical=new boolean[256];
                    if(Keyboard.isCreated()) for(int i=1;i<256;i++) physical[i]=Keyboard.isKeyDown(i);
                }
                live=next; sound.mode(live,physical); lastHeartbeat=now;
            }
            status.setDesc(!sender.error().isEmpty() ? "Transport stopped: "+sender.error()
                : sender.connected(now) ? (live ? "Connected: game click/key sounds active" : "Connected: physical sounds outside gameplay")
                : "Waiting for the Mechvibes bridge copy (local connection)");
        } catch(ReflectiveOperationException | RuntimeException e) {
            System.err.println("[BetterKeystrokes+] Mechvibes bridge disabled: "+e);
            disable(); status.setDesc("Disabled: game access failed; see game log");
        }
    }
    public static void click(int button) {
        InputSounds bridge=instance;
        if(bridge!=null && bridge.live && bridge.isEnabled() && bridge.sound!=null) bridge.sound.click(button);
    }
    public static void automatedKey(int code,boolean down) {
        InputSounds bridge=instance;
        if(bridge!=null && bridge.live && bridge.isEnabled() && bridge.sound!=null && code>0) bridge.sound.key(code,down);
    }
    public static void placeMouse(int button,boolean down) { if(down) click(button); }
}
