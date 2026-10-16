package streamproof;

import java.util.*;
import streamproof.BowGeometry.*;

/** Tick-by-tick collision assessment. No world access occurs outside Scene. */
public final class BowFlight {
    private BowFlight() {}
    public enum Result { HIT, BLOCK, ENTITY, WATER, LIMIT, MISS, UNKNOWN, UNREACHABLE }
    public static final class Budget {
        public final int limit; private int used;
        public Budget(int limit) { this.limit=limit; }
        public boolean take() { if (used>=limit)return false; used++; return true; }
        public int used() { return used; }
    }
    public static final class Surface {
        public final double fraction; public final Result kind;
        public Surface(double fraction,Result kind) { this.fraction=fraction; this.kind=kind; }
    }
    public interface Scene { Surface surface(Vec from,Vec to); }
    public static final class Body {
        public final Object key; public final Box box; public final BowPhysics.TargetPath path;
        public Body(Object key,Box box,BowPhysics.TargetPath path) { this.key=key; this.box=box; this.path=path; }
        public Vec offset(double t) { double[] out=new double[3]; path.offset(t,out); return new Vec(out[0],out[1],out[2]); }
    }
    public static final class Assessment {
        public final Result result; public final double time,clearance; public final Vec point;
        public final boolean interior; public final List<Vec> flight;
        Assessment(Result result,double time,double clearance,Vec point,boolean interior,List<Vec> points) {
            this.result=result; this.time=time; this.clearance=clearance; this.point=point; this.interior=interior;
            this.flight=Collections.unmodifiableList(new ArrayList<>(points));
        }
        public boolean valid() { return result==Result.HIT && interior; }
    }
    public static Assessment trace(Vec start,double yaw,double pitch,double speed,Body target,List<Body> bodies,Scene scene,Budget budget) {
        List<Vec> points=new ArrayList<>(); points.add(start);
        if (speed<=0) return end(Result.UNREACHABLE,0,start,false,0,points);
        Vec velocity=BowGeometry.velocity(yaw,pitch,speed),position=start;
        boolean touched=false; int touchedTick=0;
        for (int n=0;n<120;n++) {
            if (!budget.take()) return end(Result.LIMIT,n,position,false,0,points);
            Vec next=position.add(velocity); points.add(next);
            Surface surface=scene.surface(position,next);
            double first=surface==null ? Double.POSITIVE_INFINITY : surface.fraction;
            Result kind=surface==null ? Result.MISS : surface.kind;
            Body hit=null;
            for (Body body:bodies) {
                Vec a=position.sub(body.offset(n)),b=next.sub(body.offset(n+1));
                double[] clip=body.box.grow(0.3).clip(a,b);
                if (clip!=null && clip[0]<first) { first=clip[0]; kind=body==target ? Result.HIT : Result.ENTITY; hit=body; }
            }
            if (!touched && kind!=Result.MISS && kind!=Result.HIT) return end(kind,n+Math.min(1,first),position.mix(next,Math.min(1,first)),false,0,points);
            if (hit==target && !touched) { touched=true; touchedTick=n; }
            if (touched) {
                Vec a=position.sub(target.offset(n)),b=next.sub(target.offset(n+1));
                double[] inner=target.box.grow(-0.05).clip(a,b);
                if (inner!=null) {
                    double fraction=(inner[0]+inner[1])/2;
                    Vec local=a.mix(b,fraction),world=position.mix(next,fraction);
                    return end(Result.HIT,n+fraction,world,true,target.box.clearance(local),points);
                }
                if (n-touchedTick>=2) return end(Result.HIT,n+1,next,false,0,points);
            }
            position=next;
            velocity=new Vec(velocity.x*BowPhysics.DRAG,velocity.y*BowPhysics.DRAG-BowPhysics.GRAVITY,velocity.z*BowPhysics.DRAG);
        }
        return end(touched ? Result.HIT : Result.MISS,120,position,false,0,points);
    }
    private static Assessment end(Result kind,double time,Vec p,boolean interior,double clearance,List<Vec> points) {
        return new Assessment(kind,time,clearance,p,interior,points);
    }
    public static final class Readiness {
        private Object target; private long previous=Long.MIN_VALUE; private int passes;
        public void reset() { target=null; previous=Long.MIN_VALUE; passes=0; }
        public boolean update(Object key,long tick,boolean valid) {
            if (!valid || key==null) { reset(); return false; }
            if (key==target && tick==previous)return passes>=2;
            if (key!=target || tick!=previous+1) passes=0;
            if (tick!=previous) passes++;
            target=key; previous=tick; return passes>=2;
        }
    }
}
