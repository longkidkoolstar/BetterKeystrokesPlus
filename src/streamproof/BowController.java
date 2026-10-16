package streamproof;

/** Time-based steering and tick-based target retention, independent of game classes. */
public final class BowController {
    private Object owner;
    private long started,last;
    public void reset() { owner=null; last=0; }
    public double[] turn(Object target,double yaw,double pitch,double goalYaw,double goalPitch,long now,double speed) {
        if (owner!=target || last==0) { owner=target; started=last=now; return new double[]{yaw,pitch}; }
        double dt=Math.max(0,Math.min(0.05,(now-last)/1e9)); last=now;
        double ramp=Math.max(0,Math.min(1,(now-started)/150000000.0)); ramp=ramp*ramp*(3-2*ramp);
        double dy=BowPhysics.wrap(goalYaw-yaw),dp=goalPitch-pitch,length=Math.hypot(dy,dp);
        double f=length==0 ? 0 : Math.min(1,speed*dt*ramp/length);
        return new double[]{yaw+dy*f,Math.max(-90,Math.min(90,pitch+dp*f))};
    }
    public static final class Retention {
        private Object challenger;
        private long previousTick=Long.MIN_VALUE;
        private int count;
        public void reset() { challenger=null; count=0; previousTick=Long.MIN_VALUE; }
        public boolean replace(Object candidate,double score,double currentScore,long tick) {
            if (candidate==null || !(score<=currentScore*0.8)) { reset(); return false; }
            if (candidate==challenger && tick==previousTick)return count>=2;
            if (challenger!=candidate || tick!=previousTick+1) count=0;
            if (tick!=previousTick) count++;
            challenger=candidate; previousTick=tick;
            return count>=2;
        }
    }
    public static double extraTicks(long sampleNanos,long now,double roundTripMs,boolean timing) {
        double age=Math.max(0,(now-sampleNanos)/50000000.0);
        double extra=timing && Double.isFinite(roundTripMs) && roundTripMs>=0 ? Math.min(150,roundTripMs/2)/50 : 0;
        return age+extra;
    }
    public static double safetyAngle(double clearance,double distance) {
        return Math.max(0.0001,Math.min(0.2,Math.toDegrees(Math.atan2(Math.max(0,clearance-0.05),distance+1))/2));
    }
    public static double viewAngle(BowGeometry.Vec delta,double yaw,double pitch) {
        double targetYaw=Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        double targetPitch=-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        return Math.hypot(BowPhysics.wrap(targetYaw-yaw),targetPitch-pitch);
    }
    public static boolean fresh(long assessed,long now,double oldSpeed,double speed,double oldYaw,double oldPitch,double yaw,double pitch,double tolerance) {
        long elapsed=now-assessed;
        return elapsed>=0 && elapsed<100000000L && Math.abs(speed-oldSpeed)<1e-9 && Math.hypot(BowPhysics.wrap(yaw-oldYaw),pitch-oldPitch)<=tolerance;
    }
}
