package script.systems.events;

import script.*;
import script.library.chat;

public class easter_egg_hunt_manager extends script.base_script
{
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "spawnCollectibles", null, 2, false);
        return SCRIPT_CONTINUE;
    }

    public int spawnCollectibles(obj_id self, dictionary params) throws InterruptedException
    {
        for (int i = 0; i < easter_egg_hunt_data.PLACEMENTS.length; i++)
        {
            String[] p = easter_egg_hunt_data.PLACEMENTS[i];
            String map = p[0];
            String id = p[1];
            float x = Float.parseFloat(p[2]);
            float z = Float.parseFloat(p[3]);
            float y = Float.parseFloat(p[4]);
            location l = new location(x, y, z, map);
            obj_id egg = createObject(easter_egg_hunt_data.EGG_TEMPLATE, l);
            if (isIdValid(egg))
            {
                setObjVar(egg, "events.easterEggHunt.collectibleId", id);
                setObjVar(egg, "events.easterEggHunt.mapName", map);
                setObjVar(egg, "events.easterEggHunt.eventId", easter_egg_hunt_data.EVENT_ID);
            }
        }
        return SCRIPT_CONTINUE;
    }

    public static void updateProgressAndRewards(obj_id player, String mapName) throws InterruptedException
    {
        if (!isPlayer(player))
        {
            return;
        }
        updatePerMapCompletion(player, mapName);
        boolean fullComplete = true;
        for (int i = 0; i < easter_egg_hunt_data.TARGET_MAPS.length; i++)
        {
            if (!hasObjVar(player, easter_egg_hunt_data.OBJVAR_MAP_COMPLETE + "." + easter_egg_hunt_data.TARGET_MAPS[i]))
            {
                fullComplete = false;
                break;
            }
        }
        if (!fullComplete)
        {
            return;
        }
        setObjVar(player, easter_egg_hunt_data.OBJVAR_EVENT_COMPLETE, 1);
        if (hasObjVar(player, easter_egg_hunt_data.OBJVAR_REWARD_GRANTED))
        {
            return;
        }
        if (!hasSkill(player, easter_egg_hunt_data.SECRET_TITLE_SKILL))
        {
            grantSkill(player, easter_egg_hunt_data.SECRET_TITLE_SKILL);
        }
        if (!hasCommand(player, easter_egg_hunt_data.SECRET_EMOTE_COMMAND))
        {
            grantCommand(player, easter_egg_hunt_data.SECRET_EMOTE_COMMAND);
        }
        setObjVar(player, easter_egg_hunt_data.OBJVAR_REWARD_GRANTED, 1);
        chat.chat(player, "You completed the Easter Egg Hunt and unlocked a secret title + emote!");
    }

    public static void updatePerMapCompletion(obj_id player, String mapName) throws InterruptedException
    {
        if (mapName == null || mapName.length() < 1)
        {
            return;
        }
        int requiredCount = 0;
        int discoveredCount = 0;
        for (int i = 0; i < easter_egg_hunt_data.PLACEMENTS.length; i++)
        {
            String[] placement = easter_egg_hunt_data.PLACEMENTS[i];
            if (!mapName.equals(placement[0]))
            {
                continue;
            }
            requiredCount++;
            if (hasObjVar(player, easter_egg_hunt_data.OBJVAR_DISCOVERED + "." + placement[1]))
            {
                discoveredCount++;
            }
        }
        if (requiredCount > 0 && discoveredCount >= requiredCount)
        {
            setObjVar(player, easter_egg_hunt_data.OBJVAR_MAP_COMPLETE + "." + mapName, 1);
        }
        else
        {
            removeObjVar(player, easter_egg_hunt_data.OBJVAR_MAP_COMPLETE + "." + mapName);
        }
    }

    public static void archiveAndResetProgress(obj_id player, String newEventId) throws InterruptedException
    {
        if (!isPlayer(player))
        {
            return;
        }
        String currentEvent = easter_egg_hunt_data.EVENT_ID;
        if (hasObjVar(player, easter_egg_hunt_data.OBJVAR_CURRENT_EVENT))
        {
            currentEvent = getStringObjVar(player, easter_egg_hunt_data.OBJVAR_CURRENT_EVENT);
        }
        setObjVar(player, easter_egg_hunt_data.OBJVAR_ARCHIVED_EVENT, currentEvent);
        removeObjVar(player, easter_egg_hunt_data.OBJVAR_DISCOVERED);
        removeObjVar(player, easter_egg_hunt_data.OBJVAR_MAP_COMPLETE);
        removeObjVar(player, easter_egg_hunt_data.OBJVAR_EVENT_COMPLETE);
        removeObjVar(player, easter_egg_hunt_data.OBJVAR_REWARD_GRANTED);
        if (newEventId != null && newEventId.length() > 0)
        {
            setObjVar(player, easter_egg_hunt_data.OBJVAR_CURRENT_EVENT, newEventId);
        }
        else
        {
            setObjVar(player, easter_egg_hunt_data.OBJVAR_CURRENT_EVENT, easter_egg_hunt_data.EVENT_ID);
        }
    }
}
