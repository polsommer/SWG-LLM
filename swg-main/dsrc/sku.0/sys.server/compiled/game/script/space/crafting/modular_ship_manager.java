package script.space.crafting;

import script.dictionary;
import script.menu_info;
import script.menu_info_types;
import script.obj_id;
import script.string_id;

import script.library.space_modular_crafting;
import script.library.utils;

public class modular_ship_manager extends script.base_script
{
    private static final string_id SID_STATUS = new string_id("space/space_interaction", "modular_status");
    private static final string_id SID_UPGRADE = new string_id("space/space_interaction", "modular_upgrade");
    private static boolean modularCraftingAvailableChecked = false;
    private static boolean modularCraftingAvailable = false;

    public modular_ship_manager()
    {
    }

    private static boolean isModularCraftingAvailable()
    {
        if (!modularCraftingAvailableChecked)
        {
            modularCraftingAvailableChecked = true;
            try
            {
                Class.forName("script.library.space_modular_crafting");
                modularCraftingAvailable = true;
            }
            catch (Throwable err)
            {
                modularCraftingAvailable = false;
                LOG("space_modular", "space_modular_crafting not available: " + err);
            }
        }
        return modularCraftingAvailable;
    }

    private static void markModularCraftingUnavailable(Throwable err)
    {
        modularCraftingAvailable = false;
        modularCraftingAvailableChecked = true;
        LOG("space_modular", "Failed to use space_modular_crafting: " + err);
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (!isModularCraftingAvailable())
        {
            return SCRIPT_CONTINUE;
        }
        try
        {
            space_modular_crafting.refresh(self);
        }
        catch (LinkageError err)
        {
            markModularCraftingUnavailable(err);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (!isModularCraftingAvailable())
        {
            return SCRIPT_CONTINUE;
        }
        try
        {
            space_modular_crafting.refresh(self);
        }
        catch (LinkageError err)
        {
            markModularCraftingUnavailable(err);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!isModularCraftingAvailable())
        {
            return SCRIPT_CONTINUE;
        }
        if (!utils.isNestedWithin(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        mi.addRootMenu(menu_info_types.SERVER_MENU4, SID_STATUS);
        mi.addRootMenu(menu_info_types.SERVER_MENU5, SID_UPGRADE);
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (!isModularCraftingAvailable())
        {
            return SCRIPT_CONTINUE;
        }
        if (!utils.isNestedWithin(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        if (item == menu_info_types.SERVER_MENU4)
        {
            showStatus(self, player);
        }
        else if (item == menu_info_types.SERVER_MENU5)
        {
            attemptUpgrade(self, player);
        }
        return SCRIPT_CONTINUE;
    }

    private void showStatus(obj_id controlDevice, obj_id player) throws InterruptedException
    {
        if (!isModularCraftingAvailable())
        {
            sendSystemMessage(player, "Modular ship management is currently unavailable.", "");
            return;
        }
        dictionary status = space_modular_crafting.getModuleStatus(controlDevice);
        int tier = status.getInt("tier");
        int maxTier = status.getInt("maxTier");
        float mass = status.getFloat("massBonus");
        float hp = status.getFloat("hpBonus");
        float energy = status.getFloat("energyBonus");
        int supply = status.getInt("supplyDelta");
        sendSystemMessage(player, "Modular tier: " + tier + "/" + maxTier, "");
        sendSystemMessage(player, "Mass allowance bonus: " + Math.round(mass) + " kg", "");
        sendSystemMessage(player, "Chassis integrity bonus: " + Math.round(hp) + "", "");
        sendSystemMessage(player, "Power pool bonus: " + Math.round(energy) + "", "");
        sendSystemMessage(player, "Supply contribution rating: " + supply, "");
        int nextTier = tier + 1;
        if (nextTier <= maxTier)
        {
            int cost = Math.max(1, nextTier * 2);
            int cores = getIntObjVar(player, "spaceDynamic.astrogationCores");
            sendSystemMessage(player, "Astrogation cores: " + cores + " (" + cost + " required for next tier)", "");
        }
        String summary = status.getString("modules");
        if (summary != null && summary.length() > 0)
        {
            String[] lines = summary.split("\n");
            for (String line : lines)
            {
                if (line != null && line.length() > 0)
                {
                    sendSystemMessage(player, " - " + line, "");
                }
            }
        }
    }

    private void attemptUpgrade(obj_id controlDevice, obj_id player) throws InterruptedException
    {
        if (!isModularCraftingAvailable())
        {
            sendSystemMessage(player, "Modular ship management is currently unavailable.", "");
            return;
        }
        try
        {
            if (space_modular_crafting.upgradeTier(controlDevice, player))
            {
                showStatus(controlDevice, player);
            }
        }
        catch (LinkageError err)
        {
            markModularCraftingUnavailable(err);
        }
    }
}
