package script.event.pirate_ambush;

import script.base_script;
import script.dictionary;
import script.library.locations;
import script.library.utils;
import script.location;
import script.obj_id;
import script.library.create;

public class pirate_ambush extends base_script
{
    private static final String MOB_TEMPLATE = "pirate_cutthroat";
    private static final int TOTAL_WAVES = 3;
    private static final float WAVE_DELAY = 90.0f;

    public int OnAttach(obj_id self) throws InterruptedException
    {
        debugServerConsoleMsg(self, "[PIRATE AMBUSH] Pirate ambush started.");
        setObjVar(self, "waveNumber", 0);
        messageTo(self, "startWave", null, 1, false);
        return SCRIPT_CONTINUE;
    }

    public int startWave(obj_id self, dictionary params) throws InterruptedException
    {
        int wave = getIntObjVar(self, "waveNumber");
        if (wave >= TOTAL_WAVES)
        {
            debugServerConsoleMsg(self, "[PIRATE AMBUSH] All waves complete.");
            return SCRIPT_CONTINUE;
        }

        location origin = getLocation(self);
        debugServerConsoleMsg(self, "[PIRATE AMBUSH] Spawning wave " + (wave + 1));

        for (int i = 0; i < 6; i++)
        {
            location ringLoc = utils.getRandomLocationInRing(origin, 1000, 2500);
            location spawnLoc = locations.getGoodLocationAroundLocation(ringLoc, 5.0f, 5.0f, 50.0f, 50.0f);
            if (spawnLoc != null)
            {
                obj_id npc = create.object(MOB_TEMPLATE, spawnLoc);
                if (isIdValid(npc))
                {
                    setObjVar(npc, "auto_invasion.target", self);
                }
            }
        }

        setObjVar(self, "waveNumber", wave + 1);
        messageTo(self, "startWave", null, WAVE_DELAY, false);
        return SCRIPT_CONTINUE;
    }
}

