package script.creature;

import script.dictionary;
import script.library.chat;
import script.library.groundquests;
import script.library.utils; // Adding a utility library for reusable methods
import script.obj_id;
import script.string_id;

public class dynamic_enemy extends script.base_script {
    public dynamic_enemy() {}

    // Called when the script is attached to the object
    public int OnAttach(obj_id self) throws InterruptedException {
        initiateEnemyBehavior(self);
        return SCRIPT_CONTINUE;
    }

    private void initiateEnemyBehavior(obj_id self) throws InterruptedException {
        dictionary params = new dictionary();
        messageTo(self, "triggerClientEffect", params, 500, true); // Adds slight randomness to timing
        messageTo(self, "startAttackCycle", params, 1, false);
    }

    public int OnIncapacitateTarget(obj_id self, obj_id victim) throws InterruptedException {
        if (!isTargetPlayer(self, victim)) {
            return SCRIPT_CONTINUE;
        }
        executeVictoryBehavior(self);
        return SCRIPT_CONTINUE;
    }

    private boolean isTargetPlayer(obj_id self, obj_id victim) throws InterruptedException {
        obj_id player = getObjIdObjVar(self, "player");
        return isValidId(player) && exists(player) && player == victim;
    }

    private void executeVictoryBehavior(obj_id self) throws InterruptedException {
        dictionary params = new dictionary();
        messageTo(self, "showExpression", params, 3, true);
    }

    public int OnLoiterMoving(obj_id self) throws InterruptedException {
        dictionary params = new dictionary(); // Create an empty dictionary
        triggerClientEffect(self, params);    // Pass the required parameters
        return SCRIPT_CONTINUE;
    }

    public int triggerClientEffect(obj_id self, dictionary params) throws InterruptedException {
        if (!hasObjVar(self, "clientEffect")) {
            dictionary cleanUpParams = new dictionary();
            messageTo(self, "cleanUp", cleanUpParams, 0, true);
        } else {
            String effect = getStringObjVar(self, "clientEffect");
            playClientEffectObj(self, effect, self, "");
            dictionary cleanUpParams = new dictionary();
            messageTo(self, "cleanUp", cleanUpParams, 1, true);
        }
        return SCRIPT_CONTINUE;
    }

    public int cleanUp(obj_id self, dictionary params) throws InterruptedException {
        destroyObject(self);
        return SCRIPT_CONTINUE;
    }

    public int showExpression(obj_id self, dictionary params) throws InterruptedException {
        obj_id player = getObjIdObjVar(self, "player");
        if (!isValidId(player) || !exists(player)) {
            return SCRIPT_CONTINUE;
        }

        faceTo(self, player);
        bark("defeat_phrase", self);
        pathTo(self, getLocation(player));
        dictionary runAwayParams = new dictionary();
        messageTo(self, "runAwayAndDisappear", runAwayParams, 5, false);
        return SCRIPT_CONTINUE;
    }

    public int runAwayAndDisappear(obj_id self, dictionary params) throws InterruptedException {
        pathTo(self, groundquests.getRandom2DLocationAroundPlayer(self, 20, 50));
        dictionary effectParams = new dictionary();
        messageTo(self, "triggerClientEffect", effectParams, 8, true);
        return SCRIPT_CONTINUE;
    }

    public int startAttackCycle(obj_id self, dictionary params) throws InterruptedException {
        obj_id player = getObjIdObjVar(self, "player");
        if (!isValidId(player) || !exists(player)) {
            return SCRIPT_CONTINUE;
        }

        startCombat(self, player);
        bark("attack_phrase", self);
        return SCRIPT_CONTINUE;
    }

    private void bark(String phraseVar, obj_id self) throws InterruptedException {
        if (!hasObjVar(self, "phrase_string_file")) {
            return;
        }
        String phrase = getStringObjVar(self, phraseVar);
        if (phrase != null && !phrase.isEmpty()) {
            chat.chat(self, chat.CHAT_SHOUT, chat.MOOD_ANGRY, new string_id(getStringObjVar(self, "phrase_string_file"), phrase));
        }
    }

    public int OnDestroy(obj_id self) throws InterruptedException {
        cleanupOnDestroy(self);
        return SCRIPT_CONTINUE;
    }

    private void cleanupOnDestroy(obj_id self) throws InterruptedException {
        if (exists(self)) {
            destroyObject(self);
        }
    }
}

