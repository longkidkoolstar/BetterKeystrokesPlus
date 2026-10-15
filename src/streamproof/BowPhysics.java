package streamproof;

/** Air trajectory for 1.8.9: move, drag 0.99, then gravity 0.05 each tick. */
public final class BowPhysics {
    public static final double DRAG=0.99, GRAVITY=0.05;
    private BowPhysics() {}
    public interface TargetPath { void offset(double ticks,double[] result); }
    public static final TargetPath STILL=new TargetPath() {
        public void offset(double ticks,double[] result) { result[0]=result[1]=result[2]=0; }
    };
    public static double speed(int drawTicks) {
        double f=Math.max(0,drawTicks)/20.0;
        f=Math.min(1,(f*f+2*f)/3);
        return f<0.1 ? 0 : 3*f;
    }
    public static double travel(double t) {
        int n=(int)Math.floor(t); double d=Math.pow(DRAG,n);
        return (1-d)/(1-DRAG)+(t-n)*d;
    }
    public static double drop(double t) { return GRAVITY/(1-DRAG)*(t-travel(t)); }
    // Returns yaw, pitch, time to intercept; null when this charge cannot reach it.
    public static double[] solve(double x,double y,double z,double vx,double vy,double vz,double speed) {
        if (!Double.isFinite(vx+vy+vz)) return null;
        return solve(x,y,z,speed,new TargetPath() {
            public void offset(double ticks,double[] result) {
                result[0]=vx*ticks; result[1]=vy*ticks; result[2]=vz*ticks;
            }
        });
    }
    public static double[] solve(double x,double y,double z,double speed,TargetPath path) {
        if (!(speed>0) || !Double.isFinite(x+y+z+speed) || path==null) return null;
        double[] offset=new double[3];
        double previous=0.01;
        for (double t=0.25;t<=120;t+=0.25) {
            if (required(x,y,z,path,offset,t)<=speed*speed) {
                double low=previous, high=t;
                for (int i=0;i<36;i++) {
                    double mid=(low+high)/2;
                    if (required(x,y,z,path,offset,mid)>speed*speed) low=mid; else high=mid;
                }
                double time=(low+high)/2, a=travel(time);
                path.offset(time,offset);
                double dx=(x+offset[0])/a, dy=(y+offset[1]+drop(time))/a, dz=(z+offset[2])/a;
                return new double[]{Math.toDegrees(Math.atan2(dz,dx))-90,
                    -Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),time};
            }
            previous=t;
        }
        return null;
    }
    private static double required(double x,double y,double z,TargetPath path,double[] offset,double t) {
        path.offset(t,offset);
        double a=travel(t), dx=x+offset[0], dy=y+offset[1]+drop(t), dz=z+offset[2];
        return (dx*dx+dy*dy+dz*dz)/(a*a);
    }
    public static double wrap(double angle) { return ((angle+180)%360+360)%360-180; }
}
