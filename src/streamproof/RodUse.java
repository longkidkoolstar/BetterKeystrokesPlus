package streamproof;

/** Stops before the server-spawned hook arrives, without casting/reeling for the user. */
public final class RodUse {
    public static final long PENDING_NANOS=1500000000L;
    private Object owner;
    private boolean pending;
    private long castAt;
    public void reset() { owner=null; pending=false; }
    public void cast(long now) { if (!pending) { pending=true; castAt=now; } }
    public boolean ready(Object player,boolean rodInHand,boolean screenOpen,long now) throws ReflectiveOperationException {
        if (player!=owner) { owner=player; pending=false; }
        if (player==null || !rodInHand || screenOpen || !Boolean.TRUE.equals(GameAccess.call(player,"func_70089_S|isEntityAlive"))) return false;
        if (GameAccess.get(player,"field_71104_cf|fishEntity")!=null) { pending=false; return false; }
        if (pending && now-castAt<PENDING_NANOS) return false;
        pending=false;
        return true;
    }
}
