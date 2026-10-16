package streamproof;

import java.util.*;
import streamproof.BowGeometry.*;

/** Conservative evidence tracking. Disappearance and player damage are never treated as hits. */
public final class BowDiagnostics implements AutoCloseable {
    public static volatile BowDiagnostics active;
    private final BowLog log;
    private final String session=UUID.randomUUID().toString();
    private final List<Shot> shots=new ArrayList<>();
    private final Map<Object,Shot> tracked=new IdentityHashMap<>();
    private final Set<Object> settled=Collections.newSetFromMap(new WeakHashMap<Object,Boolean>());
    private long serial;
    public int hits,misses,unknown;
    public BowDiagnostics(BowLog log) { this.log=log; active=this; }
    public static final class Snapshot {
        public final int charge; public final double yaw,pitch,goalYaw,goalPitch,timing;
        public final Vec start,target; public final String mode,reason; public final boolean ready,external;
        public final List<Vec> expected,targetExpected; public final Object targetKey;
        public Snapshot(int charge,double yaw,double pitch,double goalYaw,double goalPitch,double timing,Vec start,Vec target,
                        String mode,String reason,boolean ready,boolean external,List<Vec> expected,Object targetKey,List<Vec> targetExpected) {
            this.charge=charge; this.yaw=yaw; this.pitch=pitch; this.goalYaw=goalYaw; this.goalPitch=goalPitch;
            this.timing=timing; this.start=start; this.target=target; this.mode=mode; this.reason=reason;
            this.ready=ready; this.external=external; this.expected=Collections.unmodifiableList(new ArrayList<>(expected));
            this.targetKey=targetKey; this.targetExpected=Collections.unmodifiableList(new ArrayList<>(targetExpected));
        }
    }
    private static final class Shot {
        final long id,time; final Snapshot data;
        Object arrow; boolean verified,ambiguous,done,trajectory,unexpected;
        int observations; Vec last;
        long firstObserved,missingSince,blockSince; int expectedIndex;
        Shot(long id,long time,Snapshot data) { this.id=id; this.time=time; this.data=data; }
    }
    public void release(Snapshot snapshot,long now) {
        if (shots.size()>=32) finish(shots.get(0),"unknown","tracking_limit");
        Shot shot=new Shot(++serial,now,snapshot); shots.add(shot);
        StringBuilder row=new StringBuilder("{\"event\":\"shot\",\"shot\":").append(shot.id)
            .append(",\"charge\":").append(snapshot.charge).append(",\"yaw\":").append(BowLog.number(snapshot.yaw))
            .append(",\"pitch\":").append(BowLog.number(snapshot.pitch)).append(",\"goalYaw\":").append(BowLog.number(snapshot.goalYaw))
            .append(",\"goalPitch\":").append(BowLog.number(snapshot.goalPitch)).append(",\"leadTicks\":").append(BowLog.number(snapshot.timing))
            .append(",\"mode\":\"").append(safe(snapshot.mode)).append("\",\"assessment\":\"").append(safe(snapshot.reason))
            .append("\",\"ready\":").append(snapshot.ready).append(",\"start\":").append(vec(snapshot.start))
            .append(",\"target\":").append(vec(snapshot.target)).append(",\"expectedFlight\":[");
        for (int i=0;i<snapshot.expected.size();i++) { if(i>0)row.append(','); row.append(vec(snapshot.expected.get(i))); }
        emit(row.append("]}").toString());
    }
    /** Owner-known matches are preferred. Ambiguous evidence stays unconfirmed. */
    public void arrow(Object key,Boolean localOwner,Vec position,Vec velocity,boolean inGround,long now) {
        if (Boolean.FALSE.equals(localOwner) || settled.contains(key))return;
        Shot shot=tracked.get(key);
        if (shot==null) {
            List<Shot> matches=new ArrayList<>();
            for (Shot s:shots) if (!s.done && s.arrow==null && now-s.time>=0 && now-s.time<1500000000L && s.data.start.distance(position)<4) {
                Vec expected=BowGeometry.velocity(s.data.yaw,s.data.pitch,1);
                double length=Math.sqrt(velocity.x*velocity.x+velocity.y*velocity.y+velocity.z*velocity.z);
                double dot=length==0 ? 0 : (expected.x*velocity.x+expected.y*velocity.y+expected.z*velocity.z)/length;
                if (dot>0.97) matches.add(s);
            }
            if (matches.size()!=1) { for (Shot s:matches)s.ambiguous=true; return; }
            shot=matches.get(0); shot.arrow=key; shot.verified=Boolean.TRUE.equals(localOwner); tracked.put(key,shot);
            shot.firstObserved=now;
            double closest=Double.POSITIVE_INFINITY;
            for(int i=0;i<Math.min(50,shot.data.expected.size());i++) {
                double distance=position.distance(shot.data.expected.get(i));
                if(distance<closest) { closest=distance; shot.expectedIndex=i; }
            }
        }
        if (shot.done)return;
        shot.last=position; shot.missingSince=0; shot.verified|=Boolean.TRUE.equals(localOwner);
        if (shot.observations<120) {
            emit("{\"event\":\"arrow\",\"shot\":"+shot.id+",\"ownerKnown\":"+shot.verified+",\"elapsedMs\":"+(now-shot.time)/1000000+",\"position\":"+vec(position)+"}");
            double expectedTick=shot.expectedIndex+(now-shot.firstObserved)/50000000.0;
            int i=(int)Math.round(expectedTick);
            if (i>=0 && i<shot.data.expected.size() && position.distance(shot.data.expected.get(i))>1)shot.trajectory=true;
            shot.observations++;
        }
        if(inGround && shot.blockSince==0)shot.blockSince=now;
        if(!inGround)shot.blockSince=0;
    }
    public void targetMotion(Object key,Vec observed,long now) {
        for(Shot s:shots)if(!s.done && s.data.targetKey==key) {
            int i=(int)((now-s.time)/50000000L);
            if(i>=0 && i<s.data.targetExpected.size() && observed.distance(s.data.targetExpected.get(i))>0.6)s.unexpected=true;
        }
    }
    /** Vanilla state 6 is an authoritative local-player arrow-hit marker, without an arrow ID. */
    public void hitMarker(long now,boolean otherOwnedArrows) {
        List<Shot> candidates=new ArrayList<>();
        for (Shot s:shots) if (!s.done && s.verified && !s.ambiguous && now-s.time<8000000000L)candidates.add(s);
        if (candidates.size()==1 && shots.size()==1 && !otherOwnedArrows)finish(candidates.get(0),"hit","server_player_hit_marker");
        else emit("{\"event\":\"unattributed_hit_marker\"}");
    }
    public void expire(Set<Object> present,long now) {
        for (Shot s:new ArrayList<>(shots)) {
            if (s.done)continue;
            if(s.blockSince!=0 && now-s.blockSince>250000000L)finish(s,s.verified && !s.ambiguous ? "miss" : "unknown","observed_block_impact");
            else if (s.arrow!=null && !present.contains(s.arrow)) {
                if(s.missingSince==0)s.missingSince=now;
                if(now-s.missingSince>500000000L)finish(s,"unknown","arrow_disappeared");
            }
            else if (now-s.time>8000000000L)finish(s,"unknown","timeout");
        }
    }
    public boolean matched(Object key) { return tracked.containsKey(key) || settled.contains(key); }
    public String summary() { return "Player hits "+hits+" / Block misses "+misses+" / Unknown "+unknown; }
    public void marker(long now,boolean otherOwnedArrows) { hitMarker(now,otherOwnedArrows); }
    private void finish(Shot shot,String outcome,String observed) {
        if (shot.done)return;
        shot.done=true;
        if (outcome.equals("hit"))hits++; else if(outcome.equals("miss"))misses++; else unknown++;
        String cause=shot.data.reason.equals("no_target") ? "no_target" : shot.data.external ? "external_camera_change" : !shot.data.ready ?
            (shot.data.reason.equals("BLOCK") || shot.data.reason.equals("ENTITY") ? "obstruction" :
            shot.data.reason.equals("timing_uncertainty") ? "timing_uncertainty" : "release_before_alignment") :
            shot.unexpected ? "unexpected_target_movement" : shot.trajectory ? "trajectory_mismatch" : "unknown";
        emit("{\"event\":\"outcome\",\"shot\":"+shot.id+",\"result\":\""+outcome+"\",\"observation\":\""+observed+"\",\"likelyCause\":\""+cause+"\"}");
        shots.remove(shot); if(shot.arrow!=null) { tracked.remove(shot.arrow); settled.add(shot.arrow); }
    }
    public void close() { if(active==this)active=null; for(Shot s:new ArrayList<>(shots))finish(s,"unknown","session_ended"); log.close(); }
    private void emit(String row) { log.offer("{\"session\":\""+session+"\","+row.substring(1)); }
    private static String safe(String value) { return value==null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_ .-]",""); }
    private static String vec(Vec v) { return v==null ? "null" : "["+BowLog.number(v.x)+","+BowLog.number(v.y)+","+BowLog.number(v.z)+"]"; }
}
