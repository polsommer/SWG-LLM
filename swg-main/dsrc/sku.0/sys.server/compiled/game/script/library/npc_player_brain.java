package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

public class npc_player_brain extends script.base_script
{
    public npc_player_brain()
    {
    }
    public static final String OBJVAR_ROOT = "ai.brain";
    public static final String OBJVAR_CAREER_ARCHETYPE = OBJVAR_ROOT + ".careerArchetype";
    public static final String OBJVAR_PROGRESSION_STAGE = OBJVAR_ROOT + ".progressionStage";
    public static final String OBJVAR_CURRENT_GOAL = OBJVAR_ROOT + ".currentGoal";
    public static final String OBJVAR_CURRENT_SUBGOAL = OBJVAR_ROOT + ".currentSubgoal";
    public static final String OBJVAR_GOAL_STARTED = OBJVAR_ROOT + ".goalStarted";
    public static final String OBJVAR_GOAL_LAST_PROGRESS = OBJVAR_ROOT + ".goalLastProgress";
    public static final String OBJVAR_GOAL_TIMEOUT = OBJVAR_ROOT + ".goalTimeout";
    public static final String OBJVAR_MEMORY_BUDGET = OBJVAR_ROOT + ".memoryBudget";
    public static final String OBJVAR_HISTORY_ROOT = OBJVAR_ROOT + ".history";
    public static final String OBJVAR_COOLDOWN_ROOT = OBJVAR_ROOT + ".cooldowns";
    public static final String OBJVAR_LEARN_ROOT = "ai.learn";
    public static final String OBJVAR_LEARN_GOAL_STATS_ROOT = OBJVAR_LEARN_ROOT + ".goalStats";
    public static final String OBJVAR_LEARN_ZONE_ROOT = OBJVAR_LEARN_ROOT + ".zone";
    public static final String OBJVAR_LEARN_OUTCOMES_ROOT = OBJVAR_LEARN_ROOT + ".outcomes";
    public static final String OBJVAR_LEARN_OUTCOME_INDEX = OBJVAR_LEARN_ROOT + ".outcomeIndex";
    public static final String OBJVAR_LEARN_LAST_UPDATE = OBJVAR_LEARN_ROOT + ".lastUpdate";
    public static final String OBJVAR_LEARN_LAST_DEATH_OUTCOME = OBJVAR_LEARN_ROOT + ".lastDeathOutcome";
    public static final String OBJVAR_PLAYER_SIM_ROOT = "ai.playerSimulation";
    public static final String OBJVAR_PLAYER_SIM_ENABLED = OBJVAR_PLAYER_SIM_ROOT + ".enabled";
    public static final String OBJVAR_PLAYER_SIM_VISIBLE = OBJVAR_PLAYER_SIM_ROOT + ".visible";
    public static final String OBJVAR_PLAYER_SIM_ONLINE = OBJVAR_PLAYER_SIM_ROOT + ".online";

    public static final String OBJVAR_ECON_ROOT = "ai.econ";
    public static final String OBJVAR_ECON_CREDITS = OBJVAR_ECON_ROOT + ".credits";
    public static final String OBJVAR_ECON_RESERVE = OBJVAR_ECON_ROOT + ".reserve";
    public static final String OBJVAR_ECON_INVENTORY_ROOT = OBJVAR_ECON_ROOT + ".inventory";

    public static final String GOAL_COMBAT = "combat";
    public static final String GOAL_GATHERING = "gathering";
    public static final String GOAL_CRAFTING = "crafting";
    public static final String GOAL_SOCIAL = "social";
    public static final String GOAL_TRAVEL = "travel";
    public static final String GOAL_VENDOR = "vendor";

    public static final String COMMAND_MOVE = "move";
    public static final String COMMAND_INTERACT = "interact";
    public static final String COMMAND_ATTACK = "attack";
    public static final String COMMAND_TALK = "talk";

    public static final String[] ALL_GOALS =
    {
        GOAL_COMBAT,
        GOAL_GATHERING,
        GOAL_CRAFTING,
        GOAL_SOCIAL,
        GOAL_TRAVEL,
        GOAL_VENDOR
    };

    public static final int MAX_GOAL_WEIGHT = 90;
    public static final int MAX_ADAPTIVE_BONUS = 22;
    public static final int MAX_ADAPTIVE_PENALTY = 22;
    public static final int MAX_OUTCOME_EVENTS = 8;
    public static final int LEARN_STALE_TIMEOUT_SECONDS = 10800;
    public static final int MAX_REASONABLE_DURATION = 900;
    public static final float MAX_REASONABLE_REWARD = 1500.0f;

    public static void initialize(obj_id npc, String defaultCareer, int memoryBudget) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        if (!hasObjVar(npc, OBJVAR_CAREER_ARCHETYPE))
        {
            setObjVar(npc, OBJVAR_CAREER_ARCHETYPE, defaultCareer);
        }
        if (!hasObjVar(npc, OBJVAR_PROGRESSION_STAGE))
        {
            setObjVar(npc, OBJVAR_PROGRESSION_STAGE, 1);
        }
        if (!hasObjVar(npc, OBJVAR_MEMORY_BUDGET))
        {
            setObjVar(npc, OBJVAR_MEMORY_BUDGET, memoryBudget);
        }
        if (!hasObjVar(npc, OBJVAR_CURRENT_GOAL))
        {
            setObjVar(npc, OBJVAR_CURRENT_GOAL, GOAL_SOCIAL);
        }
        if (!hasObjVar(npc, OBJVAR_CURRENT_SUBGOAL))
        {
            setObjVar(npc, OBJVAR_CURRENT_SUBGOAL, "idle");
        }
        if (!hasObjVar(npc, OBJVAR_GOAL_STARTED))
        {
            setObjVar(npc, OBJVAR_GOAL_STARTED, getGameTime());
        }
        if (!hasObjVar(npc, OBJVAR_GOAL_LAST_PROGRESS))
        {
            setObjVar(npc, OBJVAR_GOAL_LAST_PROGRESS, getGameTime());
        }
        if (!hasObjVar(npc, OBJVAR_GOAL_TIMEOUT))
        {
            setObjVar(npc, OBJVAR_GOAL_TIMEOUT, getGameTime() + rand(45, 75));
        }
        sanitizeLearningData(npc);

        npc_player_planner.initializePlanner(npc);
        npc_economy.initializeEconomy(npc);
        npc_mission_runner.initialize(npc);

        setObjVar(npc, OBJVAR_PLAYER_SIM_ENABLED, 1);
        setObjVar(npc, OBJVAR_PLAYER_SIM_VISIBLE, 1);
        setObjVar(npc, OBJVAR_PLAYER_SIM_ONLINE, 1);
    }

    public static dictionary tick(obj_id npc) throws InterruptedException
    {
        dictionary command = new dictionary();
        command.put("goal", GOAL_SOCIAL);
        command.put("subgoal", "idle");
        command.put("action", COMMAND_TALK);

        if (!isIdValid(npc) || !exists(npc))
        {
            return command;
        }
        sanitizeLearningData(npc);
        if (ai_lib.isAiDead(npc) || isIncapacitated(npc))
        {
            recordOutcomeEvent(npc, getCurrentGoal(npc), false, "death", 0.0f);
            return command;
        }

        checkForTimeoutAndFallback(npc);

        dictionary missionCommand = npc_mission_runner.getMissionCommand(npc);
        if (missionCommand != null)
        {
            return missionCommand;
        }

        String selectedGoal = chooseGoalByUtility(npc);
        String subgoal = chooseSubgoal(npc, selectedGoal);
        setCurrentGoal(npc, selectedGoal, subgoal);

        command.put("goal", selectedGoal);
        command.put("subgoal", subgoal);
        command.put("action", chooseActionForGoal(selectedGoal));
        command.put("target", chooseTargetForGoal(npc, selectedGoal));
        command.put("dest", chooseDestinationForGoal(npc, selectedGoal));

        if (selectedGoal.equals(GOAL_VENDOR))
        {
            npc_economy.processVendorGoal(npc, command);
        }

        applyCooldown(npc, selectedGoal);
        bumpHistory(npc, selectedGoal);
        return command;
    }

    public static void recordProgress(obj_id npc, String note) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        setObjVar(npc, OBJVAR_GOAL_LAST_PROGRESS, getGameTime());
        if (note != null && note.length() > 0)
        {
            setObjVar(npc, OBJVAR_CURRENT_SUBGOAL, note);
        }
    }

    public static void reportOutcomeEvent(obj_id npc, boolean success, String causeTag, float reward) throws InterruptedException
    {
        if (causeTag == null || causeTag.length() < 1)
        {
            causeTag = success ? "mission_success" : "low_profit";
        }
        String goal = getCurrentGoal(npc);
        recordOutcomeEvent(npc, goal, success, causeTag, reward);
    }

    public static boolean shouldAbandonCurrentGoal(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc) || !hasObjVar(npc, OBJVAR_GOAL_TIMEOUT))
        {
            return false;
        }
        int timeoutAt = getIntObjVar(npc, OBJVAR_GOAL_TIMEOUT);
        int lastProgress = hasObjVar(npc, OBJVAR_GOAL_LAST_PROGRESS) ? getIntObjVar(npc, OBJVAR_GOAL_LAST_PROGRESS) : 0;
        return getGameTime() > timeoutAt && (lastProgress == 0 || getGameTime() - lastProgress > 25);
    }

    public static void checkForTimeoutAndFallback(obj_id npc) throws InterruptedException
    {
        if (!shouldAbandonCurrentGoal(npc))
        {
            return;
        }
        String failedGoal = getCurrentGoal(npc);
        String cause = "low_profit";
        if (hasObjVar(npc, OBJVAR_CURRENT_SUBGOAL))
        {
            String subgoal = getStringObjVar(npc, OBJVAR_CURRENT_SUBGOAL);
            if (subgoal != null && subgoal.indexOf("path") >= 0)
            {
                cause = "path_fail";
                npc_mission_runner.notifyPathingFailure(npc);
            }
        }
        recordOutcomeEvent(npc, failedGoal, false, cause, -4.0f);
        setObjVar(npc, OBJVAR_CURRENT_GOAL, GOAL_TRAVEL);
        setObjVar(npc, OBJVAR_CURRENT_SUBGOAL, "fallback_wander");
        setObjVar(npc, OBJVAR_GOAL_STARTED, getGameTime());
        setObjVar(npc, OBJVAR_GOAL_LAST_PROGRESS, getGameTime());
        setObjVar(npc, OBJVAR_GOAL_TIMEOUT, getGameTime() + rand(45, 75));
    }

    public static String chooseGoalByUtility(obj_id npc) throws InterruptedException
    {
        String career = hasObjVar(npc, OBJVAR_CAREER_ARCHETYPE) ? getStringObjVar(npc, OBJVAR_CAREER_ARCHETYPE) : "civilian";
        int stage = hasObjVar(npc, OBJVAR_PROGRESSION_STAGE) ? getIntObjVar(npc, OBJVAR_PROGRESSION_STAGE) : 1;
        int[] weights = new int[ALL_GOALS.length];

        for (int i = 0; i < ALL_GOALS.length; i++)
        {
            String goal = ALL_GOALS[i];
            int base = 10;
            int context = getWorldContextModifier(npc, goal);
            int careerBias = getCareerBias(career, goal);
            int historyPenalty = getRecentHistoryPenalty(npc, goal);
            int cooldownPenalty = getCooldownPenalty(npc, goal);
            int stageBias = (goal.equals(GOAL_TRAVEL) ? stage * 2 : stage);
            int economyBias = goal.equals(GOAL_VENDOR) ? npc_economy.getVendorUtilityModifier(npc) : 0;
            int adaptiveBias = getAdaptiveLearningModifier(npc, goal);
            int total = base + context + careerBias + stageBias + economyBias + adaptiveBias - historyPenalty - cooldownPenalty;
            if (total < 1)
            {
                total = 1;
            }
            if (total > MAX_GOAL_WEIGHT)
            {
                total = MAX_GOAL_WEIGHT;
            }
            weights[i] = total;
        }

        int totalWeight = 0;
        for (int i = 0; i < weights.length; i++)
        {
            totalWeight += weights[i];
        }
        int pick = rand(1, totalWeight);
        int running = 0;
        for (int i = 0; i < ALL_GOALS.length; i++)
        {
            running += weights[i];
            if (pick <= running)
            {
                return ALL_GOALS[i];
            }
        }
        return GOAL_SOCIAL;
    }

    private static int getWorldContextModifier(obj_id npc, String goal) throws InterruptedException
    {
        location here = getLocation(npc);
        obj_id[] nearby = getObjectsInRange(here, 30);
        if (nearby == null)
        {
            nearby = new obj_id[0];
        }
        int players = 0;
        int hostiles = 0;
        int vendors = 0;
        int npcs = 0;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id target = nearby[i];
            if (!isIdValid(target) || target == npc)
            {
                continue;
            }
            if (isPlayer(target))
            {
                players++;
            }
            else if (isMob(target))
            {
                npcs++;
                if (getBehavior(target) >= BEHAVIOR_ALERT)
                {
                    hostiles++;
                }
            }
            if (hasScript(target, "terminal.npc_vendor") || hasScript(target, "terminal.nonvendor"))
            {
                vendors++;
            }
        }
        if (goal.equals(GOAL_COMBAT))
        {
            return hostiles * 8;
        }
        if (goal.equals(GOAL_SOCIAL))
        {
            return (players + npcs) * 3;
        }
        if (goal.equals(GOAL_VENDOR))
        {
            return vendors * 7;
        }
        if (goal.equals(GOAL_TRAVEL))
        {
            return 8 - Math.min(6, npcs);
        }
        if (goal.equals(GOAL_GATHERING))
        {
            return Math.max(1, 6 - players);
        }
        if (goal.equals(GOAL_CRAFTING))
        {
            return Math.max(1, 4 + vendors - hostiles);
        }
        return 0;
    }

    private static int getCareerBias(String career, String goal) throws InterruptedException
    {
        if (career == null)
        {
            career = "civilian";
        }
        if (career.equals("guard"))
        {
            if (goal.equals(GOAL_COMBAT))
            {
                return 16;
            }
            if (goal.equals(GOAL_TRAVEL))
            {
                return 6;
            }
        }
        else if (career.equals("merchant"))
        {
            if (goal.equals(GOAL_VENDOR))
            {
                return 16;
            }
            if (goal.equals(GOAL_CRAFTING))
            {
                return 10;
            }
        }
        else if (career.equals("artisan"))
        {
            if (goal.equals(GOAL_CRAFTING))
            {
                return 14;
            }
            if (goal.equals(GOAL_GATHERING))
            {
                return 10;
            }
        }
        if (goal.equals(GOAL_SOCIAL))
        {
            return 6;
        }
        return 2;
    }

    private static int getRecentHistoryPenalty(obj_id npc, String goal) throws InterruptedException
    {
        int memoryBudget = hasObjVar(npc, OBJVAR_MEMORY_BUDGET) ? getIntObjVar(npc, OBJVAR_MEMORY_BUDGET) : 6;
        int seen = hasObjVar(npc, OBJVAR_HISTORY_ROOT + "." + goal) ? getIntObjVar(npc, OBJVAR_HISTORY_ROOT + "." + goal) : 0;
        return Math.min(memoryBudget, seen * 2);
    }

    private static int getCooldownPenalty(obj_id npc, String goal) throws InterruptedException
    {
        String key = OBJVAR_COOLDOWN_ROOT + "." + goal;
        if (!hasObjVar(npc, key))
        {
            return 0;
        }
        int readyAt = getIntObjVar(npc, key);
        if (getGameTime() >= readyAt)
        {
            return 0;
        }
        return 10;
    }

    private static void applyCooldown(obj_id npc, String goal) throws InterruptedException
    {
        int duration = rand(18, 45);
        if (goal.equals(GOAL_COMBAT))
        {
            duration = rand(12, 22);
        }
        setObjVar(npc, OBJVAR_COOLDOWN_ROOT + "." + goal, getGameTime() + duration);
    }

    private static void bumpHistory(obj_id npc, String goal) throws InterruptedException
    {
        String historyKey = OBJVAR_HISTORY_ROOT + "." + goal;
        int count = hasObjVar(npc, historyKey) ? getIntObjVar(npc, historyKey) : 0;
        int memoryBudget = hasObjVar(npc, OBJVAR_MEMORY_BUDGET) ? getIntObjVar(npc, OBJVAR_MEMORY_BUDGET) : 6;
        setObjVar(npc, historyKey, Math.min(memoryBudget, count + 1));

        for (int i = 0; i < ALL_GOALS.length; i++)
        {
            String decayKey = OBJVAR_HISTORY_ROOT + "." + ALL_GOALS[i];
            if (ALL_GOALS[i].equals(goal) || !hasObjVar(npc, decayKey))
            {
                continue;
            }
            int old = getIntObjVar(npc, decayKey);
            if (old > 0)
            {
                setObjVar(npc, decayKey, old - 1);
            }
        }
    }

    private static String chooseSubgoal(obj_id npc, String goal) throws InterruptedException
    {
        if (goal.equals(GOAL_COMBAT))
        {
            return "acquire_threat";
        }
        if (goal.equals(GOAL_GATHERING))
        {
            return "search_nodes";
        }
        if (goal.equals(GOAL_CRAFTING))
        {
            return "prepare_station";
        }
        if (goal.equals(GOAL_SOCIAL))
        {
            return "find_conversation";
        }
        if (goal.equals(GOAL_TRAVEL))
        {
            return "patrol_lane";
        }
        if (goal.equals(GOAL_VENDOR))
        {
            return "browse_terminal";
        }
        return "idle";
    }

    private static void setCurrentGoal(obj_id npc, String goal, String subgoal) throws InterruptedException
    {
        String previousGoal = getCurrentGoal(npc);
        if (previousGoal != null && previousGoal.length() > 0)
        {
            int started = hasObjVar(npc, OBJVAR_GOAL_STARTED) ? getIntObjVar(npc, OBJVAR_GOAL_STARTED) : getGameTime();
            int duration = Math.max(1, getGameTime() - started);
            if (duration >= 6 && !previousGoal.equals(goal))
            {
                float reward = estimateRewardForGoal(npc, previousGoal, true);
                recordOutcomeEvent(npc, previousGoal, true, "mission_success", reward);
            }
        }
        setObjVar(npc, OBJVAR_CURRENT_GOAL, goal);
        setObjVar(npc, OBJVAR_CURRENT_SUBGOAL, subgoal);
        setObjVar(npc, OBJVAR_GOAL_STARTED, getGameTime());
        setObjVar(npc, OBJVAR_GOAL_LAST_PROGRESS, getGameTime());
        setObjVar(npc, OBJVAR_GOAL_TIMEOUT, getGameTime() + rand(35, 65));

        int stage = hasObjVar(npc, OBJVAR_PROGRESSION_STAGE) ? getIntObjVar(npc, OBJVAR_PROGRESSION_STAGE) : 1;
        if (rand(1, 100) <= 15)
        {
            setObjVar(npc, OBJVAR_PROGRESSION_STAGE, Math.min(10, stage + 1));
        }
    }


    private static String getCurrentGoal(obj_id npc) throws InterruptedException
    {
        if (!hasObjVar(npc, OBJVAR_CURRENT_GOAL))
        {
            return "";
        }
        return getStringObjVar(npc, OBJVAR_CURRENT_GOAL);
    }

    private static int getAdaptiveLearningModifier(obj_id npc, String goal) throws InterruptedException
    {
        float successRate = getGoalStatFloat(npc, goal, "successRate", 0.5f);
        float avgReward = getGoalStatFloat(npc, goal, "avgReward", 0.0f);
        float zoneSuccess = getZoneStatFloat(npc, "successRate", 0.5f);
        float zoneDanger = getZoneStatFloat(npc, "danger", 0.0f);
        float zoneEconomy = getZoneStatFloat(npc, "economy", 0.0f);

        float rewardNorm = clampf(avgReward / 40.0f, -1.0f, 1.0f);
        float successNorm = clampf((successRate - 0.5f) * 2.0f, -1.0f, 1.0f);
        float zoneSuccessNorm = clampf((zoneSuccess - 0.5f) * 2.0f, -1.0f, 1.0f);

        float modifier = (successNorm * 10.0f) + (rewardNorm * 8.0f) + (zoneSuccessNorm * 4.0f);
        if (goal.equals(GOAL_COMBAT) || goal.equals(GOAL_TRAVEL))
        {
            modifier -= zoneDanger * 6.0f;
        }
        if (goal.equals(GOAL_VENDOR) || goal.equals(GOAL_CRAFTING) || goal.equals(GOAL_GATHERING))
        {
            modifier += zoneEconomy * 6.0f;
        }
        return clamp((int)modifier, -MAX_ADAPTIVE_PENALTY, MAX_ADAPTIVE_BONUS);
    }

    private static void recordOutcomeEvent(obj_id npc, String goal, boolean success, String causeTag, float reward) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc) || goal == null || goal.length() < 1)
        {
            return;
        }
        int now = getGameTime();
        if (causeTag.equals("death"))
        {
            int lastDeath = hasObjVar(npc, OBJVAR_LEARN_LAST_DEATH_OUTCOME) ? getIntObjVar(npc, OBJVAR_LEARN_LAST_DEATH_OUTCOME) : 0;
            if (lastDeath == now)
            {
                return;
            }
            setObjVar(npc, OBJVAR_LEARN_LAST_DEATH_OUTCOME, now);
        }

        int started = hasObjVar(npc, OBJVAR_GOAL_STARTED) ? getIntObjVar(npc, OBJVAR_GOAL_STARTED) : now;
        int duration = clamp(now - started, 1, MAX_REASONABLE_DURATION);
        float normalizedReward = clampf(reward, -MAX_REASONABLE_REWARD, MAX_REASONABLE_REWARD);

        int eventIndex = hasObjVar(npc, OBJVAR_LEARN_OUTCOME_INDEX) ? getIntObjVar(npc, OBJVAR_LEARN_OUTCOME_INDEX) : 0;
        int slot = eventIndex % MAX_OUTCOME_EVENTS;
        String eventRoot = OBJVAR_LEARN_OUTCOMES_ROOT + "." + slot;
        setObjVar(npc, eventRoot + ".goal", goal);
        setObjVar(npc, eventRoot + ".result", success ? "success" : "fail");
        setObjVar(npc, eventRoot + ".cause", causeTag);
        setObjVar(npc, eventRoot + ".duration", duration);
        setObjVar(npc, eventRoot + ".reward", normalizedReward);
        setObjVar(npc, eventRoot + ".time", now);
        setObjVar(npc, OBJVAR_LEARN_OUTCOME_INDEX, eventIndex + 1);
        setObjVar(npc, OBJVAR_LEARN_LAST_UPDATE, now);

        updateGoalStats(npc, goal, success, duration, normalizedReward);
        updateZonePriors(npc, success, causeTag, normalizedReward);
    }

    private static void updateGoalStats(obj_id npc, String goal, boolean success, int duration, float reward) throws InterruptedException
    {
        String root = OBJVAR_LEARN_GOAL_STATS_ROOT + "." + goal;
        int successCount = hasObjVar(npc, root + ".success") ? getIntObjVar(npc, root + ".success") : 0;
        int failCount = hasObjVar(npc, root + ".fail") ? getIntObjVar(npc, root + ".fail") : 0;
        if (success)
        {
            successCount = clamp(successCount + 1, 0, 5000);
        }
        else
        {
            failCount = clamp(failCount + 1, 0, 5000);
        }

        float oldDuration = getGoalStatFloat(npc, goal, "avgDuration", (float)duration);
        float oldReward = getGoalStatFloat(npc, goal, "avgReward", 0.0f);
        float oldSuccessRate = getGoalStatFloat(npc, goal, "successRate", 0.5f);
        float alpha = 0.25f;
        float avgDuration = (oldDuration * (1.0f - alpha)) + ((float)duration * alpha);
        float avgReward = (oldReward * (1.0f - alpha)) + (reward * alpha);
        float successSample = success ? 1.0f : 0.0f;
        float successRate = (oldSuccessRate * (1.0f - alpha)) + (successSample * alpha);

        setObjVar(npc, root + ".success", successCount);
        setObjVar(npc, root + ".fail", failCount);
        setObjVar(npc, root + ".avgDuration", clampf(avgDuration, 1.0f, (float)MAX_REASONABLE_DURATION));
        setObjVar(npc, root + ".avgReward", clampf(avgReward, -MAX_REASONABLE_REWARD, MAX_REASONABLE_REWARD));
        setObjVar(npc, root + ".successRate", clampf(successRate, 0.0f, 1.0f));
    }

    private static void updateZonePriors(obj_id npc, boolean success, String causeTag, float reward) throws InterruptedException
    {
        String zone = getZoneKey(npc);
        String root = OBJVAR_LEARN_ZONE_ROOT + "." + zone;
        int samples = hasObjVar(npc, root + ".samples") ? getIntObjVar(npc, root + ".samples") : 0;
        samples = clamp(samples + 1, 1, 4000);

        float oldSuccessRate = getZoneStatFloat(npc, "successRate", 0.5f);
        float oldRewardRate = getZoneStatFloat(npc, "rewardRate", 0.0f);
        float oldDanger = getZoneStatFloat(npc, "danger", 0.0f);
        float oldEconomy = getZoneStatFloat(npc, "economy", 0.0f);

        float successSample = success ? 1.0f : 0.0f;
        float dangerSample = (causeTag.equals("death") || causeTag.equals("path_fail")) ? 1.0f : 0.0f;
        float economySample = clampf(reward / 100.0f, -1.0f, 1.0f);

        float alpha = 0.2f;
        float successRate = clampf((oldSuccessRate * (1.0f - alpha)) + (successSample * alpha), 0.0f, 1.0f);
        float rewardRate = clampf((oldRewardRate * (1.0f - alpha)) + (reward * alpha), -MAX_REASONABLE_REWARD, MAX_REASONABLE_REWARD);
        float danger = clampf((oldDanger * (1.0f - alpha)) + (dangerSample * alpha), 0.0f, 1.0f);
        float economy = clampf((oldEconomy * (1.0f - alpha)) + (economySample * alpha), -1.0f, 1.0f);

        setObjVar(npc, root + ".samples", samples);
        setObjVar(npc, root + ".successRate", successRate);
        setObjVar(npc, root + ".rewardRate", rewardRate);
        setObjVar(npc, root + ".danger", danger);
        setObjVar(npc, root + ".economy", economy);
    }

    private static float getGoalStatFloat(obj_id npc, String goal, String key, float defaultValue) throws InterruptedException
    {
        String path = OBJVAR_LEARN_GOAL_STATS_ROOT + "." + goal + "." + key;
        if (!hasObjVar(npc, path))
        {
            return defaultValue;
        }
        float v = getFloatObjVar(npc, path);
        if (Float.isNaN(v) || Float.isInfinite(v))
        {
            return defaultValue;
        }
        return v;
    }

    private static float getZoneStatFloat(obj_id npc, String key, float defaultValue) throws InterruptedException
    {
        String path = OBJVAR_LEARN_ZONE_ROOT + "." + getZoneKey(npc) + "." + key;
        if (!hasObjVar(npc, path))
        {
            return defaultValue;
        }
        float v = getFloatObjVar(npc, path);
        if (Float.isNaN(v) || Float.isInfinite(v))
        {
            return defaultValue;
        }
        return v;
    }

    private static String getZoneKey(obj_id npc) throws InterruptedException
    {
        String zone = getCurrentSceneName();
        location loc = getLocation(npc);
        if (loc != null && loc.area != null && loc.area.length() > 0)
        {
            zone = loc.area;
        }
        if (zone == null || zone.length() < 1)
        {
            zone = "unknown";
        }
        return zone;
    }

    private static float estimateRewardForGoal(obj_id npc, String goal, boolean success) throws InterruptedException
    {
        if (!success)
        {
            return -8.0f;
        }
        if (goal.equals(GOAL_VENDOR))
        {
            return 18.0f + (float)npc_economy.getVendorUtilityModifier(npc);
        }
        if (goal.equals(GOAL_COMBAT))
        {
            return 10.0f;
        }
        if (goal.equals(GOAL_GATHERING) || goal.equals(GOAL_CRAFTING))
        {
            return 12.0f;
        }
        return 6.0f;
    }

    private static void sanitizeLearningData(obj_id npc) throws InterruptedException
    {
        int now = getGameTime();
        if (hasObjVar(npc, OBJVAR_LEARN_LAST_UPDATE))
        {
            int updated = getIntObjVar(npc, OBJVAR_LEARN_LAST_UPDATE);
            if (updated <= 0 || updated > now + 120 || now - updated > LEARN_STALE_TIMEOUT_SECONDS)
            {
                removeObjVar(npc, OBJVAR_LEARN_ROOT);
                setObjVar(npc, OBJVAR_LEARN_LAST_UPDATE, now);
                return;
            }
        }
        for (int i = 0; i < ALL_GOALS.length; i++)
        {
            String root = OBJVAR_LEARN_GOAL_STATS_ROOT + "." + ALL_GOALS[i];
            int success = hasObjVar(npc, root + ".success") ? getIntObjVar(npc, root + ".success") : 0;
            int fail = hasObjVar(npc, root + ".fail") ? getIntObjVar(npc, root + ".fail") : 0;
            if (success < 0 || fail < 0 || success > 5000 || fail > 5000)
            {
                removeObjVar(npc, root);
                continue;
            }
            float avgDuration = hasObjVar(npc, root + ".avgDuration") ? getFloatObjVar(npc, root + ".avgDuration") : 1.0f;
            float avgReward = hasObjVar(npc, root + ".avgReward") ? getFloatObjVar(npc, root + ".avgReward") : 0.0f;
            if (Float.isNaN(avgDuration) || Float.isNaN(avgReward) || Float.isInfinite(avgDuration) || Float.isInfinite(avgReward))
            {
                removeObjVar(npc, root);
            }
        }
        setObjVar(npc, OBJVAR_LEARN_LAST_UPDATE, now);
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampf(float value, float min, float max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static String chooseActionForGoal(String goal) throws InterruptedException
    {
        if (goal.equals(GOAL_COMBAT))
        {
            return COMMAND_ATTACK;
        }
        if (goal.equals(GOAL_SOCIAL))
        {
            return COMMAND_TALK;
        }
        if (goal.equals(GOAL_VENDOR) || goal.equals(GOAL_CRAFTING))
        {
            return COMMAND_INTERACT;
        }
        return COMMAND_MOVE;
    }

    private static obj_id chooseTargetForGoal(obj_id npc, String goal) throws InterruptedException
    {
        location here = getLocation(npc);
        obj_id[] nearby = getObjectsInRange(here, 24);
        if (nearby == null)
        {
            nearby = new obj_id[0];
        }
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id candidate = nearby[i];
            if (!isIdValid(candidate) || candidate == npc)
            {
                continue;
            }
            if (goal.equals(GOAL_COMBAT) && isMob(candidate) && getBehavior(candidate) >= BEHAVIOR_ALERT)
            {
                return candidate;
            }
            if (goal.equals(GOAL_SOCIAL) && isPlayer(candidate) && !isIncapacitated(candidate) && !isDead(candidate))
            {
                return candidate;
            }
            if (goal.equals(GOAL_SOCIAL) && isMob(candidate) && hasScript(candidate, "ai.townperson"))
            {
                return candidate;
            }
            if (goal.equals(GOAL_VENDOR))
            {
                if (hasScript(candidate, "terminal.npc_vendor") || hasScript(candidate, "terminal.nonvendor"))
                {
                    return candidate;
                }
            }
        }
        return obj_id.NULL_ID;
    }

    private static location chooseDestinationForGoal(obj_id npc, String goal) throws InterruptedException
    {
        location loc = new location(getLocation(npc));
        float maxDrift = goal.equals(GOAL_TRAVEL) ? 22.0f : 10.0f;
        location socialAnchor = getNearbySocialAnchor(npc, goal);
        if (socialAnchor != null)
        {
            loc = new location(socialAnchor);
            maxDrift = goal.equals(GOAL_TRAVEL) ? 9.0f : 5.0f;
        }
        loc.x += rand(-maxDrift, maxDrift);
        loc.z += rand(-maxDrift, maxDrift);
        return loc;
    }

    private static location getNearbySocialAnchor(obj_id npc, String goal) throws InterruptedException
    {
        location here = getLocation(npc);
        obj_id[] nearby = getObjectsInRange(here, 42);
        if (nearby == null)
        {
            return null;
        }
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id candidate = nearby[i];
            if (!isIdValid(candidate) || candidate == npc)
            {
                continue;
            }
            if (goal.equals(GOAL_SOCIAL) && isPlayer(candidate))
            {
                return getLocation(candidate);
            }
            if (goal.equals(GOAL_VENDOR) && (hasScript(candidate, "terminal.npc_vendor") || hasScript(candidate, "terminal.nonvendor")))
            {
                return getLocation(candidate);
            }
        }
        return null;
    }
}
