package script.systems.missions.base;

import script.*;
import script.library.ai_lib;
import script.library.prose;
import script.library.slicing;
import script.library.structure;
import script.library.utils;

/**
 * SWG+ mission terminal
 * - Backward compatible: methods & menu types unchanged
 * - Safer slicing: cooldown helpers, concurrency guard, status feedback
 * - Configurable cooldown via objvar/skill, robust time tracking
 * - Optional "Redeem" hook (disabled unless objvar is set)
 * - Clean init: heading restore, stale objvars cleared, map attach
 */
public class mission_terminal extends script.base_script
{
    public mission_terminal() {}

    // ====== UI strings (legacy) ======
    public static final string_id SID_MNU_REDEEM       = new string_id("sui", "mnu_redeem");
    public static final string_id SID_REDEEM_PROMPT    = new string_id("sui", "redeem_data_item_prompt");
    public static final string_id SID_REDEEM_TITLE     = new string_id("sui", "redeem_data_item_title");
    public static final string_id SID_SLICE            = new string_id("slicing/slicing", "slice");
    public static final string_id SID_FAIL_SLICE       = new string_id("slicing/slicing", "terminal_fail");
    public static final string_id SID_SUCCESS_SLICE    = new string_id("slicing/slicing", "terminal_success");
    public static final string_id SID_NOT_YET          = new string_id("slicing/slicing", "not_yet");

    // ====== SWG+ config/keys (non-breaking) ======
    private static final String LOGP                     = "[MissionTerminal] ";
    private static final String OVAR_HEADING             = structure.VAR_TERMINAL_HEADING;
    private static final String SVAR_TERM_TIME           = "slicing.terminal_time";      // per-player cooldown mark (seconds)
    private static final String SVAR_TERM_LOCK           = "slicing.terminal_lock";      // on terminal: obj_id of slicer
    private static final String SVAR_TERM_BONUS          = "slicing.terminal_bonus";     // per-player reward multiplier
    private static final String OVAR_REDEEM_ENABLED      = "terminal.redeem.enabled";    // bool gate for Redeem menu
    private static final String OVAR_COOLDOWN_OVERRIDE   = "terminal.slice.cooldown";    // int seconds override (default 120)
    private static final String OVAR_BONUS_MULT_OVERRIDE = "terminal.slice.bonusMult";   // float override (default 1.5)
    private static final String OVAR_DEBUG               = "terminal.debug";             // enable console logging

    private static final int    BASE_COOLDOWN_SEC        = 120;   // legacy behavior
    private static final float  BASE_BONUS_MULT          = 1.5f;  // legacy behavior

    // ====== Lifecycle ======
    
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, OVAR_HEADING))
            setYaw(self, getFloatObjVar(self, OVAR_HEADING));

        // Clean any stale state from prior boots/crashes
        removeObjVar(self, "slice_start");
        removeObjVar(self, "sliced_by");
        utils.removeScriptVarTree(self, "slicing."); // clear terminal_* lock if left behind

        attachScript(self, "planet_map.map_loc_attach");
        return SCRIPT_CONTINUE;
    }

    // ====== Radial menu ======
    
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!isDead(player) && !isIncapacitated(player))
        {
            // Redeem (optional; only when enabled)
            if (hasObjVar(self, OVAR_REDEEM_ENABLED) && getIntObjVar(self, OVAR_REDEEM_ENABLED) != 0)
            {
                mi.addRootMenu(menu_info_types.SERVER_MENU1, SID_MNU_REDEEM);
            }

            // Slice (smugglers or anyone with your chosen skill gate)
            if (hasSkill(player, "class_smuggler_phase1_novice"))
            {
                mi.addRootMenu(menu_info_types.SERVER_MENU2, SID_SLICE);
            }

            // Ensure the standard mission list entry exists for terminals that need it
            menu_info_data mid = mi.getMenuItemByType(menu_info_types.MISSION_TERMINAL_LIST);
            if (mid == null)
            {
                mid = mi.getMenuItemByType(menu_info_types.ITEM_USE);
                if (mid == null)
                {
                    mi.addRootMenu(menu_info_types.MISSION_TERMINAL_LIST, new string_id("", ""));
                }
            }
        }
        return SCRIPT_CONTINUE;
    }

    // ====== Menu selection ======
    
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item == menu_info_types.SERVER_MENU1)
        {
            // Optional redeem flow (only active when OVAR_REDEEM_ENABLED != 0)
            if (hasObjVar(self, OVAR_REDEEM_ENABLED) && getIntObjVar(self, OVAR_REDEEM_ENABLED) != 0)
            {
                // Hook point: you can handle redeem logic in a sibling script or here via messageTo
                dictionary d = new dictionary();
                d.put("player", player);
                messageTo(self, "handleRedeem", d, 0.0f, false);
            }
        }
        else if (item == menu_info_types.SERVER_MENU2)
        {
            // Slicing flow
            if (!hasSkill(player, "class_smuggler_phase1_novice"))
                return SCRIPT_CONTINUE;

            if (utils.hasScriptVar(player, "slicing.slice_item"))
            {
                sendSystemMessage(player, slicing.SID_ALREADY_SLICING);
                return SCRIPT_CONTINUE;
            }

            // Concurrency guard: one slicer at a time per terminal
            if (utils.hasScriptVar(self, SVAR_TERM_LOCK))
            {
                obj_id cur = utils.getObjIdScriptVar(self, SVAR_TERM_LOCK);
                if (isIdValid(cur) && cur != player)
                {
                    sendSystemMessage(player, new string_id("slicing/slicing", "terminal_busy"));
                    debug(self, "slice denied: busy by " + cur);
                    return SCRIPT_CONTINUE;
                }
            }

            // Cooldown check (per-player)
            int now = getGameTime();
            int last = utils.getIntScriptVar(player, SVAR_TERM_TIME);
            int cd = computeCooldownSeconds(self, player);
            if (last > 0 && now < (last + cd))
            {
                int wait = (last + cd) - now;
                prose_package pp = prose.getPackage(SID_NOT_YET, wait);
                sendSystemMessageProse(player, pp);
                return SCRIPT_CONTINUE;
            }

            // Mark lock & start slicing
            utils.setScriptVar(self, SVAR_TERM_LOCK, player);
            slicing.startSlicing(player, self, "finishSlicing", "terminal");
            if (!hasObjVar(self, "sliced_by") || getObjIdObjVar(self, "sliced_by") != player)
            {
                utils.removeScriptVar(self, SVAR_TERM_LOCK);
                return SCRIPT_CONTINUE;
            }
            debug(self, "slice started by " + player + " cd=" + cd + "s");
        }
        return SCRIPT_CONTINUE;
    }

    // ====== Slicing callback ======
    public int finishSlicing(obj_id self, dictionary params) throws InterruptedException
    {
        try
        {
            if (params == null) return SCRIPT_CONTINUE;

            int success   = params.getInt("success");
            obj_id player = params.getObjId("player");
            if (!isIdValid(player)) return SCRIPT_CONTINUE;

            // Always set cooldown timestamp (even on fail)
            utils.setScriptVar(player, SVAR_TERM_TIME, getGameTime());

            // Clear lock if we own it
            if (utils.hasScriptVar(self, SVAR_TERM_LOCK))
            {
                obj_id cur = utils.getObjIdScriptVar(self, SVAR_TERM_LOCK);
                if (cur == player) utils.removeScriptVar(self, SVAR_TERM_LOCK);
            }

            if (success == 1)
            {
                sendSystemMessage(player, SID_SUCCESS_SLICE);

                // Reward multiplier (kept legacy default 1.5, but configurable & skill-boostable)
                float mult = computeBonusMultiplier(self, player);
                utils.setScriptVar(player, SVAR_TERM_BONUS, mult);

                // Optional: you can set a TTL objvar/scriptvar for external consumers
                // utils.setScriptVar(player, "slicing.terminal_bonus_ttl", getGameTime() + 600);

                // Optional toast with prose
                prose_package pp = prose.getPackage(new string_id("slicing/slicing", "terminal_bonus_applied"), (int)(mult * 100));
                sendSystemMessageProse(player, pp);

                debug(self, "slice success by " + player + " mult=" + mult);
            }
            else
            {
                sendSystemMessage(player, SID_FAIL_SLICE);
                debug(self, "slice failed by " + player);
            }
        }
        finally
        {
            // Hard clean of lock if anything went weird
            if (utils.hasScriptVar(self, SVAR_TERM_LOCK))
            {
                utils.removeScriptVar(self, SVAR_TERM_LOCK);
            }
        }
        return SCRIPT_CONTINUE;
    }

    // ====== Optional redeem handler hook (safe no-op by default) ======
    public int handleRedeem(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id player = params != null ? params.getObjId("player") : null;
        if (!isIdValid(player)) return SCRIPT_CONTINUE;

        // This is a hook point. You can:
        // - scan inventory for vouchers,
        // - validate objvars,
        // - pay out rewards / tokens,
        // - or forward to a specific script.
        // For now we just show a prompt to indicate the terminal supports redeem.
        sendSystemMessage(player, SID_REDEEM_PROMPT);
        debug(self, "redeem invoked by " + player);
        return SCRIPT_CONTINUE;
    }

    public int handleNpcMissionRequest(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id npc = params != null ? params.getObjId("player") : null;
        if (!isIdValid(npc) || !ai_lib.isNpc(npc))
        {
            if (isIdValid(npc))
            {
                writeNpcMissionOutcome(npc, self, false, "sender_not_npc", obj_id.NULL_ID);
            }
            return SCRIPT_CONTINUE;
        }

        obj_id[] missionList = getMissionObjects(npc);
        if (missionList != null && missionList.length >= 2)
        {
            writeNpcMissionOutcome(npc, self, false, "mission_cap_reached", obj_id.NULL_ID);
            return SCRIPT_CONTINUE;
        }

        obj_id mission = createMissionObjectInCreatureMissionBag(npc);
        if (!isIdValid(mission))
        {
            writeNpcMissionOutcome(npc, self, false, "mission_create_failed", obj_id.NULL_ID);
            return SCRIPT_CONTINUE;
        }

        setObjVar(mission, "npc.simProfile.generatedBy", self);
        setObjVar(mission, "npc.simProfile.generatedAt", getGameTime());
        if (params != null)
        {
            String step = params.getString("step");
            if (step != null && step.length() > 0)
            {
                setObjVar(mission, "npc.simProfile.step", step);
            }
        }

        writeNpcMissionOutcome(npc, self, true, "mission_assigned", mission);
        return SCRIPT_CONTINUE;
    }

    // ====== Helpers ======
    private int computeCooldownSeconds(obj_id self, obj_id player) throws InterruptedException
    {
        int base = BASE_COOLDOWN_SEC;
        if (hasObjVar(self, OVAR_COOLDOWN_OVERRIDE))
            base = Math.max(0, getIntObjVar(self, OVAR_COOLDOWN_OVERRIDE));

        // Optional: skill-based reduction (define a mod in your skill data if desired)
        int mod = 0;
        try { mod = getSkillStatisticModifier(player, "slicing_cooldown_reduction"); } catch (Throwable ignored) {}
        // Smuggler master? shave a bit more (safe if skill path doesn’t exist)
        if (hasSkill(player, "class_smuggler_phase4_master")) mod += 30;

        int cd = Math.max(0, base - Math.max(0, mod));
        return cd;
    }

    private float computeBonusMultiplier(obj_id self, obj_id player) throws InterruptedException
    {
        float mult = BASE_BONUS_MULT;
        if (hasObjVar(self, OVAR_BONUS_MULT_OVERRIDE))
            mult = getFloatObjVar(self, OVAR_BONUS_MULT_OVERRIDE);

        // Optional: give tiny bonus if they have a high slicing mod
        int sliceMod = 0;
        try { sliceMod = getSkillStatisticModifier(player, "slicing_bonus_percent"); } catch (Throwable ignored) {}
        if (sliceMod > 0)
        {
            mult += (sliceMod / 100.0f); // e.g., 10 → +0.10
        }
        // Clamp to sane bounds
        if (mult < 1.0f) mult = 1.0f;
        if (mult > 3.0f) mult = 3.0f;
        return mult;
    }

    private void writeNpcMissionOutcome(obj_id npc, obj_id terminal, boolean success, String detail, obj_id mission) throws InterruptedException
    {
        int now = getGameTime();
        setObjVar(npc, "npc.simProfile.lastSystem", "mission");
        setObjVar(npc, "npc.simProfile.lastAction", "handleNpcMissionRequest");
        setObjVar(npc, "npc.simProfile.lastSuccess", success ? 1 : 0);
        setObjVar(npc, "npc.simProfile.lastDetail", detail);
        setObjVar(npc, "npc.simProfile.lastTimestamp", now);
        setObjVar(npc, "npc.simProfile.mission.lastTerminal", terminal);
        setObjVar(npc, "npc.simProfile.mission.lastSuccess", success ? 1 : 0);
        setObjVar(npc, "npc.simProfile.mission.lastDetail", detail);
        setObjVar(npc, "npc.simProfile.mission.lastTimestamp", now);
        utils.setScriptVar(npc, "npc.simProfile.lastSystem", "mission");
        utils.setScriptVar(npc, "npc.simProfile.lastAction", "handleNpcMissionRequest");
        utils.setScriptVar(npc, "npc.simProfile.lastSuccess", success ? 1 : 0);
        utils.setScriptVar(npc, "npc.simProfile.lastDetail", detail);
        utils.setScriptVar(npc, "npc.simProfile.lastTimestamp", now);
        utils.setScriptVar(npc, "npc.simProfile.mission.lastSuccess", success ? 1 : 0);
        utils.setScriptVar(npc, "npc.simProfile.mission.lastDetail", detail);
        utils.setScriptVar(npc, "npc.simProfile.mission.lastTimestamp", now);
        if (isIdValid(mission))
        {
            setObjVar(npc, "npc.simProfile.mission.lastMission", mission);
        }
        else
        {
            removeObjVar(npc, "npc.simProfile.mission.lastMission");
        }
    }

    private void debug(obj_id self, String msg) throws InterruptedException
    {
        if (hasObjVar(self, OVAR_DEBUG) && getIntObjVar(self, OVAR_DEBUG) != 0)
            debugServerConsoleMsg(self, LOGP + msg);
    }
}
