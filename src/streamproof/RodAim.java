package streamproof;

import java.util.*;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Smooth pre-cast rod aiming. No synthetic clicks, item switches, casts or reels. */
public final class RodAim extends Module {
    private final SliderSetting range=new SliderSetting("Range",12,3,20,0.5);
    private final SliderSetting fov=new SliderSetting("FOV",70,5,180,1);
    private final SliderSetting turnSpeed=new SliderSetting("Turn speed (degrees/sec)",240,30,720,15);
    private final TickSetting prediction=new TickSetting("Movement prediction",true);
    private final TickSetting patterns=new TickSetting("Strafe patterns",true);
    private final SliderSetting horizon=new SliderSetting("Prediction horizon (ticks)",20,5,40,5);
    private final SliderSetting strength=new SliderSetting("Lead strength (%)",100,0,120,5);
    private final TickSetting teams=new TickSetting("Ignore teammates",true);
    private final TickSetting friends=new TickSetting("Ignore friends",true);
    private final DescriptionSetting status=new DescriptionSetting("Hold an uncast rod; cast and reel yourself");
    private final Map<Object,MotionHistory> histories=new IdentityHashMap<>();
    private final RodUse use=new RodUse();
    private Object minecraft,player,target,trackedWorld;
    private boolean aiming;
    private double goalYaw,goalPitch;
    private long lastFrame;
    public RodAim() {
        super("Rod Aim",ModuleCategory.combat);
        withDescription("Smooth rod aim before casting; pauses on cast or switching items.");
        registerSetting(range); registerSetting(fov); registerSetting(turnSpeed);
        registerSetting(prediction); registerSetting(patterns); registerSetting(horizon); registerSetting(strength);
        registerSetting(teams); registerSetting(friends); registerSetting(status);
    }
    @Override public void onDisable() {
        aiming=false; target=null; player=null; lastFrame=0; trackedWorld=null; histories.clear(); use.reset();
    }
    private boolean context() throws ReflectiveOperationException {
        if (minecraft==null) minecraft=GameAccess.call(Class.forName("net.minecraft.client.Minecraft"),"func_71410_x|getMinecraft");
        player=GameAccess.get(minecraft,"field_71439_g|thePlayer");
        Object world=GameAccess.get(minecraft,"field_71441_e|theWorld");
        if (world!=trackedWorld) { trackedWorld=world; histories.clear(); target=null; lastFrame=0; use.reset(); }
        return player!=null && world!=null;
    }
    private boolean ready() throws ReflectiveOperationException {
        if (!context()) return false;
        Object stack=GameAccess.call(player,"func_70694_bm|getHeldItem");
        Object item=stack==null ? null : GameAccess.call(stack,"func_77973_b|getItem");
        boolean rod=item!=null && Class.forName("net.minecraft.item.ItemFishingRod").isInstance(item);
        return use.ready(player,rod,GameAccess.get(minecraft,"field_71462_r|currentScreen")!=null,System.nanoTime());
    }
    @Override public void update() {
        aiming=false;
        try {
            if (!context()) { histories.clear(); trackedWorld=null; target=null; use.reset(); return; }
            Object world=GameAccess.get(minecraft,"field_71441_e|theWorld");
            List<?> players=(List<?>)GameAccess.get(world,"field_73010_i|playerEntities");
            track(world,players);
            if (!ready()) { target=null; lastFrame=0; return; }
            double yaw=GameAccess.number(player,"field_70177_z|rotationYaw");
            double pitch=GameAccess.number(player,"field_70125_A|rotationPitch");
            Object best=null; double[] solution=null; double bestRank=Double.POSITIVE_INFINITY; String mode="Off";
            for (Object candidate:players) {
                if (candidate==player) continue;
                double directYaw=Math.toDegrees(Math.atan2(z(candidate)-z(player),x(candidate)-x(player)))-90;
                if (Math.abs(BowPhysics.wrap(directYaw-yaw))>fov.getInput()/2+5 || !valid(candidate)) continue;
                MotionHistory history=histories.get(candidate);
                MotionHistory.Forecast forecast=prediction.isToggled() && history!=null
                    ? history.forecast(patterns.isToggled(),(int)horizon.getInput(),strength.getInput()/100) : null;
                double[] aim=solve(candidate,yaw,forecast==null ? BowPhysics.STILL : forecast);
                if (aim==null) continue;
                double angle=Math.hypot(BowPhysics.wrap(aim[0]-yaw),aim[1]-pitch);
                if (angle>fov.getInput()/2) continue;
                double rank=angle*(candidate==target ? 0.8 : 1);
                if (rank<bestRank) { bestRank=rank; best=candidate; solution=aim; mode=forecast==null ? "Off" : forecast.mode; }
            }
            target=best;
            if (solution!=null) {
                goalYaw=solution[0]; goalPitch=solution[1]; aiming=true;
                status.setDesc(String.format(Locale.ROOT,"%.1f blocks / %.2fs / %s",distance(best),solution[2]/20,mode));
            }
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private void track(Object world,List<?> players) throws ReflectiveOperationException {
        long tick=((Number)GameAccess.call(world,"func_82737_E|getTotalWorldTime")).longValue();
        double limit=range.getInput()+16;
        for (Object candidate:players) {
            if (candidate==player || square(x(candidate)-x(player))+square(z(candidate)-z(player))>limit*limit) continue;
            MotionHistory history=histories.get(candidate);
            if (history==null) { history=new MotionHistory(); histories.put(candidate,history); }
            history.sample(tick,x(candidate),y(candidate),z(candidate),Boolean.TRUE.equals(GameAccess.get(candidate,"field_70122_E|onGround")));
        }
        Iterator<MotionHistory> iterator=histories.values().iterator();
        while (iterator.hasNext()) {
            long seen=iterator.next().lastTick();
            if (seen==Long.MIN_VALUE || seen>tick || tick-seen>5) iterator.remove();
        }
    }
    private boolean valid(Object candidate) throws ReflectiveOperationException {
        if (!Boolean.TRUE.equals(GameAccess.call(candidate,"func_70089_S|isEntityAlive"))
            || Boolean.TRUE.equals(GameAccess.call(candidate,"func_82150_aj|isInvisible")) || distance(candidate)>range.getInput()) return false;
        if (!Boolean.TRUE.equals(GameAccess.call(player,"func_70685_l|canEntityBeSeen",candidate))) return false;
        if (teams.isToggled() && Boolean.TRUE.equals(GameAccess.call(player,"func_142014_c|isOnSameTeam",candidate))) return false;
        if (friends.isToggled()) {
            Object list=GameAccess.get(Class.forName("keystrokesmod.client.module.modules.combat.AimAssist"),"friends");
            if (list instanceof List && ((List<?>)list).contains(candidate)) return false;
        }
        return !Boolean.TRUE.equals(GameAccess.call(Class.forName("keystrokesmod.client.module.modules.world.AntiBot"),"bot",candidate));
    }
    private double[] solve(Object candidate,double yaw,BowPhysics.TargetPath path) throws ReflectiveOperationException {
        double dx=x(candidate)-x(player),dz=z(candidate)-z(player);
        double dy=y(candidate)+GameAccess.number(candidate,"field_70131_O|height")*0.55
            -y(player)-GameAccess.value(player,"func_70047_e|getEyeHeight")+0.1;
        double[] aim=null;
        for (int i=0;i<2;i++) {
            aim=RodPhysics.solve(dx+Math.cos(Math.toRadians(yaw))*0.16,dy,dz+Math.sin(Math.toRadians(yaw))*0.16,path);
            if (aim==null) return null;
            yaw=aim[0];
        }
        return aim;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent event) {
        if (!isEnabled() || event.action==PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        try {
            if (GameAccess.get(event,"entityPlayer")==player && ready()) pauseForCast();
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouse(MouseEvent event) {
        if (!isEnabled() || !event.buttonstate || event.button<0) return;
        try {
            if (!ready()) return;
            if (useKeyCode()==event.button-100) pauseForCast();
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void key(InputEvent.KeyInputEvent event) {
        if (!isEnabled() || !Keyboard.getEventKeyState()) return;
        try {
            int code=Keyboard.getEventKey()==0 ? Keyboard.getEventCharacter()+256 : Keyboard.getEventKey();
            if (ready() && useKeyCode()==code) pauseForCast();
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private int useKeyCode() throws ReflectiveOperationException {
        Object settings=GameAccess.get(minecraft,"field_71474_y|gameSettings");
        Object key=GameAccess.get(settings,"field_74313_G|keyBindUseItem");
        return ((Number)GameAccess.call(key,"func_151463_i|getKeyCode")).intValue();
    }
    private void pauseForCast() { use.cast(System.nanoTime()); aiming=false; target=null; lastFrame=0; }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if (event.phase!=TickEvent.Phase.START || !isEnabled()) return;
        long now=System.nanoTime(); double dt=lastFrame==0 ? 0 : Math.min(0.05,(now-lastFrame)/1.0e9); lastFrame=now;
        if (!aiming) return;
        try {
            Object previous=player;
            if (!ready() || previous!=player || target==null
                || GameAccess.get(target,"field_70170_p|worldObj")!=GameAccess.get(player,"field_70170_p|worldObj")
                || !Boolean.TRUE.equals(GameAccess.call(target,"func_70089_S|isEntityAlive"))) { aiming=false; return; }
            double yaw=GameAccess.number(player,"field_70177_z|rotationYaw"),pitch=GameAccess.number(player,"field_70125_A|rotationPitch");
            double dy=BowPhysics.wrap(goalYaw-yaw),dp=goalPitch-pitch,length=Math.hypot(dy,dp);
            double factor=length==0 ? 0 : Math.min(1,turnSpeed.getInput()*dt/length);
            GameAccess.call(player,"func_70082_c|setAngles",(float)(dy*factor/0.15),(float)(-dp*factor/0.15));
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private void fail(Exception e) {
        System.err.println("[BetterKeystrokes+] Rod Aim disabled: "+e); e.printStackTrace();
        disable(); status.setDesc("Disabled: game access failed; see game log");
    }
    private double distance(Object candidate) throws ReflectiveOperationException {
        return Math.sqrt(square(x(candidate)-x(player))+square(y(candidate)-y(player))+square(z(candidate)-z(player)));
    }
    private static double x(Object o) throws ReflectiveOperationException { return GameAccess.number(o,"field_70165_t|posX"); }
    private static double y(Object o) throws ReflectiveOperationException { return GameAccess.number(o,"field_70163_u|posY"); }
    private static double z(Object o) throws ReflectiveOperationException { return GameAccess.number(o,"field_70161_v|posZ"); }
    private static double square(double value) { return value*value; }
}
