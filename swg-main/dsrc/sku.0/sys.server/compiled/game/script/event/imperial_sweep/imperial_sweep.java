package script.event.imperial_sweep;

import script.base_script;
import script.dictionary;
import script.library.locations;
import script.library.npc_simulation;
import script.library.utils;
import script.location;
import script.obj_id;
import script.library.create;

public class imperial_sweep extends base_script
{
    private static final String MOB_TEMPLATE = "stormtrooper";
    private static final int TOTAL_WAVES = 4;
    private static final float WAVE_DELAY = 75.0f;

    public int OnAttach(obj_id self) throws InterruptedException
    {
        debugServerConsoleMsg(self, "[IMPERIAL SWEEP] Imperial forces mobilizing.");
        setObjVar(self, "waveNumber", 0);
        messageTo(self, "startWave", null, 1, false);
        return SCRIPT_CONTINUE;
    }

    public int startWave(obj_id self, dictionary params) throws InterruptedException
    {
        int wave = getIntObjVar(self, "waveNumber");
        if (wave >= TOTAL_WAVES)
        {
            debugServerConsoleMsg(self, "[IMPERIAL SWEEP] Final wave complete.");
            return SCRIPT_CONTINUE;
        }

        location origin = getLocation(self);
        debugServerConsoleMsg(self, "[IMPERIAL SWEEP] Deploying wave " + (wave + 1));

        for (int i = 0; i < 5; i++)
        {
            location ringLoc = utils.getRandomLocationInRing(origin, 1000, 2500);
            location spawnLoc = locations.getGoodLocationAroundLocation(ringLoc, 5.0f, 5.0f, 50.0f, 50.0f);
            if (spawnLoc != null)
            {
                obj_id npc = create.object(MOB_TEMPLATE, spawnLoc);
                if (isIdValid(npc))
                {
                    setObjVar(npc, npc_simulation.OBJVAR_ADAPTIVE_ELIGIBLE_SPAWNED, 1);
                    setObjVar(npc, "auto_invasion.target", self);
                    npc_simulation.attachAdaptiveArchetypeControllerIfAllowed(npc, MOB_TEMPLATE, self);
                }
            }
        }

        setObjVar(self, "waveNumber", wave + 1);
        messageTo(self, "startWave", null, WAVE_DELAY, false);
        return SCRIPT_CONTINUE;
    }
}

