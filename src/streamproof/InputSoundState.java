package streamproof;

import java.util.*;

/** Numeric, local-only sound events. This class never presses an input. */
public final class InputSoundState {
    public interface Sink { void offer(String packet); }
    private final Sink sink;
    private final String session=UUID.randomUUID().toString().replace("-","");
    private final boolean[] keys=new boolean[256];
    private long sequence;
    private boolean active;
    public InputSoundState(Sink sink) { this.sink=sink; }
    private void send(String event) { sink.offer("BKS1 "+session+" "+(++sequence)+" "+event); }
    public String session() { return session; }
    public boolean active() { return active; }
    public void mode(boolean next,boolean[] physical) {
        if(next!=active) {
            active=next; Arrays.fill(keys,false);
            if(next && physical!=null) System.arraycopy(physical,0,keys,0,Math.min(physical.length,keys.length));
        }
        send("H "+(active ? 1 : 0));
    }
    public void key(int code,boolean down) {
        if(!active || code<=0 || code>=keys.length || keys[code]==down) return;
        int mapped=map(code); if(mapped==0) return;
        keys[code]=down; send("K "+mapped+" "+(down ? 1 : 0));
    }
    public void click(int button) { if(active && button>=0 && button<=1) send("M "+(button+1)); }
    /** LWJGL 2 scan codes to Mechvibes/iohook's Windows key codes. */
    public static int map(int code) {
        if(code>0 && code<=88) return code;
        switch(code) {
            case 100:return 91; case 101:return 92; case 102:return 93;
            case 141:return 3597; case 156:return 3612; case 157:return 3677;
            case 181:return 3637; case 183:return 3639; case 184:return 3640;
            case 197:return 3653; case 199:return 60999; case 200:return 61000;
            case 201:return 61001; case 203:return 61003; case 205:return 61005;
            case 207:return 61007; case 208:return 61008; case 209:return 61009;
            case 210:return 61010; case 211:return 61011; case 219:return 3675;
            case 220:return 3676;
            default:return 0;
        }
    }
}
