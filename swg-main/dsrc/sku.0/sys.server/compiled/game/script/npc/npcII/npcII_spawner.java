package script.npc.npcII;

import script.dictionary;
import script.library.create;
import script.library.utils;
import script.location;
import script.obj_id;

public class npcII_spawner extends script.base_script
{
    public npcII_spawner()
    {
    }

    private static final String LOG_CHANNEL = "npcII";
    private static final String SCRIPT_BRAIN = "systems.npcii.npcii_brain";
    private static final String SCRIPT_CONVERSATION = "npc.npcII.npcII_conversation";
    private static final String OBJVAR_TRACKED_ID = "npcII.spawner.trackedId";
    private static final String SCRIPTVAR_TRACKED_ID = "npcII.spawner.trackedId";
    private static final String OBJVAR_MANAGER_ID = "npcII.spawner.manager";
    private static final String OBJVAR_IS_MANAGED = "npcII.spawner.isManaged";
    private static final String OBJVAR_RESPAWN_PENDING = "npcII.spawner.respawnPending";
    private static final String OBJVAR_RESPAWN_ATTEMPTS = "npcII.spawner.respawnAttempts";
    private static final String OBJVAR_SPAWN_LOCATION = "npcII.spawner.location";
    private static final String OBJVAR_SPAWN_TEMPLATE = "npcII.spawner.template";
    private static final String OBJVAR_CREATURE_LEVEL = "creature_attribs.level";
    private static final String SCRIPTVAR_AI_LEVEL = "ai.level";
    private static final int NPCII_BASE_LEVEL = 10;
    private static final float INITIALIZE_DELAY = 1.0f;
    private static final float BASE_RESPAWN_DELAY = 15.0f;
    private static final float RESPAWN_BACKOFF_STEP = 15.0f;
    private static final float MAX_RESPAWN_DELAY = 180.0f;
    private static final float DUPLICATE_SCAN_RADIUS = 50000.0f;

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "initializeNpcII", null, INITIALIZE_DELAY, false);
        return SCRIPT_CONTINUE;
    }

    public int initializeNpcII(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || isManagedNpc(self))
        {
            return SCRIPT_CONTINUE;
        }

        obj_id controller = getController(self);
        if (!isIdValid(controller) || !exists(controller))
        {
            LOG(LOG_CHANNEL, "SPAWNER_INIT_NO_CONTROLLER manager=" + self);
            return SCRIPT_CONTINUE;
        }

        obj_id trackedNpc = getTrackedNpc(controller);
        if (isIdValid(trackedNpc) && exists(trackedNpc))
        {
            obj_id canonical = cleanupDuplicates(self, controller, trackedNpc);
            if (isIdValid(canonical) && exists(canonical))
            {
                setCanonicalNpc(controller, canonical);
                removeObjVar(controller, OBJVAR_RESPAWN_PENDING);
                LOG(LOG_CHANNEL, "SPAWNER_INIT_FOUND manager=" + self + " controller=" + controller + " npc=" + canonical);
                return SCRIPT_CONTINUE;
            }
        }

        clearCanonicalNpc(controller);
        if (hasObjVar(controller, OBJVAR_RESPAWN_PENDING))
        {
            return SCRIPT_CONTINUE;
        }

        spawnNpcII(self, controller);
        return SCRIPT_CONTINUE;
    }

    public int OnDestroy(obj_id self) throws InterruptedException
    {
        if (!isManagedNpc(self))
        {
            return SCRIPT_CONTINUE;
        }

        obj_id manager = hasObjVar(self, OBJVAR_MANAGER_ID) ? getObjIdObjVar(self, OBJVAR_MANAGER_ID) : null;
        if (isIdValid(manager) && exists(manager))
        {
            dictionary d = new dictionary();
            d.put("npcId", self);
            messageTo(manager, "handleNpcIIDestroyed", d, 0.0f, false);
        }
        LOG(LOG_CHANNEL, "NPC_DESTROYED npc=" + self + " manager=" + manager);
        return SCRIPT_CONTINUE;
    }

    public int OnUnloadedFromMemory(obj_id self) throws InterruptedException
    {
        if (!isManagedNpc(self))
        {
            return SCRIPT_CONTINUE;
        }

        obj_id manager = hasObjVar(self, OBJVAR_MANAGER_ID) ? getObjIdObjVar(self, OBJVAR_MANAGER_ID) : null;
        if (isIdValid(manager) && exists(manager))
        {
            dictionary d = new dictionary();
            d.put("npcId", self);
            messageTo(manager, "handleNpcIIUnloaded", d, 0.0f, false);
        }
        LOG(LOG_CHANNEL, "NPC_UNLOADED npc=" + self + " manager=" + manager);
        return SCRIPT_CONTINUE;
    }

    public int handleNpcIIUnloaded(obj_id self, dictionary params) throws InterruptedException
    {
        return handleNpcGone(self, params, "UNLOADED");
    }

    public int handleNpcIIDestroyed(obj_id self, dictionary params) throws InterruptedException
    {
        return handleNpcGone(self, params, "DESTROYED");
    }

    private int handleNpcGone(obj_id self, dictionary params, String reason) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self))
        {
            return SCRIPT_CONTINUE;
        }

        obj_id controller = getController(self);
        if (!isIdValid(controller) || !exists(controller))
        {
            return SCRIPT_CONTINUE;
        }

        obj_id goneNpc = params != null && params.containsKey("npcId") ? params.getObjId("npcId") : null;
        obj_id trackedNpc = getTrackedNpc(controller);
        if (!isIdValid(trackedNpc) || trackedNpc == goneNpc)
        {
            clearCanonicalNpc(controller);
        }

        scheduleRespawn(self, controller, reason);
        return SCRIPT_CONTINUE;
    }

    private void spawnNpcII(obj_id manager, obj_id controller) throws InterruptedException
    {
        location spawnLocation = getConfiguredSpawnLocation(manager);
        String template = hasObjVar(manager, OBJVAR_SPAWN_TEMPLATE) ? getStringObjVar(manager, OBJVAR_SPAWN_TEMPLATE) : "npcII";

        obj_id npc = create.object(template, spawnLocation);
        if (!isIdValid(npc) || !exists(npc))
        {
            scheduleRespawn(manager, controller, "SPAWN_FAILED");
            LOG(LOG_CHANNEL, "SPAWN_FAILED manager=" + manager + " controller=" + controller + " template=" + template);
            return;
        }

        setName(npc, "npcII");
        setObjVar(npc, OBJVAR_MANAGER_ID, manager);
        setObjVar(npc, OBJVAR_IS_MANAGED, 1);
        applyNpcIICombatLevelFields(npc);
        if (!hasScript(npc, "npc.npcII.npcII_spawner"))
        {
            attachScript(npc, "npc.npcII.npcII_spawner");
        }
        if (!hasScript(npc, SCRIPT_BRAIN))
        {
            attachScript(npc, SCRIPT_BRAIN);
        }
        if (!hasScript(npc, SCRIPT_CONVERSATION))
        {
            attachScript(npc, SCRIPT_CONVERSATION);
        }

        obj_id canonical = cleanupDuplicates(manager, controller, npc);
        setCanonicalNpc(controller, canonical);
        removeObjVar(controller, OBJVAR_RESPAWN_PENDING);
        removeObjVar(controller, OBJVAR_RESPAWN_ATTEMPTS);

        LOG(LOG_CHANNEL, "SPAWN_SUCCESS manager=" + manager + " controller=" + controller + " npc=" + canonical + " loc=" + spawnLocation);
    }

    private void applyNpcIICombatLevelFields(obj_id npc) throws InterruptedException
    {
        // Expected npcII level keys consumed by combat/AI calculations:
        // - Core object level (setLevel/getLevel)
        // - Objvar: creature_attribs.level
        // - Scriptvar: ai.level
        setLevel(npc, NPCII_BASE_LEVEL);
        setObjVar(npc, OBJVAR_CREATURE_LEVEL, NPCII_BASE_LEVEL);
        utils.setScriptVar(npc, SCRIPTVAR_AI_LEVEL, NPCII_BASE_LEVEL);
        LOG(LOG_CHANNEL, "SPAWN_LEVEL_FIELDS_SET npc=" + npc + " level=" + NPCII_BASE_LEVEL);
    }

    private void scheduleRespawn(obj_id manager, obj_id controller, String reason) throws InterruptedException
    {
        if (hasObjVar(controller, OBJVAR_RESPAWN_PENDING))
        {
            return;
        }

        int attempts = hasObjVar(controller, OBJVAR_RESPAWN_ATTEMPTS) ? getIntObjVar(controller, OBJVAR_RESPAWN_ATTEMPTS) : 0;
        attempts++;
        setObjVar(controller, OBJVAR_RESPAWN_ATTEMPTS, attempts);
        setObjVar(controller, OBJVAR_RESPAWN_PENDING, 1);

        float delay = BASE_RESPAWN_DELAY + ((attempts - 1) * RESPAWN_BACKOFF_STEP);
        if (delay > MAX_RESPAWN_DELAY)
        {
            delay = MAX_RESPAWN_DELAY;
        }

        LOG(LOG_CHANNEL, "RESPAWN_SCHEDULED manager=" + manager + " controller=" + controller + " reason=" + reason + " attempt=" + attempts + " delay=" + delay);
        messageTo(manager, "initializeNpcII", null, delay, false);
    }

    private obj_id cleanupDuplicates(obj_id manager, obj_id controller, obj_id keepNpc) throws InterruptedException
    {
        location center = getConfiguredSpawnLocation(manager);
        obj_id[] candidates = getAllObjectsWithObjVar(center, DUPLICATE_SCAN_RADIUS, OBJVAR_IS_MANAGED);
        if (candidates == null || candidates.length <= 1)
        {
            return keepNpc;
        }

        obj_id canonical = keepNpc;
        int duplicatesRemoved = 0;
        for (int i = 0; i < candidates.length; i++)
        {
            obj_id candidate = candidates[i];
            if (!isIdValid(candidate) || !exists(candidate))
            {
                continue;
            }
            if (candidate == canonical)
            {
                continue;
            }

            obj_id candidateManager = hasObjVar(candidate, OBJVAR_MANAGER_ID) ? getObjIdObjVar(candidate, OBJVAR_MANAGER_ID) : null;
            if (isIdValid(candidateManager) && candidateManager != manager)
            {
                continue;
            }

            destroyObject(candidate);
            duplicatesRemoved++;
            LOG(LOG_CHANNEL, "DUPLICATE_CLEANUP destroyed=" + candidate + " kept=" + canonical + " controller=" + controller);
        }

        if (duplicatesRemoved > 0)
        {
            LOG(LOG_CHANNEL, "DUPLICATE_CLEANUP_SUMMARY kept=" + canonical + " removed=" + duplicatesRemoved + " controller=" + controller);
        }
        return canonical;
    }

    private location getConfiguredSpawnLocation(obj_id manager) throws InterruptedException
    {
        if (hasObjVar(manager, OBJVAR_SPAWN_LOCATION))
        {
            return getLocationObjVar(manager, OBJVAR_SPAWN_LOCATION);
        }
        return getLocation(manager);
    }

    private obj_id getController(obj_id self) throws InterruptedException
    {
        location here = getLocation(self);
        return getPlanetByName(here.area);
    }

    private obj_id getTrackedNpc(obj_id controller) throws InterruptedException
    {
        obj_id tracked = null;
        if (hasObjVar(controller, OBJVAR_TRACKED_ID))
        {
            tracked = getObjIdObjVar(controller, OBJVAR_TRACKED_ID);
        }
        if (!isIdValid(tracked) && utils.hasScriptVar(controller, SCRIPTVAR_TRACKED_ID))
        {
            tracked = utils.getObjIdScriptVar(controller, SCRIPTVAR_TRACKED_ID);
        }
        return tracked;
    }

    private void setCanonicalNpc(obj_id controller, obj_id npc) throws InterruptedException
    {
        setObjVar(controller, OBJVAR_TRACKED_ID, npc);
        utils.setScriptVar(controller, SCRIPTVAR_TRACKED_ID, npc);
    }

    private void clearCanonicalNpc(obj_id controller) throws InterruptedException
    {
        removeObjVar(controller, OBJVAR_TRACKED_ID);
        utils.removeScriptVar(controller, SCRIPTVAR_TRACKED_ID);
    }

    private boolean isManagedNpc(obj_id self) throws InterruptedException
    {
        return hasObjVar(self, OBJVAR_IS_MANAGED) && getIntObjVar(self, OBJVAR_IS_MANAGED) == 1;
    }
}
