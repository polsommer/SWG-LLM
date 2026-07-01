package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

import script.library.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class space_capital_raids extends script.base_script
{
    private static final String RAID_TABLE = "datatables/space/capital_raid_templates.iff";
    private static final String VAR_ACTIVE_RAID = "spaceRaid.active";
    private static final String VAR_ACTIVE_MISSION = "spaceDynamic.activeMission";
    private static final String VAR_RAID_WAYPOINT = "spaceRaid.waypoint";
    private static final String VAR_BONDS = "gcwCampaign.bonds";
    private static final int DEFAULT_DURATION_MINUTES = 30;
    private static final float COMPLETION_GRACE_RATIO = 0.33f;
    private static final int MIN_COMPLETION_GRACE_SECONDS = 300;
    private static final int MAX_COMPLETION_GRACE_SECONDS = 1200;
    private static final float FAILURE_GRACE_RATIO = 0.15f;
    private static final int MIN_FAILURE_GRACE_SECONDS = 180;
    private static final int MAX_FAILURE_GRACE_SECONDS = 480;

    private static Map<String, RaidDefinition> RAIDS_BY_ID;
    private static Map<String, RaidInstance> ACTIVE_INSTANCES;

    private static class RaidDefinition
    {
        final String raidId;
        final String bossTemplate;
        final int minPlayers;
        final int maxPlayers;
        final int durationMinutes;
        final int supplyReward;
        final String lootGroup;
        final String spaceScene;
        final location entryLocation;
        final location exitLocation;
        final String notes;

        RaidDefinition()
        {
            this("", "", 0, 0, 0, 0, "", "", "", "", "");
        }

        RaidDefinition(String raidId, String bossTemplate, int minPlayers, int maxPlayers, int durationMinutes, int supplyReward, String lootGroup, String spaceScene, String entryPoint, String exitPoint, String notes)
        {
            this.raidId = raidId;
            this.bossTemplate = bossTemplate;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
            this.durationMinutes = durationMinutes;
            this.supplyReward = supplyReward;
            this.lootGroup = lootGroup;
            this.spaceScene = (spaceScene != null && spaceScene.length() > 0) ? spaceScene : "space_tatooine";
            this.entryLocation = parseLocation(entryPoint, this.spaceScene);
            this.exitLocation = parseLocation(exitPoint, this.spaceScene);
            this.notes = notes;
        }
    }

    private static class RaidInstance
    {
        final String instanceId;
        final RaidDefinition definition;
        final obj_id leader;
        final List<obj_id> participants;
        final int launchTime;
        final int durationSeconds;
        final int completionGraceSeconds;
        final int failureGraceSeconds;
        obj_id boss;
        boolean completed;
        boolean failed;
        int completionTime;
        final Set<obj_id> credited = new HashSet<>();

        RaidInstance(String instanceId, RaidDefinition definition, obj_id leader, List<obj_id> participants, int launchTime, int durationSeconds, int completionGraceSeconds, int failureGraceSeconds)
        {
            this.instanceId = instanceId;
            this.definition = definition;
            this.leader = leader;
            this.participants = participants;
            this.launchTime = launchTime;
            this.durationSeconds = durationSeconds;
            this.completionGraceSeconds = completionGraceSeconds;
            this.failureGraceSeconds = failureGraceSeconds;
        }
    }

    public space_capital_raids()
    {
    }

    public static void ensureLoaded() throws InterruptedException
    {
        if (RAIDS_BY_ID != null)
        {
            return;
        }
        RAIDS_BY_ID = new HashMap<>();
        int rows = dataTableGetNumRows(RAID_TABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(RAID_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String raidId = row.getString("raidId");
            if (raidId == null || raidId.length() == 0)
            {
                continue;
            }
            RaidDefinition raid = new RaidDefinition(raidId,
                    row.getString("bossTemplate"),
                    row.getInt("minPlayers"),
                    row.getInt("maxPlayers"),
                    row.getInt("durationMinutes"),
                    row.getInt("supplyReward"),
                    row.getString("lootGroup"),
                    row.getString("spaceScene"),
                    row.getString("entryPoint"),
                    row.getString("exitPoint"),
                    row.getString("notes"));
            RAIDS_BY_ID.put(raidId, raid);
        }
    }

    public static String queueRaid(String raidId, obj_id leader, obj_id[] participants) throws InterruptedException
    {
        ensureLoaded();
        pruneInstances();
        RaidDefinition raid = RAIDS_BY_ID.get(raidId);
        if (raid == null || !isIdValid(leader))
        {
            return null;
        }
        List<obj_id> registered = new ArrayList<>();
        if (!addParticipant(registered, leader, raidId))
        {
            sendSystemMessage(leader, "Unable to register you for raid " + formatRaidName(raid) + ". Launch the assigned mission to receive the operation.", "");
            return null;
        }
        List<String> skipped = new ArrayList<>();
        if (participants != null)
        {
            for (obj_id member : participants)
            {
                if (!isIdValid(member) || member == leader)
                {
                    continue;
                }
                if (!addParticipant(registered, member, raidId))
                {
                    skipped.add(getParticipantName(member));
                }
            }
        }
        if (registered.size() < raid.minPlayers)
        {
            sendSystemMessage(leader, "Raid assignment requires " + raid.minPlayers + " qualified pilots.", "");
            return null;
        }
        if (!skipped.isEmpty())
        {
            String skippedMessage = formatSkippedParticipants(skipped);
            if (skippedMessage != null && skippedMessage.length() > 0)
            {
                sendSystemMessage(leader, "Pilots skipped (mission mismatch): " + skippedMessage, "");
            }
        }
        if (registered.size() > raid.maxPlayers)
        {
            sendSystemMessage(leader, "Only " + raid.maxPlayers + " pilots can register for " + formatRaidName(raid) + ". Excess pilots were omitted.", "");
            registered = new ArrayList<>(registered.subList(0, raid.maxPlayers));
        }
        String instanceId = raid.raidId + ":" + getGameTime() + ":" + leader;
        int now = getGameTime();
        int durationSeconds = Math.max(raid.durationMinutes > 0 ? raid.durationMinutes : DEFAULT_DURATION_MINUTES, 5) * 60;
        int completionGrace = clamp(Math.round(durationSeconds * COMPLETION_GRACE_RATIO), MIN_COMPLETION_GRACE_SECONDS, MAX_COMPLETION_GRACE_SECONDS);
        int failureGrace = clamp(Math.round(durationSeconds * FAILURE_GRACE_RATIO), MIN_FAILURE_GRACE_SECONDS, MAX_FAILURE_GRACE_SECONDS);
        if (completionGrace <= 0)
        {
            completionGrace = MIN_COMPLETION_GRACE_SECONDS;
        }
        if (failureGrace <= 0)
        {
            failureGrace = MIN_FAILURE_GRACE_SECONDS;
        }
        RaidInstance instance = new RaidInstance(instanceId, raid, leader, new ArrayList<>(registered), now, durationSeconds, completionGrace, failureGrace);
        obj_id boss = spawnBoss(instance);
        if (!isIdValid(boss))
        {
            sendSystemMessage(leader, "Unable to initialize raid " + formatRaidName(raid) + ". Please contact support.", "");
            return null;
        }
        instance.boss = boss;
        if (ACTIVE_INSTANCES == null)
        {
            ACTIVE_INSTANCES = new HashMap<>();
        }
        ACTIVE_INSTANCES.put(instanceId, instance);
        for (obj_id member : instance.participants)
        {
            briefParticipant(instance, member, member == leader);
        }
        startRaidTimer(instance);
        return instanceId;
    }

    public static void handleRaidContribution(String instanceId, obj_id contributor) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(contributor) || ACTIVE_INSTANCES == null)
        {
            return;
        }
        RaidInstance instance = ACTIVE_INSTANCES.get(instanceId);
        if (instance == null)
        {
            return;
        }
        if (instance.failed)
        {
            return;
        }
        if (!instance.completed)
        {
            sendSystemMessage(contributor, "Raid objectives are still active. Complete the capital ship encounter before debriefing.", "");
            return;
        }
        if (!instance.participants.contains(contributor))
        {
            if (!qualifiesForLateCredit(instance, contributor))
            {
                sendSystemMessage(contributor, "You were not registered for this raid instance. Coordinate with the leader before debriefing.", "");
                return;
            }
            instance.participants.add(contributor);
            if (instance.completed)
            {
                updateWaypoint(contributor, instance.definition.exitLocation, "Raid Debrief: " + formatRaidName(instance.definition));
                sendSystemMessage(contributor, "Raid registration updated. Proceed to the debrief point to finalize rewards.", "");
            }
        }
        if (instance.credited.contains(contributor))
        {
            return;
        }
        instance.credited.add(contributor);
        int faction = pvpGetAlignedFaction(contributor);
        script.library.gcw_campaign.recordSupplyContribution(instance.definition.spaceScene, faction, instance.definition.supplyReward, "space_raid");
        int bonds = getIntObjVar(contributor, VAR_BONDS);
        setObjVar(contributor, VAR_BONDS, bonds + instance.definition.supplyReward);
        sendSystemMessage(contributor, "Capital raid debrief complete. Campaign Bonds awarded: " + instance.definition.supplyReward + ".", "");
        if (instance.credited.size() >= instance.participants.size())
        {
            cleanupInstance(instance, true);
        }
    }

    public static void bossDestroyed(String instanceId, obj_id boss) throws InterruptedException
    {
        ensureLoaded();
        if (ACTIVE_INSTANCES == null)
        {
            return;
        }
        RaidInstance instance = ACTIVE_INSTANCES.get(instanceId);
        if (instance == null)
        {
            return;
        }
        instance.boss = obj_id.NULL_ID;
        instance.completed = true;
        instance.completionTime = getGameTime();
        if (isIdValid(instance.leader))
        {
            dictionary cancel = new dictionary();
            cancel.put("instanceId", instance.instanceId);
            messageTo(instance.leader, "cancelRaidTimer", cancel, 0.0f, false);
        }
        for (obj_id member : instance.participants)
        {
            if (!isIdValid(member))
            {
                continue;
            }
            if (isEligibleGuildGroup(member))
            {
                guild.recordGuildPveContribution(member, "space_boss_hunt", 1, "space_capital_raid");
            }
            updateWaypoint(member, instance.definition.exitLocation, "Raid Debrief: " + formatRaidName(instance.definition));
            sendSystemMessage(member, "Capital flagship destroyed! Return to the debrief point to finalize the operation.", "");
        }
    }

    public static void timeout(String instanceId) throws InterruptedException
    {
        ensureLoaded();
        if (ACTIVE_INSTANCES == null)
        {
            return;
        }
        RaidInstance instance = ACTIVE_INSTANCES.get(instanceId);
        if (instance == null || instance.failed || instance.completed)
        {
            return;
        }
        instance.failed = true;
        for (obj_id member : instance.participants)
        {
            if (isIdValid(member))
            {
                sendSystemMessage(member, "Raid " + formatRaidName(instance.definition) + " expired before objectives were met.", "");
            }
        }
        cleanupInstance(instance, true);
    }

    private static boolean addParticipant(List<obj_id> registered, obj_id player, String raidId) throws InterruptedException
    {
        if (!isIdValid(player) || registered.contains(player))
        {
            return false;
        }
        dictionary mission = utils.getDictionaryScriptVar(player, VAR_ACTIVE_MISSION);
        if (mission == null)
        {
            return false;
        }
        String hook = mission.getString("raidHook");
        if (hook == null || hook.length() == 0 || !hook.equals(raidId))
        {
            return false;
        }
        registered.add(player);
        return true;
    }

    private static boolean isEligibleGuildGroup(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return false;
        }
        obj_id group = getGroupObject(player);
        if (!isIdValid(group))
        {
            return false;
        }
        obj_id[] members = getGroupMemberIds(group);
        if (members == null || members.length < 2)
        {
            return false;
        }
        int guildId = getGuildId(player);
        if (guildId <= 0)
        {
            return false;
        }
        int guildMembers = 0;
        for (obj_id member : members) {
            if (!isIdValid(member)) {
                continue;
            }
            if (getGuildId(member) == guildId) {
                guildMembers++;
                if (guildMembers >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private static obj_id spawnBoss(RaidInstance instance) throws InterruptedException
    {
        location entry = instance.definition.entryLocation;
        if (entry == null)
        {
            return obj_id.NULL_ID;
        }
        location spawn = new location(entry.x, entry.y, entry.z, entry.area);
        obj_id boss = createObject(instance.definition.bossTemplate, spawn);
        if (!isIdValid(boss))
        {
            return obj_id.NULL_ID;
        }
        setObjVar(boss, VAR_ACTIVE_RAID, instance.instanceId);
        setObjVar(boss, "spaceRaid.instanceId", instance.instanceId);
        setObjVar(boss, "spaceRaid.raidId", instance.definition.raidId);
        if (instance.definition.lootGroup != null && instance.definition.lootGroup.length() > 0)
        {
            setObjVar(boss, "loot.strLootTable", instance.definition.lootGroup);
            int lootRolls = Math.max(instance.definition.minPlayers, 4);
            setObjVar(boss, "loot.intNumItems", lootRolls);
        }
        attachScript(boss, "space.combat.capital_raid_boss");
        return boss;
    }

    private static void briefParticipant(RaidInstance instance, obj_id participant, boolean leader) throws InterruptedException
    {
        if (!isIdValid(participant))
        {
            return;
        }
        setObjVar(participant, VAR_ACTIVE_RAID, instance.instanceId);
        if (instance.definition.entryLocation != null)
        {
            updateWaypoint(participant, instance.definition.entryLocation, "Raid: " + formatRaidName(instance.definition));
        }
        StringBuilder message = new StringBuilder();
        message.append("Raid assignment: ").append(formatRaidName(instance.definition));
        if (instance.definition.entryLocation != null)
        {
            message.append(" | Rendezvous ").append(instance.definition.entryLocation.area);
            message.append(" @ ").append(Math.round(instance.definition.entryLocation.x));
            message.append(", ").append(Math.round(instance.definition.entryLocation.y));
            message.append(", ").append(Math.round(instance.definition.entryLocation.z));
        }
        int durationMinutes = instance.definition.durationMinutes > 0 ? instance.definition.durationMinutes : DEFAULT_DURATION_MINUTES;
        message.append(" | Duration ").append(durationMinutes).append("m");
        if (leader)
        {
            message.append(" | You are the raid leader");
        }
        sendSystemMessage(participant, message.toString(), "");
    }

    private static void startRaidTimer(RaidInstance instance) throws InterruptedException
    {
        if (!isIdValid(instance.leader))
        {
            return;
        }
        if (!hasScript(instance.leader, "space.raid.capital_raid_session"))
        {
            attachScript(instance.leader, "space.raid.capital_raid_session");
        }
        dictionary params = new dictionary();
        params.put("instanceId", instance.instanceId);
        params.put("timeout", (float)instance.durationSeconds);
        messageTo(instance.leader, "startRaidSession", params, 0.0f, false);
    }

    private static void updateWaypoint(obj_id participant, location target, String name) throws InterruptedException
    {
        if (!isIdValid(participant))
        {
            return;
        }
        if (hasObjVar(participant, VAR_RAID_WAYPOINT))
        {
            obj_id old = getObjIdObjVar(participant, VAR_RAID_WAYPOINT);
            if (isIdValid(old))
            {
                destroyWaypointInDatapad(old, participant);
            }
            removeObjVar(participant, VAR_RAID_WAYPOINT);
        }
        if (target == null)
        {
            return;
        }
        obj_id waypoint = createWaypointInDatapad(participant, target);
        if (!isIdValid(waypoint))
        {
            return;
        }
        if (name != null && name.length() > 0)
        {
            setWaypointName(waypoint, name);
        }
        setWaypointActive(waypoint, true);
        setObjVar(participant, VAR_RAID_WAYPOINT, waypoint);
    }

    private static void cleanupInstance(RaidInstance instance, boolean notifyLeader) throws InterruptedException
    {
        if (instance == null)
        {
            return;
        }
        if (ACTIVE_INSTANCES != null)
        {
            ACTIVE_INSTANCES.remove(instance.instanceId);
        }
        if (instance.boss != null && isIdValid(instance.boss) && exists(instance.boss))
        {
            destroyObject(instance.boss);
        }
        for (obj_id member : instance.participants)
        {
            if (!isIdValid(member))
            {
                continue;
            }
            if (hasObjVar(member, VAR_ACTIVE_RAID))
            {
                String active = getStringObjVar(member, VAR_ACTIVE_RAID);
                if (active != null && active.equals(instance.instanceId))
                {
                    removeObjVar(member, VAR_ACTIVE_RAID);
                }
            }
            if (hasObjVar(member, VAR_RAID_WAYPOINT))
            {
                obj_id waypoint = getObjIdObjVar(member, VAR_RAID_WAYPOINT);
                if (isIdValid(waypoint))
                {
                    destroyWaypointInDatapad(waypoint, member);
                }
                removeObjVar(member, VAR_RAID_WAYPOINT);
            }
        }
        if (notifyLeader && isIdValid(instance.leader))
        {
            dictionary end = new dictionary();
            end.put("instanceId", instance.instanceId);
            messageTo(instance.leader, "cleanupRaidSession", end, 0.0f, false);
        }
    }

    private static void pruneInstances() throws InterruptedException
    {
        if (ACTIVE_INSTANCES == null || ACTIVE_INSTANCES.isEmpty())
        {
            return;
        }
        List<String> expired = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        int now = getGameTime();
        for (Map.Entry<String, RaidInstance> entry : ACTIVE_INSTANCES.entrySet())
        {
            RaidInstance instance = entry.getValue();
            if (instance == null)
            {
                stale.add(entry.getKey());
                continue;
            }
            if (!instance.completed && !instance.failed && now - instance.launchTime > instance.durationSeconds)
            {
                expired.add(entry.getKey());
            }
            else if (instance.failed)
            {
                if (now - instance.launchTime > instance.failureGraceSeconds)
                {
                    stale.add(entry.getKey());
                }
            }
            else if (instance.completed)
            {
                if (instance.credited.size() >= instance.participants.size() || now - instance.completionTime > instance.completionGraceSeconds)
                {
                    stale.add(entry.getKey());
                }
            }
        }
        for (String id : expired)
        {
            timeout(id);
        }
        for (String id : stale)
        {
            RaidInstance instance = ACTIVE_INSTANCES.get(id);
            if (instance != null)
            {
                cleanupInstance(instance, true);
            }
        }
    }

    private static location parseLocation(String coords, String scene)
    {
        if (coords == null || coords.length() == 0)
        {
            return null;
        }
        String[] parts = coords.trim().split("\\s+");
        if (parts.length < 3)
        {
            return null;
        }
        location loc = new location();
        try
        {
            loc.x = Float.parseFloat(parts[0]);
            loc.y = Float.parseFloat(parts[1]);
            loc.z = Float.parseFloat(parts[2]);
        }
        catch (NumberFormatException err)
        {
            return null;
        }
        loc.area = scene;
        return loc;
    }

    private static String formatRaidName(RaidDefinition raid)
    {
        if (raid == null || raid.raidId == null || raid.raidId.length() == 0)
        {
            return "Operation";
        }
        String base = raid.raidId.replace('_', ' ');
        if (base.length() == 0)
        {
            return "Operation";
        }
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    private static String getParticipantName(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return "Unknown";
        }
        String name = getFirstName(player);
        if (name == null || name.length() == 0)
        {
            name = getName(player);
        }
        return name != null ? name : "Unknown";
    }

    private static String formatSkippedParticipants(List<String> skipped)
    {
        if (skipped == null || skipped.isEmpty())
        {
            return null;
        }
        int limit = Math.min(5, skipped.size());
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < limit; i++)
        {
            if (i > 0)
            {
                buffer.append(", ");
            }
            buffer.append(skipped.get(i));
        }
        if (skipped.size() > limit)
        {
            buffer.append(" (+").append(skipped.size() - limit).append(" more)");
        }
        return buffer.toString();
    }

    private static boolean qualifiesForLateCredit(RaidInstance instance, obj_id contributor) throws InterruptedException
    {
        if (!isIdValid(contributor))
        {
            return false;
        }
        if (hasObjVar(contributor, VAR_ACTIVE_RAID))
        {
            String active = getStringObjVar(contributor, VAR_ACTIVE_RAID);
            if (active != null && active.equals(instance.instanceId))
            {
                return true;
            }
        }
        dictionary mission = utils.getDictionaryScriptVar(contributor, VAR_ACTIVE_MISSION);
        if (mission != null)
        {
            String raidInstance = mission.getString("raidInstance");
            if (raidInstance != null && raidInstance.equals(instance.instanceId))
            {
                setObjVar(contributor, VAR_ACTIVE_RAID, instance.instanceId);
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max)
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
}
