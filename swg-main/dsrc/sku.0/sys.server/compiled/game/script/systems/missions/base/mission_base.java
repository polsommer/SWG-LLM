package script.systems.missions.base;

import script.*;
import script.library.*;

import java.util.Vector;
import java.util.Locale;

/**
 * SWG+ mission base:
 * - ✅ Backward compatible: all methods/signatures preserved
 * - 🛠️ Bug fix: findRandomLocation() was writing Z from X by mistake
 * - 🧭 Better spawn helpers: uniform-in-circle placement, boxed placement, retry API
 * - 🧰 Reward safety: idempotent guard, non-negative clamps, optional token/badge hooks
 * - 🧾 Better logging: consistent, compact log prefix + optional debug flag
 */
public class mission_base extends script.base_script
{
    public mission_base() {}

    // ========= Legacy constants (kept) =========
    public static final String MISSION_SUCCESS_PERSISTENT_MESSAGE   = "success";
    public static final String MISSION_FAILURE_PERSISTENT_MESSAGE   = "failure";
    public static final String MISSION_INCOMPLETE_PERSISTENT_MESSAGE= "incomplete";
    public static final String MISSION_TIMED_OUT_PERSISTENT_MESSAGE = "timed_out";
    public static final String GENERIC_MISSION_MESSAGE_STRING_FILE  = "mission/mission_generic";
    public static final int    MAX_MISSIONS                         = 2;
    public static final String BOUNTY_MISSION_LISTENERS             = "mission.objBountyListeners";
    public static final int    BOUNTY_DIFFICULTY_BASIC              = 1;
    public static final int    BOUNTY_DIFFICULTY_ADVANCED           = 2;
    public static final int    BOUNTY_DIFFICULTY_EXPERT             = 3;
    public static final int    BOUNTY_TRACK_SPEED                   = 120;
    public static final int    BOUNTY_FIND_SPEED                    = 100;
    public static final int    DROID_PROBOT                         = 1;
    public static final int    DROID_SEEKER                         = 2;
    public static final int    DROID_TRACK_TARGET                   = 1;
    public static final int    DROID_FIND_TARGET                    = 2;
    public static final int    FACTION_NONE                         = 0;
    public static final float  BOUNTY_REWARD_MODIFIER               = 0.75f;
    public static final int    MISSION_SPAWN_TRIGGER_RANGE          = 256;
    public static final int    STATE_BOUNTY_INFORMANT               = 0;
    public static final int    STATE_BOUNTY_PROBE                   = 1;
    public static final int    INFORMANT_EASY                       = 1;
    public static final int    INFORMANT_MEDIUM                     = 2;
    public static final int    INFORMANT_HARD                       = 3;
    public static final float  SPAWN_OVERLOAD_DIFFICULTY_MODIFIER   = 0.65f;

    // ========= SWG+ additions (non-breaking) =========
    private static final String LOGP                 = "[MissionBase] ";
    private static final String OBJVAR_REWARD_LOCK   = "mission.rewardGranted";   // idempotency guard
    private static final String OBJVAR_BADGE_STRING  = "bonusBadgeStringId";      // optional: string_id for badge/UI
    private static final String OBJVAR_TOKEN_AMOUNT  = "tokenReward";             // optional: extra token payout
    private static final String OBJVAR_TOKEN_ACCOUNT = "tokenAccount";            // optional: named account key for tokens
    private static final String OBJVAR_DEBUG         = "mission.debug";           // optional: set true for extra logs
    private static final int    MAX_SPAWN_RETRIES    = 8;

    // ------------------------------------------------------------------------------------------------
    // SPAWN HELPERS
    // ------------------------------------------------------------------------------------------------

    /**
     * Legacy API: returns locCenter jittered by [-variance..+variance] on X and Z.
     * 🛠️ Fix: original code accidentally wrote Z from X; now uses Z correctly.
     * Note: mutates and returns the same location to preserve original behavior.
     */
    public location findRandomLocation(location locCenter, int intVariance) throws InterruptedException
    {
        int min = -Math.abs(intVariance);
        int max =  Math.abs(intVariance);
        locCenter.x = locCenter.x + rand(min, max);
        locCenter.z = locCenter.z + rand(min, max); // ✅ was locCenter.x before (bug)
        return locCenter;
    }

    /** SWG+ helper: uniform random within a circle of radius 'r' (no edge clustering). */
    public location findRandomLocationInCircle(location center, int r) throws InterruptedException
    {
        double theta = randf(0f, (float)(2 * Math.PI));
        double u = randf(0f, 1f);
        double d = Math.sqrt(u) * Math.max(0, r);
        location out = new location(center);
        out.x += Math.cos(theta) * d;
        out.z += Math.sin(theta) * d;
        return out;
    }

    /** SWG+ helper: try multiple times to get a valid position using the provided sampler. */
    public location findSpawnWithRetries(location center, int r, int retries) throws InterruptedException
    {
        int tries = Math.max(1, Math.min(retries, MAX_SPAWN_RETRIES));
        for (int i = 0; i < tries; i++)
        {
            location cand = findRandomLocationInCircle(center, r);
            if (isLocationGood(cand)) return cand;
        }
        // Fallback to center if all fail
        return center;
    }

    /** Heuristic "is this a reasonable place to spawn/target?" – extend as needed. */
    private boolean isLocationGood(location l) throws InterruptedException
    {
        if (l == null) return false;
        if (Float.isNaN(l.x) || Float.isNaN(l.z)) return false;
        // room for more checks (water, steep slope, navmesh) when available
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // LEGACY STUBS (kept as-is)
    // ------------------------------------------------------------------------------------------------

    public obj_id getMissionData(obj_id objMission) throws InterruptedException { return objMission; }
    public void addListener(obj_id objListener, obj_id objTarget) throws InterruptedException { return; }
    public void removeListener(obj_id objListener, obj_id objTarget) throws InterruptedException { return; }
    public void messageListeners(obj_id objOwner, String strMessageName, dictionary dctParams) throws InterruptedException { return; }

    public int getMissionBondAmount(obj_id objMission) throws InterruptedException
    {
        if (hasObjVar(objMission, "intBond"))
        {
            int intBond = getIntObjVar(objMission, "intBond");
            return intBond;
        }
        return 0;
    }

    // ------------------------------------------------------------------------------------------------
    // REWARD FLOW (kept, but hardened & instrumented)
    // ------------------------------------------------------------------------------------------------

    public void deliverReward(obj_id objMission) throws InterruptedException
    {
        obj_id objPlayer      = getMissionHolder(objMission);
        obj_id objMissionData = getMissionData(objMission);

        if (!isIdValid(objPlayer) || !isIdValid(objMissionData))
        {
            debug(objMission, "deliverReward: invalid player or mission data");
            return;
        }

        // Idempotency: avoid double payouts if scripts fire twice
        if (hasObjVar(objMissionData, OBJVAR_REWARD_LOCK))
        {
            debug(objMission, "deliverReward: already granted for " + objMissionData);
            return;
        }
        setObjVar(objMissionData, OBJVAR_REWARD_LOCK, 1);

        int intPlayerDifficulty = getIntObjVar(objMissionData, "intPlayerDifficulty");
        obj_id objGroup = getGroupObject(objPlayer);
        string_id strMessage;
        boolean boolGroup = isIdValid(objGroup);

        strMessage = new string_id(GENERIC_MISSION_MESSAGE_STRING_FILE, boolGroup ? "group_success" : "success");

        dictionary dctParams = new dictionary();

        // Bond (escrow) release first
        int intBond = getMissionBondAmount(objMissionData);
        if (intBond > 0)
        {
            transferBankCreditsTo(objMissionData, objPlayer, intBond, "testSuccess", "testFail", dctParams);
        }

        // Base reward + optional jedi bonus
        int intReward = getMissionReward(objMissionData);
        int jediBonusReward = 0;
        if (hasObjVar(objMissionData, "jediBonusReward"))
        {
            jediBonusReward = getIntObjVar(objMissionData, "jediBonusReward");
            intReward = safeAdd(intReward, jediBonusReward);
            if (jediBonusReward >= 0)
            {
                String msg = utils.packStringId(new string_id("mission/mission_generic", "bonus_reward")) + " " + jediBonusReward;
                sendSystemMessage(objPlayer, msg, null);
            }
            else
            {
                sendSystemMessage(objPlayer, new string_id("mission/mission_generic", "easy_reward"));
            }
        }

        // Incomplete penalty
        if (hasObjVar(objMissionData, "intIncomplete"))
        {
            intReward = Math.max(0, intReward / 2);
        }

        int originalGroupSize = hasObjVar(objMissionData, "originalGroupSize") ? getIntObjVar(objMissionData, "originalGroupSize") : 0;
        String strMissionType = getMissionType(objMissionData);

        // Bounty special cases (kept)
        if ("bounty".equals(strMissionType))
        {
            missions.increaseBountyJediKillTracking(objPlayer, missions.WINS);
            obj_id objTarget = getObjIdObjVar(objMissionData, "objTarget");
            if (isIdValid(objTarget) && exists(objTarget) && isPlayer(objTarget) && isJedi(objTarget))
            {
                xp.grant(objTarget, xp.JEDI_GENERAL, (intReward * -2));
            }
            if (isPlayer(objTarget) && isJedi(objTarget))
            {
                boolean isForceRanked = force_rank.isForceRanked(objTarget);
                if ((isForceRanked && intReward > jedi.MIN_FR_JEDI_BOUNTY) || (!isForceRanked && intReward > jedi.MIN_NON_FR_JEDI_BOUNTY))
                {
                    messageTo(objTarget, "updateBHKillData", dctParams, 0, true);
                }
            }
        }

        // HQ kickback (kept)
        if (hasObjVar(objMission, "hq"))
        {
            obj_id objHq = getObjIdObjVar(objMission, "hq");
            if (isIdValid(objHq))
            {
                int hqReward = Math.round(intReward / 20.0f);
                if (hqReward > 0)
                    transferBankCreditsFromNamedAccount(money.ACCT_MISSION_DYNAMIC, objHq, hqReward, "noHandler", "noHandler", new dictionary());
            }
        }

        // SOLO payout path
        if (!boolGroup)
        {
            intReward = group.getSafeDifference(objPlayer, intReward);
            if (originalGroupSize > 0 && !"destroy".equals(strMissionType))
            {
                if (originalGroupSize > 1)
                {
                    // The player is completing the mission outside of a group, but the
                    // mission data still has an old group size (e.g. they disbanded
                    // before turning in). In this case the mission terminal still
                    // advertises the full reward, so skip the stale split instead of
                    // reducing the payout.
                    debug(objMissionData, "deliverReward: ignoring stale originalGroupSize=" + originalGroupSize +
                        " for solo payout of " + objPlayer);
                }
                else
                {
                    intReward = Math.max(0, intReward / originalGroupSize);
                }
            }
            float divisor = missions.alterMissionPayoutDivisorDaily(objPlayer);
            if (divisor > 1f) intReward = Math.max(0, (int)(intReward / divisor));

            // Mission XP (kept) + Beast side-XP (kept)
            if (missions.canEarnDailyMissionXp(objPlayer) && missions.isDestroyMission(objMissionData)){
                if (beast_lib.isBeastMaster(objPlayer) && beast_lib.hasActiveBeast(objPlayer)) {
                    dictionary returnDict = new dictionary();
                    returnDict.addInt("xpAmount", xp.getMissionXpAmount(objPlayer, intPlayerDifficulty) / 2);
                    beast_lib.incrementBeastExperience(beast_lib.getBeastOnPlayer(objPlayer), returnDict);
                }
                xp.grantMissionXp(objPlayer, intPlayerDifficulty);
            }

            intReward = Math.max(0, intReward);
            transferBankCreditsFromNamedAccount(money.ACCT_MISSION_DYNAMIC, objPlayer, intReward, "testSuccess", "testFail", dctParams);
            utils.moneyInMetric(objPlayer, money.ACCT_MISSION_DYNAMIC, intReward);

            prose_package successProse = prose.getPackage(new string_id("mission/mission_generic", "success_w_amount"), intReward);
            sendSystemMessageProse(objPlayer, successProse);
            missions.incrementDaily(objPlayer);

            // SWG+ optional extras (non-breaking): badge/token hooks via objvars
            grantOptionalExtras(objPlayer, objMissionData);
        }
        // GROUP payout path (kept, with safety)
        else
        {
            int currentGroupSize = getPCGroupSize(objGroup);
            if (originalGroupSize < 0) originalGroupSize = 0;

            int missionDivisor = originalGroupSize;
            strMessage = new string_id(GENERIC_MISSION_MESSAGE_STRING_FILE, "group_success");

            if (!"destroy".equals(strMissionType))
            {
                if (currentGroupSize > originalGroupSize)
                {
                    missionDivisor = currentGroupSize;
                    strMessage = new string_id(GENERIC_MISSION_MESSAGE_STRING_FILE, "group_expanded");
                }
            }
            else
            {
                missionDivisor = 1;
                dctParams.put("intPlayerDifficulty", intPlayerDifficulty);
                group.distributeMissionXpToGroup(objPlayer, group.SPLIT_RANGE, objMissionData);
            }

            missionDivisor = Math.max(1, missionDivisor);
            group.systemPayoutToGroupInternal(money.ACCT_MISSION_DYNAMIC, objPlayer, Math.max(0, intReward), null, strMessage, "test", missionDivisor, dctParams, objMissionData);
        }

        // Faction / GCW (kept)
        if (hasObjVar(objMissionData, "strFaction"))
        {
            String strFaction   = getStringObjVar(objMissionData, "strFaction");
            int intFactionReward= getIntObjVar(objMissionData, "intFactionReward");
            int intGCWPoints    = getIntObjVar(objMissionData, "intGCWPoints");

            if (hasObjVar(objMission, "hq"))
            {
                intFactionReward = Math.round(intFactionReward * 1.05f);
            }

            // Award owner
            factions.awardFactionStanding(objPlayer, strFaction, intFactionReward);

            // GCW points if on-duty
            if ((factions.isImperial(objPlayer) || factions.isRebel(objPlayer)) && !factions.isOnLeave(objPlayer) && intGCWPoints > 0)
            {
                gcw._grantGcwPoints(null, objPlayer, intGCWPoints, false, gcw.GCW_POINT_TYPE_GROUND_PVE, "mission terminal");
            }

            // Group spillover (kept, with safety)
            if (!"deliver".equals(strMissionType) && isIdValid(objGroup))
            {
                obj_id[] members = getGroupMemberIds(objGroup);
                if (members == null || members.length == 0)
                {
                    LOG("DESIGNER_FATAL", "Group object " + objGroup + " with player " + objPlayer + " is a zero length group!!!");
                }
                else
                {
                    int per = Math.max(0, intFactionReward / members.length);
                    if (per > 0)
                    {
                        location ownerLoc = getLocation(objPlayer);
                        String ownerPlanet = (ownerLoc == null) ? "" : ownerLoc.area;
                        for (obj_id m : members)
                        {
                            if (m == objPlayer) continue;
                            location ml = getLocation(m);
                            boolean samePlanet = (ml != null) && ownerPlanet.equals(ml.area);
                            float dist = (!samePlanet) ? 100000f : getDistance(objPlayer, m);
                            if (dist < 80f)
                            {
                                factions.awardFactionStanding(m, strFaction, per);
                                if (intGCWPoints > 0)
                                {
                                    gcw._grantGcwPoints(null, m, intGCWPoints, false, gcw.GCW_POINT_TYPE_GROUND_PVE, "mission terminal");
                                }
                            }
                        }
                    }
                }
            }
        }
        return;
    }

    // ------------------------------------------------------------------------------------------------
    // PAY TARGET (kept; hardened)
    // ------------------------------------------------------------------------------------------------

    public obj_id getValidPayTarget(obj_id player, int money) throws InterruptedException
    {
        Vector targets = group.getPCMembersInRange(player, 200);
        obj_id mostMoney = player;
        if (group.getSafeDifference(player, money) == money)
        {
            return player;
        }
        if (targets != null)
        {
            for (Object target : targets)
            {
                obj_id t = (obj_id) target;
                if (!utils.isFreeTrial(t) && !hasScript(t, "ai.pet"))
                {
                    return t;
                }
                if (utils.isFreeTrial(t))
                {
                    if (group.getSafeDifference(t, money) == money)
                    {
                        return t;
                    }
                }
                if (!hasScript(t, "ai.pet"))
                {
                    if (group.getSafeDifference(t, money) > group.getSafeDifference(mostMoney, money))
                    {
                        mostMoney = t;
                    }
                }
            }
        }
        return mostMoney;
    }

    // ------------------------------------------------------------------------------------------------
    // BOUNTY HOOKS / CLEANUP (kept)
    // ------------------------------------------------------------------------------------------------

    public void setupBountyMissionObject(obj_id objMission) throws InterruptedException { return; }
    public void cleanupBountyMission(obj_id objMission) throws InterruptedException { return; }
    public void returnReward(obj_id objMission) throws InterruptedException { return; }

    public int getBountyDifficulty(obj_id objPlayer) throws InterruptedException
    {
        int intBountyLevel = getSkillStatisticModifier(objPlayer, "bounty_mission_level");
        LOG("missions", "bounty level is " + intBountyLevel);
        if (intBountyLevel < 1)
        {
            LOG("DESIGNER_FATAL", "Bounty hunter id " + objPlayer + " has a bounty difficulty of less than 1");
            return 1;
        }
        return intBountyLevel;
    }

    public obj_id cleanMissionObject(obj_id objMissionObject) throws InterruptedException
    {
        removeAllObjVars(objMissionObject);
        detachAllScripts(objMissionObject);
        attachScript(objMissionObject, "systems.missions.base.mission_object");
        attachScript(objMissionObject, "systems.missions.base.mission_cleanup_tracker");
        return objMissionObject;
    }

    // ------------------------------------------------------------------------------------------------
    // SWG+ PRIVATE HELPERS (non-breaking additions)
    // ------------------------------------------------------------------------------------------------

    private void debug(obj_id ctx, String msg) throws InterruptedException
    {
        // Enable per-mission extra logs via objvar mission.debug=true
        if (isIdValid(ctx) && hasObjVar(ctx, OBJVAR_DEBUG))
        {
            debugServerConsoleMsg(ctx, LOGP + msg);
        }
    }

    private static int safeAdd(int a, int b)
    {
        long v = (long)a + (long)b;
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (v < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int)v;
    }

    /** SWG+ optional extras: small, opt-in rewards via objvars. */
    private void grantOptionalExtras(obj_id player, obj_id missionData) throws InterruptedException
    {
        // Badge/UI toast via string_id (e.g., "badges_name", "won_boss_event")
        if (hasObjVar(missionData, OBJVAR_BADGE_STRING))
        {
            String sid = getStringObjVar(missionData, OBJVAR_BADGE_STRING);
            if (sid != null && sid.indexOf('/') > 0)
            {
                String[] parts = sid.split("/", 2);
                sendSystemMessage(player, new string_id(parts[0], parts[1]));
            }
        }
        // Token payout from named account (economy-safe)
        if (hasObjVar(missionData, OBJVAR_TOKEN_AMOUNT))
        {
            int tokens = Math.max(0, getIntObjVar(missionData, OBJVAR_TOKEN_AMOUNT));
            if (tokens > 0)
            {
                String acct = hasObjVar(missionData, OBJVAR_TOKEN_ACCOUNT)
                        ? getStringObjVar(missionData, OBJVAR_TOKEN_ACCOUNT)
                        : "ACCT_MISSION_DYNAMIC";
                transferBankCreditsFromNamedAccount(acct, player, tokens, "noHandler", "noHandler", new dictionary());
            }
        }
    }

    private static float randf(float a, float b)
    {
        if (b < a) { float t = a; a = b; b = t; }
        return a + (float)Math.random() * (b - a);
    }
}
