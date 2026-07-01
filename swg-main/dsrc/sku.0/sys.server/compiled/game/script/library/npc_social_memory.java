package script.library;

import script.location;
import script.obj_id;

public class npc_social_memory extends script.base_script
{
    public npc_social_memory()
    {
    }

    public static final String OBJVAR_ROOT = "ai.social";
    public static final int AFFINITY_MIN = -100;
    public static final int AFFINITY_MAX = 100;
    public static final int PLAYER_INTERACTION_COOLDOWN = 25;
    public static final int TOPIC_DEDUPE_WINDOW = 90;

    public static String getActorMemoryRoot(obj_id actor) throws InterruptedException
    {
        return OBJVAR_ROOT + "." + String.valueOf(actor);
    }

    public static void ensureActorMemory(obj_id npc, obj_id actor) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc) || !isIdValid(actor))
        {
            return;
        }
        String root = getActorMemoryRoot(actor);
        if (!hasObjVar(npc, root + ".affinity"))
        {
            setObjVar(npc, root + ".affinity", 0);
        }
        if (!hasObjVar(npc, root + ".isFriendly"))
        {
            setObjVar(npc, root + ".isFriendly", 0);
        }
        if (!hasObjVar(npc, root + ".isHostile"))
        {
            setObjVar(npc, root + ".isHostile", 0);
        }
        if (!hasObjVar(npc, root + ".lastInteractionTime"))
        {
            setObjVar(npc, root + ".lastInteractionTime", 0);
        }
        if (!hasObjVar(npc, root + ".lastTopic"))
        {
            setObjVar(npc, root + ".lastTopic", "intro");
        }
        if (!hasObjVar(npc, root + ".prevTopic"))
        {
            setObjVar(npc, root + ".prevTopic", "none");
        }
    }

    public static boolean canStartInteraction(obj_id npc, obj_id actor, int cooldownSeconds) throws InterruptedException
    {
        ensureActorMemory(npc, actor);
        String root = getActorMemoryRoot(actor);
        int lastAt = hasObjVar(npc, root + ".lastInteractionTime") ? getIntObjVar(npc, root + ".lastInteractionTime") : 0;
        return getGameTime() - lastAt >= Math.max(1, cooldownSeconds);
    }

    public static String selectTopic(obj_id npc, obj_id actor, String currentGoal) throws InterruptedException
    {
        ensureActorMemory(npc, actor);
        String locationType = resolveLocationType(npc);
        String eventTopic = getRecentEventTopic(npc);
        String goalTopic = "smalltalk";

        if (currentGoal != null && currentGoal.length() > 0)
        {
            if (currentGoal.equals(npc_player_brain.GOAL_COMBAT))
            {
                goalTopic = "combat";
            }
            else if (currentGoal.equals(npc_player_brain.GOAL_VENDOR))
            {
                goalTopic = "trade";
            }
            else if (currentGoal.equals(npc_player_brain.GOAL_TRAVEL))
            {
                goalTopic = "travel";
            }
            else if (currentGoal.equals(npc_player_brain.GOAL_CRAFTING))
            {
                goalTopic = "craft";
            }
            else if (currentGoal.equals(npc_player_brain.GOAL_SOCIAL))
            {
                goalTopic = "rumor";
            }
        }

        String topic = eventTopic;
        if (topic.equals("none"))
        {
            if (locationType.equals("bazaar") && rand(1, 100) <= 65)
            {
                topic = "trade";
            }
            else if (locationType.equals("cantina") && rand(1, 100) <= 65)
            {
                topic = "rumor";
            }
            else if (locationType.equals("wilderness") && rand(1, 100) <= 65)
            {
                topic = "travel";
            }
            else
            {
                topic = goalTopic;
            }
        }

        String root = getActorMemoryRoot(actor);
        String lastTopic = hasObjVar(npc, root + ".lastTopic") ? getStringObjVar(npc, root + ".lastTopic") : "";
        int lastAt = hasObjVar(npc, root + ".lastInteractionTime") ? getIntObjVar(npc, root + ".lastInteractionTime") : 0;
        if (topic.equals(lastTopic) && getGameTime() - lastAt < TOPIC_DEDUPE_WINDOW)
        {
            topic = pickAlternateTopic(topic, locationType);
        }
        return topic;
    }

    public static void recordInteraction(obj_id npc, obj_id actor, String topic, int affinityDelta, boolean hostile) throws InterruptedException
    {
        ensureActorMemory(npc, actor);
        String root = getActorMemoryRoot(actor);

        String oldTopic = hasObjVar(npc, root + ".lastTopic") ? getStringObjVar(npc, root + ".lastTopic") : "none";
        setObjVar(npc, root + ".prevTopic", oldTopic);
        setObjVar(npc, root + ".lastTopic", topic);
        setObjVar(npc, root + ".lastInteractionTime", getGameTime());

        int affinity = hasObjVar(npc, root + ".affinity") ? getIntObjVar(npc, root + ".affinity") : 0;
        affinity = clampValue(affinity + affinityDelta, AFFINITY_MIN, AFFINITY_MAX);
        setObjVar(npc, root + ".affinity", affinity);

        int isFriendly = affinity >= 20 ? 1 : 0;
        int isHostile = hostile || affinity <= -20 ? 1 : 0;
        setObjVar(npc, root + ".isFriendly", isFriendly);
        setObjVar(npc, root + ".isHostile", isHostile);

        applyPlannerBiasFromAffinity(npc, actor, affinity, isHostile == 1);
    }

    public static void noteEvent(obj_id npc, String eventType) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc) || eventType == null || eventType.length() < 1)
        {
            return;
        }
        String root = OBJVAR_ROOT + ".events." + eventType;
        setObjVar(npc, root + ".time", getGameTime());
    }

    public static String resolveLocationType(obj_id npc) throws InterruptedException
    {
        location here = getLocation(npc);
        if (here != null && here.area != null)
        {
            String area = here.area.toLowerCase();
            if (area.indexOf("bazaar") >= 0 || area.indexOf("market") >= 0)
            {
                return "bazaar";
            }
            if (area.indexOf("cantina") >= 0 || area.indexOf("bar") >= 0)
            {
                return "cantina";
            }
        }

        obj_id top = getTopMostContainer(npc);
        if (isIdValid(top))
        {
            String template = getTemplateName(top);
            if (template != null)
            {
                String lower = template.toLowerCase();
                if (lower.indexOf("bazaar") >= 0 || lower.indexOf("market") >= 0)
                {
                    return "bazaar";
                }
                if (lower.indexOf("cantina") >= 0 || lower.indexOf("bar") >= 0)
                {
                    return "cantina";
                }
            }
        }
        return "wilderness";
    }

    private static String getRecentEventTopic(obj_id npc) throws InterruptedException
    {
        int now = getGameTime();
        int combatAt = hasObjVar(npc, OBJVAR_ROOT + ".events.combat.time") ? getIntObjVar(npc, OBJVAR_ROOT + ".events.combat.time") : 0;
        int tradeAt = hasObjVar(npc, OBJVAR_ROOT + ".events.trade.time") ? getIntObjVar(npc, OBJVAR_ROOT + ".events.trade.time") : 0;
        int missionAt = hasObjVar(npc, OBJVAR_ROOT + ".events.mission.time") ? getIntObjVar(npc, OBJVAR_ROOT + ".events.mission.time") : 0;

        if (combatAt > 0 && now - combatAt < 180)
        {
            return "combat";
        }
        if (tradeAt > 0 && now - tradeAt < 180)
        {
            return "trade";
        }
        if (missionAt > 0 && now - missionAt < 240)
        {
            return "mission";
        }
        return "none";
    }

    private static String pickAlternateTopic(String topic, String locationType) throws InterruptedException
    {
        if (topic.equals("combat"))
        {
            return "mission";
        }
        if (topic.equals("trade"))
        {
            return locationType.equals("cantina") ? "rumor" : "travel";
        }
        if (topic.equals("mission"))
        {
            return "rumor";
        }
        if (topic.equals("rumor"))
        {
            return locationType.equals("bazaar") ? "trade" : "smalltalk";
        }
        return "smalltalk";
    }

    private static void applyPlannerBiasFromAffinity(obj_id npc, obj_id actor, int affinity, boolean hostile) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        int socialWeight = hasObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_IDLE_SOCIAL + ".weight") ? getIntObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_IDLE_SOCIAL + ".weight") : 3;
        int combatWeight = hasObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_COMBAT + ".weight") ? getIntObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_COMBAT + ".weight") : 2;

        if (hostile)
        {
            setObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_COMBAT + ".weight", clampValue(combatWeight - 1, 1, 12));
            setObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_IDLE_SOCIAL + ".weight", clampValue(socialWeight - 1, 1, 12));
            setObjVar(npc, OBJVAR_ROOT + ".avoid." + String.valueOf(actor), 1);
            return;
        }

        if (affinity >= 35)
        {
            setObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_COMBAT + ".weight", clampValue(combatWeight + 1, 1, 12));
            setObjVar(npc, npc_player_planner.OBJVAR_SCHEDULE_ROOT + "." + npc_player_planner.BLOCK_IDLE_SOCIAL + ".weight", clampValue(socialWeight + 1, 1, 12));
            setObjVar(npc, OBJVAR_ROOT + ".ally." + String.valueOf(actor), 1);
        }
    }

    private static int clampValue(int value, int min, int max) throws InterruptedException
    {
        if (value < min)
        {
            return min;
        }
        if (value > max)
        {
            return max;
        }
        return value;
    }

    public static boolean isHostileActor(obj_id npc, obj_id actor) throws InterruptedException
    {
        String root = getActorMemoryRoot(actor);
        return hasObjVar(npc, root + ".isHostile") && getIntObjVar(npc, root + ".isHostile") == 1;
    }

    public static int getAffinity(obj_id npc, obj_id actor) throws InterruptedException
    {
        String root = getActorMemoryRoot(actor);
        return hasObjVar(npc, root + ".affinity") ? getIntObjVar(npc, root + ".affinity") : 0;
    }
}
