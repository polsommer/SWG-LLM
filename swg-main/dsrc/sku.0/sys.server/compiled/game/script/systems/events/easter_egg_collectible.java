package script.systems.events;

import script.*;
import script.library.chat;
import script.library.utils;

public class easter_egg_collectible extends script.base_script
{
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setName(self, "Easter Egg");
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (isPlayer(player))
        {
            mi.addRootMenu(menu_info_types.ITEM_USE, new string_id("collection", "collect"));
        }
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item != menu_info_types.ITEM_USE || !isPlayer(player))
        {
            return SCRIPT_CONTINUE;
        }
        String collectibleId = getStringObjVar(self, "events.easterEggHunt.collectibleId");
        String mapName = getStringObjVar(self, "events.easterEggHunt.mapName");
        String eventId = getStringObjVar(self, "events.easterEggHunt.eventId");
        if (collectibleId == null || collectibleId.length() < 1)
        {
            return SCRIPT_CONTINUE;
        }
        if (eventId == null || eventId.length() < 1)
        {
            return SCRIPT_CONTINUE;
        }
        if (hasObjVar(player, easter_egg_hunt_data.OBJVAR_CURRENT_EVENT))
        {
            String activeEvent = getStringObjVar(player, easter_egg_hunt_data.OBJVAR_CURRENT_EVENT);
            if (activeEvent != null && activeEvent.length() > 0 && !eventId.equals(activeEvent))
            {
                sendSystemMessage(player, new string_id("collection", "collection_unavailable"));
                return SCRIPT_CONTINUE;
            }
        }
        else
        {
            setObjVar(player, easter_egg_hunt_data.OBJVAR_CURRENT_EVENT, eventId);
        }
        String discoveredObjVar = easter_egg_hunt_data.OBJVAR_DISCOVERED + "." + collectibleId;
        if (hasObjVar(player, discoveredObjVar))
        {
            sendSystemMessage(player, new string_id("collection", "already_collected"));
            return SCRIPT_CONTINUE;
        }
        setObjVar(player, discoveredObjVar, 1);
        sendSystemMessage(player, new string_id("collection", "piece_found"));
        chat.chat(player, "You discovered Easter Egg " + collectibleId + " on " + mapName + "!");
        easter_egg_hunt_manager.updateProgressAndRewards(player, mapName);
        destroyObject(self);
        return SCRIPT_CONTINUE;
    }
}
