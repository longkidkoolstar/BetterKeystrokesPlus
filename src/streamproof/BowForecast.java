package streamproof;

import streamproof.BowGeometry.*;

/** Bow-only terrain-aware vertical projection. Rod's MotionHistory API is unchanged. */
public final class BowForecast implements BowPhysics.TargetPath {
    public interface Ground { double floor(Box footprint,double fromY,double toY); }
    private final double[] x,y,z;
    public final boolean uncertain;
    public BowForecast(BowPhysics.TargetPath horizontal,Box initial,double vertical,boolean onGround,int horizon,double strength,Ground ground) {
        int length=Math.max(1,Math.min(60,horizon))+1;
        x=new double[length]; y=new double[length]; z=new double[length];
        double[] offset=new double[3]; double v=Math.max(-1,Math.min(0.6,vertical)); boolean grounded=onGround;
        boolean movingVertically=Math.abs(vertical)>0.025 || onGround;
        boolean unknown=false;
        for (int i=1;i<length;i++) {
            horizontal.offset(i,offset); x[i]=offset[0]; z[i]=offset[2];
            if (!movingVertically) { y[i]=0; continue; }
            double from=initial.y0+y[i-1],next=grounded ? from : from+(v-0.08)*0.98;
            Box footprint=initial.move(new Vec(x[i],0,z[i]));
            double floor=next<=from ? ground.floor(footprint,from+0.03,next-0.15) : Double.NaN;
            if (Double.isFinite(floor)) { next=floor; v=0; grounded=true; }
            else { grounded=false; v=(v-0.08)*0.98; if (next==from)next=from+v; }
            // Unknown drops are bounded, not extended through arbitrary unseen terrain.
            if (Math.abs(next-initial.y0)>4) { next=from; v=0; unknown=true; }
            y[i]=(next-initial.y0);
        }
        for (int i=1;i<length;i++) y[i]*=strength;
        uncertain=unknown;
    }
    public void offset(double ticks,double[] result) {
        double t=Math.max(0,Math.min(x.length-1,ticks)); int i=(int)t,j=Math.min(x.length-1,i+1); double f=t-i;
        result[0]=x[i]+(x[j]-x[i])*f; result[1]=y[i]+(y[j]-y[i])*f; result[2]=z[i]+(z[j]-z[i])*f;
    }
    public static BowPhysics.TargetPath aged(final BowPhysics.TargetPath path,final double ageTicks) {
        return new BowPhysics.TargetPath() {
            public void offset(double ticks,double[] out) { path.offset(ticks+Math.max(0,ageTicks),out); }
        };
    }
}
