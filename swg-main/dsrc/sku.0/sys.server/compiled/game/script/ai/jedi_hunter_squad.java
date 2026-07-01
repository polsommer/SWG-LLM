package script.ai;

import script.dictionary;
import script.location;
import script.obj_id;
import script.library.ai_lib;
import script.library.chat;
import script.library.jedi_hunter;
import script.library.locations;
import script.library.utils;

import java.util.Vector;

public class jedi_hunter_squad extends script.base_script
{
    public jedi_hunter_squad()
    {
    }

    public static final String VAR_PREFIX = "jediHunter.squad";
    public static final String SQUAD_DATA_PREFIX = "jediHunter.squadData.";
    public static final String MSG_THINK = "jediHunterSquadThink";
    public static final String OBJVAR_SPAWN_MASTER = "jediHunter.master";
    public static final String OBJVAR_SQUAD_ID = "jediHunter.squadId";
    public static final String OBJVAR_ORPHAN_AT = VAR_PREFIX + ".orphanAt";
    public static final String OBJVAR_PATH_FAILS = VAR_PREFIX + ".pathFails";
    public static final String OBJVAR_SPLIT_AT = VAR_PREFIX + ".splitAt";
    public static final String OBJVAR_PATROL_ANCHOR = VAR_PREFIX + ".anchor";
    public static final String OBJVAR_LAST_PATROL_POINT = VAR_PREFIX + ".lastPatrolPoint";

    public static final String SPAWN_POINT_TABLE = "datatables/spawning/jedi_hunters/spawn_points.tab";

    public static final float THINK_DELAY_MIN = 2.0f;
    public static final float THINK_DELAY_MAX = 4.0f;
    public static final float LEADER_SCAN_RANGE = 110.0f;
    public static final float SQUAD_MAX_SPREAD = 85.0f;
    public static final float FOLLOW_MIN = 5.0f;
    public static final float FOLLOW_MAX = 14.0f;
    public static final int ORPHAN_TIMEOUT_SEC = 90;
    public static final int SPLIT_TIMEOUT_SEC = 35;
    public static final int PATROL_REFRESH_MIN_SEC = 45;
    public static final int PATROL_REFRESH_MAX_SEC = 80;
    public static final int MIN_PATROL_POINTS = 5;
    public static final int MAX_PATROL_POINTS = 10;
    public static final float GENERATED_PATROL_INNER_RADIUS = 35.0f;
    public static final float GENERATED_PATROL_OUTER_RADIUS = 170.0f;
    public static final float CONFIG_POINT_SEARCH_RADIUS = 550.0f;
    public static final float POINT_MIN_SEPARATION = 28.0f;

    public static final String ALERT_PATROL = "patrol";
    public static final String ALERT_ENGAGED = "engaged";
    public static final String ALERT_SPLIT = "split";

    public static final String[] TAUNT_KEYS =
    {
        "alert_attack",
        "alert_attack_2",
        "alert_attack_3"
    };

    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_PATROL_ANCHOR))
        {
            setObjVar(self, OBJVAR_PATROL_ANCHOR, getLocation(self));
        }
        registerMember(self);
        queueThink(self, 1.0f);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        registerMember(self);
        queueThink(self, 1.0f);
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathNotFound(obj_id self) throws InterruptedException
    {
        int pathFails = hasObjVar(self, OBJVAR_PATH_FAILS) ? getIntObjVar(self, OBJVAR_PATH_FAILS) : 0;
        pathFails++;
        setObjVar(self, OBJVAR_PATH_FAILS, pathFails);
        if (pathFails >= 2)
        {
            markCurrentWaypointBad(self);
            location patrolPoint = getNextPatrolPoint(self);
            if (patrolPoint != null)
            {
                ai_lib.aiPathTo(self, patrolPoint);
            }
        }
        return SCRIPT_CONTINUE;
    }

    public int OnMovePathComplete(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, OBJVAR_PATH_FAILS))
        {
            removeObjVar(self, OBJVAR_PATH_FAILS);
        }
        if (hasObjVar(self, OBJVAR_SPLIT_AT))
        {
            removeObjVar(self, OBJVAR_SPLIT_AT);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnIncapacitated(obj_id self, obj_id killer) throws InterruptedException
    {
        unregisterMember(self);
        return SCRIPT_CONTINUE;
    }

    public int OnDestroy(obj_id self) throws InterruptedException
    {
        unregisterMember(self);
        return SCRIPT_CONTINUE;
    }

    public int jediHunterSquadThink(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }

        String squadId = getSquadId(self);
        if (squadId == null)
        {
            return SCRIPT_CONTINUE;
        }

        registerMember(self);
        obj_id leader = getOrElectLeader(self);
        if (!isIdValid(leader))
        {
            handleOrphan(self);
            queueThink(self, rand(THINK_DELAY_MIN, THINK_DELAY_MAX));
            return SCRIPT_CONTINUE;
        }

        clearOrphanState(self);

        if (self == leader)
        {
            runLeaderLogic(self);
        }
        else
        {
            runFollowerLogic(self, leader);
        }

        maybeEmitTaunt(self);
        queueThink(self, rand(THINK_DELAY_MIN, THINK_DELAY_MAX));
        return SCRIPT_CONTINUE;
    }

    private void runLeaderLogic(obj_id self) throws InterruptedException
    {
        obj_id target = chooseTarget(self);
        if (isIdValid(target))
        {
            setSharedTarget(self, target);
            setSharedAlertState(self, ALERT_ENGAGED);
            startCombat(self, target);
            faceTo(self, target);
            return;
        }

        setSharedTarget(self, obj_id.NULL_ID);

        if (isSplitTimeoutExpired(self))
        {
            setSharedAlertState(self, ALERT_PATROL);
        }

        if (!ai_lib.isInCombat(self))
        {
            location patrolPoint = getNextPatrolPoint(self);
            if (patrolPoint != null)
            {
                ai_lib.aiPathTo(self, patrolPoint);
            }
        }
    }

    private void runFollowerLogic(obj_id self, obj_id leader) throws InterruptedException
    {
        obj_id sharedTarget = getSharedTarget(self);
        if (isValidTarget(self, sharedTarget))
        {
            setSharedAlertState(self, ALERT_ENGAGED);
            ai_lib.aiFollow(self, sharedTarget, FOLLOW_MIN, FOLLOW_MAX);
            if (!ai_lib.isInCombat(self) || getCombatTarget(self) != sharedTarget)
            {
                ai_combat_assist.startAssistedCombat(self, sharedTarget);
            }
            return;
        }

        float distance = getDistance(self, leader);
        if (distance > SQUAD_MAX_SPREAD)
        {
            setObjVar(self, OBJVAR_SPLIT_AT, getGameTime());
            setSharedAlertState(self, ALERT_SPLIT);
            ai_lib.aiPathTo(self, getLocation(leader));
            return;
        }

        if (!ai_lib.isInCombat(self))
        {
            ai_lib.aiFollow(self, leader, FOLLOW_MIN, FOLLOW_MAX);
        }

        if (isSplitTimeoutExpired(self))
        {
            setSharedAlertState(self, ALERT_PATROL);
        }
    }

    private void maybeEmitTaunt(obj_id self) throws InterruptedException
    {
        obj_id leader = getSharedLeader(self);
        if (!isIdValid(leader) || self != leader)
        {
            return;
        }

        int now = getGameTime();
        String tauntVar = getSquadDataPrefix(self) + "nextTaunt";
        int nextTaunt = hasObjVar(self, tauntVar) ? getIntObjVar(self, tauntVar) : 0;
        if (now < nextTaunt)
        {
            return;
        }

        int tauntCooldown = jedi_hunter.getTauntCooldown(self);
        setObjVar(self, tauntVar, now + rand(tauntCooldown, tauntCooldown + 10));

        if (rand(0, 100) > 35)
        {
            return;
        }

        String key = TAUNT_KEYS[rand(0, TAUNT_KEYS.length - 1)];
        chat.chat(self, jedi_hunter.getBarkLine(self, key, "Force signature locked. Engage and contain."));
    }

    private void registerMember(obj_id self) throws InterruptedException
    {
        String squadId = getSquadId(self);
        obj_id master = getMasterObject(self);
        if (squadId == null || !isIdValid(master))
        {
            return;
        }

        String membersVar = getSquadDataPrefix(self) + "members";
        obj_id[] members = hasObjVar(master, membersVar) ? getObjIdArrayObjVar(master, membersVar) : null;
        Vector memberList = new Vector();
        if (members != null)
        {
            for (obj_id member : members)
            {
                if (!isIdValid(member) || !exists(member) || isDead(member))
                {
                    continue;
                }
                if (!memberList.contains(member))
                {
                    memberList.add(member);
                }
            }
        }

        if (!memberList.contains(self))
        {
            memberList.add(self);
        }

        obj_id[] newMembers = new obj_id[memberList.size()];
        memberList.toArray(newMembers);
        setObjVar(master, membersVar, newMembers);
        setObjVar(master, getSquadDataPrefix(self) + "memberCount", newMembers.length);

        obj_id leader = hasObjVar(master, getSquadDataPrefix(self) + "leader") ? getObjIdObjVar(master, getSquadDataPrefix(self) + "leader") : obj_id.NULL_ID;
        if (!isIdValid(leader) || !exists(leader) || isDead(leader))
        {
            setObjVar(master, getSquadDataPrefix(self) + "leader", self);
        }
    }

    private void unregisterMember(obj_id self) throws InterruptedException
    {
        obj_id master = getMasterObject(self);
        String squadId = getSquadId(self);
        if (!isIdValid(master) || squadId == null)
        {
            return;
        }

        String membersVar = getSquadDataPrefix(self) + "members";
        obj_id[] members = hasObjVar(master, membersVar) ? getObjIdArrayObjVar(master, membersVar) : null;
        if (members == null || members.length == 0)
        {
            return;
        }

        Vector memberList = new Vector();
        for (obj_id member : members)
        {
            if (!isIdValid(member) || !exists(member) || isDead(member) || member == self)
            {
                continue;
            }
            memberList.add(member);
        }

        if (memberList.size() == 0)
        {
            removeObjVar(master, getSquadDataPrefix(self) + "members");
            removeObjVar(master, getSquadDataPrefix(self) + "leader");
            removeObjVar(master, getSquadDataPrefix(self) + "target");
            removeObjVar(master, getSquadDataPrefix(self) + "alertState");
            removeObjVar(master, getSquadDataPrefix(self) + "memberCount");
            return;
        }

        obj_id[] newMembers = new obj_id[memberList.size()];
        memberList.toArray(newMembers);
        setObjVar(master, membersVar, newMembers);
        setObjVar(master, getSquadDataPrefix(self) + "memberCount", newMembers.length);

        obj_id leader = getSharedLeader(self);
        if (!isIdValid(leader) || leader == self)
        {
            setObjVar(master, getSquadDataPrefix(self) + "leader", newMembers[0]);
        }
    }

    private obj_id getOrElectLeader(obj_id self) throws InterruptedException
    {
        obj_id master = getMasterObject(self);
        if (!isIdValid(master))
        {
            return obj_id.NULL_ID;
        }

        obj_id leader = getSharedLeader(self);
        if (isIdValid(leader) && exists(leader) && !isDead(leader))
        {
            return leader;
        }

        String membersVar = getSquadDataPrefix(self) + "members";
        if (!hasObjVar(master, membersVar))
        {
            return obj_id.NULL_ID;
        }

        obj_id[] members = getObjIdArrayObjVar(master, membersVar);
        if (members == null || members.length == 0)
        {
            return obj_id.NULL_ID;
        }

        obj_id replacement = obj_id.NULL_ID;
        for (obj_id member : members)
        {
            if (!isIdValid(member) || !exists(member) || isDead(member))
            {
                continue;
            }
            replacement = member;
            break;
        }

        if (isIdValid(replacement))
        {
            setObjVar(master, getSquadDataPrefix(self) + "leader", replacement);
        }

        return replacement;
    }

    private obj_id chooseTarget(obj_id self) throws InterruptedException
    {
        location here = getLocation(self);
        if (here == null)
        {
            return obj_id.NULL_ID;
        }

        obj_id[] players = getAllPlayers(here, LEADER_SCAN_RANGE);
        if (players == null || players.length == 0)
        {
            return obj_id.NULL_ID;
        }

        obj_id best = obj_id.NULL_ID;
        float bestDistance = 99999.0f;

        for (obj_id player : players)
        {
            if (!isValidTarget(self, player))
            {
                continue;
            }
            if (!isJedi(player) && !ai_lib.checkForJedi(player) && rand(0, 100) > 15)
            {
                continue;
            }
            float distance = getDistance(self, player);
            if (distance < bestDistance)
            {
                best = player;
                bestDistance = distance;
            }
        }

        return best;
    }

    private boolean isValidTarget(obj_id self, obj_id target) throws InterruptedException
    {
        if (!isIdValid(target) || !exists(target) || target == self)
        {
            return false;
        }
        if (!isPlayer(target) || isDead(target) || isIncapacitated(target))
        {
            return false;
        }
        return true;
    }

    private void handleOrphan(obj_id self) throws InterruptedException
    {
        int now = getGameTime();
        int orphanedAt = hasObjVar(self, OBJVAR_ORPHAN_AT) ? getIntObjVar(self, OBJVAR_ORPHAN_AT) : 0;
        if (orphanedAt <= 0)
        {
            setObjVar(self, OBJVAR_ORPHAN_AT, now);
            return;
        }

        if ((now - orphanedAt) >= ORPHAN_TIMEOUT_SEC)
        {
            destroyObject(self);
            return;
        }

        obj_id master = getMasterObject(self);
        if (isIdValid(master))
        {
            registerMember(self);
        }
    }

    private void clearOrphanState(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, OBJVAR_ORPHAN_AT))
        {
            removeObjVar(self, OBJVAR_ORPHAN_AT);
        }
    }

    private boolean isSplitTimeoutExpired(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_SPLIT_AT))
        {
            return false;
        }
        int splitAt = getIntObjVar(self, OBJVAR_SPLIT_AT);
        if ((getGameTime() - splitAt) < SPLIT_TIMEOUT_SEC)
        {
            return false;
        }
        removeObjVar(self, OBJVAR_SPLIT_AT);
        return true;
    }

    private void moveToSquadAnchor(obj_id self) throws InterruptedException
    {
        location anchor = hasObjVar(self, OBJVAR_PATROL_ANCHOR) ? getLocationObjVar(self, OBJVAR_PATROL_ANCHOR) : null;
        if (anchor == null)
        {
            anchor = getLocation(self);
        }
        if (anchor != null)
        {
            ai_lib.aiPathTo(self, anchor);
        }
    }

    private location getNextPatrolPoint(obj_id self) throws InterruptedException
    {
        obj_id master = getMasterObject(self);
        if (!isIdValid(master))
        {
            return null;
        }

        ensurePatrolWaypoints(self, master);

        String dataPrefix = getSquadDataPrefix(self);
        String pointsVar = dataPrefix + "patrolPoints";
        if (!hasObjVar(master, pointsVar))
        {
            return null;
        }

        location[] patrolPoints = getLocationArrayObjVar(master, pointsVar);
        if (patrolPoints == null || patrolPoints.length == 0)
        {
            return null;
        }

        String indexVar = dataPrefix + "patrolIndex";
        int index = hasObjVar(master, indexVar) ? getIntObjVar(master, indexVar) : rand(0, patrolPoints.length - 1);
        if (index < 0 || index >= patrolPoints.length)
        {
            index = rand(0, patrolPoints.length - 1);
        }

        location destination = patrolPoints[index];
        int nextIndex = index + 1;
        if (nextIndex >= patrolPoints.length)
        {
            nextIndex = 0;
        }
        setObjVar(master, indexVar, nextIndex);
        setObjVar(self, OBJVAR_LAST_PATROL_POINT, destination);
        return destination;
    }

    private void ensurePatrolWaypoints(obj_id self, obj_id master) throws InterruptedException
    {
        location anchor = hasObjVar(self, OBJVAR_PATROL_ANCHOR) ? getLocationObjVar(self, OBJVAR_PATROL_ANCHOR) : null;
        if (anchor == null)
        {
            anchor = getLocation(self);
        }
        if (anchor == null)
        {
            return;
        }

        String dataPrefix = getSquadDataPrefix(self);
        String pointsVar = dataPrefix + "patrolPoints";
        location[] points = hasObjVar(master, pointsVar) ? getLocationArrayObjVar(master, pointsVar) : null;
        if (points == null)
        {
            points = new location[0];
        }

        int now = getGameTime();
        String refreshVar = dataPrefix + "patrolRefreshAt";
        int refreshAt = hasObjVar(master, refreshVar) ? getIntObjVar(master, refreshVar) : 0;
        boolean shouldRefresh = points.length < MIN_PATROL_POINTS || now >= refreshAt;
        if (!shouldRefresh)
        {
            return;
        }

        Vector waypointList = new Vector();
        for (location point : points)
        {
            addPatrolPointIfUnique(waypointList, point);
        }

        location[] configured = getConfiguredSpawnPointsNearAnchor(anchor);
        if (configured != null)
        {
            for (location point : configured)
            {
                addPatrolPointIfUnique(waypointList, point);
                if (waypointList.size() >= MAX_PATROL_POINTS)
                {
                    break;
                }
            }
        }

        int generationAttempts = 0;
        while (waypointList.size() < MIN_PATROL_POINTS && generationAttempts < 20)
        {
            location generated = locations.getGoodLocationAroundLocation(anchor, 4.0f, 4.0f, GENERATED_PATROL_INNER_RADIUS, GENERATED_PATROL_OUTER_RADIUS, false, true);
            if (!isValidLocation(generated))
            {
                generated = utils.getRandomLocationInRing(anchor, GENERATED_PATROL_INNER_RADIUS, GENERATED_PATROL_OUTER_RADIUS);
            }
            addPatrolPointIfUnique(waypointList, generated);
            generationAttempts++;
        }

        while (waypointList.size() < MAX_PATROL_POINTS && rand(0, 100) <= 30)
        {
            location generated = locations.getGoodLocationAroundLocation(anchor, 6.0f, 6.0f, GENERATED_PATROL_INNER_RADIUS, GENERATED_PATROL_OUTER_RADIUS, false, true);
            addPatrolPointIfUnique(waypointList, generated);
        }

        if (waypointList.size() == 0)
        {
            return;
        }

        location[] newPoints = new location[waypointList.size()];
        waypointList.toArray(newPoints);
        setObjVar(master, pointsVar, newPoints);

        String indexVar = dataPrefix + "patrolIndex";
        if (!hasObjVar(master, indexVar) || getIntObjVar(master, indexVar) >= newPoints.length)
        {
            setObjVar(master, indexVar, rand(0, newPoints.length - 1));
        }

        setObjVar(master, refreshVar, now + rand(PATROL_REFRESH_MIN_SEC, PATROL_REFRESH_MAX_SEC));
    }

    private location[] getConfiguredSpawnPointsNearAnchor(location anchor) throws InterruptedException
    {
        if (anchor == null || anchor.area == null || !dataTableOpen(SPAWN_POINT_TABLE))
        {
            return null;
        }

        int rows = dataTableGetNumRows(SPAWN_POINT_TABLE);
        if (rows < 1)
        {
            return null;
        }

        Vector points = new Vector();
        for (int i = 0; i < rows; i++)
        {
            String planet = dataTableGetString(SPAWN_POINT_TABLE, i, "planet");
            if (!anchor.area.equals(planet))
            {
                continue;
            }

            float x = dataTableGetFloat(SPAWN_POINT_TABLE, i, "x");
            float z = dataTableGetFloat(SPAWN_POINT_TABLE, i, "z");
            float y = dataTableGetFloat(SPAWN_POINT_TABLE, i, "y");
            location point = new location(x, z, y, planet, null);
            if (!isValidLocation(point))
            {
                continue;
            }
            if (getDistance(anchor, point) > CONFIG_POINT_SEARCH_RADIUS)
            {
                continue;
            }

            addPatrolPointIfUnique(points, point);
            if (points.size() >= MAX_PATROL_POINTS)
            {
                break;
            }
        }

        location[] results = new location[points.size()];
        points.toArray(results);
        return results;
    }

    private void addPatrolPointIfUnique(Vector points, location candidate)
    {
        if (!isValidLocation(candidate) || points == null)
        {
            return;
        }

        for (int i = 0; i < points.size(); i++)
        {
            location existing = (location)points.get(i);
            if (existing == null)
            {
                continue;
            }
            if (locationDistance(existing, candidate) < POINT_MIN_SEPARATION)
            {
                return;
            }
        }

        points.add(candidate);
    }

    private void markCurrentWaypointBad(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_LAST_PATROL_POINT))
        {
            return;
        }

        location failedPoint = getLocationObjVar(self, OBJVAR_LAST_PATROL_POINT);
        obj_id master = getMasterObject(self);
        if (!isIdValid(master) || failedPoint == null)
        {
            return;
        }

        String dataPrefix = getSquadDataPrefix(self);
        String pointsVar = dataPrefix + "patrolPoints";
        if (!hasObjVar(master, pointsVar))
        {
            return;
        }

        location[] patrolPoints = getLocationArrayObjVar(master, pointsVar);
        if (patrolPoints == null || patrolPoints.length <= 1)
        {
            return;
        }

        int nearestIndex = -1;
        float nearestDistance = 999999.0f;
        for (int i = 0; i < patrolPoints.length; i++)
        {
            location point = patrolPoints[i];
            if (point == null)
            {
                continue;
            }

            float distance = locationDistance(point, failedPoint);
            if (distance < nearestDistance)
            {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        if (nearestIndex < 0)
        {
            return;
        }

        location[] updated = new location[patrolPoints.length - 1];
        int writeIndex = 0;
        for (int i = 0; i < patrolPoints.length; i++)
        {
            if (i == nearestIndex)
            {
                continue;
            }
            updated[writeIndex] = patrolPoints[i];
            writeIndex++;
        }

        setObjVar(master, pointsVar, updated);
        setObjVar(master, dataPrefix + "patrolRefreshAt", 0);
        setObjVar(master, dataPrefix + "patrolIndex", rand(0, Math.max(updated.length - 1, 0)));
    }

    private float locationDistance(location a, location b)
    {
        if (a == null || b == null)
        {
            return 999999.0f;
        }
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float)Math.sqrt((dx * dx) + (dz * dz));
    }

    private void setSharedTarget(obj_id self, obj_id target) throws InterruptedException
    {
        obj_id master = getMasterObject(self);
        if (!isIdValid(master))
        {
            return;
        }
        setObjVar(master, getSquadDataPrefix(self) + "target", target);
    }

    private obj_id getSharedTarget(obj_id self) throws InterruptedException
    {
        obj_id master = getMasterObject(self);
        if (!isIdValid(master) || !hasObjVar(master, getSquadDataPrefix(self) + "target"))
        {
            return obj_id.NULL_ID;
        }
        return getObjIdObjVar(master, getSquadDataPrefix(self) + "target");
    }

    private void setSharedAlertState(obj_id self, String state) throws InterruptedException
    {
        obj_id master = getMasterObject(self);
        if (!isIdValid(master))
        {
            return;
        }
        setObjVar(master, getSquadDataPrefix(self) + "alertState", state);
    }

    private obj_id getSharedLeader(obj_id self) throws InterruptedException
    {
        obj_id master = getMasterObject(self);
        if (!isIdValid(master) || !hasObjVar(master, getSquadDataPrefix(self) + "leader"))
        {
            return obj_id.NULL_ID;
        }
        return getObjIdObjVar(master, getSquadDataPrefix(self) + "leader");
    }

    private obj_id getMasterObject(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_SPAWN_MASTER))
        {
            return obj_id.NULL_ID;
        }
        return getObjIdObjVar(self, OBJVAR_SPAWN_MASTER);
    }

    private String getSquadId(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_SQUAD_ID))
        {
            return null;
        }
        String squadId = getStringObjVar(self, OBJVAR_SQUAD_ID);
        if (squadId == null || squadId.length() < 1)
        {
            return null;
        }
        return squadId;
    }

    private String getSquadDataPrefix(obj_id self) throws InterruptedException
    {
        return SQUAD_DATA_PREFIX + getSquadId(self) + ".";
    }

    private void queueThink(obj_id self, float delay) throws InterruptedException
    {
        messageTo(self, MSG_THINK, null, delay, false);
    }
}
