package script.npc.simulation;

import script.*;
import script.library.ai_lib;
import script.library.behavior_telemetry;
import script.library.city;
import script.library.community_crafting;
import script.library.economy_stabilizer;
import script.library.locations;
import script.library.money;
import script.library.npc_presence;
import script.library.player_structure;
import script.library.utils;
import script.library.vendor_lib;

public class adaptive_archetype_controller extends script.base_script
{
    public adaptive_archetype_controller()
    {
    }

    public static final String VAR_ARCHETYPE = "npc.behavior.archetype";
    public static final String VAR_NEXT_UPDATE = "npc.behavior.nextUpdate";
    public static final String VAR_GOAL_CURSOR = "npc.behavior.goalCursor";
    public static final String VAR_ACTION_SEQUENCE = "npc.behavior.actionSequence";
    public static final String VAR_ACTION_COOLDOWN_ROOT = "npc.behavior.cooldown.";
    public static final String VAR_ACTION_FAIL_COUNT_ROOT = "npc.behavior.failCount.";
    public static final String VAR_AMBIENT_FAILOVER_UNTIL = "npc.behavior.ambientFailoverUntil";
    public static final String VAR_PENDING_COMBAT_TARGET = "npc.behavior.pending.combat.target";
    public static final String VAR_PENDING_COMBAT_AT = "npc.behavior.pending.combat.at";
    public static final String VAR_PENDING_MISSION_IDS = "npc.behavior.pending.mission.ids";
    public static final String VAR_PENDING_MISSION_AT = "npc.behavior.pending.mission.at";
    public static final String VAR_PENDING_CRAFT_TRACKER = "npc.behavior.pending.crafting.tracker";
    public static final String VAR_PENDING_CRAFT_STATE = "npc.behavior.pending.crafting.state";
    public static final String VAR_PENDING_CRAFT_AT = "npc.behavior.pending.crafting.at";
    public static final String VAR_PENDING_CRAFT_BASELINE = "npc.behavior.pending.crafting.baseline";
    public static final String VAR_CITY_PLACEMENTS_ROOT = "npc.behavior.cityPlacementCount.";
    public static final int ACTION_FAILOVER_THRESHOLD = 3;
    public static final int ACTION_FAILOVER_SECONDS = 180;
    public static final int MISSION_ACTION_COOLDOWN_SECONDS = 90;
    public static final int VENDOR_ACTION_COOLDOWN_SECONDS = 60;
    public static final int CRAFTING_ACTION_COOLDOWN_SECONDS = 90;
    public static final int BUILDING_ACTION_COOLDOWN_SECONDS = 300;
    public static final int CITY_STRUCTURE_CAP_PER_NPC = 2;
    public static final float ACTION_SCAN_RADIUS = 80.0f;
    public static final float PLAYER_METRIC_SCALE = 1.0f;
    public static final String VAR_ECONOMY_ROOT = "npc.simProfile.economy.";
    public static final String VAR_ECONOMY_WALLET = VAR_ECONOMY_ROOT + "wallet";
    public static final String VAR_ECONOMY_RESERVE = VAR_ECONOMY_ROOT + "reserve";
    public static final String VAR_ECONOMY_DAILY_SPEND_CAP = VAR_ECONOMY_ROOT + "dailySpendCap";
    public static final String VAR_ECONOMY_INCOME_COOLDOWN = VAR_ECONOMY_ROOT + "incomeCooldown";
    public static final String VAR_ECONOMY_SPENT_TODAY = VAR_ECONOMY_ROOT + "spentToday";
    public static final String VAR_ECONOMY_SPEND_DAY_INDEX = VAR_ECONOMY_ROOT + "spendDayIndex";
    public static final String VAR_ECONOMY_ACTIVITY_STIPEND_NEXT = VAR_ECONOMY_ROOT + "stipendNextAt";
    public static final String VAR_ECONOMY_LAST_DELTA_AMOUNT = VAR_ECONOMY_ROOT + "lastCreditDelta.amount";
    public static final String VAR_ECONOMY_LAST_DELTA_REASON = VAR_ECONOMY_ROOT + "lastCreditDelta.reason";
    public static final String VAR_ECONOMY_LAST_DELTA_DIRECTION = VAR_ECONOMY_ROOT + "lastCreditDelta.direction";
    public static final String VAR_ECONOMY_LAST_DELTA_AT = VAR_ECONOMY_ROOT + "lastCreditDelta.at";
    public static final int ECONOMY_DEFAULT_WALLET = 1800;
    public static final int ECONOMY_DEFAULT_RESERVE = 800;
    public static final int ECONOMY_DEFAULT_DAILY_SPEND_CAP = 900;
    public static final int ECONOMY_MAX_WALLET = 15000;
    public static final int ECONOMY_MAX_RESERVE = 7000;
    public static final int ECONOMY_MAX_DAILY_SPEND_CAP = 3000;
    public static final int ECONOMY_MAX_INCOME_PER_PULSE = 1200;
    public static final int ECONOMY_MISSION_PAYOUT_MIN = 150;
    public static final int ECONOMY_MISSION_PAYOUT_MAX = 550;
    public static final int ECONOMY_COMBAT_REWARD_MIN = 60;
    public static final int ECONOMY_COMBAT_REWARD_MAX = 240;
    public static final int ECONOMY_ACTIVITY_STIPEND_MIN = 35;
    public static final int ECONOMY_ACTIVITY_STIPEND_MAX = 125;
    public static final int ECONOMY_ACTIVITY_STIPEND_COOLDOWN_SECONDS = 900;
    public static final int ECONOMY_DAY_SECONDS = 86400;
    public static final String AI_LISTING_SOURCE = "adaptive_npc_listing";

    public int OnAttach(obj_id self) throws InterruptedException
    {
        initializeNpcLifecycleState(self, true);
        npc_presence.registerOrUpdateSimulatedPresence(self, "attach");
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        initializeNpcLifecycleState(self, false);
        npc_presence.registerOrUpdateSimulatedPresence(self, "initialize");
        return SCRIPT_CONTINUE;
    }

    public int OnLoadedFromDatabase(obj_id self) throws InterruptedException
    {
        initializeNpcLifecycleState(self, false);
        // Startup reconciliation: loaded NPCs rebuild the simulated presence feed.
        npc_presence.registerOrUpdateSimulatedPresence(self, "loadedFromDatabase");
        return SCRIPT_CONTINUE;
    }

    public int OnDetach(obj_id self) throws InterruptedException
    {
        npc_presence.removeSimulatedPresence(self);
        return SCRIPT_CONTINUE;
    }

    public int OnDestroy(obj_id self) throws InterruptedException
    {
        npc_presence.removeSimulatedPresence(self);
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathNotFound(obj_id self) throws InterruptedException
    {
        doAnimationAction(self, "look_around");
        return SCRIPT_CONTINUE;
    }

    public int OnFollowPathNotFound(obj_id self, obj_id target) throws InterruptedException
    {
        doAnimationAction(self, "look_around");
        return SCRIPT_CONTINUE;
    }

    public int updateBehaviorArchetype(obj_id self, dictionary params) throws InterruptedException
    {
        int now = getGameTime();
        if (getIntObjVar(self, VAR_NEXT_UPDATE) > now)
        {
            messageTo(self, "updateBehaviorArchetype", null, 30, false);
            return SCRIPT_CONTINUE;
        }
        behavior_telemetry.initializeNpcProgressionState(self);
        String archetype = behavior_telemetry.selectArchetypeForNpc(self);
        setObjVar(self, VAR_ARCHETYPE, archetype);
        setObjVar(self, "npc.simProfile.archetype", archetype);
        behavior_telemetry.ensureNpcIdentityProfile(self, archetype);
        behavior_telemetry.updateSocialGraphMemory(self);
        int refreshFrequency = behavior_telemetry.getBehaviorProfileUpdateFrequencySeconds();
        setObjVar(self, VAR_NEXT_UPDATE, now + refreshFrequency);

        behavior_telemetry.noteNpcTargetZone(self);
        behavior_telemetry.saveNpcProfileCheckpoint(self);

        npc_presence.registerOrUpdateSimulatedPresence(self, "archetypeRefresh");

        float combatScore = applyProgressionToGoalWeight(self, "combat", behavior_telemetry.scoreGoalByArchetype(self, archetype, "combat", 1.0f));
        float craftingScore = applyProgressionToGoalWeight(self, "crafting", behavior_telemetry.scoreGoalByArchetype(self, archetype, "crafting", 1.0f));
        float socialScore = applyProgressionToGoalWeight(self, "social", behavior_telemetry.scoreGoalByArchetype(self, archetype, "social", 1.0f));
        float travelScore = applyProgressionToGoalWeight(self, "travel", behavior_telemetry.scoreGoalByArchetype(self, archetype, "travel", 1.0f));
        float economicScore = applyProgressionToGoalWeight(self, "economic", behavior_telemetry.scoreGoalByArchetype(self, archetype, "economic", 1.0f));
        setObjVar(self, "npc.behavior.goalScore.combat", combatScore);
        setObjVar(self, "npc.behavior.goalScore.crafting", craftingScore);
        setObjVar(self, "npc.behavior.goalScore.social", socialScore);
        setObjVar(self, "npc.behavior.goalScore.travel", travelScore);
        setObjVar(self, "npc.behavior.goalScore.economic", economicScore);

        String dominantActivity = selectDominantGoal(combatScore, craftingScore, socialScore, travelScore, economicScore);
        String routineOverride = behavior_telemetry.selectRoutineGoalForNpc(self, archetype);
        if (routineOverride != null && routineOverride.length() > 0)
        {
            dominantActivity = routineOverride;
        }
        String[] sequence = behavior_telemetry.sampleActionSequenceForNpc(self, archetype, dominantActivity);
        setObjVar(self, VAR_ACTION_SEQUENCE, sequence);

        int delay = behavior_telemetry.getActionDelayForArchetype(archetype);
        messageTo(self, "runAdaptiveActionPulse", null, delay, false);
        messageTo(self, "updateBehaviorArchetype", null, refreshFrequency, false);
        return SCRIPT_CONTINUE;
    }

    public int runAdaptiveActionPulse(obj_id self, dictionary params) throws InterruptedException
    {
        String archetype = getStringObjVar(self, VAR_ARCHETYPE);
        if (archetype == null || archetype.length() < 1)
        {
            archetype = "social_hub_idler";
        }
        runSampledAction(self, archetype);
        evaluatePendingAuthoritativeCompletions(self);

        runGoalCycleProgression(self, archetype);

        int delay = behavior_telemetry.getActionDelayForArchetype(archetype);
        messageTo(self, "runAdaptiveActionPulse", null, delay, false);
        return SCRIPT_CONTINUE;
    }

    private void runSampledAction(obj_id self, String archetype) throws InterruptedException
    {
        String executedGoal = "social";
        String executedOutcome = "social_interaction";
        boolean visualSuccess = false;
        boolean systemSuccess = false;

        String[] sequence = utils.getStringArrayObjVar(self, VAR_ACTION_SEQUENCE, new String[0]);
        if (sequence.length < 1)
        {
            sequence = behavior_telemetry.sampleActionSequenceForNpc(self, archetype, "ambient");
            setObjVar(self, VAR_ACTION_SEQUENCE, sequence);
        }
        if (sequence.length < 1)
        {
            doAnimationAction(self, "look_around");
            behavior_telemetry.recordNpcGoalCycle(self, executedGoal, false);
            behavior_telemetry.recordNpcOutcome(self, "ambient_idle", false);
            return;
        }

        int cursor = getIntObjVar(self, "npc.behavior.actionCursor");
        String step = sequence[cursor % sequence.length];
        setObjVar(self, "npc.behavior.actionCursor", cursor + 1);

        if ("combat_focus".equals(step) || "retarget".equals(step))
        {
            executedGoal = "combat";
            executedOutcome = "combat_posture";
            visualSuccess = executeCombatAction(self, "point_at");
            systemSuccess = false;
        }
        else if ("skill_burst".equals(step))
        {
            executedGoal = "combat";
            executedOutcome = "combat_skill_burst";
            visualSuccess = executeCombatAction(self, "threaten");
            systemSuccess = false;
        }
        else if (isTravelStep(step))
        {
            executedGoal = "travel";
            executedOutcome = "travel_step";
            systemSuccess = executeTravelAction(self);
            visualSuccess = systemSuccess;
            if (!visualSuccess)
            {
                doAnimationAction(self, "look_around");
            }
        }
        else if (isPatrolStep(step))
        {
            executedGoal = "combat";
            executedOutcome = "patrol_route";
            systemSuccess = executePatrolAction(self);
            visualSuccess = systemSuccess;
            if (!visualSuccess)
            {
                doAnimationAction(self, "threaten");
            }
        }
        else if (isMissionTerminalStep(step))
        {
            executedGoal = "economic";
            executedOutcome = "mission_terminal_interaction";
            systemSuccess = executeMissionTerminalAction(self, step);
            visualSuccess = systemSuccess;
            if (!visualSuccess)
            {
                doAnimationAction(self, "check_wrist_device");
            }
        }
        else if (isVendorTradeStep(step) || "trade_checkout".equals(step))
        {
            executedGoal = "economic";
            executedOutcome = "vendor_trade_interaction";
            systemSuccess = executeVendorTradeAction(self, step);
            visualSuccess = systemSuccess;
            if (!visualSuccess)
            {
                doAnimationAction(self, "check_wrist_device");
            }
        }
        else if (isCraftingStep(step))
        {
            executedGoal = "crafting";
            executedOutcome = "crafting_interaction";
            systemSuccess = executeCraftingAction(self, step);
            visualSuccess = systemSuccess;
            if (!visualSuccess)
            {
                doAnimationAction(self, "check_wrist_device");
            }
        }
        else if (isBuildingPlacementStep(step))
        {
            executedGoal = "economic";
            executedOutcome = "building_placement";
            systemSuccess = executeBuildingPlacementAction(self, step);
            visualSuccess = systemSuccess;
            if (!visualSuccess)
            {
                doAnimationAction(self, "check_wrist_device");
            }
        }
        else if (isSocialStep(step) || "social_greet".equals(step) || "social_respond".equals(step))
        {
            executedGoal = "social";
            executedOutcome = "social_interaction";
            systemSuccess = executeSocialAction(self);
            visualSuccess = systemSuccess;
            if (!visualSuccess)
            {
                doAnimationAction(self, "wave");
            }
        }
        else
        {
            doAnimationAction(self, "look_around");
            executedGoal = "social";
            executedOutcome = "ambient_idle";
            visualSuccess = true;
            systemSuccess = true;
        }

        if (!systemSuccess && shouldFailoverToAmbient(self))
        {
            doAnimationAction(self, "look_around");
            executedOutcome = "ambient_failover";
            executedGoal = "social";
            visualSuccess = true;
            systemSuccess = true;
        }

        recordActionResult(self, executedOutcome, systemSuccess);
        if (!"combat".equals(executedGoal) && !"crafting".equals(executedGoal) && !("economic".equals(executedGoal) && "mission_terminal_interaction".equals(executedOutcome)))
        {
            behavior_telemetry.recordNpcGoalCycle(self, executedGoal, systemSuccess);
        }
        behavior_telemetry.recordNpcOutcome(self, executedOutcome, visualSuccess);
        if (systemSuccess && "travel".equals(executedGoal))
        {
            behavior_telemetry.noteNpcTargetZone(self);
        }
    }

    public int OnIncapacitatedTarget(obj_id self, obj_id victim) throws InterruptedException
    {
        if (isIdValid(victim))
        {
            onAuthoritativeCompletion(self, "combat", "kill", victim);
        }
        return SCRIPT_CONTINUE;
    }

    private void onAuthoritativeCompletion(obj_id self, String goal, String detail, obj_id subject) throws InterruptedException
    {
        if ("combat".equals(goal))
        {
            behavior_telemetry.recordNpcGoalCycle(self, "combat", true);
            behavior_telemetry.recordNpcOutcome(self, "combat_win", true);
            creditNpcIncome(self, rand(ECONOMY_COMBAT_REWARD_MIN, ECONOMY_COMBAT_REWARD_MAX), "combat_reward", money.ACCT_BOUNTY, ECONOMY_MAX_INCOME_PER_PULSE, 20);
            grantActivityStipend(self, true);
            removeObjVar(self, VAR_PENDING_COMBAT_TARGET);
            removeObjVar(self, VAR_PENDING_COMBAT_AT);
        }
        else if ("economic".equals(goal))
        {
            behavior_telemetry.recordNpcGoalCycle(self, "economic", true);
            behavior_telemetry.recordNpcOutcome(self, "economic_interaction", true);
            grantActivityStipend(self, true);
        }
        else if ("crafting".equals(goal))
        {
            behavior_telemetry.recordNpcGoalCycle(self, "crafting", true);
            behavior_telemetry.recordNpcOutcome(self, "crafting_complete", true);
            grantActivityStipend(self, true);
            setObjVar(self, VAR_PENDING_CRAFT_STATE, "complete");
        }
        behavior_telemetry.saveNpcProfileCheckpoint(self);
    }

    private boolean executeCombatAction(obj_id self, String emote) throws InterruptedException
    {
        if (isCombatStartBlockedForSelf(self, "preTargetLookup"))
        {
            doAnimationAction(self, emote);
            return true;
        }

        obj_id target = findNearbyCombatTarget(self);
        if (!isViableCombatTarget(self, target))
        {
            doAnimationAction(self, emote);
            return true;
        }
        faceTo(self, target);
        doAnimationAction(self, emote);
        if (!isViableCombatTarget(self, target))
        {
            return true;
        }
        if (isCombatStartBlockedForSelf(self, "preCombatStart"))
        {
            return true;
        }
        startCombat(self, target);
        addHate(self, target, rand(8, 22));
        setObjVar(self, VAR_PENDING_COMBAT_TARGET, target);
        setObjVar(self, VAR_PENDING_COMBAT_AT, getGameTime());
        return true;
    }

    private boolean isCombatStartBlockedForSelf(obj_id self, String context) throws InterruptedException
    {
        if (isInvulnerable(self) || !ai_lib.isAttackable(self))
        {
            LOG("adaptiveCombatBlocked_invulnerableSelf", "self(" + self + ") context(" + context + ") invulnerable(" + isInvulnerable(self) + ") attackable(" + ai_lib.isAttackable(self) + ")");
            return true;
        }
        return false;
    }

    private boolean isTravelStep(String step) throws InterruptedException
    {
        return step != null && (step.indexOf("travel") > -1 || step.indexOf("route") > -1 || step.indexOf("transit") > -1 || step.indexOf("move") > -1);
    }

    private boolean isPatrolStep(String step) throws InterruptedException
    {
        return step != null && (step.indexOf("patrol") > -1 || step.indexOf("sweep") > -1 || step.indexOf("guard") > -1);
    }

    private boolean isMissionTerminalStep(String step) throws InterruptedException
    {
        return step != null && (step.indexOf("mission_terminal") > -1 || step.indexOf("mission") > -1 && step.indexOf("terminal") > -1);
    }

    private boolean isVendorTradeStep(String step) throws InterruptedException
    {
        return step != null && (step.indexOf("vendor") > -1 || step.indexOf("trade") > -1 || step.indexOf("market") > -1 || step.indexOf("shop") > -1);
    }

    private boolean isSocialStep(String step) throws InterruptedException
    {
        return step != null && (step.indexOf("social") > -1 || step.indexOf("greet") > -1 || step.indexOf("respond") > -1 || step.indexOf("chat") > -1 || step.indexOf("talk") > -1);
    }

    private boolean isCraftingStep(String step) throws InterruptedException
    {
        return step != null && (step.indexOf("craft") > -1 || step.indexOf("schematic") > -1 || step.indexOf("assembly") > -1);
    }

    private boolean isBuildingPlacementStep(String step) throws InterruptedException
    {
        return step != null && (step.indexOf("build") > -1 || step.indexOf("placement") > -1 || step.indexOf("structure") > -1);
    }

    private boolean executeTravelAction(obj_id self) throws InterruptedException
    {
        location current = getLocation(self);
        if (!isValidLocation(current))
        {
            return false;
        }
        location destination = getRandomOffsetLocation(current, 12.0f, 48.0f);
        if (!isValidLocation(destination))
        {
            return false;
        }
        location reachableDestination = locations.getGoodLocationAroundLocation(destination, 2.0f, 2.0f, 6.0f, 6.0f, false, true);
        if (!isValidLocation(reachableDestination) || !isGroundPathDestination(current, reachableDestination))
        {
            return false;
        }
        setMovementWalk(self);
        ai_lib.pathTo(self, reachableDestination);
        return true;
    }

    private boolean executePatrolAction(obj_id self) throws InterruptedException
    {
        location origin = getLocation(self);
        if (!isValidLocation(origin))
        {
            return false;
        }
        location[] patrolPoints = new location[3];
        patrolPoints[0] = getRandomOffsetLocation(origin, 8.0f, 18.0f);
        patrolPoints[1] = getRandomOffsetLocation(origin, 14.0f, 26.0f);
        patrolPoints[2] = getRandomOffsetLocation(origin, 10.0f, 24.0f);
        for (int i = 0; i < patrolPoints.length; i++)
        {
            if (!isValidLocation(patrolPoints[i]))
            {
                return false;
            }
        }
        for (int i = 0; i < patrolPoints.length; i++)
        {
            location reachablePatrolPoint = locations.getGoodLocationAroundLocation(patrolPoints[i], 2.0f, 2.0f, 5.0f, 5.0f, false, true);
            if (!isValidLocation(reachablePatrolPoint) || !isGroundPathDestination(origin, reachablePatrolPoint))
            {
                return false;
            }
            patrolPoints[i] = reachablePatrolPoint;
        }
        setMovementWalk(self);
        patrol(self, patrolPoints);
        return true;
    }

    private boolean executeMissionTerminalAction(obj_id self, String step) throws InterruptedException
    {
        if (!isActionAvailable(self, "mission", MISSION_ACTION_COOLDOWN_SECONDS))
        {
            return false;
        }
        obj_id terminal = findNearbyObjectByTemplate(self, "terminal", "mission");
        if (!isIdValid(terminal))
        {
            return false;
        }

        return invokeMissionService(self, terminal, step);
    }

    private boolean executeVendorTradeAction(obj_id self, String step) throws InterruptedException
    {
        if (!isActionAvailable(self, "vendor", VENDOR_ACTION_COOLDOWN_SECONDS))
        {
            return false;
        }
        obj_id vendor = findNearbyObjectByTemplate(self, "vendor", "trader", "bazaar");
        if (!isIdValid(vendor))
        {
            return false;
        }

        boolean shouldListSupply = shouldPreferSupplyListing(self, step);
        if (shouldListSupply)
        {
            boolean listed = invokeBazaarListingService(self, vendor, step);
            if (listed)
            {
                return true;
            }
            return invokeVendorTradeService(self, vendor, step);
        }
        return invokeVendorTradeService(self, vendor, step);
    }

    private boolean shouldPreferSupplyListing(obj_id self, String step) throws InterruptedException
    {
        if (step != null)
        {
            String lowerStep = toLower(step);
            if (lowerStep.indexOf("sell") > -1 || lowerStep.indexOf("listing") > -1 || lowerStep.indexOf("stock") > -1)
            {
                return true;
            }
            if (lowerStep.indexOf("buy") > -1 || lowerStep.indexOf("checkout") > -1)
            {
                return false;
            }
        }

        obj_id inventory = utils.getInventoryContainer(self);
        obj_id[] contents = isIdValid(inventory) ? getContents(inventory) : null;
        int tradableCount = countTradableInventoryItems(contents);

        refreshEconomyDay(self);
        int dailyCap = Math.max(0, getIntObjVar(self, VAR_ECONOMY_DAILY_SPEND_CAP));
        int spentToday = Math.max(0, getIntObjVar(self, VAR_ECONOMY_SPENT_TODAY));
        int spendRemaining = Math.max(0, dailyCap - spentToday);

        if (tradableCount >= 3)
        {
            return true;
        }
        if (tradableCount > 0 && spendRemaining < Math.max(150, dailyCap / 5))
        {
            return true;
        }
        return rand(1, 100) <= 20;
    }

    private int countTradableInventoryItems(obj_id[] contents) throws InterruptedException
    {
        if (contents == null || contents.length < 1)
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < contents.length; i++)
        {
            obj_id item = contents[i];
            if (!isIdValid(item) || !exists(item) || hasObjVar(item, "noTrade"))
            {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean executeCraftingAction(obj_id self, String step) throws InterruptedException
    {
        if (!isActionAvailable(self, "crafting", CRAFTING_ACTION_COOLDOWN_SECONDS))
        {
            return false;
        }
        obj_id tracker = findNearbyObjectByTemplate(self, "craft", "station", "factory");
        if (!isIdValid(tracker))
        {
            return false;
        }
        return invokeCraftingService(self, tracker, step);
    }

    private boolean executeBuildingPlacementAction(obj_id self, String step) throws InterruptedException
    {
        if (!isActionAvailable(self, "building", BUILDING_ACTION_COOLDOWN_SECONDS))
        {
            return false;
        }
        return invokeBuildingPlacementService(self, step);
    }

    private boolean executeSocialAction(obj_id self) throws InterruptedException
    {
        obj_id partner = findNearbySocialActor(self);
        if (!isIdValid(partner))
        {
            return false;
        }
        faceTo(self, partner);
        if (rand(1, 100) <= 50)
        {
            doAnimationAction(self, "wave");
        }
        else
        {
            doAnimationAction(self, "bow");
        }
        loiterTarget(self, partner, 2.0f, 7.0f, 1.0f, 4.0f);
        return true;
    }

    private obj_id findNearbyObjectByTemplate(obj_id self, String tokenA, String tokenB) throws InterruptedException
    {
        return findNearbyObjectByTemplate(self, new String[]{tokenA, tokenB});
    }

    private obj_id findNearbyObjectByTemplate(obj_id self, String tokenA, String tokenB, String tokenC) throws InterruptedException
    {
        return findNearbyObjectByTemplate(self, new String[]{tokenA, tokenB, tokenC});
    }

    private obj_id findNearbyObjectByTemplate(obj_id self, String[] templateTokens) throws InterruptedException
    {
        obj_id[] nearby = getNearbyObjectsInActionRange(self);
        if (nearby.length < 1)
        {
            return obj_id.NULL_ID;
        }
        obj_id best = obj_id.NULL_ID;
        float bestDistance = ACTION_SCAN_RADIUS + 1.0f;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id test = nearby[i];
            if (!isIdValid(test) || test == self)
            {
                continue;
            }
            String template = getTemplateName(test);
            if (template == null || template.length() < 1)
            {
                continue;
            }
            String lower = toLower(template);
            boolean matches = false;
            for (int tokenIndex = 0; tokenIndex < templateTokens.length; tokenIndex++)
            {
                String token = templateTokens[tokenIndex];
                if (token != null && lower.indexOf(token) > -1)
                {
                    matches = true;
                    break;
                }
            }
            if (!matches)
            {
                continue;
            }
            float dist = getDistance(self, test);
            if (dist < bestDistance)
            {
                best = test;
                bestDistance = dist;
            }
        }
        return best;
    }

    private boolean invokeMissionService(obj_id self, obj_id terminal, String step) throws InterruptedException
    {
        obj_id[] before = getMissionObjects(self);

        location terminalLocation = getLocation(terminal);
        if (!isValidLocation(terminalLocation))
        {
            return false;
        }
        location reachableTerminalLocation = locations.getGoodLocationAroundLocation(terminalLocation, 1.0f, 1.0f, 4.0f, 4.0f, false, true);
        if (!isValidLocation(reachableTerminalLocation))
        {
            return false;
        }
        pathTo(self, reachableTerminalLocation);
        dictionary params = new dictionary();
        params.put("player", self);
        params.put("step", step);
        params.put("source", "adaptive_npc");
        messageTo(terminal, "handleNpcMissionRequest", params, 0, false);
        obj_id[] after = getMissionObjects(self);
        obj_id[] assigned = diffMissionIds(before, after);
        if (assigned.length < 1)
        {
            return false;
        }
        setObjVar(self, VAR_PENDING_MISSION_IDS, assigned);
        setObjVar(self, VAR_PENDING_MISSION_AT, getGameTime());
        return true;
    }

    private boolean invokeVendorTradeService(obj_id self, obj_id vendor, String step) throws InterruptedException
    {
        refreshEconomyDay(self);
        int reserve = Math.max(0, getIntObjVar(self, VAR_ECONOMY_RESERVE));
        int dailyCap = Math.max(0, getIntObjVar(self, VAR_ECONOMY_DAILY_SPEND_CAP));
        int spentToday = Math.max(0, getIntObjVar(self, VAR_ECONOMY_SPENT_TODAY));
        int spendRemaining = Math.max(0, dailyCap - spentToday);
        int availableForSpend = Math.max(0, money.getTotalMoney(self) - reserve);
        int cycleSpendLimit = Math.max(75, dailyCap / 4);
        int maxSpendThisCycle = Math.min(cycleSpendLimit, Math.min(spendRemaining, availableForSpend));
        if (maxSpendThisCycle < 50)
        {
            return false;
        }

        int purchaseCost = Math.max(50, rand(75, 350));
        purchaseCost = Math.min(purchaseCost, maxSpendThisCycle);
        if (!money.hasFunds(self, money.MT_TOTAL, purchaseCost))
        {
            return false;
        }

        ai_lib.aiFollow(self, vendor, 3.0f, 8.0f);
        boolean paid = money.cashTo(self, vendor, purchaseCost);
        if (!paid)
        {
            paid = money.bankTo(self, vendor, purchaseCost);
        }
        if (!paid)
        {
            return false;
        }

        setObjVar(self, VAR_ECONOMY_SPENT_TODAY, spentToday + purchaseCost);
        debitNpcEconomy(self, purchaseCost, "vendor_spend");

        dictionary params = new dictionary();
        params.put("customer", self);
        params.put("amount", purchaseCost);
        params.put("step", step);
        params.put("source", "adaptive_npc");
        messageTo(vendor, "handleNpcVendorPurchase", params, 0, false);
        return true;
    }

    private boolean invokeBazaarListingService(obj_id self, obj_id terminalOrVendor, String step) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || !isIdValid(terminalOrVendor) || !exists(terminalOrVendor))
        {
            return false;
        }

        obj_id sharedState = vendor_lib.getEconomyStateObject(terminalOrVendor);
        dictionary economyMetrics = new dictionary();
        location center = getLocation(terminalOrVendor);
        if (isIdValid(sharedState) && exists(sharedState))
        {
            economyMetrics = economy_stabilizer.tickEconomy(sharedState, center, 256.0f, 120);
        }
        if (economyMetrics == null || economyMetrics.isEmpty())
        {
            economyMetrics = economy_stabilizer.evaluateEconomy(center, 256.0f);
        }

        String economyMode = economyMetrics.getString("mode");
        if (economyMode == null || economyMode.length() < 1)
        {
            economyMode = economy_stabilizer.MODE_BALANCED;
        }
        int onlineCount = Math.max(0, economyMetrics.getInt("onlineCount"));
        int shortageCount = Math.max(0, economyMetrics.getInt("shortageCount"));

        dictionary params = new dictionary();
        String shortageCategory = vendor_lib.getPreferredShortageCategoryForVendor(terminalOrVendor);
        String shortageLabel = vendor_lib.getShortageCategoryLabel(shortageCategory);
        int shortageDeficit = Math.max(0, vendor_lib.getShortageDeficitMagnitudeForVendor(terminalOrVendor, shortageCategory));
        int spawnCapDaily = 4;
        float floorMultiplier = 0.87f;
        float ceilingMultiplier = 1.16f;
        int staleSeconds = 21600;

        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            spawnCapDaily = 6;
            floorMultiplier = 0.80f;
            ceilingMultiplier = 1.30f;
            staleSeconds = 10800;
        }
        else if (economy_stabilizer.MODE_INFLATION_CONTROL.equals(economyMode))
        {
            spawnCapDaily = 2;
            floorMultiplier = 0.93f;
            ceilingMultiplier = 1.08f;
            staleSeconds = 32400;
        }

        spawnCapDaily += Math.min(4, (shortageCount + 1) / 2);
        spawnCapDaily += Math.min(3, shortageDeficit / 6);
        if (onlineCount > 200)
        {
            spawnCapDaily += 1;
        }
        floorMultiplier = Math.max(0.70f, floorMultiplier - Math.min(0.08f, (shortageDeficit * 0.004f)));
        ceilingMultiplier = Math.min(1.45f, ceilingMultiplier + Math.min(0.16f, (shortageDeficit * 0.008f)));
        staleSeconds = Math.max(7200, staleSeconds - Math.min(7200, shortageDeficit * 240));
        if (ceilingMultiplier < floorMultiplier)
        {
            ceilingMultiplier = floorMultiplier;
        }

        int attemptBudget = 1 + Math.min(4, shortageDeficit / 5);
        if (shortageCount > 1)
        {
            attemptBudget++;
        }
        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            attemptBudget++;
        }
        attemptBudget = Math.max(1, Math.min(6, attemptBudget));

        params.put("seller", self);
        params.put("step", step);
        params.put("source", AI_LISTING_SOURCE);
        params.put("listingOrigin", AI_LISTING_SOURCE);
        params.put("listingCategory", shortageCategory);
        params.put("shortageCategoryLabel", shortageLabel);
        params.put("economyMode", economyMode);
        params.put("onlineCount", onlineCount);
        params.put("shortageCount", shortageCount);
        params.put("shortageDeficitMagnitude", shortageDeficit);
        params.put("spawnCapDaily", spawnCapDaily);
        params.put("floorMultiplier", floorMultiplier);
        params.put("ceilingMultiplier", ceilingMultiplier);
        params.put("staleSeconds", staleSeconds);
        params.put("baseListingFee", 20);
        params.put("feeWaived", 1);
        boolean created = false;
        for (int attempt = 0; attempt < attemptBudget; attempt++)
        {
            created = vendor_lib.handleNpcVendorListing(terminalOrVendor, params);
            if (created)
            {
                break;
            }
        }

        int now = getGameTime();
        String detail = created ? "listing_created" : "listing_failed";
        setObjVar(self, "npc.simProfile.vendor.listing.pending", created ? 1 : 0);
        setObjVar(self, "npc.simProfile.vendor.listing.lastAttemptAt", now);
        setObjVar(self, "npc.simProfile.vendor.listing.lastResult", detail);
        setObjVar(self, "npc.simProfile.vendor.listing.lastSource", AI_LISTING_SOURCE);
        setObjVar(self, "npc.simProfile.vendor.listing.lastCategory", shortageCategory);
        setObjVar(self, "npc.simProfile.vendor.listing.lastCategoryLabel", shortageLabel);
        utils.setScriptVar(self, "npc.simProfile.vendor.listing.pending", created ? 1 : 0);

        behavior_telemetry.recordNpcOutcome(self, detail, created);
        return created;
    }

    private boolean invokeCraftingService(obj_id self, obj_id tracker, String step) throws InterruptedException
    {
        if (!community_crafting.isInitializedForCC(tracker) || !community_crafting.isSessionActive(tracker))
        {
            return false;
        }
        if (!community_crafting.addPlayerToSystem(tracker, self))
        {
            return false;
        }

        dictionary params = new dictionary();
        params.put("crafter", self);
        params.put("step", step);
        params.put("source", "adaptive_npc");
        int baseline = getNpcCraftingTotalContribution(tracker, self);
        boolean dispatched = community_crafting.handleNpcCraftingAssist(tracker, params);
        if (!dispatched)
        {
            return false;
        }
        setObjVar(self, VAR_PENDING_CRAFT_TRACKER, tracker);
        setObjVar(self, VAR_PENDING_CRAFT_STATE, "start");
        setObjVar(self, VAR_PENDING_CRAFT_AT, getGameTime());
        setObjVar(self, VAR_PENDING_CRAFT_BASELINE, baseline);
        return true;
    }

    private obj_id[] diffMissionIds(obj_id[] before, obj_id[] after) throws InterruptedException
    {
        if (after == null || after.length < 1)
        {
            return new obj_id[0];
        }
        int beforeLen = before != null ? before.length : 0;
        obj_id[] tmp = new obj_id[after.length];
        int count = 0;
        for (int i = 0; i < after.length; i++)
        {
            obj_id mission = after[i];
            if (!isIdValid(mission))
            {
                continue;
            }
            boolean seen = false;
            for (int j = 0; j < beforeLen; j++)
            {
                if (mission == before[j])
                {
                    seen = true;
                    break;
                }
            }
            if (!seen)
            {
                tmp[count++] = mission;
            }
        }
        if (count < 1)
        {
            return new obj_id[0];
        }
        obj_id[] assigned = new obj_id[count];
        for (int i = 0; i < count; i++)
        {
            assigned[i] = tmp[i];
        }
        return assigned;
    }

    private int getNpcCraftingTotalContribution(obj_id tracker, obj_id crafter) throws InterruptedException
    {
        String quantityKey = community_crafting.OBJVAR_COMMUNITY_CRAFTING_PLAYER_QUANTITY_TOTAL + "." + crafter;
        String qualityKey = community_crafting.OBJVAR_COMMUNITY_CRAFTING_PLAYER_QUALITY_TOTAL + "." + crafter;
        return Math.max(0, getIntObjVar(tracker, quantityKey)) + Math.max(0, Math.round(getFloatObjVar(tracker, qualityKey) * 100.0f));
    }

    private boolean invokeBuildingPlacementService(obj_id self, String step) throws InterruptedException
    {
        if (!isEligibleForBuildingPlacement(self))
        {
            return false;
        }

        location here = getLocation(self);
        if (!isValidLocation(here) || isIdValid(here.cell))
        {
            return false;
        }

        String deedTemplate = "object/tangible/deed/player_deed/house_deed/corellia_house_small_deed.iff";
        String structureTemplate = "object/building/player/player_house_corellia_small_style_01.iff";
        if (structureTemplate == null || structureTemplate.length() < 1)
        {
            return false;
        }
        if (!player_structure.canPlaceGarage(here, 120.0f, structureTemplate))
        {
            return false;
        }

        dictionary deedInfo = new dictionary();
        deedInfo.put("deed_template", deedTemplate);
        deedInfo.put("source", "adaptive_npc");
        deedInfo.put("step", step);
        obj_id structure = player_structure.createPlayerStructure(structureTemplate, self, here, rand(0, 3), deedInfo);
        if (!isIdValid(structure))
        {
            return false;
        }

        persistObject(structure);
        int cityId = city.checkCity(self, false);
        if (cityId > 0)
        {
            setObjVar(structure, "npc.simulatedPlacement", 1);
            setObjVar(structure, "npc.simulatedPlacement.cityId", cityId);
            incrementCityPlacementCount(self, cityId);
        }

        dictionary params = new dictionary();
        params.put("structure", structure);
        params.put("owner", self);
        params.put("source", "adaptive_npc");
        return player_structure.handleNpcStructurePlaced(structure, params);
    }

    private boolean isEligibleForBuildingPlacement(obj_id self) throws InterruptedException
    {
        if (ai_lib.isInCombat(self) || isDead(self) || isIncapacitated(self))
        {
            return false;
        }
        int cityId = city.checkCity(self, false);
        if (cityId < 1)
        {
            return false;
        }
        if (!city.isCityZoned(cityId))
        {
            return false;
        }
        int placed = getIntObjVar(self, VAR_CITY_PLACEMENTS_ROOT + cityId);
        int civic = city.getCivicCount(cityId);
        int civicCap = city.getMaxCivicCount(cityId);
        return placed < CITY_STRUCTURE_CAP_PER_NPC && civic < civicCap;
    }

    private void incrementCityPlacementCount(obj_id self, int cityId) throws InterruptedException
    {
        String key = VAR_CITY_PLACEMENTS_ROOT + cityId;
        setObjVar(self, key, Math.max(0, getIntObjVar(self, key)) + 1);
    }

    private boolean isActionAvailable(obj_id self, String action, int cooldownSeconds) throws InterruptedException
    {
        int now = getGameTime();
        String key = VAR_ACTION_COOLDOWN_ROOT + action;
        if (getIntObjVar(self, key) > now)
        {
            return false;
        }
        setObjVar(self, key, now + Math.max(1, cooldownSeconds));
        return true;
    }

    private void recordActionResult(obj_id self, String action, boolean success) throws InterruptedException
    {
        String failKey = VAR_ACTION_FAIL_COUNT_ROOT + action;
        if (success)
        {
            setObjVar(self, failKey, 0);
            return;
        }
        setObjVar(self, failKey, Math.max(0, getIntObjVar(self, failKey)) + 1);
    }

    private boolean shouldFailoverToAmbient(obj_id self) throws InterruptedException
    {
        int now = getGameTime();
        if (getIntObjVar(self, VAR_AMBIENT_FAILOVER_UNTIL) > now)
        {
            return true;
        }

        int missionFails = getIntObjVar(self, VAR_ACTION_FAIL_COUNT_ROOT + "mission_terminal_interaction");
        int vendorFails = getIntObjVar(self, VAR_ACTION_FAIL_COUNT_ROOT + "vendor_trade_interaction");
        int craftingFails = getIntObjVar(self, VAR_ACTION_FAIL_COUNT_ROOT + "crafting_interaction");
        int buildingFails = getIntObjVar(self, VAR_ACTION_FAIL_COUNT_ROOT + "building_placement");
        if (missionFails >= ACTION_FAILOVER_THRESHOLD || vendorFails >= ACTION_FAILOVER_THRESHOLD || craftingFails >= ACTION_FAILOVER_THRESHOLD || buildingFails >= ACTION_FAILOVER_THRESHOLD)
        {
            setObjVar(self, VAR_AMBIENT_FAILOVER_UNTIL, now + ACTION_FAILOVER_SECONDS);
            return true;
        }
        return false;
    }

    private obj_id[] getNearbyObjectsInActionRange(obj_id self) throws InterruptedException
    {
        location actorLocation = getLocation(self);
        if (!isValidLocation(actorLocation))
        {
            return new obj_id[0];
        }
        obj_id[] nearby = getObjectsInRange(actorLocation, ACTION_SCAN_RADIUS);
        if (nearby == null)
        {
            return new obj_id[0];
        }
        return nearby;
    }

    private obj_id findNearbySocialActor(obj_id self) throws InterruptedException
    {
        obj_id[] nearby = getNearbyObjectsInActionRange(self);
        if (nearby.length < 1)
        {
            return obj_id.NULL_ID;
        }
        obj_id best = obj_id.NULL_ID;
        float bestDistance = ACTION_SCAN_RADIUS + 1.0f;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id test = nearby[i];
            if (!isIdValid(test) || test == self || ai_lib.aiIsDead(test))
            {
                continue;
            }
            if (!isPlayer(test) && !ai_lib.isNpc(test))
            {
                continue;
            }
            if (!canSee(self, test))
            {
                continue;
            }
            float dist = getDistance(self, test);
            if (dist < bestDistance)
            {
                best = test;
                bestDistance = dist;
            }
        }
        return best;
    }

    private location getRandomOffsetLocation(location origin, float minDistance, float maxDistance) throws InterruptedException
    {
        if (origin == null)
        {
            return null;
        }
        float angle = (float)rand(0, 359);
        float distance = (float)rand((int)(minDistance * 100.0f), (int)(maxDistance * 100.0f)) / 100.0f;
        float radians = angle * 0.017453292f;
        location target = new location(origin);
        target.x = origin.x + ((float)Math.cos(radians) * distance);
        target.z = origin.z + ((float)Math.sin(radians) * distance);
        return target;
    }


    private boolean isGroundPathDestination(location origin, location destination) throws InterruptedException
    {
        if (!isValidLocation(origin) || !isValidLocation(destination))
        {
            return false;
        }
        if (origin.area == null || destination.area == null)
        {
            return false;
        }
        if (!toLower(origin.area).equals(toLower(destination.area)))
        {
            return false;
        }
        return !isIdValid(destination.cell);
    }

    private String selectDominantGoal(float combatScore, float craftingScore, float socialScore, float travelScore, float economicScore) throws InterruptedException
    {
        String goal = "social";
        float top = socialScore;
        if (combatScore > top)
        {
            top = combatScore;
            goal = "combat";
        }
        if (craftingScore > top)
        {
            top = craftingScore;
            goal = "crafting";
        }
        if (travelScore > top)
        {
            top = travelScore;
            goal = "movement";
        }
        if (economicScore > top)
        {
            goal = "economic";
        }
        return goal;
    }

    public int OnHearSpeech(obj_id self, obj_id speaker, String text) throws InterruptedException
    {
        if (!isIdValid(speaker) || !isGod(speaker) || text == null)
        {
            return SCRIPT_CONTINUE;
        }
        String lower = toLower(text);
        if (lower.startsWith("simprofile") || lower.startsWith("npcprofile") || lower.startsWith("npcprog inspect"))
        {
            sendSystemMessageTestingOnly(speaker, "npc " + self + " " + behavior_telemetry.getNpcProfileDebugSummary(self));
            return SCRIPT_CONTINUE;
        }
        if (lower.startsWith("npcprog reset"))
        {
            behavior_telemetry.resetNpcProgression(self);
            sendSystemMessageTestingOnly(speaker, "npc progression reset for " + self);
            return SCRIPT_CONTINUE;
        }
        if (lower.startsWith("npcprog retrain"))
        {
            String[] tokens = split(lower, ' ');
            String path = tokens != null && tokens.length >= 3 ? tokens[2] : "social";
            behavior_telemetry.retrainNpcProgression(self, path);
            sendSystemMessageTestingOnly(speaker, "npc progression retrained for " + self + " path=" + path);
            return SCRIPT_CONTINUE;
        }
        if (lower.startsWith("npcprog grant"))
        {
            String[] tokens = split(lower, ' ');
            String goal = tokens != null && tokens.length >= 3 ? tokens[2] : "combat";
            behavior_telemetry.trackNpcActivityExperience(self, goal, true);
            behavior_telemetry.saveNpcProfileCheckpoint(self);
            sendSystemMessageTestingOnly(speaker, "npc progression grant applied for " + self + " goal=" + goal);
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_CONTINUE;
    }

    private void runGoalCycleProgression(obj_id self, String archetype) throws InterruptedException
    {
        String[] goals = new String[]{"combat", "crafting", "social", "travel", "economic"};
        int cursor = getIntObjVar(self, VAR_GOAL_CURSOR);
        int index = cursor % goals.length;
        String goal = goals[index];
        String routineGoal = behavior_telemetry.selectRoutineGoalForNpc(self, archetype);
        if (routineGoal != null && routineGoal.length() > 0)
        {
            goal = normalizeRoutineGoal(routineGoal);
        }
        setObjVar(self, VAR_GOAL_CURSOR, cursor + 1);

        if ("social".equals(goal))
        {
            boolean success = rand(1, 100) <= 70;
            behavior_telemetry.recordNpcGoalCycle(self, goal, success);
            behavior_telemetry.recordNpcOutcome(self, "social_interaction", success);
            grantActivityStipend(self, success);
        }
        else if ("travel".equals(goal))
        {
            boolean success = rand(1, 100) <= 65;
            behavior_telemetry.recordNpcGoalCycle(self, "travel", success);
            behavior_telemetry.recordNpcOutcome(self, "travel_success", success);
            if (success)
            {
                behavior_telemetry.noteNpcTargetZone(self);
                grantActivityStipend(self, true);
            }
        }

        behavior_telemetry.saveNpcProfileCheckpoint(self);
    }

    private String normalizeRoutineGoal(String routineGoal) throws InterruptedException
    {
        if ("cantina".equals(routineGoal) || "rest".equals(routineGoal))
        {
            return "social";
        }
        if ("mission_terminal".equals(routineGoal) || "vendor".equals(routineGoal))
        {
            return "economic";
        }
        if ("patrol".equals(routineGoal))
        {
            return "combat";
        }
        if ("travel".equals(routineGoal) || "travel_hub".equals(routineGoal))
        {
            return "travel";
        }
        if ("crafting".equals(routineGoal))
        {
            return "crafting";
        }
        return "social";
    }

    private void evaluatePendingAuthoritativeCompletions(obj_id self) throws InterruptedException
    {
        evaluatePendingCombatCompletion(self);
        evaluatePendingMissionCompletion(self);
        evaluatePendingCraftingCompletion(self);
    }

    private void evaluatePendingCombatCompletion(obj_id self) throws InterruptedException
    {
        obj_id target = getObjIdObjVar(self, VAR_PENDING_COMBAT_TARGET);
        if (!isIdValid(target))
        {
            return;
        }
        if (ai_lib.isDead(target) || isIncapacitated(target))
        {
            onAuthoritativeCompletion(self, "combat", "kill", target);
            return;
        }
        int startedAt = getIntObjVar(self, VAR_PENDING_COMBAT_AT);
        if (startedAt > 0 && getGameTime() - startedAt > 120)
        {
            behavior_telemetry.recordNpcGoalCycle(self, "combat", false);
            behavior_telemetry.recordNpcOutcome(self, "combat_loss", false);
            removeObjVar(self, VAR_PENDING_COMBAT_TARGET);
            removeObjVar(self, VAR_PENDING_COMBAT_AT);
        }
    }

    private void evaluatePendingMissionCompletion(obj_id self) throws InterruptedException
    {
        obj_id[] pending = getObjIdArrayObjVar(self, VAR_PENDING_MISSION_IDS);
        if (pending == null || pending.length < 1)
        {
            return;
        }
        int completeCount = 0;
        for (int i = 0; i < pending.length; i++)
        {
            obj_id mission = pending[i];
            if (!isIdValid(mission) || !exists(mission))
            {
                completeCount++;
                continue;
            }
            int missionStatus = getIntObjVar(mission, "mission.status");
            int completion = getIntObjVar(mission, "mission.npcCompletion");
            if (missionStatus >= 2 || completion > 0)
            {
                completeCount++;
            }
        }
        if (completeCount >= pending.length)
        {
            onAuthoritativeCompletion(self, "economic", "mission_turnin", obj_id.NULL_ID);
            creditNpcIncome(self, rand(ECONOMY_MISSION_PAYOUT_MIN, ECONOMY_MISSION_PAYOUT_MAX), "mission_success", money.ACCT_MISSION_DYNAMIC, ECONOMY_MAX_INCOME_PER_PULSE, 30);
            removeObjVar(self, VAR_PENDING_MISSION_IDS);
            removeObjVar(self, VAR_PENDING_MISSION_AT);
            return;
        }
        int assignedAt = getIntObjVar(self, VAR_PENDING_MISSION_AT);
        if (assignedAt > 0 && getGameTime() - assignedAt > 900)
        {
            behavior_telemetry.recordNpcGoalCycle(self, "economic", false);
            behavior_telemetry.recordNpcOutcome(self, "economic_interaction", false);
            removeObjVar(self, VAR_PENDING_MISSION_IDS);
            removeObjVar(self, VAR_PENDING_MISSION_AT);
        }
    }

    private void evaluatePendingCraftingCompletion(obj_id self) throws InterruptedException
    {
        obj_id tracker = getObjIdObjVar(self, VAR_PENDING_CRAFT_TRACKER);
        String state = getStringObjVar(self, VAR_PENDING_CRAFT_STATE);
        if (!isIdValid(tracker) || state == null || state.length() < 1)
        {
            return;
        }
        int baseline = getIntObjVar(self, VAR_PENDING_CRAFT_BASELINE);
        int current = getNpcCraftingTotalContribution(tracker, self);
        if ("start".equals(state) && current > baseline)
        {
            setObjVar(self, VAR_PENDING_CRAFT_STATE, "progress");
            setObjVar(self, VAR_PENDING_CRAFT_AT, getGameTime());
            return;
        }
        if ("progress".equals(state))
        {
            if (!community_crafting.isSessionActive(tracker))
            {
                onAuthoritativeCompletion(self, "crafting", "session_complete", tracker);
                removeObjVar(self, VAR_PENDING_CRAFT_TRACKER);
                removeObjVar(self, VAR_PENDING_CRAFT_STATE);
                removeObjVar(self, VAR_PENDING_CRAFT_AT);
                removeObjVar(self, VAR_PENDING_CRAFT_BASELINE);
                return;
            }
        }
        int startedAt = getIntObjVar(self, VAR_PENDING_CRAFT_AT);
        if (startedAt > 0 && getGameTime() - startedAt > 480)
        {
            behavior_telemetry.recordNpcGoalCycle(self, "crafting", false);
            behavior_telemetry.recordNpcOutcome(self, "crafting_interaction", false);
            removeObjVar(self, VAR_PENDING_CRAFT_TRACKER);
            removeObjVar(self, VAR_PENDING_CRAFT_STATE);
            removeObjVar(self, VAR_PENDING_CRAFT_AT);
            removeObjVar(self, VAR_PENDING_CRAFT_BASELINE);
        }
    }

    private boolean isViableCombatTarget(obj_id self, obj_id target) throws InterruptedException
    {
        if (!isIdValid(self) || isInvulnerable(self) || !ai_lib.isAttackable(self))
        {
            return false;
        }
        return isIdValid(target) && !target.isBuildoutObject() && target.isLoaded() && !target.isBeingDestroyed() && exists(target) && isInWorld(target) && isTangible(target) && !ai_lib.aiIsDead(target) && (isPlayer(target) || ai_lib.isNpc(target) || ai_lib.isMonster(target)) && pvpCanAttack(self, target);
    }

    private obj_id findNearbyCombatTarget(obj_id self) throws InterruptedException
    {
        obj_id[] nearby = getNearbyObjectsInActionRange(self);
        if (nearby.length < 1)
        {
            return obj_id.NULL_ID;
        }
        obj_id best = obj_id.NULL_ID;
        float bestDistance = ACTION_SCAN_RADIUS + 1.0f;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id test = nearby[i];
            if (test == self || !isViableCombatTarget(self, test))
            {
                continue;
            }
            if (!canSee(self, test) || (!ai_lib.isAggroToward(self, test) && !ai_lib.isAggroToward(test, self)))
            {
                continue;
            }
            float dist = getDistance(self, test);
            if (dist < bestDistance)
            {
                best = test;
                bestDistance = dist;
            }
        }
        return best;
    }

    private void initializeNpcLifecycleState(obj_id self, boolean freshSpawn) throws InterruptedException
    {
        setMovementWalk(self);
        setScale(self, PLAYER_METRIC_SCALE);
        behavior_telemetry.initializeNpcProgressionState(self);
        initializeEconomyProfile(self);
        if (!hasObjVar(self, "npc.simProfile.goalState.current"))
        {
            setObjVar(self, "npc.simProfile.goalState.current", "social");
        }
        if (freshSpawn)
        {
            setObjVar(self, VAR_GOAL_CURSOR, Math.max(0, getIntObjVar(self, VAR_GOAL_CURSOR)));
        }
        messageTo(self, "updateBehaviorArchetype", null, 2, false);
        int delay = behavior_telemetry.getActionDelayForArchetype(getStringObjVar(self, VAR_ARCHETYPE));
        messageTo(self, "runAdaptiveActionPulse", null, Math.max(3, delay), false);
    }

    private float applyProgressionToGoalWeight(obj_id self, String goal, float baseWeight) throws InterruptedException
    {
        int levelBand = behavior_telemetry.getNpcLevelBand(self);
        String professionPath = behavior_telemetry.getNpcProfessionPath(self);
        float bonus = 0.0f;
        if (levelBand >= 3)
        {
            if ("combat".equals(goal) || "travel".equals(goal) || "economic".equals(goal))
            {
                bonus += 0.4f;
            }
        }
        if (levelBand >= 4)
        {
            bonus += 0.6f;
        }
        if (("combat".equals(professionPath) && "combat".equals(goal)) || ("trader".equals(professionPath) && ("crafting".equals(goal) || "economic".equals(goal))) || ("social".equals(professionPath) && "social".equals(goal)))
        {
            bonus += 0.8f;
        }
        return baseWeight + bonus;
    }

    private void initializeEconomyProfile(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, VAR_ECONOMY_WALLET))
        {
            setObjVar(self, VAR_ECONOMY_WALLET, ECONOMY_DEFAULT_WALLET);
        }
        if (!hasObjVar(self, VAR_ECONOMY_RESERVE))
        {
            setObjVar(self, VAR_ECONOMY_RESERVE, ECONOMY_DEFAULT_RESERVE);
        }
        if (!hasObjVar(self, VAR_ECONOMY_DAILY_SPEND_CAP))
        {
            setObjVar(self, VAR_ECONOMY_DAILY_SPEND_CAP, ECONOMY_DEFAULT_DAILY_SPEND_CAP);
        }
        if (!hasObjVar(self, VAR_ECONOMY_INCOME_COOLDOWN))
        {
            setObjVar(self, VAR_ECONOMY_INCOME_COOLDOWN, 0);
        }
        refreshEconomyDay(self);
        clampEconomyProfile(self);
    }

    private void clampEconomyProfile(obj_id self) throws InterruptedException
    {
        setObjVar(self, VAR_ECONOMY_WALLET, Math.max(0, Math.min(getIntObjVar(self, VAR_ECONOMY_WALLET), ECONOMY_MAX_WALLET)));
        setObjVar(self, VAR_ECONOMY_RESERVE, Math.max(0, Math.min(getIntObjVar(self, VAR_ECONOMY_RESERVE), ECONOMY_MAX_RESERVE)));
        setObjVar(self, VAR_ECONOMY_DAILY_SPEND_CAP, Math.max(200, Math.min(getIntObjVar(self, VAR_ECONOMY_DAILY_SPEND_CAP), ECONOMY_MAX_DAILY_SPEND_CAP)));
    }

    private void refreshEconomyDay(obj_id self) throws InterruptedException
    {
        int dayIndex = getGameTime() / ECONOMY_DAY_SECONDS;
        int lastIndex = getIntObjVar(self, VAR_ECONOMY_SPEND_DAY_INDEX);
        if (lastIndex != dayIndex)
        {
            setObjVar(self, VAR_ECONOMY_SPEND_DAY_INDEX, dayIndex);
            setObjVar(self, VAR_ECONOMY_SPENT_TODAY, 0);
        }
    }

    private void grantActivityStipend(obj_id self, boolean activitySuccess) throws InterruptedException
    {
        if (!activitySuccess)
        {
            return;
        }
        int now = getGameTime();
        if (getIntObjVar(self, VAR_ECONOMY_ACTIVITY_STIPEND_NEXT) > now)
        {
            return;
        }
        int stipend = rand(ECONOMY_ACTIVITY_STIPEND_MIN, ECONOMY_ACTIVITY_STIPEND_MAX);
        if (creditNpcIncome(self, stipend, "activity_stipend", money.ACCT_GROUND_QUEST, ECONOMY_MAX_INCOME_PER_PULSE / 2, 45))
        {
            setObjVar(self, VAR_ECONOMY_ACTIVITY_STIPEND_NEXT, now + ECONOMY_ACTIVITY_STIPEND_COOLDOWN_SECONDS);
        }
    }

    private boolean creditNpcIncome(obj_id self, int requestedAmount, String reason, String sourceAccount, int antiInflationCap, int cooldownSeconds) throws InterruptedException
    {
        int now = getGameTime();
        int cooldownUntil = getIntObjVar(self, VAR_ECONOMY_INCOME_COOLDOWN);
        if (cooldownUntil > now)
        {
            return false;
        }
        int amount = Math.max(0, Math.min(requestedAmount, Math.max(1, antiInflationCap)));
        if (amount < 1)
        {
            return false;
        }
        boolean paid = money.bankTo(sourceAccount, self, amount);
        if (!paid)
        {
            return false;
        }

        setObjVar(self, VAR_ECONOMY_WALLET, Math.min(ECONOMY_MAX_WALLET, getIntObjVar(self, VAR_ECONOMY_WALLET) + amount));
        recordCreditDelta(self, reason, amount, true);
        behavior_telemetry.recordNpcOutcome(self, reason, true);
        setObjVar(self, VAR_ECONOMY_INCOME_COOLDOWN, now + Math.max(1, cooldownSeconds));
        return true;
    }

    private void debitNpcEconomy(obj_id self, int amount, String reason) throws InterruptedException
    {
        int debit = Math.max(0, amount);
        if (debit < 1)
        {
            return;
        }
        setObjVar(self, VAR_ECONOMY_WALLET, Math.max(0, getIntObjVar(self, VAR_ECONOMY_WALLET) - debit));
        recordCreditDelta(self, reason, debit, false);
        behavior_telemetry.recordNpcOutcome(self, reason, true);
    }

    private void recordCreditDelta(obj_id self, String reason, int amount, boolean credit) throws InterruptedException
    {
        int signedAmount = credit ? Math.abs(amount) : (0 - Math.abs(amount));
        setObjVar(self, VAR_ECONOMY_LAST_DELTA_REASON, reason);
        setObjVar(self, VAR_ECONOMY_LAST_DELTA_AMOUNT, signedAmount);
        setObjVar(self, VAR_ECONOMY_LAST_DELTA_DIRECTION, credit ? "credit" : "debit");
        setObjVar(self, VAR_ECONOMY_LAST_DELTA_AT, getGameTime());
        String base = VAR_ECONOMY_ROOT + "audit." + reason;
        setObjVar(self, base + ".lastAmount", signedAmount);
        setObjVar(self, base + ".lastAt", getGameTime());
        setObjVar(self, base + ".count", Math.max(0, getIntObjVar(self, base + ".count")) + 1);
        setObjVar(self, base + ".total", getIntObjVar(self, base + ".total") + signedAmount);
    }

}
