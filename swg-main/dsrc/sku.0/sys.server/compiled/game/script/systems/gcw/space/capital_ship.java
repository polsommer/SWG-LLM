package script.systems.gcw.space;

import script.dictionary;
import script.library.space_utils;
import script.obj_id;

import java.util.Vector;

public class capital_ship extends script.space.combat.combat_space_base {

    private static final float DAMAGE_THRESHOLD_FOR_REINFORCEMENT = 0.25f;

    public int OnShipWasHit(obj_id self, obj_id attacker, int weaponIndex, boolean isMissile, int missileType, int intSlot, boolean fromPlayerAutoTurret, float hitLocationX_o, float hitLocationY_o, float hitLocationZ_o) throws InterruptedException {

        if (!space_utils.isPlayerControlledShip(attacker) || getShipFaction(attacker).equals(getShipFaction(self))) {
            return SCRIPT_CONTINUE;
        }

        if (!hasObjVar(self, "spawner")) return SCRIPT_CONTINUE;
        obj_id spawner = getObjIdObjVar(self, "spawner");
        if (!isValidId(spawner)) return SCRIPT_CONTINUE;

        String battleType = getStringObjVar(spawner, "battle_type");
        String battleId = getStringObjVar(spawner, "battle_id");
        boolean isGunship = getShipChassisType(attacker).startsWith("player_gunship");

        if (battleType.equals(battle_spawner.BATTLE_TYPE_PVP) && pvpGetType(attacker) != PVPTYPE_DECLARED) {
            return SCRIPT_CONTINUE;
        }

        obj_id[] players = space_utils.getAllPlayersInShip(attacker);
        String roleKey = isGunship ? "gunship" : space_utils.isPobType(attacker) ? "pob" : "participant";
        setObjVar(spawner, "space_gcw." + roleKey + ".participant." + battleId + "." + attacker, players);

        // Broadcast fun alert message
        broadcastShipAlert(self, attacker);

        // Check damage threshold and call reinforcements if needed
        float healthPercent = getShipHealthPercent(self);
        if (healthPercent < DAMAGE_THRESHOLD_FOR_REINFORCEMENT && !hasObjVar(self, "reinforcementCalled")) {
            messageTo(self, "spawnSupportShip", null, 5.0f, false);
            setObjVar(self, "reinforcementCalled", true);
            debugServerConsoleMsg(self, "[AI ALERT] Reinforcements summoned due to heavy damage!");
        }

        return SCRIPT_CONTINUE;
    }

    public int OnDestroy(obj_id self) throws InterruptedException {
        if (!hasObjVar(self, "spawner")) return SCRIPT_CONTINUE;

        obj_id spawner = getObjIdObjVar(self, "spawner");
        obj_id controller = getObjIdObjVar(spawner, "controller");
        if (isValidId(controller) && getIntObjVar(controller, "space_gcw." + spawner + ".active") == 1) {
            dictionary params = new dictionary();
            params.put("spawner", spawner);
            params.put("destroyedShip", self);
            params.put("losingFaction", getShipFaction(self));
            params.put("losingRole", getStringObjVar(self, "role"));
            params.put("supportCraft", getResizeableObjIdArrayObjVar(self, "supportCraft"));
            params.put("controller", controller);
            messageTo(spawner, "capitalShipDestroyed", params, 0.0f, false);
        }
        return SCRIPT_CONTINUE;
    }

    public int removeSupportShip(obj_id self, dictionary params) {
        obj_id destroyedShip = params.getObjId("destroyedShip");
        Vector spawnedShips = getResizeableObjIdArrayObjVar(self, "supportCraft");

        if (spawnedShips != null) {
            if (hasObjVar(destroyedShip, "ace_pilot")) {
                removeObjVar(self, "heroSpawned");
            }
            try {
                spawnedShips.remove(destroyedShip);
                setObjVar(self, "supportCraft", spawnedShips);
            } catch (Exception e) {
                LOG("space_gcw", "Error removing support ship: " + e.getMessage());
            }
        }

        messageTo(self, "spawnSupportShip", null, rand(15.0f, 45.0f), false);
        return SCRIPT_CONTINUE;
    }

    public int spawnSupportShip(obj_id self, dictionary params) throws InterruptedException {
        if (!isValidId(self)) return SCRIPT_CONTINUE;

        obj_id spawner = getObjIdObjVar(self, "spawner");
        obj_id controller = getObjIdObjVar(spawner, "controller");

        if (isValidId(controller) && getIntObjVar(controller, "space_gcw." + spawner + ".active") == 1) {
            setObjVar(self, "supportCraft", battle_spawner.spawnSupportShips(self));
            removeObjVar(self, "reinforcementCalled"); // reset reinforcement flag after spawn
            debugServerConsoleMsg(self, "[AI] Support ship spawned.");
        }
        return SCRIPT_CONTINUE;
    }

    private void broadcastShipAlert(obj_id self, obj_id attacker) throws InterruptedException {
        String faction = getShipFaction(attacker);
        String name = getPlayerName(attacker);
        String alert = "";

        if (faction.equals("Rebel")) {
            alert = "Rebel ship " + name + " has struck the hull! Shields dropping!";
        } else if (faction.equals("Imperial")) {
            alert = "Imperial strike from " + name + " detected! Brace for impact!";
        } else {
            alert = "Unknown vessel " + name + " has engaged!";
        }

        debugServerConsoleMsg(self, "[ALERT] " + alert);
    }

    private float getShipHealthPercent(obj_id self) throws InterruptedException {
        float current = getFloatObjVar(self, "ship.health");
        float max = getFloatObjVar(self, "ship.maxHealth");
        return (max > 0) ? (current / max) : 1.0f;
    }
} 

