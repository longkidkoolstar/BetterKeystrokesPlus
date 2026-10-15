package streamproof;

/** Vanilla 1.8.9 hook in air: move, apply gravity, then drag (different from arrows). */
public final class RodPhysics {
    public static final double SPEED=1.5,DRAG=(double)0.92f,GRAVITY=0.03999999910593033;
    private RodPhysics() {}
    private static double travel(double t) {
        int n=(int)t; double d=Math.pow(DRAG,n);
        return (1-d)/(1-DRAG)+(t-n)*d;
    }
    private static double drop(double t) { return GRAVITY*DRAG/(1-DRAG)*(t-travel(t)); }
    /** Mean trajectory: vanilla's random casting spread cannot be predicted. */
    public static double[] solve(double x,double y,double z,BowPhysics.TargetPath path) {
        if (!Double.isFinite(x+y+z) || path==null) return null;
        double[] offset=new double[3]; double previous=0.0001;
        for (double t=0.05;t<=60;t+=0.05) {
            if (required(x,y,z,path,offset,t)<=SPEED*SPEED) {
                double low=previous,high=t;
                for (int i=0;i<36;i++) {
                    double mid=(low+high)/2;
                    if (required(x,y,z,path,offset,mid)>SPEED*SPEED) low=mid; else high=mid;
                }
                double time=(low+high)/2,a=travel(time);
                path.offset(time,offset);
                double dx=(x+offset[0])/a,dy=(y+offset[1]+drop(time))/a,dz=(z+offset[2])/a;
                return new double[]{Math.toDegrees(Math.atan2(dz,dx))-90,
                    -Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),time};
            }
            previous=t;
        }
        return null;
    }
    private static double required(double x,double y,double z,BowPhysics.TargetPath path,double[] offset,double time) {
        path.offset(time,offset);
        double a=travel(time),dx=x+offset[0],dy=y+offset[1]+drop(time),dz=z+offset[2];
        return (dx*dx+dy*dy+dz*dz)/(a*a);
    }
}
