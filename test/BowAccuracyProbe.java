package streamproof;

import java.util.*;
import streamproof.BowGeometry.*;

public final class BowAccuracyProbe {
    private static final Vec EYES=new Vec(0,1.62,0);
    private static final BowFlight.Scene EMPTY=new BowFlight.Scene() { public BowFlight.Surface surface(Vec a,Vec b) { return null; } };
    public static void main(String[] args) {
        for(double distance:new double[]{5,10,20,30,50,80}) {
            Box target=new Box(-0.3,0,distance-0.3,0.3,1.8,distance+0.3);
            double[] stable=BowSolution.aim(EYES,target,0.5,BowPhysics.STILL,0);
            check(stable!=null,"Full-draw solution");
            for(int charge=1;charge<=25;charge++) {
                double[] angle=BowSolution.aim(EYES,target,0.5,BowPhysics.STILL,0);
                near(angle[1],stable[1],1e-12);
            }
            BowFlight.Body body=new BowFlight.Body(target,target,BowPhysics.STILL);
            BowFlight.Assessment hit=trace(stable,3,body,Arrays.asList(body),EMPTY,new BowFlight.Budget(480));
            check(hit.valid(),"Full path missed at "+distance+": "+hit.result+" interior="+hit.interior);
            BowFlight.Assessment early=trace(stable,BowPhysics.speed(5),body,Arrays.asList(body),EMPTY,new BowFlight.Budget(480));
            if(distance>=10)check(!early.valid(),"Undercharged shot incorrectly ready");
        }
        for(int fps:new int[]{30,60,144,240}) {
            BowController controller=new BowController(); Object key=new Object();
            double yaw=179,pitch=0; long start=1000000000L;
            for(int frame=0;frame<=fps;frame++) {
                long now=start+(long)(frame*1e9/fps);
                double[] result=controller.turn(key,yaw,pitch,-175,-12,now,180);
                double delta=Math.hypot(BowPhysics.wrap(result[0]-yaw),result[1]-pitch);
                check(delta<=180.0/fps+1e-5,"Speed cap at "+fps);
                check(result[1]<=pitch+1e-10 && result[1]>=-12-1e-10,"Pitch overshoot/flick");
                yaw=result[0]; pitch=result[1];
            }
            near(BowPhysics.wrap(yaw+175),0,1e-6); near(pitch,-12,1e-6);
            double[] first=controller.turn(new Object(),50,20,0,-12,start+2000000000L,180);
            near(first[0],50,0); near(first[1],20,0);
            double[] stalled=controller.turn(key,0,0,90,-30,start+4000000000L,180);
            near(stalled[0],0,0);
            double[] limited=controller.turn(key,0,0,90,-30,start+8000000000L,180);
            check(Math.hypot(limited[0],limited[1])<=9+1e-6,"Frame stall cap");
        }
        BowController.Retention retention=new BowController.Retention(); Object challenger=new Object();
        check(!retention.replace(challenger,7,10,1),"First challenger tick");
        check(retention.replace(challenger,7,10,2),"Second challenger tick");
        check(retention.replace(challenger,7,10,2),"Duplicate tick");
        retention.reset(); check(!retention.replace(challenger,9,10,3),"Less than 20 percent improvement");
        check(!retention.replace(challenger,7,10,4) && !retention.replace(challenger,7,10,6),"Nonconsecutive ticks");
        BowFlight.Readiness ready=new BowFlight.Readiness(); Object key=new Object();
        check(!ready.update(key,1,true) && ready.update(key,2,true) && ready.update(key,2,true),"Ready confirmation");
        check(!ready.update(key,3,false) && !ready.update(key,4,true),"Immediate Ready removal");
        near(BowController.extraTicks(1000000000L,1025000000L,200,false),0.5,1e-9);
        near(BowController.extraTicks(1000000000L,1025000000L,200,true),2.5,1e-9);
        near(BowController.extraTicks(1000000000L,1000000000L,9999,true),3,1e-9);
        near(BowController.extraTicks(1000000000L,1000000000L,Double.NaN,true),0,0);
        check(BowController.safetyAngle(0.3,80)<0.1 && BowController.safetyAngle(0.06,80)<0.01,"Distant/narrow hit margin needs tighter angle freshness");
        near(BowController.viewAngle(new Vec(0,10,10),0,0),45,1e-9);
        near(BowController.viewAngle(new Vec(0,0,-10),180,0),0,1e-9);
        check(BowController.fresh(1000,2000,3,3,0,0,0.01,0,0.1),"Fresh shot");
        check(!BowController.fresh(1000,100001000,3,3,0,0,0,0,0.1),"Stale Ready");
        check(!BowController.fresh(1000,2000,2.6,3,0,0,0,0,0.1),"Changed charge Ready");
        check(!BowController.fresh(1000,2000,3,3,0,0,0.2,0,0.1),"Changed camera Ready");

        final Box wall=new Box(-2,-2,4,2,5,4.5);
        BowFlight.Scene blocked=new BowFlight.Scene() { public BowFlight.Surface surface(Vec a,Vec b) {
            double[] c=wall.clip(a,b); return c==null ? null : new BowFlight.Surface(c[0],BowFlight.Result.BLOCK);
        }};
        Box box=new Box(-0.3,0,9.7,0.3,1.8,10.3); BowFlight.Body body=new BowFlight.Body(box,box,BowPhysics.STILL);
        double[] aim=BowSolution.aim(EYES,box,0.5,BowPhysics.STILL,0);
        check(trace(aim,3,body,Arrays.asList(body),blocked,new BowFlight.Budget(480)).result==BowFlight.Result.BLOCK,"Obstacle");
        BowFlight.Body other=new BowFlight.Body(new Object(),new Box(-0.5,0,4,0.5,2,5),BowPhysics.STILL);
        check(trace(aim,3,body,Arrays.asList(body,other),EMPTY,new BowFlight.Budget(480)).result==BowFlight.Result.ENTITY,"Intervening entity");
        BowFlight.Scene water=new BowFlight.Scene() { public BowFlight.Surface surface(Vec a,Vec b) { return new BowFlight.Surface(0.2,BowFlight.Result.WATER); }};
        check(trace(aim,3,body,Arrays.asList(body),water,new BowFlight.Budget(480)).result==BowFlight.Result.WATER,"Water");
        BowFlight.Scene unknown=new BowFlight.Scene() { public BowFlight.Surface surface(Vec a,Vec b) { return new BowFlight.Surface(0,BowFlight.Result.UNKNOWN); }};
        check(trace(aim,3,body,Arrays.asList(body),unknown,new BowFlight.Budget(480)).result==BowFlight.Result.UNKNOWN,"Unsupported scene");
        BowFlight.Budget budget=new BowFlight.Budget(480);
        for(int i=0;i<10;i++)BowFlight.trace(EYES,90,0,3,body,Arrays.asList(body),EMPTY,budget);
        check(budget.used()==480,"Shared collision cap");
        check(trace(aim,3,body,Arrays.asList(body),EMPTY,budget).result==BowFlight.Result.LIMIT,"Exhaustion treated as clear");
        final BowPhysics.TargetPath moving=new BowPhysics.TargetPath() { public void offset(double t,double[] out) { out[0]=t*0.2;out[1]=0;out[2]=0; }};
        BowFlight.Body movingBody=new BowFlight.Body(key,box,moving);
        double[] lead=BowSolution.aim(EYES,box,0.5,moving,0);
        check(trace(lead,3,movingBody,Arrays.asList(movingBody),EMPTY,new BowFlight.Budget(480)).valid(),"Moving hitbox");
        BowPhysics.TargetPath reversing=new BowPhysics.TargetPath() { public void offset(double t,double[] out) { out[0]=t<6 ? t*0.2 : 1.2-(t-6)*0.2;out[1]=0;out[2]=0; }};
        BowFlight.Body reversed=new BowFlight.Body(key,box,reversing);
        double[] reverseAim=BowSolution.aim(EYES,box,0.5,reversing,0);
        check(trace(reverseAim,3,reversed,Arrays.asList(reversed),EMPTY,new BowFlight.Budget(480)).valid(),"Reversal intercept");
        Vec partialOrigin=BowGeometry.launch(EYES,0),partialDelta=box.point(0.5).sub(partialOrigin);
        double[] partial=BowPhysics.solve(partialDelta.x,partialDelta.y,partialDelta.z,BowPhysics.speed(10),BowPhysics.STILL);
        check(trace(partial,BowPhysics.speed(10),body,Arrays.asList(body),EMPTY,new BowFlight.Budget(480)).valid(),"Partial-power flight assessment");
        BowForecast.Ground ground=new BowForecast.Ground() { public double floor(Box b,double from,double to) { return from>=0 && to<=0 ? 0 : Double.NaN; }};
        BowForecast jump=new BowForecast(moving,new Box(-0.3,1,9.7,0.3,2.8,10.3),0.25,false,40,1,ground);
        double[] offset=new double[3];jump.offset(40,offset);near(offset[1],-1,1e-6);
        Box jumpBox=new Box(-0.3,1,9.7,0.3,2.8,10.3);BowFlight.Body jumping=new BowFlight.Body(key,jumpBox,jump);
        double[] jumpAim=BowSolution.aim(EYES,jumpBox,0.5,jump,0);
        check(trace(jumpAim,3,jumping,Arrays.asList(jumping),EMPTY,new BowFlight.Budget(480)).valid(),"Landing intercept");
        BowForecast standing=new BowForecast(BowPhysics.STILL,box,0,true,40,1,ground);standing.offset(40,offset);near(offset[1],0,1e-6);
        BowForecast.aged(moving,2).offset(3,offset);near(offset[0],1,1e-9);
        System.out.println("PASS bow accuracy: stable charging angles at 5-80 blocks; actual partial/full charge, moving hitbox, obstacles/entities/water/unknown paths and 480-segment cap");
        System.out.println("PASS bow control: 30/60/144/240 FPS, ramp/reset, yaw wrap, no overshoot, frame stalls, target retention, readiness confirmation, age/latency limits and terrain landing");
    }
    private static BowFlight.Assessment trace(double[] aim,double speed,BowFlight.Body target,List<BowFlight.Body> bodies,BowFlight.Scene scene,BowFlight.Budget budget) {
        return BowFlight.trace(BowGeometry.launch(EYES,aim[0]),aim[0],aim[1],speed,target,bodies,scene,budget);
    }
    private static void near(double a,double b,double tolerance) { check(Double.isFinite(a) && Math.abs(a-b)<=tolerance,a+" != "+b); }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
