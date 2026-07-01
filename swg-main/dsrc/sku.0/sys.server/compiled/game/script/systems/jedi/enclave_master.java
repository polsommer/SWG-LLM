package script.systems.jedi;

import script.dictionary;
import script.obj_id;
import script.location;

import script.library.factions;
import script.library.force_rank;
import script.library.gcw_campaign;
import script.library.enclave_trials;
import script.library.enclave_trials.TrialDefinition;
import script.library.sui;
import script.library.utils;

import java.util.ArrayList;
import java.util.List;

public class enclave_master extends script.base_script
{
    private static final String VAR_AVAILABLE = "enclave.available";
    private static final String VAR_ALIGNMENT = "enclave.alignment";
    private static final String VAR_PHASE = "enclave.phase";
    private static final String VAR_SUPPLY = "enclave.supply";
    private static final String VAR_PLANET = "enclave.planet";
    private static final String VAR_TRIAL_SUI = "enclave.trialStatusPid";
    private static final String VAR_DAILY_CARD = "enclave.dailyCard";
    private static final int FRS_XP_PER_POINT = 100;
    private static final String VAR_RUINS_BOOK_GRANTED = "enclave.ruins.bookGranted";
    private static final String VAR_RUINS_UNLOCK_WAYPOINT = "enclave.ruins.unlockWaypoint";
    private static final String RUINS_BOOK_TEMPLATE = "object/tangible/loot/quest/victor_questn_journal.iff";
    private static final int RUINS_UNLOCK_LEVEL = 90;
    private static final location RUINS_UNLOCK_LOCATION = new location(442.0f, 5.0f, 4590.0f, "dantooine", null);
    private static final String MSG_RUINS_BACKFILL = "enclaveRuinsUnlockBackfill";

    public enclave_master()
    {
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        refreshState(self, false, false);
        queueRuinsBackfill(self, 1.0f);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        refreshState(self, false, false);
        queueRuinsBackfill(self, 2.0f);
        return SCRIPT_CONTINUE;
    }

    public int handlePlayerLogin(obj_id self, dictionary params) throws InterruptedException
    {
        refreshState(self, true, false);
        queueRuinsBackfill(self, 4.0f);
        return SCRIPT_CONTINUE;
    }

    public int requestEnclaveTrialStatus(obj_id self, dictionary params) throws InterruptedException
    {
        boolean forceDailyRefresh = false;
        if (params != null && params.containsKey("forceRefresh"))
        {
            forceDailyRefresh = params.getBoolean("forceRefresh");
        }
        refreshState(self, true, forceDailyRefresh);
        return SCRIPT_CONTINUE;
    }

    public int updateTrialProgress(obj_id self, dictionary params) throws InterruptedException
    {
        String cardId = params.getString("cardId");
        int amount = params.getInt("amount");
        if (amount <= 0)
        {
            amount = 1;
        }
        TrialDefinition def = null;
        if (cardId != null && cardId.length() > 0)
        {
            def = enclave_trials.getTrial(cardId);
        }
        if (def == null)
        {
            String objectiveType = params.getString("objectiveType");
            String alignment = utils.getStringScriptVar(self, VAR_ALIGNMENT);
            String phase = utils.getStringScriptVar(self, VAR_PHASE);
            float supply = utils.getFloatScriptVar(self, VAR_SUPPLY);
            def = enclave_trials.findTrialByObjectiveType(objectiveType, alignment, phase, supply);
        }
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        cardId = def.cardId;
        int progress = getIntObjVar(self, "enclave.progress." + cardId);
        progress += amount;
        int previous = progress - amount;
        if (previous < 0)
        {
            previous = 0;
        }
        if (progress < def.objectiveTarget)
        {
            setObjVar(self, "enclave.progress." + cardId, progress);
            sendSystemMessage(self, "Trial " + enclave_trials.formatTrialName(def.cardId) + " progress: " + progress + "/" + def.objectiveTarget + ".", "");
            String progressStatus = enclave_trials.getProgressStatus(def, progress);
            if (progressStatus != null && progressStatus.length() > 0)
            {
                sendSystemMessage(self, progressStatus, "");
            }
            if (previous <= 0)
            {
                String requirements = enclave_trials.describeRequirements(def);
                if (requirements != null && requirements.length() > 0)
                {
                    sendSystemMessage(self, "Requirements: " + requirements + ".", "");
                }
                String hint = enclave_trials.getHint(def, progress);
                if (hint != null && hint.length() > 0)
                {
                    sendSystemMessage(self, "Hint: " + hint, "");
                }
            }
            return SCRIPT_CONTINUE;
        }
        setObjVar(self, "enclave.progress." + cardId, 0);
        int completions = getIntObjVar(self, "enclave.history." + cardId);
        setObjVar(self, "enclave.history." + cardId, completions + 1);
        int reward = def.reward;
        if (reward > 0)
        {
            int bonds = getIntObjVar(self, "gcwCampaign.bonds");
            setObjVar(self, "gcwCampaign.bonds", bonds + reward);
        }
        int influence = def.influenceReward;
        if (influence > 0)
        {
            int totalInfluence = getIntObjVar(self, "enclave.influence");
            setObjVar(self, "enclave.influence", totalInfluence + influence);
        }
        String alignment = utils.getStringScriptVar(self, VAR_ALIGNMENT);
        String planet = utils.getStringScriptVar(self, VAR_PLANET);
        if (planet == null || planet.length() == 0)
        {
            planet = "tatooine";
        }
        gcw_campaign.recordSupplyContribution(planet, alignmentToFaction(alignment), influence, "enclave_trial");
        if (force_rank.isForceRanked(self))
        {
            int xpAward = (def.reward + def.influenceReward) * FRS_XP_PER_POINT;
            if (xpAward > 0)
            {
                force_rank.adjustForceRankXP(self, xpAward);
            }
        }
        StringBuilder completion = new StringBuilder();
        completion.append("Trial ").append(enclave_trials.formatTrialName(def.cardId)).append(" complete.");
        if (reward > 0)
        {
            completion.append(" Campaign Bonds earned: ").append(reward).append('.');
        }
        if (influence > 0)
        {
            completion.append(" Enclave influence gained: ").append(influence).append('.');
        }
        sendSystemMessage(self, completion.toString(), "");
        boolean countedDaily = enclave_trials.registerCompletion(self, def);
        if (countedDaily)
        {
            String streakStatus = enclave_trials.getDailyStreakStatus(self);
            if (streakStatus != null && streakStatus.length() > 0)
            {
                sendSystemMessage(self, streakStatus, "");
            }
        }
        else if (enclave_trials.getDailyStreak(self) <= 0)
        {
            sendSystemMessage(self, "Complete highlighted daily missions to build your enclave reputation.", "");
        }
        String previousDaily = utils.getStringScriptVar(self, VAR_DAILY_CARD);
        refreshState(self, false, true);
        String[] available = utils.getStringArrayScriptVar(self, VAR_AVAILABLE);
        showTrialStatus(self, available);
        String currentDaily = utils.getStringScriptVar(self, VAR_DAILY_CARD);
        if (currentDaily != null && (previousDaily == null || !currentDaily.equals(previousDaily)))
        {
            TrialDefinition newDaily = enclave_trials.getTrial(currentDaily);
            if (newDaily != null)
            {
                sendSystemMessage(self, "New daily mission: " + enclave_trials.formatTrialName(newDaily.cardId) + ".", "");
                String dailyHint = enclave_trials.getHint(newDaily, getIntObjVar(self, "enclave.progress." + newDaily.cardId));
                if (dailyHint != null && dailyHint.length() > 0)
                {
                    sendSystemMessage(self, "Hint: " + dailyHint, "");
                }
            }
        }
        else if (currentDaily == null && previousDaily != null && previousDaily.length() > 0)
        {
            sendSystemMessage(self, "Daily mission rotation is currently paused. Check back after the next enclave update.", "");
        }
        return SCRIPT_CONTINUE;
    }

    public int enclaveRuinsUnlockBackfill(obj_id self, dictionary params) throws InterruptedException
    {
        ensureRuinsUnlockKit(self);
        return SCRIPT_CONTINUE;
    }

    private void queueRuinsBackfill(obj_id self, float delay) throws InterruptedException
    {
        if (!isIdValid(self) || !isPlayer(self))
        {
            return;
        }
        messageTo(self, MSG_RUINS_BACKFILL, null, delay, false);
    }

    private void refreshState(obj_id self, boolean notify, boolean forceDailyRefresh) throws InterruptedException
    {
        String alignment = determineAlignment(self);
        utils.setScriptVar(self, VAR_ALIGNMENT, alignment);
        String planet = resolvePlanet(self);
        utils.setScriptVar(self, VAR_PLANET, planet);
        String phase = gcw_campaign.getCurrentPhaseName(planet);
        if (phase == null || phase.length() == 0)
        {
            phase = "Preparation";
        }
        utils.setScriptVar(self, VAR_PHASE, phase);
        float supply = gcw_campaign.getEffectiveSupply(planet, alignmentToFaction(alignment));
        utils.setScriptVar(self, VAR_SUPPLY, supply);
        TrialDefinition[] availableDefs = enclave_trials.getAvailableTrialDefinitions(alignment, phase, supply);
        String[] trials;
        if (availableDefs != null && availableDefs.length > 0)
        {
            trials = new String[availableDefs.length];
            for (int i = 0; i < availableDefs.length; i++)
            {
                trials[i] = availableDefs[i].cardId;
            }
        }
        else
        {
            trials = new String[0];
        }
        utils.setScriptVar(self, VAR_AVAILABLE, trials);
        TrialDefinition daily = enclave_trials.ensureDailyTrial(self, alignment, phase, supply, forceDailyRefresh);
        if (daily != null)
        {
            utils.setScriptVar(self, VAR_DAILY_CARD, daily.cardId);
        }
        else
        {
            utils.removeScriptVar(self, VAR_DAILY_CARD);
        }
        enclave_trials.refreshWaypoints(self, daily, availableDefs);
        ensureRuinsUnlockKit(self);
        if (notify)
        {
            sendStateSummary(self, alignment, phase, supply);
            showTrialStatus(self, trials);
        }
    }

    private void ensureRuinsUnlockKit(obj_id self) throws InterruptedException
    {
        if (!isIdValid(self) || !isPlayer(self))
        {
            return;
        }
        if (!isJedi(self) || getLevel(self) < RUINS_UNLOCK_LEVEL)
        {
            return;
        }
        boolean hasBook = utils.playerHasItemByTemplate(self, RUINS_BOOK_TEMPLATE);
        if (!hasBook)
        {
            obj_id book = createObjectInInventoryAllowOverload(RUINS_BOOK_TEMPLATE, self);
            if (isIdValid(book))
            {
                setName(book, "Weird Old Book");
                setObjVar(self, VAR_RUINS_BOOK_GRANTED, 1);
                sendSystemMessage(self, "You sense an ancient pull through the Force. A Weird Old Book appears in your pack.", "");
                hasBook = true;
            }
        }
        if (hasBook && !hasObjVar(self, VAR_RUINS_BOOK_GRANTED))
        {
            setObjVar(self, VAR_RUINS_BOOK_GRANTED, 1);
        }
        obj_id waypoint = obj_id.NULL_ID;
        boolean created = false;
        if (hasObjVar(self, VAR_RUINS_UNLOCK_WAYPOINT))
        {
            obj_id existing = getObjIdObjVar(self, VAR_RUINS_UNLOCK_WAYPOINT);
            if (isIdValid(existing) && exists(existing))
            {
                waypoint = existing;
                location existingLocation = getWaypointLocation(existing);
                if (existingLocation == null || existingLocation.area == null || !RUINS_UNLOCK_LOCATION.area.equals(existingLocation.area) || getDistance(existingLocation, RUINS_UNLOCK_LOCATION) > 32.0f)
                {
                    setWaypointLocation(existing, RUINS_UNLOCK_LOCATION);
                    sendSystemMessage(self, "Your ruins trial waypoint has been corrected to the active Dantooine ruins site.", "");
                }
            }
            else
            {
                removeObjVar(self, VAR_RUINS_UNLOCK_WAYPOINT);
            }
        }
        if (!isIdValid(waypoint))
        {
            waypoint = findWaypointInDatapad(self, "Dantooine Abandoned Ruins - Jedi Artifact Trial", RUINS_UNLOCK_LOCATION, 32.0f);
        }
        if (!isIdValid(waypoint))
        {
            waypoint = createWaypointInDatapad(self, RUINS_UNLOCK_LOCATION);
            created = isIdValid(waypoint);
        }
        if (!isIdValid(waypoint))
        {
            return;
        }
        setWaypointName(waypoint, "Dantooine Abandoned Ruins - Jedi Artifact Trial");
        setWaypointColor(waypoint, "yellow");
        setWaypointActive(waypoint, true);
        setWaypointVisible(waypoint, true);
        setObjVar(self, VAR_RUINS_UNLOCK_WAYPOINT, waypoint);
        if (created)
        {
            sendSystemMessage(self, "A new waypoint has been added for your ruins trial on Dantooine.", "");
        }
    }


    private obj_id findWaypointInDatapad(obj_id player, String expectedName, location expectedLocation, float tolerance) throws InterruptedException
    {
        if (!isIdValid(player) || expectedLocation == null)
        {
            return obj_id.NULL_ID;
        }
        obj_id[] waypoints = getWaypointsInDatapad(player);
        if (waypoints == null || waypoints.length == 0)
        {
            return obj_id.NULL_ID;
        }
        for (obj_id waypoint : waypoints)
        {
            if (!isIdValid(waypoint) || !exists(waypoint))
            {
                continue;
            }
            String name = getWaypointName(waypoint);
            if (expectedName != null && expectedName.length() > 0 && (name == null || !expectedName.equals(name)))
            {
                continue;
            }
            location waypointLocation = getWaypointLocation(waypoint);
            if (waypointLocation == null || waypointLocation.area == null || !expectedLocation.area.equals(waypointLocation.area))
            {
                continue;
            }
            if (getDistance(waypointLocation, expectedLocation) <= tolerance)
            {
                return waypoint;
            }
        }
        return obj_id.NULL_ID;
    }

    private String determineAlignment(obj_id self) throws InterruptedException
    {
        if (force_rank.isForceRanked(self))
        {
            int council = force_rank.getCouncilAffiliation(self);
            if (council == force_rank.LIGHT_COUNCIL)
            {
                return "light";
            }
            if (council == force_rank.DARK_COUNCIL)
            {
                return "dark";
            }
        }
        String factionName = factions.getFactionNameByHashCode(pvpGetAlignedFaction(self));
        if (factionName != null)
        {
            if (factionName.equalsIgnoreCase(factions.FACTION_REBEL))
            {
                return "light";
            }
            if (factionName.equalsIgnoreCase(factions.FACTION_IMPERIAL))
            {
                return "dark";
            }
        }
        return "neutral";
    }

    private String resolvePlanet(obj_id self) throws InterruptedException
    {
        script.location here = getLocation(self);
        if (here != null && here.area != null && here.area.length() > 0)
        {
            return here.area;
        }
        return "tatooine";
    }

    private String alignmentToFaction(String alignment)
    {
        if (alignment == null)
        {
            return null;
        }
        if ("dark".equals(alignment))
        {
            return factions.FACTION_IMPERIAL;
        }
        if ("light".equals(alignment))
        {
            return factions.FACTION_REBEL;
        }
        return null;
    }

    private String capitalize(String value)
    {
        if (value == null || value.length() == 0)
        {
            return "Neutral";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void sendStateSummary(obj_id self, String alignment, String phase, float supply) throws InterruptedException
    {
        sendSystemMessage(self, composeStateSummary(alignment, phase, supply), "");
    }

    private String composeStateSummary(String alignment, String phase, float supply)
    {
        StringBuilder header = new StringBuilder();
        header.append("Enclave alignment: ").append(capitalize(alignment));
        header.append(". Campaign phase: ").append(phase != null && phase.length() > 0 ? phase : "Preparation");
        header.append(". Regional supply: ").append(Math.round(supply));
        return header.toString();
    }

    private void showTrialStatus(obj_id self, String[] trials) throws InterruptedException
    {
        String alignment = utils.getStringScriptVar(self, VAR_ALIGNMENT);
        String phase = utils.getStringScriptVar(self, VAR_PHASE);
        float supply = utils.getFloatScriptVar(self, VAR_SUPPLY);
        String summary = composeStateSummary(alignment, phase, supply);
        StringBuilder body = new StringBuilder();
        List<String> displaySections = new ArrayList<>();
        body.append(summary).append("\n\n");
        displaySections.add(summary);
        int totalInfluence = getIntObjVar(self, "enclave.influence");
        String influenceLine = "Total enclave influence earned: " + totalInfluence + ".";
        body.append(influenceLine).append("\n\n");
        displaySections.add(influenceLine);
        int streak = enclave_trials.getDailyStreak(self);
        if (streak > 0)
        {
            String streakLine = "Daily mission streak: " + streak + ".";
            body.append(streakLine).append("\n\n");
            displaySections.add(streakLine);
        }
        TrialDefinition daily = enclave_trials.getAssignedDailyTrial(self);
        if (daily == null)
        {
            String dailyCard = utils.getStringScriptVar(self, VAR_DAILY_CARD);
            if (dailyCard != null && dailyCard.length() > 0)
            {
                daily = enclave_trials.getTrial(dailyCard);
            }
        }
        boolean hasTrials = trials != null && trials.length > 0;
        if (daily != null)
        {
            int dailyProgress = getIntObjVar(self, "enclave.progress." + daily.cardId);
            int dailyCompletions = getIntObjVar(self, "enclave.history." + daily.cardId);
            String header = "Daily enclave mission:";
            String block = buildTrialLine(daily, dailyProgress, dailyCompletions, true);
            body.append(header).append("\n");
            body.append(block).append("\n\n");
            displaySections.add(header);
            displaySections.add(block);
        }
        if (!hasTrials)
        {
            String noTrials = "No additional enclave trials unlocked. Increase regional supply or await the next phase.";
            body.append(noTrials);
            displaySections.add(noTrials);
            String message = body.toString().trim();
            if (!presentTrialWindow(self, message))
            {
                for (String section : displaySections)
                {
                    if (section != null && section.length() > 0)
                    {
                        sendSystemMessage(self, section, "");
                    }
                }
            }
            return;
        }
        String listHeader = "Available enclave trials:";
        body.append(listHeader).append("\n");
        displaySections.add(listHeader);
        String dailyCardId = daily != null ? daily.cardId : null;
        for (String trialId : trials)
        {
            if (trialId == null)
            {
                continue;
            }
            if (dailyCardId != null && dailyCardId.equals(trialId))
            {
                continue;
            }
            TrialDefinition def = enclave_trials.getTrial(trialId);
            String line;
            if (def == null)
            {
                line = " - " + trialId;
            }
            else
            {
                int progress = getIntObjVar(self, "enclave.progress." + def.cardId);
                int completions = getIntObjVar(self, "enclave.history." + def.cardId);
                line = buildTrialLine(def, progress, completions, false);
            }
            displaySections.add(line);
            body.append(line).append("\n\n");
        }
        String message = body.toString().trim();
        if (!presentTrialWindow(self, message))
        {
            for (String section : displaySections)
            {
                if (section != null && section.length() > 0)
                {
                    sendSystemMessage(self, section, "");
                }
            }
        }
    }

    public int handleTrialStatusClosed(obj_id self, dictionary params) throws InterruptedException
    {
        utils.removeScriptVar(self, VAR_TRIAL_SUI);
        return SCRIPT_CONTINUE;
    }

    private boolean presentTrialWindow(obj_id self, String body) throws InterruptedException
    {
        if (!isIdValid(self))
        {
            return false;
        }
        dismissTrialStatus(self);
        int pid = sui.createSUIPage(sui.SUI_MSGBOX, self, self, "handleTrialStatusClosed");
        if (pid <= 0)
        {
            return false;
        }
        setSUIProperty(pid, sui.MSGBOX_TITLE, sui.PROP_TEXT, "Enclave Trials");
        setSUIProperty(pid, sui.MSGBOX_PROMPT, sui.PROP_TEXT, body);
        sui.msgboxButtonSetup(pid, sui.OK_ONLY);
        sui.showSUIPage(pid);
        utils.setScriptVar(self, VAR_TRIAL_SUI, pid);
        return true;
    }

    private void dismissTrialStatus(obj_id self) throws InterruptedException
    {
        if (!utils.hasScriptVar(self, VAR_TRIAL_SUI))
        {
            return;
        }
        int pid = utils.getIntScriptVar(self, VAR_TRIAL_SUI);
        if (pid > 0)
        {
            sui.closeSUI(self, pid);
        }
        utils.removeScriptVar(self, VAR_TRIAL_SUI);
    }

    private String buildTrialLine(TrialDefinition def, int progress, int completions, boolean highlightDaily)
    {
        StringBuilder line = new StringBuilder();
        line.append(highlightDaily ? " * " : " - ");
        line.append(enclave_trials.formatTrialName(def.cardId));
        line.append(" ").append(progress).append("/").append(def.objectiveTarget);
        if (completions > 0)
        {
            line.append(" (completed ").append(completions).append("x)");
        }
        String requirements = enclave_trials.describeRequirements(def);
        if (requirements != null && requirements.length() > 0)
        {
            line.append("\n   Requirements: ").append(requirements);
        }
        String objective = enclave_trials.describeObjective(def);
        if (objective != null && objective.length() > 0)
        {
            line.append("\n   Objective: ").append(objective);
        }
        String hint = enclave_trials.getHint(def, progress);
        if (hint != null && hint.length() > 0)
        {
            line.append("\n   Hint: ").append(hint);
        }
        String progressStatus = enclave_trials.getProgressStatus(def, progress);
        if (progressStatus != null && progressStatus.length() > 0)
        {
            line.append("\n   Progress: ").append(progressStatus);
        }
        String rewards = enclave_trials.describeRewards(def);
        if (rewards != null && rewards.length() > 0)
        {
            line.append("\n   Rewards: ").append(rewards);
        }
        return line.toString();
    }

}
