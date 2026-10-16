package streamproof;

import java.io.File;
import java.util.*;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.*;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import streamproof.BowGeometry.*;

/** Stable full-draw camera control and separate actual-charge shot assessment. */
public final class BowAim extends Module {
    private static BowAim instance;
    private final SliderSetting range=new SliderSetting("Range",50,5,100,1);
    private final SliderSetting fov=new SliderSetting("FOV",60,5,180,1);
    private final SliderSetting turnSpeed=new SliderSetting("Turn speed (degrees/sec)",180,15,720,15);
    private final TickSetting prediction=new TickSetting("Movement prediction",true),patterns=new TickSetting("Strafe patterns",true);
    private final SliderSetting horizon=new SliderSetting("Prediction horizon (ticks)",40,5,60,5);
    private final SliderSetting strength=new SliderSetting("Lead strength (%)",100,0,120,5);
    private final TickSetting teams=new TickSetting("Ignore teammates",true),friends=new TickSetting("Ignore friends",true);
    private final TickSetting timing=new TickSetting("Timing compensation",false),showReady=new TickSetting("Show shot readiness",true);
    private final TickSetting diagnostics=new TickSetting("Shot diagnostics",false);
    private final DescriptionSetting status=new DescriptionSetting("Stable full-draw aim; release the arrow yourself");
    private final DescriptionSetting logStatus=new DescriptionSetting("Diagnostics off");
    private final Map<Object,MotionHistory> histories=new IdentityHashMap<>();
    private final Map<Object,Vec> expectedNext=new IdentityHashMap<>();
    private final Map<Object,Long> unsettled=new IdentityHashMap<>();
    private final BowController controller=new BowController();
    private final BowController.Retention retention=new BowController.Retention();
    private final BowFlight.Readiness readiness=new BowFlight.Readiness();
    private Object minecraft,player,world,held;
    private BowWorld scene;
    private BowSolution solution;
    private BowPhysics.TargetPath cachedPath=BowPhysics.STILL;
    private BowFlight.Assessment actual;
    private BowDiagnostics diagnostic;
    private BowLog logger;
    private long sampleNanos,assessedNanos,tick;
    private double roundTrip=-1,assessedYaw,assessedPitch,assessedSpeed,frameYaw,framePitch,frameGoalYaw,frameGoalPitch,ageTicks;
    private boolean ready,externalCamera,frameWritten,otherOwnedArrows;
    private String reason="No target",mode="Off";
    public BowAim() {
        super("Bow Aim",ModuleCategory.combat); instance=this;
        withDescription("Stable full-draw aim, full-path checks and actual-charge readiness. Manual release.");
        registerSetting(range); registerSetting(fov); registerSetting(turnSpeed); registerSetting(prediction); registerSetting(patterns);
        registerSetting(horizon); registerSetting(strength); registerSetting(teams); registerSetting(friends);
        registerSetting(timing); registerSetting(showReady); registerSetting(diagnostics); registerSetting(status); registerSetting(logStatus);
    }
    private void resetAim() {
        solution=null; held=null; ready=false; actual=null; controller.reset(); retention.reset(); readiness.reset(); frameWritten=false; externalCamera=false;
    }
    @Override public void onDisable() {
        resetAim(); histories.clear(); expectedNext.clear(); unsettled.clear(); world=null; player=null; scene=null; roundTrip=-1; closeLog();
    }
    private void closeLog() { if(diagnostic!=null)diagnostic.close(); diagnostic=null; logger=null; }
    private boolean context() throws ReflectiveOperationException {
        if(minecraft==null)minecraft=GameAccess.call(Class.forName("net.minecraft.client.Minecraft"),"func_71410_x|getMinecraft");
        Object p=GameAccess.get(minecraft,"field_71439_g|thePlayer"),w=GameAccess.get(minecraft,"field_71441_e|theWorld");
        if(p!=player || w!=world) {
            resetAim(); histories.clear(); expectedNext.clear(); unsettled.clear(); closeLog(); roundTrip=-1;
            player=p; world=w; scene=world==null || player==null ? null : new BowWorld(world,player);
        }
        return player!=null && world!=null;
    }
    private boolean drawing() throws ReflectiveOperationException {
        if(!context() || GameAccess.get(minecraft,"field_71462_r|currentScreen")!=null
            || !Boolean.TRUE.equals(GameAccess.call(player,"func_70089_S|isEntityAlive"))
            || !Boolean.TRUE.equals(GameAccess.call(player,"func_71039_bw|isUsingItem")))return false;
        Object stack=GameAccess.call(player,"func_71011_bu|getItemInUse");
        if(stack==null || !Class.forName("net.minecraft.item.ItemBow").isInstance(GameAccess.call(stack,"func_77973_b|getItem")))return false;
        if(held!=stack) { resetAim(); held=stack; }
        return true;
    }
    @Override public void update() {
        try {
            if(!context())return;
            long now=System.nanoTime(); tick=((Number)GameAccess.call(world,"func_82737_E|getTotalWorldTime")).longValue();
            configureLog(); observeArrows(now);
            List<?> players=(List<?>)GameAccess.get(world,"field_73010_i|playerEntities");
            track(players); sampleNanos=now; readPing();
            if(!drawing()) { resetAim(); return; }
            double yaw=number(player,"field_70177_z|rotationYaw"),pitch=number(player,"field_70125_A|rotationPitch");
            ageTicks=BowController.extraTicks(sampleNanos,now,roundTrip,timing.isToggled());
            List<Object> candidates=new ArrayList<>();
            for(Object e:players)if(e!=player && valid(e))candidates.add(e);
            final double viewYaw=yaw;
            Collections.sort(candidates,new Comparator<Object>() { public int compare(Object a,Object b) {
                try { return Double.compare(directAngle(a,viewYaw),directAngle(b,viewYaw)); } catch(ReflectiveOperationException e) { return 0; }
            }});
            if(solution!=null && candidates.remove(solution.body.key))candidates.add(0,solution.body.key);
            if(candidates.size()>3)candidates=new ArrayList<>(candidates.subList(0,3));
            Map<Object,BowPhysics.TargetPath> paths=new IdentityHashMap<>();
            Map<Object,BowFlight.Body> targets=new IdentityHashMap<>();
            for(Object e:candidates) {
                BowPhysics.TargetPath path=forecast(e); paths.put(e,path);
                targets.put(e,new BowFlight.Body(e,BowWorld.bounds(e),BowForecast.aged(path,ageTicks)));
            }
            List<BowFlight.Body> bodies=bodies(targets);
            List<BowSolution> trials=new ArrayList<>();
            for(Object e:candidates) {
                BowFlight.Body body=targets.get(e); boolean reachable=false;
                for(double height:new double[]{0.35,0.5,0.65}) {
                    double[] aim=solve(body,height,body.path,yaw);
                    if(aim!=null) { reachable=true; trials.add(new BowSolution(body,height,aim,Math.max(0.1,Math.hypot(BowPhysics.wrap(aim[0]-yaw),aim[1]-pitch)),null)); }
                }
                if(!reachable) {
                    Vec delta=body.box.point(0.5).sub(eyes());
                    double[] aim={Math.toDegrees(Math.atan2(delta.z,delta.x))-90,-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))),Double.NaN};
                    BowFlight.Assessment unavailable=new BowFlight.Assessment(BowFlight.Result.UNREACHABLE,0,0,eyes(),false,Collections.<Vec>emptyList());
                    trials.add(new BowSolution(body,0.5,aim,100000,unavailable));
                }
            }
            Collections.sort(trials,new Comparator<BowSolution>() { public int compare(BowSolution a,BowSolution b) { return Double.compare(a.score,b.score); }});
            // Reserve a check for the retained target instead of evicting it through the work cap.
            if(solution!=null)for(int i=0;i<trials.size();i++)if(trials.get(i).body.key==solution.body.key) { trials.add(0,trials.remove(i)); break; }
            BowFlight.Budget budget=new BowFlight.Budget(480); List<BowSolution> checked=new ArrayList<>();
            for(int i=0;i<Math.min(3,trials.size());i++) {
                BowSolution s=trials.get(i);
                BowFlight.Assessment flight=s.assessment!=null ? s.assessment : BowFlight.trace(BowGeometry.launch(eyes(),s.yaw),s.yaw,s.pitch,3,s.body,bodies,scene,budget);
                checked.add(new BowSolution(s.body,s.height,new double[]{s.yaw,s.pitch,s.flightTicks},s.score,flight));
            }
            BowSolution previous=solution,current=null,best=null,fallback=null;
            for(BowSolution s:checked) {
                if(fallback==null)fallback=s;
                if(!s.assessment.valid())continue;
                if(previous!=null && s.body.key==previous.body.key && better(s,current))current=s;
                if(better(s,best))best=s;
            }
            if(current!=null && best!=null && best.body.key!=current.body.key
                && !retention.replace(best.body.key,best.score,current.score,tick))best=current;
            else if(current!=null && (best==null || best.body.key==current.body.key)) { best=current; retention.reset(); }
            solution=best!=null ? best : fallback;
            if(solution==null) { resetAim(); reason="No reachable target"; status.setDesc(reason); return; }
            if(previous==null || previous.body.key!=solution.body.key) { controller.reset(); readiness.reset(); ready=false; }
            cachedPath=paths.get(solution.body.key);
            MotionHistory history=histories.get(solution.body.key);
            mode=!prediction.isToggled() ? "Off" : history==null ? "Recent motion" : history.forecast(patterns.isToggled(),(int)horizon.getInput(),strength.getInput()/100).mode;
            assessedYaw=yaw; assessedPitch=pitch; assessedNanos=now;
            int charge=(int)GameAccess.value(player,"func_71057_bx|getItemInUseDuration");
            assessedSpeed=BowPhysics.speed(charge);
            actual=BowFlight.trace(BowGeometry.launch(eyes(),yaw),yaw,pitch,assessedSpeed,solution.body,bodies,scene,budget);
            boolean uncertain=prediction.isToggled() && (history==null || history.samples()<2 || unsettled.containsKey(solution.body.key)
                && unsettled.get(solution.body.key)>=tick || cachedPath instanceof BowForecast && ((BowForecast)cachedPath).uncertain
                || !mode.equals("Still") && solution.flightTicks+ageTicks>horizon.getInput());
            reason=!solution.assessment.valid() ? solution.assessment.result.name() : uncertain ? "movement_uncertainty" : actual.valid() ? "HIT" : charge<20 ? "charging" :
                Math.hypot(BowPhysics.wrap(solution.yaw-yaw),solution.pitch-pitch)>0.75 ? "turning" :
                actual.result==BowFlight.Result.HIT ? "hitbox_margin" : actual.result.name();
            ready=readiness.update(solution.body.key,tick,solution.assessment.valid() && actual.valid() && !uncertain);
            status.setDesc((ready ? "Ready" : label())+" / "+reason+" / "+String.format(Locale.ROOT,"%.0f blocks / %.2fs / %s",
                position(player).distance(position(solution.body.key)),solution.flightTicks/20,mode));
        } catch(ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private static boolean better(BowSolution a,BowSolution b) {
        return b==null || a.clearance>b.clearance+1e-6 || Math.abs(a.clearance-b.clearance)<1e-6 && a.score<b.score;
    }
    private void track(List<?> players) throws ReflectiveOperationException {
        for(Object e:players) {
            if(e==player || position(e).distance(position(player))>range.getInput()+16)continue;
            MotionHistory h=histories.get(e); if(h==null) { h=new MotionHistory(); histories.put(e,h); }
            Vec expected=expectedNext.get(e),at=position(e);
            if(diagnostic!=null)diagnostic.targetMotion(e,at,System.nanoTime());
            if(expected!=null && expected.distance(at)>0.25)unsettled.put(e,tick+2);
            h.sample(tick,at.x,at.y,at.z,Boolean.TRUE.equals(GameAccess.get(e,"field_70122_E|onGround")));
            double[] offset=new double[3]; h.forecast(false,5,1).offset(1,offset); expectedNext.put(e,at.add(offset[0],offset[1],offset[2]));
        }
        for(Object key:new ArrayList<>(histories.keySet()))if(histories.get(key).lastTick()>tick || tick-histories.get(key).lastTick()>5) {
            histories.remove(key); expectedNext.remove(key); unsettled.remove(key);
        }
    }
    private BowPhysics.TargetPath forecast(final Object e) throws ReflectiveOperationException {
        if(!prediction.isToggled())return BowPhysics.STILL;
        MotionHistory h=histories.get(e); if(h==null)return BowPhysics.STILL;
        MotionHistory.Forecast base=h.forecast(patterns.isToggled(),(int)horizon.getInput(),strength.getInput()/100);
        double vertical=number(e,"field_70163_u|posY")-number(e,"field_70167_r|prevPosY");
        return new BowForecast(base,BowWorld.bounds(e),vertical,Boolean.TRUE.equals(GameAccess.get(e,"field_70122_E|onGround")),
            (int)horizon.getInput(),strength.getInput()/100,new BowForecast.Ground() {
                public double floor(Box footprint,double from,double to) { return scene.floor(footprint,from,to); }
            });
    }
    private List<BowFlight.Body> bodies(Map<Object,BowFlight.Body> targets) throws ReflectiveOperationException {
        List<BowFlight.Body> result=new ArrayList<>();
        for(Object e:(List<?>)GameAccess.get(world,"field_72996_f|loadedEntityList")) {
            if(e==player || position(e).distance(position(player))>range.getInput()+8
                || !Boolean.TRUE.equals(GameAccess.call(e,"func_70067_L|canBeCollidedWith")))continue;
            if(targets.containsKey(e)) { result.add(targets.get(e)); continue; }
            final double vx=number(e,"field_70165_t|posX")-number(e,"field_70169_q|prevPosX");
            final double vy=number(e,"field_70163_u|posY")-number(e,"field_70167_r|prevPosY");
            final double vz=number(e,"field_70161_v|posZ")-number(e,"field_70166_s|prevPosZ");
            result.add(new BowFlight.Body(e,BowWorld.bounds(e),new BowPhysics.TargetPath() {
                public void offset(double t,double[] out) { t=Math.min(20,t); out[0]=clamp(vx,-1.5,1.5)*t; out[1]=clamp(vy,-1,1)*Math.min(6,t); out[2]=clamp(vz,-1.5,1.5)*t; }
            }));
        }
        for(BowFlight.Body body:targets.values())if(!result.contains(body))result.add(body);
        return result;
    }
    private boolean valid(Object e) throws ReflectiveOperationException {
        if(!Boolean.TRUE.equals(GameAccess.call(e,"func_70089_S|isEntityAlive")) || Boolean.TRUE.equals(GameAccess.call(e,"func_82150_aj|isInvisible"))
            || position(e).distance(position(player))>range.getInput() || directAngle(e,number(player,"field_70177_z|rotationYaw"))>fov.getInput()/2)return false;
        if(teams.isToggled() && Boolean.TRUE.equals(GameAccess.call(player,"func_142014_c|isOnSameTeam",e)))return false;
        if(friends.isToggled()) {
            Object list=GameAccess.get(Class.forName("keystrokesmod.client.module.modules.combat.AimAssist"),"friends");
            if(list instanceof List && ((List<?>)list).contains(e))return false;
        }
        return !Boolean.TRUE.equals(GameAccess.call(Class.forName("keystrokesmod.client.module.modules.world.AntiBot"),"bot",e));
    }
    private double directAngle(Object e,double yaw) throws ReflectiveOperationException {
        Vec delta=position(e).add(0,number(e,"field_70131_O|height")/2,0).sub(eyes());
        return BowController.viewAngle(delta,yaw,number(player,"field_70125_A|rotationPitch"));
    }
    private double[] solve(BowFlight.Body body,double height,BowPhysics.TargetPath path,double yaw) throws ReflectiveOperationException {
        return BowSolution.aim(eyes(),body.box,height,path,yaw);
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START || !isEnabled())return;
        try {
            if(!drawing() || solution==null) { resetAim(); return; }
            if(!Boolean.TRUE.equals(GameAccess.call(solution.body.key,"func_70089_S|isEntityAlive"))) { resetAim(); return; }
            if(solution.assessment==null || !solution.assessment.valid()) { ready=false; controller.reset(); return; }
            long now=System.nanoTime();
            if(now-sampleNanos>150000000L) { ready=false; reason="timing_uncertainty"; controller.reset(); return; }
            double yaw=number(player,"field_70177_z|rotationYaw"),pitch=number(player,"field_70125_A|rotationPitch");
            if(frameWritten && Math.hypot(BowPhysics.wrap(yaw-frameYaw),pitch-framePitch)>2)externalCamera=true;
            double age=BowController.extraTicks(sampleNanos,now,roundTrip,timing.isToggled());
            BowFlight.Body body=new BowFlight.Body(solution.body.key,solution.body.box,BowForecast.aged(cachedPath,age));
            double[] goal=solve(body,solution.height,body.path,yaw);
            if(goal==null) { ready=false; controller.reset(); return; }
            frameGoalYaw=goal[0]; frameGoalPitch=goal[1];
            double[] angles=controller.turn(solution.body.key,yaw,pitch,goal[0],goal[1],now,turnSpeed.getInput());
            GameAccess.call(player,"func_70082_c|setAngles",(float)((angles[0]-yaw)/0.15),(float)(-(angles[1]-pitch)/0.15));
            frameYaw=angles[0]; framePitch=angles[1]; frameWritten=true;
            double speed=BowPhysics.speed((int)GameAccess.value(player,"func_71057_bx|getItemInUseDuration"));
            if(!fresh(frameYaw,framePitch,speed,now) || Math.hypot(BowPhysics.wrap(goal[0]-solution.yaw),goal[1]-solution.pitch)>safetyAngle())ready=false;
        } catch(ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private String label() {
        if(ready)return "Ready";
        return reason.equals("BLOCK") || reason.equals("ENTITY")
            || reason.equals("UNREACHABLE") && solution!=null && !solution.assessment.valid() ? "Blocked" : "Adjusting";
    }
    @SubscribeEvent public void hud(RenderGameOverlayEvent.Post event) {
        if(event.type!=RenderGameOverlayEvent.ElementType.ALL || !isEnabled() || !showReady.isToggled() || solution==null)return;
        try {
            if(!drawing())return;
            if(!fresh(number(player,"field_70177_z|rotationYaw"),number(player,"field_70125_A|rotationPitch"),
                BowPhysics.speed((int)GameAccess.value(player,"func_71057_bx|getItemInUseDuration")),System.nanoTime()))ready=false;
            final Object font=GameAccess.get(minecraft,"field_71466_p|fontRendererObj");
            Object scaled=Class.forName("net.minecraft.client.gui.ScaledResolution").getConstructor(Class.forName("net.minecraft.client.Minecraft")).newInstance(minecraft);
            final String text=label();
            int width=((Number)GameAccess.call(scaled,"func_78326_a|getScaledWidth")).intValue();
            int height=((Number)GameAccess.call(scaled,"func_78328_b|getScaledHeight")).intValue();
            int textWidth=((Number)GameAccess.call(font,"func_78256_a|getStringWidth",text)).intValue();
            final int x=(width-textWidth)/2,y=height/2+13,color=ready ? 0x55FF55 : text.equals("Blocked") ? 0xFF5555 : 0xFFCC55;
            BowHud.paint(new BowHud.Painter() { public void draw() throws ReflectiveOperationException {
                GameAccess.call(font,"func_78276_b|drawString",text,x+1,y+1,0x101010);
                GameAccess.call(font,"func_78276_b|drawString",text,x,y,color);
            }});
        } catch(ReflectiveOperationException | RuntimeException e) { ready=false; System.err.println("[BetterKeystrokes+] Readiness HUD: "+e); }
    }
    private void readPing() {
        try {
            Object info=GameAccess.call(player,"func_175155_b|getPlayerInfo");
            double value=info==null ? -1 : GameAccess.value(info,"func_178853_c|getResponseTime");
            roundTrip=value<0 ? -1 : roundTrip<0 ? value : roundTrip*0.8+value*0.2;
        } catch(ReflectiveOperationException | RuntimeException e) { roundTrip=-1; }
    }
    private void configureLog() throws ReflectiveOperationException {
        if(diagnostics.isToggled() && diagnostic==null) {
            File game=(File)GameAccess.get(minecraft,"field_71412_D|mcDataDir");
            logger=new BowLog(game.toPath().resolve("keystrokesmod/diagnostics/bow")); diagnostic=new BowDiagnostics(logger);
        } else if(!diagnostics.isToggled() && diagnostic!=null)closeLog();
        logStatus.setDesc(diagnostic==null ? "Diagnostics off" : !logger.error.isEmpty() ? "Log unavailable" : diagnostic.summary()+" / dropped "+logger.dropped);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void loose(ArrowLooseEvent event) {
        if(!isEnabled() || diagnostic==null || event.isCanceled())return;
        try {
            if(GameAccess.get(event,"entityPlayer")!=player)return;
            double yaw=number(player,"field_70177_z|rotationYaw"),pitch=number(player,"field_70125_A|rotationPitch");
            long now=System.nanoTime(); double releaseAge=BowController.extraTicks(sampleNanos,now,roundTrip,timing.isToggled());
            Vec origin=BowGeometry.launch(eyes(),yaw),velocity=BowGeometry.velocity(yaw,pitch,BowPhysics.speed(event.charge));
            List<Vec> expected=new ArrayList<>(); Vec at=origin; expected.add(at);
            for(int i=0;i<120;i++) { at=at.add(velocity); expected.add(at); velocity=new Vec(velocity.x*0.99,velocity.y*0.99-0.05,velocity.z*0.99); }
            List<Vec> targetExpected=new ArrayList<>(); Vec targetAt=solution==null ? null : position(solution.body.key); double[] offset=new double[3];
            if(solution!=null)for(int i=0;i<=120;i++) { cachedPath.offset(i+releaseAge,offset); targetExpected.add(targetAt.add(offset[0],offset[1],offset[2])); }
            boolean fresh=solution!=null && fresh(yaw,pitch,BowPhysics.speed(event.charge),now);
            diagnostic.release(new BowDiagnostics.Snapshot(event.charge,yaw,pitch,solution==null ? yaw : frameWritten ? frameGoalYaw : solution.yaw,
                solution==null ? pitch : frameWritten ? frameGoalPitch : solution.pitch,releaseAge,origin,
                targetAt,mode,solution==null ? "no_target" : fresh ? reason : "timing_uncertainty",ready && fresh,externalCamera,expected,solution==null ? null : solution.body.key,targetExpected),now);
            externalCamera=false;
        } catch(ReflectiveOperationException | RuntimeException e) { System.err.println("[BetterKeystrokes+] Shot snapshot: "+e); }
    }
    private void observeArrows(long now) throws ReflectiveOperationException {
        if(diagnostic==null)return;
        Set<Object> present=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        Class<?> arrow=Class.forName("net.minecraft.entity.projectile.EntityArrow");
        for(Object e:(List<?>)GameAccess.get(world,"field_72996_f|loadedEntityList"))if(arrow.isInstance(e)) {
            present.add(e); Object owner=GameAccess.get(e,"field_70250_c|shootingEntity");
            diagnostic.arrow(e,owner==null ? null : owner==player,position(e),
                new Vec(number(e,"field_70159_w|motionX"),number(e,"field_70181_x|motionY"),number(e,"field_70179_y|motionZ")),
                Boolean.TRUE.equals(GameAccess.get(e,"field_70254_i|inGround")),now);
        }
        otherOwnedArrows=false;
        for(Object e:present) {
            Object owner=GameAccess.get(e,"field_70250_c|shootingEntity");
            if((owner==player || owner==null) && !diagnostic.matched(e))otherOwnedArrows=true;
        }
        diagnostic.expire(present,now);
    }
    public static void observeGameState(Object packet) {
        BowAim self=instance; if(self==null || self.diagnostic==null)return;
        try { if(GameAccess.value(packet,"func_149138_c|getGameState")==6)self.diagnostic.marker(System.nanoTime(),self.otherOwnedArrows); }
        catch(ReflectiveOperationException | RuntimeException ignored) {}
    }
    private void fail(Exception e) { System.err.println("[BetterKeystrokes+] Bow Aim disabled: "+e); e.printStackTrace(); disable(); status.setDesc("Disabled: see game log"); }
    private double safetyAngle() throws ReflectiveOperationException {
        return actual==null || solution==null ? 0 : BowController.safetyAngle(actual.clearance,eyes().distance(solution.body.box.point(solution.height)));
    }
    private boolean fresh(double yaw,double pitch,double speed,long now) throws ReflectiveOperationException {
        return actual!=null && BowController.fresh(assessedNanos,now,assessedSpeed,speed,assessedYaw,assessedPitch,yaw,pitch,safetyAngle());
    }
    private Vec eyes() throws ReflectiveOperationException { return position(player).add(0,GameAccess.value(player,"func_70047_e|getEyeHeight"),0); }
    private static double number(Object e,String field) throws ReflectiveOperationException { return GameAccess.number(e,field); }
    private static Vec position(Object e) throws ReflectiveOperationException { return new Vec(number(e,"field_70165_t|posX"),number(e,"field_70163_u|posY"),number(e,"field_70161_v|posZ")); }
    private static double clamp(double n,double low,double high) { return Math.max(low,Math.min(high,n)); }
}
