package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class space_modular_crafting extends script.base_script
{
    private static final String DATATABLE = "datatables/space/modular_ship_modules.iff";
    private static final String VAR_BASE_MASS = "modularShip.baseMass";
    private static final String VAR_BASE_HP = "modularShip.baseHp";
    private static final String VAR_ENERGY = "modularShip.energyBonus";
    private static final String VAR_MODULE_ROOT = "modular.modules.";
    private static final String VAR_TIER = "modularShip.tier";
    private static final String VAR_SUPPLY = "modularShip.supplyDelta";
    private static final String VAR_SHIP = "modularShip.shipId";
    private static final String VAR_SHIP_TYPE = "modularShip.shipType";

    private static Map<String, Map<Integer, List<ModuleDefinition>>> MODULES;
    private static Map<String, Integer> MAX_TIERS;

    private static class ModuleDefinition
    {
        final String slot;
        final String moduleId;
        final float massDelta;
        final float hpDelta;
        final float energyDelta;
        final int supplyDelta;
        final String abilityPackage;
        final String description;

        ModuleDefinition()
        {
            this("", "", 0f, 0f, 0f, 0, "", "");
        }

        ModuleDefinition(String slot, String moduleId, float massDelta, float hpDelta, float energyDelta, int supplyDelta, String abilityPackage, String description)
        {
            this.slot = slot;
            this.moduleId = moduleId;
            this.massDelta = massDelta;
            this.hpDelta = hpDelta;
            this.energyDelta = energyDelta;
            this.supplyDelta = supplyDelta;
            this.abilityPackage = abilityPackage;
            this.description = description;
        }
    }

    public space_modular_crafting()
    {
    }

    private static void ensureLoaded() throws InterruptedException
    {
        if (MODULES != null)
        {
            return;
        }
        MODULES = new HashMap<>();
        MAX_TIERS = new HashMap<>();
        int rows = dataTableGetNumRows(DATATABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE, i);
            if (row == null)
            {
                continue;
            }
            String chassis = row.getString("chassisType");
            int tier = row.getInt("tier");
            String slot = row.getString("slot");
            String moduleId = row.getString("moduleId");
            float massDelta = row.getFloat("massDelta");
            float hpDelta = row.getFloat("hpDelta");
            float energyDelta = row.getFloat("energyDelta");
            int supplyDelta = row.getInt("supplyDelta");
            String abilityPackage = row.getString("abilityPackage");
            String description = row.getString("description");
            if ((chassis == null) || chassis.length() == 0 || (slot == null) || slot.length() == 0)
            {
                continue;
            }
            chassis = chassis.toLowerCase();
            ModuleDefinition def = new ModuleDefinition(slot, moduleId, massDelta, hpDelta, energyDelta, supplyDelta, abilityPackage, description);
            Map<Integer, List<ModuleDefinition>> tiers = MODULES.get(chassis);
            if (tiers == null)
            {
                tiers = new HashMap<>();
                MODULES.put(chassis, tiers);
            }
            List<ModuleDefinition> defs = tiers.get(tier);
            if (defs == null)
            {
                defs = new ArrayList<>();
                tiers.put(tier, defs);
            }
            defs.add(def);
            Integer max = MAX_TIERS.get(chassis);
            if (max == null || tier > max)
            {
                MAX_TIERS.put(chassis, tier);
            }
        }
        for (Map<Integer, List<ModuleDefinition>> tierMap : MODULES.values())
        {
            for (List<ModuleDefinition> defs : tierMap.values())
            {
                Collections.sort(defs, (a, b) -> a.slot.compareToIgnoreCase(b.slot));
            }
        }
    }

    private static List<ModuleDefinition> getModulesForTier(String chassisType, int tier) throws InterruptedException
    {
        ensureLoaded();
        if ((chassisType == null) || chassisType.length() == 0)
        {
            return null;
        }
        chassisType = chassisType.toLowerCase();
        Map<Integer, List<ModuleDefinition>> tiers = MODULES.get(chassisType);
        if (tiers == null)
        {
            return null;
        }
        List<ModuleDefinition> defs = tiers.get(Integer.valueOf(tier));
        if (defs == null)
        {
            return null;
        }
        return new ArrayList<>(defs);
    }

    public static void initializeShipModules(obj_id controlDevice, obj_id ship, String chassisType) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(controlDevice) || !isIdValid(ship) || chassisType == null || chassisType.length() == 0)
        {
            return;
        }
        setObjVar(controlDevice, VAR_SHIP_TYPE, chassisType.toLowerCase());
        setObjVar(controlDevice, VAR_SHIP, ship);
        if (!hasObjVar(controlDevice, VAR_TIER))
        {
            setObjVar(controlDevice, VAR_TIER, 1);
        }
        if (!hasObjVar(ship, VAR_BASE_MASS))
        {
            setObjVar(ship, VAR_BASE_MASS, getChassisComponentMassMaximum(ship));
        }
        if (!hasObjVar(ship, VAR_BASE_HP))
        {
            setObjVar(ship, VAR_BASE_HP, getShipMaximumChassisHitPoints(ship));
        }
        refresh(controlDevice);
    }

    public static void refresh(obj_id controlDevice) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(controlDevice))
        {
            return;
        }
        obj_id ship = getObjIdObjVar(controlDevice, VAR_SHIP);
        if (!isIdValid(ship))
        {
            obj_id[] contents = getContents(controlDevice);
            if (contents != null && contents.length > 0)
            {
                ship = contents[0];
                if (isIdValid(ship))
                {
                    setObjVar(controlDevice, VAR_SHIP, ship);
                }
            }
        }
        if (!isIdValid(ship))
        {
            return;
        }
        String chassis = getStringObjVar(controlDevice, VAR_SHIP_TYPE);
        if (chassis == null || chassis.length() == 0)
        {
            return;
        }
        int tier = getIntObjVar(controlDevice, VAR_TIER);
        applyTier(controlDevice, ship, chassis, tier, null, false);
    }

    public static boolean upgradeTier(obj_id controlDevice, obj_id player) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(controlDevice) || !isIdValid(player))
        {
            return false;
        }
        obj_id ship = getObjIdObjVar(controlDevice, VAR_SHIP);
        if (!isIdValid(ship))
        {
            refresh(controlDevice);
            ship = getObjIdObjVar(controlDevice, VAR_SHIP);
        }
        if (!isIdValid(ship))
        {
            sendSystemMessage(player, "Unable to locate ship data for modular upgrade.", "");
            return false;
        }
        String chassis = getStringObjVar(controlDevice, VAR_SHIP_TYPE);
        if (chassis == null || chassis.length() == 0)
        {
            sendSystemMessage(player, "This chassis does not support modular upgrades.", "");
            return false;
        }
        int tier = getIntObjVar(controlDevice, VAR_TIER);
        int maxTier = getMaxTierForChassis(chassis);
        if (tier >= maxTier)
        {
            sendSystemMessage(player, "All modular upgrades for this chassis have been unlocked.", "");
            return false;
        }
        int nextTier = tier + 1;
        int cost = getUpgradeCost(nextTier);
        int cores = space_dynamic_content.getAstrogationCores(player);
        if (cores < cost)
        {
            sendSystemMessage(player, "You require " + cost + " Astrogation Cores to perform this upgrade.", "");
            return false;
        }
        if (!space_dynamic_content.spendAstrogationCores(player, cost))
        {
            sendSystemMessage(player, "Astrogation core expenditure failed. Try again.", "");
            return false;
        }
        setObjVar(controlDevice, VAR_TIER, nextTier);
        applyTier(controlDevice, ship, chassis, nextTier, player, true);
        return true;
    }

    public static dictionary getModuleStatus(obj_id controlDevice) throws InterruptedException
    {
        ensureLoaded();
        dictionary status = new dictionary();
        if (!isIdValid(controlDevice))
        {
            return status;
        }
        String chassis = getStringObjVar(controlDevice, VAR_SHIP_TYPE);
        int tier = getIntObjVar(controlDevice, VAR_TIER);
        status.put("tier", tier);
        status.put("maxTier", getMaxTierForChassis(chassis));
        status.put("massBonus", getFloatObjVar(controlDevice, "modularShip.massBonus"));
        status.put("hpBonus", getFloatObjVar(controlDevice, "modularShip.hpBonus"));
        status.put("energyBonus", getFloatObjVar(controlDevice, VAR_ENERGY));
        status.put("supplyDelta", getIntObjVar(controlDevice, VAR_SUPPLY));
        status.put("modules", buildModuleSummary(chassis, tier));
        return status;
    }

    private static int getUpgradeCost(int nextTier)
    {
        return Math.max(1, nextTier * 2);
    }

    private static int getMaxTierForChassis(String chassisType) throws InterruptedException
    {
        ensureLoaded();
        if (chassisType == null)
        {
            return 1;
        }
        Integer value = MAX_TIERS.get(chassisType.toLowerCase());
        return value != null ? value : 1;
    }

    private static boolean applyTier(obj_id controlDevice, obj_id ship, String chassisType, int tier, obj_id player, boolean announce) throws InterruptedException
    {
        List<ModuleDefinition> modules = getModulesForTier(chassisType, tier);
        if (modules == null || modules.isEmpty())
        {
            return false;
        }
        if (!hasObjVar(ship, VAR_BASE_MASS))
        {
            setObjVar(ship, VAR_BASE_MASS, getChassisComponentMassMaximum(ship));
        }
        if (!hasObjVar(ship, VAR_BASE_HP))
        {
            setObjVar(ship, VAR_BASE_HP, getShipMaximumChassisHitPoints(ship));
        }
        float baseMass = getFloatObjVar(ship, VAR_BASE_MASS);
        float baseHp = getFloatObjVar(ship, VAR_BASE_HP);
        float totalMass = 0.0f;
        float totalHp = 0.0f;
        float totalEnergy = 0.0f;
        int supply = 0;
        for (ModuleDefinition def : modules)
        {
            totalMass += def.massDelta;
            totalHp += def.hpDelta;
            totalEnergy += def.energyDelta;
            supply += def.supplyDelta;
            String path = VAR_MODULE_ROOT + def.slot.toLowerCase();
            setObjVar(ship, path + ".id", def.moduleId);
            setObjVar(ship, path + ".tier", tier);
            if (def.abilityPackage != null && def.abilityPackage.length() > 0)
            {
                setObjVar(ship, path + ".ability", def.abilityPackage);
            }
            else if (hasObjVar(ship, path + ".ability"))
            {
                removeObjVar(ship, path + ".ability");
            }
        }
        float newMass = baseMass + totalMass;
        float newHp = baseHp + totalHp;
        setChassisComponentMassMaximum(ship, newMass);
        setObjVar(controlDevice, "modularShip.massBonus", totalMass);
        setShipMaximumChassisHitPoints(ship, newHp);
        float currentHp = getShipCurrentChassisHitPoints(ship);
        if (currentHp > newHp)
        {
            setShipCurrentChassisHitPoints(ship, newHp);
        }
        else
        {
            setShipCurrentChassisHitPoints(ship, currentHp + totalHp);
        }
        setObjVar(controlDevice, "modularShip.hpBonus", totalHp);
        setObjVar(controlDevice, VAR_ENERGY, totalEnergy);
        setObjVar(ship, VAR_ENERGY, totalEnergy);
        setObjVar(controlDevice, VAR_SUPPLY, supply);
        setObjVar(ship, "modularShip.currentTier", tier);
        if (announce && isIdValid(player))
        {
            sendSystemMessage(player, "Modular systems recalibrated to tier " + tier + ".", "");
            sendSystemMessage(player, "Astrogation cores remaining: " + space_dynamic_content.getAstrogationCores(player), "");
            location loc = getLocation(player);
            String planet = (loc != null && loc.area != null) ? loc.area : "tatooine";
            int faction = pvpGetAlignedFaction(player);
            script.library.gcw_campaign.recordSupplyContribution(planet, faction, supply, "modular_upgrade");
        }
        return true;
    }

    private static String buildModuleSummary(String chassisType, int tier) throws InterruptedException
    {
        List<ModuleDefinition> modules = getModulesForTier(chassisType, tier);
        if (modules == null || modules.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ModuleDefinition def : modules)
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(capitalize(def.slot)).append(": ").append(def.moduleId.replace('_', ' '));
        }
        return sb.toString();
    }

    private static String capitalize(String value)
    {
        if (value == null || value.length() == 0)
        {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
