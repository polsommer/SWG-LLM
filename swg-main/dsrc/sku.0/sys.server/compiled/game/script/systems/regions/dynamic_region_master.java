package script.systems.regions;

import script.dictionary;
import script.library.ai_lib;
import script.library.create;
import script.library.utils;
import script.location;
import script.obj_id;
import script.region;

import java.util.ArrayList;
import java.util.Random;

public class dynamic_region_master extends script.base_script {

    // Constants for object variables
    private static final String BIRTH = "dynamic_region.birth";
    private static final String PLANET = "dynamic_region.planet";
    private static final String NAME = "dynamic_region.name";
    private static final String DURATION = "dynamic_region.duration";
    private static final String MAX_DURATION = "dynamic_region.maxDuration";
    private static final String CREATURE_LIST = "dynamic_region.creatureList";
    private static final String SPAWN_INTERVAL = "dynamic_region.spawnInterval";
    private static final String MAX_CREATURES = "dynamic_region.maxCreatures";
    
    private static final int DEFAULT_SPAWN_INTERVAL = 300; // Default interval in seconds
    private static final int DEFAULT_MAX_CREATURES = 5;    // Default maximum creatures

    private Random random = new Random();

    public dynamic_region_master() {}


    public int OnInitialize(obj_id self) throws InterruptedException {
        if (!hasObjVar(self, BIRTH)) {
            int currentEpoch = getCalendarTime();
            setObjVar(self, BIRTH, currentEpoch);
        }
        messageTo(self, "heartbeat", null, 60.0f, false);  // Initial heartbeat message
        return SCRIPT_CONTINUE;
    }


    public int OnAttach(obj_id self) throws InterruptedException {
        int birth = getIntObjVarOrDefault(self, BIRTH, getCalendarTime());
        setObjVar(self, BIRTH, birth);
        messageTo(self, "heartbeat", null, 60.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int destroySelf(obj_id self, dictionary params) throws InterruptedException {
        int currentEpoch = getCalendarTime();
        int birth = getIntObjVarOrDefault(self, BIRTH, 0);
        int maxDuration = getIntObjVarOrDefault(self, MAX_DURATION, 0);
        obj_id[] creatures = getObjIdArrayObjVar(self, CREATURE_LIST);

        if (currentEpoch < (birth + maxDuration * 60) && creatures != null && creatures.length > 0) {
            for (obj_id creature : creatures) {
                if (isIdValid(creature) && ai_lib.isInCombat(creature)) {
                    messageTo(self, "destroySelf", null, 60.0f, false);
                    return SCRIPT_CONTINUE;
                }
            }
        }

        // Destroy creatures
        if (creatures != null) {
            for (obj_id creature : creatures) {
                if (isIdValid(creature)) {
                    destroyObject(creature);
                }
            }
        }

        // Remove the region
        String regionName = getStringObjVar(self, NAME);
        String regionPlanet = getStringObjVar(self, PLANET);
        region regionSelf = getRegion(regionPlanet, regionName);
        if (regionSelf != null) {
            deleteRegion(regionSelf);
        } else {
            LOG("DynamicRegionMaster", "Failed to delete region: " + regionName + " on " + regionPlanet + " does not exist.");
        }
        return SCRIPT_CONTINUE;
    }

    public int heartbeat(obj_id self, dictionary params) throws InterruptedException {
        int duration = getIntObjVarOrDefault(self, DURATION, 0);
        int birth = getIntObjVarOrDefault(self, BIRTH, 0);
        
        if (duration == 0 || birth == 0) {
            messageTo(self, "destroySelf", null, 0.0f, false);
            return SCRIPT_CONTINUE;
        }

        int currentEpoch = getCalendarTime();
        int timeLeft = (birth + duration * 60) - currentEpoch;

        if (timeLeft <= 0) {
            messageTo(self, "destroySelf", null, 0.0f, false);
        } else {
            float nextInterval = Math.min(60.0f, timeLeft);
            messageTo(self, "manageSpawns", null, nextInterval, false);
            messageTo(self, "heartbeat", null, nextInterval, false);
        }
        return SCRIPT_CONTINUE;
    }

    public int manageSpawns(obj_id self, dictionary params) throws InterruptedException {
        int maxCreatures = getIntObjVarOrDefault(self, MAX_CREATURES, DEFAULT_MAX_CREATURES);
        obj_id[] creatures = getObjIdArrayObjVar(self, CREATURE_LIST);
        int activeCreatureCount = 0;
        if (creatures != null) {
            ArrayList<obj_id> validCreatures = new ArrayList<>();
            for (obj_id creature : creatures) {
                if (isIdValid(creature) && exists(creature)) {
                    validCreatures.add(creature);
                }
            }
            activeCreatureCount = validCreatures.size();
            if (activeCreatureCount != creatures.length) {
                setObjVar(self, CREATURE_LIST, validCreatures.toArray(new obj_id[0]));
            }
        }

        if (activeCreatureCount < maxCreatures) {
            spawnNewCreature(self);
        }
        return SCRIPT_CONTINUE;
    }

private void spawnNewCreature(obj_id self) throws InterruptedException {
    String regionPlanet = getStringObjVar(self, PLANET);
    String spawnDatatable = getStringObjVar(self, "dynamic_region.spawnDatatable");
    if (spawnDatatable == null || spawnDatatable.isEmpty() || !dataTableOpen(spawnDatatable)) {
        LOG("DynamicSpawn", "Invalid spawn datatable: " + spawnDatatable);
        return;
    }
    if (regionPlanet == null || regionPlanet.length() <= 0) {
        location here = getLocation(self);
        if (here != null && here.area != null && here.area.length() > 0) {
            regionPlanet = here.area;
        }
    }
    if (regionPlanet == null || regionPlanet.length() <= 0) {
        LOG("DynamicSpawn", "Cannot spawn creature: no valid planet/scene on region controller " + self);
        return;
    }

    int numRows = dataTableGetNumRows(spawnDatatable);
    if (numRows <= 0) {
        LOG("DynamicSpawn", "Spawn datatable has no rows: " + spawnDatatable);
        return;
    }
    int randomRow = random.nextInt(numRows);
    String templateName = dataTableGetString(spawnDatatable, randomRow, 0);
    if (templateName == null || templateName.length() <= 0) {
        LOG("DynamicSpawn", "Spawn datatable row has invalid template at row " + randomRow + " in " + spawnDatatable);
        return;
    }
    float cx = dataTableGetFloat(spawnDatatable, randomRow, 1);
    float cy = dataTableGetFloat(spawnDatatable, randomRow, 2);
    float cz = dataTableGetFloat(spawnDatatable, randomRow, 3);
    location spawnLocation = new location(cx, cy, cz, regionPlanet);
    obj_id creature = create.object(templateName, spawnLocation);

    if (isIdValid(creature)) {
        addCreatureToList(self, creature);
        
        // Create a basic patrol path around the spawn location
        location[] patrolPath = {
            new location(cx + 5, cy, cz, regionPlanet),
            new location(cx, cy, cz + 5, regionPlanet),
            new location(cx - 5, cy, cz, regionPlanet),
            new location(cx, cy, cz - 5, regionPlanet)
        };
        
        // Set patrol path for the creature
        ai_lib.setPatrolPath(creature, patrolPath);
    } else {
        LOG("DynamicSpawn", "Failed to spawn creature from template: " + templateName);
    }
}

    private void addCreatureToList(obj_id self, obj_id creature) throws InterruptedException {
        ArrayList<obj_id> creatureList = new ArrayList<>();
        obj_id[] existingCreatures = getObjIdArrayObjVar(self, CREATURE_LIST);
        if (existingCreatures != null) {
            for (obj_id existingCreature : existingCreatures) {
                if (isIdValid(existingCreature)) {
                    creatureList.add(existingCreature);
                }
            }
        }
        creatureList.add(creature);
        setObjVar(self, CREATURE_LIST, creatureList.toArray(new obj_id[0]));
    }

    public int OnDynamicSpawnRegionCreated(obj_id self, obj_id regionObject, String spawnDatatable, float x, float y, float z) throws InterruptedException {
        setObjVar(self, "dynamic_region.spawnDatatable", spawnDatatable);
        LOG("DynamicSpawn", "Dynamic spawn region created with datatable: " + spawnDatatable);
        return SCRIPT_CONTINUE;
    }

    // Helper method to get integer object variable with default
    private int getIntObjVarOrDefault(obj_id obj, String varName, int defaultValue) {
        return hasObjVar(obj, varName) ? getIntObjVar(obj, varName) : defaultValue;
    }
}
