package script.live_event;

import script.*;

public class map_dressing_zone_entry extends script.live_event.map_dressing_manager
{
    public int OnLogin(obj_id self) throws InterruptedException
    {
        return applyMapDressing(self, getCurrentSceneName());
    }

    public int OnSceneChanged(obj_id self, String oldScene, String newScene) throws InterruptedException
    {
        clearMapDressing(self, oldScene);
        return applyMapDressing(self, newScene);
    }

    public int OnCommand(obj_id self, String command, String params, obj_id target) throws InterruptedException
    {
        if (!"liveEventComponent".equalsIgnoreCase(command))
        {
            return SCRIPT_CONTINUE;
        }

        String[] tokens = split(params, ' ');
        if (tokens == null || tokens.length < 2)
        {
            sendSystemMessage(self, "Usage: /liveEventComponent <sky|flyover|announcement> <on|off>", null);
            return SCRIPT_CONTINUE;
        }

        boolean enabled = "on".equalsIgnoreCase(tokens[1]);
        return setComponentEnabled(self, tokens[0], enabled);
    }
}
