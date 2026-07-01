package script.ai;

import script.dictionary;
import script.conversation.townperson_social_dispatcher;
import script.library.*;
import script.location;
import script.obj_id;

public class townperson extends script.base_script
{
    public townperson()
    {
    }
    public static final String ALERT_VOLUME_NAME = "alertTriggerVolume";
    public static final String SOCIAL_VOLUME = "npc_socialization";
    public static final float SOCIAL_RANGE = 15.0f;
    public static final String ACTION_ALERT = "alert";
    public static final String ACTION_THREATEN = "threaten";
    public static final int CONVO_LENGTH = 300;
    public static final String CREATURE_TABLE = "datatables/mob/creatures.iff";
    public static final String MESSAGE_BRAIN_TICK = "handleLongHorizonBrainTick";
    public static final String MESSAGE_BRAIN_COMMAND = "handleBrainCommand";
    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (getConfigSetting("GameServer", "disableAITriggerVolumes") == null)
        {
            createTriggerVolume(SOCIAL_VOLUME, SOCIAL_RANGE, false);
        }
        String diction = "townperson";
        switch (rand(1, 3))
        {
            case 1:
            diction = "townperson_fancy";
            break;
            case 2:
            diction = "townperson_slang";
            break;
            case 3:
            diction = "townperson";
            break;
        }
        setObjVar(self, "ai.diction", diction);
        npc_player_brain.initialize(self, "civilian", 6);
        npc_identity_assets.applyIdentityAssets(self);
        messageTo(self, MESSAGE_BRAIN_TICK, null, rand(3.0f, 8.0f), false);
        factions.setFaction(self, "townperson");
        switch (rand(1, 5))
        {
            case 1:
            factions.setFaction(self, "ImperialCitizen");
            break;
            case 2:
            factions.setFaction(self, "RebelCitizen");
            break;
        }
        attachScript(self, "theme_park.tatooine.city_convo");
        return SCRIPT_CONTINUE;
    }
    public int OnAddedToWorld(obj_id self) throws InterruptedException
    {
        utils.removeScriptVar(self, "ai.speaking");
        if (getConfigSetting("GameServer", "disableAITriggerVolumes") == null)
        {
            createTriggerVolume(SOCIAL_VOLUME, SOCIAL_RANGE, false);
        }
        setAttributeAttained(self, attrib.TOWNSPERSON);
        setAttributeInterested(self, attrib.TOWNSPERSON);
        npc_player_brain.initialize(self, "civilian", 6);
        npc_identity_assets.applyIdentityAssets(self);
        if (!hasMessageTo(self, MESSAGE_BRAIN_TICK))
        {
            messageTo(self, MESSAGE_BRAIN_TICK, null, rand(4.0f, 9.0f), false);
        }
        return SCRIPT_CONTINUE;
    }
    public int handleLongHorizonBrainTick(obj_id self, dictionary params) throws InterruptedException
    {
        if (!exists(self) || isIncapacitated(self))
        {
            return SCRIPT_CONTINUE;
        }
        if (ai_lib.isInCombat(self))
        {
            messageTo(self, MESSAGE_BRAIN_TICK, null, rand(6.0f, 10.0f), false);
            return SCRIPT_CONTINUE;
        }
        if (npc_player_planner.shouldRefreshPlan(self))
        {
            npc_player_planner.refreshPlan(self);
        }
        dictionary command = npc_player_planner.dequeueNextStep(self);
        messageTo(self, MESSAGE_BRAIN_COMMAND, command, 0.0f, false);
        messageTo(self, MESSAGE_BRAIN_TICK, null, rand(7.0f, 13.0f), false);
        return SCRIPT_CONTINUE;
    }
    public int handleBrainCommand(obj_id self, dictionary params) throws InterruptedException
    {
        if (params == null || !params.containsKey("action"))
        {
            return SCRIPT_CONTINUE;
        }
        String action = params.getString("action");
        String goal = params.containsKey("goal") ? params.getString("goal") : "";
        obj_id target = params.containsKey("target") ? params.getObjId("target") : obj_id.NULL_ID;
        location dest = params.containsKey("dest") ? params.getLocation("dest") : null;
        if (action.equals(npc_player_brain.COMMAND_MOVE) && dest != null)
        {
            pathTo(self, dest);
            npc_player_brain.recordProgress(self, "pathing_" + goal);
            return SCRIPT_CONTINUE;
        }
        if (action.equals(npc_player_brain.COMMAND_INTERACT) && isIdValid(target) && exists(target))
        {
            faceTo(self, target);
            ai_lib.doAction(self, "manipulate_medium");
            npc_player_brain.recordProgress(self, "interacting_" + goal);
            npc_social_memory.noteEvent(self, "trade");
            npc_player_brain.reportOutcomeEvent(self, true, "mission_success", 8.0f);
            return SCRIPT_CONTINUE;
        }
        if (action.equals(npc_player_brain.COMMAND_ATTACK) && isIdValid(target) && exists(target))
        {
            if (npc_social_memory.isHostileActor(self, target))
            {
                npc_player_brain.recordProgress(self, "avoiding_hostile_actor");
                pathTo(self, utils.getRandomLocationInRing(getLocation(self), 16.0f, 26.0f));
                npc_player_brain.reportOutcomeEvent(self, true, "mission_success", 3.0f);
                return SCRIPT_CONTINUE;
            }
            startCombat(self, target);
            npc_player_brain.recordProgress(self, "engaging_combat");
            npc_social_memory.noteEvent(self, "combat");
            npc_player_brain.reportOutcomeEvent(self, true, "mission_success", 10.0f);
            return SCRIPT_CONTINUE;
        }
        if (action.equals(npc_player_brain.COMMAND_TALK) && isIdValid(target) && exists(target) && hasScript(target, "ai.townperson"))
        {
            initiateDialog(self, target);
            npc_player_brain.recordProgress(self, "socializing");
            npc_player_brain.reportOutcomeEvent(self, true, "mission_success", 4.0f);
            return SCRIPT_CONTINUE;
        }
        if (action.equals(npc_player_brain.COMMAND_TALK) && isIdValid(target) && exists(target) && isPlayer(target))
        {
            faceTo(self, target);
            String currentGoal = hasObjVar(self, npc_player_brain.OBJVAR_CURRENT_GOAL) ? getStringObjVar(self, npc_player_brain.OBJVAR_CURRENT_GOAL) : goal;
            boolean dispatched = townperson_social_dispatcher.handlePlayerInteraction(self, target, currentGoal);
            if (dispatched)
            {
                npc_player_brain.recordProgress(self, "socializing_player_rich");
                npc_player_brain.reportOutcomeEvent(self, true, "mission_success", 6.0f);
            }
            else
            {
                npc_player_brain.recordProgress(self, "socializing_player_cooldown");
            }
            return SCRIPT_CONTINUE;
        }
        if (dest != null)
        {
            pathTo(self, dest);
            npc_player_brain.recordProgress(self, "fallback_move");
        }
        return SCRIPT_CONTINUE;
    }
    public int OnTriggerVolumeEntered(obj_id self, String volumeName, obj_id breacher) throws InterruptedException
    {
        if (hasObjVar(breacher, "gm"))
        {
            return SCRIPT_CONTINUE;
        }
        if (breacher == self)
        {
            return SCRIPT_CONTINUE;
        }
        if (isIncapacitated(self))
        {
            return SCRIPT_CONTINUE;
        }
        if (volumeName.equals(ALERT_VOLUME_NAME))
        {
            if (hasScript(breacher, "ai.townperson"))
            {
                addTriggerVolumeEventSource(SOCIAL_VOLUME, breacher);
            }
            return SCRIPT_CONTINUE;
        }
        if (volumeName.equals(SOCIAL_VOLUME))
        {
            if (isPlayer(breacher))
            {
                if (npc_social_memory.canStartInteraction(self, breacher, npc_social_memory.PLAYER_INTERACTION_COOLDOWN))
                {
                    dictionary command = new dictionary();
                    command.put("goal", npc_player_brain.GOAL_SOCIAL);
                    command.put("subgoal", "social_player");
                    command.put("action", npc_player_brain.COMMAND_TALK);
                    command.put("target", breacher);
                    command.put("dest", getLocation(self));
                    messageTo(self, MESSAGE_BRAIN_COMMAND, command, 1.0f, false);
                }
            }
            else
            {
                initiateDialog(self, breacher);
            }
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_CONTINUE;
    }
    public int OnTriggerVolumeExited(obj_id self, String volumeName, obj_id breacher) throws InterruptedException
    {
        if (volumeName.equals(ALERT_VOLUME_NAME))
        {
            if (hasScript(breacher, "ai.townperson"))
            {
                removeTriggerVolumeEventSource(SOCIAL_VOLUME, breacher);
            }
            return SCRIPT_CONTINUE;
        }
        return SCRIPT_CONTINUE;
    }
    public int cancelFacing(obj_id self, dictionary params) throws InterruptedException
    {
        utils.removeScriptVar(self, "ai.speaking");
        if (getBehavior(self) == BEHAVIOR_CALM)
        {
            messageTo(self, "resumeDefaultCalmBehavior", null, 5, false);
        }
        return SCRIPT_CONTINUE;
    }
    public void initiateDialog(obj_id talker, obj_id listener) throws InterruptedException
    {
        if (ai_lib.isInCombat(talker) || ai_lib.isInCombat(listener))
        {
            return;
        }
        if (ai_lib.isFollowing(talker) || ai_lib.isFollowing(listener))
        {
            return;
        }
        aiUnEquipWeapons(talker);
        aiUnEquipWeapons(listener);
        if (utils.hasScriptVar(talker, "ai.speaking") || utils.hasScriptVar(listener, "ai.speaking"))
        {
            return;
        }
        if (getBehavior(talker) >= BEHAVIOR_THREATEN || getBehavior(listener) >= BEHAVIOR_THREATEN)
        {
            return;
        }
        if (getIntObjVar(listener, "ai.defaultCalmBehavior") == ai_lib.BEHAVIOR_SENTINEL || getIntObjVar(talker, "ai.defaultCalmBehavior") == ai_lib.BEHAVIOR_SENTINEL)
        {
            return;
        }
        utils.setScriptVar(talker, "ai.speaking", listener);
        utils.setScriptVar(listener, "ai.speaking", talker);
        stop(talker);
        stop(listener);
        faceTo(listener, talker);
        utils.setScriptVar(talker, "ai.pathingToSocialize", listener);
        utils.setScriptVar(listener, "ai.pathingToSocialize", talker);
        location pathToLoc = new location(getLocation(listener));
        location myLoc = getLocation(talker);
        if (pathToLoc.x < myLoc.x)
        {
            pathToLoc.x += 1.5f;
        }
        else 
        {
            pathToLoc.x -= 1.5f;
        }
        if (pathToLoc.z < myLoc.z)
        {
            pathToLoc.z += 1.5f;
        }
        else 
        {
            pathToLoc.z -= 1.5f;
        }
        pathTo(talker, pathToLoc);
    }
    public int OnMovePathComplete(obj_id self) throws InterruptedException
    {
        if (!utils.hasScriptVar(self, "ai.pathingToSocialize"))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id listener = utils.getObjIdScriptVar(self, "ai.pathingToSocialize");
        if (exists(listener))
        {
            faceTo(self, listener);
            faceTo(listener, self);
            ai_lib.greet(self, listener);
            ai_lib.doAction(self, "conversation_" + getGender(self));
            ai_lib.doAction(listener, "conversation_" + getGender(listener));
        }
        messageTo(self, "handleEndSocializing", null, CONVO_LENGTH, isObjectPersisted(self));
        return SCRIPT_CONTINUE;
    }
    public int handleEndSocializing(obj_id self, dictionary params) throws InterruptedException
    {
        if (!utils.hasScriptVar(self, "ai.pathingToSocialize"))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id listener = utils.getObjIdScriptVar(self, "ai.pathingToSocialize");
        endSocializing(self, listener);
        return SCRIPT_CONTINUE;
    }
    public int OnMovePathNotFound(obj_id self) throws InterruptedException
    {
        npc_player_brain.reportOutcomeEvent(self, false, "path_fail", -6.0f);
        if (!utils.hasScriptVar(self, "ai.pathingToSocialize"))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id listener = utils.getObjIdScriptVar(self, "ai.pathingToSocialize");
        endSocializing(self, listener);
        return SCRIPT_CONTINUE;
    }
    public void endSocializing(obj_id talker, obj_id listener) throws InterruptedException
    {
        if (getBehavior(talker) == BEHAVIOR_CALM)
        {
            ai_lib.setMood(talker, ai_lib.MOOD_CALM);
            ai_lib.dismiss(talker, listener);
            messageTo(talker, "resumeDefaultCalmBehavior", null, 5, false);
        }
        if (getBehavior(listener) == BEHAVIOR_CALM)
        {
            ai_lib.setMood(listener, ai_lib.MOOD_CALM);
            ai_lib.doAction(listener, "wave" + rand(1, 2));
            messageTo(listener, "resumeDefaultCalmBehavior", null, 8, false);
        }
        utils.removeScriptVar(talker, "ai.pathingToSocialize");
        utils.removeScriptVar(listener, "ai.pathingToSocialize");
        utils.removeScriptVar(talker, "ai.speaking");
        utils.removeScriptVar(listener, "ai.speaking");
    }
    public int OnBehaviorChange(obj_id self, int newBehavior, int oldBehavior, int[] changeFlags) throws InterruptedException
    {
        if (isIncapacitated(self))
        {
            return SCRIPT_CONTINUE;
        }
        if (newBehavior > oldBehavior)
        {
            if (utils.hasScriptVar(self, "ai.pathingToSocialize"))
            {
                obj_id listener = utils.getObjIdScriptVar(self, "ai.pathingToSocialize");
                endSocializing(self, listener);
            }
            if (newBehavior >= BEHAVIOR_ALERT && newBehavior < BEHAVIOR_ATTACK)
            {
                doAgitateBehavior(self, newBehavior);
                return SCRIPT_OVERRIDE;
            }
            else 
            {
            }
            return SCRIPT_CONTINUE;
        }
        else if (newBehavior == BEHAVIOR_CALM)
        {
            if (utils.hasScriptVar(self, "ai.speaking"))
            {
                ai_lib.setMood(self, ai_lib.MOOD_CALM);
                return SCRIPT_OVERRIDE;
            }
            if (oldBehavior != BEHAVIOR_CALM)
            {
                chat.setNeutralMood(self);
                ai_lib.barkString(self, "calm");
            }
        }
        return SCRIPT_CONTINUE;
    }
    public void doAgitateBehavior(obj_id npc, int behavior) throws InterruptedException
    {
        if (isInvulnerable(npc))
        {
            return;
        }
        return;
    }
    public int OnTargeted(obj_id self, obj_id attacker) throws InterruptedException
    {
        if (isInvulnerable(self))
        {
            return SCRIPT_OVERRIDE;
        }
        npc_player_brain.reportOutcomeEvent(self, false, "death", -12.0f);
        addToMentalStateToward(self, attacker, FEAR, 100.0f);
        return SCRIPT_OVERRIDE;
    }
    public int OnEnteredCombat(obj_id self) throws InterruptedException
    {
        if (isInvulnerable(self))
        {
            return SCRIPT_OVERRIDE;
        }
        return SCRIPT_OVERRIDE;
    }
    public int OnStartNpcConversation(obj_id self, obj_id speaker) throws InterruptedException
    {
        if (isPlayer(speaker))
        {
            String goal = hasObjVar(self, npc_player_brain.OBJVAR_CURRENT_GOAL) ? getStringObjVar(self, npc_player_brain.OBJVAR_CURRENT_GOAL) : npc_player_brain.GOAL_SOCIAL;
            townperson_social_dispatcher.handlePlayerInteraction(self, speaker, goal);
        }
        if (utils.hasScriptVar(self, "ai.speaking"))
        {
            obj_id listener = utils.getObjIdScriptVar(self, "ai.speaking");
            if (listener != speaker)
            {
                endSocializing(self, listener);
            }
        }
        utils.setScriptVar(self, "ai.speaking", speaker);
        return SCRIPT_CONTINUE;
    }
    public int OnEndNpcConversation(obj_id self, obj_id speaker) throws InterruptedException
    {
        utils.removeScriptVar(self, "ai.speaking");
        if (getBehavior(self) == BEHAVIOR_CALM)
        {
            messageTo(self, "resumeDefaultCalmBehavior", null, 5, false);
        }
        return SCRIPT_CONTINUE;
    }
    public int resumeDefaultCalmBehavior(obj_id self, dictionary params) throws InterruptedException
    {
        if (utils.hasScriptVar(self, "ai.speaking"))
        {
            return SCRIPT_OVERRIDE;
        }
        if (getBehavior(self) == BEHAVIOR_CALM)
        {
            aiUnEquipWeapons(self);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnSawAttack(obj_id self, obj_id defender, obj_id[] attackers) throws InterruptedException
    {
        if (getConfigSetting("GameServer", "disableAICombat") != null)
        {
            setWantSawAttackTriggers(self, false);
            return SCRIPT_OVERRIDE;
        }
        if (ai_lib.isAiDead(self) || isInvulnerable(self))
        {
            setWantSawAttackTriggers(self, false);
            return SCRIPT_OVERRIDE;
        }
        if (!utils.hasScriptVar(self, "ai.pathingToSocialize"))
        {
            npc_social_memory.noteEvent(self, "combat");
            if (isIdValid(defender) && defender != self)
            {
                npc_social_memory.recordInteraction(self, defender, "combat", -3, true);
            }
            return SCRIPT_CONTINUE;
        }
        setAnimationMood(self, "nervous");
        obj_id listener = utils.getObjIdScriptVar(self, "ai.pathingToSocialize");
        endSocializing(self, listener);
        return SCRIPT_CONTINUE;
    }
}
