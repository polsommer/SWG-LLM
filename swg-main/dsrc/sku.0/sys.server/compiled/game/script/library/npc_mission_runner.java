package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

public class npc_mission_runner extends script.base_script
{
    public npc_mission_runner()
    {
    }

    public static final String OBJVAR_MISSION_ROOT = "ai.mission";
    public static final String OBJVAR_ACTIVE_MISSION_ID = OBJVAR_MISSION_ROOT + ".activeMissionId";
    public static final String OBJVAR_OBJECTIVE_TYPE = OBJVAR_MISSION_ROOT + ".objectiveType";
    public static final String OBJVAR_TARGET_LOCATION = OBJVAR_MISSION_ROOT + ".targetLocation";
    public static final String OBJVAR_COMPLETION_STATE = OBJVAR_MISSION_ROOT + ".completionState";
    public static final String OBJVAR_REWARD_ESTIMATE = OBJVAR_MISSION_ROOT + ".rewardEstimate";
    public static final String OBJVAR_PHASE = OBJVAR_MISSION_ROOT + ".phase";
    public static final String OBJVAR_ASSIGNED_AT = OBJVAR_MISSION_ROOT + ".assignedAt";
    public static final String OBJVAR_PHASE_STARTED = OBJVAR_MISSION_ROOT + ".phaseStarted";
    public static final String OBJVAR_FAIL_COUNT = OBJVAR_MISSION_ROOT + ".failCount";
    public static final String OBJVAR_TIMEOUT_COUNT = OBJVAR_MISSION_ROOT + ".timeoutCount";
    public static final String OBJVAR_SUCCESS_COUNT = OBJVAR_MISSION_ROOT + ".successCount";
    public static final String OBJVAR_PATHING_FAILURES = OBJVAR_MISSION_ROOT + ".pathingFailures";

    public static final String PHASE_NONE = "none";
    public static final String PHASE_TRAVEL = "travel";
    public static final String PHASE_OBJECTIVE = "objective";
    public static final String PHASE_RETURN = "return";
    public static final String PHASE_COMPLETE = "complete";
    public static final String PHASE_FAILED = "failed";

    private static final int MISSION_TIMEOUT_SECONDS = 420;
    private static final int MAX_PROGRESSION_STAGE = 20;
    private static final int MAX_STAGE_GROWTH_PER_MISSION = 2;

    public static void initialize(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        if (!hasObjVar(npc, OBJVAR_COMPLETION_STATE))
        {
            setObjVar(npc, OBJVAR_COMPLETION_STATE, PHASE_NONE);
        }
        if (!hasObjVar(npc, OBJVAR_PHASE))
        {
            setObjVar(npc, OBJVAR_PHASE, PHASE_NONE);
        }
    }

    public static dictionary getMissionCommand(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return null;
        }
        maintainMissionAssignment(npc);
        obj_id missionData = getActiveMission(npc);
        if (!isIdValid(missionData) || !exists(missionData))
        {
            return null;
        }
        if (!missions.isEligibleNpcMission(npc, missionData))
        {
            failAndResetMission(npc, missionData, "ineligible");
            return null;
        }

        String phase = determinePhase(missionData);
        setObjVar(npc, OBJVAR_PHASE, phase);
        setObjVar(npc, OBJVAR_COMPLETION_STATE, phase);

        if (phase.equals(PHASE_COMPLETE))
        {
            completeMission(npc, missionData);
            return null;
        }

        if (isMissionTimedOut(npc))
        {
            failAndResetMission(npc, missionData, "timeout");
            return null;
        }

        dictionary command = new dictionary();
        command.put("goal", npc_player_brain.GOAL_TRAVEL);
        command.put("action", npc_player_brain.COMMAND_MOVE);

        if (phase.equals(PHASE_OBJECTIVE))
        {
            String objectiveType = hasObjVar(npc, OBJVAR_OBJECTIVE_TYPE) ? getStringObjVar(npc, OBJVAR_OBJECTIVE_TYPE) : "unknown";
            if (isCombatMissionType(objectiveType))
            {
                command.put("goal", npc_player_brain.GOAL_COMBAT);
                command.put("subgoal", "mission_objective_combat");
                command.put("action", npc_player_brain.COMMAND_ATTACK);
            }
            else
            {
                command.put("goal", npc_player_brain.GOAL_SOCIAL);
                command.put("subgoal", "mission_objective_interact");
                command.put("action", npc_player_brain.COMMAND_INTERACT);
            }
            command.put("dest", getObjectiveLocation(npc, missionData));
        }
        else if (phase.equals(PHASE_RETURN))
        {
            command.put("goal", npc_player_brain.GOAL_TRAVEL);
            command.put("subgoal", "mission_turn_in");
            command.put("action", npc_player_brain.COMMAND_INTERACT);
            command.put("dest", getReturnLocation(npc, missionData));
        }
        else
        {
            command.put("goal", npc_player_brain.GOAL_TRAVEL);
            command.put("subgoal", "mission_travel");
            command.put("action", npc_player_brain.COMMAND_MOVE);
            command.put("dest", getObjectiveLocation(npc, missionData));
        }

        command.put("missionId", missionData);
        command.put("missionPhase", phase);
        return command;
    }

    public static void notifyPathingFailure(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        int failures = hasObjVar(npc, OBJVAR_PATHING_FAILURES) ? getIntObjVar(npc, OBJVAR_PATHING_FAILURES) : 0;
        failures++;
        setObjVar(npc, OBJVAR_PATHING_FAILURES, failures);

        obj_id missionData = getActiveMission(npc);
        if (!isIdValid(missionData) || !exists(missionData))
        {
            return;
        }
        if (failures >= 3)
        {
            failAndResetMission(npc, missionData, "pathing_failure");
            return;
        }

        location retarget = getObjectiveLocation(npc, missionData);
        retarget.x += rand(-6.0f, 6.0f);
        retarget.z += rand(-6.0f, 6.0f);
        setObjVar(npc, OBJVAR_TARGET_LOCATION, retarget);
        LOG("npc_mission", "mission_retarget npc=" + npc + " mission=" + missionData + " attempt=" + failures);
    }

    private static void maintainMissionAssignment(obj_id npc) throws InterruptedException
    {
        obj_id missionData = getActiveMission(npc);
        if (isIdValid(missionData) && exists(missionData) && missions.isEligibleNpcMission(npc, missionData))
        {
            return;
        }
        missionData = missions.selectEligibleNpcMission(npc);
        if (!isIdValid(missionData) || !exists(missionData))
        {
            return;
        }
        String missionType = getMissionType(missionData);
        int reward = Math.max(0, getMissionReward(missionData));
        location target = getObjectiveLocation(npc, missionData);

        setObjVar(npc, OBJVAR_ACTIVE_MISSION_ID, missionData);
        setObjVar(npc, OBJVAR_OBJECTIVE_TYPE, missionType);
        setObjVar(npc, OBJVAR_TARGET_LOCATION, target);
        setObjVar(npc, OBJVAR_REWARD_ESTIMATE, reward);
        setObjVar(npc, OBJVAR_COMPLETION_STATE, PHASE_TRAVEL);
        setObjVar(npc, OBJVAR_PHASE, PHASE_TRAVEL);
        setObjVar(npc, OBJVAR_ASSIGNED_AT, getGameTime());
        setObjVar(npc, OBJVAR_PHASE_STARTED, getGameTime());
        setObjVar(npc, OBJVAR_PATHING_FAILURES, 0);

        LOG("npc_mission", "mission_assign npc=" + npc + " mission=" + missionData + " type=" + missionType + " reward=" + reward);
    }

    private static obj_id getActiveMission(obj_id npc) throws InterruptedException
    {
        if (!hasObjVar(npc, OBJVAR_ACTIVE_MISSION_ID))
        {
            return obj_id.NULL_ID;
        }
        return getObjIdObjVar(npc, OBJVAR_ACTIVE_MISSION_ID);
    }

    private static String determinePhase(obj_id missionData) throws InterruptedException
    {
        int missionState = hasObjVar(missionData, "intState") ? getIntObjVar(missionData, "intState") : 0;
        if (missionState == missions.STATE_MISSION_COMPLETE)
        {
            return PHASE_COMPLETE;
        }
        if (missionState == missions.STATE_DYNAMIC_PICKUP || missionState == missions.STATE_DYNAMIC_START || missionState == missions.STATE_DELIVER_PICKUP)
        {
            return PHASE_TRAVEL;
        }
        if (missionState == missions.STATE_DYNAMIC_DROPOFF || missionState == missions.STATE_DELIVER_DROPOFF)
        {
            return PHASE_RETURN;
        }
        return PHASE_OBJECTIVE;
    }

    private static location getObjectiveLocation(obj_id npc, obj_id missionData) throws InterruptedException
    {
        location objective = getMissionStartLocation(missionData);
        if (objective == null)
        {
            objective = getLocation(npc);
        }
        return objective;
    }

    private static location getReturnLocation(obj_id npc, obj_id missionData) throws InterruptedException
    {
        location dropoff = getMissionEndLocation(missionData);
        if (dropoff == null)
        {
            dropoff = getLocation(npc);
        }
        return dropoff;
    }

    private static boolean isCombatMissionType(String missionType)
    {
        if (missionType == null)
        {
            return false;
        }
        return missionType.equals("destroy") || missionType.equals("bounty") || missionType.equals("assassin") || missionType.equals("hunting") || missionType.equals("recon");
    }

    private static boolean isMissionTimedOut(obj_id npc) throws InterruptedException
    {
        if (!hasObjVar(npc, OBJVAR_ASSIGNED_AT))
        {
            return false;
        }
        int assignedAt = getIntObjVar(npc, OBJVAR_ASSIGNED_AT);
        return getGameTime() - assignedAt > MISSION_TIMEOUT_SECONDS;
    }

    private static void completeMission(obj_id npc, obj_id missionData) throws InterruptedException
    {
        int reward = hasObjVar(npc, OBJVAR_REWARD_ESTIMATE) ? getIntObjVar(npc, OBJVAR_REWARD_ESTIMATE) : Math.max(0, getMissionReward(missionData));
        int credits = hasObjVar(npc, npc_player_brain.OBJVAR_ECON_CREDITS) ? getIntObjVar(npc, npc_player_brain.OBJVAR_ECON_CREDITS) : 0;
        setObjVar(npc, npc_player_brain.OBJVAR_ECON_CREDITS, credits + reward);

        int stage = hasObjVar(npc, npc_player_brain.OBJVAR_PROGRESSION_STAGE) ? getIntObjVar(npc, npc_player_brain.OBJVAR_PROGRESSION_STAGE) : 1;
        int growth = Math.min(MAX_STAGE_GROWTH_PER_MISSION, Math.max(1, reward / 2000));
        setObjVar(npc, npc_player_brain.OBJVAR_PROGRESSION_STAGE, Math.min(MAX_PROGRESSION_STAGE, stage + growth));

        int success = hasObjVar(npc, OBJVAR_SUCCESS_COUNT) ? getIntObjVar(npc, OBJVAR_SUCCESS_COUNT) : 0;
        setObjVar(npc, OBJVAR_SUCCESS_COUNT, success + 1);
        LOG("npc_mission", "mission_success npc=" + npc + " mission=" + missionData + " reward=" + reward + " stageGrowth=" + growth);
        logMissionTelemetry(npc, "success");
        clearMissionAssignment(npc);
    }

    private static void failAndResetMission(obj_id npc, obj_id missionData, String reason) throws InterruptedException
    {
        int fails = hasObjVar(npc, OBJVAR_FAIL_COUNT) ? getIntObjVar(npc, OBJVAR_FAIL_COUNT) : 0;
        setObjVar(npc, OBJVAR_FAIL_COUNT, fails + 1);
        if (reason.equals("timeout"))
        {
            int timeouts = hasObjVar(npc, OBJVAR_TIMEOUT_COUNT) ? getIntObjVar(npc, OBJVAR_TIMEOUT_COUNT) : 0;
            setObjVar(npc, OBJVAR_TIMEOUT_COUNT, timeouts + 1);
        }
        setObjVar(npc, OBJVAR_COMPLETION_STATE, PHASE_FAILED);
        LOG("npc_mission", "mission_fail npc=" + npc + " mission=" + missionData + " reason=" + reason);
        logMissionTelemetry(npc, "failure");
        clearMissionAssignment(npc);
    }

    private static void logMissionTelemetry(obj_id npc, String eventType) throws InterruptedException
    {
        int success = hasObjVar(npc, OBJVAR_SUCCESS_COUNT) ? getIntObjVar(npc, OBJVAR_SUCCESS_COUNT) : 0;
        int failure = hasObjVar(npc, OBJVAR_FAIL_COUNT) ? getIntObjVar(npc, OBJVAR_FAIL_COUNT) : 0;
        int total = success + failure;
        int rate = 0;
        if (total > 0)
        {
            rate = (success * 100) / total;
        }
        LOG("npc_mission", "mission_telemetry npc=" + npc + " event=" + eventType + " success=" + success + " failure=" + failure + " successRate=" + rate);
    }

    private static void clearMissionAssignment(obj_id npc) throws InterruptedException
    {
        removeObjVar(npc, OBJVAR_ACTIVE_MISSION_ID);
        removeObjVar(npc, OBJVAR_OBJECTIVE_TYPE);
        removeObjVar(npc, OBJVAR_TARGET_LOCATION);
        removeObjVar(npc, OBJVAR_REWARD_ESTIMATE);
        removeObjVar(npc, OBJVAR_PHASE);
        removeObjVar(npc, OBJVAR_ASSIGNED_AT);
        removeObjVar(npc, OBJVAR_PHASE_STARTED);
        removeObjVar(npc, OBJVAR_PATHING_FAILURES);
    }
}
