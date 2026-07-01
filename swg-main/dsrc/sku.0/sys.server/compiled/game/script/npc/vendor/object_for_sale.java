package script.npc.vendor;

import script.library.*;
import script.menu_info_types;
import script.obj_id;
import script.prose_package;
import script.string_id;

public class object_for_sale extends script.base_script
{
    public object_for_sale()
    {
    }
    public static final String VENDOR_TOKEN_TYPE = "item.token.type";
    public static final string_id SID_INV_FULL = new string_id("spam", "npc_vendor_player_inv_full");
    public int OnAttach(obj_id self) throws InterruptedException
    {
        setObjVar(self, township.OBJECT_FOR_SALE_ON_VENDOR, true);
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item == menu_info_types.ITEM_PUBLIC_CONTAINER_USE1)
        {
            if (hasObjVar(self, "faction_recruiter.faction"))
            {
                String itemFactionName = getStringObjVar(self, "faction_recruiter.faction");
                int playerFaction = pvpGetAlignedFaction(player);
                String playerFactionName = factions.getFactionNameByHashCode(playerFaction);
                if (!itemFactionName.equals(playerFactionName))
                {
                    sendSystemMessage(player, new string_id("spam", "wrong_faction"));
                    return SCRIPT_OVERRIDE;
                }
            }
            if (!confirmInventory(self, player))
            {
                return SCRIPT_OVERRIDE;
            }
            if (confirmFunds(self, player))
            {
                processItemPurchase(self, player);
            }
            else 
            {
                sendSystemMessage(player, new string_id("spam", "buildabuff_nsf_buffee"));
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int OnGetAttributes(obj_id self, obj_id player, String[] names, String[] attribs) throws InterruptedException
    {
        if (names == null || attribs == null || names.length != attribs.length)
        {
            return SCRIPT_CONTINUE;
        }
        final int firstFreeIndex = getFirstFreeIndex(names);
        if (firstFreeIndex >= 0 && firstFreeIndex < names.length)
        {
            names[firstFreeIndex] = utils.packStringId(new string_id("set_bonus", "vendor_cost"));
            attribs[firstFreeIndex] = createPureConcatenatedJoy(self);
        }
        return SCRIPT_CONTINUE;
    }
    public String createPureConcatenatedJoy(obj_id self) throws InterruptedException
    {
        String pureConcatenatedJoy = getString(new string_id("set_bonus", "vendor_sale_object_justify_line"));
        int creditCost = 0;
        int tokenArrayLength = 0;
        creditCost = getIntObjVar(self, "item.object_for_sale.cash_cost");
        int[] tokenCost = getIntArrayObjVar(self, "item.object_for_sale.token_cost");
        if (tokenCost == null)
        {
            tokenCost = new int[0];
        }
        String vendorTokenList = hasObjVar(self, VENDOR_TOKEN_TYPE) ? getStringObjVar(self, VENDOR_TOKEN_TYPE) : null;
        String[] tokenTypes = tokenmanager.getTokenTypes(vendorTokenList);

        tokenArrayLength = Math.min(tokenCost.length, tokenTypes.length);

        for (int i = 0; i < tokenArrayLength; i++)
        {
            if (tokenCost[i] > 0)
            {
                pureConcatenatedJoy += "[" + tokenCost[i] + "] " + tokenmanager.getTokenDisplayName(tokenTypes[i]) + "\n";
            }
        }
        if (creditCost > 0)
        {
            pureConcatenatedJoy += creditCost + getString(new string_id("set_bonus", "vendor_credits"));
        }
        return pureConcatenatedJoy;
    }
    public boolean confirmInventory(obj_id self, obj_id player) throws InterruptedException
    {
        obj_id pInv = utils.getInventoryContainer(player);
        if (!isValidId(pInv) || !exists(pInv))
        {
            return false;
        }
        if (getVolumeFree(pInv) <= 0)
        {
            sendSystemMessage(player, SID_INV_FULL);
            return false;
        }
        return true;
    }

    public boolean confirmFunds(obj_id self, obj_id player) throws InterruptedException {
        int creditCost = getIntObjVar(self, "item.object_for_sale.cash_cost");
        int[] tokenCosts = getIntArrayObjVar(self, "item.object_for_sale.token_cost");
        if (tokenCosts == null)
        {
            tokenCosts = new int[0];
        }

        // do we have enough credits for the item?
        boolean hasTheCredits = (creditCost < 1 || money.hasFunds(player, money.MT_TOTAL, creditCost));

        // bail if we don't have enough credits - doesn't even matter if we have enough tokens or not.
        if(!hasTheCredits) return false;

        String tokenList = hasObjVar(self, VENDOR_TOKEN_TYPE) ? getStringObjVar(self, VENDOR_TOKEN_TYPE) : null;
        String[] tokenTypes = tokenmanager.getTokenTypes(tokenList);
        int tokenSlots = Math.min(tokenCosts.length, tokenTypes.length);

        boolean requiresTokens = false;
        for (int i = 0; i < tokenSlots; i++)
        {
            if (tokenCosts[i] > 0)
            {
                requiresTokens = true;
                if (tokenmanager.getTokenTotal(player, tokenTypes[i]) < tokenCosts[i])
                {
                    return false;
                }
            }
        }

        return !requiresTokens || tokenSlots > 0;
    }
    public void processItemPurchase(obj_id self, obj_id player) throws InterruptedException
    {
        obj_id inventory = utils.getInventoryContainer(player);
        int creditCost = getIntObjVar(self, "item.object_for_sale.cash_cost");
        int[] tokenCostForReals = getIntArrayObjVar(self, "item.object_for_sale.token_cost");
        if (tokenCostForReals == null)
        {
            tokenCostForReals = new int[0];
        }
        String tokenList = hasObjVar(self, VENDOR_TOKEN_TYPE) ? getStringObjVar(self, VENDOR_TOKEN_TYPE) : null;
        String[] tokenTypes = tokenmanager.getTokenTypes(tokenList);
        int tokenSlots = Math.min(tokenCostForReals.length, tokenTypes.length);

        if (creditCost > 0 && !money.hasFunds(player, money.MT_TOTAL, creditCost))
        {
            sendSystemMessage(player, new string_id("spam", "buildabuff_nsf_buffee"));
            return;
        }

        for (int i = 0; i < tokenSlots; i++)
        {
            if (tokenCostForReals[i] > 0 && tokenmanager.getTokenTotal(player, tokenTypes[i]) < tokenCostForReals[i])
            {
                sendSystemMessage(player, new string_id("spam", "buildabuff_nsf_buffee"));
                return;
            }
        }
        obj_id purchasedItem = obj_id.NULL_ID;
        String myName = "";
        if (static_item.isStaticItem(self))
        {
            myName = static_item.getStaticItemName(self);
            purchasedItem = static_item.createNewItemFunction(myName, inventory);
        }
        else 
        {
            myName = getTemplateName(self);
            purchasedItem = createObjectOverloaded(myName, inventory);
        }
        if (hasScript(purchasedItem, "npc.faction_recruiter.biolink_item"))
        {
            setBioLink(purchasedItem, player);
        }
        CustomerServiceLog("Heroic-Token: ", "player " + getFirstName(player) + "(" + player + ") purchased item " + myName + "(" + purchasedItem + ")");
        if (!exists(purchasedItem))
        {
            sendSystemMessage(player, new string_id("set_bonus", "vendor_cant_purchase"));
            return;
        }
        String readableName = getString(parseNameToStringId(getName(self), self));
        prose_package pp = new prose_package();
        pp = prose.setStringId(pp, new string_id("set_bonus", "vendor_item_purchased"));
        pp = prose.setTT(pp, readableName);
        sendSystemMessageProse(player, pp);
        for (int i = 0; i < tokenSlots; i++)
        {
            if (tokenCostForReals[i] > 0 && !tokenmanager.purchaseTokenItem(player, tokenCostForReals[i], tokenTypes[i]))
            {
                destroyObject(purchasedItem);
                sendSystemMessage(player, new string_id("set_bonus", "vendor_cant_purchase"));
                return;
            }
        }

        if (creditCost >= 1)
        {
            obj_id containedBy = getContainedBy(getContainedBy(getContainedBy(self)));
            if (!money.requestPayment(player, containedBy, creditCost, "no_handler", null, false))
            {
                sendSystemMessage(player, new string_id("set_bonus", "vendor_cant_purchase"));
                return;
            }
        }
        return;
    }
    public string_id parseNameToStringId(String itemName, obj_id item) throws InterruptedException
    {
        String[] parsedString = split(itemName, ':');
        string_id itemNameSID;
        if (static_item.isStaticItem(item))
        {
            itemNameSID = static_item.getStaticItemStringIdName(item);
        }
        else if (parsedString.length > 1)
        {
            String stfFile = parsedString[0];
            String reference = parsedString[1];
            itemNameSID = new string_id(stfFile, reference);
        }
        else 
        {
            String stfFile = parsedString[0];
            itemNameSID = new string_id(stfFile, " ");
        }
        return itemNameSID;
    }
}
