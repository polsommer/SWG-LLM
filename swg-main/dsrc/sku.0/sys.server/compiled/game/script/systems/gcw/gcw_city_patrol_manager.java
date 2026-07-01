package script.systems.gcw;

import script.*;
import script.library.*;

public class gcw_city_patrol_manager extends script.base_script
{
    public gcw_city_patrol_manager()
    {
    }

    public static final String ROUTE_TABLE_ROOT = "datatables/event/gcw_city_patrol_routes/";
    public static final String VAR_CITY_NAME = "gcwCityPatrol.cityName";
    public static final String VAR_CURRENT_FACTION = "gcwCityPatrol.currentFaction";
    public static final String VAR_LAST_FLIP_TIME = "gcwCityPatrol.lastFlipTime";
    public static final String VAR_GUARDS = "gcwCityPatrol.guards";
    public static final String VAR_TABLE = "gcwCityPatrol.routeTable";
    public static final int FACTION_FLIP_COOLDOWN_SECONDS = 120;
    public static final int PATROL_REFRESH_SECONDS = 30;
    public static final int MAX_GUARDS_PER_CITY = 16;

    public int OnAttach(obj_id self) throws InterruptedException
    {
        messageTo(self, "initializeCityPatrolManager", null, 1.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "initializeCityPatrolManager", null, 1.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnDestroy(obj_id self) throws InterruptedException
    {
        despawnAllPatrols(self);
        return SCRIPT_CONTINUE;
    }

    public int OnAboutToBeDestroyed(obj_id self) throws InterruptedException
    {
        despawnAllPatrols(self);
        return SCRIPT_CONTINUE;
    }

    public int initializeCityPatrolManager(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self))
        {
            return SCRIPT_CONTINUE;
        }

        String cityName = gcw.getCityFromTable(self);
        if (cityName == null || cityName.length() <= 0)
        {
            return SCRIPT_CONTINUE;
        }

        String routeTable = ROUTE_TABLE_ROOT + cityName + ".iff";
        if (dataTableGetNumRows(routeTable) <= 0)
        {
            LOG("gcw_city_patrol", "initializeCityPatrolManager missing or empty route table for city " + cityName + " table: " + routeTable);
            return SCRIPT_CONTINUE;
        }

        utils.setScriptVar(self, VAR_CITY_NAME, cityName);
        utils.setScriptVar(self, VAR_TABLE, routeTable);
        messageTo(self, "updateCityPatrols", null, 2.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int updateCityPatrols(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self))
        {
            return SCRIPT_CONTINUE;
        }

        if (!utils.hasScriptVar(self, VAR_TABLE))
        {
            messageTo(self, "initializeCityPatrolManager", null, 10.0f, false);
            return SCRIPT_CONTINUE;
        }

        int desiredFaction = getCityAlignmentFaction(self);
        if (desiredFaction != factions.FACTION_FLAG_REBEL && desiredFaction != factions.FACTION_FLAG_IMPERIAL)
        {
            messageTo(self, "updateCityPatrols", null, PATROL_REFRESH_SECONDS, false);
            return SCRIPT_CONTINUE;
        }

        cleanupInvalidPatrolList(self);

        int currentFaction = utils.hasScriptVar(self, VAR_CURRENT_FACTION) ? utils.getIntScriptVar(self, VAR_CURRENT_FACTION) : -1;
        if (currentFaction != desiredFaction)
        {
            int now = getGameTime();
            int lastFlipTime = utils.hasScriptVar(self, VAR_LAST_FLIP_TIME) ? utils.getIntScriptVar(self, VAR_LAST_FLIP_TIME) : 0;
            if (currentFaction != -1 && now - lastFlipTime < FACTION_FLIP_COOLDOWN_SECONDS)
            {
                messageTo(self, "updateCityPatrols", null, 10.0f, false);
                return SCRIPT_CONTINUE;
            }

            despawnAllPatrols(self);
            if (spawnFactionPatrols(self, desiredFaction))
            {
                utils.setScriptVar(self, VAR_CURRENT_FACTION, desiredFaction);
                utils.setScriptVar(self, VAR_LAST_FLIP_TIME, now);
            }
        }
        else
        {
            ensurePatrolPopulation(self, desiredFaction);
        }

        messageTo(self, "updateCityPatrols", null, PATROL_REFRESH_SECONDS, false);
        return SCRIPT_CONTINUE;
    }

    public int handleCityFactionUpdated(obj_id self, dictionary params) throws InterruptedException
    {
        messageTo(self, "updateCityPatrols", null, 0.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int getCityAlignmentFaction(obj_id self) throws InterruptedException
    {
        int rebelPercent = gcw.getRebelPercentileByRegion(self);
        if (rebelPercent > 50)
        {
            return factions.FACTION_FLAG_REBEL;
        }
        return factions.FACTION_FLAG_IMPERIAL;
    }

    public boolean ensurePatrolPopulation(obj_id self, int faction) throws InterruptedException
    {
        obj_id[] guards = utils.getObjIdArrayScriptVar(self, VAR_GUARDS);
        if (guards != null && guards.length > 0)
        {
            return true;
        }
        return spawnFactionPatrols(self, faction);
    }

    public boolean spawnFactionPatrols(obj_id self, int faction) throws InterruptedException
    {
        if (!utils.hasScriptVar(self, VAR_TABLE))
        {
            return false;
        }

        String table = utils.getStringScriptVar(self, VAR_TABLE);
        int rows = dataTableGetNumRows(table);
        if (rows <= 0)
        {
            return false;
        }

        String currentEntry = "";
        obj_id[] spawned = new obj_id[0];

        float spawnX = 0;
        float spawnY = 0;
        float spawnZ = 0;
        float holdX = 0;
        float holdY = 0;
        float holdZ = 0;
        int tier = 1;
        String route = "";

        for (int i = 0; i < rows; i++)
        {
            String entryId = dataTableGetString(table, i, "entry_id");
            if (entryId == null || entryId.length() <= 0)
            {
                continue;
            }

            if (!entryId.equals(currentEntry))
            {
                if (currentEntry.length() > 0 && spawned.length < MAX_GUARDS_PER_CITY)
                {
                    obj_id guard = spawnGuard(self, faction, tier, spawnX, spawnY, spawnZ, holdX, holdY, holdZ, route, currentEntry);
                    if (isIdValid(guard) && exists(guard))
                    {
                        spawned = addObjIdToArray(spawned, guard);
                    }
                }

                currentEntry = entryId;
                spawnX = dataTableGetFloat(table, i, "spawn_x");
                spawnY = dataTableGetFloat(table, i, "spawn_y");
                spawnZ = dataTableGetFloat(table, i, "spawn_z");
                holdX = dataTableGetFloat(table, i, "hold_x");
                holdY = dataTableGetFloat(table, i, "hold_y");
                holdZ = dataTableGetFloat(table, i, "hold_z");
                tier = dataTableGetInt(table, i, "tier");
                route = "";
            }

            String routeNode = dataTableGetFloat(table, i, "waypoint_x") + "," + dataTableGetFloat(table, i, "waypoint_y") + "," + dataTableGetFloat(table, i, "waypoint_z");
            if (route == null || route.length() <= 0)
            {
                route = routeNode;
            }
            else
            {
                route += ";" + routeNode;
            }
        }

        if (currentEntry.length() > 0 && spawned.length < MAX_GUARDS_PER_CITY)
        {
            obj_id guard = spawnGuard(self, faction, tier, spawnX, spawnY, spawnZ, holdX, holdY, holdZ, route, currentEntry);
            if (isIdValid(guard) && exists(guard))
            {
                spawned = addObjIdToArray(spawned, guard);
            }
        }

        if (spawned.length > 0)
        {
            utils.setScriptVar(self, VAR_GUARDS, spawned);
            return true;
        }
        return false;
    }

    public obj_id spawnGuard(obj_id self, int faction, int tier, float spawnX, float spawnY, float spawnZ, float holdX, float holdY, float holdZ, String route, String entryId) throws InterruptedException
    {
        location spawnLoc = new location(spawnX, spawnY, spawnZ, getLocation(self).area, null);
        String template = getGuardTemplateForTier(faction, tier);
        if (template == null || template.length() <= 0)
        {
            return null;
        }

        obj_id guard = create.object(template, spawnLoc);
        if (!isIdValid(guard) || !exists(guard))
        {
            return null;
        }

        setHibernationDelay(guard, 3600.0f);
        setObjVar(guard, "gcw.cityPatrol.manager", self);
        setObjVar(guard, "gcw.cityPatrol.entry", entryId);
        setObjVar(guard, "gcw.cityPatrol.route", route);
        setObjVar(guard, "gcw.cityPatrol.holdX", holdX);
        setObjVar(guard, "gcw.cityPatrol.holdY", holdY);
        setObjVar(guard, "gcw.cityPatrol.holdZ", holdZ);
        setObjVar(guard, "gcw.cityPatrol.faction", faction);
        attachScript(guard, "systems.gcw.gcw_city_patrol_guard");
        return guard;
    }

    public String getGuardTemplateForTier(int faction, int tier) throws InterruptedException
    {
        String[] pool = gcw_patrol.normalImperials;
        if (tier <= 0)
        {
            if (faction == factions.FACTION_FLAG_REBEL)
            {
                pool = gcw_patrol.lowRebels;
            }
            else
            {
                pool = gcw_patrol.lowImperials;
            }
        }
        else if (tier == 1)
        {
            if (faction == factions.FACTION_FLAG_REBEL)
            {
                pool = gcw_patrol.normalRebels;
            }
            else
            {
                pool = gcw_patrol.normalImperials;
            }
        }
        else
        {
            if (faction == factions.FACTION_FLAG_REBEL)
            {
                pool = gcw_patrol.eliteRebels;
            }
            else
            {
                pool = gcw_patrol.eliteImperials;
            }
        }

        if (pool == null || pool.length <= 0)
        {
            return null;
        }
        return pool[rand(0, pool.length - 1)];
    }

    public void cleanupInvalidPatrolList(obj_id self) throws InterruptedException
    {
        obj_id[] guards = utils.getObjIdArrayScriptVar(self, VAR_GUARDS);
        if (guards == null || guards.length <= 0)
        {
            return;
        }

        obj_id[] validGuards = new obj_id[0];
        for (obj_id guard : guards)
        {
            if (!isIdValid(guard) || !exists(guard) || isDead(guard))
            {
                continue;
            }
            validGuards = addObjIdToArray(validGuards, guard);
        }

        if (validGuards.length > 0)
        {
            utils.setScriptVar(self, VAR_GUARDS, validGuards);
        }
        else
        {
            utils.removeScriptVar(self, VAR_GUARDS);
        }
    }

    public void despawnAllPatrols(obj_id self) throws InterruptedException
    {
        obj_id[] guards = utils.getObjIdArrayScriptVar(self, VAR_GUARDS);
        utils.removeScriptVar(self, VAR_GUARDS);
        if (guards == null || guards.length <= 0)
        {
            return;
        }

        for (obj_id guard : guards)
        {
            if (!isIdValid(guard) || !exists(guard))
            {
                continue;
            }
            destroyObject(guard);
        }
    }

    private obj_id[] addObjIdToArray(obj_id[] source, obj_id value) throws InterruptedException
    {
        if (!isIdValid(value))
        {
            return source;
        }

        if (source == null)
        {
            return new obj_id[]
            {
                value
            };
        }

        obj_id[] expanded = new obj_id[source.length + 1];
        System.arraycopy(source, 0, expanded, 0, source.length);
        expanded[source.length] = value;
        return expanded;
    }
}
