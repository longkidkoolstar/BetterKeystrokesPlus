package streamproof;

import java.util.IdentityHashMap;
import java.util.Map;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;

public final class HealthData {
    private static final Map<Module, Options> options=new IdentityHashMap<>();
    private static Module esp;
    private static boolean warned;
    private static final class Options {
        final SliderSetting source=new SliderSetting("Health source (Entity/Tab)",1,1,2,1);
        final TickSetting hearts=new TickSetting("Tab scores are hearts",false);
    }
    public static void attach(Module module) {
        if (options.containsKey(module)) return;
        Options o=new Options(); options.put(module,o);
        module.registerSetting(o.source); module.registerSetting(o.hearts);
        module.registerSetting(new DescriptionSetting("1 Entity / 2 Tab; missing Tab score uses Entity"));
        if (module.getClass().getSimpleName().equals("PlayerESP")) esp=module;
    }
    private static Float tab(Object player,Module module) throws ReflectiveOperationException {
        Options o=options.get(module);
        return o!=null && o.source.getInput()>=2 ? TabHealth.read(player,o.hearts.isToggled()) : null;
    }
    private static void warn(Exception e) {
        if (!warned) { warned=true; System.err.println("[BetterKeystrokes+] Health reader: "+e); }
    }
    public static float health(Object player,Module module) {
        try {
            Float score=tab(player,module);
            return score!=null ? score : (float)GameAccess.value(player,"func_110143_aJ|getHealth");
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            try { return (float)GameAccess.value(player,"func_110143_aJ|getHealth"); }
            catch (ReflectiveOperationException | RuntimeException failure) { return 0; }
        }
    }
    public static float maximum(Object player,Module module) {
        try { return Math.max(1,(float)GameAccess.value(player,"func_110138_aP|getMaxHealth")); }
        catch (ReflectiveOperationException | RuntimeException e) { warn(e); return 20; }
    }
    public static float absorption(Object player,Module module) {
        try {
            // Tab is already the server's complete value; do not add entity absorption twice.
            return tab(player,module)!=null ? 0 : (float)GameAccess.value(player,"func_110139_bj|getAbsorptionAmount");
        } catch (ReflectiveOperationException | RuntimeException e) { warn(e); return 0; }
    }
    public static float espHealth(Object player) { return Math.max(0,Math.min(maximum(player,esp),health(player,esp))); }
    public static float espMaximum(Object player) { return maximum(player,esp); }
}
