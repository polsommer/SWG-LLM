package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

public class npc_player_planner extends script.base_script
{
    public npc_player_planner()
    {
    }

    public static final String OBJVAR_ROOT = "ai.brain";
    public static final String OBJVAR_NEEDS_ROOT = OBJVAR_ROOT + ".needs";
    public static final String OBJVAR_NEED_HUNGER = OBJVAR_NEEDS_ROOT + ".hunger";
    public static final String OBJVAR_NEED_CREDITS = OBJVAR_NEEDS_ROOT + ".credits";
    public static final String OBJVAR_NEED_GEAR_DURABILITY = OBJVAR_NEEDS_ROOT + ".gearDurability";
    public static final String OBJVAR_NEED_MISSION_INTENT = OBJVAR_NEEDS_ROOT + ".missionIntent";
    public static final String OBJVAR_NEED_SOCIAL_INTENT = OBJVAR_NEEDS_ROOT + ".socialIntent";

    public static final String OBJVAR_SCHEDULE_ROOT = OBJVAR_ROOT + ".schedule";
    public static final String OBJVAR_SCHEDULE_ACTIVE_BLOCK = OBJVAR_SCHEDULE_ROOT + ".activeBlock";
    public static final String OBJVAR_SCHEDULE_ACTIVE_UNTIL = OBJVAR_SCHEDULE_ROOT + ".activeUntil";

    public static final String OBJVAR_PLAN_QUEUE_ROOT = OBJVAR_ROOT + ".planQueue";
    public static final String OBJVAR_PLAN_QUEUE_SIZE = OBJVAR_PLAN_QUEUE_ROOT + ".size";

    public static final String BLOCK_COMBAT = "combat";
    public static final String BLOCK_BAZAAR = "bazaar";
    public static final String BLOCK_CRAFTING = "crafting";
    public static final String BLOCK_MISSIONING = "missioning";
    public static final String BLOCK_IDLE_SOCIAL = "idleSocial";

    public static void initializePlanner(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        initializeNeeds(npc);
        initializeSchedule(npc);
        if (!hasObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE))
        {
            setObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE, 0);
        }
    }

    public static boolean shouldRefreshPlan(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return false;
        }
        if (isPlanInvalidated(npc))
        {
            return true;
        }
        return getQueueSize(npc) <= 0;
    }

    public static void refreshPlan(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        initializePlanner(npc);
        decayNeeds(npc);
        refreshScheduleBlock(npc);
        clearPlanQueue(npc);

        String activeBlock = hasObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_BLOCK) ? getStringObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_BLOCK) : BLOCK_IDLE_SOCIAL;
        buildPlanForBlock(npc, activeBlock);

        if (getQueueSize(npc) <= 0)
        {
            dictionary fallback = npc_player_brain.tick(npc);
            enqueueStep(npc, fallback);
        }
    }

    public static dictionary dequeueNextStep(obj_id npc) throws InterruptedException
    {
        dictionary command = new dictionary();
        command.put("goal", npc_player_brain.GOAL_SOCIAL);
        command.put("subgoal", "idle");
        command.put("action", npc_player_brain.COMMAND_TALK);

        if (!isIdValid(npc) || !exists(npc) || getQueueSize(npc) <= 0)
        {
            return command;
        }

        String root = OBJVAR_PLAN_QUEUE_ROOT + ".0";
        if (hasObjVar(npc, root + ".goal"))
        {
            command.put("goal", getStringObjVar(npc, root + ".goal"));
        }
        if (hasObjVar(npc, root + ".subgoal"))
        {
            command.put("subgoal", getStringObjVar(npc, root + ".subgoal"));
        }
        if (hasObjVar(npc, root + ".action"))
        {
            command.put("action", getStringObjVar(npc, root + ".action"));
        }
        if (hasObjVar(npc, root + ".target"))
        {
            command.put("target", getObjIdObjVar(npc, root + ".target"));
        }
        if (hasObjVar(npc, root + ".dest"))
        {
            command.put("dest", getLocationObjVar(npc, root + ".dest"));
        }

        shiftQueueLeft(npc);
        return command;
    }

    public static boolean isPlanInvalidated(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return true;
        }
        if (ai_lib.isAiDead(npc) || isIncapacitated(npc))
        {
            return true;
        }
        return npc_player_brain.shouldAbandonCurrentGoal(npc);
    }

    private static void initializeNeeds(obj_id npc) throws InterruptedException
    {
        if (!hasObjVar(npc, OBJVAR_NEED_HUNGER))
        {
            setObjVar(npc, OBJVAR_NEED_HUNGER, rand(25, 45));
        }
        if (!hasObjVar(npc, OBJVAR_NEED_CREDITS))
        {
            setObjVar(npc, OBJVAR_NEED_CREDITS, rand(300, 900));
        }
        if (!hasObjVar(npc, OBJVAR_NEED_GEAR_DURABILITY))
        {
            setObjVar(npc, OBJVAR_NEED_GEAR_DURABILITY, rand(55, 95));
        }
        if (!hasObjVar(npc, OBJVAR_NEED_MISSION_INTENT))
        {
            setObjVar(npc, OBJVAR_NEED_MISSION_INTENT, rand(30, 65));
        }
        if (!hasObjVar(npc, OBJVAR_NEED_SOCIAL_INTENT))
        {
            setObjVar(npc, OBJVAR_NEED_SOCIAL_INTENT, rand(35, 70));
        }
    }

    private static void decayNeeds(obj_id npc) throws InterruptedException
    {
        setObjVar(npc, OBJVAR_NEED_HUNGER, clampNeed(getIntObjVar(npc, OBJVAR_NEED_HUNGER) + rand(3, 8)));
        setObjVar(npc, OBJVAR_NEED_CREDITS, Math.max(0, getIntObjVar(npc, OBJVAR_NEED_CREDITS) - rand(5, 18)));
        setObjVar(npc, OBJVAR_NEED_GEAR_DURABILITY, clampNeed(getIntObjVar(npc, OBJVAR_NEED_GEAR_DURABILITY) - rand(1, 4)));
        setObjVar(npc, OBJVAR_NEED_MISSION_INTENT, clampNeed(getIntObjVar(npc, OBJVAR_NEED_MISSION_INTENT) + rand(-3, 6)));
        setObjVar(npc, OBJVAR_NEED_SOCIAL_INTENT, clampNeed(getIntObjVar(npc, OBJVAR_NEED_SOCIAL_INTENT) + rand(-2, 6)));
    }

    private static int clampNeed(int value) throws InterruptedException
    {
        if (value < 0)
        {
            return 0;
        }
        if (value > 100)
        {
            return 100;
        }
        return value;
    }

    private static void initializeSchedule(obj_id npc) throws InterruptedException
    {
        setDefaultWeight(npc, BLOCK_COMBAT, 12);
        setDefaultWeight(npc, BLOCK_BAZAAR, 16);
        setDefaultWeight(npc, BLOCK_CRAFTING, 14);
        setDefaultWeight(npc, BLOCK_MISSIONING, 16);
        setDefaultWeight(npc, BLOCK_IDLE_SOCIAL, 22);

        if (!hasObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_BLOCK))
        {
            setObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_BLOCK, BLOCK_IDLE_SOCIAL);
        }
        if (!hasObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_UNTIL))
        {
            setObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_UNTIL, 0);
        }
    }

    private static void setDefaultWeight(obj_id npc, String block, int value) throws InterruptedException
    {
        String key = OBJVAR_SCHEDULE_ROOT + "." + block + ".weight";
        if (!hasObjVar(npc, key))
        {
            setObjVar(npc, key, value);
        }
    }

    private static void refreshScheduleBlock(obj_id npc) throws InterruptedException
    {
        int now = getGameTime();
        if (hasObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_UNTIL) && getIntObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_UNTIL) > now)
        {
            return;
        }
        String block = pickWeightedBlockForTime(npc);
        setObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_BLOCK, block);
        setObjVar(npc, OBJVAR_SCHEDULE_ACTIVE_UNTIL, now + rand(80, 160));
    }

    private static String pickWeightedBlockForTime(obj_id npc) throws InterruptedException
    {
        int hour = (getGameTime() / 3600) % 24;
        int hunger = getIntObjVar(npc, OBJVAR_NEED_HUNGER);
        int missionIntent = getIntObjVar(npc, OBJVAR_NEED_MISSION_INTENT);
        int socialIntent = getIntObjVar(npc, OBJVAR_NEED_SOCIAL_INTENT);

        int combatWeight = getWeight(npc, BLOCK_COMBAT) + ((hour >= 19 || hour <= 4) ? 5 : 0);
        int bazaarWeight = getWeight(npc, BLOCK_BAZAAR) + (hunger / 8);
        int craftingWeight = getWeight(npc, BLOCK_CRAFTING) + ((hour >= 8 && hour <= 17) ? 6 : 0);
        int missionWeight = getWeight(npc, BLOCK_MISSIONING) + (missionIntent / 7);
        int socialWeight = getWeight(npc, BLOCK_IDLE_SOCIAL) + (socialIntent / 6);

        int total = Math.max(1, combatWeight) + Math.max(1, bazaarWeight) + Math.max(1, craftingWeight) + Math.max(1, missionWeight) + Math.max(1, socialWeight);
        int pick = rand(1, total);

        int running = Math.max(1, combatWeight);
        if (pick <= running)
        {
            return BLOCK_COMBAT;
        }
        running += Math.max(1, bazaarWeight);
        if (pick <= running)
        {
            return BLOCK_BAZAAR;
        }
        running += Math.max(1, craftingWeight);
        if (pick <= running)
        {
            return BLOCK_CRAFTING;
        }
        running += Math.max(1, missionWeight);
        if (pick <= running)
        {
            return BLOCK_MISSIONING;
        }
        return BLOCK_IDLE_SOCIAL;
    }

    private static int getWeight(obj_id npc, String block) throws InterruptedException
    {
        String key = OBJVAR_SCHEDULE_ROOT + "." + block + ".weight";
        return hasObjVar(npc, key) ? getIntObjVar(npc, key) : 1;
    }

    private static void buildPlanForBlock(obj_id npc, String block) throws InterruptedException
    {
        if (block.equals(BLOCK_COMBAT))
        {
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_TRAVEL, "seek_threat", npc_player_brain.COMMAND_MOVE, obj_id.NULL_ID, randomNearbyDestination(npc, 18.0f)));
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_COMBAT, "acquire_threat", npc_player_brain.COMMAND_ATTACK, chooseCombatTarget(npc), randomNearbyDestination(npc, 8.0f)));
            return;
        }
        if (block.equals(BLOCK_BAZAAR))
        {
            obj_id vendor = chooseVendorTarget(npc);
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_TRAVEL, "move_to_bazaar", npc_player_brain.COMMAND_MOVE, vendor, randomNearbyDestination(npc, 14.0f)));
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_VENDOR, "browse_terminal", npc_player_brain.COMMAND_INTERACT, vendor, randomNearbyDestination(npc, 6.0f)));
            return;
        }
        if (block.equals(BLOCK_CRAFTING))
        {
            obj_id station = chooseVendorTarget(npc);
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_GATHERING, "collect_inputs", npc_player_brain.COMMAND_MOVE, obj_id.NULL_ID, randomNearbyDestination(npc, 16.0f)));
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_CRAFTING, "prepare_station", npc_player_brain.COMMAND_INTERACT, station, randomNearbyDestination(npc, 6.0f)));
            return;
        }
        if (block.equals(BLOCK_MISSIONING))
        {
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_TRAVEL, "move_to_terminal", npc_player_brain.COMMAND_MOVE, obj_id.NULL_ID, randomNearbyDestination(npc, 20.0f)));
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_VENDOR, "accept_mission", npc_player_brain.COMMAND_INTERACT, chooseVendorTarget(npc), randomNearbyDestination(npc, 8.0f)));
            enqueueStep(npc, buildStep(npc_player_brain.GOAL_TRAVEL, "route_to_mission", npc_player_brain.COMMAND_MOVE, obj_id.NULL_ID, randomNearbyDestination(npc, 20.0f)));
            return;
        }

        obj_id talkTarget = chooseSocialTarget(npc);
        enqueueStep(npc, buildStep(npc_player_brain.GOAL_TRAVEL, "wander_social", npc_player_brain.COMMAND_MOVE, talkTarget, randomNearbyDestination(npc, 12.0f)));
        enqueueStep(npc, buildStep(npc_player_brain.GOAL_SOCIAL, "find_conversation", npc_player_brain.COMMAND_TALK, talkTarget, randomNearbyDestination(npc, 5.0f)));
    }

    private static dictionary buildStep(String goal, String subgoal, String action, obj_id target, location dest) throws InterruptedException
    {
        dictionary step = new dictionary();
        step.put("goal", goal);
        step.put("subgoal", subgoal);
        step.put("action", action);
        step.put("target", target);
        step.put("dest", dest);
        return step;
    }

    private static void enqueueStep(obj_id npc, dictionary step) throws InterruptedException
    {
        if (step == null)
        {
            return;
        }
        int size = getQueueSize(npc);
        String root = OBJVAR_PLAN_QUEUE_ROOT + "." + size;

        if (step.containsKey("goal"))
        {
            setObjVar(npc, root + ".goal", step.getString("goal"));
        }
        if (step.containsKey("subgoal"))
        {
            setObjVar(npc, root + ".subgoal", step.getString("subgoal"));
        }
        if (step.containsKey("action"))
        {
            setObjVar(npc, root + ".action", step.getString("action"));
        }
        if (step.containsKey("target"))
        {
            setObjVar(npc, root + ".target", step.getObjId("target"));
        }
        if (step.containsKey("dest"))
        {
            setObjVar(npc, root + ".dest", step.getLocation("dest"));
        }
        setObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE, size + 1);
    }

    private static int getQueueSize(obj_id npc) throws InterruptedException
    {
        return hasObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE) ? getIntObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE) : 0;
    }

    private static void shiftQueueLeft(obj_id npc) throws InterruptedException
    {
        int size = getQueueSize(npc);
        if (size <= 0)
        {
            setObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE, 0);
            return;
        }

        removePlanStep(npc, 0);
        for (int i = 1; i < size; i++)
        {
            copyPlanStep(npc, i, i - 1);
        }
        removePlanStep(npc, size - 1);
        setObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE, size - 1);
    }

    private static void copyPlanStep(obj_id npc, int fromIndex, int toIndex) throws InterruptedException
    {
        copyPlanField(npc, fromIndex, toIndex, "goal");
        copyPlanField(npc, fromIndex, toIndex, "subgoal");
        copyPlanField(npc, fromIndex, toIndex, "action");
        copyPlanField(npc, fromIndex, toIndex, "target");
        copyPlanField(npc, fromIndex, toIndex, "dest");
    }

    private static void copyPlanField(obj_id npc, int fromIndex, int toIndex, String field) throws InterruptedException
    {
        String from = OBJVAR_PLAN_QUEUE_ROOT + "." + fromIndex + "." + field;
        String to = OBJVAR_PLAN_QUEUE_ROOT + "." + toIndex + "." + field;
        if (!hasObjVar(npc, from))
        {
            removeObjVar(npc, to);
            return;
        }
        if (field.equals("goal") || field.equals("subgoal") || field.equals("action"))
        {
            setObjVar(npc, to, getStringObjVar(npc, from));
            return;
        }
        if (field.equals("target"))
        {
            setObjVar(npc, to, getObjIdObjVar(npc, from));
            return;
        }
        setObjVar(npc, to, getLocationObjVar(npc, from));
    }

    private static void removePlanStep(obj_id npc, int index) throws InterruptedException
    {
        String root = OBJVAR_PLAN_QUEUE_ROOT + "." + index;
        removeObjVar(npc, root + ".goal");
        removeObjVar(npc, root + ".subgoal");
        removeObjVar(npc, root + ".action");
        removeObjVar(npc, root + ".target");
        removeObjVar(npc, root + ".dest");
    }

    private static void clearPlanQueue(obj_id npc) throws InterruptedException
    {
        int size = getQueueSize(npc);
        for (int i = 0; i < size; i++)
        {
            removePlanStep(npc, i);
        }
        setObjVar(npc, OBJVAR_PLAN_QUEUE_SIZE, 0);
    }

    private static location randomNearbyDestination(obj_id npc, float radius) throws InterruptedException
    {
        location loc = new location(getLocation(npc));
        loc.x += rand(-radius, radius);
        loc.z += rand(-radius, radius);
        return loc;
    }

    private static obj_id chooseCombatTarget(obj_id npc) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(getLocation(npc), 28);
        if (nearby == null)
        {
            return obj_id.NULL_ID;
        }
        obj_id allyTarget = obj_id.NULL_ID;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id candidate = nearby[i];
            if (!isIdValid(candidate) || candidate == npc)
            {
                continue;
            }
            if (npc_social_memory.isHostileActor(npc, candidate))
            {
                continue;
            }
            if (isMob(candidate) && getBehavior(candidate) >= BEHAVIOR_ALERT)
            {
                return candidate;
            }
            if (hasObjVar(npc, npc_social_memory.OBJVAR_ROOT + ".ally." + String.valueOf(candidate)))
            {
                allyTarget = candidate;
            }
        }
        return allyTarget;
    }

    private static obj_id chooseVendorTarget(obj_id npc) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(getLocation(npc), 30);
        if (nearby == null)
        {
            return obj_id.NULL_ID;
        }
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id candidate = nearby[i];
            if (!isIdValid(candidate) || candidate == npc)
            {
                continue;
            }
            if (hasScript(candidate, "terminal.npc_vendor") || hasScript(candidate, "terminal.nonvendor"))
            {
                return candidate;
            }
        }
        return obj_id.NULL_ID;
    }

    private static obj_id chooseSocialTarget(obj_id npc) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(getLocation(npc), 24);
        if (nearby == null)
        {
            return obj_id.NULL_ID;
        }
        obj_id fallback = obj_id.NULL_ID;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id candidate = nearby[i];
            if (!isIdValid(candidate) || candidate == npc)
            {
                continue;
            }
            if (npc_social_memory.isHostileActor(npc, candidate))
            {
                continue;
            }
            if (hasObjVar(npc, npc_social_memory.OBJVAR_ROOT + ".ally." + String.valueOf(candidate)))
            {
                return candidate;
            }
            if (fallback == obj_id.NULL_ID && (isPlayer(candidate) || hasScript(candidate, "ai.townperson")))
            {
                fallback = candidate;
            }
        }
        return fallback;
    }
}
