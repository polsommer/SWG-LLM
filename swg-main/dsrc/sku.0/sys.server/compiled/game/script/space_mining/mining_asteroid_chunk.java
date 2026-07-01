package script.space_mining;

import script.*;
import script.library.*;

public class mining_asteroid_chunk extends script.base_script {
    public mining_asteroid_chunk() {}

    private static final int MAX_RESOURCE = 1000000;
    private static final float FALLEENS_FIST_BUFF_MULTIPLIER = 1.5f;

    
    public int OnAttach(obj_id self) throws InterruptedException {
        setHitpoints(self, 25);
        setMaxHitpoints(self, 25);
        LOG("space_mining", "Asteroid attached with ID: " + self);
        return SCRIPT_CONTINUE;
    }

    
    public int OnShipWasHit(obj_id self, obj_id attacker, int weaponIndex, boolean isMissile, int missileType, int chassisSlot, boolean isPlayerAutoTurret, float hitLocationX_o, float hitLocationY_o, float hitLocationZ_o) throws InterruptedException {
        obj_id attackingPilot = space_utils.getPilotForRealsies(attacker);
        
        // Validate the attacking pilot and expansion requirements
        if (!isIdValid(attackingPilot)) {
            LOG("space_mining", "Hit not registered: Invalid attacking pilot ID for attacker: " + attacker);
            return SCRIPT_CONTINUE;
        }
        if (!features.hasEpisode3Expansion(attackingPilot)) {
            LOG("space_mining", "Hit not registered: Player lacks required expansion.");
            return SCRIPT_CONTINUE;
        }

        LOG("space_mining", "Asteroid hit by attacker: " + attacker + ", weaponIndex: " + weaponIndex + ", pilot: " + attackingPilot);

        // Determine if the weapon is a mining or tractor type
        int weaponCrc = getShipComponentCrc(attacker, weaponIndex + ship_chassis_slot_type.SCST_weapon_first);
        if (getShipComponentDescriptorWeaponIsTractor(weaponCrc)) {
            LOG("space_mining", "Detected tractor beam hit on asteroid.");
            handleTractorBeamHit(self, attacker, getAttackingPosition(attacker), getTransform_o2w(self));
        } else if (getShipComponentDescriptorWeaponIsMining(weaponCrc)) {
            LOG("space_mining", "Detected mining weapon hit on asteroid.");
            handleMiningWeaponHit(self, attacker, weaponIndex, new vector(hitLocationX_o, hitLocationY_o, hitLocationZ_o));
        } else {
            LOG("space_mining", "Hit not registered: Weapon is not mining or tractor type.");
        }
        
        return SCRIPT_CONTINUE;
    }

    private void handleTractorBeamHit(obj_id self, obj_id attacker, vector attackingPosition_w, transform selfTransform) throws InterruptedException {
        vector currentVelocity_w = getDynamicMiningAsteroidVelocity(self);
        vector directionToAttacker_w = attackingPosition_w.subtract(selfTransform.getPosition_p()).normalize().multiply(30.0f);
        currentVelocity_w = currentVelocity_w.add(directionToAttacker_w);
        setVelocityWithinLimit(self, currentVelocity_w, 55.0f);

        if (getDistance(getLocation(self), getLocation(attacker)) - getObjectCollisionRadius(attacker) < 100.0f) {
            if (shipAbsorbAsteroid(self, attacker, 40)) {
                LOG("space_mining", "Asteroid absorbed by tractor beam.");
                destroyObject(self);
                grantRareAsteroid(space_utils.getPilotForRealsies(attacker));
            } else {
                LOG("space_mining", "Tractor beam absorption failed: unable to absorb asteroid.");
            }
        } else {
            LOG("space_mining", "Tractor beam hit, but attacker is out of range for absorption.");
        }
    }

    private void handleMiningWeaponHit(obj_id self, obj_id attacker, int weaponSlot, vector hitLocation_o) throws InterruptedException {
        float fltDamage = space_combat.getShipWeaponDamage(attacker, self, weaponSlot + ship_chassis_slot_type.SCST_weapon_0) / 100;
        
        if (fltDamage <= 0) {
            LOG("space_mining", "Mining weapon hit registered with zero or negative damage, hit ignored.");
            space_utils.sendSystemMessageShip(attacker, new string_id("space_mining", "hit_failed"), true, true, true, true);
            return;
        }

        int newHitpoints = getHitpoints(self) - (int) fltDamage;
        setHitpoints(self, newHitpoints);

        if (newHitpoints <= 0) {
            LOG("space_mining", "Asteroid destroyed by mining weapon hit, absorbing resources.");
            if (shipAbsorbAsteroid(self, attacker, 20)) {
                grantRareAsteroid(space_utils.getPilotForRealsies(attacker));
                handleShipDestruction(self, 1.0f);
            } else {
                LOG("space_mining", "Failed to absorb asteroid resources after destruction.");
            }
        } else {
            LOG("space_mining", "Asteroid damaged but not destroyed, hit location: " + hitLocation_o);
            notifyShipHit(self, hitLocation_o, hitLocation_o, ship_hit_type.HT_chassis, 0.5f, 1.0f);
        }
    }

    public boolean shipAbsorbAsteroid(obj_id asteroid, obj_id attacker, int amount) throws InterruptedException {
        LOG("space_mining", "Attempting to absorb asteroid for attacker: " + attacker);

        if (!isShipSlotInstalled(attacker, ship_chassis_slot_type.SCST_cargo_hold)) {
            LOG("space_mining", "No cargo hold installed for attacker.");
            space_utils.sendSystemMessageShip(attacker, new string_id("space_mining", "no_hold"), true, true, true, true);
            return false;
        }

        obj_id attackingPilot = space_utils.getPilotForRealsies(attacker);
        String strAsteroidType = getStringObjVar(asteroid, "strAsteroidType");
        int contentsMax = getShipCargoHoldContentsMaximum(attacker);
        int contentsCurrent = getShipCargoHoldContentsCurrent(attacker);

        if (contentsMax <= 0 || contentsCurrent >= contentsMax) {
            LOG("space_mining", "Cargo hold is either full or has no space capacity.");
            space_utils.sendSystemMessageShip(attacker, new string_id("space_mining", "hold_full"), true, true, true, true);
            return false;
        }

        if (buff.hasBuff(attackingPilot, "tcg_series4_falleens_fist")) {
            amount *= FALLEENS_FIST_BUFF_MULTIPLIER;
            LOG("space_mining", "Falleen's Fist Buff: Granting 50% resource increase for Pilot: " + attackingPilot);
        }

        String resourceClassName = getResourceClassNameForAsteroid(strAsteroidType);
        int deltaAmount = modifyShipCargoHoldContent(attacker, resourceClassName, amount);
        
        if (deltaAmount > 0) {
            LOG("space_mining", "Absorbed resources: deltaAmount=" + deltaAmount);
            dictionary d = new dictionary();
            d.put("resourceAmt", deltaAmount);
            d.put("player", attackingPilot);
            d.put("resourceType", strAsteroidType);
            messageTo(attackingPilot, "handleAsteroidMined", d, 0, false);
            return true;
        } else {
            LOG("space_mining", "Failed to add resources: no resources absorbed.");
            return false;
        }
    }
    
    public obj_id grantRareAsteroid(obj_id player) throws InterruptedException {
        obj_id playerInv = utils.getInventoryContainer(player);
        String datatable = "datatables/space_mining/mining_rares.iff";
        int rows = dataTableGetNumRows(datatable);
        int row = rand(0, rows - 1);
        String template = dataTableGetString(datatable, row, 0);
        int rarity = dataTableGetInt(datatable, row, 1);
        int roll = rand(1, rarity);

        if (roll <= 1) {
            obj_id rare = createObject(template, playerInv, "");
            if (isIdValid(rare)) {
                sendSystemMessage(player, new string_id("space_mining", "gotrare"));
            } else {
                sendSystemMessage(player, new string_id("space_mining", "invfull"));
            }
            return rare;
        } else {
            sendSystemMessage(player, new string_id("space_mining", "no_rare"));
        }
        return null;
    }

    public String getResourceClassNameForAsteroid(String strAsteroidType) throws InterruptedException {
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

    private void setVelocityWithinLimit(obj_id self, vector currentVelocity_w, float maxVelocity) {
        if (currentVelocity_w.magnitude() > maxVelocity) {
            currentVelocity_w = currentVelocity_w.multiply(maxVelocity / currentVelocity_w.magnitude());
        }
        setDynamicMiningAsteroidVelocity(self, currentVelocity_w);
    }

    private vector getAttackingPosition(obj_id attacker) throws InterruptedException {
        location attackingLocation = getLocation(attacker);
        return new vector(attackingLocation.x, attackingLocation.y, attackingLocation.z);
    }
}

