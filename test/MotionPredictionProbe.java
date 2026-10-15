package streamproof;

public final class MotionPredictionProbe {
    public static void main(String[] args) {
        MotionHistory still=new MotionHistory();
        for (int i=0;i<120;i++) still.sample(i,0.003*Math.sin(i),0,0.003*Math.cos(i),true);
        MotionHistory.Forecast f=still.forecast(true,40,1);
        if (!f.mode.equals("Still")) throw new AssertionError("Stationary jitter became movement");
        offset(f,30,0,0,0);
        MotionHistory uncertainGround=new MotionHistory();
        for (int i=0;i<10;i++) uncertainGround.sample(i,0,0,0,false);
        offset(uncertainGround.forecast(true,40,1),30,0,0,0);
        MotionHistory straight=new MotionHistory();
        for (int i=0;i<=30;i++) straight.sample(i,i*0.22,0,i*0.1,true);
        offset(straight.forecast(true,40,1),20,4.4,0,2);
        offset(straight.forecast(true,10,1),80,2.2,0,1);
        offset(straight.forecast(true,40,0.5),20,2.2,0,1);
        offset(straight.forecast(true,40,0),20,0,0,0);
        if (straight.forecast(true,40,1).period!=0) throw new AssertionError("Straight movement mistaken for strafing");
        MotionHistory packetSteps=new MotionHistory(); double packetX=0;
        packetSteps.sample(0,0,0,0,true);
        for (int i=1;i<=96;i++) { if (i%3==0) packetX+=0.66; packetSteps.sample(i,packetX,0,0,true); }
        if (packetSteps.forecast(true,40,1).period!=0) throw new AssertionError("Packet stop/go steps mistaken for strafing");
        straight.sample(31,30*0.22,0,3,true); straight.sample(32,30*0.22,0,3,true);
        offset(straight.forecast(true,40,1),20,0,0,0);
        MotionHistory reverse=new MotionHistory();
        for (int i=0;i<=30;i++) reverse.sample(i,i*0.25,0,0,true);
        reverse.sample(31,7.25,0,0,true);
        offset(reverse.forecast(true,40,1),6,-1.5,0,0);

        MotionHistory strafe=new MotionHistory(); double x=0;
        strafe.sample(0,0,0,0,true);
        for (int i=1;i<=96;i++) { x+=step(i-1); strafe.sample(i,x,0,0,true); }
        f=strafe.forecast(true,40,1);
        if (f.period!=16 || f.confidence<0.99) throw new AssertionError("Repeated strafe not recognized: "+f.period);
        offset(f,6,1.32,0,0); offset(f,12,0.88,0,0);
        if (strafe.forecast(false,40,1).period!=0) throw new AssertionError("Pattern toggle ignored");
        MotionHistory noisy=new MotionHistory(); double nx=0,nz=0;
        noisy.sample(0,0,0,0,true);
        for (int i=1;i<=96;i++) {
            nx+=step(i-1)+0.002*Math.sin(i*1.7); nz+=0.12+0.002*Math.cos(i*0.7);
            noisy.sample(i,nx,0,nz,true);
        }
        if (noisy.forecast(true,40,1).period!=16) throw new AssertionError("Noisy forward strafe not recognized");
        noisy.sample(97,nx,0,nz+0.3,true);
        if (noisy.forecast(true,40,1).period!=0) throw new AssertionError("Unexpected turn kept old pattern");
        // An independent future path lets us compare the previous straight-line model.
        double[] adaptive=BowPhysics.solve(0,0,35,3,f);
        double[] linear=BowPhysics.solve(0,0,35,-0.22,0,0,3);
        double adaptiveMiss=miss(adaptive,35),linearMiss=miss(linear,35);
        if (adaptiveMiss>1e-6 || linearMiss<0.5) throw new AssertionError("Pattern intercept did not improve");
        strafe.sample(97,x,0,0,true);
        if (strafe.forecast(true,40,1).period!=0) throw new AssertionError("Stopped target kept old strafe");
        strafe.sample(98,x,0,0,true); offset(strafe.forecast(true,40,1),30,0,0,0);

        MotionHistory irregular=new MotionHistory(); long seed=7; x=0; double z=0;
        irregular.sample(0,x,0,z,true);
        for (int i=1;i<=96;i++) {
            seed=(seed*1664525+1013904223L)&0xffffffffL; x+=((seed>>>8)%601-300)/1000.0;
            seed=(seed*1664525+1013904223L)&0xffffffffL; z+=((seed>>>8)%601-300)/1000.0;
            irregular.sample(i,x,0,z,true);
        }
        if (irregular.forecast(true,40,1).period!=0) throw new AssertionError("Irregular movement produced a pattern");
        irregular.sample(100,x,0,z,true); offset(irregular.forecast(true,40,1),20,0,0,0);
        irregular.sample(101,x+100,0,z,true); offset(irregular.forecast(true,40,1),20,0,0,0);
        irregular.sample(101,x+200,0,z,true);
        if (irregular.samples()!=0) throw new AssertionError("Duplicate tick sampled");
        if (still.samples()!=96) throw new AssertionError("History not bounded");

        MotionHistory jump=new MotionHistory(); jump.sample(0,0,0,0,true);
        double y=0,vy=0.42;
        for (int i=1;i<=5;i++) { y+=vy; jump.sample(i,0,y,0,false); vy=(vy-0.08)*0.98; }
        offset(jump.forecast(true,40,1),30,0,-y,0);

        double previousPitch=0,previousTime=0;
        for (int distance:new int[]{5,20,50,80}) {
            double[] shot=BowPhysics.solve(0,0,distance,0,0,0,3);
            if (shot==null || shot[1]>=previousPitch || shot[2]<=previousTime) throw new AssertionError("Distance/drop did not increase");
            stationaryHit(shot,distance,3); previousPitch=shot[1]; previousTime=shot[2];
        }
        double[] full=BowPhysics.solve(0,0,15,0,0,0,3),partial=BowPhysics.solve(0,0,15,0,0,0,BowPhysics.speed(10));
        if (partial==null || partial[1]>=full[1] || partial[2]<=full[2]) throw new AssertionError("Charge compensation");
        stationaryHit(partial,15,BowPhysics.speed(10));
        System.out.println("PASS adaptive prediction: still/jitter, straight motion, stops/reversals, strafe recognition, pattern toggle, irregular fallback, bounded history/horizon, resets and jump landing");
        System.out.printf(java.util.Locale.ROOT,"PASS independent strafe simulation: old linear miss %.2f blocks; adaptive miss %.6f blocks%n",linearMiss,adaptiveMiss);
        System.out.println("PASS distance/charge: forward-simulated hits at 5/20/50/80 blocks and partial charge; drop/flight time scale with distance");
    }
    private static double step(int phase) { return phase%16<8 ? 0.22 : -0.22; }
    private static double futureX(double ticks) {
        int n=(int)ticks; double x=0;
        for (int i=0;i<n;i++) x+=step(i);
        return x+step(n)*(ticks-n);
    }
    private static double[] arrow(double[] shot) {
        if (shot==null) throw new AssertionError("No shot");
        return arrow(shot,3);
    }
    private static double[] arrow(double[] shot,double speed) {
        double yaw=Math.toRadians(shot[0]),pitch=Math.toRadians(shot[1]);
        double vx=-Math.sin(yaw)*Math.cos(pitch)*speed,vy=-Math.sin(pitch)*speed,vz=Math.cos(yaw)*Math.cos(pitch)*speed;
        double x=0,y=0,z=0; int n=(int)shot[2];
        for (int i=0;i<n;i++) { x+=vx; y+=vy; z+=vz; vx*=0.99; vy=vy*0.99-0.05; vz*=0.99; }
        double f=shot[2]-n; return new double[]{x+vx*f,y+vy*f,z+vz*f};
    }
    private static double miss(double[] shot,double distance) {
        double[] pos=arrow(shot);
        return Math.sqrt(square(pos[0]-futureX(shot[2]))+square(pos[1])+square(pos[2]-distance));
    }
    private static void stationaryHit(double[] shot,double distance,double speed) {
        double[] pos=arrow(shot,speed); near(pos[0],0); near(pos[1],0); near(pos[2],distance);
    }
    private static void offset(BowPhysics.TargetPath path,double ticks,double x,double y,double z) {
        double[] result=new double[3]; path.offset(ticks,result); near(result[0],x); near(result[1],y); near(result[2],z);
    }
    private static void near(double a,double b) {
        if (!Double.isFinite(a) || Math.abs(a-b)>1e-6) throw new AssertionError(a+" != "+b);
    }
    private static double square(double value) { return value*value; }
}
