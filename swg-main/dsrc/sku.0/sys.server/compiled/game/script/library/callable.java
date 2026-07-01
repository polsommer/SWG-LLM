package script.library;

import script.obj_id;

import java.util.Vector;

public class callable extends script.base_script
{
    public callable()
    {
    }
    public static final int CALLABLE_TYPE_UNKNOWN = -1;
    public static final int CALLABLE_TYPE_COMBAT_OTHER = 0;
    public static final int CALLABLE_TYPE_COMBAT_PET = 1;
    public static final int CALLABLE_TYPE_FAMILIAR = 2;
    public static final int CALLABLE_TYPE_RIDEABLE = 3;
    public static final int CALLABLE_TYPE_SHIP = 4;
    public static final String OBJVAR_CALLABLE_TYPE_COMBAT_OTHER = "callable.type.combat_other";
    public static final String OBJVAR_CALLABLE_TYPE_COMBAT_PET = "callable.type.combat_pet";
    public static final String OBJVAR_CALLABLE_TYPE_FAMILIAR = "callable.type.familiar";
    public static final String OBJVAR_CALLABLE_TYPE_RIDEABLE = "callable.type.rideable";
    public static final String OBJVAR_CALLABLE_CALLED = "pet.called";
    public static final String OBJVAR_CALLABLE_CONTROL_DEVICE = "pet.controlDevice";
    public static final String SCRIPTVAR_RIDEABLE_PACKED_CALLABLE = "callable.recently_packed";
    public static final int MAX_STORED_COMBAT_PETS = 20;
    public static final int MAX_STORED_FAMILIARS = 40;
    public static final int BASE_MAX_STORED_RIDEABLES = 60;
    public static final int ABSOLUTE_MAX_STORED_RIDEABLES = 150;
    public static final String ADDON_MAX_STORED_RIDEABLES_OBJVAR = "rideables.addonMaxStoredRideables";
    public static void clog(String text) throws InterruptedException
    {
        if (text != null)
        {
            LOG("callable beasts", text);
        }
    }
    public static int getControlDeviceType(obj_id cd) throws InterruptedException
    {
        if (beast_lib.isValidBCD(cd))
        {
            return CALLABLE_TYPE_COMBAT_PET;
        }
        if (hasObjVar(cd, "ai.pet.trainedMount") || hasScript(cd, "systems.vehicle_system.vehicle_control_device"))
        {
            return CALLABLE_TYPE_RIDEABLE;
        }
        if (hasScript(cd, "space.ship_control_device.ship_control_device"))
        {
            return CALLABLE_TYPE_SHIP;
        }
        int petType = -1;
        if (hasObjVar(cd, "ai.pet.type"))
        {
            petType = getIntObjVar(cd, "ai.pet.type");
        }
        switch (petType)
        {
            case pet_lib.PET_TYPE_NON_AGGRO:
            case pet_lib.PET_TYPE_AGGRO:
            case pet_lib.PET_TYPE_NPC:
            case pet_lib.PET_TYPE_DROID:
            return CALLABLE_TYPE_COMBAT_PET;
            case pet_lib.PET_TYPE_FAMILIAR:
            return CALLABLE_TYPE_FAMILIAR;
        }
        return CALLABLE_TYPE_UNKNOWN;
    }
    public static int getCallableType(obj_id objCallable) throws InterruptedException
    {
        if (!isIdValid(objCallable) || !exists(objCallable))
        {
            return CALLABLE_TYPE_UNKNOWN;
        }
        if ((beast_lib.isBeast(objCallable) || pet_lib.isDroidPet(objCallable) || ai_lib.aiGetNiche(objCallable) == NICHE_NPC) && pet_lib.getPetType(objCallable) != pet_lib.PET_TYPE_FAMILIAR)
        {
            return CALLABLE_TYPE_COMBAT_PET;
        }
        if (pet_lib.isFamiliarPetType(objCallable))
        {
            return CALLABLE_TYPE_FAMILIAR;
        }
        if (hasCallableCD(objCallable))
        {
            obj_id petControlDevice = getCallableCD(objCallable);
            if ((isValidId(petControlDevice) && exists(petControlDevice) && hasObjVar(petControlDevice, "ai.pet.trainedMount")) || pet_lib.isVehiclePet(objCallable))
            {
                return CALLABLE_TYPE_RIDEABLE;
            }
        }
        if (hasObjVar(objCallable, "ai.pet.buddy"))
        {
            return CALLABLE_TYPE_COMBAT_OTHER;
        }
        return CALLABLE_TYPE_UNKNOWN;
    }
    public static String getCallableTypeObjVar(int type) throws InterruptedException
    {
        switch (type)
        {
            case CALLABLE_TYPE_COMBAT_OTHER:
            return OBJVAR_CALLABLE_TYPE_COMBAT_OTHER;
            case CALLABLE_TYPE_COMBAT_PET:
            return OBJVAR_CALLABLE_TYPE_COMBAT_PET;
            case CALLABLE_TYPE_FAMILIAR:
            return OBJVAR_CALLABLE_TYPE_FAMILIAR;
            case CALLABLE_TYPE_RIDEABLE:
            return OBJVAR_CALLABLE_TYPE_RIDEABLE;
            default:
            return null;
        }
    }
    private static obj_id[] getCallableArray(obj_id master, String strObjVar) throws InterruptedException
    {
        if (!beast_lib.isValidPlayer(master) || strObjVar == null)
        {
            return new obj_id[0];
        }
        obj_id[] stored = null;
        if (utils.hasScriptVar(master, strObjVar))
        {
            stored = utils.getObjIdArrayScriptVar(master, strObjVar);
        }
        if (stored == null || stored.length == 0)
        {
            if (utils.hasScriptVar(master, strObjVar))
            {
                utils.removeScriptVar(master, strObjVar);
            }
            return new obj_id[0];
        }
        Vector valid = new Vector();
        valid.setSize(0);
        boolean changed = false;
        for (obj_id entry : stored)
        {
            if (isIdValid(entry) && exists(entry))
            {
                valid.addElement(entry);
            }
            else
            {
                changed = true;
            }
        }
        if (valid.size() == 0)
        {
            utils.removeScriptVar(master, strObjVar);
            return new obj_id[0];
        }
        obj_id[] cleaned = new obj_id[valid.size()];
        valid.toArray(cleaned);
        if (changed || cleaned.length != stored.length)
        {
            utils.setScriptVar(master, strObjVar, cleaned);
        }
        return cleaned;
    }
    public static obj_id[] getCallablesByType(obj_id master, int objCallableType) throws InterruptedException
    {
        String strObjVar = getCallableTypeObjVar(objCallableType);
        if (strObjVar == null)
        {
            return new obj_id[0];
        }
        return getCallableArray(master, strObjVar);
    }
    public static int getCallableCount(obj_id master, int objCallableType) throws InterruptedException
    {
        obj_id[] callables = getCallablesByType(master, objCallableType);
        if (callables == null)
        {
            return 0;
        }
        return callables.length;
    }
    public static boolean removeCallable(obj_id master, obj_id objCallable, int objCallableType) throws InterruptedException
    {
        if (!beast_lib.isValidPlayer(master))
        {
            return false;
        }
        if (!isIdValid(objCallable) || !exists(objCallable))
        {
            return false;
        }
        String strObjVar = getCallableTypeObjVar(objCallableType);
        if (strObjVar == null)
        {
            return false;
        }
        obj_id[] callables = getCallableArray(master, strObjVar);
        if (callables == null || callables.length == 0)
        {
            return false;
        }
        Vector remaining = new Vector();
        remaining.setSize(0);
        boolean removed = false;
        for (obj_id callableId : callables)
        {
            if (callableId == objCallable)
            {
                removed = true;
                continue;
            }
            if (isIdValid(callableId) && exists(callableId))
            {
                remaining.addElement(callableId);
            }
        }
        if (!removed)
        {
            return false;
        }
        if (remaining.size() == 0)
        {
            utils.removeScriptVar(master, strObjVar);
        }
        else
        {
            obj_id[] updated = new obj_id[remaining.size()];
            remaining.toArray(updated);
            utils.setScriptVar(master, strObjVar, updated);
        }
        return true;
    }
    public static obj_id getCallable(obj_id master, int objCallableType) throws InterruptedException
    {
        obj_id[] callables = getCallablesByType(master, objCallableType);
        if (callables != null && callables.length > 0)
        {
            return callables[0];
        }
        return null;
    }
    public static obj_id[] getCallables(obj_id master) throws InterruptedException
    {
        int[] callableTypes =
        {
            CALLABLE_TYPE_COMBAT_OTHER,
            CALLABLE_TYPE_COMBAT_PET,
            CALLABLE_TYPE_FAMILIAR,
            CALLABLE_TYPE_RIDEABLE
        };
        Vector callables = new Vector();
        callables.setSize(0);
        for (int callableType : callableTypes) {
            obj_id[] typeCallables = getCallablesByType(master, callableType);
            if (typeCallables == null || typeCallables.length == 0) {
                continue;
            }
            for (obj_id objCallable : typeCallables) {
                if (isIdValid(objCallable) && exists(objCallable)) {
                    callables = utils.addElement(callables, objCallable);
                }
            }
        }
        obj_id[] _callables = new obj_id[0];
        if (callables != null)
        {
            _callables = new obj_id[callables.size()];
            callables.toArray(_callables);
        }
        return _callables;
    }
    public static obj_id[] getDatapadCallablesByType(obj_id master, int callableType) throws InterruptedException
    {
        Vector callables = new Vector();
        callables.setSize(0);
        obj_id datapad = utils.getPlayerDatapad(master);
        if (!isIdValid(datapad))
        {
            return null;
        }
        obj_id[] dataItems = getContents(datapad);
        for (obj_id dataItem : dataItems) {
            if (getControlDeviceType(dataItem) == callableType) {
                callables = utils.addElement(callables, dataItem);
            }
        }
        obj_id[] _callables = new obj_id[0];
        if (callables != null)
        {
            _callables = new obj_id[callables.size()];
            callables.toArray(_callables);
        }
        return _callables;
    }
    public static boolean hasCallable(obj_id master, int objCallableType) throws InterruptedException
    {
        return hasCallable(master, objCallableType, obj_id.NULL_ID);
    }
    public static boolean hasCallable(obj_id master, int objCallableType, obj_id beast) throws InterruptedException
    {
        if (!beast_lib.isValidPlayer(master))
        {
            return true;
        }
        String strObjVar = getCallableTypeObjVar(objCallableType);
        if (strObjVar == null)
        {
            return true;
        }
        obj_id[] callables = getCallableArray(master, strObjVar);
        if (callables == null || callables.length == 0)
        {
            return false;
        }
        if (!isIdValid(beast))
        {
            return true;
        }
        for (obj_id callableId : callables)
        {
            if (callableId != beast)
            {
                return true;
            }
        }
        return false;
    }
    public static boolean hasAnyCallable(obj_id master) throws InterruptedException
    {
        if (hasCallable(master, CALLABLE_TYPE_COMBAT_OTHER) || hasCallable(master, CALLABLE_TYPE_COMBAT_PET) || hasCallable(master, CALLABLE_TYPE_FAMILIAR) || hasCallable(master, CALLABLE_TYPE_RIDEABLE))
        {
            return true;
        }
        return false;
    }
    public static boolean setCallable(obj_id master, obj_id objCallable, int objCallableType) throws InterruptedException
    {
        if (!beast_lib.isValidPlayer(master))
        {
            return false;
        }
        if (objCallableType == -1 && isIdValid(objCallable) && exists(objCallable))
        {
            objCallableType = getCallableType(objCallable);
        }
        String strObjVar = getCallableTypeObjVar(objCallableType);
        if (strObjVar == null)
        {
            return false;
        }
        if (objCallableType != -1 && !isIdValid(objCallable))
        {
            utils.removeScriptVar(master, strObjVar);
            return true;
        }
        if (!isIdValid(objCallable) || !exists(objCallable))
        {
            return false;
        }
        obj_id[] callables = getCallableArray(master, strObjVar);
        if (callables != null)
        {
            for (obj_id callableId : callables)
            {
                if (callableId == objCallable)
                {
                    return true;
                }
            }
        }
        int currentLength = (callables != null) ? callables.length : 0;
        obj_id[] updated = new obj_id[currentLength + 1];
        if (currentLength > 0)
        {
            System.arraycopy(callables, 0, updated, 0, currentLength);
        }
        updated[currentLength] = objCallable;
        utils.setScriptVar(master, strObjVar, updated);
        return true;
    }
    public static obj_id getCDCallable(obj_id cd) throws InterruptedException
    {
        if (!isIdValid(cd) || !exists(cd))
        {
            return null;
        }
        if (utils.hasScriptVar(cd, OBJVAR_CALLABLE_CALLED))
        {
            obj_id objCallable = utils.getObjIdScriptVar(cd, OBJVAR_CALLABLE_CALLED);
            if (isIdValid(objCallable) && exists(objCallable))
            {
                return objCallable;
            }
        }
        return null;
    }
    public static boolean hasCDCallable(obj_id cd) throws InterruptedException
    {
        if (!isIdValid(cd) || !exists(cd))
        {
            return false;
        }
        if (utils.hasScriptVar(cd, OBJVAR_CALLABLE_CALLED))
        {
            obj_id objCallable = utils.getObjIdScriptVar(cd, OBJVAR_CALLABLE_CALLED);
            if (isIdValid(objCallable) && exists(objCallable))
            {
                return true;
            }
        }
        return false;
    }
    public static boolean setCDCallable(obj_id cd, obj_id objCallable) throws InterruptedException
    {
        if (!isIdValid(cd) || !exists(cd))
        {
            return false;
        }
        if (isIdValid(objCallable) && exists(objCallable))
        {
            utils.setScriptVar(cd, OBJVAR_CALLABLE_CALLED, objCallable);
        }
        else
        {
            utils.removeScriptVar(cd, OBJVAR_CALLABLE_CALLED);
        }
        return true;
    }
    public static obj_id getCallableCD(obj_id objCallable) throws InterruptedException
    {
        if (!isIdValid(objCallable) || !exists(objCallable))
        {
            return null;
        }
        if (hasObjVar(objCallable, OBJVAR_CALLABLE_CONTROL_DEVICE))
        {
            obj_id cd = getObjIdObjVar(objCallable, OBJVAR_CALLABLE_CONTROL_DEVICE);
            if (isIdValid(cd) && exists(cd))
            {
                return cd;
            }
        }
        return null;
    }
    public static boolean hasCallableCD(obj_id objCallable) throws InterruptedException
    {
        if (!isIdValid(objCallable) || !exists(objCallable))
        {
            return false;
        }
        if (hasObjVar(objCallable, OBJVAR_CALLABLE_CONTROL_DEVICE))
        {
            obj_id cd = getObjIdObjVar(objCallable, OBJVAR_CALLABLE_CONTROL_DEVICE);
            if (isIdValid(cd) && exists(cd))
            {
                return true;
            }
        }
        return false;
    }
    public static boolean setCallableCD(obj_id objCallable, obj_id cd) throws InterruptedException
    {
        if (!isIdValid(cd) || !exists(cd) || !isIdValid(objCallable) || !exists(objCallable))
        {
            return false;
        }
        setObjVar(objCallable, OBJVAR_CALLABLE_CONTROL_DEVICE, cd);
        return true;
    }
    public static void setCallableLinks(obj_id master, obj_id cd, obj_id objCallable) throws InterruptedException
    {
        setCallable(master, objCallable, getCallableType(objCallable));
        setCDCallable(cd, objCallable);
        setCallableCD(objCallable, cd);
        setMaster(objCallable, master);
        if (pet_lib.isDroidPet(objCallable))
        {
            obj_id inventory = utils.getInventoryContainer(objCallable);
            if (isIdValid(inventory))
            {
                setOwner(inventory, master);
            }
        }
    }
    public static void storeCallable(obj_id master, obj_id objCallable) throws InterruptedException
    {
        if (!isIdValid(objCallable) || !exists(objCallable))
        {
            return;
        }
        int callableType = getCallableType(objCallable);
        if (beast_lib.isBeast(objCallable))
        {
            beast_lib.storeBeast(beast_lib.getBeastBCD(objCallable));
        }
        else
        {
            clog("callableType: " + callableType + " pet_lib.isVehiclePet(objCallable): " + pet_lib.isVehiclePet(objCallable));
            if (pet_lib.isVehiclePet(objCallable))
            {
                vehicle.storeVehicle(getCallableCD(objCallable), master, false);
            }
            else
            {
                pet_lib.storePet(objCallable, master);
            }
        }
        removeCallable(master, objCallable, callableType);
    }
    public static void storeCallables(obj_id master) throws InterruptedException
    {
        if (!isIdValid(master) || !exists(master) || !hasAnyCallable(master))
        {
            return;
        }
        int[] callableTypes =
        {
            CALLABLE_TYPE_COMBAT_OTHER,
            CALLABLE_TYPE_COMBAT_PET,
            CALLABLE_TYPE_FAMILIAR,
            CALLABLE_TYPE_RIDEABLE
        };
        for (int callableType : callableTypes) {
            obj_id[] typeCallables = getCallablesByType(master, callableType);
            if (typeCallables == null || typeCallables.length == 0) {
                continue;
            }
            for (obj_id objCallable : typeCallables) {
                if (isIdValid(objCallable) && exists(objCallable)) {
                    storeCallable(master, objCallable);
                }
            }
        }
    }
    public static void restoreCallable(obj_id master) throws InterruptedException
    {
        if (!isIdValid(master) || !exists(master) || hasCallable(master, CALLABLE_TYPE_COMBAT_PET) || !utils.hasScriptVar(master, SCRIPTVAR_RIDEABLE_PACKED_CALLABLE))
        {
            return;
        }
        obj_id cd = utils.getObjIdScriptVar(master, SCRIPTVAR_RIDEABLE_PACKED_CALLABLE);
        if (beast_lib.isValidBCD(cd))
        {
            beast_lib.createBeastFromBCD(master, cd);
        }
        utils.removeScriptVar(master, SCRIPTVAR_RIDEABLE_PACKED_CALLABLE);
    }
    public static void killCallable(obj_id objCallable, obj_id killer) throws InterruptedException
    {
        if (!isIdValid(objCallable) || !exists(objCallable))
        {
            return;
        }
        if (beast_lib.isBeast(objCallable))
        {
            if (!isDead(objCallable))
            {
                beast_lib.killBeast(objCallable, killer);
            }
        }
        else
        {
            if (objCallable.isLoaded() && ai_lib.aiIsDead(objCallable) && !isDead(objCallable))
            {
                utils.setScriptVar(objCallable, "killer", killer);
                pet_lib.killPet(objCallable);
            }
        }
    }
    public static void killCallables(obj_id master, obj_id killer) throws InterruptedException
    {
        if (!isIdValid(master) || !exists(master) || !hasAnyCallable(master))
        {
            return;
        }
        int[] callableTypes =
        {
            CALLABLE_TYPE_COMBAT_OTHER,
            CALLABLE_TYPE_COMBAT_PET,
            CALLABLE_TYPE_FAMILIAR,
            CALLABLE_TYPE_RIDEABLE
        };
        for (int callableType : callableTypes) {
            obj_id[] typeCallables = getCallablesByType(master, callableType);
            if (typeCallables == null || typeCallables.length == 0) {
                continue;
            }
            for (obj_id objCallable : typeCallables) {
                if (isIdValid(objCallable) && exists(objCallable)) {
                    killCallable(objCallable, killer);
                }
            }
        }
    }
    public static int getNumStoredCDByType(obj_id master, int CDType) throws InterruptedException
    {
        if (!isIdValid(master) || !exists(master))
        {
            return 0;
        }
        obj_id datapad = utils.getPlayerDatapad(master);
        if (!isIdValid(datapad))
        {
            return 0;
        }
        obj_id[] dataItems = getContents(datapad);
        if (dataItems == null || dataItems.length < 1)
        {
            return 0;
        }
        int numStored = 0;
        for (obj_id dataItem : dataItems) {
            if (getControlDeviceType(dataItem) == CDType) {
                numStored++;
            }
        }
        return numStored;
    }
    public static int getMaxAllowedStoredRideables(obj_id player) throws InterruptedException
    {
        int totalMaxStoredRideables = BASE_MAX_STORED_RIDEABLES;
        int addonMaxStoredRideables = getAddonMaxAllowedStoredRideables(player);
        if (addonMaxStoredRideables < 0)
        {
            addonMaxStoredRideables = 0;
        }
        totalMaxStoredRideables += addonMaxStoredRideables;
        if (totalMaxStoredRideables > ABSOLUTE_MAX_STORED_RIDEABLES)
        {
            totalMaxStoredRideables = ABSOLUTE_MAX_STORED_RIDEABLES;
        }
        return totalMaxStoredRideables;
    }
    public static int getAddonMaxAllowedStoredRideables(obj_id player) throws InterruptedException
    {
        int addonAmount = 0;
        if (hasObjVar(player, ADDON_MAX_STORED_RIDEABLES_OBJVAR))
        {
            addonAmount = getIntObjVar(player, ADDON_MAX_STORED_RIDEABLES_OBJVAR);
        }
        return addonAmount;
    }
    public static boolean hasMaxStoredRideables(obj_id player) throws InterruptedException
    {
        int totalMaxStoredRideables = getMaxAllowedStoredRideables(player);
        if (getNumStoredCDByType(player, CALLABLE_TYPE_RIDEABLE) >= totalMaxStoredRideables)
        {
            return true;
        }
        return false;
    }
    public static boolean increaseAddonMaxStoredRideables(obj_id player, int amountIncreased) throws InterruptedException
    {
        return increaseAddonMaxStoredRideables(player, amountIncreased, obj_id.NULL_ID);
    }
    public static boolean increaseAddonMaxStoredRideables(obj_id player, int amountIncreased, obj_id source) throws InterruptedException
    {
        int currentAddonAmount = getAddonMaxAllowedStoredRideables(player);
        int newAddonMaxAmount = currentAddonAmount + amountIncreased;
        if (newAddonMaxAmount < 0)
        {
            String negativeAddonLog = "Increased Addon for Max Stored Rideables *FAILED*: (" + player + ") " + getName(player) + ": has failed to increase max stored rideables - would be negative addon amount: attempted new addon = " + newAddonMaxAmount + "(current addon [" + currentAddonAmount + "] + attempted increase to addon[" + amountIncreased + "]).";
            if (isIdValid(source))
            {
                negativeAddonLog += " Source of Increase: " + getEncodedName(source) + " (" + source + ").";
            }
            CustomerServiceLog("max_stored_rideables", negativeAddonLog);
            return false;
        }
        int currentMaxAllowedStoredRideables = getMaxAllowedStoredRideables(player);
        int newMaxAllowedStoredRideables = BASE_MAX_STORED_RIDEABLES + newAddonMaxAmount;
        if (newMaxAllowedStoredRideables > ABSOLUTE_MAX_STORED_RIDEABLES)
        {
            String overCapLog = "Increased Addon for Max Stored Rideables *FAILED*: (" + player + ") " + getName(player) + ": has failed to increase max stored rideables - would be over the cap: attempted new max = " + newMaxAllowedStoredRideables + "(Base Amount [" + BASE_MAX_STORED_RIDEABLES + "] + current addon [" + currentAddonAmount + "] + attempted increase to addon[" + amountIncreased + "]).";
            if (isIdValid(source))
            {
                overCapLog += " Source of Increase: " + getEncodedName(source) + " (" + source + ").";
            }
            CustomerServiceLog("max_stored_rideables", overCapLog);
            return false;
        }
        setObjVar(player, ADDON_MAX_STORED_RIDEABLES_OBJVAR, newAddonMaxAmount);
        String successLog = "Increased Addon for Max Stored Rideables *SUCCESS*: (" + player + ") " + getName(player) + ": successfully increased max stored rideables: new max = " + newMaxAllowedStoredRideables + " (Base Amount [" + BASE_MAX_STORED_RIDEABLES + "] + current addon [" + currentAddonAmount + "] + increase to addon[" + amountIncreased + "]).";
        if (isIdValid(source))
        {
            successLog += " Source of Increase: " + getEncodedName(source) + " (" + source + ").";
        }
        CustomerServiceLog("max_stored_rideables", successLog);
        return true;
    }
    public static boolean hasMaxStoredCombatPets(obj_id master) throws InterruptedException
    {
        if (getNumStoredCDByType(master, CALLABLE_TYPE_COMBAT_PET) >= MAX_STORED_COMBAT_PETS)
        {
            return true;
        }
        return false;
    }
}
