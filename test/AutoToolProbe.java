package streamproof;

/** Action timelines for first-block delays, switch ownership and deliberate combat mining. */
public final class AutoToolProbe {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static int step(AutoToolState s,long ms,int current,boolean attack,Object block,int best,long delay,boolean guard) {
        return s.step(ms*1000000L,current,attack,true,block,best,delay,guard,180,true);
    }
    private static void eq(int got,int wanted,String message) { check(got==wanted,message+": "+got+" != "+wanted); }
    public static void main(String[] args) {
        AutoToolState s=new AutoToolState();
        eq(step(s,0,0,true,"wool@1",2,50,true),-1,"first block starts delay");
        eq(step(s,49,0,true,"wool@1",2,50,true),-1,"delay not yet elapsed");
        eq(step(s,50,0,true,"wool@1",2,50,true),2,"first block switches without needing another block type");
        eq(s.originalSlot(),0,"saves original slot");
        eq(step(s,70,2,true,"wool@2",2,50,true),-1,"same material next position keeps tool");
        eq(step(s,80,2,true,"stone@3",4,50,true),4,"can change tools inside one mining session");
        eq(s.originalSlot(),0,"a second tool never overwrites original slot");
        eq(step(s,90,4,true,null,4,50,true),0,"looking away while holding attack restores weapon");
        check(!s.ownsSlot(),"restoration clears ownership");
        eq(step(s,100,0,false,null,0,50,true),-1,"later release cannot restore twice");

        s.reset();
        eq(step(s,0,0,true,"stone@1",3,0,false),3,"zero delay switches immediately");
        eq(step(s,10,3,false,"stone@1",3,0,false),0,"attack release restores");
        eq(step(s,20,0,true,"stone@1",3,0,false),3,"same block can be mined again after release");
        eq(s.step(30_000_000L,3,true,false,"stone@1",3,0,false,0,true),0,"GUI/focus/use/clicker pause restores");
        eq(s.stop(0,true),-1,"disable cleanup is idempotent");

        s.reset();
        eq(step(s,0,1,true,"wool@1",2,0,true),2,"normal mining has no extra combat wait");
        s.attacked(10_000_000L);
        eq(s.stop(2,true),1,"attack action restores before vanilla packet sync");
        eq(step(s,20,1,true,"wool@1",2,0,true),-1,"stray block after attacking player does not equip tool");
        eq(step(s,100,1,true,null,2,0,true),-1,"brief block contact ends harmlessly");
        eq(step(s,110,1,true,"wool@1",2,0,true),-1,"new intentional hold starts afresh");
        eq(step(s,289,1,true,"wool@1",2,0,true),-1,"combat hold not yet complete");
        eq(step(s,290,1,true,"wool@1",2,0,true),2,"intentional mining allowed during combat");
        eq(step(s,300,2,true,"wool@2",2,0,true),-1,"continuous bed defense mining stays active");
        // Incoming damage is deliberately not an input or a reason to stop mining.
        eq(step(s,310,2,true,"wood@3",4,0,true),4,"can change blocks/tools while attacked");
        eq(step(s,320,4,true,null,4,0,true),1,"returning to combat restores the saved weapon");

        s.reset(); s.attacked(0);
        eq(step(s,0,0,true,"wool@1",2,0,true),-1,"guard arms on first position");
        eq(step(s,170,0,true,"wool@2",2,0,true),-1,"different same-material block restarts intent timer");
        eq(step(s,180,0,true,"wool@2",2,0,true),-1,"block type alone is not continuity");
        eq(step(s,350,0,true,"wool@2",2,0,true),2,"new position eventually qualifies");
        s.reset(); s.attacked(0);
        eq(step(s,1001,0,true,"wool@1",2,0,true),2,"expired combat window does not delay ordinary mining");
        s.reset(); s.attacked(0);
        eq(step(s,1,0,true,"wool@1",2,0,false),2,"combat guard is configurable OFF");
        s.reset(); s.attacked(0);
        eq(step(s,950,0,true,"wool@1",2,0,true),-1,"intent wait captured while combat is recent");
        eq(step(s,1001,0,true,"wool@1",2,0,true),-1,"combat expiry cannot shorten an active hold");

        s.reset();
        eq(step(s,0,0,true,"stone",2,0,false),2,"owns auto tool");
        eq(step(s,10,5,true,"stone",2,0,false),-1,"manual slot change is respected");
        eq(step(s,20,5,true,"other block",3,0,false),-1,"manual override survives block changes");
        eq(step(s,30,5,false,null,3,0,false),-1,"release never overrides manual slot");
        eq(step(s,40,5,true,"stone",2,0,false),2,"new attack can select a tool again");
        s.manualSelection();
        eq(step(s,50,2,true,"stone",4,0,false),-1,"pressing the already-selected hotbar key also overrides");
        eq(step(s,60,2,false,null,4,0,false),-1,"explicit same-slot choice is not switched back");
        eq(step(s,70,2,true,"stone",4,0,false),4,"next session is independent");
        eq(s.stop(4,false),-1,"Hotkey back OFF keeps tool");
        eq(s.stop(4,true),-1,"later enabling switch-back cannot restore stale slot");
        s.reset();
        eq(step(s,0,0,true,"stone",2,0,false),2,"context test owns slot");
        s.reset();
        eq(s.stop(2,true),-1,"world/player/inventory reset never restores into another context");

        s.reset();
        eq(step(s,0,0,true,"stone",0,0,false),-1,"already-best held item is not claimed");
        check(!s.ownsSlot(),"no switch means no saved-slot ownership");
        eq(step(s,1,0,true,"stone",3,0,false),3,"inventory changes can supply a better tool");
        eq(s.stop(8,true),-1,"external slot change prevents stale restoration");
        s.reset();
        eq(step(s,0,0,true,"stone",2,2000,false),-1,"long configured delay honored");
        eq(step(s,1999,0,true,"stone",2,2000,false),-1,"long delay not skipped");
        eq(step(s,2000,0,true,"stone",2,2000,false),2,"long delay completes");

        double[] rates=new double[9];
        rates[0]=AutoToolState.rate(1,0,true); // sword/hand baseline on this block
        rates[2]=AutoToolState.rate(5,0,true); // shears on wool
        eq(AutoToolState.best(rates,0),2,"wool chooses shears");
        rates[4]=rates[2];
        eq(AutoToolState.best(rates,4),4,"ties keep held tool");
        rates=new double[9];
        rates[0]=AutoToolState.rate(1,0,false);
        rates[2]=AutoToolState.rate(6,0,false); // fast wrong tool
        rates[4]=AutoToolState.rate(2,0,true); // harvest-capable pick
        eq(AutoToolState.best(rates,0),4,"harvest penalty participates in ranking");
        check(AutoToolState.rate(2,4,true)>AutoToolState.rate(8,0,true),"efficiency can make a slower base tool faster");
        check(AutoToolState.rate(1,5,true)==AutoToolState.rate(1,0,true),"efficiency does not boost base speed one");
        check(AutoToolState.rate(Double.NaN,0,true)==0,"invalid tool speed cannot win");
        System.out.println("PASS Auto Tool: "+checks+" checks; first/same-block delays, ownership/restoration, combat intent, manual overrides and tool ranking");
    }
}
