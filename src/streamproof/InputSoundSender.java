package streamproof;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Bounded asynchronous loopback transport: no sockets/files/audio work on a game frame. */
public final class InputSoundSender implements InputSoundState.Sink,AutoCloseable {
    public static final int PORT=39189;
    private final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(256);
    private volatile boolean closing;
    private volatile long lastAck;
    private volatile String error="";
    private volatile String session="";
    private volatile int dropped;
    public InputSoundSender() {
        Thread worker=new Thread(new Runnable(){public void run(){work();}},"BetterKeystrokes-input-sounds");
        worker.setDaemon(true); worker.start();
    }
    public void session(String value) { session=value; }
    public void offer(String packet) { if(!closing && !queue.offer(packet)) dropped++; }
    public boolean connected(long now) { return lastAck!=0 && now-lastAck<1000000000L; }
    public String error() { return error; }
    public int dropped() { return dropped; }
    public void close() { closing=true; }
    private void work() {
        try(DatagramSocket socket=new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0))) {
            socket.setSoTimeout(1); InetAddress loopback=InetAddress.getByName("127.0.0.1");
            byte[] receive=new byte[128]; DatagramPacket response=new DatagramPacket(receive,receive.length);
            while(!closing || !queue.isEmpty()) {
                String packet=queue.poll(10,TimeUnit.MILLISECONDS);
                if(packet!=null) {
                    byte[] bytes=packet.getBytes(StandardCharsets.US_ASCII);
                    socket.send(new DatagramPacket(bytes,bytes.length,loopback,PORT));
                }
                try {
                    response.setLength(receive.length); socket.receive(response);
                    String ack=new String(response.getData(),0,response.getLength(),StandardCharsets.US_ASCII);
                    if(response.getAddress().isLoopbackAddress() && response.getPort()==PORT && ack.equals("BKS1 ACK "+session)) lastAck=System.nanoTime();
                } catch(SocketTimeoutException ignored) {}
            }
        } catch(Exception e) { error=e.getClass().getSimpleName(); }
    }
}
