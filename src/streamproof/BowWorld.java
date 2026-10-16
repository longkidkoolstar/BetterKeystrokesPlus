package streamproof;

import java.lang.reflect.Constructor;
import java.util.*;
import streamproof.BowGeometry.*;

/** Main-thread adapter to vanilla collision shapes; reflection avoids redistributing MC classes. */
public final class BowWorld implements BowFlight.Scene {
    private final Object world,player;
    private final Constructor<?> vector,box;
    private final Object water,lava;
    public BowWorld(Object world,Object player) throws ReflectiveOperationException {
        this.world=world; this.player=player;
        vector=Class.forName("net.minecraft.util.Vec3").getConstructor(double.class,double.class,double.class);
        box=Class.forName("net.minecraft.util.AxisAlignedBB").getConstructor(double.class,double.class,double.class,double.class,double.class,double.class);
        Class<?> material=Class.forName("net.minecraft.block.material.Material");
        water=GameAccess.get(material,"field_151586_h|water"); lava=GameAccess.get(material,"field_151587_i|lava");
    }
    public static Box bounds(Object entity) throws ReflectiveOperationException {
        Object b=GameAccess.call(entity,"func_174813_aQ|getEntityBoundingBox"); return readBox(b);
    }
    private static Box readBox(Object b) throws ReflectiveOperationException {
        return new Box(GameAccess.number(b,"field_72340_a|minX"),GameAccess.number(b,"field_72338_b|minY"),GameAccess.number(b,"field_72339_c|minZ"),
            GameAccess.number(b,"field_72336_d|maxX"),GameAccess.number(b,"field_72337_e|maxY"),GameAccess.number(b,"field_72334_f|maxZ"));
    }
    public BowFlight.Surface surface(Vec from,Vec to) {
        try {
            // Include flowing liquids: vanilla ray tracing only collides with some liquid states.
            Object sweep=box.newInstance(Math.min(from.x,to.x)-0.125,Math.min(from.y,to.y)-0.125,Math.min(from.z,to.z)-0.125,
                Math.max(from.x,to.x)+0.125,Math.max(from.y,to.y)+0.125,Math.max(from.z,to.z)+0.125);
            if(Boolean.TRUE.equals(GameAccess.call(world,"func_72875_a|isAABBInMaterial",sweep,water))
                || Boolean.TRUE.equals(GameAccess.call(world,"func_72875_a|isAABBInMaterial",sweep,lava)))return new BowFlight.Surface(0,BowFlight.Result.WATER);
            Object hit=GameAccess.call(world,"func_147447_a|rayTraceBlocks",vector.newInstance(from.x,from.y,from.z),vector.newInstance(to.x,to.y,to.z),true,false,false);
            if (hit==null) return null;
            Object position=GameAccess.call(hit,"func_178782_a|getBlockPos");
            Object block=GameAccess.call(GameAccess.call(world,"func_180495_p|getBlockState",position),"func_177230_c|getBlock");
            Object material=GameAccess.call(block,"func_149688_o|getMaterial");
            boolean fluid=Boolean.TRUE.equals(GameAccess.call(material,"func_76224_d|isLiquid"));
            Object v=GameAccess.get(hit,"field_72307_f|hitVec");
            Vec point=new Vec(GameAccess.number(v,"field_72450_a|xCoord"),GameAccess.number(v,"field_72448_b|yCoord"),GameAccess.number(v,"field_72449_c|zCoord"));
            double distance=from.distance(to),fraction=distance<1e-9 ? 0 : Math.min(1,from.distance(point)/distance);
            return new BowFlight.Surface(fraction,fluid ? BowFlight.Result.WATER : BowFlight.Result.BLOCK);
        } catch (ReflectiveOperationException | RuntimeException failure) { return new BowFlight.Surface(0,BowFlight.Result.UNKNOWN); }
    }
    /** Highest collision floor crossed by a downward-moving footprint. NaN means no ground. */
    public double floor(Box footprint,double fromY,double toY) {
        try {
            Object query=box.newInstance(footprint.x0+0.02,Math.min(fromY,toY)-0.01,footprint.z0+0.02,
                footprint.x1-0.02,Math.max(fromY,toY)+0.01,footprint.z1-0.02);
            List<?> collisions=(List<?>)GameAccess.call(world,"func_72945_a|getCollidingBoundingBoxes",player,query);
            double highest=Double.NEGATIVE_INFINITY;
            for (Object value:collisions) {
                Box b=readBox(value);
                if (b.y1<=fromY+0.03 && b.y1>=toY-0.02) highest=Math.max(highest,b.y1);
            }
            return highest==Double.NEGATIVE_INFINITY ? Double.NaN : highest;
        } catch (ReflectiveOperationException | RuntimeException failure) { return Double.NaN; }
    }
}
