package script.theme_park.heroic.lost_star_destroyer;

import script.library.instance;
import script.library.sui;
import script.*;

public class exit_terminal extends script.base_script
{
    public exit_terminal()
    {
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        menu_info_data mid = mi.getMenuItemByType(menu_info_types.ITEM_USE);
        if (mid == null)
        {
            mi.addRootMenu(menu_info_types.ITEM_USE, new string_id("ui_radial", "item_use"));
        }
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item == menu_info_types.ITEM_USE)
        {
            sui.msgbox(self, player, "Leave the Lost Star Destroyer instance?", sui.YES_NO, "msgDungeonEjectConfirmed");
        }
        return SCRIPT_CONTINUE;
    }
    public int msgDungeonEjectConfirmed(obj_id self, dictionary params) throws InterruptedException
    {
        String button = params.getString("buttonPressed");
        obj_id player = params.getObjId("player");
        if (button.equals("Cancel"))
        {
            return SCRIPT_CONTINUE;
        }
        instance.requestExitPlayer("heroic_lost_star_destroyer", player);
        return SCRIPT_CONTINUE;
    }
}
