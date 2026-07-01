package script.ai.imperial_presence;

import script.dictionary;
import script.library.ai_lib;
import script.library.create;
import script.location;
import script.obj_id;

import java.util.Vector;

public class bounty_hunt_director extends script.base_script
{
    public bounty_hunt_director()
    {
    }

    public static final String OBJVAR_PREFIX = "bountyHunterPresence";
    public static final String OBJVAR_SQUAD_IDS = OBJVAR_PREFIX + ".squadIds";
    public static final String OBJVAR_SQUAD_COUNTER = OBJVAR_PREFIX + ".squadCounter";
    public static final String OBJVAR_PLANETS = OBJVAR_PREFIX + ".planets";
    public static final String OBJVAR_LOOP_MIN = OBJVAR_PREFIX + ".loopDelayMin";
    public static final String OBJVAR_LOOP_MAX = OBJVAR_PREFIX + ".loopDelayMax";
    public static final String OBJVAR_COOLDOWN_MIN = OBJVAR_PREFIX + ".cooldownMin";
    public static final String OBJVAR_COOLDOWN_MAX = OBJVAR_PREFIX + ".cooldownMax";
    public static final String OBJVAR_MEMBER_BEHAVIOR_SCRIPT = OBJVAR_PREFIX + ".memberBehaviorScript";
    public static final String OBJVAR_GLOBAL_SPAWN_TABLE = OBJVAR_PREFIX + ".spawnTable";

    // Keep this list aligned with available imperial presence director spawn-point datatables.
    public static final String[] DEFAULT_PLANETS =
    {
        "tatooine",
        "naboo",
        "corellia",
        "talus",
        "rori",
        "lok",
        "dantooine",
        "dathomir",
        "endor",
        "yavin4"
    };

    public static final int DEFAULT_PLANET_CAP = 2;

    public static final String[] DEFAULT_LEADER_POOL =
    {
        "bounty_hunter",
        "bounty_hunter_female",
        "commando"
    };

    public static final String[] DEFAULT_SUPPORT_POOL =
    {
        "bounty_hunter",
        "bounty_hunter_female",
        "commando",
        "mercenary"
    };

    public int OnAttach(obj_id self) throws InterruptedException
    {
        messageTo(self, "startSpawning", null, rand(5, 15), false);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "startSpawning", null, rand(3, 10), false);
        return SCRIPT_CONTINUE;
    }

    public int startSpawning(obj_id self, dictionary params) throws InterruptedException
    {
        cleanSquadState(self);

        String[] planets = getConfiguredPlanets(self);
        if (planets != null && planets.length > 0)
        {
            String selectedPlanet = pickEligiblePlanet(self, planets);
            if (selectedPlanet != null)
            {
                spawnSquadOnPlanet(self, selectedPlanet);
            }
        }

        int minDelay = getObjVarInt(self, OBJVAR_LOOP_MIN, 45);
        int maxDelay = getObjVarInt(self, OBJVAR_LOOP_MAX, 120);
        if (maxDelay < minDelay)
        {
            maxDelay = minDelay;
        }
        messageTo(self, "startSpawning", null, rand(minDelay, maxDelay), false);
        return SCRIPT_CONTINUE;
    }

    private String pickEligiblePlanet(obj_id self, String[] planets) throws InterruptedException
    {
        int now = getGameTime();
        int[] shuffledIndexes = new int[planets.length];
        for (int i = 0; i < planets.length; i++)
        {
            shuffledIndexes[i] = i;
        }

        for (int i = shuffledIndexes.length - 1; i > 0; i--)
        {
            int swapIndex = rand(0, i);
            int temp = shuffledIndexes[i];
            shuffledIndexes[i] = shuffledIndexes[swapIndex];
            shuffledIndexes[swapIndex] = temp;
        }

        for (int i = 0; i < shuffledIndexes.length; i++)
        {
            String planet = planets[shuffledIndexes[i]];
            if (planet == null || planet.length() == 0)
            {
                continue;
            }
            int cap = getPlanetCap(self, planet);
            if (cap <= 0)
            {
                continue;
            }
            int active = getActiveSquadCountForPlanet(self, planet);
            if (active >= cap)
            {
                continue;
            }
            int nextAllowed = getObjVarInt(self, OBJVAR_PREFIX + ".planet." + planet + ".nextSpawnTime", 0);
            if (now < nextAllowed)
            {
                continue;
            }
            String table = getSpawnPointTable(self, planet);
            if (table == null || !dataTableOpen(table) || dataTableGetNumRows(table) <= 0)
            {
                continue;
            }
            return planet;
        }
        return null;
    }

    private void spawnSquadOnPlanet(obj_id self, String planet) throws InterruptedException
    {
        location spawnLoc = getSpawnLocation(self, planet);
        if (spawnLoc == null)
        {
            return;
        }

        String leaderTemplate = getRandomLeaderTemplate(self, planet);
        if (leaderTemplate == null || leaderTemplate.length() == 0)
        {
            return;
        }

        obj_id leader = create.object(leaderTemplate, spawnLoc);
        if (!isIdValid(leader))
        {
            return;
        }

        int squadId = getObjVarInt(self, OBJVAR_SQUAD_COUNTER, 0) + 1;
        setObjVar(self, OBJVAR_SQUAD_COUNTER, squadId);

        Vector members = new Vector();
        members.setSize(0);
        members.add(leader);

        setupSquadMember(self, leader, squadId, planet, true);

        int supportCount = rand(2, 4);
        for (int i = 0; i < supportCount; i++)
        {
            String supportTemplate = getRandomSupportTemplate(self, planet);
            if (supportTemplate == null || supportTemplate.length() == 0)
            {
                continue;
            }
            location supportLoc = new location(spawnLoc.x + rand(-10.0f, 10.0f), spawnLoc.y, spawnLoc.z + rand(-10.0f, 10.0f), planet, null);
            obj_id support = create.object(supportTemplate, supportLoc);
            if (!isIdValid(support))
            {
                continue;
            }
            setupSquadMember(self, support, squadId, planet, false);
            ai_lib.followInFormation(support, leader, ai_lib.FORMATION_COLUMN, i + 1);
            members.add(support);
        }

        setObjVar(self, OBJVAR_PREFIX + ".squads." + squadId + ".planet", planet);
        setObjVar(self, OBJVAR_PREFIX + ".squads." + squadId + ".members", members);

        Vector squadIds = getSquadIds(self);
        if (!squadIds.contains(squadId))
        {
            squadIds.add(squadId);
        }
        setObjVar(self, OBJVAR_SQUAD_IDS, squadIds);

        int cooldownMin = getObjVarInt(self, OBJVAR_COOLDOWN_MIN, 180);
        int cooldownMax = getObjVarInt(self, OBJVAR_COOLDOWN_MAX, 420);
        if (cooldownMax < cooldownMin)
        {
            cooldownMax = cooldownMin;
        }
        setObjVar(self, OBJVAR_PREFIX + ".planet." + planet + ".nextSpawnTime", getGameTime() + rand(cooldownMin, cooldownMax));
    }

    private location getSpawnLocation(obj_id self, String planet) throws InterruptedException
    {
        String table = getSpawnPointTable(self, planet);
        if (table == null || !dataTableOpen(table))
        {
            return null;
        }

        int rows = dataTableGetNumRows(table);
        if (rows <= 0)
        {
            return null;
        }

        int row = rand(0, rows - 1);
        if (dataTableHasColumn(table, "planet"))
        {
            boolean found = false;
            int attempts = rows;
            while (attempts > 0)
            {
                String rowPlanet = dataTableGetString(table, row, "planet");
                if (rowPlanet != null && rowPlanet.equals(planet))
                {
                    found = true;
                    break;
                }
                row = rand(0, rows - 1);
                attempts = attempts - 1;
            }
            if (!found)
            {
                return null;
            }
        }

        float x = dataTableGetFloat(table, row, dataTableHasColumn(table, "x") ? "x" : "locX");
        float y = dataTableGetFloat(table, row, dataTableHasColumn(table, "y") ? "y" : "locY");
        float z = dataTableGetFloat(table, row, dataTableHasColumn(table, "z") ? "z" : "locZ");

        x = x + rand(-25.0f, 25.0f);
        z = z + rand(-25.0f, 25.0f);

        return new location(x, y, z, planet, null);
    }

    private String getSpawnPointTable(obj_id self, String planet) throws InterruptedException
    {
        String byPlanetObjVar = OBJVAR_PREFIX + ".spawnTable." + planet;
        if (hasObjVar(self, byPlanetObjVar))
        {
            return getStringObjVar(self, byPlanetObjVar);
        }
        if (hasObjVar(self, OBJVAR_GLOBAL_SPAWN_TABLE))
        {
            return getStringObjVar(self, OBJVAR_GLOBAL_SPAWN_TABLE);
        }
        return "datatables/spawning/imperial_presence/director_spawn_points/" + planet + ".tab";
    }

    private void setupSquadMember(obj_id director, obj_id npc, int squadId, String planet, boolean leader) throws InterruptedException
    {
        String behaviorScript = hasObjVar(director, OBJVAR_MEMBER_BEHAVIOR_SCRIPT) ? getStringObjVar(director, OBJVAR_MEMBER_BEHAVIOR_SCRIPT) : "ai.jedi_hunter_patrol";
        if (behaviorScript != null && behaviorScript.length() > 0 && !hasScript(npc, behaviorScript))
        {
            attachScript(npc, behaviorScript);
        }
        setObjVar(npc, OBJVAR_PREFIX + ".director", director);
        setObjVar(npc, OBJVAR_PREFIX + ".squadId", squadId);
        setObjVar(npc, OBJVAR_PREFIX + ".planet", planet);
        if (leader)
        {
            setObjVar(npc, OBJVAR_PREFIX + ".leader", 1);
        }
    }

    private String getRandomLeaderTemplate(obj_id self, String planet) throws InterruptedException
    {
        String[] pool = getTemplatePool(self, planet, "leaders", DEFAULT_LEADER_POOL);
        if (pool == null || pool.length == 0)
        {
            return null;
        }
        return pool[rand(0, pool.length - 1)];
    }

    private String getRandomSupportTemplate(obj_id self, String planet) throws InterruptedException
    {
        String[] pool = getTemplatePool(self, planet, "supports", DEFAULT_SUPPORT_POOL);
        if (pool == null || pool.length == 0)
        {
            return null;
        }
        return pool[rand(0, pool.length - 1)];
    }

    private String[] getTemplatePool(obj_id self, String planet, String type, String[] defaults) throws InterruptedException
    {
        String planetPath = OBJVAR_PREFIX + "." + planet + "." + type;
        if (hasObjVar(self, planetPath))
        {
            String[] configured = getStringArrayObjVar(self, planetPath);
            if (configured != null && configured.length > 0)
            {
                return configured;
            }
        }
        String globalPath = OBJVAR_PREFIX + "." + type;
        if (hasObjVar(self, globalPath))
        {
            String[] configured = getStringArrayObjVar(self, globalPath);
            if (configured != null && configured.length > 0)
            {
                return configured;
            }
        }
        return defaults;
    }

    private int getPlanetCap(obj_id self, String planet) throws InterruptedException
    {
        String capObjVar = OBJVAR_PREFIX + ".planetCap." + planet;
        if (hasObjVar(self, capObjVar))
        {
            return getIntObjVar(self, capObjVar);
        }
        if (planet.equals("tatooine"))
        {
            return 4;
        }
        if (planet.equals("naboo"))
        {
            return 3;
        }
        if (planet.equals("corellia"))
        {
            return 3;
        }
        if (planet.equals("talus") || planet.equals("rori") || planet.equals("lok") || planet.equals("dantooine") || planet.equals("dathomir") || planet.equals("endor") || planet.equals("yavin4"))
        {
            return DEFAULT_PLANET_CAP;
        }
        return DEFAULT_PLANET_CAP;
    }

    private int getActiveSquadCountForPlanet(obj_id self, String planet) throws InterruptedException
    {
        Vector squadIds = getSquadIds(self);
        int count = 0;
        for (int i = 0; i < squadIds.size(); i++)
        {
            int squadId = ((Integer)squadIds.get(i));
            String planetObjVar = OBJVAR_PREFIX + ".squads." + squadId + ".planet";
            if (!hasObjVar(self, planetObjVar))
            {
                continue;
            }
            if (planet.equals(getStringObjVar(self, planetObjVar)))
            {
                count++;
            }
        }
        return count;
    }

    private Vector getSquadIds(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_SQUAD_IDS))
        {
            Vector squadIds = new Vector();
            squadIds.setSize(0);
            return squadIds;
        }
        return getResizeableIntArrayObjVar(self, OBJVAR_SQUAD_IDS);
    }

    private void cleanSquadState(obj_id self) throws InterruptedException
    {
        Vector squadIds = getSquadIds(self);
        if (squadIds == null || squadIds.size() <= 0)
        {
            return;
        }

        Vector survivors = new Vector();
        survivors.setSize(0);

        for (int i = 0; i < squadIds.size(); i++)
        {
            int squadId = ((Integer)squadIds.get(i));
            String memberPath = OBJVAR_PREFIX + ".squads." + squadId + ".members";
            if (!hasObjVar(self, memberPath))
            {
                removeObjVar(self, OBJVAR_PREFIX + ".squads." + squadId);
                continue;
            }

            Vector members = getResizeableObjIdArrayObjVar(self, memberPath);
            Vector aliveMembers = new Vector();
            aliveMembers.setSize(0);

            for (int j = 0; j < members.size(); j++)
            {
                obj_id member = ((obj_id)members.get(j));
                if (!isIdValid(member) || !exists(member) || isDead(member) || isIncapacitated(member))
                {
                    continue;
                }
                aliveMembers.add(member);
            }

            if (aliveMembers.size() > 0)
            {
                setObjVar(self, memberPath, aliveMembers);
                survivors.add(squadId);
            }
            else
            {
                removeObjVar(self, OBJVAR_PREFIX + ".squads." + squadId);
            }
        }

        if (survivors.size() > 0)
        {
            setObjVar(self, OBJVAR_SQUAD_IDS, survivors);
        }
        else
        {
            removeObjVar(self, OBJVAR_SQUAD_IDS);
        }
    }

    private int getObjVarInt(obj_id self, String objVar, int defaultValue) throws InterruptedException
    {
        if (!hasObjVar(self, objVar))
        {
            return defaultValue;
        }
        return getIntObjVar(self, objVar);
    }

    private String[] getConfiguredPlanets(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, OBJVAR_PLANETS))
        {
            String[] planets = getStringArrayObjVar(self, OBJVAR_PLANETS);
            if (planets != null && planets.length > 0)
            {
                return planets;
            }
        }
        return DEFAULT_PLANETS;
    }
}
