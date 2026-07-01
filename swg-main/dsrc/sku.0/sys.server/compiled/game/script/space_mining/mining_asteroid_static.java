package script.space_mining;

import script.*;
import script.library.*;

public class mining_asteroid_static extends script.base_script {
    public mining_asteroid_static() {}

    
    public int OnAttach(obj_id self) throws InterruptedException {
        setObjVar(self, "mining_asteroid.numShipsSpawned", 0);
        setObjVar(self, "dynamicCount", 0);
        LOG("space_mining", "Asteroid attached with initial dynamic count and ship spawn count set to 0.");
        return SCRIPT_CONTINUE;
    }

    
    public int OnShipInternalDamageOverTimeRemoved(obj_id self, int chassisSlot, float damageRate, float damageThreshold) throws InterruptedException {
        obj_id pilot = getPilotId(self);
        if (pilot != null) {
            LOG("space_mining", "Internal damage over time removed for pilot: " + pilot);
        }
        return SCRIPT_CONTINUE;
    }

    
    public int OnShipWasHit(obj_id self, obj_id attacker, int weaponIndex, boolean isMissile, int missileType, int chassisSlot, boolean isPlayerAutoTurret, float hitLocationX_o, float hitLocationY_o, float hitLocationZ_o) throws InterruptedException {
        String strAsteroidTable = "datatables/space_mining/mining_asteroids.iff";
        int intDangerLevel = getIntObjVar(self, "intDangerLevel");
        int intDangerPct = getIntObjVar(self, "intDangerPct");
        obj_id attackingPilot = space_utils.getPilotForRealsies(attacker);

        if (!isIdValid(attackingPilot) || !features.hasEpisode3Expansion(attackingPilot)) {
            LOG("space_mining", "Hit ignored: invalid pilot or expansion requirement not met.");
            return SCRIPT_CONTINUE;
        }

        int intWeaponSlot = weaponIndex + ship_chassis_slot_type.SCST_weapon_0;
        String strAsteroidType = getStringObjVar(self, "strAsteroidType");

        location attackingLocation = getLocation(attacker);
        vector attackingPosition_w = new vector(attackingLocation.x, attackingLocation.y, attackingLocation.z);
        transform selfTransform = getTransform_o2w(self);
        vector attackingLocation_o = selfTransform.rotateTranslate_p2l(attackingPosition_w);

        int weaponCrc = getShipComponentCrc(attacker, weaponIndex + ship_chassis_slot_type.SCST_weapon_first);
        if (getShipComponentDescriptorWeaponIsMining(weaponCrc)) {
            processMiningWeaponHit(self, attacker, intWeaponSlot, strAsteroidTable, strAsteroidType, intDangerLevel, intDangerPct, attackingLocation_o, hitLocationX_o, hitLocationY_o, hitLocationZ_o);
        }

        return SCRIPT_CONTINUE;
    }

    private void processMiningWeaponHit(obj_id self, obj_id attacker, int weaponSlot, String asteroidTable, String asteroidType, int dangerLevel, int dangerPct, vector attackingLocation_o, float hitLocationX_o, float hitLocationY_o, float hitLocationZ_o) throws InterruptedException {
        float fltDamage = space_combat.getShipWeaponDamage(attacker, self, weaponSlot) / 100;
        int oldHitpoints = getHitpoints(self);
        setHitpoints(self, oldHitpoints - (int)fltDamage);
        int newHitpoints = getHitpoints(self);

        if (newHitpoints <= 0) {
            handleShipDestruction(self, 1.0f);
        } else {
            vector hitLocation_o = new vector(hitLocationX_o, hitLocationY_o, hitLocationZ_o);
            notifyShipHit(self, attackingLocation_o, hitLocation_o, ship_hit_type.HT_chassis, 0.5f, 1.0f);

            int newDamageBracket = newHitpoints / 50;
            int oldDamageBracket = oldHitpoints / 50;
            int damageBracketDifference = oldDamageBracket - newDamageBracket;
            vector direction_o = attackingLocation_o.approximateNormalize();
            location selfLocation = getLocation(self);

            for (int i = 0; i < damageBracketDifference; ++i) {
                spawnDynamicAsteroids(self, asteroidTable, asteroidType, dangerLevel, selfLocation, direction_o);
                spawnPirateAttack(attacker, self, dangerLevel, dangerPct);
            }
        }
    }

    private void spawnDynamicAsteroids(obj_id self, String asteroidTable, String asteroidType, int dangerLevel, location selfLocation, vector direction_o) throws InterruptedException {
        int dynamicCount = getIntObjVar(self, "dynamicCount");

        if (dynamicCount >= 3) {
            LOG("space_mining", "Maximum dynamic asteroid count reached.");
            return;
        }

        vector spawnDirection_o = new vector(direction_o.x * random.rand(), direction_o.y * random.rand(), direction_o.z * random.rand()).approximateNormalize();
        vector spawnLocation_o = spawnDirection_o.multiply(200.0f);
        vector spawnLocation_w = getTransform_o2w(self).rotateTranslate_l2p(spawnLocation_o);
        location spawnLoc = new location(spawnLocation_w.x, spawnLocation_w.y, spawnLocation_w.z);

        adjustSpawnVelocity(spawnDirection_o, dangerLevel);

        int choice = rand(3, 4);
        String template = dataTableGetString(asteroidTable, asteroidType, choice);

        obj_id spawnDynamicAsteroid = createObject(template, spawnLoc);
        setObjVar(self, "dynamicCount", dynamicCount + 1);

        if (!isIdValid(spawnDynamicAsteroid)) {
            LOG("space_mining", "Failed to create dynamic asteroid: " + template);
            return;
        }

        setObjVar(spawnDynamicAsteroid, "strAsteroidType", asteroidType);
        setObjVar(spawnDynamicAsteroid, "objParentAsteroid", self);
        setDynamicMiningAsteroidVelocity(spawnDynamicAsteroid, spawnDirection_o);
    }

    private void adjustSpawnVelocity(vector spawnDirection, int dangerLevel) {
        float baseVelocity;
        switch (dangerLevel) {
            case 0:
            case 1:
                baseVelocity = 20.0f;
                break;
            case 2:
            case 3:
                baseVelocity = 30.0f;
                break;
            case 4:
                baseVelocity = 40.0f;
                break;
            case 5:
                baseVelocity = 45.0f;
                break;
            default:
                baseVelocity = 40.0f;
                break;
        }
        spawnDirection.multiply(baseVelocity + (random.rand() * 20.0f));
    }

    private void spawnPirateAttack(obj_id attacker, obj_id self, int dangerLevel, int dangerPct) throws InterruptedException {
        int roll = rand(1, 100);
        if (roll > dangerPct || getIntObjVar(self, "mining_asteroid.numShipsSpawned") >= 16) {
            return;
        }

        int squad = ship_ai.squadCreateSquadId();
        String attackTable = "datatables/space_mining/mining_threat/threat_tier_" + dangerLevel + ".iff";
        int rows = dataTableGetNumRows(attackTable);
        int spawnGroup = rand(0, rows - 1);

        for (int j = 1; j < 9; j++) {
            if (getIntObjVar(self, "mining_asteroid.numShipsSpawned") >= 16) {
                return;
            }
            String spawn = dataTableGetString(attackTable, spawnGroup, j);
            if (spawn == null) {
                continue;
            }

            obj_id newship = createPirateShip(attacker, spawn, self);
            if (isIdValid(newship)) {
                setObjVar(self, "mining_asteroid.numShipsSpawned", getIntObjVar(self, "mining_asteroid.numShipsSpawned") + 1);
                ship_ai.unitSetSquadId(newship, squad);
                ship_ai.spaceAttack(newship, attacker);
            }
        }
        ship_ai.squadSetFormationRandom(squad);
    }

    private obj_id createPirateShip(obj_id attacker, String spawn, obj_id parentAsteroid) throws InterruptedException {
        transform playerLocation = getTransform_o2w(attacker);
        float dist = rand(700.0f, 800.0f);
        vector n = playerLocation.getLocalFrameK_p().normalize().multiply(dist);
        playerLocation = playerLocation.move_p(n).yaw_l(3.14f);

        vector vi = playerLocation.getLocalFrameI_p().normalize().multiply(rand(-150.0f, 150.0f));
        vector vj = playerLocation.getLocalFrameJ_p().normalize().multiply(rand(-150.0f, 150.0f));
        playerLocation = playerLocation.move_p(vi.add(vj));

        obj_id newship = space_create.createShipHyperspace(spawn, playerLocation);
        attachScript(newship, "space_mining.mining_pirate");
        setObjVar(newship, "space_mining.parentRoid", parentAsteroid);

        return newship;
    }

    
    public int OnDestroy(obj_id self) throws InterruptedException {
        if (hasObjVar(self, "objParent")) {
            space_content.notifySpawner(self);
        }
        LOG("space_mining", "Asteroid destroyed, notifying spawner if present.");
        return SCRIPT_CONTINUE;
    }

    public int handlePirateKilled(obj_id self, dictionary params) throws InterruptedException {
        int spawnCount = getIntObjVar(self, "mining_asteroid.numShipsSpawned");
        setObjVar(self, "mining_asteroid.numShipsSpawned", spawnCount - 1);
        LOG("space_mining", "Pirate killed, reducing spawned ship count. New count: " + (spawnCount - 1));
        return SCRIPT_CONTINUE;
    }

    public int decrementCount(obj_id self, dictionary params) throws InterruptedException {
        int count = getIntObjVar(self, "dynamicCount");
        setObjVar(self, "dynamicCount", count - 1);
        LOG("space_mining", "Dynamic asteroid count decremented. New count: " + (count - 1));
        return SCRIPT_CONTINUE;
    }
}

