package streamproof;

import java.nio.file.*;
import java.util.*;
import streamproof.BowGeometry.*;

public final class BowDiagnosticsProbe {
    private static final Vec ZERO=new Vec(0,0,0),VELOCITY=new Vec(0,0,3);
    private static BowDiagnostics.Snapshot snapshot() {
        List<Vec> path=new ArrayList<>(); for(int i=0;i<=120;i++)path.add(new Vec(0,0,i*3));
        return new BowDiagnostics.Snapshot(20,0,0,0,0,0,ZERO,new Vec(0,0,10),"Still","HIT",true,false,path,new Object(),path);
    }
    public static void main(String[] args) throws Exception {
        Path root=Paths.get(".build/bow-diagnostic-test-"+System.nanoTime());
        BowLog log=new BowLog(root.resolve("session")); BowDiagnostics d=new BowDiagnostics(log);
        long t=10000000000L;
        d.release(snapshot(),t); Object hit=new Object();
        d.arrow(hit,true,ZERO,VELOCITY,false,t+100000000); d.hitMarker(t+200000000,false);
        check(d.hits==1 && d.misses==0 && d.unknown==0,"Unique owner-known hit marker");
        check(d.matched(hit),"Settled arrow identity retained");
        t+=1000000000L; d.release(snapshot(),t); Object gone=new Object();
        d.arrow(gone,true,ZERO,VELOCITY,false,t+100000000);
        d.expire(Collections.emptySet(),t+200000000);check(d.unknown==0,"Allow delayed marker after removal");
        d.expire(Collections.emptySet(),t+800000000);check(d.unknown==1 && d.hits==1,"Disappearance is unknown");
        t+=1000000000L; d.release(snapshot(),t);Object block=new Object();
        d.arrow(block,true,ZERO,VELOCITY,true,t+100000000);
        d.expire(Collections.singleton(block),t+400000000);check(d.misses==1,"Observed owner-known block impact");
        t+=1000000000L;d.release(snapshot(),t);d.release(snapshot(),t+1);
        d.arrow(new Object(),true,ZERO,VELOCITY,false,t+100000000);d.hitMarker(t+200000000,false);
        check(d.hits==1,"Ambiguous match incorrectly confirmed");
        d.expire(Collections.emptySet(),t+9000000000L);check(d.unknown==3,"Ambiguous shots should expire unknown");
        t+=10000000000L;d.release(snapshot(),t);Object unowned=new Object();
        d.arrow(unowned,null,ZERO,VELOCITY,false,t+100000000);d.hitMarker(t+200000000,false);
        check(d.hits==1,"Unknown owner must not confirm");d.expire(Collections.emptySet(),t+9000000000L);
        t+=10000000000L;d.release(snapshot(),t);Object concurrent=new Object();
        d.arrow(concurrent,true,ZERO,VELOCITY,false,t+100000000);d.hitMarker(t+200000000,true);
        check(d.hits==1,"Other owned arrows must prevent attribution");d.expire(Collections.emptySet(),t+9000000000L);
        t+=10000000000L;d.release(snapshot(),t);Object late=new Object();
        d.arrow(late,true,ZERO,VELOCITY,false,t+100000000);d.expire(Collections.emptySet(),t+200000000);
        d.hitMarker(t+400000000,false);check(d.hits==2,"Delayed hit marker should still be attributable");
        t+=1000000000L;d.release(snapshot(),t);Object verified=new Object();
        d.arrow(verified,true,ZERO,VELOCITY,false,t+100000000);d.release(snapshot(),t+150000000);
        d.hitMarker(t+200000000,false);check(d.hits==2,"Unmatched simultaneous shot must prevent attribution");
        d.close();check(log.await(3000),"Writer shutdown");check(log.error.isEmpty(),"Log error: "+log.error);
        String rows=new String(Files.readAllBytes(root.resolve("session/bow-current.jsonl")),java.nio.charset.StandardCharsets.UTF_8);
        check(rows.contains("\"expectedFlight\"") && rows.contains("arrow_disappeared") && rows.contains("unattributed_hit_marker"),"Missing evidence fields");
        check(!rows.contains("serverAddress") && !rows.contains("playerName") && !rows.contains("chat"),"Private field in diagnostics");
        BowLog rotating=new BowLog(root.resolve("rotation"),1000);
        for(int i=0;i<1000;i++)rotating.offer("{\"test\":"+i+",\"data\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}");
        rotating.close();check(rotating.await(3000),"Rotation writer shutdown");
        long bytes=0; int files=0;
        try(java.nio.file.DirectoryStream<Path> stream=Files.newDirectoryStream(root.resolve("rotation"))) {
            for(Path p:stream) { bytes+=Files.size(p); files++; }
        }
        check(files<=2 && bytes<=2000,"Rotation exceeds two bounded files");
        Path bad=root.resolve("not-a-directory");Files.write(bad,new byte[]{1});BowLog failed=new BowLog(bad);
        check(failed.await(3000) && !failed.error.isEmpty(),"Write failure should report error");
        System.out.println("PASS bow diagnostics: verified/ambiguous/unknown ownership, hit markers, block misses, disappearance grace, timeouts, bounded async logs and writer failures");
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
