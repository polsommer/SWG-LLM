package script.space_mining;

import script.library.features;
import script.library.space_combat;
import script.library.space_utils;
import script.library.utils;
import script.location;
import script.obj_id;
import script.transform;
import script.vector;

public class mining_asteroid_dynamic extends script.base_script {
    public static final int MAX_RESOURCE = 1000000;

    public mining_asteroid_dynamic() {}

    
    public int OnAttach(obj_id self) throws InterruptedException {
        setHitpoints(self, 50);
        LOG("space_mining", "Asteroid attached with hitpoints: 50");
        return SCRIPT_CONTINUE;
    }

    
    public int OnShipInternalDamageOverTimeRemoved(obj_id self, int chassisSlot, float damageRate, float damageThreshold) throws InterruptedException {
        obj_id pilot = getPilotId(self);
        if (pilot != null) {
            LOG("space_mining", "Internal Damage Over Time removed for Pilot: " + pilot);
        }
        return SCRIPT_CONTINUE;
    }

    
    public int OnShipWasHit(obj_id self, obj_id attacker, int weaponIndex, boolean isMissile, int missileType, int chassisSlot, boolean isPlayerAutoTurret, float hitLocationX_o, float hitLocationY_o, float hitLocationZ_o) throws InterruptedException {
        obj_id attackingPilot = space_utils.getPilotForRealsies(attacker);
        if (!isIdValid(attackingPilot) || !features.hasEpisode3Expansion(attackingPilot)) {
            LOG("space_mining", "Hit ignored: invalid pilot or expansion requirement not met.");
            return SCRIPT_CONTINUE;
        }

        LOG("space_mining", "Asteroid hit by attacker: " + attacker);

        String strAsteroidTable = "datatables/space_mining/mining_asteroids.iff";
        String strAsteroidType = getStringObjVar(self, "strAsteroidType");
        int intWeaponSlot = weaponIndex + ship_chassis_slot_type.SCST_weapon_0;

        location attackingLocation = getLocation(attacker);
        vector attackingPosition_w = new vector(attackingLocation.x, attackingLocation.y, attackingLocation.z);
        transform selfTransform = getTransform_o2w(self);
        vector attackingLocation_o = selfTransform.rotateTranslate_p2l(attackingPosition_w);
        int weaponCrc = getShipComponentCrc(attacker, weaponIndex + ship_chassis_slot_type.SCST_weapon_first);

        // Skip if the weapon is a tractor
        if (getShipComponentDescriptorWeaponIsTractor(weaponCrc)) {
            return SCRIPT_CONTINUE;
        }

        // Process the hit if the weapon is a mining type
        if (getShipComponentDescriptorWeaponIsMining(weaponCrc)) {
            float fltDamage = space_combat.getShipWeaponDamage(attacker, self, intWeaponSlot) / 100;
            int oldHitpoints = getHitpoints(self);
            setHitpoints(self, oldHitpoints - (int) fltDamage);

            if (getHitpoints(self) <= 0) {
                // Asteroid destroyed, spawn fragments
                spawnAsteroidFragments(self, strAsteroidTable, strAsteroidType);
            } else {
                vector hitLocation_o = new vector(hitLocationX_o, hitLocationY_o, hitLocationZ_o);
                notifyShipHit(self, attackingLocation_o, hitLocation_o, ship_hit_type.HT_chassis, 0.5f, 1.0f);
            }
        }
        return SCRIPT_CONTINUE;
    }

    private void spawnAsteroidFragments(obj_id self, String strAsteroidTable, String strAsteroidType) throws InterruptedException {
        location selfLocation = getLocation(self);
        int choice = rand(5, 6);
        String template = dataTableGetString(strAsteroidTable, strAsteroidType, choice);
        int chunks = rand(2, 7);

        LOG("space_mining", "Asteroid destroyed, spawning " + chunks + " fragments of type: " + strAsteroidType);

        for (int i = 0; i < chunks; i++) {
            obj_id spawnDynamicAsteroid = createObject(template, selfLocation);
            setObjVar(spawnDynamicAsteroid, "strAsteroidType", strAsteroidType);

            vector currentVelocity_w = getDynamicMiningAsteroidVelocity(self);
            vector spawnDirection_w = currentVelocity_w.cross(vector.randomUnit()).normalize().multiply(10.0f);
            currentVelocity_w = currentVelocity_w.add(spawnDirection_w);
            setDynamicMiningAsteroidVelocity(spawnDynamicAsteroid, currentVelocity_w);

            // Adjust parent asteroid velocity
            currentVelocity_w = currentVelocity_w.subtract(spawnDirection_w).subtract(spawnDirection_w);
            setDynamicMiningAsteroidVelocity(self, currentVelocity_w);
        }

        obj_id parentRoid = getObjIdObjVar(self, "objParentAsteroid");
        messageTo(parentRoid, "decrementCount", null, 0, false);
        handleShipDestruction(self, 1.0f);
    }

    public void giveResourceReward(obj_id objAsteroid, obj_id objAttacker, int intAmount) throws InterruptedException {
        String strAsteroidType = getStringObjVar(objAsteroid, "strAsteroidType");
        obj_id objPilot = space_utils.getPilotForRealsies(objAttacker);
        obj_id objContainer = space_utils.isShipWithInterior(objAttacker) ? getObjIdObjVar(objAttacker, "objLootBox") : utils.getInventoryContainer(objPilot);

        if (!isIdValid(objContainer)) {
            LOG("space_mining", "Error: No valid loot container found for resources.");
            return;
        }

        String strResourceType = getResourceType(strAsteroidType);
        obj_id[] objResourceIds = getResourceTypes(strResourceType);
        obj_id objResourceId = (objResourceIds != null && objResourceIds.length > 0) ? objResourceIds[rand(0, objResourceIds.length - 1)] : null;

        if (!isIdValid(objResourceId)) {
            sendSystemMessageTestingOnly(objPilot, "No resources available!");
            return;
        }

        obj_id objStack = getResourceStack(objContainer, objResourceId);
        if (isIdValid(objStack)) {
            int intCount = getResourceContainerQuantity(objStack) + intAmount;
            if (intCount > MAX_RESOURCE) {
                addResourceToContainer(objStack, objResourceId, MAX_RESOURCE - getResourceContainerQuantity(objStack), null);
                intAmount = intCount - MAX_RESOURCE;
                objStack = createResourceCrate(objResourceId, intAmount, objContainer);
            } else {
                addResourceToContainer(objStack, objResourceId, intAmount, null);
            }
        } else {
            objStack = createResourceCrate(objResourceId, intAmount, objContainer);
        }
    }

    public obj_id getResourceStack(obj_id objContainer, obj_id objResource) throws InterruptedException {
        if (!isIdValid(objContainer)) {
            return null;
        }

        obj_id[] objContents = getContents(objContainer);
        if (objContents == null) {
            return null;
        }

        for (obj_id objContent : objContents) {
            if (getResourceContainerResourceType(objContent) == objResource && getResourceContainerQuantity(objContent) < MAX_RESOURCE) {
                return objContent;
            }
        }
        return null;
    }

    public String getResourceType(String strAsteroidType) throws InterruptedException {
        switch (strAsteroidType) {
            case "iron": return "space_metal_iron";
            case "carbonaceous": return "space_metal_carbonaceous";
            case "silicaceous": return "space_metal_silicaceous";
            case "ice": return "space_metal_ice";
            case "obsidian": return "space_metal_obsidian";
            case "diamond": return "space_gem_diamond";
            case "crystal": return "space_gem_crystal";
            case "petrochem": return "space_chemical_petrochem";
            case "acid": return "space_chemical_acid";
            case "cyanomethanic": return "space_chemical_cyanomethanic";
            case "sulfuric": return "space_chemical_sulfuric";
            case "methane": return "space_gas_methane";
            case "organometallic": return "space_gas_organometallic";
            default: return null;
        }
    }
}

