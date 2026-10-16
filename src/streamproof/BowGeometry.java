package streamproof;

/** Immutable geometry shared by the solver, collision checks and diagnostic snapshots. */
public final class BowGeometry {
    private BowGeometry() {}
    public static final class Vec {
        public final double x,y,z;
        public Vec(double x,double y,double z) { this.x=x; this.y=y; this.z=z; }
        public Vec add(double x,double y,double z) { return new Vec(this.x+x,this.y+y,this.z+z); }
        public Vec add(Vec v) { return add(v.x,v.y,v.z); }
        public Vec sub(Vec v) { return add(-v.x,-v.y,-v.z); }
        public Vec mix(Vec v,double t) { return add((v.x-x)*t,(v.y-y)*t,(v.z-z)*t); }
        public double distance(Vec v) { return Math.sqrt(square(x-v.x)+square(y-v.y)+square(z-v.z)); }
    }
    public static final class Box {
        public final double x0,y0,z0,x1,y1,z1;
        public Box(double x0,double y0,double z0,double x1,double y1,double z1) {
            this.x0=x0; this.y0=y0; this.z0=z0; this.x1=x1; this.y1=y1; this.z1=z1;
        }
        public Box move(Vec v) { return new Box(x0+v.x,y0+v.y,z0+v.z,x1+v.x,y1+v.y,z1+v.z); }
        public Box grow(double n) { return new Box(x0-n,y0-n,z0-n,x1+n,y1+n,z1+n); }
        public Vec point(double height) { return new Vec((x0+x1)/2,y0+(y1-y0)*height,(z0+z1)/2); }
        public double clearance(Vec v) { return Math.min(Math.min(Math.min(v.x-x0,x1-v.x),Math.min(v.y-y0,y1-v.y)),Math.min(v.z-z0,z1-v.z)); }
        /** Parametric entry/exit into a segment, including a segment starting inside. */
        public double[] clip(Vec a,Vec b) {
            if (x0>x1 || y0>y1 || z0>z1) return null;
            double low=0,high=1;
            double[] starts={a.x,a.y,a.z},ends={b.x,b.y,b.z},mins={x0,y0,z0},maxs={x1,y1,z1};
            for (int i=0;i<3;i++) {
                double d=ends[i]-starts[i];
                if (Math.abs(d)<1e-12) { if (starts[i]<mins[i] || starts[i]>maxs[i])return null; }
                else {
                    double p=(mins[i]-starts[i])/d,q=(maxs[i]-starts[i])/d;
                    low=Math.max(low,Math.min(p,q)); high=Math.min(high,Math.max(p,q));
                    if (low>high) return null;
                }
            }
            return new double[]{low,high};
        }
    }
    public static Vec velocity(double yaw,double pitch,double speed) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new Vec(-Math.sin(y)*Math.cos(p)*speed,-Math.sin(p)*speed,Math.cos(y)*Math.cos(p)*speed);
    }
    public static Vec launch(Vec eyes,double yaw) {
        return eyes.add(-Math.cos(Math.toRadians(yaw))*0.16,-0.1,-Math.sin(Math.toRadians(yaw))*0.16);
    }
    public static double square(double v) { return v*v; }
}
