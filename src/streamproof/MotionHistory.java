package streamproof;

/** Short, local motion history. Repeated movement is a hypothesis, never a guarantee. */
public final class MotionHistory {
    private static final int CAPACITY=96;
    private final double[] dx=new double[CAPACITY],dy=new double[CAPACITY],dz=new double[CAPACITY];
    private int next,count;
    private double lastX,lastY,lastZ,groundY;
    private long lastTick=Long.MIN_VALUE,groundTick=Long.MIN_VALUE;
    private boolean grounded;
    public long lastTick() { return lastTick; }
    public int samples() { return count; }
    public void sample(long tick,double x,double y,double z,boolean onGround) {
        if (!Double.isFinite(x+y+z)) { clear(); return; }
        if (lastTick!=Long.MIN_VALUE && tick==lastTick) return;
        double vx=x-lastX,vy=y-lastY,vz=z-lastZ;
        if (lastTick==Long.MIN_VALUE || tick-lastTick!=1 || vx*vx+vy*vy+vz*vz>9) {
            count=next=0; groundTick=Long.MIN_VALUE;
        } else {
            dx[next]=vx; dy[next]=vy; dz[next]=vz;
            next=(next+1)%CAPACITY; count=Math.min(CAPACITY,count+1);
        }
        lastX=x; lastY=y; lastZ=z; lastTick=tick; grounded=onGround;
        if (onGround) { groundY=y; groundTick=tick; }
    }
    public void clear() { count=next=0; lastTick=groundTick=Long.MIN_VALUE; }
    private double at(double[] values,int ago) { return values[(next-1-ago+CAPACITY)%CAPACITY]; }
    private boolean stopped() {
        return count>=2 && Math.hypot(at(dx,0),at(dz,0))<0.018 && Math.hypot(at(dx,1),at(dz,1))<0.018;
    }
    private double velocity(double[] values) {
        if (count==0 || stopped()) return 0;
        if (count==1 || at(dx,0)*at(dx,1)+at(dz,0)*at(dz,1)<-0.001) return at(values,0);
        double result=0,weight=0;
        double[] weights={0.6,0.3,0.1};
        for (int i=0;i<Math.min(count,3);i++) { result+=weights[i]*at(values,i); weight+=weights[i]; }
        return result/weight;
    }
    public Forecast forecast(boolean patterns,int horizon,double strength) {
        horizon=Math.max(1,Math.min(60,horizon)); strength=Math.max(0,Math.min(1.2,strength));
        int period=0; double confidence=0;
        if (patterns && !stopped()) {
            double bestError=Double.POSITIVE_INFINITY;
            for (int p=6;p<=32 && 3*p<=count;p++) {
                double mx=0,mz=0,vx=0,vz=0,error=0;
                for (int i=0;i<3*p;i++) { mx+=at(dx,i); mz+=at(dz,i); }
                mx/=3*p; mz/=3*p;
                for (int i=0;i<3*p;i++) { vx+=square(at(dx,i)-mx); vz+=square(at(dz,i)-mz); }
                double variance=(vx+vz)/(3*p);
                if (variance<0.0025) continue;
                double meanSpeed=Math.hypot(mx,mz);
                double axisX=vx>=vz ? 1 : 0,axisZ=vx>=vz ? 0 : 1;
                if (meanSpeed>0.05) { axisX=-mz/meanSpeed; axisZ=mx/meanSpeed; }
                double sideways=0;
                for (int i=0;i<3*p;i++) sideways+=square((at(dx,i)-mx)*axisX+(at(dz,i)-mz)*axisZ);
                // Packet stop/go jitter along a straight line is not a strafe cycle.
                if (sideways/(3*p)<0.0025) continue;
                for (int i=0;i<2*p;i++) error+=square(at(dx,i)-at(dx,i+p))+square(at(dz,i)-at(dz,i+p));
                double mismatch=error/(2*p)/(2*variance);
                if (mismatch>0.10) continue;
                // Reject a new stop/turn immediately even when older cycles still match.
                boolean recentMatch=true;
                double tolerance=0.035+0.20*Math.sqrt(variance);
                for (int i=0;i<2;i++) if (Math.hypot(at(dx,i)-at(dx,i+p),at(dz,i)-at(dz,i+p))>tolerance) recentMatch=false;
                if (!recentMatch) continue;
                int sign=0,reversals=0;
                for (int i=3*p-1;i>=0;i--) {
                    double v=(at(dx,i)-mx)*axisX+(at(dz,i)-mz)*axisZ;
                    int s=Math.abs(v)<0.035 ? 0 : (v>0 ? 1 : -1);
                    if (s!=0) { if (sign!=0 && sign!=s) reversals++; sign=s; }
                }
                if (reversals<4) continue;
                double score=mismatch+p*0.0001;
                if (score<bestError) { bestError=score; period=p; confidence=1-mismatch; }
            }
        }
        double[] x=new double[horizon+1],y=new double[horizon+1],z=new double[horizon+1];
        double linearX=velocity(dx),linearZ=velocity(dz);
        double patternWeight=period==0 ? 0 : Math.min(1,(confidence-0.85)/0.15);
        double vertical=count==0 || grounded ? 0 : Math.max(-1,Math.min(0.6,at(dy,0)));
        boolean airborne=false;
        for (int i=0;i<Math.min(count,3);i++) if (Math.abs(at(dy,i))>0.025) airborne=!grounded;
        boolean knownFloor=groundTick!=Long.MIN_VALUE && lastTick-groundTick<=30 && lastY>=groundY && lastY-groundY<3;
        for (int i=1;i<=horizon;i++) {
            double px=linearX,pz=linearZ;
            if (period!=0) {
                int phase=(i-1)%period; double tx=0,tz=0;
                for (int cycle=1;cycle<=3;cycle++) { tx+=at(dx,cycle*period-1-phase); tz+=at(dz,cycle*period-1-phase); }
                px=px*(1-patternWeight)+tx/3*patternWeight;
                pz=pz*(1-patternWeight)+tz/3*patternWeight;
            }
            x[i]=x[i-1]+px*strength; z[i]=z[i-1]+pz*strength;
            y[i]=y[i-1];
            // Short jump estimate; avoid projecting a brief ascent upward forever.
            if (airborne && i<=(knownFloor ? 12 : 6)) {
                vertical=(vertical-0.08)*0.98;
                double height=y[i-1]+vertical;
                if (knownFloor && height<groundY-lastY) { height=groundY-lastY; vertical=0; }
                y[i]=height;
            }
        }
        for (int i=1;i<=horizon;i++) y[i]*=strength;
        return new Forecast(x,y,z,stopped() && grounded ? "Still" : period!=0 ? "Strafe" : "Recent motion",period,confidence);
    }
    private static double square(double v) { return v*v; }
    public static final class Forecast implements BowPhysics.TargetPath {
        private final double[] x,y,z;
        public final String mode;
        public final int period;
        public final double confidence;
        private Forecast(double[] x,double[] y,double[] z,String mode,int period,double confidence) {
            this.x=x; this.y=y; this.z=z; this.mode=mode; this.period=period; this.confidence=confidence;
        }
        public void offset(double ticks,double[] result) {
            double t=Math.max(0,Math.min(x.length-1,ticks)); int i=(int)t,j=Math.min(x.length-1,i+1); double f=t-i;
            result[0]=x[i]+(x[j]-x[i])*f; result[1]=y[i]+(y[j]-y[i])*f; result[2]=z[i]+(z[j]-z[i])*f;
        }
    }
}
