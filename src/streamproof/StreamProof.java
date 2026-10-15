package streamproof;

import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.main.Raven;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Experimental Windows-only overlay separation. Not a universal capture guarantee. */
public final class StreamProof extends Module {
    private static StreamProof instance;
    private static boolean hooksRegistered;
    private final DescriptionSetting status = new DescriptionSetting("Experimental: windowed mode; F9 toggles");
    private final SliderSetting overlayFps = new SliderSetting("Overlay FPS",30,10,240,5);
    private final TickSetting borderless = new TickSetting("Borderless fullscreen",false);
    private final TickSetting nativeFullscreen = new TickSetting("OBS native mode",false);
    public StreamProof() {
        super("Stream Proof", ModuleCategory.client);
        withKeycode(67); // F9
        withDescription("Experimental capture-excluded overlay. Scene-changing visuals are blocked.");
        registerSetting(status);
        registerSetting(overlayFps);
        registerSetting(borderless);
        registerSetting(nativeFullscreen);
        instance = this;
    }
    public static void initialize() {
        if (!hooksRegistered) {
            MinecraftForge.EVENT_BUS.register(new Frames());
            hooksRegistered = true;
            System.out.println("[StreamProof] v5 frame hooks registered during mod initialization");
        }
    }
    @Override public void onEnable() {
        Overlay.refreshRate=(int)overlayFps.getInput();
        Overlay.nativeMode=nativeFullscreen.isToggled();
        if (!Overlay.prepare()) {
            this.enabled = false;
            status.setDesc(Overlay.status);
            warn(Overlay.status);
            return;
        }
        for (Module m : Raven.moduleManager.getModules()) {
            if (blocked(m) && m.isEnabled()) m.disable();
        }
    }
    @Override public void onDisable() {
        Overlay.stop();
        try { WindowMode.restore(); } catch (RuntimeException e) { e.printStackTrace(); }
    }
    @Override public void update() {
        Overlay.refreshRate=(int)overlayFps.getInput();
        try { WindowMode.apply(!Overlay.nativeMode && borderless.isToggled()); }
        catch (RuntimeException e) { borderless.setEnabled(false); e.printStackTrace(); }
        status.setDesc(Overlay.status);
    }
    @Override public void guiButtonToggled(TickSetting setting) {
        if(setting==nativeFullscreen && isEnabled()) { disable(); enable(); }
    }
    private static void warn(String reason) {
        System.err.println("[StreamProof] Mode OFF; ordinary visuals are capturable. " + reason);
        keystrokesmod.client.utils.Utils.Player.sendMessageToSelf("&cStream Proof OFF: " + reason);
    }
    private static void recoverFailure() {
        if (Overlay.active && Overlay.hasFailed() && instance != null) {
            String reason = Overlay.status;
            instance.disable();
            instance.status.setDesc(reason);
            warn(reason);
        }
    }
    public static boolean allowEnable(Module m) { return !Overlay.active || !blocked(m); }
    private static boolean blocked(Module m) {
        String n = m.getClass().getSimpleName();
        return n.equals("Fullbright") || n.equals("Chams")
            || n.equals("Freecam") || n.equals("SumoFences") || n.equals("NameHider")
            || n.equals("AntiShuffle") || n.equals("StringEncrypt")
            || n.equals("FPSSpoofer") || n.equals("ClientNameSpoof")
            || n.equals("FakeChat") || n.equals("ChatLogger") || n.equals("DuelsStats");
    }
    public static final class Frames {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void start(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.START) Overlay.startFrame();
        }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void end(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.END) { Overlay.endFrame(); recoverFailure(); }
        }
    }
}
