package streamproof;
import java.nio.*;
import org.lwjgl.BufferUtils;

public class CopyBenchmark {
    static volatile int checksum;
    static void oldCopy(ByteBuffer source,int[] target,int w,int h) {
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int p=(y*w+x)*4;
            int r=source.get(p)&255,g=source.get(p+1)&255,b=source.get(p+2)&255,a=source.get(p+3)&255;
            target[(h-1-y)*w+x]=(a<<24)|(r<<16)|(g<<8)|b;
        }
    }
    public static void main(String[] args) {
        int w=1920,h=1080,runs=40;
        ByteBuffer bytes=BufferUtils.createByteBuffer(w*h*4);IntBuffer packed=bytes.asIntBuffer();
        for(int i=0;i<w*h;i++)packed.put(i,i|0xFF000000);
        int[] target=new int[w*h];
        for(int i=0;i<10;i++){oldCopy(bytes,target,w,h);Overlay.copyRows(packed,target,w,h);}
        long before=System.nanoTime();
        for(int i=0;i<runs;i++){oldCopy(bytes,target,w,h);checksum=target[i];}
        long middle=System.nanoTime();
        for(int i=0;i<runs;i++){Overlay.copyRows(packed,target,w,h);checksum=target[i];}
        long after=System.nanoTime();
        System.out.printf("1080p CPU copy benchmark (%d frames): v3 %.3f ms/frame; v4 %.3f ms/frame. Excludes GPU readback, game rendering, and window compositing.%n",runs,(middle-before)/1e6/runs,(after-middle)/1e6/runs);
    }
}
