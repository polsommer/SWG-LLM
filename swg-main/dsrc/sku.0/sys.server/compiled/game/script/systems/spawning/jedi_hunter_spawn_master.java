package script.systems.spawning;

import script.dictionary;
import script.library.create;
import script.library.jedi_hunter;
import script.library.locations;
import script.library.utils;
import script.location;
import script.obj_id;
import script.region;

public class jedi_hunter_spawn_master extends script.base_script
{
    public jedi_hunter_spawn_master()
    {
    }

    public static final String CONFIG_TOGGLE = "enableJediHunterSpawners";
    public static final String VAR_PREFIX = "jediHunter";
    public static final String MSG_TICK = "jediHunterSpawnTick";
    public static final String MSG_DESPAWN = "jediHunterMobDestroyed";

    public static final String DEFAULT_TABLE = "datatables/spawning/jedi_hunters/default.tab";
    public static final String TABLE_ROOT = "datatables/spawning/jedi_hunters/";
    public static final String BEHAVIOR_TABLE = "datatables/spawning/jedi_hunters/behavior.tab";
    public static final String GROUP_TABLE = "datatables/spawning/jedi_hunters/group_weights.tab";
    public static final String SPAWN_POINT_TABLE = "datatables/spawning/jedi_hunters/spawn_points.tab";

    public static final String[] DEFAULT_PLANETS =
    {
        "corellia",
        "dantooine",
        "dathomir",
        "endor",
        "lok",
        "naboo",
        "rori",
        "talus",
        "tatooine",
        "yavin4"
    };

    public static final int DEFAULT_PLANET_CAP = 24;
    public static final int DEFAULT_MIN_GROUP_SIZE = 2;
    public static final int DEFAULT_MAX_GROUP_SIZE = 6;
    public static final float DEFAULT_MIN_TICK = 45.0f;
    public static final float DEFAULT_MAX_TICK = 90.0f;
    public static final float PLAYER_SCAN_RADIUS = 16384.0f;
    public static final float SPAWN_MIN_DISTANCE = 80.0f;
    public static final float SPAWN_MAX_DISTANCE = 160.0f;
    public static final int SPAWN_POINT_PICK_RETRIES = 10;
    public static final int FALLBACK_SPAWN_PICK_RETRIES = 30;
    public static final float SPAWN_POINT_PROXIMITY_BLOCK = 64.0f;
    public static final float SPAWN_POINT_NAV_CHECK_RADIUS = 10.0f;

    private static class spawn_point_pick
    {
        public location loc;
        public float heading;
        public boolean hasHeading;
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        initialize(self);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        initialize(self);
        return SCRIPT_CONTINUE;
    }

    public int OnUniverseComplete(obj_id self) throws InterruptedException
    {
        initialize(self);
        return SCRIPT_CONTINUE;
    }

    public int jediHunterSpawnTick(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isEnabled())
        {
            return SCRIPT_CONTINUE;
        }

        String[] planets = getActivePlanets(self);
        if (planets == null || planets.length == 0)
        {
            scheduleNextTick(self);
            return SCRIPT_CONTINUE;
        }

        String planet = planets[rand(0, planets.length - 1)];
        if (getPlanetSpawnCount(self, planet) >= getPlanetCap(self, planet))
        {
            scheduleNextTick(self);
            return SCRIPT_CONTINUE;
        }

        obj_id player = pickPlayerOnPlanet(planet);
        if (!isIdValid(player))
        {
            scheduleNextTick(self);
            return SCRIPT_CONTINUE;
        }

        spawn_point_pick selectedPoint = pickSpawnPointForPlanet(planet, player);
        location spawnLocation = selectedPoint != null ? selectedPoint.loc : null;
        if (!isValidLocation(spawnLocation))
        {
            spawnLocation = getSpawnLocationNearPlayer(player);
        }
        if (!isValidLocation(spawnLocation))
        {
            scheduleNextTick(self);
            return SCRIPT_CONTINUE;
        }

        int minGroup = getMinGroupSize(self, planet);
        int maxGroup = getMaxGroupSize(self, planet);
        int groupSize = pickWeightedGroupSize(planet, minGroup, maxGroup);
        String squadId = planet + "_" + getGameTime() + "_" + rand(1000, 9999);
        Float spawnHeading = (selectedPoint != null && selectedPoint.hasHeading) ? selectedPoint.heading : null;
        int spawned = spawnSquad(self, planet, spawnLocation, squadId, groupSize, spawnHeading);

        if (spawned > 0)
        {
            utils.setScriptVar(self, VAR_PREFIX + ".squad." + squadId + ".planet", planet);
            utils.setScriptVar(self, VAR_PREFIX + ".squad." + squadId + ".count", spawned);
            setObjVar(self, VAR_PREFIX + ".lastSquad", squadId);
        }

        scheduleNextTick(self);
        return SCRIPT_CONTINUE;
    }

    public int jediHunterMobDestroyed(obj_id self, dictionary params) throws InterruptedException
    {
        if (params == null)
        {
            return SCRIPT_CONTINUE;
        }

        String planet = params.getString("planet");
        String squadId = params.getString("squadId");

        if (planet != null && planet.length() > 0)
        {
            decrementPlanetSpawnCount(self, planet);
        }

        if (squadId != null && squadId.length() > 0)
        {
            String squadCountVar = VAR_PREFIX + ".squad." + squadId + ".count";
            if (utils.hasScriptVar(self, squadCountVar))
            {
                int remaining = utils.getIntScriptVar(self, squadCountVar) - 1;
                if (remaining <= 0)
                {
                    utils.removeScriptVar(self, squadCountVar);
                    utils.removeScriptVar(self, VAR_PREFIX + ".squad." + squadId + ".planet");
                }
                else
                {
                    utils.setScriptVar(self, squadCountVar, remaining);
                }
            }
        }

        return SCRIPT_CONTINUE;
    }

    private void initialize(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, VAR_PREFIX + ".activePlanets"))
        {
            setObjVar(self, VAR_PREFIX + ".activePlanets", DEFAULT_PLANETS);
        }

        String[] planets = getActivePlanets(self);
        if (planets != null)
        {
            for (int i = 0; i < planets.length; i++)
            {
                String capVar = VAR_PREFIX + ".cap." + planets[i];
                if (!hasObjVar(self, capVar))
                {
                    setObjVar(self, capVar, DEFAULT_PLANET_CAP);
                }
                String countVar = VAR_PREFIX + ".planetCount." + planets[i];
                if (!utils.hasScriptVar(self, countVar))
                {
                    utils.setScriptVar(self, countVar, 0);
                }
            }
        }

        if (!hasObjVar(self, VAR_PREFIX + ".minGroupSize"))
        {
            setObjVar(self, VAR_PREFIX + ".minGroupSize", DEFAULT_MIN_GROUP_SIZE);
        }
        if (!hasObjVar(self, VAR_PREFIX + ".maxGroupSize"))
        {
            setObjVar(self, VAR_PREFIX + ".maxGroupSize", DEFAULT_MAX_GROUP_SIZE);
        }
        if (!hasObjVar(self, VAR_PREFIX + ".minTick"))
        {
            setObjVar(self, VAR_PREFIX + ".minTick", DEFAULT_MIN_TICK);
        }
        if (!hasObjVar(self, VAR_PREFIX + ".maxTick"))
        {
            setObjVar(self, VAR_PREFIX + ".maxTick", DEFAULT_MAX_TICK);
        }

        applyBehaviorTuning(self);

        if (isEnabled())
        {
            messageTo(self, MSG_TICK, null, 10.0f, false);
        }
    }

    private boolean isEnabled() throws InterruptedException
    {
        String configValue = getConfigSetting("GameServer", CONFIG_TOGGLE);
        if (configValue == null)
        {
            return false;
        }
        return configValue.equalsIgnoreCase("true") || configValue.equalsIgnoreCase("on") || configValue.equals("1");
    }

    private void scheduleNextTick(obj_id self) throws InterruptedException
    {
        float minTick = getFloatObjVar(self, VAR_PREFIX + ".minTick");
        float maxTick = getFloatObjVar(self, VAR_PREFIX + ".maxTick");
        if (maxTick < minTick)
        {
            maxTick = minTick;
        }
        messageTo(self, MSG_TICK, null, rand(minTick, maxTick), false);
    }

    private String[] getActivePlanets(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, VAR_PREFIX + ".activePlanets"))
        {
            return null;
        }
        return getStringArrayObjVar(self, VAR_PREFIX + ".activePlanets");
    }

    private int getPlanetCap(obj_id self, String planet) throws InterruptedException
    {
        String capVar = VAR_PREFIX + ".cap." + planet;
        if (hasObjVar(self, capVar))
        {
            return getIntObjVar(self, capVar);
        }
        return DEFAULT_PLANET_CAP;
    }

    private int getPlanetSpawnCount(obj_id self, String planet) throws InterruptedException
    {
        String countVar = VAR_PREFIX + ".planetCount." + planet;
        if (utils.hasScriptVar(self, countVar))
        {
            return utils.getIntScriptVar(self, countVar);
        }
        return 0;
    }

    private void incrementPlanetSpawnCount(obj_id self, String planet) throws InterruptedException
    {
        String countVar = VAR_PREFIX + ".planetCount." + planet;
        int count = getPlanetSpawnCount(self, planet);
        utils.setScriptVar(self, countVar, count + 1);
    }

    private void decrementPlanetSpawnCount(obj_id self, String planet) throws InterruptedException
    {
        String countVar = VAR_PREFIX + ".planetCount." + planet;
        int count = getPlanetSpawnCount(self, planet);
        count--;
        if (count < 0)
        {
            count = 0;
        }
        utils.setScriptVar(self, countVar, count);
    }

    private int getMinGroupSize(obj_id self, String planet) throws InterruptedException
    {
        String planetVar = VAR_PREFIX + ".group.min." + planet;
        if (hasObjVar(self, planetVar))
        {
            return Math.max(1, getIntObjVar(self, planetVar));
        }
        return Math.max(1, getIntObjVar(self, VAR_PREFIX + ".minGroupSize"));
    }

    private int getMaxGroupSize(obj_id self, String planet) throws InterruptedException
    {
        int min = getMinGroupSize(self, planet);
        String planetVar = VAR_PREFIX + ".group.max." + planet;
        int max = hasObjVar(self, planetVar) ? getIntObjVar(self, planetVar) : getIntObjVar(self, VAR_PREFIX + ".maxGroupSize");
        if (max < min)
        {
            max = min;
        }
        return max;
    }

    private obj_id pickPlayerOnPlanet(String planet) throws InterruptedException
    {
        location center = new location(0.0f, 0.0f, 0.0f, planet);
        obj_id[] players = getAllPlayers(center, PLAYER_SCAN_RADIUS);
        if (players == null || players.length == 0)
        {
            return null;
        }

        int safety = 0;
        while (safety < 25)
        {
            obj_id candidate = players[rand(0, players.length - 1)];
            safety++;
            if (!isIdValid(candidate) || isDead(candidate) || isIncapacitated(candidate) || !isPlayerConnected(candidate) || isInWorldCell(candidate))
            {
                continue;
            }
            return candidate;
        }

        return null;
    }

    private location getSpawnLocationNearPlayer(obj_id player) throws InterruptedException
    {
        location playerLocation = getLocation(player);
        if (!isValidLocation(playerLocation))
        {
            return null;
        }

        int retries = Math.max(SPAWN_POINT_PICK_RETRIES, FALLBACK_SPAWN_PICK_RETRIES);
        for (int i = 0; i < retries; i++)
        {
            location candidate = locations.getGoodLocationAroundLocation(playerLocation, 4.0f, 4.0f, SPAWN_MAX_DISTANCE, SPAWN_MAX_DISTANCE, false, true);
            if (!isValidLocation(candidate))
            {
                continue;
            }
            if (getDistance(playerLocation, candidate) < SPAWN_MIN_DISTANCE)
            {
                continue;
            }
            if (!isSpawnPointLocationValid(candidate, player))
            {
                continue;
            }
            return candidate;
        }

        return null;
    }

    private spawn_point_pick pickSpawnPointForPlanet(String planet, obj_id player) throws InterruptedException
    {
        if (!dataTableOpen(SPAWN_POINT_TABLE))
        {
            return null;
        }

        int[] rows = getSpawnPointRowsForPlanet(planet);
        if (rows == null || rows.length == 0)
        {
            return null;
        }

        int retries = Math.min(SPAWN_POINT_PICK_RETRIES, Math.max(1, rows.length));
        for (int i = 0; i < retries; i++)
        {
            int row = pickWeightedSpawnPointRow(rows);
            if (row < 0)
            {
                return null;
            }

            spawn_point_pick candidate = buildSpawnPointLocation(planet, row);
            if (candidate == null)
            {
                continue;
            }
            if (!isSpawnPointLocationValid(candidate.loc, player))
            {
                continue;
            }

            return candidate;
        }

        return null;
    }

    private int[] getSpawnPointRowsForPlanet(String planet) throws InterruptedException
    {
        int rows = dataTableGetNumRows(SPAWN_POINT_TABLE);
        int count = 0;
        for (int i = 0; i < rows; i++)
        {
            if (planet.equals(dataTableGetString(SPAWN_POINT_TABLE, i, "planet")))
            {
                count++;
            }
        }

        if (count < 1)
        {
            return null;
        }

        int[] filteredRows = new int[count];
        int index = 0;
        for (int i = 0; i < rows; i++)
        {
            if (planet.equals(dataTableGetString(SPAWN_POINT_TABLE, i, "planet")))
            {
                filteredRows[index] = i;
                index++;
            }
        }

        return filteredRows;
    }

    private int pickWeightedSpawnPointRow(int[] rows) throws InterruptedException
    {
        if (rows == null || rows.length == 0)
        {
            return -1;
        }

        int totalWeight = 0;
        for (int i = 0; i < rows.length; i++)
        {
            int weight = dataTableGetInt(SPAWN_POINT_TABLE, rows[i], "weight");
            totalWeight += Math.max(1, weight);
        }

        if (totalWeight <= 0)
        {
            return rows[rand(0, rows.length - 1)];
        }

        int roll = rand(1, totalWeight);
        int running = 0;
        for (int i = 0; i < rows.length; i++)
        {
            int weight = dataTableGetInt(SPAWN_POINT_TABLE, rows[i], "weight");
            running += Math.max(1, weight);
            if (roll <= running)
            {
                return rows[i];
            }
        }

        return rows[rand(0, rows.length - 1)];
    }

    private spawn_point_pick buildSpawnPointLocation(String planet, int row) throws InterruptedException
    {
        float x = dataTableGetFloat(SPAWN_POINT_TABLE, row, "x");
        float z = dataTableGetFloat(SPAWN_POINT_TABLE, row, "z");
        float y = dataTableGetFloat(SPAWN_POINT_TABLE, row, "y");

        spawn_point_pick pick = new spawn_point_pick();
        pick.loc = new location(x, y, z, planet);
        pick.loc.y = getElevation(pick.loc);
        pick.heading = dataTableGetFloat(SPAWN_POINT_TABLE, row, "heading");
        pick.hasHeading = true;
        return pick;
    }

    private boolean isSpawnPointLocationValid(location candidate, obj_id player) throws InterruptedException
    {
        if (!isValidLocation(candidate))
        {
            return false;
        }
        if (candidate.cell != null && isIdValid(candidate.cell))
        {
            return false;
        }
        if (isBelowWater(candidate))
        {
            return false;
        }

        region[] regionList = getRegionsAtPoint(candidate);
        if (regionList != null)
        {
            for (int i = 0; i < regionList.length; i++)
            {
                region r = regionList[i];
                if (r != null && r.getGeographicalType() == script.library.regions.GEO_CITY)
                {
                    return false;
                }
            }
        }

        location navTest = locations.getGoodLocationAroundLocation(candidate, 2.0f, 2.0f, SPAWN_POINT_NAV_CHECK_RADIUS, SPAWN_POINT_NAV_CHECK_RADIUS, false, true);
        if (!isValidLocation(navTest) || getDistance(candidate, navTest) > SPAWN_POINT_NAV_CHECK_RADIUS)
        {
            return false;
        }

        obj_id[] nearbyPlayers = getAllPlayers(candidate, SPAWN_POINT_PROXIMITY_BLOCK);
        if (nearbyPlayers != null)
        {
            for (int i = 0; i < nearbyPlayers.length; i++)
            {
                obj_id nearby = nearbyPlayers[i];
                if (!isIdValid(nearby) || isDead(nearby) || isIncapacitated(nearby) || !isPlayerConnected(nearby))
                {
                    continue;
                }
                if (!isInWorldCell(nearby))
                {
                    return false;
                }
            }
        }

        if (isIdValid(player))
        {
            location playerLoc = getLocation(player);
            if (!isValidLocation(playerLoc) || getDistance(playerLoc, candidate) < SPAWN_MIN_DISTANCE)
            {
                return false;
            }
        }

        return true;
    }

    private int spawnSquad(obj_id self, String planet, location center, String squadId, int requestedSize, Float centerHeading) throws InterruptedException
    {
        int capRemaining = getPlanetCap(self, planet) - getPlanetSpawnCount(self, planet);
        int groupSize = Math.min(requestedSize, capRemaining);
        if (groupSize < 1)
        {
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < groupSize; i++)
        {
            String template = pickWeightedTemplate(planet);
            if (template == null || template.length() < 1)
            {
                continue;
            }

            location spawnLocation;
            if (i == 0)
            {
                spawnLocation = center;
            }
            else
            {
                spawnLocation = locations.getGoodLocationAroundLocation(center, 2.0f, 2.0f, 24.0f, 24.0f, false, true);
            }
            if (!isValidLocation(spawnLocation))
            {
                continue;
            }

            obj_id npc;
            if (template.indexOf(".iff") > -1)
            {
                npc = createObject(template, spawnLocation);
            }
            else
            {
                npc = create.object(template, spawnLocation);
            }

            if (!isIdValid(npc))
            {
                continue;
            }

            setObjVar(npc, VAR_PREFIX + ".master", self);
            setObjVar(npc, VAR_PREFIX + ".planet", planet);
            setObjVar(npc, VAR_PREFIX + ".squadId", squadId);
            setObjVar(npc, "jediHunter.squad.anchor", center);
            applyNpcBehavior(self, npc, planet);
            attachScript(npc, "systems.spawning.jedi_hunter_spawn_tracker");
            attachScript(npc, "ai.jedi_hunter_squad");
            if (centerHeading != null)
            {
                setYaw(npc, centerHeading);
            }

            incrementPlanetSpawnCount(self, planet);
            spawned++;
        }

        return spawned;
    }

    private String pickWeightedTemplate(String planet) throws InterruptedException
    {
        String table = TABLE_ROOT + planet + ".tab";
        if (!dataTableOpen(table))
        {
            table = DEFAULT_TABLE;
            if (!dataTableOpen(table))
            {
                return null;
            }
        }

        int rows = dataTableGetNumRows(table);
        if (rows <= 0)
        {
            return null;
        }

        int totalWeight = 0;
        for (int i = 0; i < rows; i++)
        {
            int weight = dataTableGetInt(table, i, "weight");
            if (weight > 0)
            {
                totalWeight += weight;
            }
        }

        if (totalWeight < 1)
        {
            return null;
        }

        int roll = rand(1, totalWeight);
        int running = 0;
        for (int i = 0; i < rows; i++)
        {
            int weight = dataTableGetInt(table, i, "weight");
            if (weight <= 0)
            {
                continue;
            }
            running += weight;
            if (roll <= running)
            {
                return dataTableGetString(table, i, "template");
            }
        }

        return null;
    }

    private int pickWeightedGroupSize(String planet, int minGroup, int maxGroup) throws InterruptedException
    {
        if (!dataTableOpen(GROUP_TABLE))
        {
            return rand(minGroup, maxGroup);
        }

        int rows = dataTableGetNumRows(GROUP_TABLE);
        int totalWeight = 0;
        for (int i = 0; i < rows; i++)
        {
            String rowPlanet = dataTableGetString(GROUP_TABLE, i, "planet");
            if (!planet.equals(rowPlanet) && !"default".equals(rowPlanet))
            {
                continue;
            }
            int size = dataTableGetInt(GROUP_TABLE, i, "group_size");
            int weight = dataTableGetInt(GROUP_TABLE, i, "weight");
            if (size < minGroup || size > maxGroup || weight <= 0)
            {
                continue;
            }
            totalWeight += weight;
        }

        if (totalWeight < 1)
        {
            return rand(minGroup, maxGroup);
        }

        int roll = rand(1, totalWeight);
        int running = 0;
        for (int i = 0; i < rows; i++)
        {
            String rowPlanet = dataTableGetString(GROUP_TABLE, i, "planet");
            if (!planet.equals(rowPlanet) && !"default".equals(rowPlanet))
            {
                continue;
            }
            int size = dataTableGetInt(GROUP_TABLE, i, "group_size");
            int weight = dataTableGetInt(GROUP_TABLE, i, "weight");
            if (size < minGroup || size > maxGroup || weight <= 0)
            {
                continue;
            }
            running += weight;
            if (roll <= running)
            {
                return size;
            }
        }

        return rand(minGroup, maxGroup);
    }

    private void applyBehaviorTuning(obj_id self) throws InterruptedException
    {
        if (!dataTableOpen(BEHAVIOR_TABLE))
        {
            return;
        }

        int rows = dataTableGetNumRows(BEHAVIOR_TABLE);
        for (int i = 0; i < rows; i++)
        {
            String planet = dataTableGetString(BEHAVIOR_TABLE, i, "planet");
            if (planet == null || planet.length() < 1)
            {
                continue;
            }

            int minGroup = dataTableGetInt(BEHAVIOR_TABLE, i, "min_group");
            int maxGroup = dataTableGetInt(BEHAVIOR_TABLE, i, "max_group");
            if ("default".equals(planet))
            {
                if (minGroup > 0)
                {
                    setObjVar(self, VAR_PREFIX + ".minGroupSize", minGroup);
                }
                if (maxGroup > 0)
                {
                    setObjVar(self, VAR_PREFIX + ".maxGroupSize", maxGroup);
                }
                continue;
            }

            if (minGroup > 0)
            {
                setObjVar(self, VAR_PREFIX + ".group.min." + planet, minGroup);
            }
            if (maxGroup > 0)
            {
                setObjVar(self, VAR_PREFIX + ".group.max." + planet, maxGroup);
            }
        }
    }

    private void applyNpcBehavior(obj_id self, obj_id npc, String planet) throws InterruptedException
    {
        String rowPlanet = findBehaviorRowPlanet(planet);
        if (rowPlanet == null)
        {
            return;
        }

        int row = findBehaviorRow(rowPlanet);
        if (row < 0)
        {
            return;
        }

        setObjVar(npc, jedi_hunter.OBJVAR_SCAN_RADIUS, dataTableGetFloat(BEHAVIOR_TABLE, row, "scan_radius"));
        setObjVar(npc, jedi_hunter.OBJVAR_FINE_MAX_LEVEL, dataTableGetInt(BEHAVIOR_TABLE, row, "fine_threshold"));
        setObjVar(npc, jedi_hunter.OBJVAR_TAUNT_COOLDOWN, dataTableGetInt(BEHAVIOR_TABLE, row, "taunt_cooldown"));
        setObjVar(npc, jedi_hunter.OBJVAR_RESPAWN_DELAY, dataTableGetInt(BEHAVIOR_TABLE, row, "respawn_delay"));
        setObjVar(npc, jedi_hunter.OBJVAR_BARK_NAMESPACE, dataTableGetString(BEHAVIOR_TABLE, row, "bark_namespace"));
    }

    private String findBehaviorRowPlanet(String planet) throws InterruptedException
    {
        if (!dataTableOpen(BEHAVIOR_TABLE))
        {
            return null;
        }
        int rows = dataTableGetNumRows(BEHAVIOR_TABLE);
        for (int i = 0; i < rows; i++)
        {
            if (planet.equals(dataTableGetString(BEHAVIOR_TABLE, i, "planet")))
            {
                return planet;
            }
        }
        for (int i = 0; i < rows; i++)
        {
            if ("default".equals(dataTableGetString(BEHAVIOR_TABLE, i, "planet")))
            {
                return "default";
            }
        }
        return null;
    }

    private int findBehaviorRow(String planet) throws InterruptedException
    {
        int rows = dataTableGetNumRows(BEHAVIOR_TABLE);
        for (int i = 0; i < rows; i++)
        {
            if (planet.equals(dataTableGetString(BEHAVIOR_TABLE, i, "planet")))
            {
                return i;
            }
        }
        return -1;
    }
}
