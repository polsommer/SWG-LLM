package script.library;

import script.dictionary;
import script.location;
import script.obj_id;
import script.prose_package;
import script.string_id;

import java.util.Vector;

public class vendor_lib extends script.base_script
{
    public vendor_lib()
    {
    }
    public static final String VAR_MAINTENANCE_RATE = "vendor.maintanence.rate";
    public static final String VAR_DECAY_RATE = "vendor.maintanence.decay";
    public static final String VAR_LAST_MAINTANENCE = "vendor.last_maintanence";
    public static final String VAR_CONDITION = "vendor.condition";
    public static final String VAR_MAX_CONDITION = "vendor.max_condition";
    public static final String VAR_PACKUP_VERSION = "vendor.packup_version";
    public static final String VAR_ECONOMY_STATE = "economy.sharedState";
    public static final String SPECIAL_VENDOR_IDENTIFIER = "vendor.special_vendor";
    public static final int MAINTENANCE_HEARTBEAT = 3600;
    public static final int BASE_MAINT_RATE = 1;
    public static final int BASE_DECAY_RATE = 2;
    public static final boolean LOGGING_ON = true;
    public static final String LOGGING_CATEGORY = "greeter";
    public static final String STATIC_ITEM_DEED_NAME = "static_item_deed_name";
    public static final String OWNER_OBJVAR = "object.owner";
    public static final String AI_LISTING_SOURCE = "adaptive_npc_listing";
    public static final String AI_LISTING_FLAG = "npc.simulation.listing.trusted";
    public static final int AI_LISTING_MIN_PRICE = 1000;
    public static final int AI_LISTING_MAX_PRICE = 900000;
    public static final String AI_LISTING_TRACK_ROOT = "vendor.aiListing";
    public static final String AI_LISTING_TRACK_IDS = AI_LISTING_TRACK_ROOT + ".seeded.ids";
    public static final String AI_LISTING_TRACK_EXPIRES = AI_LISTING_TRACK_ROOT + ".seeded.expires";
    public static final String AI_LISTING_TRACK_DAILY_ROOT = AI_LISTING_TRACK_ROOT + ".daily";
    public static final String AI_LISTING_TRACK_MEDIAN_ROOT = AI_LISTING_TRACK_ROOT + ".median";
    public static final String AI_LISTING_TRACK_PRICE_HISTORY_ROOT = AI_LISTING_TRACK_ROOT + ".history";
    public static final int AI_LISTING_DEFAULT_DAILY_CAP = 3;
    public static final int AI_LISTING_MAX_HISTORY = 15;
    public static final int AI_LISTING_DEFAULT_STALE_SECONDS = 21600;
    public static final String CHILD_GREETER_NONVENDOR_ID_OBJVAR = "object.child_greeter_nonvendor_objid";
    public static final String CNTRLR_GREETER_NONVENDOR_ID_OBJVAR = "object.terminal_greeter_nonvendor_objid";
    public static final String LASTKNOWN_GREETER_TERMINAL_LOC = "object.terminal_last_location";
    public static final String NONVENDOR_VENDOR_SCRIPT = "terminal.nonvendor";
    public static final String NONVENDOR_VAR_PREFIX = "nonvendor";
    public static final String NONVENDOR_TYPE_PREFIX = "nonvendor_";
    public static final String NONTRADER_NONVENDOR_TYPE = NONVENDOR_VAR_PREFIX + ".give_nontrader";
    public static final String NONVENDOR_CREATURE_TYPE_SCRVAR = NONVENDOR_VAR_PREFIX + ".creature_type";
    public static final String NONVENDOR_NAMES_SCRVAR = NONVENDOR_VAR_PREFIX + ".unsplit_nonvendor_names";
    public static final String NONVENDOR_STRING_ID_SCRVAR = NONVENDOR_VAR_PREFIX + ".unsplit_nonvendor_string_ids";
    public static final String NONVENDOR_CREATURE_NAME_SCRVAR = NONVENDOR_VAR_PREFIX + ".selection_creature_name";
    public static final String NONVENDOR_CREATURE_TEMPLATE_SCRVAR = NONVENDOR_VAR_PREFIX + ".selection_nonvendor_template";
    public static final String NONVENDOR_CREATURE_TEMPLATE_LIST = NONVENDOR_VAR_PREFIX + ".selection_nonvendor_template_list";
    public static final String NONVENDOR_CREATURENAME_LIST_SCRVAR = NONVENDOR_VAR_PREFIX + ".selection_creature_name_list";
    public static final String NONVENDOR_SELECTION_SCRVAR = NONVENDOR_VAR_PREFIX + ".selection_string_id";
    public static final String NONVENDOR_CUSTOM_NAME_SCRVAR = NONVENDOR_VAR_PREFIX + ".creature_custom_name";
    public static final String NONVENDOR_APPEARANCE_LIST = NONVENDOR_VAR_PREFIX + ".appearance_list";
    public static final String TCG_OBJVAR_PREFIX = "tcg";
    public static final String GREETER_OBJVAR_TREE = TCG_OBJVAR_PREFIX + ".greeter";
    public static final String GREETER_TYPE_PREFIX = "greeter_";
    public static final String GREETER_VAR_PREFIX = "greeter_prefix";
    public static final String CREATING_GREETER = GREETER_VAR_PREFIX + ".creatingGreeter";
    public static final String GREETER_NOT_INIT_OBJVAR = GREETER_VAR_PREFIX + ".greeter_not_initialized";
    public static final String GREETER_INIT_OBJVAR = GREETER_VAR_PREFIX + ".greeter_initialized";
    public static final String GREETER_OWNER_OBJVAR = GREETER_VAR_PREFIX + ".greeter_owner";
    public static final String GREETER_TYPE_OBJVAR = GREETER_VAR_PREFIX + ".greeter_type";
    public static final String GREETER_ACTIVE_OBJVAR = GREETER_VAR_PREFIX + ".greeter_active";
    public static final String GREETER_IS_ACTIVATED_OBJVAR = GREETER_VAR_PREFIX + ".greeter_currently_activated";
    public static final String GREETER_ANIMATES_OBJVAR = GREETER_VAR_PREFIX + ".greeter_animates";
    public static final String GREETER_ANIMATING_OBJVAR = GREETER_VAR_PREFIX + ".greeter_currently_animating";
    public static final String GREETER_VOICES_OBJVAR = GREETER_VAR_PREFIX + ".greeter_voices";
    public static final String GREETER_VOICING_OBJVAR = GREETER_VAR_PREFIX + ".greeter_currently_voicing";
    public static final String GREETER_SOUNDING_OBJVAR = GREETER_VAR_PREFIX + ".greeter_currently_sounding";
    public static final String GREETER_SOUNDS_OBJVAR = GREETER_VAR_PREFIX + ".greeter_sounds";
    public static final String GREETER_MOODS_OBJVAR = GREETER_VAR_PREFIX + ".greeter_moods";
    public static final String GREETER_EFFECT_OBJVAR = GREETER_VAR_PREFIX + ".greeter_effects";
    public static final String GREETER_HAS_EFFECT_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_effect";
    public static final String GREETER_STATEMENT_OBJVAR = GREETER_VAR_PREFIX + ".greeter_statement";
    public static final String GREETER_HAS_STATEMENT_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_statement";
    public static final String GREETER_HAS_COLOR_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_color";
    public static final String GREETER_SOUNDS_VO_MENU_OBJVAR = GREETER_VAR_PREFIX + ".greeter_sound_vo_menu_selection";
    public static final String CREATURE_TYPE = GREETER_VAR_PREFIX + ".creature_type";
    public static final String CREATURE_LAST_CREATED = GREETER_VAR_PREFIX + ".creature_last_created";
    public static final String GREETER_DATA_OBTAINED = GREETER_VAR_PREFIX + ".greeter_data_obtained";
    public static final String GREETER_TYPE = GREETER_VAR_PREFIX + ".greeter_type";
    public static final String GREETER_CAN_DRESS_OBJVAR = GREETER_VAR_PREFIX + ".greeter_can_be_dressed";
    public static final String GREETER_TYPE_NICHE = GREETER_VAR_PREFIX + ".greeter_niche";
    public static final String GREETER_TYPE_HAS_FEMALE_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_female";
    public static final String GREETER_TYPE_SPEAKBASIC_OBJVAR = GREETER_VAR_PREFIX + ".greeter_speaks_basic";
    public static final String GREETER_LOCATION_OBJVAR = GREETER_VAR_PREFIX + ".greeter_location";
    public static final String GREETER_ACTUAL_ANIMATION = GREETER_VAR_PREFIX + ".greeter_actual_animation";
    public static final String GREETER_ACTUAL_VOICE = GREETER_VAR_PREFIX + ".greeter_actual_voice";
    public static final String GREETER_ACTUAL_MOOD = GREETER_VAR_PREFIX + ".greeter_actual_mood";
    public static final String GREETER_ACTUAL_SOUND = GREETER_VAR_PREFIX + ".greeter_actual_sound";
    public static final String GREETER_ACTUAL_EFFECT = GREETER_VAR_PREFIX + ".greeter_actual_effect";
    public static final String GREETER_ACTUAL_STATEMENT = GREETER_VAR_PREFIX + ".greeter_actual_statement";
    public static final String GREETER_ACTUAL_MOOD_STRING = GREETER_VAR_PREFIX + ".greeter_mood_string";
    public static final String GREETER_ACTUAL_ANIMATION_STRING = GREETER_VAR_PREFIX + ".greeter_anim_string";
    public static final String GREETER_CURRENTLY_RANDOMIZED_GREET = GREETER_VAR_PREFIX + ".greeter_is_randomized_greeting";
    public static final String GREETER_RANDOM_TEST_FIRE = GREETER_VAR_PREFIX + ".greeter_random_test_greeting";
    public static final String GREETER_CREATURE_TYPE_OBJVAR = GREETER_VAR_PREFIX + ".greeter_verified_creature_type";
    public static final String GREETER_CREATURE_NAME_OBJVAR = GREETER_VAR_PREFIX + ".selection_creature_name";
    public static final String GREETER_NAMES_OBJVAR = GREETER_VAR_PREFIX + ".unsplit_greeter_names";
    public static final String GREETER_STRING_ID_OBJVAR = GREETER_VAR_PREFIX + ".unsplit_greeter_string_ids";
    public static final String GREETER_CREATURENAME_LIST_OBJVAR = GREETER_VAR_PREFIX + ".selection_creature_name_list";
    public static final String GREETER_SELECTION_OBJVAR = GREETER_VAR_PREFIX + ".selection_string_id";
    public static final String GREETER_CUSTOM_NAME_OBJVAR = GREETER_VAR_PREFIX + ".creature_custom_name";
    public static final String GREETER_APPEARANCE_LIST = GREETER_VAR_PREFIX + ".appearance_list";
    public static final String GREETER_COLOR_OBJVAR = GREETER_VAR_PREFIX + ".color_setting";
    public static final String GREETER_ALREADY_BARKED_SCRVAR = GREETER_VAR_PREFIX + ".greeter_already_barked";
    public static final String GREETER_PLAYTERCOLOR_SCRVAR = GREETER_VAR_PREFIX + ".greeter_color_setting";
    public static final String GREETER_COLOR = GREETER_VAR_PREFIX + ".greeter_color_str_array";
    public static final String GREETER_CONVERSE = GREETER_VAR_PREFIX + ".greeter_conversable";
    public static final String GREETER_HAS_NICHE_OBJVAR = GREETER_VAR_PREFIX + ".greeter_niche";
    public static final String GREETER_IS_DRESSABLE = GREETER_VAR_PREFIX + ".greeter_is_dressable";
    public static final String GREETER_SPEAKS_BASIC = GREETER_VAR_PREFIX + ".greeter_speaks_basic";
    public static final String GREETER_HAS_ANIMS_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_anims";
    public static final String GREETER_HAS_VO_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_vo";
    public static final String GREETER_HAS_SOUND_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_sound";
    public static final String GREETER_HAS_MOOD_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_mood";
    public static final String GREETER_HAS_CHAT_OBJVAR = GREETER_VAR_PREFIX + ".greeter_has_chat";
    public static final String GREETER_SCRIPT = "terminal.greeter";
    public static final String GREETER_DEED_OBJVAR = "greeter.give_greeter";
    public static final String GREETER_OWNER_OID_OBJVAR = GREETER_VAR_PREFIX + ".greeter_owner";
    public static final String TBL_VENDOR_ROOT_TBL_DIR = "datatables/vendor/";
    public static final String TBL_GREETER_ANIMS = "datatables/vendor/vendor_areabark_anims.iff";
    public static final String TBL_GREETER_MOODS = "datatables/vendor/vendor_areabark_moods.iff";
    public static final String TBL_GREETER_SAY_CHAT = "datatables/vendor/greeter_say_chat.iff";
    public static final String TBL_GREETER_SOUND_VOICE_EFFECT = "datatables/vendor/greeter_sound_voice_effect.iff";
    public static final String TBL_GREETER_NONVENDOR_TABLE = "datatables/vendor/greeter_nonvendor_data.iff";
    public static final String TBL_SHORTAGE_INCENTIVE_RULES = "datatables/economy/shortage_incentive_rules.iff";
    public static final String TBL_STABILIZATION_SEEDING_DENYLIST = "datatables/economy/stabilization_seeding_denylist.iff";
    public static final String TBL_LISTING_CATEGORY_RULES = "datatables/economy/listing_category_rules.iff";
    public static final String TBL_CATEGORY_DEMAND_COEFFICIENTS = "datatables/commodity/category_demand_coefficients.iff";
    public static final String[] SUPPORTED_LISTING_CATEGORIES = new String[]{
        "weapon",
        "weapon_melee",
        "weapon_ranged",
        "weapon_heavy",
        "armor",
        "armor_clothing",
        "armor_segment",
        "resource",
        "resource_inorganic",
        "resource_organic",
        "misc",
        "misc_consumable",
        "misc_component"
    };
    public static final float DEFAULT_SHORTAGE_FEE_MULTIPLIER = 0.85f;
    public static final float DEFAULT_SHORTAGE_NPC_SEED_FEE_MULTIPLIER = 0.45f;
    public static final float DEFAULT_SHORTAGE_REWARD_PCT = 0.03f;
    public static final int DEFAULT_SHORTAGE_DAILY_REWARD_CAP = 30000;
    public static final int DEFAULT_SHORTAGE_DAILY_REWARD_COUNT_CAP = 15;
    public static final int DEFAULT_SHORTAGE_ACCOUNT_DAILY_REWARD_CAP = 60000;
    public static final int DEFAULT_SHORTAGE_ACCOUNT_DAILY_REWARD_COUNT_CAP = 30;
    public static final int DEFAULT_SHORTAGE_REPEAT_TRADE_COOLDOWN_SECONDS = 1800;
    public static final String SHORTAGE_REWARD_ROOT = "vendor.shortageRewards";
    private static final String ECONOMY_VALIDATION_OBJVAR = SHORTAGE_REWARD_ROOT + ".validation";
    private static final int ECONOMY_VALIDATION_VERSION = 2;
    public static final int STABILIZATION_RECENT_ACTION_MAX = 12;
    public static final String COL_CREATURE_TYPE = "creature_type";
    public static final String COL_GREETER_CREATURE_NAME = "greeter_creature_name";
    public static final String COL_NONVENDOR_CREATURE_NAME = "nonvendor_creature_name";
    public static final String COL_NONVENDOR_CREATURE_TEMPLATE = "nonvendor_template_name";
    public static final String COL_NICHE = "greeter_niche";
    public static final String COL_GRTR_STRING_ID = "greeter_appearance_string_id";
    public static final String COL_NONVNDR_STRING_ID = "nonvendor_appearance_string_id";
    public static final String COL_DRESSED = "greeter_can_be_dressed";
    public static final String COL_SPEAK_BASIC = "greeter_speaks_basic";
    public static final String COL_SAY_CHAT = "greeter_say_chat";
    public static final String COL_ANIMATES = "greeter_animates";
    public static final String COL_VO = "greeter_voice_over";
    public static final String COL_SOUNDS = "greeter_sounds";
    public static final String COL_MOODS = "greeter_has_moods";
    public static final String COL_COLOR = "greeter_can_color";
    public static final int VAR_TRUE = 1;
    public static final int VAR_FALSE = 0;
    public static final string_id SID_OBSCENE = new string_id("player_structure", "obscene");
    public static final string_id SID_INVALID_GREETER_TABLE_DATA = new string_id("player_vendor", "greeter_data_table_corrupt");
    public static final string_id SID_SYS_CREATE_GREETER_SUCCESS = new string_id("player_structure", "create_greeter_success");
    public static final string_id SID_INVENTORY_FULL_GENERIC = new string_id("player_structure", "inventory_full_generic");
    public static final string_id SID_GREETER_PACK_UP_SUCCESS = new string_id("player_structure", "greeter_packup_success");
    public static final string_id SID_GREETER_PACK_UP_FAILURE = new string_id("player_structure", "greeter_packup_failure");
    public static final string_id SID_MUST_BE_IN_VALID_LOCATION = new string_id("player_structure", "must_be_valid_location");
    public static final string_id SID_SHORTAGE_PROMPT_MODE = new string_id("player_vendor", "shortage_prompt_mode");
    public static final string_id SID_SHORTAGE_PROMPT_ACTIVE = new string_id("player_vendor", "shortage_prompt_active");
    public static final string_id SID_SHORTAGE_PROMPT_FEE_PREVIEW = new string_id("player_vendor", "shortage_prompt_fee_preview");
    public static final string_id SID_SHORTAGE_PROMPT_REWARD_ELIGIBLE = new string_id("player_vendor", "shortage_prompt_reward_eligible");
    public static final string_id SID_SHORTAGE_PROMPT_REWARD_INELIGIBLE = new string_id("player_vendor", "shortage_prompt_reward_ineligible");
    public static boolean payMaintenance(obj_id player, obj_id vendor, int amt) throws InterruptedException
    {
        if (player == null || player == obj_id.NULL_ID)
        {
            return false;
        }
        if (vendor == null || vendor == obj_id.NULL_ID)
        {
            return false;
        }
        if (amt < 1 || amt > 100000)
        {
            return false;
        }
        dictionary params = new dictionary();
        params.put(money.DICT_MSG_PAYER, "1");
        return money.requestPayment(player, vendor, amt, "handleVendorPayment", params, true);
    }
    public static boolean withdrawMaintenance(obj_id player, obj_id vendor, int amt) throws InterruptedException
    {
        if (player == null || player == obj_id.NULL_ID)
        {
            return false;
        }
        if (vendor == null || vendor == obj_id.NULL_ID)
        {
            return false;
        }
        if (amt < 1 || amt > 100000)
        {
            return false;
        }
        dictionary d = new dictionary();
        d.put("player", player);
        d.put("amount", amt);
        return transferBankCreditsTo(vendor, player, amt, "msgVendorWithdrawSuccess", "msgVendorWithdrawFail", d);
    }
    public static int decrementMaintenancePool(obj_id vendor, int amt) throws InterruptedException
    {
        if (vendor == null || vendor == obj_id.NULL_ID)
        {
            return -1;
        }
        if (amt < 1)
        {
            return -1;
        }
        int pool = getBankBalance(vendor);
        if (amt > pool)
        {
            return -2;
        }
        if (money.bankTo(vendor, money.ACCT_VENDOR, amt))
        {
            CustomerServiceLog("vendor", "Vendor decrement maintenance pool.  Vendor " + vendor + " Amount: " + amt);
            return pool - amt;
        }
        else 
        {
            return -1;
        }
    }
    public static int damageVendor(obj_id vendor, int amt) throws InterruptedException
    {
        if (vendor == null || vendor == obj_id.NULL_ID)
        {
            return -1;
        }
        if (amt < 1)
        {
            return -1;
        }
        int condition = getVendorCondition(vendor);
        condition = condition - amt;
        CustomerServiceLog("vendor", "VENDOR DAMAGE DUE TO NO EMPTY MAINTENANCE POOL!  Vendor " + vendor + " Damage: " + amt);
        if (condition < 1)
        {
            CustomerServiceLog("vendor", "VENDOR CONDITION ZERO! Disabling vendor due to nonpayment of maintenance. Vendor " + vendor);
            CustomerServiceLog("vendor", "Note: Vendor disable code is not finished.  Vendor will continue to operate normally. Vendor " + vendor);
            condition = 0;
            obj_id inv = utils.getInventoryContainer(vendor);
            if (inv == null)
            {
                inv = vendor;
            }
            setObjVar(inv, "vendor_deactivated", 1);
        }
        else 
        {
            setObjVar(vendor, VAR_CONDITION, condition);
        }
        return condition;
    }
    public static int repairVendor(obj_id vendor, int amt) throws InterruptedException
    {
        if (vendor == null || vendor == obj_id.NULL_ID)
        {
            return -1;
        }
        if (amt < 1)
        {
            return -1;
        }
        int condition = getVendorCondition(vendor);
        int max_condition = getMaxCondition(vendor);
        condition = condition + amt;
        if (condition > max_condition)
        {
            condition = max_condition;
        }
        setObjVar(vendor, VAR_CONDITION, condition);
        CustomerServiceLog("vendor", "Vendor repair damage.  Vendor " + vendor + " Amount: " + amt);
        if (condition == max_condition)
        {
            obj_id inv = utils.getInventoryContainer(vendor);
            if (inv == null)
            {
                inv = vendor;
            }
            removeObjVar(inv, "vendor_deactivated");
        }
        return condition;
    }
    public static int getMaintenanceRate(obj_id vendor) throws InterruptedException
    {
        return getIntObjVar(vendor, VAR_MAINTENANCE_RATE);
    }
    public static obj_id getEconomyStateObject(obj_id source) throws InterruptedException
    {
        if (!isValidId(source) || source == obj_id.NULL_ID || !exists(source))
        {
            return obj_id.NULL_ID;
        }
        if (hasObjVar(source, VAR_ECONOMY_STATE))
        {
            obj_id linked = getObjIdObjVar(source, VAR_ECONOMY_STATE);
            if (isValidId(linked) && linked != obj_id.NULL_ID && exists(linked))
            {
                return linked;
            }
        }
        obj_id inventory = utils.getInventoryContainer(source);
        if (isValidId(inventory) && inventory != obj_id.NULL_ID && exists(inventory) && hasObjVar(inventory, VAR_ECONOMY_STATE))
        {
            obj_id linked = getObjIdObjVar(inventory, VAR_ECONOMY_STATE);
            if (isValidId(linked) && linked != obj_id.NULL_ID && exists(linked))
            {
                return linked;
            }
        }
        return obj_id.NULL_ID;
    }
    public static int applyEconomyAuctionFeeTuning(obj_id source, int baseFee) throws InterruptedException
    {
        int safeBase = Math.max(0, baseFee);
        if (safeBase < 1)
        {
            return safeBase;
        }
        obj_id sharedState = getEconomyStateObject(source);
        float multiplier = economy_stabilizer.getAuctionFeeMultiplier(source, sharedState);
        return Math.max(1, (int)Math.round(safeBase * multiplier));
    }
    public static int applyEconomyAuctionFeeTuning(obj_id source, obj_id listingItem, obj_id actor, int baseFee) throws InterruptedException
    {
        int tuned = applyEconomyAuctionFeeTuning(source, baseFee);
        String category = getListingCategoryForObject(listingItem);
        String label = getShortageCategoryLabel(category);
        if (label != null && label.length() > 0 && isValidId(actor) && exists(actor))
        {
            setObjVar(actor, "vendor.shortage.lastCategoryLabel", label);
            setObjVar(actor, "vendor.shortage.lastCategory", category);
        }
        float shortageMultiplier = getShortageFeeMultiplier(source, category, false);
        int adjusted = Math.max(1, (int)Math.round(tuned * shortageMultiplier));
        if (isValidId(actor) && exists(actor))
        {
            setObjVar(actor, "vendor.shortage.lastListingFee", adjusted);
            notePromptedListingSuccess(actor, source, category);
        }
        return adjusted;
    }
    public static void showShortageBoostPrompt(obj_id source, obj_id actor) throws InterruptedException
    {
        if (!isValidId(source) || source == obj_id.NULL_ID || !exists(source) || !isValidId(actor) || actor == obj_id.NULL_ID || !exists(actor) || !isPlayer(actor))
        {
            return;
        }
        obj_id sharedState = getEconomyStateObject(source);
        String mode = economy_stabilizer.MODE_BALANCED;
        if (isValidId(sharedState) && exists(sharedState) && hasObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_MODE))
        {
            mode = getStringObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_MODE);
        }

        prose_package modePrompt = new prose_package();
        modePrompt.stringId = SID_SHORTAGE_PROMPT_MODE;
        modePrompt.other.set(mode);
        sendSystemMessageProse(actor, modePrompt);

        String shortageCategory = null;
        if (isValidId(sharedState) && exists(sharedState) && hasObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + ".essentialAny") && getIntObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + ".essentialAny") == 1)
        {
            shortageCategory = getPreferredShortageCategoryForVendor(source);
        }
        if (shortageCategory != null && shortageCategory.length() > 0)
        {
            String shortageLabel = getShortageCategoryLabel(shortageCategory);
            prose_package activePrompt = new prose_package();
            activePrompt.stringId = SID_SHORTAGE_PROMPT_ACTIVE;
            activePrompt.other.set(shortageLabel);
            sendSystemMessageProse(actor, activePrompt);

            dictionary incentiveRow = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, shortageCategory);
            if ((incentiveRow == null || incentiveRow.isEmpty()) && !shortageCategory.equals(getEconomyCategoryRoot(shortageCategory)))
            {
                incentiveRow = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, getEconomyCategoryRoot(shortageCategory));
            }
            boolean rewardEligible = incentiveRow != null && !incentiveRow.isEmpty() && incentiveRow.getInt("reward_enabled") == 1;
            sendSystemMessage(actor, rewardEligible ? SID_SHORTAGE_PROMPT_REWARD_ELIGIBLE : SID_SHORTAGE_PROMPT_REWARD_INELIGIBLE);
            setObjVar(actor, "vendor.shortage.promptedCategory", shortageCategory);
            setObjVar(actor, "vendor.shortage.promptedCategoryLabel", shortageLabel);
        }

        if (hasObjVar(actor, "vendor.shortage.lastListingFee"))
        {
            int discountedFee = Math.max(0, getIntObjVar(actor, "vendor.shortage.lastListingFee"));
            if (discountedFee > 0)
            {
                prose_package feePrompt = new prose_package();
                feePrompt.stringId = SID_SHORTAGE_PROMPT_FEE_PREVIEW;
                feePrompt.digitInteger = discountedFee;
                sendSystemMessageProse(actor, feePrompt);
            }
        }

        setObjVar(actor, "vendor.shortage.promptShownAt", getGameTime());
        incrementPromptCounter(actor, "shown");
        behavior_telemetry.recordActivityEvent(actor, "vendor", "shortage_prompt_shown", 1);
    }

    public static void notePromptedListingSuccess(obj_id actor, obj_id source, String category) throws InterruptedException
    {
        if (!isValidId(actor) || actor == obj_id.NULL_ID || !exists(actor) || !isPlayer(actor))
        {
            return;
        }
        if (!hasObjVar(actor, "vendor.shortage.promptShownAt"))
        {
            return;
        }
        int shownAt = getIntObjVar(actor, "vendor.shortage.promptShownAt");
        if (shownAt < 1 || (getGameTime() - shownAt) > 1800)
        {
            return;
        }
        incrementPromptCounter(actor, "promptedListingSuccess");
        behavior_telemetry.recordActivityEvent(actor, "vendor", "shortage_prompt_listing_success", 1);
        if (category != null && category.length() > 0)
        {
            setObjVar(actor, "vendor.shortage.lastPromptedSuccessCategory", normalizeListingCategory(category));
            setObjVar(actor, "vendor.shortage.lastPromptedSuccessLabel", getShortageCategoryLabel(category));
        }
    }

    private static void incrementPromptCounter(obj_id actor, String counterName) throws InterruptedException
    {
        if (!isValidId(actor) || actor == obj_id.NULL_ID || !exists(actor) || counterName == null || counterName.length() < 1)
        {
            return;
        }
        String path = "vendor.shortage.telemetry." + counterName;
        int count = hasObjVar(actor, path) ? Math.max(0, getIntObjVar(actor, path)) : 0;
        setObjVar(actor, path, count + 1);
    }

    public static int applyEconomyMaintenanceSinkTuning(obj_id source, int baseCost) throws InterruptedException
    {
        int safeBase = Math.max(0, baseCost);
        if (safeBase < 1)
        {
            return safeBase;
        }
        obj_id sharedState = getEconomyStateObject(source);
        float multiplier = economy_stabilizer.getMaintenanceSinkMultiplier(source, sharedState);
        return Math.max(1, (int)Math.round(safeBase * multiplier));
    }
    public static int getMaintenancePool(obj_id vendor) throws InterruptedException
    {
        return getBankBalance(vendor);
    }
    public static int getDecayRate(obj_id vendor) throws InterruptedException
    {
        return getIntObjVar(vendor, VAR_DECAY_RATE);
    }
    public static int getVendorCondition(obj_id vendor) throws InterruptedException
    {
        return getIntObjVar(vendor, VAR_CONDITION);
    }
    public static int getMaxCondition(obj_id vendor) throws InterruptedException
    {
        return getIntObjVar(vendor, VAR_MAX_CONDITION);
    }
    public static void finalizePackUp(obj_id player, obj_id vendor, obj_id packer, boolean isAbandoned) throws InterruptedException
    {
        obj_id vcd;
        obj_id datapad;
        final boolean isLoadedAndAuthoritative = player.isLoaded() && player.isAuthoritative();
        CustomerServiceLog("vendorpackup", "Player " + getPlayerName(packer) + " (" + packer + ") is attempting to pack vendor (" + vendor + ",abandoned=" + isAbandoned + ") owned by player " + getPlayerName(player) + " (" + player + ")");
        if (isLoadedAndAuthoritative)
        {
            datapad = utils.getPlayerDatapad(player);
        }
        else 
        {
            datapad = utils.getPlayerDatapad(packer);
        }
        if (!isIdValid(datapad))
        {
            return;
        }
        vcd = createObjectOverloaded("object/intangible/vendor/generic_vendor_control_device.iff", datapad);
        if (!isIdValid(vcd))
        {
            return;
        }
        if (isAbandoned)
        {
            setObjVar(vcd, "abandoned.packer", packer);
            setObjVar(vcd, "abandoned.owner", player);
            setObjVar(vcd, "abandoned.vendor", vendor);
        }
        attachScript(vcd, "vendor.vendor_control_device");
        String vendorName = getName(vendor);
        setObjVar(vendor, VAR_PACKUP_VERSION, 1);
        updateVendorStatus(vendor, VENDOR_STATUS_FLAG_PACKED);
        if (hasObjVar(vendor, "vendor.map_registered"))
        {
            removeObjVar(vendor, "vendor.map_registered");
            removePlanetaryMapLocation(vendor);
        }
        setName(vcd, vendorName);
        putIn(vendor, vcd, player);
        if (!isLoadedAndAuthoritative)
        {
            final int maxDepth = isNpcVendor(vendor) ? 1 : 0;
            moveToOfflinePlayerDatapadAndUnload(vcd, player, maxDepth + 1);
            fixLoadWith(vendor, player, maxDepth);
        }
        CustomerServiceLog("vendorpackup", "Player " + getPlayerName(packer) + " (" + packer + ") packed vendor (" + vendor + ",abandoned=" + isAbandoned + ") owned by player " + getPlayerName(player) + " (" + player + ") into device (" + vcd + ")");
    }
    public static boolean isVendorPackUpEnabled() throws InterruptedException
    {
        return utils.checkConfigFlag("GameServer", "allowPlayersToPackVendors");
    }
    public static obj_id getAuctionContainer(obj_id vendor) throws InterruptedException
    {
        obj_id container = utils.getInventoryContainer(vendor);
        if (container == null)
        {
            container = vendor;
        }
        return container;
    }
    public static boolean isNpcVendor(obj_id vendor) throws InterruptedException
    {
        return getAuctionContainer(vendor) != vendor;
    }
    public static String[] getAllGreeterDatatableColumnNames(obj_id object, String datatablePath) throws InterruptedException
    {
        blog("vendor_lib.getAllGreeterDatatableColumnNames:init");
        if (!isValidId(object) || !exists(object))
        {
            return null;
        }
        else if (datatablePath == null || datatablePath.equals(""))
        {
            return null;
        }
        else if (!datatablePath.startsWith(TBL_VENDOR_ROOT_TBL_DIR))
        {
            return null;
        }
        String[] preParsedColList = dataTableGetColumnNames(datatablePath);
        if (preParsedColList == null)
        {
            return null;
        }
        int preParsedColListLength = preParsedColList.length;
        if (preParsedColListLength < 0)
        {
            return null;
        }
        blog("vendor_lib.getAllGreeterDatatableColumnNames: prevalidation completed");
        Vector greeterColNames = new Vector();
        greeterColNames.setSize(0);
        for (String aPreParsedColList : preParsedColList) {
            if (aPreParsedColList.startsWith("greeter")) {
                greeterColNames = utils.addElement(greeterColNames, aPreParsedColList);
            }
        }
        if (greeterColNames.size() <= 0)
        {
            return null;
        }
        blog("vendor_lib.getAllGreeterDatatableColumnNames: greeter col name list being returned.");
        String[] _greeterColNames = new String[0];
        _greeterColNames = new String[greeterColNames.size()];
        greeterColNames.toArray(_greeterColNames);
        return _greeterColNames;
    }
    public static boolean buildNpcInPlayerStructure(obj_id controller, obj_id player, String npcType, boolean newGreeter) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to create a nonvendor: deed invalid.");
            return false;
        }
        if (!isValidId(player) || !exists(player))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to create a nonvendor: player invalid.");
            return false;
        }
        if (npcType == null || npcType.equals(""))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to get NPC TYPE to create.");
            return false;
        }
        return buildNpcInPlayerStructure(controller, player, npcType, null, newGreeter);
    }
    public static boolean buildNpcInPlayerStructure(obj_id controller, obj_id owner, String npcType, location where, boolean newGreeter) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to create a nonvendor: deed invalid.");
            CustomerServiceLog("vendor", "vendor_lib.buildNpcInPlayerStructure: Greeter/Non-Vendor could not be created. Reason: Greeter Controller Invalid.");
            return false;
        }
        if (!isValidId(owner))
        {
            CustomerServiceLog("vendor", "vendor_lib.buildNpcInPlayerStructure: Greeter/Non-Vendor could not be created. Reason: Greeter Controller Owner OID: " + owner + " is invalid.");
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to create a nonvendor: player invalid.");
            return false;
        }
        if (npcType == null || npcType.length() <= 0)
        {
            CustomerServiceLog("vendor", "vendor_lib.buildNpcInPlayerStructure: Greeter/Non-Vendor could not be created. Reason: Greeter/Non-Vendor type not found. Owner OID: " + owner + "  Controller OID: " + controller);
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to create a nonvendor: npcType invalid.");
            return false;
        }
        if (!npcType.startsWith(NONVENDOR_VAR_PREFIX) && !npcType.startsWith(GREETER_VAR_PREFIX))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to create a nonvendor: npcType could not be found.");
            blog("vendor_lib.buildNpcInPlayerStructure: npcType received: " + npcType + " npcType needed:" + NONVENDOR_TYPE_PREFIX);
            return false;
        }
        if (newGreeter && !validateNpcPlacementInStructure(owner))
        {
            sendSystemMessage(owner, SID_MUST_BE_IN_VALID_LOCATION);
            blog("vendor_lib.buildNpcInPlayerStructure: Unable to create a nonvendor: validateNpcPlacementInStructure said NO.");
            return false;
        }
        location loc;
        if (where == null)
        {
            loc = getLocation(owner);
        }
        else 
        {
            loc = (location)where.clone();
        }
        if (loc == null)
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Location could not be found");
            return false;
        }
        String itemName = getStaticItemName(controller);
        if (itemName == null || itemName.equals(""))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Static Item Name not found");
            return false;
        }
        if (npcType.startsWith(NONVENDOR_VAR_PREFIX))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: I am a nonvendor vendor");
            String nonVendorSpawn = utils.getStringScriptVar(owner, NONVENDOR_CREATURE_TEMPLATE_SCRVAR);
            if (nonVendorSpawn == null || nonVendorSpawn.equals(""))
            {
                blog("vendor_lib.buildNpcInPlayerStructure: Creature Name not found");
                return false;
            }
            obj_id nonVendorCreature = createObject(nonVendorSpawn, loc);
            blog("vendor_lib.buildNpcInPlayerStructure: created mob staticObject: " + nonVendorCreature);
            String nonVendorName = utils.getStringScriptVar(owner, NONVENDOR_CUSTOM_NAME_SCRVAR);
            blog("vendor_lib.buildNpcInPlayerStructure: nonVendorName: " + nonVendorName);
            if (nonVendorName != null && !nonVendorName.equals(""))
            {
                setName(nonVendorCreature, nonVendorName);
            }
            setObjVar(nonVendorCreature, STATIC_ITEM_DEED_NAME, itemName);
            setObjVar(nonVendorCreature, GREETER_OWNER_OBJVAR, owner);
            persistObject(nonVendorCreature);
            setOwner(nonVendorCreature, owner);
            attachScript(nonVendorCreature, NONVENDOR_VENDOR_SCRIPT);
            CustomerServiceLog("tcg", "TCG NonVendor: " + nonVendorCreature + " of type: " + nonVendorSpawn + " was placed at location: " + loc + " by owner: " + owner + ".");
            return true;
        }
        blog("vendor_lib.buildNpcInPlayerStructure: I am a GREETER");
        String greeterSelection = utils.getStringObjVar(controller, vendor_lib.GREETER_CREATURE_NAME_OBJVAR);
        if (greeterSelection == null || greeterSelection.equals(""))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Greeter Spawn String Not Found");
            return false;
        }
        String customName = utils.getStringObjVar(controller, vendor_lib.GREETER_CUSTOM_NAME_OBJVAR);
        if (customName == null || customName.equals(""))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Greeter Custom Name Not Found");
            return false;
        }
        String creatureType = utils.getStringObjVar(controller, vendor_lib.GREETER_CREATURE_TYPE_OBJVAR);
        if (creatureType == null || creatureType.equals(""))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Creature Type Not Found");
            return false;
        }
        blog("vendor_lib.buildNpcInPlayerStructure: primary greeter build validation complete. About to spawn: " + greeterSelection);
        obj_id greeterObj = create.staticObject(greeterSelection, loc);
        if (!isValidId(greeterObj) || !exists(greeterObj))
        {
            blog("vendor_lib.buildNpcInPlayerStructure: Greeter Creature Could NOT BE CREATED");
            return false;
        }
        String greeterName = "Greeter: " + customName;
        setName(greeterObj, greeterName);
        setObjVar(controller, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR, greeterObj);
        setObjVar(greeterObj, vendor_lib.CNTRLR_GREETER_NONVENDOR_ID_OBJVAR, controller);
        setObjVar(controller, vendor_lib.GREETER_LOCATION_OBJVAR, getLocation(greeterObj));
        setInvulnerable(greeterObj, true);
        attachScript(greeterObj, GREETER_SCRIPT);
        setObjVar(greeterObj, STATIC_ITEM_DEED_NAME, itemName);
        ai_lib.setDefaultCalmBehavior(greeterObj, ai_lib.BEHAVIOR_SENTINEL);
        setOwner(greeterObj, owner);
        setObjVar(greeterObj, vendor_lib.GREETER_OWNER_OBJVAR, owner);
        setObjVar(greeterObj, vendor_lib.CREATURE_LAST_CREATED, getCalendarTimeStringLocal(getCalendarTime()));
        setObjVar(controller, vendor_lib.CREATURE_TYPE, creatureType);
        blog("vendor_lib.buildGreeter: Done setting up Greeter");
        setObjVar(controller, "unmoveable", 1);
        CustomerServiceLog("tcg", "TCG Greeter: " + greeterObj + " of type: " + greeterSelection + " was placed at location: " + loc + " by owner: " + owner + ".");
        return true;
    }
    public static boolean validateNpcPlacementInStructure(obj_id player) throws InterruptedException
    {
        if (!isValidId(player) || !exists(player))
        {
            return false;
        }
        obj_id structure = getTopMostContainer(player);
        if (!isValidId(structure) || !exists(structure))
        {
            return false;
        }
        if (!player_structure.isPlayerStructure(structure) || player_structure.isCivic(structure))
        {
            return false;
        }
        if (player_structure.isOwner(structure, player))
        {
            return true;
        }
        return player_structure.isAdmin(structure, player);
    }
    public static boolean validateNonVendorInStructure(obj_id nonVendor) throws InterruptedException
    {
        if (!isValidId(nonVendor) || !exists(nonVendor))
        {
            return false;
        }
        obj_id structure = getTopMostContainer(nonVendor);
        if (!isValidId(structure) || !exists(structure))
        {
            return false;
        }
        return !(!player_structure.isPlayerStructure(structure) || player_structure.isCivic(structure));
    }
    public static String getGreeterNonVendorCreatureType(obj_id object, String greeterOrNonVendorType) throws InterruptedException
    {
        blog("vendor_lib.getGreeterNonVendorCreatureType init");
        if (!isValidId(object) || !exists(object))
        {
            return null;
        }
        else if (greeterOrNonVendorType == null || greeterOrNonVendorType.equals(""))
        {
            return null;
        }
        blog("vendor_lib.getGreeterNonVendorCreatureType validation complete");
        String[] creatureTypes = dataTableGetStringColumnNoDefaults(TBL_GREETER_NONVENDOR_TABLE, COL_CREATURE_TYPE);
        if (creatureTypes == null)
        {
            if (isPlayer(object))
            {
                sendSystemMessage(object, SID_INVALID_GREETER_TABLE_DATA);
            }
            return null;
        }
        blog("vendor_lib.getGreeterNonVendorCreatureType creatureTypes received");
        for (int i = 0; i < creatureTypes.length; i++)
        {
            if (!greeterOrNonVendorType.contains(creatureTypes[i]))
            {
                blog("vendor_lib.getGreeterNonVendorCreatureType greeterOrNonVendorType: " + greeterOrNonVendorType + " " + creatureTypes[i]);
                continue;
            }
            blog("vendor_lib.getGreeterNonVendorCreatureType greeterOrNonVendorType FOUND: " + creatureTypes[i] + "row: " + i);
            return creatureTypes[i];
        }
        blog("vendor_lib.getGreeterNonVendorCreatureType greeterOrNonVendorType NOT found");
        return null;
    }
    public static boolean isSpecialVendor(obj_id object) throws InterruptedException
    {
        if (!isValidId(object) || !exists(object))
        {
            return false;
        }
        if (!hasObjVar(object, vendor_lib.SPECIAL_VENDOR_IDENTIFIER))
        {
            return false;
        }
        return getBooleanObjVar(object, vendor_lib.SPECIAL_VENDOR_IDENTIFIER);
    }
    public static obj_id setObjectOwner(obj_id controller) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            return null;
        }
        obj_id owner = getOwner(controller);
        if (!isValidId(owner))
        {
            owner = utils.getContainingPlayer(controller);
            if (!isValidId(owner) && !exists(owner))
            {
                return null;
            }
            setOwner(controller, owner);
        }
        if (!hasObjVar(controller, vendor_lib.OWNER_OBJVAR))
        {
            setObjVar(controller, vendor_lib.OWNER_OBJVAR, owner);
        }
        return owner;
    }
    public static obj_id getObjectOwner(obj_id controller) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            return null;
        }
        obj_id owner = getOwner(controller);
        if (!isValidId(owner))
        {
            owner = setObjectOwner(controller);
            if (!isValidId(owner))
            {
                return null;
            }
        }
        return owner;
    }
    public static boolean isControllerOrChildInValidLocation(obj_id controller) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        obj_id structure = getTopMostContainer(controller);
        if (!isValidId(structure) || !exists(structure))
        {
            return false;
        }
        return player_structure.isPlayerStructure(structure);
    }
    public static boolean isObjectInSameCellAsController(obj_id controller, obj_id object) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        if (!isValidId(object) || !exists(object))
        {
            return false;
        }
        obj_id controllerStructure = getTopMostContainer(controller);
        if (!isValidId(controllerStructure) || !exists(controllerStructure))
        {
            return false;
        }
        if (!controllerContainmentCheck(controller))
        {
            return false;
        }
        location controllerLoc = getLocation(controller);
        if (controllerLoc == null)
        {
            return false;
        }
        location objLocation = getLocation(object);
        if (objLocation == null)
        {
            return false;
        }
        return controllerLoc.cell == objLocation.cell;
    }
    public static boolean recreateObject(obj_id controller, obj_id player) throws InterruptedException
    {
        blog("vendor_lib.recreateObject init");
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        if (!isValidId(player) || !exists(player))
        {
            return false;
        }
        blog("vendor_lib.recreateObject validation completed. Getting Controller Location");
        location where = getLocation(controller);
        if (where == null)
        {
            return false;
        }
        String npcType = "";
        if (hasObjVar(controller, NONVENDOR_VAR_PREFIX))
        {
            npcType = NONVENDOR_VAR_PREFIX;
        }
        else if (hasObjVar(controller, GREETER_VAR_PREFIX))
        {
            npcType = GREETER_VAR_PREFIX;
        }
        if (npcType.equals(""))
        {
            return false;
        }
        blog("vendor_lib.recreateObjectAtLocation placing greeter via buildNpcInPlayerStructure at CONTROLLER location");
        return buildNpcInPlayerStructure(controller, player, npcType, where, false);
    }
    public static boolean recreateObjectAtLocation(obj_id controller, obj_id owner, location loc) throws InterruptedException
    {
        blog("vendor_lib.recreateObjectAtLocation init");
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        if (!isValidId(owner))
        {
            return false;
        }
        if (loc == null)
        {
            return false;
        }
        blog("vendor_lib.recreateObjectAtLocation Greeter Location Validated");
        blog("vendor_lib.recreateObjectAtLocation getting controller location for testing");
        location where = getLocation(controller);
        if (where == null)
        {
            CustomerServiceLog("vendor", "vendor_lib.recreateObjectAtLocation: GREETER CONTROLLER LOCATION INVALID: Controller: " + controller + ") Owner: " + getName(owner) + " (" + owner + ")");
            return false;
        }
        String npcType = "";
        if (hasObjVar(controller, NONVENDOR_VAR_PREFIX))
        {
            npcType = NONVENDOR_VAR_PREFIX;
        }
        else if (hasObjVar(controller, GREETER_VAR_PREFIX))
        {
            npcType = GREETER_VAR_PREFIX;
        }
        if (npcType.equals(""))
        {
            return false;
        }
        blog("vendor_lib.recreateObjectAtLocation placing greeter via buildNpcInPlayerStructure at greeter location");
        return buildNpcInPlayerStructure(controller, owner, npcType, loc, false);
    }
    public static boolean controllerContainmentCheck(obj_id controller) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        if (utils.isNestedWithinAPlayer(controller))
        {
            return false;
        }
        obj_id building = getTopMostContainer(controller);
        if (!isGameObjectTypeOf(building, GOT_building_player) && (!isGameObjectTypeOf(building, GOT_ship_fighter) && !space_utils.isShipWithInterior(building)))
        {
            return false;
        }
        if (player_structure.isCivic(building))
        {
            return false;
        }
        location here = getLocation(controller);
        return getContainedBy(controller) == here.cell;
    }
    public static boolean removeObjectFromController(obj_id controller, obj_id greeter) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        removeObjVar(controller, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR);
        removeObjVar(controller, vendor_lib.GREETER_IS_ACTIVATED_OBJVAR);
        removeObjVar(controller, vendor_lib.GREETER_ANIMATING_OBJVAR);
        removeObjVar(controller, vendor_lib.GREETER_VOICING_OBJVAR);
        removeObjVar(controller, vendor_lib.GREETER_SOUNDING_OBJVAR);
        removeObjVar(controller, vendor_lib.GREETER_HAS_MOOD_OBJVAR);
        removeObjVar(controller, vendor_lib.GREETER_HAS_STATEMENT_OBJVAR);
        removeObjVar(controller, vendor_lib.GREETER_CURRENTLY_RANDOMIZED_GREET);
        if (isValidId(greeter) && exists(greeter))
        {
            destroyObject(greeter);
        }
        removeObjVar(controller, "unmoveable");
        return true;
    }
    public static boolean updateGreeterFunctionality(obj_id greeter) throws InterruptedException
    {
        blog("vendor_lib.updateGreeterFunctionality init");
        if (!isValidId(greeter) || !exists(greeter))
        {
            return false;
        }
        obj_id controller = getObjIdObjVar(greeter, CNTRLR_GREETER_NONVENDOR_ID_OBJVAR);
        if (!isValidId(controller) || !exists(controller))
        {
            CustomerServiceLog("vendor", "vendor_lib.updateGreeterFunctionality: GREETER CONTROLLER INVALID: Greeter: " + getName(greeter) + " (" + greeter + ") GREETER UPDATE FAILED - NOTIFY DESIGN");
            return false;
        }
        if (!removeObjectFromController(controller, greeter))
        {
            CustomerServiceLog("vendor", "vendor_lib.updateGreeterFunctionality: GREETER NOT DESTROYED PROPERLY FOR UPDATE: Greeter: " + getName(greeter) + " (" + greeter + ") Owner: " + getOwner(controller));
            CustomerServiceLog("vendor", "vendor_lib.updateGreeterFunctionality: GREETER BEING MANUALLY DESTROYED: Greeter: " + getName(greeter) + " (" + greeter);
            destroyObject(greeter);
            return false;
        }
        return true;
    }
    public static boolean colorizeGreeterFromWidget(obj_id player, obj_id greeter, String params) throws InterruptedException
    {
        if (!isValidId(player) || !exists(player))
        {
            return false;
        }
        if (!isValidId(greeter) || !exists(greeter))
        {
            return false;
        }
        if (params == null || params.equals(""))
        {
            return false;
        }
        obj_id controller = getObjIdObjVar(greeter, vendor_lib.CNTRLR_GREETER_NONVENDOR_ID_OBJVAR);
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        String[] colorArray = split(params, ' ');
        if (colorArray == null || colorArray.length <= 0)
        {
            return false;
        }
        setObjVar(controller, vendor_lib.GREETER_COLOR, colorArray);
        for (int i = 0; i < colorArray.length; i += 2)
        {
            if (colorArray[i] == null || colorArray[i].equals(""))
            {
                break;
            }
            hue.setColor(greeter, colorArray[i], utils.stringToInt(colorArray[i + 1]));
        }
        utils.removeScriptVar(player, vendor_lib.GREETER_PLAYTERCOLOR_SCRVAR);
        return true;
    }
    public static boolean colorizeGreeterFromController(obj_id controller, obj_id greeter) throws InterruptedException
    {
        if (!isValidId(controller) || !exists(controller))
        {
            return false;
        }
        if (!isValidId(greeter) || !exists(greeter))
        {
            return false;
        }
        String[] colorArray = getStringArrayObjVar(controller, vendor_lib.GREETER_COLOR);
        if (colorArray == null || colorArray.length <= 0)
        {
            return false;
        }
        for (int i = 0; i < colorArray.length; i += 2)
        {
            if (colorArray[i] == null || colorArray[i].equals(""))
            {
                break;
            }
            hue.setColor(greeter, colorArray[i], utils.stringToInt(colorArray[i + 1]));
        }
        return true;
    }

    public static boolean handleNpcVendorPurchase(obj_id vendor, dictionary params) throws InterruptedException
    {
        obj_id customer = params != null ? params.getObjId("customer") : null;
        int amount = params != null ? Math.max(0, params.getInt("amount")) : 0;
        if (!isValidId(vendor) || !exists(vendor) || !isValidId(customer) || !exists(customer))
        {
            return false;
        }
        if (!ai_lib.isNpc(customer))
        {
            writeNpcVendorOutcome(customer, vendor, amount, false, "sender_not_npc", obj_id.NULL_ID);
            return false;
        }

        obj_id inventory = utils.getInventoryContainer(vendor);
        obj_id customerInventory = utils.getInventoryContainer(customer);
        obj_id[] stock = isValidId(inventory) ? getContents(inventory) : null;
        obj_id delivered = obj_id.NULL_ID;
        if (stock != null && isValidId(customerInventory))
        {
            for (int i = 0; i < stock.length; i++)
            {
                obj_id item = stock[i];
                if (!isValidId(item) || !exists(item))
                {
                    continue;
                }
                if (putIn(item, customerInventory, customer) || putInOverloaded(item, customerInventory))
                {
                    delivered = item;
                    break;
                }
            }
        }

        String detail = "purchase_failed";
        boolean success = false;
        if (isValidId(delivered))
        {
            detail = "item_delivered";
            success = true;
        }
        else if (amount > 0)
        {
            detail = "service_consumed";
            success = true;
        }
        if (success && isValidId(delivered))
        {
            applyShortageSellerReward(vendor, delivered, amount, customer);
        }
        writeNpcVendorOutcome(customer, vendor, amount, success, detail, delivered);
        return success;
    }

    public static boolean handleNpcVendorListing(obj_id vendor, dictionary params) throws InterruptedException
    {
        obj_id seller = params != null ? params.getObjId("seller") : null;
        String source = params != null ? params.getString("source") : null;
        String category = getRequestedListingCategory(params);
        String economyMode = getListingEconomyMode(vendor, params);
        recordNpcListingTelemetry(vendor, economyMode, category, false);
        if (!isValidId(vendor) || !exists(vendor) || !isValidId(seller) || !exists(seller))
        {
            return false;
        }
        if (!ai_lib.isNpc(seller) || !hasScript(seller, "npc.simulation.adaptive_archetype_controller") || source == null || !source.equals(AI_LISTING_SOURCE))
        {
            logStabilizationAction("STAB_LIST_SKIP_SOURCE", vendor, seller, "misc", "invalid_source_or_actor");
            writeNpcListingOutcome(seller, vendor, false, "listing_failed", obj_id.NULL_ID, 0, source);
            return false;
        }

        cleanupExpiredSeededListings(vendor);

        obj_id inventory = utils.getInventoryContainer(seller);
        if (!isValidId(inventory) || inventory == obj_id.NULL_ID)
        {
            logStabilizationAction("STAB_LIST_FAIL_INV", vendor, seller, "misc", "missing_inventory");
            writeNpcListingOutcome(seller, vendor, false, "listing_failed", obj_id.NULL_ID, 0, source);
            return false;
        }

        String categoryLabel = getShortageCategoryLabel(category);
        if (!isCategoryInEssentialShortage(vendor, params, category))
        {
            logStabilizationAction("STAB_LIST_SKIP_SHORTAGE", vendor, seller, category, "not_essential_shortage");
            writeNpcListingOutcome(seller, vendor, false, "listing_skipped_not_essential_shortage", obj_id.NULL_ID, 0, source);
            return false;
        }
        if (!canSpawnCategoryListingToday(vendor, category, params, economyMode))
        {
            logStabilizationAction("STAB_LIST_SKIP_CAP", vendor, seller, category, "daily_cap");
            writeNpcListingOutcome(seller, vendor, false, "listing_skipped_daily_cap", obj_id.NULL_ID, 0, source);
            return false;
        }

        obj_id listingItem = obj_id.NULL_ID;
        obj_id[] contents = getContents(inventory);
        if (contents != null)
        {
            for (int i = 0; i < contents.length; i++)
            {
                obj_id candidate = contents[i];
                if (!isValidId(candidate) || !exists(candidate) || hasObjVar(candidate, "noTrade"))
                {
                    continue;
                }
                if (!doesListingMatchCategory(candidate, category))
                {
                    continue;
                }
                if (isDenylistedForAutoSeeding(category, getTemplateName(candidate)))
                {
                    continue;
                }
                listingItem = candidate;
                break;
            }
        }
        if (!isValidId(listingItem))
        {
            String fallbackTemplate = getFallbackTemplateForCategory(category);
            if (isDenylistedForAutoSeeding(category, fallbackTemplate))
            {
                logStabilizationAction("STAB_LIST_SKIP_DENY", vendor, seller, category, "fallback_denylisted");
                writeNpcListingOutcome(seller, vendor, false, "listing_skipped_denylist", obj_id.NULL_ID, 0, source);
                return false;
            }
            listingItem = createObject(fallbackTemplate, inventory, "");
            if (!isValidId(listingItem) || !exists(listingItem))
            {
                logStabilizationAction("STAB_LIST_FAIL_CREATE", vendor, seller, category, "fallback_create_failed");
                writeNpcListingOutcome(seller, vendor, false, "listing_failed", obj_id.NULL_ID, 0, source);
                return false;
            }
            setObjVar(listingItem, "npc.simulatedPlaceholder", 1);
        }

        obj_id vendorContainer = getAuctionContainer(vendor);
        if (!isValidId(vendorContainer) || vendorContainer == obj_id.NULL_ID)
        {
            writeNpcListingOutcome(seller, vendor, false, "listing_failed", listingItem, 0, source);
            return false;
        }

        float floorMultiplier = getListingFloorMultiplier(params, economyMode);
        float ceilingMultiplier = getListingCeilingMultiplier(params, economyMode);
        int seededPrice = getAdaptiveNpcSeededPrice(vendor, category);
        int price = computeIntelligentNpcListingPrice(vendor, vendorContainer, seller, listingItem, category, seededPrice, floorMultiplier, ceilingMultiplier);
        int reducedFeePreview = Math.max(1, (int)Math.round(Math.max(1, params != null ? params.getInt("baseListingFee") : 20) * getShortageFeeMultiplier(vendor, category, true)));
        if (!(putIn(listingItem, vendorContainer) || putInOverloaded(listingItem, vendorContainer)))
        {
            writeNpcListingOutcome(seller, vendor, false, "listing_failed", listingItem, 0, source);
            return false;
        }

        setObjVar(listingItem, "vendor.price", price);
        setObjVar(listingItem, "vendor.npcListing", 1);
        setObjVar(listingItem, "vendor.listingSource", AI_LISTING_SOURCE);
        setObjVar(listingItem, AI_LISTING_FLAG, 1);
        setObjVar(listingItem, "vendor.listingCreator", seller);
        setObjVar(listingItem, "vendor.listingCategory", category);
        setObjVar(listingItem, "vendor.shortageCategoryLabel", categoryLabel);
        setObjVar(listingItem, "vendor.shortageListingFeePreview", reducedFeePreview);
        int staleSeconds = getListingStaleSeconds(params, economyMode);
        int expiresAt = getGameTime() + staleSeconds;
        setObjVar(listingItem, "vendor.aiListing.expiresAt", expiresAt);
        setObjVar(listingItem, "vendor.aiListing.economyMode", economyMode);
        registerSeededListing(vendor, listingItem, expiresAt);
        incrementDailyCategorySpawn(vendor, category);
        recordListingPriceHistory(vendor, category, price);
        recordNpcListingTelemetry(vendor, economyMode, category, true);
        logStabilizationAction("STAB_LIST_CREATED", vendor, seller, category, "price=" + price);
        writeNpcListingOutcome(seller, vendor, true, "listing_created", listingItem, price, source);
        return true;
    }


    private static int getAdaptiveNpcSeededPrice(obj_id vendor, String category) throws InterruptedException
    {
        String normalized = normalizeListingCategory(category);
        int[] categoryBand = getCategoryDemandSeedBand(normalized);
        int categoryMin = categoryBand[0];
        int categoryMax = categoryBand[1];

        int rollingMedian = getRollingMedianPrice(vendor, normalized);
        int seededPrice;
        if (rollingMedian > 0)
        {
            int spread = Math.max(1, (int)Math.round(rollingMedian * 0.15f));
            int medianFloor = clamp(rollingMedian - spread, categoryMin, categoryMax);
            int medianCeiling = clamp(rollingMedian + spread, categoryMin, categoryMax);
            if (medianCeiling < medianFloor)
            {
                medianCeiling = medianFloor;
            }
            seededPrice = rand(medianFloor, medianCeiling);
        }
        else
        {
            seededPrice = rand(categoryMin, categoryMax);
        }

        obj_id sharedState = getEconomyStateObject(vendor);
        float modeMultiplier = economy_stabilizer.getAuctionFeeMultiplier(vendor, sharedState);
        seededPrice = (int)Math.round(seededPrice * modeMultiplier);
        return clamp(seededPrice, categoryMin, categoryMax);
    }

    private static int[] getCategoryDemandSeedBand(String category) throws InterruptedException
    {
        validateEconomyCategoryTables(obj_id.NULL_ID);
        int minSeed = AI_LISTING_MIN_PRICE;
        int maxSeed = Math.min(AI_LISTING_MAX_PRICE, AI_LISTING_MIN_PRICE * 5);
        String normalized = normalizeListingCategory(category);
        dictionary row = dataTableGetRow(TBL_CATEGORY_DEMAND_COEFFICIENTS, normalized);
        if ((row == null || row.isEmpty()) && !normalized.equals(getEconomyCategoryRoot(normalized)))
        {
            row = dataTableGetRow(TBL_CATEGORY_DEMAND_COEFFICIENTS, getEconomyCategoryRoot(normalized));
        }
        if (row != null && !row.isEmpty())
        {
            minSeed = Math.max(1, row.getInt("min_price"));
            maxSeed = Math.max(minSeed, row.getInt("max_price"));
        }
        minSeed = clamp(minSeed, AI_LISTING_MIN_PRICE, AI_LISTING_MAX_PRICE);
        maxSeed = clamp(maxSeed, minSeed, AI_LISTING_MAX_PRICE);
        if (maxSeed <= minSeed)
        {
            maxSeed = Math.min(AI_LISTING_MAX_PRICE, minSeed + Math.max(500, (int)Math.round(minSeed * 0.75f)));
        }
        return new int[]{minSeed, maxSeed};
    }

    private static float[] getCategoryListingJitterRange(String category) throws InterruptedException
    {
        String normalized = getEconomyCategoryRoot(category);
        if ("resource".equals(normalized))
        {
            return new float[]{0.10f, 0.14f};
        }
        if ("misc".equals(normalized))
        {
            return new float[]{0.11f, 0.15f};
        }
        if ("armor".equals(normalized))
        {
            return new float[]{0.13f, 0.18f};
        }
        if ("weapon".equals(normalized))
        {
            return new float[]{0.15f, 0.20f};
        }
        return new float[]{0.10f, 0.16f};
    }


    private static int computeIntelligentNpcListingPrice(obj_id vendor, obj_id vendorContainer, obj_id seller, obj_id listingItem, String category, int seededPrice, float floorMultiplier, float ceilingMultiplier) throws InterruptedException
    {
        int best = Math.max(AI_LISTING_MIN_PRICE, seededPrice);
        obj_id[] listings = getContents(vendorContainer);
        int sameTemplateCount = 0;
        int categoryCount = 0;
        int[] sameTemplatePrices = new int[listings != null ? listings.length : 0];
        int[] categoryPrices = new int[listings != null ? listings.length : 0];

        String template = getTemplateName(listingItem);
        if (template == null)
        {
            template = "";
        }

        if (listings != null)
        {
            for (int i = 0; i < listings.length; i++)
            {
                obj_id test = listings[i];
                if (!isValidId(test) || test == listingItem)
                {
                    continue;
                }
                int testPrice = hasObjVar(test, "vendor.price") ? Math.max(1, getIntObjVar(test, "vendor.price")) : 0;
                if (testPrice < 1)
                {
                    continue;
                }
                String testTemplate = getTemplateName(test);
                if (testTemplate != null && testTemplate.equals(template))
                {
                    sameTemplatePrices[sameTemplateCount++] = testPrice;
                }
                if (doesListingMatchCategory(test, category))
                {
                    categoryPrices[categoryCount++] = testPrice;
                }
            }
        }

        int sameTemplateMedian = getRobustMedianPrice(sameTemplatePrices, sameTemplateCount);
        int categoryMedian = getRobustMedianPrice(categoryPrices, categoryCount);
        int rollingMedian = getRollingMedianPrice(vendor, category);
        int observedMedian = sameTemplateMedian > 0 ? sameTemplateMedian : categoryMedian;
        if (observedMedian > 0)
        {
            rollingMedian = rollingMedian > 0 ? Math.round((rollingMedian * 0.65f) + (observedMedian * 0.35f)) : observedMedian;
        }
        if (rollingMedian <= 0)
        {
            rollingMedian = Math.max(AI_LISTING_MIN_PRICE, seededPrice);
        }
        setObjVar(vendor, AI_LISTING_TRACK_MEDIAN_ROOT + "." + category, rollingMedian);

        int floor = clamp((int)Math.round(rollingMedian * floorMultiplier), AI_LISTING_MIN_PRICE, AI_LISTING_MAX_PRICE);
        int ceiling = clamp((int)Math.round(rollingMedian * ceilingMultiplier), AI_LISTING_MIN_PRICE, AI_LISTING_MAX_PRICE);
        if (ceiling < floor)
        {
            ceiling = floor;
        }
        best = clamp(best, floor, ceiling);

        int npcWallet = hasObjVar(seller, "npc.simProfile.economy.wallet") ? Math.max(0, getIntObjVar(seller, "npc.simProfile.economy.wallet")) : 0;
        int npcReserve = hasObjVar(seller, "npc.simProfile.economy.reserve") ? Math.max(0, getIntObjVar(seller, "npc.simProfile.economy.reserve")) : 0;
        if (npcWallet < npcReserve)
        {
            best = (int)(best * 1.08f);
        }

        float[] jitterRange = getCategoryListingJitterRange(category);
        float jitterPct = rand((int)Math.round(jitterRange[0] * 100.0f), (int)Math.round(jitterRange[1] * 100.0f)) / 100.0f;
        float jitterSign = rand(0, 1) == 0 ? -1.0f : 1.0f;
        best = Math.max(1, Math.round(best * (1.0f + (jitterPct * jitterSign))));

        return clamp(best, floor, ceiling);
    }

    private static String getRequestedListingCategory(dictionary params) throws InterruptedException
    {
        validateEconomyCategoryTables(obj_id.NULL_ID);
        String category = params != null ? params.getString("listingCategory") : null;
        if (category == null || category.length() < 1)
        {
            category = params != null ? params.getString("category") : null;
        }
        if (category == null || category.length() < 1)
        {
            return "misc";
        }
        return normalizeListingCategory(category);
    }

    public static String getPreferredShortageCategoryForVendor(obj_id vendor) throws InterruptedException
    {
        obj_id sharedState = getEconomyStateObject(vendor);
        String bestCategory = "misc";
        int bestDeficit = 0;
        String[] categories = new String[]{"weapon", "armor", "resource", "misc"};
        for (int i = 0; i < categories.length; i++)
        {
            String category = categories[i];
            if (!isCategoryInEssentialShortage(vendor, null, category))
            {
                continue;
            }
            int target = economy_stabilizer.getCategoryShortageTarget(sharedState, category);
            int live = 0;
            if (isValidId(sharedState) && exists(sharedState) && hasObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + category + ".live"))
            {
                live = getIntObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + category + ".live");
            }
            int deficit = Math.max(1, target - live);
            if (deficit > bestDeficit)
            {
                bestDeficit = deficit;
                bestCategory = category;
            }
        }
        return bestCategory;
    }

    public static int getShortageDeficitMagnitudeForVendor(obj_id vendor, String category) throws InterruptedException
    {
        String normalized = normalizeListingCategory(category);
        obj_id sharedState = getEconomyStateObject(vendor);
        int target = economy_stabilizer.getCategoryShortageTarget(sharedState, normalized);
        int live = 0;
        if (isValidId(sharedState) && exists(sharedState) && hasObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + normalized + ".live"))
        {
            live = getIntObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + normalized + ".live");
        }
        return Math.max(0, target - live);
    }

    public static String getShortageCategoryLabel(String category) throws InterruptedException
    {
        validateEconomyCategoryTables(obj_id.NULL_ID);
        String normalized = normalizeListingCategory(category);
        dictionary row = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, normalized);
        if ((row == null || row.isEmpty()) && !normalized.equals(getEconomyCategoryRoot(normalized)))
        {
            row = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, getEconomyCategoryRoot(normalized));
        }
        if (row != null && !row.isEmpty())
        {
            String label = row.getString("label");
            if (label != null && label.length() > 0)
            {
                return label;
            }
        }
        if ("weapon".equals(normalized))
        {
            return "Weapons Shortage";
        }
        if ("armor".equals(normalized))
        {
            return "Armor Shortage";
        }
        if ("resource".equals(normalized))
        {
            return "Resource Shortage";
        }
        return "General Goods Shortage";
    }

    private static float getShortageFeeMultiplier(obj_id source, String category, boolean npcSeeding) throws InterruptedException
    {
        validateEconomyCategoryTables(source);
        String normalized = normalizeListingCategory(category);
        String shortageCategory = getEconomyCategoryRoot(normalized);
        if (!isCategoryInEssentialShortage(source, null, shortageCategory))
        {
            return 1.0f;
        }
        dictionary row = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, normalized);
        if ((row == null || row.isEmpty()) && !normalized.equals(shortageCategory))
        {
            row = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, shortageCategory);
        }
        float fallback = npcSeeding ? DEFAULT_SHORTAGE_NPC_SEED_FEE_MULTIPLIER : DEFAULT_SHORTAGE_FEE_MULTIPLIER;
        if (row == null || row.isEmpty())
        {
            return fallback;
        }
        if (row.getInt("essential_only") == 1 && !isCategoryInEssentialShortage(source, null, shortageCategory))
        {
            return 1.0f;
        }
        float value = npcSeeding ? row.getFloat("npc_seed_fee_multiplier") : row.getFloat("fee_multiplier");
        if (value <= 0.01f)
        {
            value = fallback;
        }
        return Math.max(0.05f, Math.min(1.0f, value));
    }

    private static String getListingCategoryForObject(obj_id item) throws InterruptedException
    {
        if (isValidId(item) && exists(item))
        {
            if (hasObjVar(item, "vendor.listingCategory"))
            {
                return normalizeListingCategory(getStringObjVar(item, "vendor.listingCategory"));
            }
            return getListingCategoryForTemplate(getTemplateName(item));
        }
        return "misc";
    }

    public static String normalizeListingCategory(String category) throws InterruptedException
    {
        String normalized = category == null ? "" : toLower(category);
        for (int i = 0; i < SUPPORTED_LISTING_CATEGORIES.length; i++)
        {
            if (SUPPORTED_LISTING_CATEGORIES[i].equals(normalized))
            {
                return normalized;
            }
        }
        return "misc";
    }

    public static String getEconomyCategoryRoot(String category) throws InterruptedException
    {
        String normalized = normalizeListingCategory(category);
        if (normalized.indexOf("weapon_") == 0)
        {
            return "weapon";
        }
        if (normalized.indexOf("armor_") == 0)
        {
            return "armor";
        }
        if (normalized.indexOf("resource_") == 0)
        {
            return "resource";
        }
        if (normalized.indexOf("misc_") == 0)
        {
            return "misc";
        }
        if ("weapon".equals(normalized) || "armor".equals(normalized) || "resource".equals(normalized))
        {
            return normalized;
        }
        return "misc";
    }

    private static boolean isCategoryInEssentialShortage(obj_id vendor, dictionary params, String category) throws InterruptedException
    {
        String shortageCategory = getEconomyCategoryRoot(category);
        if (params != null)
        {
            int essentialFlag = params.getInt("essentialShortage");
            if (essentialFlag == 1)
            {
                return true;
            }
            if (params.getInt("shortageEssential." + shortageCategory) == 1 || params.getInt("shortageEssential." + normalizeListingCategory(category)) == 1)
            {
                return true;
            }
            obj_id sharedState = params.getObjId("economyState");
            if (isValidId(sharedState) && exists(sharedState))
            {
                return hasObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + shortageCategory + ".essential") && getIntObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + shortageCategory + ".essential") == 1;
            }
        }
        return hasObjVar(vendor, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + shortageCategory + ".essential") && getIntObjVar(vendor, economy_stabilizer.OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + shortageCategory + ".essential") == 1;
    }

    private static boolean canSpawnCategoryListingToday(obj_id vendor, String category, dictionary params, String economyMode) throws InterruptedException
    {
        int cap = getDefaultSpawnCapByMode(economyMode);
        if (params != null)
        {
            cap = Math.max(0, params.getInt("spawnCapDaily"));
            if (cap < 1)
            {
                cap = Math.max(0, params.getInt("categoryDailyCap"));
            }
        }
        if (cap < 1)
        {
            cap = AI_LISTING_DEFAULT_DAILY_CAP;
        }
        int day = getGameTime() / 86400;
        int spawned = hasObjVar(vendor, AI_LISTING_TRACK_DAILY_ROOT + "." + day + "." + category) ? getIntObjVar(vendor, AI_LISTING_TRACK_DAILY_ROOT + "." + day + "." + category) : 0;
        return spawned < cap;
    }

    private static void incrementDailyCategorySpawn(obj_id vendor, String category) throws InterruptedException
    {
        int day = getGameTime() / 86400;
        String path = AI_LISTING_TRACK_DAILY_ROOT + "." + day + "." + category;
        int spawned = hasObjVar(vendor, path) ? getIntObjVar(vendor, path) : 0;
        setObjVar(vendor, path, spawned + 1);
    }

    private static float getListingFloorMultiplier(dictionary params, String economyMode) throws InterruptedException
    {
        float configured = params != null ? params.getFloat("floorMultiplier") : 0.0f;
        if (configured <= 0.05f)
        {
            configured = getDefaultFloorMultiplierByMode(economyMode);
        }
        return Math.max(0.05f, Math.min(2.0f, configured));
    }

    private static float getListingCeilingMultiplier(dictionary params, String economyMode) throws InterruptedException
    {
        float configured = params != null ? params.getFloat("ceilingMultiplier") : 0.0f;
        if (configured <= 0.05f)
        {
            configured = getDefaultCeilingMultiplierByMode(economyMode);
        }
        return Math.max(0.05f, Math.min(3.0f, configured));
    }

    private static int getListingStaleSeconds(dictionary params, String economyMode) throws InterruptedException
    {
        int staleSeconds = params != null ? params.getInt("staleSeconds") : 0;
        if (staleSeconds < 300)
        {
            staleSeconds = getDefaultStaleSecondsByMode(economyMode);
        }
        return staleSeconds;
    }

    private static String getListingEconomyMode(obj_id vendor, dictionary params) throws InterruptedException
    {
        String mode = params != null ? params.getString("economyMode") : null;
        if (mode != null && mode.length() > 0)
        {
            return mode;
        }
        obj_id sharedState = getEconomyStateObject(vendor);
        if (isValidId(sharedState) && exists(sharedState) && hasObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_MODE))
        {
            mode = getStringObjVar(sharedState, economy_stabilizer.OBJVAR_ECONOMY_MODE);
        }
        if (mode == null || mode.length() < 1)
        {
            mode = economy_stabilizer.MODE_BALANCED;
        }
        return mode;
    }

    private static int getDefaultSpawnCapByMode(String economyMode) throws InterruptedException
    {
        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            return 6;
        }
        if (economy_stabilizer.MODE_INFLATION_CONTROL.equals(economyMode))
        {
            return 2;
        }
        return 4;
    }

    private static float getDefaultFloorMultiplierByMode(String economyMode) throws InterruptedException
    {
        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            return 0.80f;
        }
        if (economy_stabilizer.MODE_INFLATION_CONTROL.equals(economyMode))
        {
            return 0.93f;
        }
        return 0.87f;
    }

    private static float getDefaultCeilingMultiplierByMode(String economyMode) throws InterruptedException
    {
        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            return 1.30f;
        }
        if (economy_stabilizer.MODE_INFLATION_CONTROL.equals(economyMode))
        {
            return 1.08f;
        }
        return 1.16f;
    }

    private static int getDefaultStaleSecondsByMode(String economyMode) throws InterruptedException
    {
        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            return 10800;
        }
        if (economy_stabilizer.MODE_INFLATION_CONTROL.equals(economyMode))
        {
            return 32400;
        }
        return AI_LISTING_DEFAULT_STALE_SECONDS;
    }

    private static void recordNpcListingTelemetry(obj_id vendor, String economyMode, String category, boolean created) throws InterruptedException
    {
        obj_id sharedState = getEconomyStateObject(vendor);
        if (!isValidId(sharedState) || !exists(sharedState))
        {
            return;
        }
        String safeMode = economyMode != null && economyMode.length() > 0 ? economyMode : economy_stabilizer.MODE_BALANCED;
        String modeKey = "balanced";
        if (economy_stabilizer.MODE_RECOVERY.equals(safeMode))
        {
            modeKey = "recovery";
        }
        else if (economy_stabilizer.MODE_INFLATION_CONTROL.equals(safeMode))
        {
            modeKey = "inflation_control";
        }
        String categoryKey = normalizeListingCategory(category);
        String root = SHORTAGE_REWARD_ROOT + ".kpi.mode." + modeKey + "." + categoryKey;
        int attempted = hasObjVar(sharedState, root + ".attempted") ? Math.max(0, getIntObjVar(sharedState, root + ".attempted")) : 0;
        setObjVar(sharedState, root + ".attempted", attempted + 1);
        if (created)
        {
            int createdCount = hasObjVar(sharedState, root + ".created") ? Math.max(0, getIntObjVar(sharedState, root + ".created")) : 0;
            setObjVar(sharedState, root + ".created", createdCount + 1);
        }
    }

    private static void registerSeededListing(obj_id vendor, obj_id listingItem, int expiresAt) throws InterruptedException
    {
        obj_id[] ids = hasObjVar(vendor, AI_LISTING_TRACK_IDS) ? getObjIdArrayObjVar(vendor, AI_LISTING_TRACK_IDS) : null;
        int[] expires = hasObjVar(vendor, AI_LISTING_TRACK_EXPIRES) ? getIntArrayObjVar(vendor, AI_LISTING_TRACK_EXPIRES) : null;
        int current = ids != null ? ids.length : 0;
        obj_id[] nextIds = new obj_id[current + 1];
        int[] nextExpires = new int[current + 1];
        for (int i = 0; i < current; i++)
        {
            nextIds[i] = ids[i];
            nextExpires[i] = expires != null && i < expires.length ? expires[i] : 0;
        }
        nextIds[current] = listingItem;
        nextExpires[current] = expiresAt;
        setObjVar(vendor, AI_LISTING_TRACK_IDS, nextIds);
        setObjVar(vendor, AI_LISTING_TRACK_EXPIRES, nextExpires);
    }

    private static void cleanupExpiredSeededListings(obj_id vendor) throws InterruptedException
    {
        if (!isValidId(vendor) || !exists(vendor))
        {
            return;
        }
        if (!hasObjVar(vendor, AI_LISTING_TRACK_IDS))
        {
            return;
        }
        obj_id[] ids = getObjIdArrayObjVar(vendor, AI_LISTING_TRACK_IDS);
        int[] expires = hasObjVar(vendor, AI_LISTING_TRACK_EXPIRES) ? getIntArrayObjVar(vendor, AI_LISTING_TRACK_EXPIRES) : null;
        if (ids == null || ids.length < 1)
        {
            removeObjVar(vendor, AI_LISTING_TRACK_IDS);
            removeObjVar(vendor, AI_LISTING_TRACK_EXPIRES);
            return;
        }
        obj_id container = getAuctionContainer(vendor);
        int now = getGameTime();
        obj_id[] keepIds = new obj_id[ids.length];
        int[] keepExpires = new int[ids.length];
        int keepCount = 0;
        for (int i = 0; i < ids.length; i++)
        {
            obj_id listing = ids[i];
            int expiresAt = expires != null && i < expires.length ? expires[i] : 0;
            if (!isValidId(listing) || !exists(listing))
            {
                continue;
            }
            boolean stillListed = isValidId(container) && getContainedBy(listing) == container;
            boolean isTrackedNpcListing = hasObjVar(listing, AI_LISTING_FLAG) && getIntObjVar(listing, AI_LISTING_FLAG) == 1 && hasObjVar(listing, "vendor.listingSource") && AI_LISTING_SOURCE.equals(getStringObjVar(listing, "vendor.listingSource"));
            if (stillListed && isTrackedNpcListing && expiresAt > 0 && now >= expiresAt)
            {
                destroyObject(listing);
                continue;
            }
            if (stillListed && isTrackedNpcListing)
            {
                keepIds[keepCount] = listing;
                keepExpires[keepCount] = expiresAt;
                keepCount++;
            }
        }
        if (keepCount < 1)
        {
            removeObjVar(vendor, AI_LISTING_TRACK_IDS);
            removeObjVar(vendor, AI_LISTING_TRACK_EXPIRES);
            return;
        }
        obj_id[] compactIds = new obj_id[keepCount];
        int[] compactExpires = new int[keepCount];
        for (int i = 0; i < keepCount; i++)
        {
            compactIds[i] = keepIds[i];
            compactExpires[i] = keepExpires[i];
        }
        setObjVar(vendor, AI_LISTING_TRACK_IDS, compactIds);
        setObjVar(vendor, AI_LISTING_TRACK_EXPIRES, compactExpires);
    }

    private static boolean doesListingMatchCategory(obj_id listing, String category) throws InterruptedException
    {
        if (!isValidId(listing) || !exists(listing))
        {
            return false;
        }
        String normalizedRequested = normalizeListingCategory(category);
        String template = getTemplateName(listing);
        if (isDenylistedForAutoSeeding(normalizedRequested, template))
        {
            return false;
        }
        String actual = normalizeListingCategory(getListingCategoryForTemplate(template));
        if (normalizedRequested.equals(actual))
        {
            return true;
        }
        return getEconomyCategoryRoot(normalizedRequested).equals(getEconomyCategoryRoot(actual));
    }

    private static String getListingCategoryForTemplate(String template) throws InterruptedException
    {
        String lowered = template == null ? "" : toLower(template);
        if (lowered.length() > 0)
        {
            int rows = dataTableGetNumRows(TBL_LISTING_CATEGORY_RULES);
            if (rows > 0)
            {
                int bestPriority = Integer.MAX_VALUE;
                int bestRow = -1;
                for (int i = 0; i < rows; i++)
                {
                    String matchType = toLower(dataTableGetString(TBL_LISTING_CATEGORY_RULES, i, "match_type"));
                    String pattern = toLower(dataTableGetString(TBL_LISTING_CATEGORY_RULES, i, "pattern"));
                    String category = normalizeListingCategory(dataTableGetString(TBL_LISTING_CATEGORY_RULES, i, "category"));
                    if (pattern == null || pattern.length() < 1)
                    {
                        continue;
                    }
                    boolean matched = false;
                    if ("exact".equals(matchType))
                    {
                        matched = lowered.equals(pattern);
                    }
                    else if ("prefix".equals(matchType))
                    {
                        matched = lowered.indexOf(pattern) == 0;
                    }
                    else if ("contains".equals(matchType))
                    {
                        matched = lowered.indexOf(pattern) > -1;
                    }
                    if (!matched)
                    {
                        continue;
                    }
                    int priority = dataTableGetInt(TBL_LISTING_CATEGORY_RULES, i, "priority");
                    if (priority < bestPriority)
                    {
                        bestPriority = priority;
                        bestRow = i;
                    }
                }
                if (bestRow > -1)
                {
                    return normalizeListingCategory(dataTableGetString(TBL_LISTING_CATEGORY_RULES, bestRow, "category"));
                }
            }
        }

        if (lowered.indexOf("weapon") > -1)
        {
            return "weapon";
        }
        if (lowered.indexOf("armor") > -1 || lowered.indexOf("clothing") > -1)
        {
            return "armor";
        }
        if (lowered.indexOf("resource") > -1 || lowered.indexOf("ore") > -1 || lowered.indexOf("gas") > -1)
        {
            return "resource";
        }
        return "misc";
    }

    public static String getListingCategoryForTemplateValue(String template) throws InterruptedException
    {
        return getListingCategoryForTemplate(template);
    }

    private static String getFallbackTemplateForCategory(String category) throws InterruptedException
    {
        String rootCategory = getEconomyCategoryRoot(category);
        if ("weapon".equals(rootCategory))
        {
            return "object/weapon/ranged/pistol/pistol_cdef.iff";
        }
        if ("armor".equals(rootCategory))
        {
            return "object/tangible/wearables/armor/ubese/armor_ubese_helmet.iff";
        }
        if ("resource".equals(rootCategory))
        {
            return "object/resource_container/inorganic_minerals.iff";
        }
        return "object/tangible/loot/quest/endrine.iff";
    }

    private static int getMedianPrice(int[] values, int count) throws InterruptedException
    {
        if (values == null || count < 1)
        {
            return 0;
        }
        for (int i = 0; i < count; i++)
        {
            for (int j = i + 1; j < count; j++)
            {
                if (values[j] < values[i])
                {
                    int swap = values[i];
                    values[i] = values[j];
                    values[j] = swap;
                }
            }
        }
        int middle = count / 2;
        if ((count % 2) == 1)
        {
            return values[middle];
        }
        return (values[middle - 1] + values[middle]) / 2;
    }

    private static int getRobustMedianPrice(int[] values, int count) throws InterruptedException
    {
        if (values == null || count < 1)
        {
            return 0;
        }
        int[] copy = new int[count];
        for (int i = 0; i < count; i++)
        {
            copy[i] = values[i];
        }
        getMedianPrice(copy, count);
        int trimEachSide = 0;
        if (count >= 5)
        {
            trimEachSide = Math.max(1, count / 10);
        }
        int trimmedCount = count - (trimEachSide * 2);
        if (trimmedCount < 1)
        {
            trimEachSide = 0;
            trimmedCount = count;
        }
        int[] trimmed = new int[trimmedCount];
        for (int i = 0; i < trimmedCount; i++)
        {
            trimmed[i] = copy[i + trimEachSide];
        }
        return getMedianPrice(trimmed, trimmedCount);
    }

    private static int getRollingMedianPrice(obj_id vendor, String category) throws InterruptedException
    {
        String historyPath = AI_LISTING_TRACK_PRICE_HISTORY_ROOT + "." + category;
        if (hasObjVar(vendor, historyPath))
        {
            int[] history = getIntArrayObjVar(vendor, historyPath);
            if (history != null && history.length > 0)
            {
                int[] copy = new int[history.length];
                for (int i = 0; i < history.length; i++)
                {
                    copy[i] = history[i];
                }
                return getRobustMedianPrice(copy, copy.length);
            }
        }
        String medianPath = AI_LISTING_TRACK_MEDIAN_ROOT + "." + category;
        return hasObjVar(vendor, medianPath) ? getIntObjVar(vendor, medianPath) : 0;
    }

    private static void recordListingPriceHistory(obj_id vendor, String category, int price) throws InterruptedException
    {
        String historyPath = AI_LISTING_TRACK_PRICE_HISTORY_ROOT + "." + category;
        int[] history = hasObjVar(vendor, historyPath) ? getIntArrayObjVar(vendor, historyPath) : null;
        int oldLen = history != null ? history.length : 0;
        int kept = Math.min(oldLen, AI_LISTING_MAX_HISTORY - 1);
        int[] next = new int[kept + 1];
        int start = Math.max(0, oldLen - kept);
        for (int i = 0; i < kept; i++)
        {
            next[i] = history[start + i];
        }
        next[kept] = clamp(price, AI_LISTING_MIN_PRICE, AI_LISTING_MAX_PRICE);
        setObjVar(vendor, historyPath, next);
        int[] copy = new int[next.length];
        for (int i = 0; i < next.length; i++)
        {
            copy[i] = next[i];
        }
        setObjVar(vendor, AI_LISTING_TRACK_MEDIAN_ROOT + "." + category, getRobustMedianPrice(copy, copy.length));
    }

    private static void applyShortageSellerReward(obj_id vendor, obj_id soldItem, int saleAmount, obj_id buyer) throws InterruptedException
    {
        if (!isValidId(vendor) || !exists(vendor) || !isValidId(soldItem) || !exists(soldItem))
        {
            return;
        }
        obj_id owner = hasObjVar(vendor, "vendor_owner") ? getObjIdObjVar(vendor, "vendor_owner") : obj_id.NULL_ID;
        if (!isValidId(owner) || !exists(owner))
        {
            return;
        }

        String category = getListingCategoryForObject(soldItem);
        if (!isCategoryInEssentialShortage(vendor, null, category))
        {
            logStabilizationAction("STAB_REWARD_SKIP_SHORTAGE", vendor, owner, category, "not_essential_shortage");
            return;
        }
        dictionary row = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, category);
        if ((row == null || row.isEmpty()) && !category.equals(getEconomyCategoryRoot(category)))
        {
            row = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, getEconomyCategoryRoot(category));
        }
        if (row == null || row.isEmpty() || row.getInt("reward_enabled") != 1)
        {
            logStabilizationAction("STAB_REWARD_SKIP_DISABLED", vendor, owner, category, "rule_disabled");
            return;
        }

        float rewardPct = row.getFloat("seller_reward_pct");
        if (rewardPct <= 0.0f)
        {
            rewardPct = DEFAULT_SHORTAGE_REWARD_PCT;
        }
        int reward = Math.max(0, Math.round(Math.max(0, saleAmount) * rewardPct));
        if (reward < 1)
        {
            logStabilizationAction("STAB_REWARD_SKIP_ZERO", vendor, owner, category, "reward_below_1");
            return;
        }

        int day = getGameTime() / 86400;
        String dayRoot = SHORTAGE_REWARD_ROOT + ".daily." + day;
        int rewardCap = Math.max(0, row.getInt("daily_reward_cap"));
        int rewardCountCap = Math.max(0, row.getInt("daily_reward_count_cap"));
        if (rewardCap < 1)
        {
            rewardCap = DEFAULT_SHORTAGE_DAILY_REWARD_CAP;
        }
        if (rewardCountCap < 1)
        {
            rewardCountCap = DEFAULT_SHORTAGE_DAILY_REWARD_COUNT_CAP;
        }
        int accountRewardCap = Math.max(0, row.getInt("account_daily_reward_cap"));
        int accountRewardCountCap = Math.max(0, row.getInt("account_daily_reward_count_cap"));
        if (accountRewardCap < 1)
        {
            accountRewardCap = DEFAULT_SHORTAGE_ACCOUNT_DAILY_REWARD_CAP;
        }
        if (accountRewardCountCap < 1)
        {
            accountRewardCountCap = DEFAULT_SHORTAGE_ACCOUNT_DAILY_REWARD_COUNT_CAP;
        }
        int rewardedToday = hasObjVar(owner, dayRoot + ".credits") ? Math.max(0, getIntObjVar(owner, dayRoot + ".credits")) : 0;
        int rewardedCountToday = hasObjVar(owner, dayRoot + ".count") ? Math.max(0, getIntObjVar(owner, dayRoot + ".count")) : 0;
        if (rewardedCountToday >= rewardCountCap || rewardedToday >= rewardCap)
        {
            logStabilizationAction("STAB_REWARD_SKIP_OWNER_CAP", vendor, owner, category, "owner_daily_cap");
            return;
        }

        int ownerStationId = getPlayerStationId(owner);
        String accountDayRoot = SHORTAGE_REWARD_ROOT + ".accountDaily." + day + "." + ownerStationId;
        int accountRewardedToday = hasObjVar(vendor, accountDayRoot + ".credits") ? Math.max(0, getIntObjVar(vendor, accountDayRoot + ".credits")) : 0;
        int accountRewardedCountToday = hasObjVar(vendor, accountDayRoot + ".count") ? Math.max(0, getIntObjVar(vendor, accountDayRoot + ".count")) : 0;
        if (accountRewardedCountToday >= accountRewardCountCap || accountRewardedToday >= accountRewardCap)
        {
            logStabilizationAction("STAB_REWARD_SKIP_ACCT_CAP", vendor, owner, category, "account_daily_cap");
            return;
        }

        int rewardRemaining = Math.max(0, rewardCap - rewardedToday);
        int accountRewardRemaining = Math.max(0, accountRewardCap - accountRewardedToday);
        reward = Math.min(reward, Math.min(rewardRemaining, accountRewardRemaining));
        if (reward < 1)
        {
            logStabilizationAction("STAB_REWARD_SKIP_CAP_REMAINDER", vendor, owner, category, "cap_remainder_zero");
            return;
        }
        int repeatTradeCooldownSeconds = Math.max(0, row.getInt("repeat_trade_cooldown_seconds"));
        if (repeatTradeCooldownSeconds < 1)
        {
            repeatTradeCooldownSeconds = DEFAULT_SHORTAGE_REPEAT_TRADE_COOLDOWN_SECONDS;
        }
        if (isRepeatTradeCooldownActive(vendor, owner, buyer, soldItem, category, repeatTradeCooldownSeconds))
        {
            logStabilizationAction("STAB_REWARD_SKIP_COOLDOWN", vendor, owner, category, "repeat_trade");
            return;
        }
        if (!money.bankTo(money.ACCT_VENDOR, owner, reward))
        {
            logStabilizationAction("STAB_REWARD_FAIL_BANK", vendor, owner, category, "bank_transfer_failed");
            return;
        }

        setObjVar(owner, dayRoot + ".credits", rewardedToday + reward);
        setObjVar(owner, dayRoot + ".count", rewardedCountToday + 1);
        setObjVar(vendor, accountDayRoot + ".credits", accountRewardedToday + reward);
        setObjVar(vendor, accountDayRoot + ".count", accountRewardedCountToday + 1);
        setObjVar(owner, SHORTAGE_REWARD_ROOT + ".lastAmount", reward);
        setObjVar(owner, SHORTAGE_REWARD_ROOT + ".lastCategory", category);
        setObjVar(owner, SHORTAGE_REWARD_ROOT + ".lastCategoryLabel", getShortageCategoryLabel(category));
        setObjVar(owner, SHORTAGE_REWARD_ROOT + ".lastTimestamp", getGameTime());
        markRepeatTradeReward(vendor, owner, buyer, soldItem, category, repeatTradeCooldownSeconds);
        logStabilizationAction("STAB_REWARD_GRANTED", vendor, owner, category, "amount=" + reward);
    }

    private static boolean isRepeatTradeCooldownActive(obj_id vendor, obj_id owner, obj_id buyer, obj_id soldItem, String category, int cooldownSeconds) throws InterruptedException
    {
        if (!isValidId(vendor) || !exists(vendor) || !isValidId(owner) || !exists(owner))
        {
            return false;
        }
        if (cooldownSeconds < 1)
        {
            return false;
        }
        int ownerStationId = getPlayerStationId(owner);
        int buyerStationId = isValidId(buyer) ? getPlayerStationId(buyer) : 0;
        String template = isValidId(soldItem) && exists(soldItem) ? getTemplateName(soldItem) : "unknown";
        String tradeKey = ownerStationId + "_" + buyerStationId + "_" + category + "_" + hashTemplateForObjVar(template);
        String cooldownPath = SHORTAGE_REWARD_ROOT + ".cooldowns." + tradeKey;
        int now = getGameTime();
        int nextAt = hasObjVar(vendor, cooldownPath) ? getIntObjVar(vendor, cooldownPath) : 0;
        return nextAt > now;
    }

    private static void markRepeatTradeReward(obj_id vendor, obj_id owner, obj_id buyer, obj_id soldItem, String category, int duration) throws InterruptedException
    {
        if (!isValidId(vendor) || !exists(vendor) || !isValidId(owner) || !exists(owner))
        {
            return;
        }
        int ownerStationId = getPlayerStationId(owner);
        int buyerStationId = isValidId(buyer) ? getPlayerStationId(buyer) : 0;
        String template = isValidId(soldItem) && exists(soldItem) ? getTemplateName(soldItem) : "unknown";
        String tradeKey = ownerStationId + "_" + buyerStationId + "_" + category + "_" + hashTemplateForObjVar(template);
        String cooldownPath = SHORTAGE_REWARD_ROOT + ".cooldowns." + tradeKey;
        setObjVar(vendor, cooldownPath, getGameTime() + duration);
    }

    private static String hashTemplateForObjVar(String template) throws InterruptedException
    {
        if (template == null || template.length() < 1)
        {
            return "t0";
        }
        int hash = 17;
        for (int i = 0; i < template.length(); i++)
        {
            hash = (hash * 31) + template.charAt(i);
        }
        return "t" + Math.abs(hash);
    }

    private static boolean isDenylistedForAutoSeeding(String category, String template) throws InterruptedException
    {
        int rows = dataTableGetNumRows(TBL_STABILIZATION_SEEDING_DENYLIST);
        if (rows < 1)
        {
            return false;
        }
        String normalizedCategory = normalizeListingCategory(category);
        String normalizedTemplate = template != null ? toLower(template) : "";
        for (int i = 0; i < rows; i++)
        {
            int enabled = dataTableGetInt(TBL_STABILIZATION_SEEDING_DENYLIST, i, "enabled");
            if (enabled != 1)
            {
                continue;
            }
            String denyType = toLower(dataTableGetString(TBL_STABILIZATION_SEEDING_DENYLIST, i, "deny_type"));
            String denyValue = toLower(dataTableGetString(TBL_STABILIZATION_SEEDING_DENYLIST, i, "deny_value"));
            if ("category".equals(denyType) && denyValue.equals(normalizedCategory))
            {
                return true;
            }
            if ("template".equals(denyType) && denyValue.length() > 0 && normalizedTemplate.equals(denyValue))
            {
                return true;
            }
            if ("template_contains".equals(denyType) && denyValue.length() > 0 && normalizedTemplate.indexOf(denyValue) > -1)
            {
                return true;
            }
        }
        return false;
    }

    private static void logStabilizationAction(String code, obj_id vendor, obj_id actor, String category, String detail) throws InterruptedException
    {
        String safeCode = code != null ? code : "STAB_UNKNOWN";
        String safeCategory = category != null ? category : "misc";
        String safeDetail = detail != null ? detail : "";
        CustomerServiceLog("economy_stabilizer", safeCode + " vendor=" + vendor + " actor=" + actor + " category=" + safeCategory + " detail=" + safeDetail);

        obj_id sharedState = getEconomyStateObject(vendor);
        if (!isValidId(sharedState) || !exists(sharedState))
        {
            return;
        }

        int now = getGameTime();
        String actorInfo = isValidId(actor) ? actor.toString() : "0";
        String entry = now + ":" + safeCode + ":" + safeCategory + ":" + actorInfo;
        appendRecentStabilizationAction(sharedState, entry, STABILIZATION_RECENT_ACTION_MAX);

        if ("STAB_LIST_CREATED".equals(safeCode))
        {
            int created = hasObjVar(sharedState, SHORTAGE_REWARD_ROOT + ".kpi.listingsCreated") ? Math.max(0, getIntObjVar(sharedState, SHORTAGE_REWARD_ROOT + ".kpi.listingsCreated")) : 0;
            setObjVar(sharedState, SHORTAGE_REWARD_ROOT + ".kpi.listingsCreated", created + 1);
        }
        else if ("STAB_REWARD_GRANTED".equals(safeCode))
        {
            int sold = hasObjVar(sharedState, SHORTAGE_REWARD_ROOT + ".kpi.listingsSold") ? Math.max(0, getIntObjVar(sharedState, SHORTAGE_REWARD_ROOT + ".kpi.listingsSold")) : 0;
            setObjVar(sharedState, SHORTAGE_REWARD_ROOT + ".kpi.listingsSold", sold + 1);
        }
    }

    private static void appendRecentStabilizationAction(obj_id sharedState, String entry, int maxEntries) throws InterruptedException
    {
        if (!isValidId(sharedState) || !exists(sharedState) || entry == null || entry.length() < 1)
        {
            return;
        }

        String historyPath = SHORTAGE_REWARD_ROOT + ".recent.actions";
        String[] existing = hasObjVar(sharedState, historyPath) ? getStringArrayObjVar(sharedState, historyPath) : null;
        int oldLen = existing != null ? existing.length : 0;
        int safeMax = Math.max(1, maxEntries);
        int nextLen = Math.min(safeMax, oldLen + 1);
        String[] next = new String[nextLen];

        int copyCount = Math.max(0, nextLen - 1);
        int start = Math.max(0, oldLen - copyCount);
        for (int i = 0; i < copyCount; i++)
        {
            next[i] = existing[start + i];
        }
        next[nextLen - 1] = entry;
        setObjVar(sharedState, historyPath, next);
    }

    private static void writeNpcListingOutcome(obj_id seller, obj_id vendor, boolean success, String detail, obj_id listingItem, int price, String source) throws InterruptedException
    {
        if (!isValidId(seller) || !exists(seller))
        {
            return;
        }
        int now = getGameTime();
        setObjVar(seller, "npc.simProfile.lastSystem", "vendor");
        setObjVar(seller, "npc.simProfile.lastAction", "handleNpcVendorListing");
        setObjVar(seller, "npc.simProfile.lastSuccess", success ? 1 : 0);
        setObjVar(seller, "npc.simProfile.lastDetail", detail);
        setObjVar(seller, "npc.simProfile.lastTimestamp", now);

        setObjVar(seller, "npc.simProfile.vendor.listing.lastVendor", vendor);
        setObjVar(seller, "npc.simProfile.vendor.listing.lastSuccess", success ? 1 : 0);
        setObjVar(seller, "npc.simProfile.vendor.listing.lastDetail", detail);
        setObjVar(seller, "npc.simProfile.vendor.listing.lastTimestamp", now);
        setObjVar(seller, "npc.simProfile.vendor.listing.lastPrice", price);
        setObjVar(seller, "npc.simProfile.vendor.listing.lastSource", source != null ? source : "");
        setObjVar(seller, "npc.simProfile.vendor.listing.feeWaived", success ? 1 : 0);
        String category = isValidId(listingItem) && exists(listingItem) && hasObjVar(listingItem, "vendor.listingCategory") ? getStringObjVar(listingItem, "vendor.listingCategory") : "misc";
        String categoryLabel = getShortageCategoryLabel(category);
        setObjVar(seller, "npc.simProfile.vendor.listing.lastCategory", category);
        setObjVar(seller, "npc.simProfile.vendor.listing.lastCategoryLabel", categoryLabel);
        if (success)
        {
            setObjVar(seller, "npc.simProfile.vendor.listing.listing_created", Math.max(0, getIntObjVar(seller, "npc.simProfile.vendor.listing.listing_created")) + 1);
        }
        else
        {
            setObjVar(seller, "npc.simProfile.vendor.listing.listing_failed", Math.max(0, getIntObjVar(seller, "npc.simProfile.vendor.listing.listing_failed")) + 1);
        }

        if (isValidId(listingItem))
        {
            setObjVar(seller, "npc.simProfile.vendor.listing.lastItem", listingItem);
        }
        else
        {
            removeObjVar(seller, "npc.simProfile.vendor.listing.lastItem");
        }

        utils.setScriptVar(seller, "npc.simProfile.lastSystem", "vendor");
        utils.setScriptVar(seller, "npc.simProfile.lastAction", "handleNpcVendorListing");
        utils.setScriptVar(seller, "npc.simProfile.lastSuccess", success ? 1 : 0);
        utils.setScriptVar(seller, "npc.simProfile.lastDetail", detail);
        utils.setScriptVar(seller, "npc.simProfile.lastTimestamp", now);
        utils.setScriptVar(seller, "npc.simProfile.vendor.listing.lastCategoryLabel", categoryLabel);
        logStabilizationAction(success ? "STAB_LIST_RESULT_OK" : "STAB_LIST_RESULT_FAIL", vendor, seller, category, detail);
    }

    private static void writeNpcVendorOutcome(obj_id customer, obj_id vendor, int amount, boolean success, String detail, obj_id delivered) throws InterruptedException
    {
        int now = getGameTime();
        String soldCategory = isValidId(delivered) && exists(delivered) ? getListingCategoryForObject(delivered) : "misc";
        String soldCategoryLabel = getShortageCategoryLabel(soldCategory);
        setObjVar(customer, "npc.simProfile.lastSystem", "vendor");
        setObjVar(customer, "npc.simProfile.lastAction", "handleNpcVendorPurchase");
        setObjVar(customer, "npc.simProfile.lastSuccess", success ? 1 : 0);
        setObjVar(customer, "npc.simProfile.lastDetail", detail);
        setObjVar(customer, "npc.simProfile.lastTimestamp", now);

        setObjVar(customer, "npc.simProfile.vendor.lastVendor", vendor);
        setObjVar(customer, "npc.simProfile.vendor.lastAmount", amount);
        setObjVar(customer, "npc.simProfile.vendor.lastSuccess", success ? 1 : 0);
        setObjVar(customer, "npc.simProfile.vendor.lastDetail", detail);
        setObjVar(customer, "npc.simProfile.vendor.lastTimestamp", now);
        setObjVar(customer, "npc.simProfile.vendor.lastCategory", soldCategory);
        setObjVar(customer, "npc.simProfile.vendor.lastCategoryLabel", soldCategoryLabel);

        if (isValidId(delivered))
        {
            setObjVar(customer, "npc.simProfile.vendor.lastItem", delivered);
        }
        else
        {
            removeObjVar(customer, "npc.simProfile.vendor.lastItem");
        }
        utils.setScriptVar(customer, "npc.simProfile.lastSystem", "vendor");
        utils.setScriptVar(customer, "npc.simProfile.lastAction", "handleNpcVendorPurchase");
        utils.setScriptVar(customer, "npc.simProfile.lastSuccess", success ? 1 : 0);
        utils.setScriptVar(customer, "npc.simProfile.lastDetail", detail);
        utils.setScriptVar(customer, "npc.simProfile.lastTimestamp", now);
        utils.setScriptVar(customer, "npc.simProfile.vendor.lastSuccess", success ? 1 : 0);
        utils.setScriptVar(customer, "npc.simProfile.vendor.lastDetail", detail);
        utils.setScriptVar(customer, "npc.simProfile.vendor.lastTimestamp", now);
        utils.setScriptVar(customer, "npc.simProfile.vendor.lastCategoryLabel", soldCategoryLabel);
    }

    private static void validateEconomyCategoryTables(obj_id context) throws InterruptedException
    {
        obj_id validationObj = obj_id.NULL_ID;
        if (isValidId(context) && exists(context))
        {
            validationObj = getEconomyStateObject(context);
            if (!isValidId(validationObj) || !exists(validationObj))
            {
                validationObj = context;
            }
        }
        if (isValidId(validationObj) && exists(validationObj))
        {
            int version = hasObjVar(validationObj, ECONOMY_VALIDATION_OBJVAR + ".version") ? getIntObjVar(validationObj, ECONOMY_VALIDATION_OBJVAR + ".version") : 0;
            if (version >= ECONOMY_VALIDATION_VERSION)
            {
                return;
            }
        }

        for (int i = 0; i < SUPPORTED_LISTING_CATEGORIES.length; i++)
        {
            String category = SUPPORTED_LISTING_CATEGORIES[i];
            String rootCategory = getEconomyCategoryRoot(category);

            dictionary demandRow = dataTableGetRow(TBL_CATEGORY_DEMAND_COEFFICIENTS, category);
            if ((demandRow == null || demandRow.isEmpty()) && !category.equals(rootCategory))
            {
                demandRow = dataTableGetRow(TBL_CATEGORY_DEMAND_COEFFICIENTS, rootCategory);
            }
            if (demandRow == null || demandRow.isEmpty())
            {
                logStabilizationAction("STAB_TABLE_WARN_DEMAND", context, obj_id.NULL_ID, category, "missing_row_fallback_misc");
            }

            dictionary shortageRow = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, category);
            if ((shortageRow == null || shortageRow.isEmpty()) && !category.equals(rootCategory))
            {
                shortageRow = dataTableGetRow(TBL_SHORTAGE_INCENTIVE_RULES, rootCategory);
            }
            if (shortageRow == null || shortageRow.isEmpty())
            {
                logStabilizationAction("STAB_TABLE_WARN_SHORTAGE", context, obj_id.NULL_ID, category, "missing_row_fallback_misc");
            }
        }

        if (isValidId(validationObj) && exists(validationObj))
        {
            setObjVar(validationObj, ECONOMY_VALIDATION_OBJVAR + ".version", ECONOMY_VALIDATION_VERSION);
            setObjVar(validationObj, ECONOMY_VALIDATION_OBJVAR + ".time", getGameTime());
        }
    }

    public static boolean blog(String msg) throws InterruptedException
    {
        if (LOGGING_ON && msg != null && !msg.equals(""))
        {
            LOG(LOGGING_CATEGORY, msg);
        }
        return true;
    }

    private static int clamp(int value, int min, int max)
    {
        if (max < min)
        {
            int swap = min;
            min = max;
            max = swap;
        }
        return Math.max(min, Math.min(max, value));
    }
}
