package script.systems.behavior;

import script.*;
import script.library.behavior_telemetry;

public class profile_builder extends script.base_script
{
    public profile_builder()
    {
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        messageTo(self, "runArchetypeAggregationPass", null, 30, false);
        return SCRIPT_CONTINUE;
    }

    public int runArchetypeAggregationPass(obj_id self, dictionary params) throws InterruptedException
    {
        // Offline/periodic pass entry point. This script intentionally does not store raw player data,
        // only aggregate archetype counters suitable for NPC simulation selection.
        int grinder = getIntObjVar(self, behavior_telemetry.AGGREGATE_ROOT + ".grinder");
        int crafter = getIntObjVar(self, behavior_telemetry.AGGREGATE_ROOT + ".crafter_runner");
        int social = getIntObjVar(self, behavior_telemetry.AGGREGATE_ROOT + ".social_hub_idler");
        int total = Math.max(1, grinder + crafter + social);
        setObjVar(self, behavior_telemetry.AGGREGATE_ROOT + ".distribution", new int[]{(grinder * 100) / total, (crafter * 100) / total, (social * 100) / total});
        setObjVar(self, behavior_telemetry.AGGREGATE_ROOT + ".lastBuild", getGameTime());
        messageTo(self, "runArchetypeAggregationPass", null, 900, false);
        return SCRIPT_CONTINUE;
    }
}
