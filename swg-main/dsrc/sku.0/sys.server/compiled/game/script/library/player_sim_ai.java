package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

public class player_sim_ai extends script.base_script
{
    public player_sim_ai()
    {
    }

    public static final String MESSAGE_COORDINATOR_PULSE = "handleCoordinatorPulse";
    public static final String SCRIPTVAR_GOAL = "playerSim.goal";
    public static final String SCRIPTVAR_SUBGOAL = "playerSim.subgoal";
    public static final String SCRIPTVAR_HOME = "playerSim.home";
    public static final String SCRIPTVAR_PATH_FAILURES = "playerSim.pathFailures";

    public static final String GOAL_TRAVEL_TO_POI = "travel-to-poi";
    public static final String GOAL_GRIND_COMBAT = "grind-combat";
    public static final String GOAL_GATHER_RESOURCE = "gather-resource";
    public static final String GOAL_VISIT_VENDOR = "visit-vendor";
    public static final String GOAL_SOCIAL_IDLE = "social-idle";
    public static final String GOAL_RECOVER_AFTER_COMBAT = "recover-after-combat";

    public static final float AGGRO_SCAN_RADIUS = 32.0f;
    public static final float SOCIAL_SCAN_RADIUS = 20.0f;

    public int OnAttach(obj_id self) throws InterruptedException
    {
        initializeCoordinator(self);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        initializeCoordinator(self);
        return SCRIPT_CONTINUE;
    }

    public int OnUnloadedFromMemory(obj_id self) throws InterruptedException
    {
        if (utils.hasScriptVar(self, SCRIPTVAR_GOAL))
        {
            utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, GOAL_RECOVER_AFTER_COMBAT);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnEnteredCombat(obj_id self) throws InterruptedException
    {
        utils.setScriptVar(self, SCRIPTVAR_GOAL, GOAL_RECOVER_AFTER_COMBAT);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "combat-active");
        schedulePulse(self, 5.0f);
        return SCRIPT_CONTINUE;
    }

    public int OnExitedCombat(obj_id self) throws InterruptedException
    {
        utils.setScriptVar(self, SCRIPTVAR_GOAL, GOAL_RECOVER_AFTER_COMBAT);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "combat-rejoin-loop");
        schedulePulse(self, 1.0f);
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathNotFound(obj_id self) throws InterruptedException
    {
        markPathFailure(self, "move-path-not-found");
        return SCRIPT_CONTINUE;
    }

    public int OnFollowPathNotFound(obj_id self, obj_id target) throws InterruptedException
    {
        markPathFailure(self, "follow-path-not-found");
        return SCRIPT_CONTINUE;
    }

    public int handleCoordinatorPulse(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || ai_lib.aiIsDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        selectAndExecuteGoal(self);
        schedulePulse(self, rand(6.0f, 12.0f));
        return SCRIPT_CONTINUE;
    }

    private void initializeCoordinator(obj_id self) throws InterruptedException
    {
        if (!utils.hasScriptVar(self, SCRIPTVAR_HOME))
        {
            utils.setScriptVar(self, SCRIPTVAR_HOME, getLocation(self));
        }
        if (!utils.hasScriptVar(self, SCRIPTVAR_GOAL))
        {
            utils.setScriptVar(self, SCRIPTVAR_GOAL, GOAL_SOCIAL_IDLE);
            utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "spawned");
        }
        ai_lib.enableModernAmbientLife(self);
        schedulePulse(self, rand(2.0f, 5.0f));
    }

    private void schedulePulse(obj_id self, float delay) throws InterruptedException
    {
        if (!hasMessageTo(self, MESSAGE_COORDINATOR_PULSE))
        {
            messageTo(self, MESSAGE_COORDINATOR_PULSE, null, delay, false);
        }
    }

    private void selectAndExecuteGoal(obj_id self) throws InterruptedException
    {
        String selectedGoal = pickGoal(self);
        utils.setScriptVar(self, SCRIPTVAR_GOAL, selectedGoal);

        if (selectedGoal.equals(GOAL_GRIND_COMBAT))
        {
            executeGrindCombat(self);
            return;
        }
        if (selectedGoal.equals(GOAL_TRAVEL_TO_POI))
        {
            executeTravelToPoi(self);
            return;
        }
        if (selectedGoal.equals(GOAL_VISIT_VENDOR))
        {
            executeVisitVendor(self);
            return;
        }
        if (selectedGoal.equals(GOAL_GATHER_RESOURCE))
        {
            executeGatherResource(self);
            return;
        }
        if (selectedGoal.equals(GOAL_RECOVER_AFTER_COMBAT))
        {
            executeRecover(self);
            return;
        }
        executeSocialIdle(self);
    }

    private String pickGoal(obj_id self) throws InterruptedException
    {
        if (ai_lib.isInCombat(self))
        {
            return GOAL_GRIND_COMBAT;
        }

        obj_id nearestEnemy = findNearestEnemy(self, AGGRO_SCAN_RADIUS);
        if (isIdValid(nearestEnemy))
        {
            return GOAL_GRIND_COMBAT;
        }

        int pathFailures = utils.hasScriptVar(self, SCRIPTVAR_PATH_FAILURES) ? utils.getIntScriptVar(self, SCRIPTVAR_PATH_FAILURES) : 0;
        if (pathFailures > 2)
        {
            return GOAL_RECOVER_AFTER_COMBAT;
        }

        int socialCount = countNearbySocialActors(self);
        int roll = rand(0, 100);
        if (socialCount >= 2 && roll > 60)
        {
            return GOAL_SOCIAL_IDLE;
        }
        if (roll > 75)
        {
            return GOAL_VISIT_VENDOR;
        }
        if (roll > 45)
        {
            return GOAL_TRAVEL_TO_POI;
        }
        return GOAL_GATHER_RESOURCE;
    }

    private void executeTravelToPoi(obj_id self) throws InterruptedException
    {
        location home = utils.getLocationScriptVar(self, SCRIPTVAR_HOME);
        location[] route = new location[4];
        route[0] = getOffset(home, 10.0f, 4.0f);
        route[1] = getOffset(home, -8.0f, 12.0f);
        route[2] = getOffset(home, -12.0f, -9.0f);
        route[3] = getOffset(home, 6.0f, -13.0f);
        ai_lib.setPatrolRandomPath(self, route);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "poi-patrol-loop");
    }

    private void executeGrindCombat(obj_id self) throws InterruptedException
    {
        obj_id enemy = findNearestEnemy(self, AGGRO_SCAN_RADIUS);
        if (!isIdValid(enemy))
        {
            utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "seek-target");
            ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_LOITER);
            return;
        }
        startCombat(self, enemy);
        addHate(self, enemy, 10);
        ai_lib.triggerAgroLinks(self, enemy);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "combat-engaged");
    }

    private void executeGatherResource(obj_id self) throws InterruptedException
    {
        location home = utils.getLocationScriptVar(self, SCRIPTVAR_HOME);
        location gatherLoc = getOffset(home, rand(-18.0f, 18.0f), rand(-18.0f, 18.0f));
        pathTo(self, gatherLoc);
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_LOITER);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "resource-node-scan");
    }

    private void executeVisitVendor(obj_id self) throws InterruptedException
    {
        obj_id vendor = findNearestVendor(self, 48.0f);
        if (isIdValid(vendor))
        {
            ai_lib.aiFollow(self, vendor, 3.0f, 7.0f);
            utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "follow-vendor");
            return;
        }
        executeTravelToPoi(self);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "vendor-fallback-travel");
    }

    private void executeSocialIdle(obj_id self) throws InterruptedException
    {
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_LOITER);
        ai_lib.enableModernAmbientLife(self);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "ambient-social");
    }

    private void executeRecover(obj_id self) throws InterruptedException
    {
        location home = utils.getLocationScriptVar(self, SCRIPTVAR_HOME);
        pathTo(self, home);
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_LOITER);
        if (utils.hasScriptVar(self, SCRIPTVAR_PATH_FAILURES))
        {
            utils.removeScriptVar(self, SCRIPTVAR_PATH_FAILURES);
        }
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, "recover-home");
    }

    private void markPathFailure(obj_id self, String reason) throws InterruptedException
    {
        int failures = utils.hasScriptVar(self, SCRIPTVAR_PATH_FAILURES) ? utils.getIntScriptVar(self, SCRIPTVAR_PATH_FAILURES) : 0;
        failures++;
        utils.setScriptVar(self, SCRIPTVAR_PATH_FAILURES, failures);
        utils.setScriptVar(self, SCRIPTVAR_GOAL, GOAL_RECOVER_AFTER_COMBAT);
        utils.setScriptVar(self, SCRIPTVAR_SUBGOAL, reason);
        schedulePulse(self, 1.0f);
    }

    private obj_id findNearestEnemy(obj_id self, float radius) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(getLocation(self), radius);
        obj_id target = null;
        float bestDist = radius + 1.0f;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id test = nearby[i];
            if (!isIdValid(test) || test == self || ai_lib.aiIsDead(test))
            {
                continue;
            }
            if (!canSee(self, test) || !isMob(test))
            {
                continue;
            }
            if (!ai_lib.isAggroToward(self, test) && !ai_lib.isAggroToward(test, self))
            {
                continue;
            }
            float dist = getDistance(self, test);
            if (dist < bestDist)
            {
                target = test;
                bestDist = dist;
            }
        }
        return target;
    }

    private obj_id findNearestVendor(obj_id self, float radius) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(getLocation(self), radius);
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id test = nearby[i];
            if (!isIdValid(test) || !ai_lib.isNpc(test))
            {
                continue;
            }
            String template = getTemplateName(test);
            if (template != null && template.indexOf("vendor") > -1)
            {
                return test;
            }
        }
        return null;
    }

    private int countNearbySocialActors(obj_id self) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(getLocation(self), SOCIAL_SCAN_RADIUS);
        int count = 0;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id test = nearby[i];
            if (!isIdValid(test) || test == self || ai_lib.aiIsDead(test))
            {
                continue;
            }
            if (isPlayer(test) || ai_lib.isNpc(test))
            {
                count++;
            }
        }
        return count;
    }

    private location getOffset(location start, float dx, float dz) throws InterruptedException
    {
        location out = new location(start);
        out.x = start.x + dx;
        out.z = start.z + dz;
        return out;
    }
}
