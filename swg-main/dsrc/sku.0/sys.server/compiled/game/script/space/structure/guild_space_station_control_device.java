package script.space.structure;

import script.*;
import script.library.guild_space_station;
import script.library.utils;

public class guild_space_station_control_device extends script.base_script
{
    public guild_space_station_control_device()
    {
    }
    public static final string_id DEPLOY_STATION = new string_id("space/space_interaction", "deploy_guild_station");
    public static final string_id RECALL_STATION = new string_id("space/space_interaction", "recall_guild_station");
    public int OnAttach(obj_id self) throws InterruptedException
    {
        setObjVar(self, "noTrade", 1);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setObjVar(self, "noTrade", 1);
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!utils.isNestedWithin(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        if (isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id station = guild_space_station.getStationForControlDevice(self);
        if (isIdValid(station) && exists(station))
        {
            mi.addRootMenu(menu_info_types.SERVER_MENU1, RECALL_STATION);
        }
        else
        {
            mi.addRootMenu(menu_info_types.SERVER_MENU1, DEPLOY_STATION);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (!utils.isNestedWithin(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        if (isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        if (item != menu_info_types.SERVER_MENU1)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id station = guild_space_station.getStationForControlDevice(self);
        if (isIdValid(station) && exists(station))
        {
            guild_space_station.recallStation(player, self, station);
        }
        else
        {
            guild_space_station.deployFromDatapad(player, self);
        }
        return SCRIPT_CONTINUE;
    }
}
