package script.systems.gcw;

import script.dictionary;
import script.library.force_rank;
import script.library.money;
import script.library.sui;
import script.library.utils;
import script.menu_info;
import script.menu_info_types;
import script.obj_id;
import script.string_id;

public class terminal_frs_shop extends script.base_script
{
    public terminal_frs_shop()
    {
    }
    private static final String DATATABLE_SHOP = "datatables/pvp/jedi_enclave_shop_inventory.iff";
    private static final String SCRIPT_VAR_SUI_PID = "force_rank.shop.sui";
    private static final String SCRIPT_VAR_ITEM_ROWS = "force_rank.shop.rows";
    private static final String SCRIPT_VAR_ENCLAVE = "force_rank.shop.enclave";
    private static final int MENU_OPEN_SHOP = menu_info_types.SERVER_MENU1;

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        obj_id enclave = getTopMostContainer(self);
        if (!isIdValid(enclave) || !hasScript(enclave, force_rank.SCRIPT_ENCLAVE_CONTROLLER))
        {
            destroyObject(self);
            return SCRIPT_CONTINUE;
        }
        utils.setScriptVar(enclave, force_rank.SCRIPT_VAR_SHOP_TERMINAL, self);
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id enclave = getTopMostContainer(self);
        if (!isIdValid(enclave) || !hasScript(enclave, force_rank.SCRIPT_ENCLAVE_CONTROLLER))
        {
            return SCRIPT_CONTINUE;
        }
        mi.addRootMenu(MENU_OPEN_SHOP, new string_id(force_rank.STF_FILE, "vote_status"));
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item != MENU_OPEN_SHOP)
        {
            return SCRIPT_CONTINUE;
        }
        showShop(self, player);
        return SCRIPT_CONTINUE;
    }

    public int handleEnclaveShopSelection(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id player = params.getObjId("player");
        if (!isIdValid(player) || sui.getIntButtonPressed(params) == sui.BP_CANCEL)
        {
            clearPlayerShopState(player);
            return SCRIPT_CONTINUE;
        }
        int idx = sui.getListboxSelectedRow(params);
        int[] rows = utils.getIntArrayScriptVar(player, SCRIPT_VAR_ITEM_ROWS);
        obj_id enclave = utils.getObjIdScriptVar(player, SCRIPT_VAR_ENCLAVE);
        clearPlayerShopState(player);
        if (rows == null || idx < 0 || idx >= rows.length)
        {
            return SCRIPT_CONTINUE;
        }
        purchaseRow(self, player, enclave, rows[idx]);
        return SCRIPT_CONTINUE;
    }

    private void showShop(obj_id self, obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        obj_id enclave = getTopMostContainer(self);
        if (!isIdValid(enclave) || !hasScript(enclave, force_rank.SCRIPT_ENCLAVE_CONTROLLER))
        {
            return;
        }
        if (!force_rank.isPlayersEnclave(enclave, player))
        {
            sendSystemMessageTestingOnly(player, "You are not authorized to use this enclave terminal.");
            return;
        }
        int council = force_rank.getCouncilAffiliation(enclave);
        int playerRank = force_rank.getForceRank(player);
        int playerLevel = getLevel(player);
        int numRows = dataTableGetNumRows(DATATABLE_SHOP);
        if (numRows < 1)
        {
            sendSystemMessageTestingOnly(player, "The enclave inventory is unavailable.");
            return;
        }
        String[] entries = new String[numRows];
        int[] rowIds = new int[numRows];
        int count = 0;
        for (int i = 0; i < numRows; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE_SHOP, i);
            if (row == null)
            {
                continue;
            }
            if (row.getInt("Council") != council)
            {
                continue;
            }
            int requiredRank = row.getInt("RequiredRank");
            int requiredLevel = row.getInt("RequiredLevel");
            if (playerRank < requiredRank || playerLevel < requiredLevel)
            {
                continue;
            }
            String itemName = row.getString("DisplayName");
            if (itemName == null || itemName.length() < 1)
            {
                itemName = row.getString("ItemTemplate");
            }
            int cost = row.getInt("Cost");
            entries[count] = itemName + " [Rank " + requiredRank + ", Level " + requiredLevel + ", " + cost + " cr]";
            rowIds[count] = i;
            count++;
        }
        if (count < 1)
        {
            sendSystemMessageTestingOnly(player, "You do not yet meet the rank and level requirements for enclave inventory.");
            return;
        }
        if (utils.hasScriptVar(player, SCRIPT_VAR_SUI_PID))
        {
            forceCloseSUIPage(utils.getIntScriptVar(player, SCRIPT_VAR_SUI_PID));
        }
        String[] filteredEntries = new String[count];
        int[] filteredRows = new int[count];
        System.arraycopy(entries, 0, filteredEntries, 0, count);
        System.arraycopy(rowIds, 0, filteredRows, 0, count);
        int pid = sui.listbox(self, player, "Select an item to purchase.", sui.OK_CANCEL, "Enclave Quartermaster", filteredEntries, "handleEnclaveShopSelection");
        utils.setScriptVar(player, SCRIPT_VAR_SUI_PID, pid);
        utils.setScriptVar(player, SCRIPT_VAR_ITEM_ROWS, filteredRows);
        utils.setScriptVar(player, SCRIPT_VAR_ENCLAVE, enclave);
    }

    private void purchaseRow(obj_id self, obj_id player, obj_id enclave, int rowIdx) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        if (!isIdValid(enclave) || !hasScript(enclave, force_rank.SCRIPT_ENCLAVE_CONTROLLER))
        {
            sendSystemMessageTestingOnly(player, "The enclave inventory is unavailable.");
            return;
        }
        if (!force_rank.isPlayersEnclave(enclave, player))
        {
            sendSystemMessageTestingOnly(player, "You are not authorized to use this enclave terminal.");
            return;
        }
        dictionary row = dataTableGetRow(DATATABLE_SHOP, rowIdx);
        if (row == null)
        {
            return;
        }
        int council = force_rank.getCouncilAffiliation(enclave);
        int requiredRank = row.getInt("RequiredRank");
        int requiredLevel = row.getInt("RequiredLevel");
        if (row.getInt("Council") != council || force_rank.getForceRank(player) < requiredRank || getLevel(player) < requiredLevel)
        {
            sendSystemMessageTestingOnly(player, "You no longer meet the requirements for that item.");
            return;
        }
        String itemTemplate = row.getString("ItemTemplate");
        int cost = row.getInt("Cost");
        if (itemTemplate == null || itemTemplate.length() < 1 || cost < 1)
        {
            sendSystemMessageTestingOnly(player, "That inventory entry is invalid.");
            return;
        }
        if (getBankBalance(player) < cost)
        {
            sendSystemMessageTestingOnly(player, "You do not have enough bank credits for this purchase.");
            return;
        }
        obj_id inventory = getObjectInSlot(player, "inventory");
        if (!isIdValid(inventory))
        {
            return;
        }
        if (!money.bankTo(player, money.ACCT_JEDI_DEATH, cost))
        {
            sendSystemMessageTestingOnly(player, "Unable to process payment.");
            return;
        }
        obj_id item = createObject(itemTemplate, inventory, "");
        if (!isIdValid(item))
        {
            money.bankTo(money.ACCT_JEDI_DEATH, player, cost);
            sendSystemMessageTestingOnly(player, "Your purchase could not be delivered, and your credits were refunded.");
            return;
        }
        String itemName = row.getString("DisplayName");
        if (itemName == null || itemName.length() < 1)
        {
            itemName = itemTemplate;
        }
        sendSystemMessageTestingOnly(player, "Purchased: " + itemName + ".");
    }

    private void clearPlayerShopState(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        utils.removeScriptVar(player, SCRIPT_VAR_SUI_PID);
        utils.removeScriptVar(player, SCRIPT_VAR_ITEM_ROWS);
        utils.removeScriptVar(player, SCRIPT_VAR_ENCLAVE);
    }
}
