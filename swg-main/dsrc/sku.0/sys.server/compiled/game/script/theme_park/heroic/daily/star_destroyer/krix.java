package script.theme_park.heroic.daily.star_destroyer;

import script.*;
import script.library.chat;
import script.library.prose;
import script.library.trial;
import script.library.utils;

import java.util.Vector;

public class krix extends script.base_script
{
    public krix()
    {
    }
    public static final float[] HEALTH_TRIGGER = 
    {
        0.5f,
        0.10f,
        0.15f,
        0.20f

    };
    public int OnAttach(obj_id self) throws InterruptedException
    {
        trial.setHp(self, 12432);
        return SCRIPT_CONTINUE;
    }
    public int startCombat(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id[] players = trial.getValidTargetsInCell(trial.getTop(self), "secondaryhangar");
        obj_id[] allSpawn = trial.getObjectsInDungeonWithObjVar(trial.getTop(self), "spawn_id");
        obj_id[] guards = getGuardsFromList(allSpawn);
        if (players == null || players.length == 0)
        {
            dictionary dict = trial.getSessionDict(trial.getTop(self));
            dict.put("triggerType", "triggerId");
            dict.put("triggerName", "reset_krix");
            messageTo(trial.getTop(self), "triggerFired", dict, 0.0f, false);
            return SCRIPT_CONTINUE;
        }
        obj_id mainTarget = trial.getClosestValidTarget(self, players);
        for (obj_id player : players) {
            startCombat(self, player);
            if (guards != null && guards.length > 0) {
                for (obj_id guard : guards) {
                    startCombat(guard, player);
                    setHate(guard, player, rand(10, 1000));
                    startCombat(self, player);
                }
            }
        }
        setHate(self, mainTarget, 10000);
        startAttackCycle(self);
        return SCRIPT_CONTINUE;
    }
    public obj_id[] getGuardsFromList(obj_id[] allSpawn) throws InterruptedException
    {
        if (allSpawn == null || allSpawn.length == 0)
        {
            return null;
        }
        Vector guards = new Vector();
        guards.setSize(0);
        for (obj_id obj_id : allSpawn) {
            if ((getStringObjVar(obj_id, "spawn_id")).equals("sd_gren")) {
                if (!isDead(obj_id)) {
                    guards.add(obj_id);
                }
            }
        }
        if (guards == null || guards.size() == 0)
        {
            return null;
        }
        if (guards != null)
        {
            allSpawn = new obj_id[guards.size()];
            guards.toArray(allSpawn);

        }
        return allSpawn;
    }
    public void startAttackCycle(obj_id self) throws InterruptedException
    {
        dictionary dict = trial.getSessionDict(self, "special");
        dict.put("next", 0);
        messageTo(self, "executeFocus", dict, 5.0f, false);
    }
    public int executeFocus(obj_id self, dictionary params) throws InterruptedException
    {
        if (!trial.verifySession(self, params, "special") || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        validateCombat(self);
        obj_id[] players = trial.getValidTargetsInCell(trial.getTop(self), "secondaryhangar");
        obj_id[] allSpawn = trial.getObjectsInDungeonWithObjVar(trial.getTop(self), "spawn_id");
        obj_id[] guards = getGuardsFromList(allSpawn);
        if (players == null || players.length == 0)
        {
            return SCRIPT_CONTINUE;
        }
        
        if (guards == null || guards.length == 0)
        {
            trial.bumpSession(self, "special");
            phaseTwo(self);
            return SCRIPT_CONTINUE;
        }
        obj_id focus = players[rand(0, players.length - 1)];
        for (obj_id player : players) {
            if (isDead(player) || isIncapacitated(player)) {
                continue;
            }
            for (obj_id guard : guards) {
                if (isDead(guard)) {
                    continue;
                }
                messageTo(guard, "resetHate", null, 5.0f, false);
                if (player == focus) {
                    utils.setScriptVar(guard, "focus", focus);
                    setHate(guard, player, 10000.0f);
                } else {
                    setHate(guard, player, 1.0f);
                }
            }
        }
        chat.chat(self, "No, you fools! Kill " + getPlayerName(focus) + "!");
        prose_package pp = new prose_package();
        pp = prose.setStringId(pp, new string_id("spam", "krix_directed_target_spam"));
        pp = prose.setTO(pp, focus);
        utils.sendSystemMessageProse(players, pp);
        messageTo(self, "executeFocus", trial.getSessionDict(self, "special"), 12.0f, false);
        return SCRIPT_CONTINUE;
    }
    public void phaseTwo(obj_id self) throws InterruptedException
    {
        chat.chat(self, "Those useless Imperials, I will do this myself.");
        obj_id[] hateList = getHateList(self);
        utils.sendSystemMessage(hateList, new string_id("spam", "krix_do_myself"));
    }
   
    public int OnCreatureDamaged(obj_id self, obj_id attacker, obj_id weapon, int[] damage) throws InterruptedException
    {
        float max = getMaxHealth(self);
        float current = getHealth(self);
        float ratio = current / max;
        int healthTrigger = getHealthTrigger(self, ratio);
        if (healthTrigger == -1)
        {
            return SCRIPT_CONTINUE;
        }
        dictionary dict = trial.getSessionDict(trial.getTop(self));
        dict.put("triggerType", "triggerId");
        dict.put("triggerName", "hasten_grenadier");
        messageTo(trial.getTop(self), "triggerFired", dict, 0.0f, false);
        return SCRIPT_CONTINUE;
    }
    public int getHealthTrigger(obj_id self, float ratio) throws InterruptedException
    {
        for (int i = 0; i < HEALTH_TRIGGER.length; i++)
        {
            if (utils.hasScriptVar(self, "health_trigger_list." + i))
            {
                continue;
            }
            if (ratio <= HEALTH_TRIGGER[i])
            {
                utils.setScriptVar(self, "health_trigger_list." + i, 1);
                return i;
            }
        }
        return -1;
    }
    public void validateCombat(obj_id self) throws InterruptedException
    {
        obj_id[] players = trial.getValidTargetsInCell(trial.getTop(self), "secondaryhangar");
        if (players == null || players.length == 0)
        {
            dictionary dict = trial.getSessionDict(trial.getTop(self));
            dict.put("triggerType", "triggerId");
            dict.put("triggerName", "reset_krix");
            messageTo(trial.getTop(self), "triggerFired", dict, 0.0f, false);
            return;
        }
        for (obj_id player : players) {
            startCombat(self, player);
        }
    }
}
