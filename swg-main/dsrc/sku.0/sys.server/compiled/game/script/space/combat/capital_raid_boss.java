package script.space.combat;

import script.obj_id;

import script.library.space_capital_raids;

public class capital_raid_boss extends script.base_script
{
    public capital_raid_boss()
    {
    }

    public int OnDestroy(obj_id self) throws InterruptedException
    {
        String instanceId = getStringObjVar(self, "spaceRaid.active");
        if (instanceId == null || instanceId.length() == 0)
        {
            instanceId = getStringObjVar(self, "spaceRaid.instanceId");
        }
        if (instanceId != null && instanceId.length() > 0)
        {
            space_capital_raids.bossDestroyed(instanceId, self);
        }
        return SCRIPT_CONTINUE;
    }
}
