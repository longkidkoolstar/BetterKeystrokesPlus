package streamproof;
import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class InputSoundProbe {
    private static void check(boolean ok,String text) { if(!ok) throw new AssertionError(text); }
    public static void main(String[] args) throws Exception {
        List<String> events=new ArrayList<>(); InputSoundState s=new InputSoundState(events::add);
        s.key(42,true); s.click(0); check(events.isEmpty(),"disabled bridge is silent");
        s.mode(true,null); s.key(42,true); s.key(42,true); s.key(42,false); s.key(42,false);
        check(events.size()==3,"held automated Shift does not repeat down/up");
        check(events.get(1).endsWith("K 42 1") && events.get(2).endsWith("K 42 0"),"Shift sound transitions");
        s.click(0); s.click(0); s.click(1); check(events.size()==6,"every actual left/right action has its own click");
        check(events.get(5).endsWith("M 2"),"right mouse mapping");
        s.key(-100,true);s.key(0,true);s.key(255,true);s.click(2);check(events.size()==6,"invalid/unsupported input is ignored");
        s.key(17,true);s.key(17,false);s.key(17,true);check(events.size()==9,"automated W release/repress is heard");
        s.mode(false,null);s.click(0);s.key(42,true);check(events.size()==10,"menu/disable stops mirrored sounds");
        boolean[] physical=new boolean[256];physical[42]=true;s.mode(true,physical);s.key(42,true);
        check(events.size()==11,"activation snapshots an already-held physical key without replaying it");
        s.key(42,false);check(events.size()==12,"snapshot key release is still heard");
        check(InputSoundState.map(157)==3677 && InputSoundState.map(200)==61000 && InputSoundState.map(184)==3640,"Windows Mechvibes extended mapping");
        String previousSession=s.session();check(previousSession.matches("[a-f0-9]{32}"),"session is random numeric routing metadata");
        int seq=0;for(String event:events) {String[] p=event.split(" ");check(p[1].equals(previousSession) && Long.parseLong(p[2])==++seq,"packet order/session");}
        // Real transport to a loopback fixture. No input is pressed and no audio is played.
        try(DatagramSocket receiver=new DatagramSocket(new InetSocketAddress("127.0.0.1",InputSoundSender.PORT))) {
            receiver.setSoTimeout(3000); InputSoundSender sender=new InputSoundSender(); sender.session(previousSession);
            sender.offer(events.get(0));byte[] data=new byte[2048];DatagramPacket packet=new DatagramPacket(data,data.length);receiver.receive(packet);
            check(new String(packet.getData(),0,packet.getLength(),StandardCharsets.US_ASCII).equals(events.get(0)),"loopback event matches immutable packet");
            byte[] ack=("BKS1 ACK "+previousSession).getBytes(StandardCharsets.US_ASCII);
            receiver.send(new DatagramPacket(ack,ack.length,packet.getAddress(),packet.getPort()));
            long until=System.nanoTime()+1000000000L;while(!sender.connected(System.nanoTime()) && System.nanoTime()<until)Thread.sleep(5);
            check(sender.connected(System.nanoTime()),"local companion acknowledgment");sender.close();
        }
        System.out.println("PASS input sounds: actual click pulses, held/released keys, physical snapshots, disabled/context silence, mapping and loopback transport");
    }
}
