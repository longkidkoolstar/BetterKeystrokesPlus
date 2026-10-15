package streamproof;

/** Rod activation timeline plus independent tick-by-tick hook simulations. */
public final class RodAimProbe {
    public static final class Player {
        public Object field_71104_cf; boolean alive=true;
        public boolean func_70089_S() { return alive; }
    }
    public static final class McpPlayer {
        public Object fishEntity;
        public boolean isEntityAlive() { return true; }
    }
    public static void main(String[] args) throws Exception {
        RodUse use=new RodUse(); Player player=new Player(); long now=10000000000L;
        check(!use.ready(player,false,false,now),"Sword should not aim");
        check(use.ready(player,true,false,now),"Switching to an uncast rod should aim");
        use.cast(now);
        check(!use.ready(player,true,false,now+1),"Click must stop aiming before server hook appears");
        check(!use.ready(player,false,false,now+2),"Switching to sword should stay inactive");
        check(!use.ready(player,true,false,now+3),"Quick switch back must not erase pending cast");
        player.field_71104_cf=new Object();
        check(!use.ready(player,true,false,now+100000000),"Hook out must stop aiming");
        check(!use.ready(player,true,false,now+3000000000L),"Active hook must stay inactive after timeout");
        player.field_71104_cf=null;
        check(use.ready(player,true,false,now+3100000000L),"Reeled hook should allow the next cast");
        check(!use.ready(player,true,true,now),"GUI should block aiming");
        player.alive=false; check(!use.ready(player,true,false,now),"Dead player should not aim"); player.alive=true;
        use.cast(now); use.cast(now+RodUse.PENDING_NANOS-1);
        check(use.ready(player,true,false,now+RodUse.PENDING_NANOS+1),"Duplicate input must not extend a rejected cast forever");
        use.cast(now);
        Player respawn=new Player(); check(use.ready(respawn,true,false,now),"New player should reset old cast");
        use.cast(now); use.reset(); check(use.ready(respawn,true,false,now),"Disable/world reset should clear old cast");
        check(!use.ready(null,true,false,now),"Disconnected player");
        McpPlayer mcp=new McpPlayer(); check(use.ready(mcp,true,false,now),"MCP method/field names");
        mcp.fishEntity=new Object(); check(!use.ready(mcp,true,false,now),"MCP hook detection");

        for (double distance:new double[]{3,6,10,12}) intercept(0,-0.53,distance,BowPhysics.STILL);
        intercept(3,1.5,7,BowPhysics.STILL); intercept(-3,-3,8,BowPhysics.STILL);
        BowPhysics.TargetPath moving=new BowPhysics.TargetPath() {
            public void offset(double t,double[] out) { out[0]=0.2*t; out[1]=0; out[2]=-0.05*t; }
        };
        intercept(0,-0.53,9,moving);
        MotionHistory history=new MotionHistory(); double x=0; history.sample(0,0,0,0,true);
        for (int i=1;i<=96;i++) { x+=(i-1)%16<8 ? 0.22 : -0.22; history.sample(i,x,0,0,true); }
        MotionHistory.Forecast strafe=history.forecast(true,20,1);
        check(strafe.period==16,"Learned movement reused by rod aiming");
        intercept(0,-0.53,9,strafe);
        check(RodPhysics.solve(0,0,30,BowPhysics.STILL)==null,"Beyond hook range should have no solution");
        check(RodPhysics.solve(Double.NaN,0,9,BowPhysics.STILL)==null,"Invalid coordinates");
        double[] rod=RodPhysics.solve(0,-0.53,10,BowPhysics.STILL);
        double[] bow=BowPhysics.solve(0,-0.53,10,0,0,0,3);
        check(Math.abs(rod[1]-bow[1])>1 && rod[2]>bow[2],"Rod must use its own trajectory");
        System.out.println("PASS rod activation: sword -> rod -> cast -> hook out -> reel; immediate pause, switch-back, GUI/death, timeout, respawn/reset, SRG/MCP fields");
        System.out.println("PASS rod trajectory: independent hook simulations at 3/6/10/12 blocks, height differences, movement/strafe interception, unreachable targets and separate bow physics");
    }
    private static void intercept(double x,double y,double z,BowPhysics.TargetPath target) {
        double[] aim=RodPhysics.solve(x,y,z,target); check(aim!=null,"Missing rod intercept");
        double yaw=Math.toRadians(aim[0]),pitch=Math.toRadians(aim[1]);
        double vx=-Math.sin(yaw)*Math.cos(pitch)*1.5,vy=-Math.sin(pitch)*1.5,vz=Math.cos(yaw)*Math.cos(pitch)*1.5;
        double px=0,py=0,pz=0; int n=(int)aim[2];
        // Intentionally simulate the real update order; do not reuse the solver's formula.
        for (int i=0;i<n;i++) {
            px+=vx; py+=vy; pz+=vz;
            vy-=0.03999999910593033;
            vx*=(double)0.92f; vy*=(double)0.92f; vz*=(double)0.92f;
        }
        double f=aim[2]-n; px+=vx*f; py+=vy*f; pz+=vz*f;
        double[] offset=new double[3]; target.offset(aim[2],offset);
        near(px,x+offset[0]); near(py,y+offset[1]); near(pz,z+offset[2]);
    }
    private static void near(double a,double b) { check(Double.isFinite(a) && Math.abs(a-b)<1e-6,a+" != "+b); }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
