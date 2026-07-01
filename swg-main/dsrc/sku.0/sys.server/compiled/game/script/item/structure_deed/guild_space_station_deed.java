package script.item.structure_deed;

import script.*;
import script.library.guild_space_station;
import script.library.space_transition;

public class guild_space_station_deed extends script.base_script
{
    public guild_space_station_deed()
    {
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
        if (item != menu_info_types.ITEM_USE)
        {
            return SCRIPT_CONTINUE;
        }
        location loc = getLocation(player);
        obj_id ship = space_transition.getContainingShip(player);
        if (isIdValid(ship))
        {
            loc = getLocation(ship);
        }
        float yaw = isIdValid(ship) ? getYaw(ship) : 0.0f;
        location offset = new location(loc.x, loc.y, loc.z + 500.0f, loc.area, loc.cell);
        guild_space_station.spawnStation(player, self, offset, yaw);
        return SCRIPT_CONTINUE;
    }
}
