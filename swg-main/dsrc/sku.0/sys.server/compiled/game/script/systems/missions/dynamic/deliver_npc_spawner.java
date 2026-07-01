package script.systems.missions.dynamic;

import script.library.create;
import script.library.locations;
import script.library.ai_lib;
import script.location;
import script.obj_id;
import script.dictionary;

public class deliver_npc_spawner extends script.base_script {

    private static final String[] NPC_TYPES = {
        "commoner", "merchant", "citizen_female", "citizen_male", "trader"
    };

    public deliver_npc_spawner() {
    }

    public int OnAttach(obj_id self) throws InterruptedException {
        if (shouldSpawnNpc(self)) {
            messageTo(self, "spawnNpc", null, 1.0f, false);
        }
        return SCRIPT_CONTINUE;
    }

    public int spawnNpc(obj_id self, dictionary params) throws InterruptedException {
        location here = getLocation(self);
        location spawnLoc = getRandomNearbyLocation(here, 5.0f); // 5 meter radius
        String npcType = NPC_TYPES[rand(0, NPC_TYPES.length - 1)];

        obj_id npc = create.object(npcType, spawnLoc);
                ai_lib.setDefaultCalmBehavior(npc, ai_lib.BEHAVIOR_WANDER);
        setInvulnerable(npc, true);
if (isIdValid(npc)) {
            attachScript(npc, "systems.missions.dynamic.mission_deliver_npc");
            ai_lib.setDefaultCalmBehavior(npc, ai_lib.BEHAVIOR_WANDER); // Enable roaming AI
            setObjVar(npc, "wander_radius", 10.0f); // Used by some wander implementations
        }
        return SCRIPT_CONTINUE;
    }

    private boolean shouldSpawnNpc(obj_id self) throws InterruptedException {
        String empiredayRunning = getConfigSetting("GameServer", "empireday_ceremony");
        location here = getLocation(self);
        String city = locations.getCityName(here);
        if (city == null) {
            city = locations.getGuardSpawnerRegionName(here);
        }
        return !(empiredayRunning != null &&
                 (empiredayRunning.equals("true") || empiredayRunning.equals("1")) &&
                 "theed".equalsIgnoreCase(city));
    }

    private location getRandomNearbyLocation(location origin, float radius) {
        float dx = rand(-radius * 100, radius * 100) / 100.0f;
        float dz = rand(-radius * 100, radius * 100) / 100.0f;
        return new location(origin.x + dx, origin.y, origin.z + dz, origin.area, origin.cell);
    }
}
