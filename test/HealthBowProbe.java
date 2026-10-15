package streamproof;

/** Deterministic game-data fixtures and forward-simulated arrow interceptions. */
public final class HealthBowProbe {
    public static final class Player {
        public World field_70170_p=new World();
        public String func_70005_c_() { return "Player"; }
    }
    public static final class World {
        final Board board=new Board();
        public Board func_96441_U() { return board; }
    }
    public static final class Board {
        Object objective=new Object(); boolean present=true; int points=14,created;
        public Object func_96539_a(int slot) {
            if (slot!=0) throw new AssertionError("Not the Tab slot"); return objective;
        }
        public boolean func_178819_b(String name,Object objective) { return present; }
        public Score func_96529_a(String name,Object objective) { created++; return new Score(points); }
    }
    public static final class Score {
        final int points; Score(int points) { this.points=points; }
        public int func_96652_c() { return points; }
    }
    public static final class Mcp {
        public String getName() { return "MCP"; }
        public int score(int slot) { return slot+10; }
        public String score(String name) { return name; }
    }
    public static void main(String[] args) throws Exception {
        Player p=new Player(); Board b=p.field_70170_p.board;
        near(TabHealth.read(p,false),14,0); near(TabHealth.read(p,true),28,0);
        b.points=0; near(TabHealth.read(p,false),0,0); // zero is a real value
        b.points=-1; if (TabHealth.read(p,false)!=null) throw new AssertionError("Negative score");
        b.present=false; int calls=b.created;
        if (TabHealth.read(p,false)!=null || b.created!=calls) throw new AssertionError("Missing score was created");
        b.objective=null; if (TabHealth.read(p,false)!=null) throw new AssertionError("Missing objective");
        p.field_70170_p=null; if (TabHealth.read(p,false)!=null) throw new AssertionError("Unloaded world");
        Mcp m=new Mcp();
        if (!GameAccess.call(m,"func_70005_c_|getName").equals("MCP")) throw new AssertionError("MCP alias");
        near(((Number)GameAccess.call(m,"score",2)).doubleValue(),12,0);
        if (!GameAccess.call(m,"score","s").equals("s")) throw new AssertionError("Overload cache");
        near(BowPhysics.speed(0),0,0); near(BowPhysics.speed(20),3,0); near(BowPhysics.speed(200),3,0);
        near(BowPhysics.speed(10),1.25,1e-12);
        intercept(0,0,30,0,0,0,3);
        intercept(24,5,35,0.2,0,0,3);
        intercept(-18,-5,20,-0.1,-0.1,0.05,2);
        intercept(4,2,7,0,0,0,BowPhysics.speed(10));
        intercept(0,20,0,0,0,0,3);
        if (BowPhysics.solve(0,0,1000,0,0,0,3)!=null) throw new AssertionError("Unreachable target");
        if (BowPhysics.solve(0,0,30,0,0,0,0)!=null) throw new AssertionError("Uncharged bow");
        if (BowPhysics.solve(Double.NaN,0,30,0,0,0,3)!=null) throw new AssertionError("NaN solution");
        near(BowPhysics.wrap(-359),1,0); near(BowPhysics.wrap(359),-1,0);
        System.out.println("PASS Tab health: SRG/MCP access, units, zero, missing objective/score, no score creation");
        System.out.println("PASS bow physics: charge curve, stationary/moving/elevated/vertical intercepts, unreachable targets, angle wrap");
    }
    private static void intercept(double x,double y,double z,double vx,double vy,double vz,double speed) {
        double[] aim=BowPhysics.solve(x,y,z,vx,vy,vz,speed);
        if (aim==null) throw new AssertionError("No intercept");
        double yaw=Math.toRadians(aim[0]),pitch=Math.toRadians(aim[1]);
        double ax=-Math.sin(yaw)*Math.cos(pitch)*speed, ay=-Math.sin(pitch)*speed, az=Math.cos(yaw)*Math.cos(pitch)*speed;
        double px=0,py=0,pz=0; int ticks=(int)Math.floor(aim[2]);
        // Independent tick-by-tick simulation, rather than reusing the solver's equations.
        for (int i=0;i<ticks;i++) { px+=ax; py+=ay; pz+=az; ax*=0.99; ay=ay*0.99-0.05; az*=0.99; }
        double remainder=aim[2]-ticks; px+=ax*remainder; py+=ay*remainder; pz+=az*remainder;
        near(px,x+vx*aim[2],1e-7); near(py,y+vy*aim[2],1e-7); near(pz,z+vz*aim[2],1e-7);
    }
    private static void near(double a,double b,double tolerance) {
        if (!Double.isFinite(a) || Math.abs(a-b)>tolerance) throw new AssertionError(a+" != "+b);
    }
}
