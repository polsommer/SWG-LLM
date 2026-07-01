package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class space_dynamic_content extends script.base_script
{
    private static final String ARC_TABLE = "datatables/space/dynamic_mission_arcs.iff";
    private static final String EVENT_TABLE = "datatables/space/weekly_events.iff";
    private static final String REWARD_TABLE = "datatables/space/space_reputation_rewards.iff";

    private static final String VAR_ACTIVE_MISSION = "spaceDynamic.activeMission";
    private static final String VAR_REPUTATION = "spaceDynamic.reputation";
    private static final String VAR_REPUTATION_RANK = "spaceDynamic.reputationRank";
    private static final String VAR_CORES = "spaceDynamic.astrogationCores";
    private static final String VAR_BONDS = "gcwCampaign.bonds";

    private static Map<String, MissionArc> ARCS_BY_ID;
    private static Map<Integer, List<MissionArc>> ARCS_BY_TIER;
    private static Map<String, WeeklyEvent> EVENTS_BY_ID;
    private static List<WeeklyEvent> EVENT_ROTATION;
    private static Map<String, ReputationReward> REWARDS_BY_RANK;
    private static List<ReputationReward> REWARD_ORDER;
    private static boolean CAPITAL_RAIDS_AVAILABLE = true;

    public static class MissionArc
    {
        public final String arcId;
        public final int tier;
        public final String faction;
        public final String objectiveType;
        public final int baseReward;
        public final int supplyImpact;
        public final int reputationImpact;
        public final int astrogationReward;
        public final String raidHook;
        public final String requiresShipClass;
        public final String trialCard;
        public final String narrative;

        public MissionArc()
        {
            this("", 0, null, null, 0, 0, 0, 0, null, null, null, null);
        }

        public MissionArc(String arcId, int tier, String faction, String objectiveType, int baseReward, int supplyImpact, int reputationImpact, int astrogationReward, String raidHook, String requiresShipClass, String trialCard, String narrative)
        {
            this.arcId = arcId;
            this.tier = tier;
            this.faction = faction != null ? faction.toLowerCase() : "neutral";
            this.objectiveType = objectiveType;
            this.baseReward = baseReward;
            this.supplyImpact = supplyImpact;
            this.reputationImpact = reputationImpact;
            this.astrogationReward = astrogationReward;
            this.raidHook = raidHook;
            this.requiresShipClass = requiresShipClass;
            this.trialCard = trialCard;
            this.narrative = narrative;
        }
    }

    public static class WeeklyEvent
    {
        final String eventId;
        final String displayName;
        final String modifierType;
        final float modifierValue;
        final String description;
        final String supplyTag;

        public WeeklyEvent()
        {
            this("", "", "", 0.0f, null, null);
        }

        public WeeklyEvent(String eventId, String displayName, String modifierType, float modifierValue, String description, String supplyTag)
        {
            this.eventId = eventId;
            this.displayName = displayName;
            this.modifierType = modifierType;
            this.modifierValue = modifierValue;
            this.description = description;
            this.supplyTag = supplyTag;
        }
    }

    public static class ReputationReward
    {
        public final String rank;
        public final int threshold;
        public final String title;
        public final String unlock;
        public final String perk;
        public final String description;

        public ReputationReward()
        {
            this("", 0, "", null, null, null);
        }

        public ReputationReward(String rank, int threshold, String title, String unlock, String perk, String description)
        {
            this.rank = rank;
            this.threshold = threshold;
            this.title = title;
            this.unlock = unlock;
            this.perk = perk;
            this.description = description;
        }
    }

    public space_dynamic_content()
    {
    }

    private static void ensureArcsLoaded() throws InterruptedException
    {
        if (ARCS_BY_ID != null)
        {
            return;
        }
        ARCS_BY_ID = new HashMap<>();
        ARCS_BY_TIER = new HashMap<>();
        int rows = dataTableGetNumRows(ARC_TABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(ARC_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String arcId = row.getString("arcId");
            if (arcId == null || arcId.length() == 0)
            {
                continue;
            }
            MissionArc arc = new MissionArc(arcId,
                    row.getInt("tier"),
                    row.getString("faction"),
                    row.getString("objectiveType"),
                    row.getInt("baseReward"),
                    row.getInt("supplyImpact"),
                    row.getInt("reputationImpact"),
                    row.getInt("astrogationReward"),
                    row.getString("raidHook"),
                    row.getString("requiresShipClass"),
                    row.getString("trialCard"),
                    row.getString("narrative"));
            ARCS_BY_ID.put(arc.arcId, arc);
            List<MissionArc> tierList = ARCS_BY_TIER.get(Integer.valueOf(arc.tier));
            if (tierList == null)
            {
                tierList = new ArrayList<>();
                ARCS_BY_TIER.put(Integer.valueOf(arc.tier), tierList);
            }
            tierList.add(arc);
        }
        for (List<MissionArc> arcs : ARCS_BY_TIER.values())
        {
            Collections.shuffle(arcs);
        }
    }

    private static void ensureEventsLoaded() throws InterruptedException
    {
        if (EVENT_ROTATION != null)
        {
            return;
        }
        EVENT_ROTATION = new ArrayList<>();
        EVENTS_BY_ID = new HashMap<>();
        int rows = dataTableGetNumRows(EVENT_TABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(EVENT_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String id = row.getString("eventId");
            if (id == null || id.length() == 0)
            {
                continue;
            }
            WeeklyEvent event = new WeeklyEvent(id,
                    row.getString("displayName"),
                    row.getString("modifierType"),
                    row.getFloat("modifierValue"),
                    row.getString("description"),
                    row.getString("supplyTag"));
            EVENT_ROTATION.add(event);
            EVENTS_BY_ID.put(id, event);
        }
        if (EVENT_ROTATION.isEmpty())
        {
            EVENT_ROTATION.add(new WeeklyEvent("default", "Calm Lanes", "reward_multiplier", 1.0f, "Standard patrol conditions prevail this week.", "neutral"));
            EVENTS_BY_ID.put("default", EVENT_ROTATION.get(0));
        }
    }

    private static void ensureRewardsLoaded() throws InterruptedException
    {
        if (REWARD_ORDER != null)
        {
            return;
        }
        REWARD_ORDER = new ArrayList<>();
        REWARDS_BY_RANK = new HashMap<>();
        int rows = dataTableGetNumRows(REWARD_TABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(REWARD_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String rank = row.getString("rank");
            if (rank == null || rank.length() == 0)
            {
                continue;
            }
            ReputationReward reward = new ReputationReward(rank,
                    row.getInt("pointsRequired"),
                    row.getString("title"),
                    row.getString("unlock"),
                    row.getString("perk"),
                    row.getString("description"));
            REWARD_ORDER.add(reward);
            REWARDS_BY_RANK.put(rank, reward);
        }
        Collections.sort(REWARD_ORDER, (a, b) -> Integer.compare(a.threshold, b.threshold));
    }

    private static void ensureContentLoaded() throws InterruptedException
    {
        ensureArcsLoaded();
        ensureEventsLoaded();
        ensureRewardsLoaded();
        if (CAPITAL_RAIDS_AVAILABLE)
        {
            try
            {
                space_capital_raids.ensureLoaded();
            }
            catch (Throwable t)
            {
                CAPITAL_RAIDS_AVAILABLE = false;
            }
        }
    }

    private static void storeActiveMission(obj_id player, dictionary missionData) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        if (missionData == null)
        {
            utils.removeScriptVar(player, VAR_ACTIVE_MISSION);
            return;
        }
        utils.setScriptVar(player, VAR_ACTIVE_MISSION, missionData);
    }

    public static dictionary getActiveMission(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return null;
        }
        return utils.getDictionaryScriptVar(player, VAR_ACTIVE_MISSION);
    }

    public static void onPlayerLaunch(obj_id player, obj_id ship, location destination, boolean starportLaunch) throws InterruptedException
    {
        ensureContentLoaded();
        if (!isIdValid(player) || !isIdValid(ship))
        {
            return;
        }
        int reputation = getIntObjVar(player, VAR_REPUTATION);
        int tier = resolveTier(reputation);
        int faction = pvpGetAlignedFaction(player);
        String factionTag = resolveFactionTag(faction);
        MissionArc arc = chooseArc(player, ship, tier, factionTag);
        if (arc == null)
        {
            return;
        }
        WeeklyEvent event = getCurrentEvent();
        float rewardModifier = resolveRewardModifier(arc);
        String planet = resolveDestinationPlanet(destination, player);
        dictionary data = new dictionary();
        data.put("arcId", arc.arcId);
        data.put("tier", arc.tier);
        data.put("supplyImpact", arc.supplyImpact);
        data.put("planet", planet);
        data.put("rewardModifier", rewardModifier);
        if (event != null)
        {
            data.put("eventModifierType", event.modifierType);
            data.put("eventModifierValue", event.modifierValue);
        }
        data.put("faction", arc.faction);
        if (arc.raidHook != null && arc.raidHook.length() > 0)
        {
            data.put("raidHook", arc.raidHook);
        }
        storeActiveMission(player, data);
        sendSystemMessage(player, "[Space Command] " + arc.narrative, "");
        if (arc.raidHook != null && arc.raidHook.length() > 0)
        {
            obj_id group = getGroupObject(player);
            obj_id[] participants = null;
            if (isIdValid(group))
            {
                participants = getGroupMemberIds(group);
            }
            String raidInstance = null;
            if (CAPITAL_RAIDS_AVAILABLE)
            {
                try
                {
                    raidInstance = space_capital_raids.queueRaid(arc.raidHook, player, participants);
                }
                catch (Throwable t)
                {
                    CAPITAL_RAIDS_AVAILABLE = false;
                }
            }
            if (raidInstance != null && raidInstance.length() > 0)
            {
                data.put("raidInstance", raidInstance);
                storeActiveMission(player, data);
            }
        }
    }

    public static void completeMission(obj_id player, String arcId, boolean success) throws InterruptedException
    {
        ensureContentLoaded();
        if (!isIdValid(player))
        {
            return;
        }
        dictionary active = utils.getDictionaryScriptVar(player, VAR_ACTIVE_MISSION);
        if (active == null)
        {
            return;
        }
        String activeArcId = active.getString("arcId");
        if (arcId != null && arcId.length() > 0 && (activeArcId == null || !activeArcId.equals(arcId)))
        {
            return;
        }
        MissionArc arc = ARCS_BY_ID.get(activeArcId);
        if (arc == null)
        {
            utils.removeScriptVar(player, VAR_ACTIVE_MISSION);
            return;
        }
        if (!success)
        {
            sendSystemMessage(player, "Mission " + arc.arcId + " failed. Debrief at the starport terminal for reassignment.", "");
            utils.removeScriptVar(player, VAR_ACTIVE_MISSION);
            return;
        }
        applyMissionRewards(player, arc, active);
        guild.recordGuildPveContribution(player, "space_mission", 1, "space_mission");
        utils.removeScriptVar(player, VAR_ACTIVE_MISSION);
    }

    public static boolean spendAstrogationCores(obj_id player, int amount) throws InterruptedException
    {
        if (!isIdValid(player) || amount <= 0)
        {
            return false;
        }
        int cores = getIntObjVar(player, VAR_CORES);
        if (cores < amount)
        {
            return false;
        }
        setObjVar(player, VAR_CORES, cores - amount);
        return true;
    }

    public static void grantAstrogationCores(obj_id player, int amount) throws InterruptedException
    {
        if (!isIdValid(player) || amount <= 0)
        {
            return;
        }
        int cores = getIntObjVar(player, VAR_CORES);
        setObjVar(player, VAR_CORES, cores + amount);
    }

    public static int getAstrogationCores(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return 0;
        }
        return getIntObjVar(player, VAR_CORES);
    }

    public static dictionary getActiveWeeklyEvent() throws InterruptedException
    {
        ensureEventsLoaded();
        WeeklyEvent event = getCurrentEvent();
        dictionary info = new dictionary();
        info.put("eventId", event.eventId);
        info.put("displayName", event.displayName);
        info.put("modifierType", event.modifierType);
        info.put("modifierValue", event.modifierValue);
        info.put("description", event.description);
        info.put("supplyTag", event.supplyTag);
        return info;
    }

    private static MissionArc chooseArc(obj_id player, obj_id ship, int tier, String factionTag) throws InterruptedException
    {
        List<MissionArc> candidates = ARCS_BY_TIER.get(Integer.valueOf(tier));
        if (candidates == null || candidates.isEmpty())
        {
            candidates = ARCS_BY_TIER.get(Integer.valueOf(1));
        }
        if (candidates == null || candidates.isEmpty())
        {
            return null;
        }
        List<MissionArc> filtered = new ArrayList<>();
        String template = getTemplateName(ship);
        for (MissionArc arc : candidates)
        {
            if (arc.faction != null && !arc.faction.equals("neutral") && !arc.faction.equalsIgnoreCase(factionTag))
            {
                continue;
            }
            if (!matchesShipClass(template, arc.requiresShipClass))
            {
                continue;
            }
            filtered.add(arc);
        }
        if (filtered.isEmpty())
        {
            filtered.addAll(candidates);
        }
        return filtered.get(rand(0, filtered.size() - 1));
    }

    private static int resolveTier(int reputation)
    {
        if (reputation >= 1200)
        {
            return 3;
        }
        if (reputation >= 400)
        {
            return 2;
        }
        return 1;
    }

    private static String resolveFactionTag(int faction) throws InterruptedException
    {
        String factionName = factions.getFactionNameByHashCode(faction);
        if (factionName != null)
        {
            if (factionName.equalsIgnoreCase(factions.FACTION_IMPERIAL))
            {
                return "imperial";
            }
            if (factionName.equalsIgnoreCase(factions.FACTION_REBEL))
            {
                return "rebel";
            }
        }
        return "neutral";
    }

    private static float resolveRewardModifier(MissionArc arc) throws InterruptedException
    {
        WeeklyEvent event = getCurrentEvent();
        if (event == null)
        {
            return 1.0f;
        }
        if ("reward_multiplier".equals(event.modifierType))
        {
            return event.modifierValue;
        }
        if ("faction_bonus_imperial".equals(event.modifierType) && "imperial".equals(arc.faction))
        {
            return 1.0f + (event.modifierValue / 100.0f);
        }
        if ("faction_bonus_rebel".equals(event.modifierType) && "rebel".equals(arc.faction))
        {
            return 1.0f + (event.modifierValue / 100.0f);
        }
        return 1.0f;
    }

    private static WeeklyEvent getCurrentEvent() throws InterruptedException
    {
        ensureEventsLoaded();
        int seconds = getCalendarTime();
        int week = seconds / (7 * 86400);
        if (week < 0)
        {
            week = 0;
        }
        return EVENT_ROTATION.get(week % EVENT_ROTATION.size());
    }

    private static String resolveDestinationPlanet(location destination, obj_id player) throws InterruptedException
    {
        if (destination != null && destination.area != null)
        {
            return destination.area;
        }
        location here = getLocation(player);
        if (here != null && here.area != null)
        {
            return here.area;
        }
        return "tatooine";
    }

    private static boolean matchesShipClass(String template, String requirement)
    {
        if (requirement == null || requirement.length() == 0)
        {
            return true;
        }
        if (template == null || template.length() == 0)
        {
            return false;
        }
        String lowerTemplate = template.toLowerCase();
        String req = requirement.toLowerCase();
        if ("light".equals(req))
        {
            return lowerTemplate.contains("light") || lowerTemplate.contains("scout") || lowerTemplate.contains("nimble");
        }
        if ("fighter".equals(req))
        {
            return lowerTemplate.contains("fighter") || lowerTemplate.contains("xwing") || lowerTemplate.contains("tie");
        }
        if ("interceptor".equals(req))
        {
            return lowerTemplate.contains("interceptor") || lowerTemplate.contains("tiein") || lowerTemplate.contains("advanced");
        }
        if ("freighter".equals(req))
        {
            return lowerTemplate.contains("freighter") || lowerTemplate.contains("transport") || lowerTemplate.contains("yt");
        }
        if ("gunship".equals(req))
        {
            return lowerTemplate.contains("gunship") || lowerTemplate.contains("gunboat") || lowerTemplate.contains("assault");
        }
        return true;
    }

    private static void applyMissionRewards(obj_id player, MissionArc arc, dictionary active) throws InterruptedException
    {
        float modifier = active.getFloat("rewardModifier");
        if (modifier <= 0)
        {
            modifier = 1.0f;
        }
        int reputationGain = Math.round((arc.baseReward + arc.reputationImpact) * modifier);
        int currentRep = getIntObjVar(player, VAR_REPUTATION);
        currentRep += reputationGain;
        setObjVar(player, VAR_REPUTATION, currentRep);
        evaluateReputation(player, currentRep);
        int coresAward = Math.round(arc.astrogationReward * modifier);
        if (coresAward > 0)
        {
            grantAstrogationCores(player, coresAward);
            sendSystemMessage(player, "Astrogation cores earned: " + coresAward + ".", "");
        }
        int bonds = getIntObjVar(player, VAR_BONDS);
        int bondGain = Math.max(1, arc.supplyImpact / 2);
        setObjVar(player, VAR_BONDS, bonds + bondGain);
        sendSystemMessage(player, "Campaign Bonds awarded: " + bondGain + ".", "");
        String planet = active.getString("planet");
        if (planet == null || planet.length() == 0)
        {
            planet = "tatooine";
        }
        int faction = pvpGetAlignedFaction(player);
        script.library.gcw_campaign.recordSupplyContribution(planet, faction, arc.supplyImpact, "space_mission");
        if (arc.trialCard != null && arc.trialCard.length() > 0)
        {
            enclave_trials.recordProgress(player, arc.trialCard, 1);
        }
        String raidInstance = active.getString("raidInstance");
        if (CAPITAL_RAIDS_AVAILABLE && raidInstance != null && raidInstance.length() > 0)
        {
            try
            {
                space_capital_raids.handleRaidContribution(raidInstance, player);
            }
            catch (Throwable t)
            {
                CAPITAL_RAIDS_AVAILABLE = false;
            }
        }
        sendSystemMessage(player, "Mission " + arc.arcId + " complete. Reputation gained: " + reputationGain + ".", "");
    }

    private static void evaluateReputation(obj_id player, int reputation) throws InterruptedException
    {
        ensureRewardsLoaded();
        String currentRank = getStringObjVar(player, VAR_REPUTATION_RANK);
        String achievedRank = currentRank;
        for (ReputationReward reward : REWARD_ORDER)
        {
            if (reputation >= reward.threshold)
            {
                achievedRank = reward.rank;
            }
        }
        if (achievedRank != null && !achievedRank.equals(currentRank))
        {
            setObjVar(player, VAR_REPUTATION_RANK, achievedRank);
            ReputationReward reward = REWARDS_BY_RANK.get(achievedRank);
            if (reward != null)
            {
                sendSystemMessage(player, "New space reputation rank achieved: " + reward.title + ".", "");
                if (reward.description != null && reward.description.length() > 0)
                {
                    sendSystemMessage(player, reward.description, "");
                }
            }
        }
    }
}
