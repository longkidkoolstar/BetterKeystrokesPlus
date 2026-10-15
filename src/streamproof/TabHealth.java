package streamproof;

/** Reads the very same objective/score used by vanilla's Tab renderer. */
public final class TabHealth {
    private TabHealth() {}
    public static Float read(Object player, boolean hearts) throws ReflectiveOperationException {
        Object world=GameAccess.get(player,"field_70170_p|worldObj");
        if (world==null) return null;
        Object board=GameAccess.call(world,"func_96441_U|getScoreboard");
        Object objective=GameAccess.call(board,"func_96539_a|getObjectiveInDisplaySlot",0);
        if (objective==null) return null;
        String name=(String)GameAccess.call(player,"func_70005_c_|getName");
        // Check first: getValueFromObjective creates a zero score for missing names.
        if (!Boolean.TRUE.equals(GameAccess.call(board,"func_178819_b|entityHasObjective",name,objective))) return null;
        Object score=GameAccess.call(board,"func_96529_a|getValueFromObjective",name,objective);
        float value=((Number)GameAccess.call(score,"func_96652_c|getScorePoints")).floatValue();
        if (value<0) return null;
        return value*(hearts ? 2f : 1f);
    }
}
