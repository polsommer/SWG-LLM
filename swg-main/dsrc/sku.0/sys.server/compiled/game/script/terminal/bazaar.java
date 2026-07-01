package script.terminal;

import script.menu_info;
import script.menu_info_types;
import script.modifiable_int;
import script.obj_id;
import script.string_id;
import script.library.vendor_lib;

public class bazaar extends script.terminal.base.base_terminal
{
    public bazaar()
    {
    }
    public static final string_id SID_BAZAAR_OPTIONS = new string_id("terminal_ui", "bazaar_options");
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        return super.OnObjectMenuRequest(self, player, mi);
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item == menu_info_types.ITEM_USE)
        {
            vendor_lib.showShortageBoostPrompt(self, player);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnRequestAuctionFee(obj_id self, obj_id who, obj_id location, obj_id item, boolean premium, modifiable_int amount) throws InterruptedException
    {
        int baseFee = premium ? 100 : 20;
        int adjustedFee = vendor_lib.applyEconomyAuctionFeeTuning(self, item, who, baseFee);
        amount.set(adjustedFee);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setOwner(self, self);
        attachScript(self, "planet_map.map_loc_attach");
        return super.OnInitialize(self);
    }
}
