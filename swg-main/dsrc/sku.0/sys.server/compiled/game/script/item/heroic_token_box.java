package script.item;

import script.dictionary;
import script.library.static_item;
import script.library.sui;
import script.library.tokenmanager;
import script.library.utils;
import script.menu_info;
import script.menu_info_data;
import script.menu_info_types;
import script.obj_id;
import script.string_id;

import java.util.ArrayList;

public class heroic_token_box extends script.base_script
{
    private static final String TOKENS_HELD_VAR = "item.set.tokens_held";
    private static final String SCRIPT_VAR_BASE = "heroicTokenBox";
    private static final String SCRIPT_VAR_CHOICES = SCRIPT_VAR_BASE + ".choices";
    private static final String SCRIPT_VAR_SELECTED = SCRIPT_VAR_BASE + ".selected";
    private static final String SCRIPT_VAR_MAX = SCRIPT_VAR_BASE + ".max";
    private static final String SCRIPT_VAR_BOX = SCRIPT_VAR_BASE + ".box";

    public heroic_token_box()
    {
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!isIdValid(player) || !exists(player))
        {
            return SCRIPT_CONTINUE;
        }
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
        if (!isIdValid(player) || !exists(player) || isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        if (getTopMostContainer(self) != player)
        {
            sendSystemMessage(player, "You must keep the Box of Achievements in your inventory to access its contents.", null);
            return SCRIPT_CONTINUE;
        }
        showWithdrawMenu(self, player);
        return SCRIPT_CONTINUE;
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, TOKENS_HELD_VAR))
        {
            tokenmanager.initializeBox(self);
        }
        else
        {
            tokenmanager.verifyBox(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, TOKENS_HELD_VAR))
        {
            tokenmanager.initializeBox(self);
        }
        else
        {
            tokenmanager.verifyBox(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnGetAttributes(obj_id self, obj_id player, String[] names, String[] attribs) throws InterruptedException
    {
        int free = getFirstFreeIndex(names);
        if (free == -1)
        {
            return SCRIPT_CONTINUE;
        }
        if (!hasObjVar(self, TOKENS_HELD_VAR))
        {
            return SCRIPT_CONTINUE;
        }
        int[] tokenTypes = getIntArrayObjVar(self, TOKENS_HELD_VAR);
        if (tokenTypes.length == tokenmanager.HEROIC_TOKENS.length)
        {
            for (int i = 0; i < tokenTypes.length; i++)
            {
                if (free >= names.length)
                {
                    break;
                }
                names[free] = utils.packStringId(new string_id("static_item_n", tokenmanager.HEROIC_TOKENS[i]));
                attribs[free++] = Integer.toString(tokenTypes[i]);
            }
        }
        return SCRIPT_CONTINUE;
    }

    public int OnDestroy(obj_id self) throws InterruptedException
    {
        obj_id whoDat = getTopMostContainer(self);
        sendSystemMessage(whoDat, new string_id("spam", "can_not_destroy"));
        return SCRIPT_OVERRIDE;
    }

    private void showWithdrawMenu(obj_id self, obj_id player) throws InterruptedException
    {
        cleanupWithdrawState(player);
        tokenmanager.verifyBox(self);
        int[] storedTokens = getIntArrayObjVar(self, TOKENS_HELD_VAR);
        if (storedTokens == null || storedTokens.length == 0)
        {
            sendSystemMessage(player, "The Box of Achievements is not initialized.", null);
            return;
        }
        ArrayList<String> entries = new ArrayList<String>();
        ArrayList<Integer> indices = new ArrayList<Integer>();
        for (int i = 0; i < storedTokens.length; i++)
        {
            int count = storedTokens[i];
            if (count <= 0)
            {
                continue;
            }
            String displayName = getString(new string_id("static_item_n", tokenmanager.HEROIC_TOKENS[i]));
            if (displayName == null || displayName.equals(""))
            {
                displayName = tokenmanager.HEROIC_TOKENS[i];
            }
            entries.add("[" + count + "] " + displayName);
            indices.add(i);
        }
        if (entries.isEmpty())
        {
            sendSystemMessage(player, "Your Box of Achievements does not contain any stored tokens.", null);
            return;
        }
        String[] options = entries.toArray(new String[0]);
        int[] mapping = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++)
        {
            mapping[i] = indices.get(i);
        }
        utils.setScriptVar(player, SCRIPT_VAR_CHOICES, mapping);
        utils.setScriptVar(player, SCRIPT_VAR_BOX, self);
        sui.listbox(self, player, "Select the type of token you wish to withdraw.", sui.OK_CANCEL, "Box of Achievements", options, "handleWithdrawSelection", true, false);
    }

    public int handleWithdrawSelection(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id player = sui.getPlayerId(params);
        if (!isIdValid(player) || !exists(player))
        {
            return SCRIPT_CONTINUE;
        }
        if (sui.getIntButtonPressed(params) != sui.BP_OK)
        {
            cleanupWithdrawState(player);
            return SCRIPT_CONTINUE;
        }
        int row = sui.getListboxSelectedRow(params);
        if (row < 0)
        {
            cleanupWithdrawState(player);
            return SCRIPT_CONTINUE;
        }
        int[] mapping = utils.getIntArrayScriptVar(player, SCRIPT_VAR_CHOICES);
        if (mapping == null || row >= mapping.length || row < 0)
        {
            cleanupWithdrawState(player);
            sendSystemMessage(player, "Unable to determine the selected token type. Please try again.", null);
            return SCRIPT_CONTINUE;
        }
        int tokenIndex = mapping[row];
        int[] storedTokens = getIntArrayObjVar(self, TOKENS_HELD_VAR);
        if (storedTokens == null || tokenIndex < 0 || tokenIndex >= storedTokens.length)
        {
            cleanupWithdrawState(player);
            sendSystemMessage(player, "The stored token information could not be accessed.", null);
            return SCRIPT_CONTINUE;
        }
        int available = storedTokens[tokenIndex];
        if (available <= 0)
        {
            cleanupWithdrawState(player);
            sendSystemMessage(player, "There are no tokens of that type stored in the box.", null);
            return SCRIPT_CONTINUE;
        }
        utils.setScriptVar(player, SCRIPT_VAR_SELECTED, tokenIndex);
        utils.setScriptVar(player, SCRIPT_VAR_MAX, available);
        utils.setScriptVar(player, SCRIPT_VAR_BOX, self);
        promptForWithdrawAmount(self, player, tokenIndex, available);
        return SCRIPT_CONTINUE;
    }

    public int handleWithdrawAmount(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id player = sui.getPlayerId(params);
        if (!isIdValid(player) || !exists(player))
        {
            cleanupWithdrawState(player);
            return SCRIPT_CONTINUE;
        }
        if (sui.getIntButtonPressed(params) != sui.BP_OK)
        {
            cleanupWithdrawState(player);
            return SCRIPT_CONTINUE;
        }
        if (!utils.hasScriptVar(player, SCRIPT_VAR_SELECTED) || !utils.hasScriptVar(player, SCRIPT_VAR_MAX) || !utils.hasScriptVar(player, SCRIPT_VAR_BOX))
        {
            cleanupWithdrawState(player);
            sendSystemMessage(player, "The withdrawal request could not be processed. Please try again.", null);
            return SCRIPT_CONTINUE;
        }
        int tokenIndex = utils.getIntScriptVar(player, SCRIPT_VAR_SELECTED);
        int max = utils.getIntScriptVar(player, SCRIPT_VAR_MAX);
        String amountText = sui.getInputBoxText(params);
        if (amountText == null || amountText.equals(""))
        {
            sendSystemMessage(player, "Please enter the number of tokens to withdraw.", null);
            promptForWithdrawAmount(self, player, tokenIndex, max);
            return SCRIPT_CONTINUE;
        }
        int amount;
        try
        {
            amount = Integer.parseInt(amountText.trim());
        }
        catch (NumberFormatException err)
        {
            sendSystemMessage(player, "Please enter a valid number of tokens to withdraw.", null);
            promptForWithdrawAmount(self, player, tokenIndex, max);
            return SCRIPT_CONTINUE;
        }
        if (amount <= 0)
        {
            sendSystemMessage(player, "You must withdraw at least one token.", null);
            promptForWithdrawAmount(self, player, tokenIndex, max);
            return SCRIPT_CONTINUE;
        }
        if (amount > max)
        {
            sendSystemMessage(player, "You do not have that many tokens stored.", null);
            promptForWithdrawAmount(self, player, tokenIndex, max);
            return SCRIPT_CONTINUE;
        }
        obj_id storedBox = utils.getObjIdScriptVar(player, SCRIPT_VAR_BOX);
        if (!isIdValid(storedBox) || storedBox != self)
        {
            sendSystemMessage(player, "The Box of Achievements you interacted with is no longer available.", null);
            cleanupWithdrawState(player);
            return SCRIPT_CONTINUE;
        }
        if (withdrawTokens(self, player, tokenIndex, amount))
        {
            cleanupWithdrawState(player);
            showWithdrawMenu(self, player);
        }
        else
        {
            cleanupWithdrawState(player);
        }
        return SCRIPT_CONTINUE;
    }

    private void promptForWithdrawAmount(obj_id self, obj_id player, int tokenIndex, int max) throws InterruptedException
    {
        String tokenName = getString(new string_id("static_item_n", tokenmanager.HEROIC_TOKENS[tokenIndex]));
        if (tokenName == null || tokenName.equals(""))
        {
            tokenName = tokenmanager.HEROIC_TOKENS[tokenIndex];
        }
        String prompt = "Enter the number of " + tokenName + " to withdraw (1-" + max + "):";
        sui.inputbox(self, player, prompt, sui.OK_CANCEL, "Withdraw Tokens", sui.INPUT_NORMAL, null, "handleWithdrawAmount", null);
    }

    private boolean withdrawTokens(obj_id self, obj_id player, int tokenIndex, int amount) throws InterruptedException
    {
        int[] storedTokens = getIntArrayObjVar(self, TOKENS_HELD_VAR);
        if (storedTokens == null || tokenIndex < 0 || tokenIndex >= storedTokens.length)
        {
            sendSystemMessage(player, "The Box of Achievements failed to access the stored tokens.", null);
            return false;
        }
        if (storedTokens[tokenIndex] < amount)
        {
            sendSystemMessage(player, "There are not enough tokens of that type stored.", null);
            return false;
        }
        if (!grantTokensToPlayer(player, tokenIndex, amount))
        {
            sendSystemMessage(player, "Unable to move the tokens to your inventory. Ensure you have enough space and try again.", null);
            return false;
        }
        storedTokens[tokenIndex] -= amount;
        if (storedTokens[tokenIndex] < 0)
        {
            storedTokens[tokenIndex] = 0;
        }
        setObjVar(self, TOKENS_HELD_VAR, storedTokens);
        String tokenName = getString(new string_id("static_item_n", tokenmanager.HEROIC_TOKENS[tokenIndex]));
        if (tokenName == null || tokenName.equals(""))
        {
            tokenName = tokenmanager.HEROIC_TOKENS[tokenIndex];
        }
        sendSystemMessage(player, "You withdraw " + amount + " " + tokenName + (amount == 1 ? "" : "s") + " from the Box of Achievements.", null);
        return true;
    }

    private boolean grantTokensToPlayer(obj_id player, int tokenIndex, int amount) throws InterruptedException
    {
        obj_id inventory = utils.getInventoryContainer(player);
        if (!isIdValid(inventory))
        {
            return false;
        }
        obj_id[] contents = getContents(inventory);
        if (contents != null)
        {
            for (obj_id item : contents)
            {
                if (!isIdValid(item))
                {
                    continue;
                }
                String itemName = getStaticItemName(item);
                if (itemName != null && itemName.equals(tokenmanager.HEROIC_TOKENS[tokenIndex]))
                {
                    setCount(item, getCount(item) + amount);
                    return true;
                }
            }
        }
        obj_id newToken = static_item.createNewItemFunction(tokenmanager.HEROIC_TOKENS[tokenIndex], inventory);
        if (!isIdValid(newToken))
        {
            return false;
        }
        if (amount > 1)
        {
            setCount(newToken, amount);
        }
        return true;
    }

    private void cleanupWithdrawState(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        utils.removeScriptVar(player, SCRIPT_VAR_CHOICES);
        utils.removeScriptVar(player, SCRIPT_VAR_SELECTED);
        utils.removeScriptVar(player, SCRIPT_VAR_MAX);
        utils.removeScriptVar(player, SCRIPT_VAR_BOX);
    }
}
