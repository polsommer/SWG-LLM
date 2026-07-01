package script.theme_park.dathomir.wod2;

import script.*;
import script.library.groundquests;
import script.library.utils;

public class artifact extends script.base_script
{
    public artifact()
    {
    }
    public static final String ARTIFACT_QUEST_NAME = "rodstart";
    public static final String PROXIMITY_VOLUME = "artifact_proximity";
    public static final String OBJVAR_PROXIMITY_RANGE = "artifactProximityRange";
    public static final String OBJVAR_PROXIMITY_GROUP = "artifactProximityGroup";
    public static final String OBJVAR_PROXIMITY_RANGE_PREFIX = "artifactProximityRange.";
    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (isMob(self))
        {
            setCondition(self, CONDITION_CONVERSABLE);
        }
        updateProximityVolume(self);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (isMob(self))
        {
            setCondition(self, CONDITION_CONVERSABLE);
        }
        updateProximityVolume(self);
        return SCRIPT_CONTINUE;
    }
    public int OnTriggerVolumeEntered(obj_id self, String volumeName, obj_id player) throws InterruptedException
    {
        if (!PROXIMITY_VOLUME.equals(volumeName))
        {
            return SCRIPT_CONTINUE;
        }
        if (!isPlayer(player) || isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        if (!hasScript(player, "quest.task.ground.retrieve_item"))
        {
            return SCRIPT_CONTINUE;
        }
        if (!hasObjVar(self, "artifactTask"))
        {
            return SCRIPT_CONTINUE;
        }
        String artifactTask = getStringObjVar(self, "artifactTask");
        if (artifactTask == null || artifactTask.length() == 0)
        {
            return SCRIPT_CONTINUE;
        }
        if (groundquests.isTaskActive(player, ARTIFACT_QUEST_NAME, artifactTask))
        {
            if (!groundquests.playerNeedsToRetrieveThisItem(player, self))
            {
                sendSystemMessage(player, new string_id("nexus", "artifact_already_used"));
                return SCRIPT_CONTINUE;
            }
            sendRetrieveObjectFoundMessage(self, player);
            return SCRIPT_CONTINUE;
        }
        if (groundquests.hasCompletedTask(player, ARTIFACT_QUEST_NAME, artifactTask))
        {
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info menuInfo) throws InterruptedException
    {
        if (isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        int menu = 0;
        if (isMob(self))
        {
            menu = menuInfo.addRootMenu(menu_info_types.CONVERSE_START, null);
        }
        else 
        {
            menu = menuInfo.addRootMenu(menu_info_types.ITEM_USE, new string_id("ui_radial", "item_use"));
        }
        if (hasScript(player, "quest.task.ground.retrieve_item"))
        {
            if (!hasObjVar(self, "artifactTask"))
            {
                return SCRIPT_CONTINUE;
            }
            String artifactTask = getStringObjVar(self, "artifactTask");
            if (groundquests.isTaskActive(player, ARTIFACT_QUEST_NAME, artifactTask))
            {
                if (groundquests.playerNeedsToRetrieveThisItem(player, self))
                {
                    String menuText = groundquests.getRetrieveMenuText(player, self);
                    string_id menuStringId = utils.unpackString(menuText);
                    if (isMob(self))
                    {
                        menu = menuInfo.addRootMenu(menu_info_types.CONVERSE_START, menuStringId);
                    }
                    else 
                    {
                        menu = menuInfo.addRootMenu(menu_info_types.ITEM_USE, menuStringId);
                    }
                }
            }
        }
        menu_info_data menuInfoData = menuInfo.getMenuItemById(menu);
        if (menuInfoData != null)
        {
            menuInfoData.setServerNotify(true);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        if (item == menu_info_types.ITEM_USE || item == menu_info_types.CONVERSE_START)
        {
            if (hasScript(player, "quest.task.ground.retrieve_item"))
            {
                String artifactTask = getStringObjVar(self, "artifactTask");
                CustomerServiceLog("rodstart_artifact", "Pickup attempt: player=" + player + " task=" + artifactTask + " object=" + self);
                if (groundquests.isTaskActive(player, ARTIFACT_QUEST_NAME, artifactTask))
                {
                    if (!groundquests.playerNeedsToRetrieveThisItem(player, self))
                    {
                        CustomerServiceLog("rodstart_artifact", "Pickup failure (already used): player=" + player + " task=" + artifactTask + " object=" + self);
                        sendSystemMessage(player, new string_id("nexus", "artifact_already_used"));
                        return SCRIPT_CONTINUE;
                    }
                    sendRetrieveObjectFoundMessage(self, player);
                    return SCRIPT_CONTINUE;
                }
                else 
                {
                    if (groundquests.hasCompletedTask(player, ARTIFACT_QUEST_NAME, artifactTask))
                    {
                        CustomerServiceLog("rodstart_artifact", "Pickup failure (task completed): player=" + player + " task=" + artifactTask + " object=" + self);
                        sendSystemMessage(player, new string_id("nexus", "artifact_already_used"));
                        return SCRIPT_CONTINUE;
                    }
                }
            }
        }      
        CustomerServiceLog("rodstart_artifact", "Pickup failure (not needed): player=" + player + " task=" + getStringObjVar(self, "artifactTask") + " object=" + self);
        string_id msg = new string_id("nexus", "artifact_dont_need");
        sendSystemMessage(player, msg);
        return SCRIPT_CONTINUE;
    }
    public void updateProximityVolume(obj_id self) throws InterruptedException
    {
        float range = getProximityRange(self);
        if (range > 0.0f)
        {
            if (!hasTriggerVolume(self, PROXIMITY_VOLUME))
            {
                createTriggerVolume(PROXIMITY_VOLUME, range, true);
            }
            return;
        }
        if (hasTriggerVolume(self, PROXIMITY_VOLUME))
        {
            removeTriggerVolume(PROXIMITY_VOLUME);
        }
    }
    public float getProximityRange(obj_id self) throws InterruptedException
    {
        float range = 0.0f;
        if (hasObjVar(self, OBJVAR_PROXIMITY_GROUP))
        {
            String group = getStringObjVar(self, OBJVAR_PROXIMITY_GROUP);
            if (group != null && group.length() > 0)
            {
                String rangeObjVar = OBJVAR_PROXIMITY_RANGE_PREFIX + group;
                if (hasObjVar(self, rangeObjVar))
                {
                    range = getFloatObjVar(self, rangeObjVar);
                }
            }
        }
        if (range <= 0.0f && hasObjVar(self, OBJVAR_PROXIMITY_RANGE))
        {
            range = getFloatObjVar(self, OBJVAR_PROXIMITY_RANGE);
        }
        return range;
    }
    public void sendRetrieveObjectFoundMessage(obj_id self, obj_id player) throws InterruptedException
    {
        String artifactTask = getStringObjVar(self, "artifactTask");
        CustomerServiceLog("rodstart_artifact", "Pickup success: player=" + player + " task=" + artifactTask + " object=" + self);
        CustomerServiceLog("rodstart_artifact", "Quest increment: player=" + player + " task=" + artifactTask + " object=" + self);
        dictionary webster = new dictionary();
        webster.put("source", self);
        messageTo(player, "questRetrieveItemObjectFound", webster, 0, false);
        sendSystemMessage(player, new string_id("nexus", "artifact_used"));
        if (hasObjVar(self, "questRetrieveSignal"))
        {
            String questRetrieveSignal = getStringObjVar(self, "questRetrieveSignal");
            if (questRetrieveSignal != null && questRetrieveSignal.length() > 0)
            {
                groundquests.sendSignal(player, questRetrieveSignal);
            }
        }
        if (hasObjVar(self, "questFlavorObject"))
        {
            messageTo(self, "handleQuestFlavorObject", null, 0, false);
        }
        if (groundquests.hasCompletedTask(player, ARTIFACT_QUEST_NAME, artifactTask))
        {
            CustomerServiceLog("rodstart_artifact", "Quest completion: player=" + player + " task=" + artifactTask + " object=" + self);
        }
        return;
    }

}
