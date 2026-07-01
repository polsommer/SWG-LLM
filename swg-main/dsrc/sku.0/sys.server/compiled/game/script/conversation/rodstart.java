package script.conversation;

import script.library.*;
import script.obj_id;
import script.prose_package;
import script.string_id;

public class rodstart extends script.conversation.base.conversation_base
{
    public String conversation = "conversation.rodstart";
    public String c_stringFile = "conversation/rodstart";
    
    public rodstart()
    {
        super.scriptName = "rodstart";
        super.conversation = conversation;
        super.c_stringFile = c_stringFile;
    }
    private boolean rodstart_condition_axkva_min_intro_01(obj_id player) throws InterruptedException
    {
        return groundquests.isTaskActive(player, "axkva_min_intro", "axkva_min_intro_01");
    }
    private boolean rodstart_condition_axkva_min_intro_02(obj_id player) throws InterruptedException
    {
        return groundquests.isTaskActive(player, "axkva_min_intro", "axkva_min_intro_02");
    }
    private boolean rodstart_condition_axkva_min_intro_04(obj_id player) throws InterruptedException
    {
        return groundquests.isTaskActive(player, "axkva_min_intro", "axkva_min_intro_04");
    }
    private boolean rodstart_condition_axkva_min_intro_06(obj_id player) throws InterruptedException
    {
        return groundquests.isTaskActive(player, "axkva_min_intro", "axkva_min_intro_06");
    }
    private boolean rodstart_condition_notCompletedTokenBox(obj_id player) throws InterruptedException
    {
        return !groundquests.isQuestActiveOrComplete(player, "rodstart");
    }
    private void rodstart_action_axkva_min_intro_02_signal(obj_id player) throws InterruptedException
    {
        groundquests.sendSignal(player, "axkva_min_intro_02");
    }
    private void rodstart_action_axkva_min_intro_04_signal(obj_id player) throws InterruptedException
    {
        groundquests.sendSignal(player, "axkva_min_intro_04");
    }
    private void rodstart_action_axkva_min_intro_06_signal(obj_id player) throws InterruptedException
    {
        String axkvaMinShardsString = "item_axkva_min_shards_04_01";
        obj_id inv = getObjectInSlot(player, "inventory");
        if (isIdValid(inv))
        {
            obj_id axkvaMinShard = static_item.createNewItemFunction(axkvaMinShardsString, inv);
            prose_package pp = prose.getPackage(new string_id("quest/ground/system_message", "placed_in_inventory_plural"), player, player);
            prose.setTO(pp, getNameStringId(axkvaMinShard));
            sendSystemMessageProse(player, pp);
        }
    }
    private void rodstart_action_giveTokenBoxQuest(obj_id player) throws InterruptedException
    {
        groundquests.grantQuest(player, "rodstart");
    }
    private int rodstart_handleBranch1(obj_id player, string_id response) throws InterruptedException
    {
        if (response.equals("s_25"))
        {
            utils.removeScriptVar(player, conversation + ".branchId");
            npcEndConversationWithMessage(player, new string_id(c_stringFile, "s_27"));
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_DEFAULT;
    }
    private int rodstart_handleBranch3(obj_id player, string_id response) throws InterruptedException
    {
        if (response.equals("s_32"))
        {
            return craft_response(new String[] {"s_34", "s_36"}, 4, player);
        }
        return SCRIPT_DEFAULT;
    }
    private int rodstart_handleBranch4(obj_id player, string_id response) throws InterruptedException
    {
        if (response.equals("s_36"))
        {
            return craft_response(new String[] {"s_38", "s_40"}, 5, player);
        }
        return SCRIPT_DEFAULT;
    }
    private int rodstart_handleBranch5(obj_id player, string_id response) throws InterruptedException
    {
        if (response.equals("s_40"))
        {
            rodstart_action_axkva_min_intro_04_signal(player);
            utils.removeScriptVar(player, conversation + ".branchId");
            npcEndConversationWithMessage(player, new string_id(c_stringFile, "s_42"));
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_DEFAULT;
    }
    private int rodstart_handleBranch7(obj_id player, string_id response) throws InterruptedException
    {
        if (response.equals("s_26"))
        {
            rodstart_action_giveTokenBoxQuest(player);
            utils.removeScriptVar(player, conversation + ".branchId");
            npcEndConversationWithMessage(player, new string_id(c_stringFile, "s_39"));
            return SCRIPT_CONTINUE;
        }
        else if (response.equals("s_41"))
        {
            utils.removeScriptVar(player, conversation + ".branchId");
            npcEndConversationWithMessage(player, new string_id(c_stringFile, "s_43"));
            return SCRIPT_CONTINUE;
        }
        else if (response.equals("s_35"))
        {
            return craft_response(new String[] {"s_29", "s_31"}, 11, player);
        }
        else if (response.equals("s_37"))
        {
            utils.removeScriptVar(player, conversation + ".branchId");
            npcEndConversationWithMessage(player, new string_id(c_stringFile, "s_30"));
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_DEFAULT;
    }
    private int rodstart_handleBranch10(obj_id player, string_id response) throws InterruptedException
    {
        if (response.equals("s_28"))
        {
            return craft_response(new String[] {"s_29", "s_31"}, 11, player);
        }
        else if (response.equals("s_24"))
        {
            utils.removeScriptVar(player, conversation + ".branchId");
            npcEndConversationWithMessage(player, new string_id(c_stringFile, "s_30"));
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_DEFAULT;
    }
    private int rodstart_handleBranch11(obj_id player, obj_id npc, string_id response) throws InterruptedException
    {
        if (response.equals("s_31"))
        {
            doAnimationAction(npc, "rub_chin_thoughtful");
            rodstart_action_axkva_min_intro_02_signal(player);
            utils.removeScriptVar(player, conversation + ".branchId");
            npcEndConversationWithMessage(player, new string_id(c_stringFile, "s_33"));
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_DEFAULT;
    }
    public int OnStartNpcConversation(obj_id self, obj_id player) throws InterruptedException
    {
        if (ai_lib.isInCombat(self) || ai_lib.isInCombat(player))
        {
            return SCRIPT_OVERRIDE;
        }
        faceTo(self, player);
        if (rodstart_condition_axkva_min_intro_06(player))
        {
            rodstart_action_axkva_min_intro_06_signal(player);
            return craft_repeater(new String[] {"s_9", "s_25"}, 1, player, self);
        }
        else if (rodstart_condition_axkva_min_intro_04(player))
        {
            doAnimationAction(self, "explain");
            return craft_repeater(new String[] {"s_10", "s_32"}, 3, player, self);
        }
        else if (rodstart_condition_notCompletedTokenBox(player))
        {
            if (rodstart_condition_axkva_min_intro_01(player)){
                return craft_repeater(new String[] {"s_23", "s_26", "s_41", "s_37"}, 7, player, self);
            }
            else if (rodstart_condition_axkva_min_intro_02(player)){
                return craft_repeater(new String[] {"s_23", "s_26", "s_41", "s_35"}, 7, player, self);
            }
            else {
                return craft_repeater(new String[]{"s_23", "s_26", "s_41"}, 7, player, self);
            }
        }
        else{
            if (rodstart_condition_axkva_min_intro_01(player)){
                return craft_repeater(new String[] {"s_18", "s_24"}, 10, player, self);

            }
            else if (rodstart_condition_axkva_min_intro_02(player)){
                return craft_repeater(new String[] {"s_18", "s_28"}, 10, player, self);
            }
        }
                return craft_repeater(new String[]{"s_23", "s_26", "s_41"}, 7, player, self);
    }
    public int OnNpcConversationResponse(obj_id self, String conversationId, obj_id player, string_id response) throws InterruptedException
    {
        if (!conversationId.equals("rodstart"))
        {
            return SCRIPT_CONTINUE;
        }
        int branchId = utils.getIntScriptVar(player, conversation + ".branchId");
        if (branchId == 1 && rodstart_handleBranch1(player, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        else if (branchId == 3 && rodstart_handleBranch3(player, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        else if (branchId == 4 && rodstart_handleBranch4(player, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        else if (branchId == 5 && rodstart_handleBranch5(player, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        else if (branchId == 7 && rodstart_handleBranch7(player, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        else if (branchId == 10 && rodstart_handleBranch10(player, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        else if (branchId == 11 && rodstart_handleBranch11(player, self, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        chat.chat(self, "Error:  Fell through all branches and responses for OnNpcConversationResponse.");
        utils.removeScriptVar(player, conversation + ".branchId");
        return SCRIPT_CONTINUE;
    }
}
