package streamproof;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded asynchronous log; never reads game objects on the writer thread. */
public final class BowLog implements AutoCloseable {
    private static final String STOP=new String("stop");
    private static final Object FILE_LOCK=new Object();
    private final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(128);
    private final Thread worker;
    private volatile boolean accepting=true;
    public volatile String error="";
    public final AtomicInteger dropped=new AtomicInteger();
    public BowLog(final Path directory) { this(directory,5000000); }
    public BowLog(final Path directory,final long fileLimit) {
        worker=new Thread(new Runnable() { public void run() { synchronized(FILE_LOCK) {
            try {
                Files.createDirectories(directory);
                Path current=directory.resolve("bow-current.jsonl"),previous=directory.resolve("bow-previous.jsonl");
                if (Files.exists(previous) && Files.size(previous)>fileLimit)Files.delete(previous);
                if (Files.exists(current) && Files.size(current)>fileLimit)Files.delete(current);
                OutputStream out=Files.newOutputStream(current,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                long size=Files.size(current);
                try {
                    for (;;) {
                        String row=queue.take(); if (row==STOP)break;
                        byte[] bytes=(row+"\n").getBytes(StandardCharsets.UTF_8);
                        if (bytes.length>fileLimit) { dropped.incrementAndGet(); continue; }
                        if (size+bytes.length>fileLimit) {
                            out.close(); Files.move(current,previous,StandardCopyOption.REPLACE_EXISTING);
                            out=Files.newOutputStream(current,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING); size=0;
                        }
                        out.write(bytes); out.flush(); size+=bytes.length;
                    }
                } finally { out.close(); }
            } catch (Exception e) { error=e.getClass().getSimpleName()+": "+e.getMessage(); }
            finally { accepting=false; }
        }}},"BetterKeystrokes-bow-log");
        worker.setDaemon(true); worker.start();
    }
    public void offer(String immutableJson) { if (accepting && !queue.offer(immutableJson))dropped.incrementAndGet(); }
    public void close() { accepting=false; while(!queue.offer(STOP)) { queue.poll(); dropped.incrementAndGet(); } }
    public boolean await(long milliseconds) throws InterruptedException { worker.join(milliseconds); return !worker.isAlive(); }
    public static String number(double n) { return Double.isFinite(n) ? Double.toString(n) : "null"; }
}
