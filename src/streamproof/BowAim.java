package streamproof;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Locale;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Opt-in bow aiming. Target updates run at game tick rate; turns run at frame rate. */
public final class BowAim extends Module {
    private final SliderSetting range=new SliderSetting("Range",50,5,100,1);
    private final SliderSetting fov=new SliderSetting("FOV",60,5,180,1);
    private final SliderSetting turnSpeed=new SliderSetting("Turn speed (degrees/sec)",180,15,720,15);
    private final TickSetting prediction=new TickSetting("Movement prediction",true);
    private final TickSetting patterns=new TickSetting("Strafe patterns",true);
    private final SliderSetting horizon=new SliderSetting("Prediction horizon (ticks)",40,5,60,5);
    private final SliderSetting strength=new SliderSetting("Lead strength (%)",100,0,120,5);
    private final TickSetting teams=new TickSetting("Ignore teammates",true);
    private final TickSetting friends=new TickSetting("Ignore friends",true);
    private final DescriptionSetting status=new DescriptionSetting("Aims while drawing; release the arrow yourself");
    private Object minecraft, player, target;
    private Object trackedWorld;
    private final Map<Object,MotionHistory> histories=new IdentityHashMap<>();
    private double goalYaw,goalPitch;
    private boolean aiming;
    private long lastFrame;
    public BowAim() {
        super("Bow Aim",ModuleCategory.combat);
        withDescription("Bow aim with distance/charge compensation and adaptive movement prediction.");
        registerSetting(range); registerSetting(fov); registerSetting(turnSpeed);
        registerSetting(prediction); registerSetting(patterns); registerSetting(horizon); registerSetting(strength);
        registerSetting(teams); registerSetting(friends); registerSetting(status);
    }
    @Override public void onDisable() {
        aiming=false; target=null; player=null; lastFrame=0; histories.clear(); trackedWorld=null;
    }
    private boolean context() throws ReflectiveOperationException {
        if (minecraft==null) minecraft=GameAccess.call(Class.forName("net.minecraft.client.Minecraft"),"func_71410_x|getMinecraft");
        player=GameAccess.get(minecraft,"field_71439_g|thePlayer");
        return player!=null && GameAccess.get(minecraft,"field_71441_e|theWorld")!=null;
    }
    private boolean drawing() throws ReflectiveOperationException {
        if (!context() || GameAccess.get(minecraft,"field_71462_r|currentScreen")!=null
            || !Boolean.TRUE.equals(GameAccess.call(player,"func_70089_S|isEntityAlive"))
            || !Boolean.TRUE.equals(GameAccess.call(player,"func_71039_bw|isUsingItem"))) return false;
        Object stack=GameAccess.call(player,"func_71011_bu|getItemInUse");
        if (stack==null) return false;
        Object item=GameAccess.call(stack,"func_77973_b|getItem");
        return Class.forName("net.minecraft.item.ItemBow").isInstance(item);
    }
    @Override public void update() {
        aiming=false;
        try {
            if (!context()) { histories.clear(); trackedWorld=null; target=null; return; }
            Object world=GameAccess.get(minecraft,"field_71441_e|theWorld");
            List<?> players=(List<?>)GameAccess.get(world,"field_73010_i|playerEntities");
            track(world,players);
            if (!drawing()) { target=null; lastFrame=0; return; }
            double speed=BowPhysics.speed((int)GameAccess.value(player,"func_71057_bx|getItemInUseDuration"));
            if (speed<=0) return;
            double yaw=GameAccess.number(player,"field_70177_z|rotationYaw");
            double pitch=GameAccess.number(player,"field_70125_A|rotationPitch");
            Object best=null; double[] solution=null; double bestAngle=Double.POSITIVE_INFINITY;
            String bestMode="Off";
            for (Object candidate:players) {
                if (candidate==player) continue;
                double directYaw=Math.toDegrees(Math.atan2(z(candidate)-z(player),x(candidate)-x(player)))-90;
                if (Math.abs(BowPhysics.wrap(directYaw-yaw))>fov.getInput()/2+5) continue;
                if (!valid(candidate)) continue;
                MotionHistory history=histories.get(candidate);
                MotionHistory.Forecast forecast=prediction.isToggled() && history!=null
                    ? history.forecast(patterns.isToggled(),(int)horizon.getInput(),strength.getInput()/100) : null;
                double[] aim=solve(candidate,speed,yaw,forecast==null ? BowPhysics.STILL : forecast);
                if (aim==null) continue;
                double angle=Math.hypot(BowPhysics.wrap(aim[0]-yaw),aim[1]-pitch);
                if (angle>fov.getInput()/2) continue;
                // Small retention bias prevents switching between neighboring players each tick.
                double rank=angle*(candidate==target ? 0.8 : 1);
                if (rank<bestAngle) {
                    bestAngle=rank; best=candidate; solution=aim; bestMode=forecast==null ? "Off" : forecast.mode;
                }
            }
            target=best;
            if (solution!=null) {
                goalYaw=solution[0]; goalPitch=solution[1]; aiming=true;
                double distance=Math.sqrt(square(x(best)-x(player))+square(y(best)-y(player))+square(z(best)-z(player)));
                status.setDesc(String.format(Locale.ROOT,"%.0f blocks / %.2fs / %s",distance,solution[2]/20,bestMode));
            }
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private void track(Object world,List<?> players) throws ReflectiveOperationException {
        if (world!=trackedWorld) { histories.clear(); trackedWorld=world; target=null; lastFrame=0; }
        long tick=((Number)GameAccess.call(world,"func_82737_E|getTotalWorldTime")).longValue();
        double limit=range.getInput()+16;
        for (Object candidate:players) {
            if (candidate==player) continue;
            double dx=x(candidate)-x(player),dz=z(candidate)-z(player);
            if (dx*dx+dz*dz>limit*limit) continue;
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
        if (candidate==player || !Boolean.TRUE.equals(GameAccess.call(candidate,"func_70089_S|isEntityAlive"))
            || Boolean.TRUE.equals(GameAccess.call(candidate,"func_82150_aj|isInvisible"))) return false;
        double dx=x(candidate)-x(player),dy=y(candidate)-y(player),dz=z(candidate)-z(player);
        if (dx*dx+dy*dy+dz*dz>range.getInput()*range.getInput()) return false;
        if (!Boolean.TRUE.equals(GameAccess.call(player,"func_70685_l|canEntityBeSeen",candidate))) return false;
        if (teams.isToggled() && Boolean.TRUE.equals(GameAccess.call(player,"func_142014_c|isOnSameTeam",candidate))) return false;
        if (friends.isToggled()) {
            Object list=GameAccess.get(Class.forName("keystrokesmod.client.module.modules.combat.AimAssist"),"friends");
            if (list instanceof List && ((List<?>)list).contains(candidate)) return false;
        }
        return !Boolean.TRUE.equals(GameAccess.call(Class.forName("keystrokesmod.client.module.modules.world.AntiBot"),"bot",candidate));
    }
    private double[] solve(Object candidate,double speed,double yaw,BowPhysics.TargetPath path) throws ReflectiveOperationException {
        double dx=x(candidate)-x(player),dz=z(candidate)-z(player);
        double dy=y(candidate)+GameAccess.number(candidate,"field_70131_O|height")*0.55
            -y(player)-GameAccess.value(player,"func_70047_e|getEyeHeight")+0.1;
        double[] aim=null;
        for (int i=0;i<2;i++) {
            // Vanilla arrows spawn 0.16 blocks to the side of the shooter's eyes.
            aim=BowPhysics.solve(dx+Math.cos(Math.toRadians(yaw))*0.16,dy,
                dz+Math.sin(Math.toRadians(yaw))*0.16,speed,path);
            if (aim==null) return null;
            yaw=aim[0];
        }
        return aim;
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if (event.phase!=TickEvent.Phase.START || !isEnabled()) return;
        long now=System.nanoTime();
        double dt=lastFrame==0 ? 0 : Math.min(0.05,(now-lastFrame)/1.0e9); lastFrame=now;
        if (!aiming) return;
        try {
            Object previousPlayer=player;
            if (!drawing() || previousPlayer!=player || target==null
                || GameAccess.get(target,"field_70170_p|worldObj")!=GameAccess.get(player,"field_70170_p|worldObj")
                || !Boolean.TRUE.equals(GameAccess.call(target,"func_70089_S|isEntityAlive"))) {
                aiming=false; return;
            }
            double yaw=GameAccess.number(player,"field_70177_z|rotationYaw"),pitch=GameAccess.number(player,"field_70125_A|rotationPitch");
            double dy=BowPhysics.wrap(goalYaw-yaw),dp=goalPitch-pitch;
            double length=Math.hypot(dy,dp), factor=length==0 ? 0 : Math.min(1,turnSpeed.getInput()*dt/length);
            // Vanilla's mouse-turn helper also adjusts previous rotations, preventing
            // camera interpolation from snapping back toward the last tick's angle.
            GameAccess.call(player,"func_70082_c|setAngles",(float)(dy*factor/0.15),(float)(-dp*factor/0.15));
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private void fail(Exception e) {
        System.err.println("[BetterKeystrokes+] Bow Aim disabled: "+e); e.printStackTrace();
        disable(); status.setDesc("Disabled: game access failed; see game log");
    }
    private static double x(Object o) throws ReflectiveOperationException { return GameAccess.number(o,"field_70165_t|posX"); }
    private static double y(Object o) throws ReflectiveOperationException { return GameAccess.number(o,"field_70163_u|posY"); }
    private static double z(Object o) throws ReflectiveOperationException { return GameAccess.number(o,"field_70161_v|posZ"); }
    private static double square(double v) { return v*v; }
}
