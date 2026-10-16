package streamproof;

/** Owns only switches made by Auto Tool. No game objects, clocks or input synthesis. */
public final class AutoToolState {
    public static final int NONE=-1;
    private int original=NONE,owned=NONE;
    private Object pendingBlock;
    private long pendingAt,pendingWait,combatAt;
    private boolean pending,combat,manual;

    public void reset() {
        original=owned=NONE; pendingBlock=null; pending=false; combat=false; manual=false;
    }
    public void attacked(long now) { combat=true; combatAt=now; }
    public boolean recentCombat(long now) { return combat && now>=combatAt && now-combatAt<=1000000000L; }
    public boolean ownsSlot() { return owned!=NONE; }
    public int originalSlot() { return original; }
    public boolean waiting() { return pending; }
    public boolean manualOverride() { return manual; }
    public void manualSelection() { original=owned=NONE; clearPending(); manual=true; }
    private void clearPending() { pending=false; pendingBlock=null; }
    public int stop(int current,boolean switchBack) {
        int restore=switchBack && owned==current && original>=0 && original<9 ? original : NONE;
        original=owned=NONE; clearPending();
        return restore==current ? NONE : restore;
    }
    public int step(long now,int current,boolean attack,boolean allowed,Object block,int best,
                    long delayMillis,boolean guard,long holdMillis,boolean switchBack) {
        if(current<0 || current>8) { reset(); return NONE; }
        if(owned!=NONE && current!=owned) manualSelection();
        if(!attack || !allowed || block==null) {
            int action=stop(current,switchBack);
            if(!attack) manual=false;
            return action;
        }
        if(manual) return NONE;
        if(owned!=NONE) {
            if(best>=0 && best<9 && best!=current) { owned=best; return best; }
            return NONE;
        }
        if(!pending || !block.equals(pendingBlock)) {
            pending=true; pendingBlock=block; pendingAt=now;
            long hold=guard && recentCombat(now) ? holdMillis : 0;
            pendingWait=Math.min(2000,Math.max(0,Math.max(delayMillis,hold)))*1000000L;
        }
        if(now<pendingAt) { pendingAt=now; return NONE; }
        if(now-pendingAt<pendingWait || best<0 || best>8 || best==current) return NONE;
        original=current; owned=best; clearPending(); return best;
    }
    /** Vanilla efficiency bonus and harvest penalty; shared status effects do not change ranking. */
    public static double rate(double speed,int efficiency,boolean harvest) {
        if(!Double.isFinite(speed) || speed<=0) return 0;
        if(speed>1 && efficiency>0) speed+=(double)efficiency*efficiency+1;
        return speed/(harvest ? 30 : 100);
    }
    public static int best(double[] rates,int current) {
        if(current<0 || current>=rates.length) return NONE;
        int best=current; double score=rates[current];
        for(int slot=0;slot<Math.min(9,rates.length);slot++) {
            if(Double.isFinite(rates[slot]) && rates[slot]>score+1.0e-6) { best=slot; score=rates[slot]; }
        }
        return best;
    }
}
