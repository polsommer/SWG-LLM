package script.space.raid;

import script.dictionary;
import script.obj_id;

import script.library.space_capital_raids;
import script.library.utils;

public class capital_raid_session extends script.base_script
{
    private static final String VAR_INSTANCE = "spaceRaid.sessionInstance";

    public capital_raid_session()
    {
    }

    public int startRaidSession(obj_id self, dictionary params) throws InterruptedException
    {
        String instanceId = params.getString("instanceId");
        float timeout = params.getFloat("timeout");
        if (instanceId == null || instanceId.length() == 0)
        {
            return SCRIPT_CONTINUE;
        }
        utils.setScriptVar(self, VAR_INSTANCE, instanceId);
        dictionary timer = new dictionary();
        timer.put("instanceId", instanceId);
        messageTo(self, "handleRaidTimeout", timer, timeout > 0.0f ? timeout : 1800.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int cancelRaidTimer(obj_id self, dictionary params) throws InterruptedException
    {
        String instanceId = params.getString("instanceId");
        String active = utils.getStringScriptVar(self, VAR_INSTANCE);
        if (active != null && active.equals(instanceId))
        {
            utils.removeScriptVar(self, VAR_INSTANCE);
        }
        return SCRIPT_CONTINUE;
    }

    public int handleRaidTimeout(obj_id self, dictionary params) throws InterruptedException
    {
        String instanceId = params.getString("instanceId");
        String active = utils.getStringScriptVar(self, VAR_INSTANCE);
        if (instanceId == null || instanceId.length() == 0)
        {
            return SCRIPT_CONTINUE;
        }
        if (active == null || !active.equals(instanceId))
        {
            return SCRIPT_CONTINUE;
        }
        utils.removeScriptVar(self, VAR_INSTANCE);
        space_capital_raids.timeout(instanceId);
        return SCRIPT_CONTINUE;
    }

    public int cleanupRaidSession(obj_id self, dictionary params) throws InterruptedException
    {
        String instanceId = params.getString("instanceId");
        String active = utils.getStringScriptVar(self, VAR_INSTANCE);
        if (active != null && (instanceId == null || instanceId.length() == 0 || active.equals(instanceId)))
        {
            utils.removeScriptVar(self, VAR_INSTANCE);
        }
        detachScript(self, "space.raid.capital_raid_session");
        return SCRIPT_CONTINUE;
    }
}
