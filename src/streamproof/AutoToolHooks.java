package streamproof;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.*;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.modules.combat.LeftClicker;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Extension for the existing Auto Tool module/settings, without distributing its source. */
public final class AutoToolHooks {
    private static final Map<Module,AutoToolHooks> states=new WeakHashMap<>();
    private static Module instance;
    private final AutoToolState state=new AutoToolState();
    private final TickSetting guard=new TickSetting("Combat mining guard",true);
    private final SliderSetting hold=new SliderSetting("Combat hold (ms)",180,0,500,10);
    private final DescriptionSetting status=new DescriptionSetting("Combat guard: hold a block briefly after attacking a player");
    private Object minecraft,player,world,inventory;
    private AutoToolHooks(Module module) {
        module.registerSetting(guard); module.registerSetting(hold); module.registerSetting(status);
    }
    public static void attach(Module module) { states.put(module,new AutoToolHooks(module)); instance=module; }
    private static AutoToolHooks of(Module module) { return states.get(module); }
    public static void tick(Module module,TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.START && module.isEnabled()) pump(module,null,true);
    }
    public static void render(Module module,TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.START && module.isEnabled()) pump(module,null,false);
    }
    /** Runs before vanilla synchronizes the held slot and sends its attack packet. */
    public static void beforeAttack(Object attacker,Object target) {
        Module module=instance; AutoToolHooks hook=module==null ? null : of(module);
        if(hook==null || !module.isEnabled()) return;
        try {
            if(!hook.context() || attacker!=hook.player) return;
            if(Class.forName("net.minecraft.entity.player.EntityPlayer").isInstance(target)) hook.state.attacked(System.nanoTime());
            int restore=hook.state.stop(hook.slot(),hook.switchBack(module)); hook.apply(restore);
            hook.publish(module);
            hook.status.setDesc(restore>=0 ? "Attacking: original slot restored" : "Attacking; Auto Tool paused");
        } catch(ReflectiveOperationException | RuntimeException e) { hook.fail(module,e); }
    }
    /** Real block actions also refresh selection, before vanilla calculates mining speed. */
    public static void beforeBlock(Object position) {
        Module module=instance;
        if(module!=null && module.isEnabled()) pump(module,position,true);
    }
    public static void finish(Module module) {
        AutoToolHooks hook=of(module); if(hook==null) return;
        try { if(hook.context()) hook.apply(hook.state.stop(hook.slot(),hook.switchBack(module))); }
        catch(ReflectiveOperationException | RuntimeException e) { System.err.println("[BetterKeystrokes+] Auto Tool restore failed: "+e); }
        hook.state.reset(); hook.publish(module);
    }
    public static void mouse(Module module,MouseEvent event) {
        AutoToolHooks hook=of(module); if(hook==null || !module.isEnabled()) return;
        try {
            if(event.dwheel!=0 || event.buttonstate && hook.hotbarKey(event.button-100)) hook.manualInput();
            if(!event.buttonstate && event.button!=-1 && hook.attackCode()==event.button-100) pump(module,null,false);
        } catch(ReflectiveOperationException | RuntimeException e) { hook.fail(module,e); }
    }
    public static void key(Module module,InputEvent.KeyInputEvent event) {
        AutoToolHooks hook=of(module); if(hook==null || !module.isEnabled()) return;
        try {
            if(Keyboard.getEventKeyState() && hook.hotbarKey(Keyboard.getEventKey())) hook.manualInput();
            if(!Keyboard.getEventKeyState() && hook.attackCode()==Keyboard.getEventKey()) pump(module,null,false);
        } catch(ReflectiveOperationException | RuntimeException e) { hook.fail(module,e); }
    }
    private static void pump(Module module,Object actualPosition,boolean evaluate) {
        AutoToolHooks hook=of(module); if(hook==null) return;
        try { hook.run(module,actualPosition,evaluate); }
        catch(ReflectiveOperationException | RuntimeException e) { hook.fail(module,e); }
    }
    private boolean context() throws ReflectiveOperationException {
        if(minecraft==null) minecraft=GameAccess.call(Class.forName("net.minecraft.client.Minecraft"),"func_71410_x|getMinecraft");
        Object nextPlayer=GameAccess.get(minecraft,"field_71439_g|thePlayer");
        Object nextWorld=GameAccess.get(minecraft,"field_71441_e|theWorld");
        Object nextInventory=nextPlayer==null ? null : GameAccess.get(nextPlayer,"field_71071_by|inventory");
        if(nextPlayer!=player || nextWorld!=world || nextInventory!=inventory) {
            state.reset(); player=nextPlayer; world=nextWorld; inventory=nextInventory;
        }
        return player!=null && world!=null && inventory!=null;
    }
    private int slot() throws ReflectiveOperationException { return (int)GameAccess.number(inventory,"field_70461_c|currentItem"); }
    private boolean switchBack(Module module) { return ((TickSetting)module.getSettingByName("Hotkey back")).isToggled(); }
    private int attackCode() throws ReflectiveOperationException {
        if(minecraft==null) return 0;
        Object settings=GameAccess.get(minecraft,"field_71474_y|gameSettings");
        return code(GameAccess.get(settings,"field_74312_F|keyBindAttack"));
    }
    private static int code(Object binding) throws ReflectiveOperationException {
        return ((Number)GameAccess.call(binding,"func_151463_i|getKeyCode")).intValue();
    }
    private boolean hotbarKey(int input) throws ReflectiveOperationException {
        if(minecraft==null || input==0) return false;
        Object settings=GameAccess.get(minecraft,"field_71474_y|gameSettings");
        for(Object binding:(Object[])GameAccess.get(settings,"field_151456_ac|keyBindsHotbar")) if(code(binding)==input) return true;
        return false;
    }
    private boolean attackDown() throws ReflectiveOperationException {
        int key=attackCode();
        return key<0 ? Mouse.isCreated() && key+100>=0 && key+100<Mouse.getButtonCount() && Mouse.isButtonDown(key+100)
            : key>0 && key<Keyboard.KEYBOARD_SIZE && Keyboard.isCreated() && Keyboard.isKeyDown(key);
    }
    private void manualInput() throws ReflectiveOperationException {
        if(state.ownsSlot() || state.waiting() || attackDown()) state.manualSelection();
    }
    private void apply(int next) throws ReflectiveOperationException {
        if(next>=0 && next<9) GameAccess.field(inventory,"field_70461_c|currentItem").setInt(inventory,next);
    }
    private void run(Module module,Object actualPosition,boolean evaluate) throws ReflectiveOperationException {
        if(!context()) { publish(module); return; }
        boolean attack=attackDown();
        boolean allowed=GameAccess.get(minecraft,"field_71462_r|currentScreen")==null
            && Boolean.TRUE.equals(GameAccess.get(minecraft,"field_71415_G|inGameHasFocus"))
            && Boolean.TRUE.equals(GameAccess.call(player,"func_70089_S|isEntityAlive"))
            && !Boolean.TRUE.equals(GameAccess.call(player,"func_71039_bw|isUsingItem"));
        Module clicker=Raven.moduleManager==null ? null : Raven.moduleManager.getModuleByClazz(LeftClicker.class);
        if(clicker!=null && clicker.isEnabled() && !LeftClicker.breakBlocks.isToggled()) allowed=false;
        Object position=actualPosition;
        if(position==null) {
            Object hit=GameAccess.get(minecraft,"field_71476_x|objectMouseOver");
            String type=hit==null ? "MISS" : String.valueOf(GameAccess.get(hit,"field_72313_a|typeOfHit"));
            if(type.equals("ENTITY") && attack) {
                Object entity=GameAccess.get(hit,"field_72308_g|entityHit");
                if(Class.forName("net.minecraft.entity.player.EntityPlayer").isInstance(entity)) state.attacked(System.nanoTime());
            }
            if(type.equals("BLOCK")) position=GameAccess.call(hit,"func_178782_a|getBlockPos");
        }
        int current=slot();
        if(!attack || !allowed || position==null) {
            apply(state.step(System.nanoTime(),current,attack,allowed,null,current,0,false,0,switchBack(module)));
            status.setDesc(!allowed ? "Paused: menu, focus, item use or clicker setting" : "Hold attack on a block to select a tool");
            publish(module); return;
        }
        // Rendering only handles fast release/target/context cleanup; inventory/world evaluation stays on ticks/actions.
        if(!evaluate) { publish(module); return; }
        Object blockState=GameAccess.call(world,"func_180495_p|getBlockState",position);
        Object block=GameAccess.call(blockState,"func_177230_c|getBlock");
        Object material=GameAccess.call(block,"func_149688_o|getMaterial");
        Object air=GameAccess.get(Class.forName("net.minecraft.init.Blocks"),"field_150350_a|air");
        boolean mineable=block!=air && !Boolean.TRUE.equals(GameAccess.call(material,"func_76224_d|isLiquid"))
            && GameAccess.value(block,"func_176195_g|getBlockHardness",world,position)>=0;
        if(!mineable) {
            apply(state.stop(current,switchBack(module))); publish(module); return;
        }
        int best=bestTool(block,blockState,material,current);
        DoubleSliderSetting delays=(DoubleSliderSetting)module.getSettingByName("Max delay");
        double low=Math.max(0,delays.getInputMin()),high=Math.max(low,delays.getInputMax());
        long delay=(long)(low==high ? low : ThreadLocalRandom.current().nextDouble(low,high));
        BlockKey key=new BlockKey(position,blockState);
        apply(state.step(System.nanoTime(),current,true,true,key,best,delay,guard.isToggled(),(long)hold.getInput(),switchBack(module)));
        status.setDesc(state.manualOverride() ? "Your hotbar choice is kept until attack is released"
            : state.ownsSlot() ? "Mining; original slot saved until you stop or attack an entity"
            : state.waiting() && state.recentCombat(System.nanoTime()) && guard.isToggled() ? "Combat guard: keep holding this block to mine"
            : "Waiting for mining delay, or your held item is already best");
        publish(module);
    }
    private int bestTool(Object block,Object blockState,Object material,int current) throws ReflectiveOperationException {
        boolean free=Boolean.TRUE.equals(GameAccess.call(material,"func_76229_l|isToolNotRequired"));
        double[] rates=new double[9];
        for(int index=0;index<9;index++) {
            Object stack=GameAccess.call(inventory,"func_70301_a|getStackInSlot",index);
            double speed=1; int efficiency=0; boolean harvest=free;
            if(stack!=null) {
                Object item=GameAccess.call(stack,"func_77973_b|getItem");
                speed=GameAccess.value(item,"getDigSpeed",stack,blockState);
                harvest=free || Boolean.TRUE.equals(GameAccess.call(stack,"func_150998_b|canHarvestBlock",block));
                if(speed>1) efficiency=((Number)GameAccess.call(Class.forName("net.minecraft.enchantment.EnchantmentHelper"),"func_77506_a|getEnchantmentLevel",32,stack)).intValue();
            }
            rates[index]=AutoToolState.rate(speed,efficiency,harvest);
        }
        return AutoToolState.best(rates,current);
    }
    private void publish(Module module) {
        // Retain public compatibility fields for the original class; ownership is maintained in this controller.
        try {
            GameAccess.field(module,"mining").setBoolean(null,state.ownsSlot());
            GameAccess.field(module,"previousSlot").setInt(null,state.originalSlot());
            GameAccess.field(module,"justFinishedMining").setBoolean(null,false);
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private void fail(Module module,Exception error) {
        System.err.println("[BetterKeystrokes+] Auto Tool disabled: "+error);
        finish(module); module.disable(); status.setDesc("Disabled: game access failed; see game log");
    }
    private static final class BlockKey {
        final int x,y,z; final Object blockState;
        BlockKey(Object position,Object blockState) throws ReflectiveOperationException {
            x=(int)GameAccess.value(position,"func_177958_n|getX");
            y=(int)GameAccess.value(position,"func_177956_o|getY");
            z=(int)GameAccess.value(position,"func_177952_p|getZ");
            this.blockState=blockState;
        }
        @Override public boolean equals(Object other) {
            if(!(other instanceof BlockKey)) return false;
            BlockKey b=(BlockKey)other; return x==b.x && y==b.y && z==b.z && blockState.equals(b.blockState);
        }
        @Override public int hashCode() { return ((x*31+y)*31+z)*31+blockState.hashCode(); }
    }
}
