package script.library;

import script.dictionary;
import script.location;
import script.obj_id;
import script.region;
import script.string_id;
import script.library.create;
import script.library.groundquests;
import script.library.locations;
import script.library.utils;

public class rodstart_artifact extends script.base_script
{
    public rodstart_artifact()
    {
    }
    public static final String QUEST_NAME = "rodstart";
    public static final String DATATABLE = "datatables/theme_park/dathomir/rodstart_artifact_search.tab";
    public static final String SPAWN_DATATABLE = "datatables/theme_park/dathomir/rodstart_artifact_spawns.tab";
    public static final String COLUMN_TASK_NAME = "TASK_NAME";
    public static final String COLUMN_PLANET = "PLANET";
    public static final String COLUMN_X = "X";
    public static final String COLUMN_Y = "Y";
    public static final String COLUMN_Z = "Z";
    public static final String COLUMN_RADIUS = "SEARCH_AREA_RADIUS";
    public static final String COLUMN_SPAWN_RADIUS = "SPAWN_RADIUS";
    public static final String COLUMN_REGION = "REGION_NAME";
    public static final String COLUMN_TEMPLATE = "TEMPLATE";
    public static final String COLUMN_DESPAWN_SECONDS = "DESPAWN_SECONDS";
    public static final String COLUMN_RESPAWN_SECONDS = "RESPAWN_SECONDS";
    public static final String COLUMN_PROXIMITY_RANGE = "PROXIMITY_RANGE";
    public static final String OBJVAR_SEARCH_WAYPOINT_PREFIX = "quest.rodstart.search_area.";
    public static final String OBJVAR_SPAWN_ID_PREFIX = "quest.rodstart.artifact.spawn.";
    public static final String OBJVAR_SPAWN_RETRY_PREFIX = "quest.rodstart.artifact.spawn_retry.";
    public static final string_id SEARCH_AREA_NAME = new string_id("treasure_map/treasure_map", "search_area");
    public static final String WAYPOINT_COLOR = "yellow";
    public static final int DEFAULT_DESPAWN_SECONDS = 900;
    public static final int DEFAULT_RESPAWN_SECONDS = 60;

    public static boolean isRodstartQuest(String questName) throws InterruptedException
    {
        return questName != null && questName.equals(QUEST_NAME);
    }

    public static void handleTaskActivated(obj_id player, int questCrc, int taskId) throws InterruptedException
    {
        String questName = questGetQuestName(questCrc);
        if (!isRodstartQuest(questName))
        {
            return;
        }
        String taskName = groundquests.getTaskStringDataEntry(questCrc, taskId, groundquests.dataTableColumnTaskName);
        if (taskName == null || taskName.length() == 0)
        {
            return;
        }
        createSearchAreaWaypoint(player, taskName);
        queueSpawn(player, taskName);
    }

    public static void handleTaskDeactivated(obj_id player, int questCrc, int taskId) throws InterruptedException
    {
        String questName = questGetQuestName(questCrc);
        if (!isRodstartQuest(questName))
        {
            return;
        }
        String taskName = groundquests.getTaskStringDataEntry(questCrc, taskId, groundquests.dataTableColumnTaskName);
        if (taskName == null || taskName.length() == 0)
        {
            return;
        }
        removeSearchAreaWaypoint(player, taskName);
        clearSpawn(player, taskName);
        if (!hasAnyActiveRodstartRetrieveTask(player))
        {
            if (hasScript(player, "quest.rodstart.artifact_spawner"))
            {
                detachScript(player, "quest.rodstart.artifact_spawner");
            }
        }
    }

    public static void createSearchAreaWaypoint(obj_id player, String taskName) throws InterruptedException
    {
        if (!isIdValid(player) || taskName == null || taskName.length() == 0)
        {
            return;
        }
        dictionary row = getSearchAreaRow(taskName);
        if (row == null || row.isEmpty())
        {
            return;
        }
        String objvarName = OBJVAR_SEARCH_WAYPOINT_PREFIX + taskName;
        if (hasObjVar(player, objvarName))
        {
            obj_id existing = getObjIdObjVar(player, objvarName);
            if (isIdValid(existing))
            {
                destroyWaypointInDatapad(existing, player);
            }
            removeObjVar(player, objvarName);
        }
        location baseLoc = getSearchBaseLocation(row);
        if (baseLoc == null)
        {
            return;
        }
        float radius = row.getFloat(COLUMN_RADIUS);
        location waypointLoc = utils.getRandomLocationInRing(baseLoc, 0.0f, radius);
        if (waypointLoc == null)
        {
            waypointLoc = baseLoc;
        }
        obj_id waypoint = createWaypointInDatapad(player, waypointLoc);
        if (!isIdValid(waypoint))
        {
            return;
        }
        setWaypointName(waypoint, localize(SEARCH_AREA_NAME));
        setWaypointColor(waypoint, WAYPOINT_COLOR);
        setWaypointActive(waypoint, true);
        setObjVar(player, objvarName, waypoint);
    }

    public static void removeSearchAreaWaypoint(obj_id player, String taskName) throws InterruptedException
    {
        if (!isIdValid(player) || taskName == null || taskName.length() == 0)
        {
            return;
        }
        String objvarName = OBJVAR_SEARCH_WAYPOINT_PREFIX + taskName;
        if (hasObjVar(player, objvarName))
        {
            obj_id waypoint = getObjIdObjVar(player, objvarName);
            if (isIdValid(waypoint))
            {
                destroyWaypointInDatapad(waypoint, player);
            }
            removeObjVar(player, objvarName);
        }
    }

    private static dictionary getSearchAreaRow(String taskName) throws InterruptedException
    {
        int rowCount = dataTableGetNumRows(DATATABLE);
        for (int i = 0; i < rowCount; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE, i);
            if (row == null || row.isEmpty())
            {
                continue;
            }
            String rowTask = row.getString(COLUMN_TASK_NAME);
            if (rowTask != null && rowTask.equals(taskName))
            {
                return row;
            }
        }
        return null;
    }

    private static location getSearchBaseLocation(dictionary row) throws InterruptedException
    {
        String planet = row.getString(COLUMN_PLANET);
        String regionName = row.getString(COLUMN_REGION);
        location baseLoc = null;
        if (regionName != null && regionName.length() > 0)
        {
            region searchRegion = getRegion(planet, regionName);
            if (searchRegion != null)
            {
                baseLoc = locations.getRegionCenter(searchRegion);
                if (baseLoc != null)
                {
                    baseLoc.area = planet;
                }
            }
        }
        if (baseLoc == null)
        {
            baseLoc = new location(row.getFloat(COLUMN_X), row.getFloat(COLUMN_Y), row.getFloat(COLUMN_Z), planet);
        }
        return baseLoc;
    }

    public static void queueSpawn(obj_id player, String taskName) throws InterruptedException
    {
        if (!isIdValid(player) || taskName == null || taskName.length() == 0)
        {
            return;
        }
        if (!hasScript(player, "quest.rodstart.artifact_spawner"))
        {
            attachScript(player, "quest.rodstart.artifact_spawner");
        }
        dictionary params = new dictionary();
        params.put("taskName", taskName);
        messageTo(player, "rodstartArtifactSpawn", params, 0, false);
    }

    public static boolean hasActiveSpawn(obj_id player, String taskName) throws InterruptedException
    {
        String objvarName = OBJVAR_SPAWN_ID_PREFIX + taskName;
        if (hasObjVar(player, objvarName))
        {
            obj_id spawn = getObjIdObjVar(player, objvarName);
            if (isIdValid(spawn))
            {
                return true;
            }
            removeObjVar(player, objvarName);
        }
        return false;
    }

    public static void trackSpawn(obj_id player, String taskName, obj_id spawn) throws InterruptedException
    {
        if (!isIdValid(player) || taskName == null || taskName.length() == 0)
        {
            return;
        }
        String objvarName = OBJVAR_SPAWN_ID_PREFIX + taskName;
        setObjVar(player, objvarName, spawn);
    }

    public static void clearSpawn(obj_id player, String taskName) throws InterruptedException
    {
        if (!isIdValid(player) || taskName == null || taskName.length() == 0)
        {
            return;
        }
        String objvarName = OBJVAR_SPAWN_ID_PREFIX + taskName;
        if (hasObjVar(player, objvarName))
        {
            obj_id spawn = getObjIdObjVar(player, objvarName);
            if (isIdValid(spawn))
            {
                destroyObject(spawn);
            }
            removeObjVar(player, objvarName);
        }
        clearSpawnRetry(player, taskName);
    }

    public static void clearAllSpawns(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        int rowCount = dataTableGetNumRows(SPAWN_DATATABLE);
        for (int i = 0; i < rowCount; i++)
        {
            dictionary row = dataTableGetRow(SPAWN_DATATABLE, i);
            if (row == null || row.isEmpty())
            {
                continue;
            }
            String taskName = row.getString(COLUMN_TASK_NAME);
            if (taskName != null && taskName.length() > 0)
            {
                clearSpawn(player, taskName);
            }
        }
    }

    public static obj_id spawnArtifactForTask(obj_id player, String taskName) throws InterruptedException
    {
        dictionary row = getSpawnRow(taskName);
        if (row == null || row.isEmpty())
        {
            return null;
        }
        location spawnLoc = getSpawnLocation(row);
        if (spawnLoc == null)
        {
            return null;
        }
        String template = row.getString(COLUMN_TEMPLATE);
        if (template == null || template.length() == 0)
        {
            return null;
        }
        obj_id spawn = create.object(template, spawnLoc);
        if (!isIdValid(spawn))
        {
            return null;
        }
        setObjVar(spawn, "artifactTask", taskName);
        float proximityRange = row.getFloat(COLUMN_PROXIMITY_RANGE);
        if (proximityRange > 0.0f)
        {
            setObjVar(spawn, "artifactProximityRange", proximityRange);
        }
        attachScript(spawn, "theme_park.dathomir.wod2.artifact");
        return spawn;
    }

    public static int incrementSpawnRetry(obj_id player, String taskName) throws InterruptedException
    {
        String objvarName = OBJVAR_SPAWN_RETRY_PREFIX + taskName;
        int count = 0;
        if (hasObjVar(player, objvarName))
        {
            count = getIntObjVar(player, objvarName);
        }
        count++;
        setObjVar(player, objvarName, count);
        return count;
    }

    public static void clearSpawnRetry(obj_id player, String taskName) throws InterruptedException
    {
        String objvarName = OBJVAR_SPAWN_RETRY_PREFIX + taskName;
        if (hasObjVar(player, objvarName))
        {
            removeObjVar(player, objvarName);
        }
    }

    public static int getSpawnDespawnSeconds(String taskName) throws InterruptedException
    {
        dictionary row = getSpawnRow(taskName);
        if (row == null || row.isEmpty())
        {
            return DEFAULT_DESPAWN_SECONDS;
        }
        int seconds = row.getInt(COLUMN_DESPAWN_SECONDS);
        return seconds > 0 ? seconds : DEFAULT_DESPAWN_SECONDS;
    }

    public static int getSpawnRespawnSeconds(String taskName) throws InterruptedException
    {
        dictionary row = getSpawnRow(taskName);
        if (row == null || row.isEmpty())
        {
            return DEFAULT_RESPAWN_SECONDS;
        }
        int seconds = row.getInt(COLUMN_RESPAWN_SECONDS);
        return seconds > 0 ? seconds : DEFAULT_RESPAWN_SECONDS;
    }

    private static boolean hasAnyActiveRodstartRetrieveTask(obj_id player) throws InterruptedException
    {
        dictionary tasks = groundquests.getActiveTasksForTaskType(player, "retrieve_item");
        if (tasks == null || tasks.isEmpty())
        {
            return false;
        }
        java.util.Enumeration keys = tasks.keys();
        while (keys.hasMoreElements())
        {
            String questCrcString = (String)keys.nextElement();
            int questCrc = utils.stringToInt(questCrcString);
            if (QUEST_NAME.equals(questGetQuestName(questCrc)))
            {
                return true;
            }
        }
        return false;
    }

    private static dictionary getSpawnRow(String taskName) throws InterruptedException
    {
        int rowCount = dataTableGetNumRows(SPAWN_DATATABLE);
        for (int i = 0; i < rowCount; i++)
        {
            dictionary row = dataTableGetRow(SPAWN_DATATABLE, i);
            if (row == null || row.isEmpty())
            {
                continue;
            }
            String rowTask = row.getString(COLUMN_TASK_NAME);
            if (rowTask != null && rowTask.equals(taskName))
            {
                return row;
            }
        }
        return null;
    }

    private static location getSpawnLocation(dictionary row) throws InterruptedException
    {
        String planet = row.getString(COLUMN_PLANET);
        String regionName = row.getString(COLUMN_REGION);
        location baseLoc = null;
        if (regionName != null && regionName.length() > 0)
        {
            region searchRegion = getRegion(planet, regionName);
            if (searchRegion != null)
            {
                baseLoc = locations.getRegionCenter(searchRegion);
                if (baseLoc != null)
                {
                    baseLoc.area = planet;
                }
            }
        }
        if (baseLoc == null)
        {
            baseLoc = new location(row.getFloat(COLUMN_X), row.getFloat(COLUMN_Y), row.getFloat(COLUMN_Z), planet);
        }
        float radius = row.getFloat(COLUMN_SPAWN_RADIUS);
        if (radius > 0.0f)
        {
            location spawnLoc = utils.getRandomLocationInRing(baseLoc, 0.0f, radius);
            if (spawnLoc != null)
            {
                spawnLoc.y = getHeightAtLocation(spawnLoc.x, spawnLoc.z);
                return spawnLoc;
            }
        }
        baseLoc.y = getHeightAtLocation(baseLoc.x, baseLoc.z);
        return baseLoc;
    }
}
