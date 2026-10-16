package streamproof;

/** Camera goal always uses full power; actual-charge assessment is kept separately. */
public final class BowSolution {
    public static double[] aim(BowGeometry.Vec eyes,BowGeometry.Box box,double height,BowPhysics.TargetPath path,double yaw) {
        BowGeometry.Vec goal=box.point(height); double[] result=null;
        for(int i=0;i<2;i++) {
            BowGeometry.Vec delta=goal.sub(BowGeometry.launch(eyes,yaw));
            result=BowPhysics.solve(delta.x,delta.y,delta.z,3,path);
            if(result==null)return null;
            yaw=result[0];
        }
        return result;
    }
    public final BowFlight.Body body;
    public final double height,yaw,pitch,flightTicks,score,clearance;
    public final BowFlight.Assessment assessment;
    public BowSolution(BowFlight.Body body,double height,double[] aim,double score,BowFlight.Assessment assessment) {
        this.body=body; this.height=height; yaw=aim[0]; pitch=aim[1]; flightTicks=aim[2]; this.score=score;
        this.assessment=assessment; clearance=assessment==null ? 0 : assessment.clearance;
    }
}
