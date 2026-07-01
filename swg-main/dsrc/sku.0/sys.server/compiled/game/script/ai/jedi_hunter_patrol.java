package script.ai;

import script.dictionary;
import script.location;
import script.obj_id;
import script.library.ai_lib;
import script.library.chat;
import script.library.jedi_hunter;
import script.library.utils;

import java.util.Vector;

public class jedi_hunter_patrol extends script.base_script
{
    public jedi_hunter_patrol()
    {
    }

    public static final String OBJVAR_STATE = "jediHunter.state";
    public static final String OBJVAR_PATROL_ORIGIN = "jediHunter.patrol.origin";
    public static final String OBJVAR_COOLDOWN_BASE = "jediHunter.cooldown";
    public static final String OBJVAR_KNOWN_PATROL_POINTS = "jediHunter.patrol.knownPoints";

    public static final String SCRIPTVAR_STATE = "jediHunter.state";
    public static final String SCRIPTVAR_TARGET = "jediHunter.target";
    public static final String SCRIPTVAR_PATROL_POINTS = "jediHunter.patrol.points";
    public static final String SCRIPTVAR_PATROL_INDEX = "jediHunter.patrol.index";
    public static final String SCRIPTVAR_NEXT_AMBIENT = "jediHunter.nextAmbient";
    public static final String SCRIPTVAR_LAST_PATROL_DEST = "jediHunter.patrol.lastDest";

    public static final int STATE_PATROL = 0;
    public static final int STATE_APPROACH = 1;
    public static final int STATE_QUESTION = 2;
    public static final int STATE_RESOLVE = 3;
    public static final int STATE_DISENGAGE = 4;

    public static final float SCAN_RANGE = 80.0f;
    public static final float APPROACH_MIN_RANGE = 4.0f;
    public static final float APPROACH_MAX_RANGE = 7.0f;
    public static final float QUESTION_RANGE = 9.0f;
    public static final int COOLDOWN_SECONDS = 180;

    public static final String BARK_INTERROGATE = "interrogate";
    public static final String BARK_DISMISS_NON_JEDI = "dismiss_non_jedi";
    public static final String BARK_FINE_DEMAND = "fine_demand";
    public static final String BARK_ALERT_ATTACK = "alert_attack";
    public static final String BARK_VICTORY = "victory";
    public static final String BARK_DISENGAGE = "disengage";

    public static final int MIN_PATROL_POINTS = 8;
    public static final int MAX_PATROL_POINTS = 24;
    public static final float MIN_POINT_SEPARATION = 12.0f;

    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_PATROL_ORIGIN))
        {
            setObjVar(self, OBJVAR_PATROL_ORIGIN, getLocation(self));
        }

        initializeLearnedPatrol(self);
        beginPatrol(self);
        messageTo(self, "handleThink", null, 1.0f, false);
        return SCRIPT_CONTINUE;
    }


    public int OnIncapacitatedTarget(obj_id self, obj_id victim) throws InterruptedException
    {
        if (isIdValid(victim) && isPlayer(victim))
        {
            chat.chat(self, jedi_hunter.getBarkLine(self, BARK_VICTORY, "Target neutralized. Sector secured."));
        }
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathComplete(obj_id self) throws InterruptedException
    {
        if (getState(self) == STATE_PATROL && rand(0, 100) <= 30)
        {
            learnPatrolFromEnvironment(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathNotFound(obj_id self) throws InterruptedException
    {
        if (getState(self) == STATE_PATROL)
        {
            location lastDestination = utils.getLocationScriptVar(self, SCRIPTVAR_LAST_PATROL_DEST);
            if (lastDestination != null)
            {
                forgetPatrolPointNear(self, lastDestination);
            }
            ensureMinimumPatrolPoints(self);
            moveToNextPatrolPoint(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int handleThink(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }

        if (ai_lib.isInCombat(self))
        {
            messageTo(self, "handleThink", null, 3.0f, false);
            return SCRIPT_CONTINUE;
        }

        int state = getState(self);
        switch (state)
        {
            case STATE_PATROL:
                handlePatrolState(self);
                break;
            case STATE_APPROACH:
                handleApproachState(self);
                break;
            case STATE_QUESTION:
                handleQuestionState(self);
                break;
            case STATE_RESOLVE:
                handleResolveState(self);
                break;
            case STATE_DISENGAGE:
                handleDisengageState(self);
                break;
            default:
                beginPatrol(self);
                break;
        }

        messageTo(self, "handleThink", null, rand(2, 4), false);
        return SCRIPT_CONTINUE;
    }

    private void handlePatrolState(obj_id self) throws InterruptedException
    {
        doAmbientPatrolBehavior(self);

        if (rand(0, 100) <= 25)
        {
            learnPatrolFromEnvironment(self);
        }

        obj_id target = chooseCandidate(self);
        if (isIdValid(target))
        {
            utils.setScriptVar(self, SCRIPTVAR_TARGET, target);
            setState(self, STATE_APPROACH);
            faceTo(self, target);
            chat.chat(self, jedi_hunter.getBarkLine(self, BARK_INTERROGATE, "Citizen, hold position for a compliance scan."));
            learnPatrolPoint(self, getLocation(target));
            return;
        }

        if (rand(0, 100) <= 20)
        {
            moveToNextPatrolPoint(self);
        }
        else if (rand(0, 100) <= 8)
        {
            exploreNewPatrolArea(self);
        }
    }

    private void handleApproachState(obj_id self) throws InterruptedException
    {
        obj_id target = utils.getObjIdScriptVar(self, SCRIPTVAR_TARGET);
        if (!isTargetValid(self, target))
        {
            setState(self, STATE_DISENGAGE);
            return;
        }

        float distance = getDistance(self, target);
        faceTo(self, target);
        learnPatrolPoint(self, getLocation(target));

        if (distance > QUESTION_RANGE)
        {
            follow(self, target, APPROACH_MIN_RANGE, APPROACH_MAX_RANGE);
            return;
        }

        stop(self);
        setState(self, STATE_QUESTION);
        chat.chat(self, jedi_hunter.getBarkLine(self, BARK_INTERROGATE, "Identify yourself and declare your discipline."));
    }

    private void handleQuestionState(obj_id self) throws InterruptedException
    {
        obj_id target = utils.getObjIdScriptVar(self, SCRIPTVAR_TARGET);
        if (!isTargetValid(self, target))
        {
            setState(self, STATE_DISENGAGE);
            return;
        }

        faceTo(self, target);
        learnPatrolPoint(self, getLocation(target));
        setState(self, STATE_RESOLVE);

        boolean jediTarget = jedi_hunter.isJediTarget(target);
        int targetLevel = getLevel(target);

        if (!jediTarget)
        {
            chat.chat(self, jedi_hunter.getBarkLine(self, BARK_DISMISS_NON_JEDI, "No Jedi profile detected. You are dismissed."));
            applyTargetCooldown(self, target);
            setState(self, STATE_DISENGAGE);
            return;
        }

        if (targetLevel > jedi_hunter.getJediFineMaxLevel(self))
        {
            chat.chat(self, jedi_hunter.getBarkLine(self, BARK_ALERT_ATTACK, "High-threat force signature confirmed. Tracking protocol engaged."));
        }
        else
        {
            chat.chat(self, jedi_hunter.getBarkLine(self, BARK_FINE_DEMAND, "Low-intensity force signature noted. Compliance fine may be issued."));
        }
    }

    private void handleResolveState(obj_id self) throws InterruptedException
    {
        obj_id target = utils.getObjIdScriptVar(self, SCRIPTVAR_TARGET);
        if (!isTargetValid(self, target))
        {
            setState(self, STATE_DISENGAGE);
            return;
        }

        learnPatrolPoint(self, getLocation(target));
        int policyResult = jedi_hunter.evaluatePolicy(self, target);
        if (policyResult == jedi_hunter.RESULT_ENGAGE)
        {
            chat.chat(self, jedi_hunter.getBarkLine(self, BARK_ALERT_ATTACK, "Jedi activity logged. Command has been alerted."));
        }
        else if (policyResult == jedi_hunter.RESULT_RELEASED)
        {
            chat.chat(self, jedi_hunter.getBarkLine(self, BARK_DISENGAGE, "You are released from this stop. Keep moving."));
            applyTargetCooldown(self, target);
        }
        else
        {
            applyTargetCooldown(self, target);
        }

        setState(self, STATE_DISENGAGE);
    }

    private void handleDisengageState(obj_id self) throws InterruptedException
    {
        stop(self);
        utils.removeScriptVar(self, SCRIPTVAR_TARGET);
        beginPatrol(self);
    }

    private void beginPatrol(obj_id self) throws InterruptedException
    {
        ensureMinimumPatrolPoints(self);
        if (!utils.hasScriptVar(self, SCRIPTVAR_PATROL_INDEX))
        {
            int maxIndex = Math.max(getPatrolPointCount(self) - 1, 0);
            utils.setScriptVar(self, SCRIPTVAR_PATROL_INDEX, rand(0, maxIndex));
        }

        setState(self, STATE_PATROL);

        location[] patrolPoints = utils.getLocationArrayScriptVar(self, SCRIPTVAR_PATROL_POINTS);
        if (patrolPoints != null && patrolPoints.length > 1)
        {
            ai_lib.setPatrolRandomPath(self, patrolPoints);
        }
        else
        {
            moveToNextPatrolPoint(self);
        }
    }

    private void initializeLearnedPatrol(obj_id self) throws InterruptedException
    {
        if (utils.hasScriptVar(self, SCRIPTVAR_PATROL_POINTS))
        {
            return;
        }

        if (hasObjVar(self, OBJVAR_KNOWN_PATROL_POINTS))
        {
            location[] known = getLocationArrayObjVar(self, OBJVAR_KNOWN_PATROL_POINTS);
            if (known != null && known.length > 0)
            {
                utils.setScriptVar(self, SCRIPTVAR_PATROL_POINTS, known);
                return;
            }
        }

        regeneratePatrol(self);
    }

    private void regeneratePatrol(obj_id self) throws InterruptedException
    {
        location origin = getLocationObjVar(self, OBJVAR_PATROL_ORIGIN);
        if (origin == null)
        {
            origin = getLocation(self);
        }

        Vector points = new Vector();
        for (int i = 0; i < MIN_PATROL_POINTS; i++)
        {
            points.add(utils.getRandomLocationInRing(origin, 10.0f, 70.0f));
        }

        location[] patrolPoints = new location[points.size()];
        points.toArray(patrolPoints);
        syncPatrolPoints(self, patrolPoints);
        utils.setScriptVar(self, SCRIPTVAR_PATROL_INDEX, 0);
    }

    private void moveToNextPatrolPoint(obj_id self) throws InterruptedException
    {
        location[] patrolPoints = utils.getLocationArrayScriptVar(self, SCRIPTVAR_PATROL_POINTS);
        if (patrolPoints == null || patrolPoints.length == 0)
        {
            ensureMinimumPatrolPoints(self);
            patrolPoints = utils.getLocationArrayScriptVar(self, SCRIPTVAR_PATROL_POINTS);
            if (patrolPoints == null || patrolPoints.length == 0)
            {
                return;
            }
        }

        int index = utils.getIntScriptVar(self, SCRIPTVAR_PATROL_INDEX);
        if (index < 0 || index >= patrolPoints.length)
        {
            index = 0;
        }

        location destination = patrolPoints[index];
        utils.setScriptVar(self, SCRIPTVAR_LAST_PATROL_DEST, destination);
        ai_lib.aiPathTo(self, destination);

        index++;
        if (index >= patrolPoints.length)
        {
            index = 0;
        }
        utils.setScriptVar(self, SCRIPTVAR_PATROL_INDEX, index);
    }

    private void doAmbientPatrolBehavior(obj_id self) throws InterruptedException
    {
        int now = getGameTime();
        int nextAmbient = utils.hasScriptVar(self, SCRIPTVAR_NEXT_AMBIENT) ? utils.getIntScriptVar(self, SCRIPTVAR_NEXT_AMBIENT) : 0;
        if (now < nextAmbient)
        {
            return;
        }

        utils.setScriptVar(self, SCRIPTVAR_NEXT_AMBIENT, now + rand(8, 16));

        if (rand(0, 100) <= 40)
        {
            chat.chat(self, "Patrol sweep in progress.");
        }
        else if (rand(0, 100) <= 20)
        {
            stop(self);
        }
    }

    private void learnPatrolFromEnvironment(obj_id self) throws InterruptedException
    {
        location here = getLocation(self);
        if (here == null)
        {
            return;
        }

        obj_id[] players = getAllPlayers(here, jedi_hunter.getScanRadius(self));
        if (players != null)
        {
            for (obj_id player : players)
            {
                if (!isTargetValid(self, player))
                {
                    continue;
                }
                if (rand(0, 100) <= 35)
                {
                    learnPatrolPoint(self, getLocation(player));
                }
            }
        }

        if (rand(0, 100) <= 20)
        {
            location origin = getLocationObjVar(self, OBJVAR_PATROL_ORIGIN);
            if (origin == null)
            {
                origin = here;
            }
            learnPatrolPoint(self, utils.getRandomLocationInRing(origin, 20.0f, 120.0f));
        }
    }

    private void exploreNewPatrolArea(obj_id self) throws InterruptedException
    {
        location origin = getLocationObjVar(self, OBJVAR_PATROL_ORIGIN);
        if (origin == null)
        {
            origin = getLocation(self);
        }
        if (origin == null)
        {
            return;
        }

        location explorationPoint = utils.getRandomLocationInRing(origin, 40.0f, 150.0f);
        learnPatrolPoint(self, explorationPoint);
        ai_lib.aiPathTo(self, explorationPoint);
    }

    private void learnPatrolPoint(obj_id self, location candidatePoint) throws InterruptedException
    {
        if (candidatePoint == null)
        {
            return;
        }

        location[] patrolPoints = utils.getLocationArrayScriptVar(self, SCRIPTVAR_PATROL_POINTS);
        if (patrolPoints == null)
        {
            patrolPoints = new location[0];
        }

        for (location point : patrolPoints)
        {
            if (point == null)
            {
                continue;
            }
            if (locationDistance(point, candidatePoint) < MIN_POINT_SEPARATION)
            {
                return;
            }
        }

        location[] newPatrolPoints;
        if (patrolPoints.length < MAX_PATROL_POINTS)
        {
            newPatrolPoints = new location[patrolPoints.length + 1];
            for (int i = 0; i < patrolPoints.length; i++)
            {
                newPatrolPoints[i] = patrolPoints[i];
            }
            newPatrolPoints[patrolPoints.length] = candidatePoint;
        }
        else
        {
            newPatrolPoints = patrolPoints;
            int replaceIndex = rand(0, patrolPoints.length - 1);
            newPatrolPoints[replaceIndex] = candidatePoint;
        }

        syncPatrolPoints(self, newPatrolPoints);
    }

    private void forgetPatrolPointNear(obj_id self, location failedPoint) throws InterruptedException
    {
        if (failedPoint == null)
        {
            return;
        }

        location[] patrolPoints = utils.getLocationArrayScriptVar(self, SCRIPTVAR_PATROL_POINTS);
        if (patrolPoints == null || patrolPoints.length <= MIN_PATROL_POINTS)
        {
            return;
        }

        int nearestIndex = -1;
        float nearestDistance = 999999.0f;

        for (int i = 0; i < patrolPoints.length; i++)
        {
            if (patrolPoints[i] == null)
            {
                continue;
            }
            float distance = locationDistance(patrolPoints[i], failedPoint);
            if (distance < nearestDistance)
            {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        if (nearestIndex < 0 || nearestDistance > (MIN_POINT_SEPARATION * 2.0f))
        {
            return;
        }

        location[] newPatrolPoints = new location[patrolPoints.length - 1];
        int writeIndex = 0;
        for (int i = 0; i < patrolPoints.length; i++)
        {
            if (i == nearestIndex)
            {
                continue;
            }
            newPatrolPoints[writeIndex] = patrolPoints[i];
            writeIndex++;
        }

        syncPatrolPoints(self, newPatrolPoints);
    }

    private void ensureMinimumPatrolPoints(obj_id self) throws InterruptedException
    {
        location[] patrolPoints = utils.getLocationArrayScriptVar(self, SCRIPTVAR_PATROL_POINTS);
        if (patrolPoints == null || patrolPoints.length < MIN_PATROL_POINTS)
        {
            regeneratePatrol(self);
        }
    }

    private void syncPatrolPoints(obj_id self, location[] patrolPoints) throws InterruptedException
    {
        utils.setScriptVar(self, SCRIPTVAR_PATROL_POINTS, patrolPoints);
        setObjVar(self, OBJVAR_KNOWN_PATROL_POINTS, patrolPoints);
    }

    private float locationDistance(location a, location b)
    {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float)Math.sqrt((dx * dx) + (dz * dz));
    }

    private obj_id chooseCandidate(obj_id self) throws InterruptedException
    {
        location here = getLocation(self);
        if (here == null)
        {
            return null;
        }

        obj_id[] players = getAllPlayers(here, jedi_hunter.getScanRadius(self));
        obj_id best = chooseNearestAllowed(self, players);
        if (isIdValid(best))
        {
            return best;
        }

        obj_id[] nearby = getObjectsInRange(here, jedi_hunter.getScanRadius(self));
        return chooseNearestAllowed(self, nearby);
    }

    private obj_id chooseNearestAllowed(obj_id self, obj_id[] candidates) throws InterruptedException
    {
        if (candidates == null || candidates.length == 0)
        {
            return null;
        }

        obj_id best = null;
        float bestDistance = 999999.0f;

        for (obj_id candidate : candidates)
        {
            if (!isTargetValid(self, candidate) || isOnCooldown(self, candidate))
            {
                continue;
            }

            float distance = getDistance(self, candidate);
            if (distance < bestDistance)
            {
                best = candidate;
                bestDistance = distance;
            }
        }

        return best;
    }

    private boolean isTargetValid(obj_id self, obj_id target) throws InterruptedException
    {
        if (!isIdValid(target) || !exists(target) || target == self)
        {
            return false;
        }
        if (!isPlayer(target) || isDead(target) || !isInWorld(target))
        {
            return false;
        }
        return getDistance(self, target) <= (jedi_hunter.getScanRadius(self) + 30.0f);
    }

    private boolean isOnCooldown(obj_id self, obj_id target) throws InterruptedException
    {
        String key = OBJVAR_COOLDOWN_BASE + "." + target;
        return hasObjVar(self, key) && getIntObjVar(self, key) > getGameTime();
    }

    private void applyTargetCooldown(obj_id self, obj_id target) throws InterruptedException
    {
        setObjVar(self, OBJVAR_COOLDOWN_BASE + "." + target, getGameTime() + COOLDOWN_SECONDS);
    }

    private int getPatrolPointCount(obj_id self) throws InterruptedException
    {
        location[] patrolPoints = utils.getLocationArrayScriptVar(self, SCRIPTVAR_PATROL_POINTS);
        return patrolPoints != null ? patrolPoints.length : 0;
    }

    private void setState(obj_id self, int state) throws InterruptedException
    {
        setObjVar(self, OBJVAR_STATE, state);
        utils.setScriptVar(self, SCRIPTVAR_STATE, state);
    }

    private int getState(obj_id self) throws InterruptedException
    {
        if (utils.hasScriptVar(self, SCRIPTVAR_STATE))
        {
            return utils.getIntScriptVar(self, SCRIPTVAR_STATE);
        }
        if (hasObjVar(self, OBJVAR_STATE))
        {
            return getIntObjVar(self, OBJVAR_STATE);
        }
        return STATE_PATROL;
    }
}
