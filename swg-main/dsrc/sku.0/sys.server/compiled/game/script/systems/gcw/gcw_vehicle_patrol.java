package script.systems.gcw;

import script.*;
import script.library.*;

public class gcw_vehicle_patrol extends script.base_script
{
    public gcw_vehicle_patrol()
    {
    }
    public static final String IMPERIAL_GCW_VEHICLE_TEMPLATE = "object/mobile/vehicle/hoth_at_st.iff";
    public static final String REBEL_GCW_SNOWSPEEDER_TEMPLATE = "object/mobile/vehicle/snowspeeder.iff";
    public static final String REBEL_GCW_AT_XT_TEMPLATE = "object/mobile/vehicle/walker_at_xt_player.iff";
    public static final String REBEL_GCW_AT_XT_LEGACY_TEMPLATE = "object/mobile/vehicle/walker_at_xt.iff";
    public static final String[] imperialVehicleTemplates =
    {
        IMPERIAL_GCW_VEHICLE_TEMPLATE
    };
    public static final String[] rebelVehicleTemplates =
    {
        REBEL_GCW_SNOWSPEEDER_TEMPLATE,
        REBEL_GCW_AT_XT_TEMPLATE
    };
    public int OnAttach(obj_id self) throws InterruptedException
    {
        setObjVar(self, gcw.GCW_TOOL_TEMPLATE_OBJVAR, "object/tangible/gcw/crafting_quest/gcw_vehicle_tool.iff");
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "handleGCWPatrol", null, 120, false);
        return SCRIPT_CONTINUE;
    }
    
    public int OnGetAttributes(obj_id self, obj_id player, String[] names, String[] attribs) throws InterruptedException
    {
        if (!exists(self))
        {
            return SCRIPT_CONTINUE;
        }
        int idx = utils.getValidAttributeIndex(names);
        if (idx == -1)
        {
            return SCRIPT_CONTINUE;
        }
        names[idx] = "object_repaired";
        if (hasObjVar(self, gcw.GCW_OBJECT_REPAIR_COUNT))
        {
            int repairCount = getIntObjVar(self, gcw.GCW_OBJECT_REPAIR_COUNT);
            if (repairCount > 0)
            {
                attribs[idx] = "" + repairCount;
                idx++;
                return SCRIPT_CONTINUE;
            }
        }
        attribs[idx] = "Never Repaired";
        idx++;
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!utils.hasScriptVar(self, "faction"))
        {
            LOG("gcw_patrol_point", "no faction on turret obj");
            return SCRIPT_CONTINUE;
        }
        if (!factions.isPlayerSameGcwFactionAsSchedulerObject(player, self))
        {
            LOG("gcw_patrol_point", "faction invalid on turret obj");
            return SCRIPT_CONTINUE;
        }
        else 
        {
            if (utils.isProfession(player, utils.TRADER))
            {
                LOG("gcw_patrol_point", "player is trader");
                if (!gcw.canGcwObjectBeRepaired(self))
                {
                    LOG("gcw_patrol_point", "OnObjectMenuRequest no repair needed");
                    sendSystemMessage(player, gcw.SID_DOESNT_NEED_REPAIR);
                    return SCRIPT_CONTINUE;
                }
                if (!gcw.hasConstructionOrRepairTool(player, self))
                {
                    LOG("gcw_patrol_point", "OnObjectMenuRequest no resources");
                    gcw.playerSystemMessageResourceNeeded(player, self, false);
                    return SCRIPT_CONTINUE;
                }
                menu_info_data data = mi.getMenuItemByType(menu_info_types.ITEM_USE);
                if (data != null)
                {
                    data.setServerNotify(true);
                }
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        LOG("gcw_patrol_point", "OnObjectMenuSelect");
        if (!isIdValid(player) || !exists(player) || isIncapacitated(player) || isDead(player) || factions.isOnLeave(player))
        {
            return SCRIPT_CONTINUE;
        }
        if (!utils.hasScriptVar(self, "faction"))
        {
            LOG("gcw_patrol_point", "no faction on patrol obj");
            return SCRIPT_CONTINUE;
        }
        int faction = utils.getIntScriptVar(self, "faction");
        if (faction < 0)
        {
            LOG("gcw_patrol_point", "faction invalid on patrol obj");
            return SCRIPT_CONTINUE;
        }
        if (item != menu_info_types.ITEM_USE)
        {
            return SCRIPT_CONTINUE;
        }
        if (!factions.isPlayerSameGcwFactionAsSchedulerObject(player, self))
        {
            LOG("gcw_patrol_point", "Wrong Faction");
            return SCRIPT_CONTINUE;
        }
        else 
        {
            if (utils.isProfession(player, utils.TRADER))
            {
                LOG("gcw_patrol_point", "player is trader");
                if (!gcw.canGcwObjectBeRepaired(self))
                {
                    sendSystemMessage(player, gcw.SID_DOESNT_NEED_REPAIR);
                    return SCRIPT_CONTINUE;
                }
                if (!gcw.hasConstructionOrRepairTool(player, self))
                {
                    gcw.playerSystemMessageResourceNeeded(player, self, false);
                    return SCRIPT_CONTINUE;
                }
                LOG("gcw_patrol_point", "player can repair");
                if (groundquests.isQuestActive(player, gcw.GCW_REPAIR_VEHICLE_PATROL_QUEST))
                {
                    if (gcw.useGcwObjectForQuest(player, self, gcw.GCW_REPAIR_VEHICLE_PATROL_QUEST))
                    {
                        gcw.useConstructionOrRepairTool(player, self);
                    }
                    return SCRIPT_CONTINUE;
                }
                if (groundquests.hasCompletedQuest(player, gcw.GCW_REPAIR_VEHICLE_PATROL_QUEST))
                {
                    groundquests.clearQuest(player, gcw.GCW_REPAIR_VEHICLE_PATROL_QUEST);
                }
                if (!groundquests.isQuestActive(player, gcw.GCW_REPAIR_VEHICLE_PATROL_QUEST))
                {
                    groundquests.grantQuest(player, gcw.GCW_REPAIR_VEHICLE_PATROL_QUEST);
                }
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int getConstructionQuestsCompleted(obj_id pylon) throws InterruptedException
    {
        int completed = 0;
        if (!isIdValid(pylon) || !exists(pylon))
        {
            return 0;
        }
        if (hasObjVar(pylon, "gcw.constructionQuestsCompleted"))
        {
            completed = getIntObjVar(pylon, "gcw.constructionQuestsCompleted");
        }
        return completed;
    }
    public int handleGCWPatrol(obj_id self, dictionary params) throws InterruptedException
    {
        int faction = -1;
        if (utils.hasScriptVar(self, "faction"))
        {
            faction = utils.getIntScriptVar(self, "faction");
        }
        obj_id kit = utils.getObjIdScriptVar(self, "creator");
        if (!isIdValid(kit) || !exists(kit))
        {
            return SCRIPT_CONTINUE;
        }
        int construction = getConstructionQuestsCompleted(kit);
        String npcName = "";
        npcName = getVehicleTemplateFromKit(kit, faction);
        if (npcName == null || npcName.length() <= 0)
        {
            return SCRIPT_CONTINUE;
        }
        if (getSchedulerNPCs(kit, "gcwPatrol") < 0)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id npc = null;
        if (construction < 1)
        {
            if (!hasObjVar(self, "gcw.startupBattlefieldVehicleSpawned") && getSchedulerNPCs(kit, "gcwPatrol") < 500)
            {
                npc = createSchedulerNPC(kit, npcName);
                setObjVar(self, "gcw.startupBattlefieldVehicleSpawned", 1);
            }
        }
        else if (getSchedulerNPCs(kit, "gcwPatrol") < 500)
        {
            obj_id currentPatrol = utils.getObjIdScriptVar(kit, "currentPatrol");
            if (!isIdValid(currentPatrol) || !exists(currentPatrol) || isIncapacitated(currentPatrol) || isDead(currentPatrol))
            {
                npc = createSchedulerNPC(kit, npcName);
                utils.setScriptVar(kit, "currentPatrol", npc);
            }
        }
        if (isIdValid(npc) && exists(npc))
        {
            setObjVar(npc, "battlefield_vehicle.overrideAllowedZones", "all");
            attachScript(npc, "systems.vehicle_system.battlefield_vehicle");
            detachScript(npc, "systems.vehicle_system.vehicle_base");
            detachScript(npc, "systems.vehicle_system.vehicle_ping");
        }
        messageTo(self, "handleGCWPatrol", null, 30.0f + rand(0, 30), false);
        return SCRIPT_CONTINUE;
    }
    public int getSchedulerNPCs(obj_id kit, String npcID) throws InterruptedException
    {
        if (!isIdValid(kit) || !exists(kit))
        {
            return -1;
        }
        obj_id kitParent = trial.getParent(kit);
        if (!isIdValid(kitParent) || !exists(kitParent))
        {
            return -1;
        }
        obj_id[] npcs = trial.getObjectsInInstanceBySpawnId(trial.getParent(kit), npcID);
        if (npcs == null)
        {
            return 0;
        }
        return npcs.length;
    }
    public obj_id createSchedulerNPC(obj_id kit, String npcName) throws InterruptedException
    {
        if (!isIdValid(kit) || !exists(kit))
        {
            return null;
        }
        obj_id kitParent = trial.getParent(kit);
        if (!isIdValid(kitParent) || !exists(kitParent))
        {
            return null;
        }
        location loc = getLocation(kit);
        obj_id npc = create.object(npcName, loc);
        if (!isIdValid(npc) || !exists(npc))
        {
            return null;
        }
        trial.markAsTempObject(npc, true);
        trial.setParent(kitParent, npc, true);
        trial.setInterest(npc);
        setObjVar(npc, "spawn_id", "gcwPatrol");
        trial.storeSpawnedChild(kitParent, npc, "gcwPatrol");
        String patrol = getStringObjVar(kit, "patrolPoint");
        if (patrol != null && patrol.length() > 0)
        {
            dictionary path_data = utils.hasScriptVar(kitParent, trial.SEQUENCER_PATH_DATA) ? utils.getDictionaryScriptVar(kitParent, trial.SEQUENCER_PATH_DATA) : null;
            if (path_data != null && !path_data.isEmpty())
            {
                utils.setScriptVar(npc, trial.SEQUENCER_PATH_DATA, utils.getDictionaryScriptVar(kitParent, trial.SEQUENCER_PATH_DATA));
            }
            setObjVar(npc, "patrol_path", patrol);
            setHibernationDelay(npc, 3600.0f);
        }
        attachScript(npc, "systems.dungeon_sequencer.ai_controller");
        return npc;
    }
    public String getVehicleTemplateFromKit(obj_id kit, int faction) throws InterruptedException
    {
        if (!isIdValid(kit) || !exists(kit))
        {
            return "";
        }
        if (hasObjVar(kit, "vehicleTemplate"))
        {
            String overrideTemplate = getStringObjVar(kit, "vehicleTemplate");
            if (overrideTemplate != null && overrideTemplate.length() > 0)
            {
                return normalizeTemplateForFaction(overrideTemplate, faction);
            }
        }
        if (hasObjVar(kit, "vehicleTemplatePool"))
        {
            String templatePool = getStringObjVar(kit, "vehicleTemplatePool");
            if (templatePool != null && templatePool.length() > 0)
            {
                String[] templateList = split(templatePool, '|');
                if (templateList != null && templateList.length > 0)
                {
                    String selectedTemplate = templateList[rand(0, templateList.length - 1)];
                    return normalizeTemplateForFaction(selectedTemplate, faction);
                }
            }
        }
        if (faction == factions.FACTION_FLAG_REBEL)
        {
            return normalizeTemplateForFaction(rebelVehicleTemplates[rand(0, rebelVehicleTemplates.length - 1)], faction);
        }
        if (faction == factions.FACTION_FLAG_IMPERIAL)
        {
            return normalizeTemplateForFaction(imperialVehicleTemplates[rand(0, imperialVehicleTemplates.length - 1)], faction);
        }
        return "";
    }
    public String normalizeTemplateForFaction(String template, int faction) throws InterruptedException
    {
        if (template == null || template.length() <= 0)
        {
            return template;
        }
        if (faction == factions.FACTION_FLAG_IMPERIAL)
        {
            return IMPERIAL_GCW_VEHICLE_TEMPLATE;
        }
        if (faction == factions.FACTION_FLAG_REBEL)
        {
            if (template.equals(REBEL_GCW_AT_XT_LEGACY_TEMPLATE))
            {
                return REBEL_GCW_AT_XT_TEMPLATE;
            }
            if (!template.equals(REBEL_GCW_SNOWSPEEDER_TEMPLATE) && !template.equals(REBEL_GCW_AT_XT_TEMPLATE))
            {
                return rebelVehicleTemplates[rand(0, rebelVehicleTemplates.length - 1)];
            }
        }
        return template;
    }

    public int destroyGCWPatrol(obj_id self, dictionary params) throws InterruptedException
    {
        trial.cleanupObject(self);
        return SCRIPT_CONTINUE;
    }
    public int OnDeath(obj_id self, obj_id killer, obj_id corpseId) throws InterruptedException
    {
        handleDestroyPatrol(self, killer);
        return SCRIPT_CONTINUE;
    }
    public int OnObjectDisabled(obj_id self, obj_id killer) throws InterruptedException
    {
        handleDestroyPatrol(self, killer);
        return SCRIPT_CONTINUE;
    }
    public void handleDestroyPatrol(obj_id self, obj_id killer) throws InterruptedException
    {
        location death = getLocation(self);
        playClientEffectObj(killer, "clienteffect/combat_explosion_lair_large.cef", self, "");
        playClientEffectLoc(killer, "clienteffect/combat_explosion_lair_large.cef", death, 0);
        setInvulnerable(self, true);
        messageTo(self, "destroyGCWPatrol", null, 1.0f, false);
        return;
    }
}
