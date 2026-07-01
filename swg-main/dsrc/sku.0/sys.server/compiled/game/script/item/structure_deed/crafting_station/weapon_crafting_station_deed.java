package script.item.structure_deed.crafting_station;

import script.*;

public class weapon_crafting_station_deed extends script.base_script
{
    public weapon_crafting_station_deed()
    {
    }

    public static final String VERSION = "v1.00.00";

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setObjVar(self, "unUsed", 1);
        setObjVar(self, "canUseInSpace", true); // ✅ Added for space compatibility
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        menu_info_data mid = mi.getMenuItemByType(menu_info_types.ITEM_USE);
        if (mid != null)
        {
            mid.setServerNotify(true);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item == menu_info_types.ITEM_USE)
        {
            if (hasObjVar(self, "usedUp"))
            {
                return SCRIPT_CONTINUE;
            }

            location locTest = new location(getLocation(player));
            locTest.x += 5;
            locTest.z += 5;

            if (isInSpace(player)) // Optional space handling
            {
                locTest.y += 10; // Raise for zero-G placement if needed
            }

            String harvesterTemplate = "object/tangible/crafting/station/weapon_station.iff";
            obj_id harvesterObject = createObject(harvesterTemplate, locTest);

            if (harvesterObject == null)
            {
                return SCRIPT_OVERRIDE;
            }

            setObjVar(self, "usedUp", 1);
            destroyObject(self);
        }
        return SCRIPT_CONTINUE;
    }

    // Helper stub for space detection
    private boolean isInSpace(obj_id player)
    {
        String scene = getCurrentSceneName();
        return scene != null && scene.indexOf("space") != -1;
    }
}

