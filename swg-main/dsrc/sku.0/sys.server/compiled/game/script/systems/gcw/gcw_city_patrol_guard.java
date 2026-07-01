package script.systems.gcw;

import script.*;
import script.library.*;

public class gcw_city_patrol_guard extends script.base_script
{
    public gcw_city_patrol_guard()
    {
    }

    public static final int HOLD_TIME_SECONDS = 20;
    public static final float ARRIVAL_DISTANCE = 5.0f;

    public int OnAttach(obj_id self) throws InterruptedException
    {
        messageTo(self, "beginPatrolCycle", null, 2.0f, false);
        messageTo(self, "validateController", null, 30.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "beginPatrolCycle", null, 2.0f, false);
        messageTo(self, "validateController", null, 30.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int beginPatrolCycle(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }

        moveToHoldPoint(self);
        return SCRIPT_CONTINUE;
    }

    public int validateController(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }

        if (!hasObjVar(self, "gcw.cityPatrol.manager"))
        {
            destroyObject(self);
            return SCRIPT_CONTINUE;
        }

        obj_id manager = getObjIdObjVar(self, "gcw.cityPatrol.manager");
        if (!isIdValid(manager) || !exists(manager))
        {
            destroyObject(self);
            return SCRIPT_CONTINUE;
        }

        messageTo(self, "validateController", null, 30.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathComplete(obj_id self) throws InterruptedException
    {
        String state = hasObjVar(self, "gcw.cityPatrol.state") ? getStringObjVar(self, "gcw.cityPatrol.state") : "";
        if (state.equals("to_hold"))
        {
            ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_SENTINEL);
            messageTo(self, "resumeRoute", null, HOLD_TIME_SECONDS, false);
            return SCRIPT_CONTINUE;
        }

        if (state.equals("to_waypoint"))
        {
            messageTo(self, "moveToNextWaypoint", null, 0.0f, false);
            return SCRIPT_CONTINUE;
        }

        return SCRIPT_CONTINUE;
    }

    public int OnMovePathNotFound(obj_id self) throws InterruptedException
    {
        messageTo(self, "beginPatrolCycle", null, 6.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathBlocked(obj_id self) throws InterruptedException
    {
        messageTo(self, "beginPatrolCycle", null, 6.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int resumeRoute(obj_id self, dictionary params) throws InterruptedException
    {
        setObjVar(self, "gcw.cityPatrol.routeIndex", 0);
        messageTo(self, "moveToNextWaypoint", null, 0.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int moveToNextWaypoint(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }

        String[] route = getRoute(self);
        if (route == null || route.length <= 0)
        {
            moveToHoldPoint(self);
            return SCRIPT_CONTINUE;
        }

        int index = hasObjVar(self, "gcw.cityPatrol.routeIndex") ? getIntObjVar(self, "gcw.cityPatrol.routeIndex") : 0;
        if (index >= route.length)
        {
            moveToHoldPoint(self);
            return SCRIPT_CONTINUE;
        }

        location target = parseLocation(self, route[index]);
        if (target == null)
        {
            setObjVar(self, "gcw.cityPatrol.routeIndex", index + 1);
            messageTo(self, "moveToNextWaypoint", null, 0.0f, false);
            return SCRIPT_CONTINUE;
        }

        setObjVar(self, "gcw.cityPatrol.state", "to_waypoint");
        setObjVar(self, "gcw.cityPatrol.routeIndex", index + 1);
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_LOITER);
        pathTo(self, target);
        return SCRIPT_CONTINUE;
    }

    public void moveToHoldPoint(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, "gcw.cityPatrol.holdX") || !hasObjVar(self, "gcw.cityPatrol.holdY") || !hasObjVar(self, "gcw.cityPatrol.holdZ"))
        {
            return;
        }

        float x = getFloatObjVar(self, "gcw.cityPatrol.holdX");
        float y = getFloatObjVar(self, "gcw.cityPatrol.holdY");
        float z = getFloatObjVar(self, "gcw.cityPatrol.holdZ");
        location holdLoc = new location(x, y, z, getLocation(self).area, null);

        if (utils.getDistance2D(getLocation(self), holdLoc) <= ARRIVAL_DISTANCE)
        {
            setObjVar(self, "gcw.cityPatrol.state", "to_hold");
            ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_SENTINEL);
            messageTo(self, "resumeRoute", null, HOLD_TIME_SECONDS, false);
            return;
        }

        setObjVar(self, "gcw.cityPatrol.state", "to_hold");
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_LOITER);
        pathTo(self, holdLoc);
    }

    public String[] getRoute(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, "gcw.cityPatrol.route"))
        {
            return null;
        }

        String routeString = getStringObjVar(self, "gcw.cityPatrol.route");
        if (routeString == null || routeString.length() <= 0)
        {
            return null;
        }

        return split(routeString, ';');
    }

    public location parseLocation(obj_id self, String packedLoc) throws InterruptedException
    {
        if (packedLoc == null || packedLoc.length() <= 0)
        {
            return null;
        }

        String[] tokens = split(packedLoc, ',');
        if (tokens == null || tokens.length < 3)
        {
            return null;
        }

        float x = utils.stringToFloat(tokens[0]);
        float y = utils.stringToFloat(tokens[1]);
        float z = utils.stringToFloat(tokens[2]);
        return new location(x, y, z, getLocation(self).area, null);
    }
}
