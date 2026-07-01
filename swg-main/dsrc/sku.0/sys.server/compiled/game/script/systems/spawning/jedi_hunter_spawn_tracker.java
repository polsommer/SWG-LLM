package script.systems.spawning;

import script.dictionary;
import script.library.jedi_hunter;
import script.library.utils;
import script.obj_id;

public class jedi_hunter_spawn_tracker extends script.base_script
{
    public jedi_hunter_spawn_tracker()
    {
    }

    public int OnIncapacitated(obj_id self, obj_id killer) throws InterruptedException
    {
        notifyMaster(self);
        return SCRIPT_CONTINUE;
    }

    public int OnDestroy(obj_id self) throws InterruptedException
    {
        notifyMaster(self);
        return SCRIPT_CONTINUE;
    }

    private void notifyMaster(obj_id self) throws InterruptedException
    {
        if (utils.hasScriptVar(self, "jediHunter.cleanupDone"))
        {
            return;
        }
        utils.setScriptVar(self, "jediHunter.cleanupDone", 1);

        obj_id master = getObjIdObjVar(self, "jediHunter.master");
        if (!isIdValid(master))
        {
            return;
        }

        dictionary payload = new dictionary();
        payload.put("planet", getStringObjVar(self, "jediHunter.planet"));
        payload.put("squadId", getStringObjVar(self, "jediHunter.squadId"));
        int respawnDelay = hasObjVar(self, jedi_hunter.OBJVAR_RESPAWN_DELAY) ? getIntObjVar(self, jedi_hunter.OBJVAR_RESPAWN_DELAY) : jedi_hunter.DEFAULT_RESPAWN_DELAY_SECONDS;
        if (respawnDelay < 0)
        {
            respawnDelay = 0;
        }
        messageTo(master, "jediHunterMobDestroyed", payload, respawnDelay, false);
    }
}
