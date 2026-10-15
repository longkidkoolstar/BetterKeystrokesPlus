package streamproof;
import java.awt.EventQueue;
import java.awt.Frame;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class WindowModeProbe {
    public static void main(String[] args)throws Exception {
        final Throwable[] failure=new Throwable[1];
        EventQueue.invokeAndWait(new Runnable(){public void run(){
            Frame sample=new Frame("Hidden borderless test");
            try {
                sample.setBounds(-20000,-20000,320,240);sample.addNotify();
                Pointer hwnd=Native.getWindowPointer(sample);
                Memory before=new Memory(16),after=new Memory(16),client=new Memory(16);
                Overlay.fn("GetWindowRect").invokeInt(new Object[]{hwnd,before});
                int style=Overlay.fn("GetWindowLongW").invokeInt(new Object[]{hwnd,-16});
                WindowMode.enter(hwnd);
                int borderlessStyle=Overlay.fn("GetWindowLongW").invokeInt(new Object[]{hwnd,-16});
                if((borderlessStyle&0x00C40000)!=0)throw new AssertionError("Caption or thick frame remained");
                Overlay.fn("GetClientRect").invokeInt(new Object[]{hwnd,client});
                if(client.getInt(8)<320 || client.getInt(12)<240)throw new AssertionError("Window did not fill a monitor");
                WindowMode.restore();Overlay.fn("GetWindowRect").invokeInt(new Object[]{hwnd,after});
                for(int p=0;p<16;p+=4)if(before.getInt(p)!=after.getInt(p))throw new AssertionError("Window bounds not restored");
                if(style!=Overlay.fn("GetWindowLongW").invokeInt(new Object[]{hwnd,-16}))throw new AssertionError("Window style not restored");
                System.out.println("PASS borderless native window: monitor-sized client, decorations removed, original style/bounds restored; test window remained hidden");
            }catch(Throwable t){failure[0]=t;}
            finally{if(WindowMode.isEnabled())WindowMode.restore();sample.dispose();}
        }});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
}
