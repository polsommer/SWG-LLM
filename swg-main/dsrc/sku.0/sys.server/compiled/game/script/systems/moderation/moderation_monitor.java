package script.systems.moderation;

import script.base_script;
import script.dictionary;
import script.obj_id;

public class moderation_monitor extends base_script {

    private static final float CHECK_INTERVAL = 60.0f;

    public int OnAttach(obj_id self) throws InterruptedException {
        moderation_lib.refreshMuteState(self);
        scheduleNextCheck(self);
        return SCRIPT_CONTINUE;
    }

    public int OnLogin(obj_id self) throws InterruptedException {
        moderation_lib.refreshMuteState(self);
        scheduleNextCheck(self);
        return SCRIPT_CONTINUE;
    }

    public int OnSpeaking(obj_id self, String text) throws InterruptedException {
        return moderation_lib.handleMuteOnSpeak(self, text);
    }

    public int handleModerationMutePoll(obj_id self, dictionary params) throws InterruptedException {
        if (moderation_lib.isMuted(self)) {
            scheduleNextCheck(self);
        } else {
            moderation_lib.refreshMuteState(self);
        }
        return SCRIPT_CONTINUE;
    }

    private void scheduleNextCheck(obj_id self) throws InterruptedException {
        messageTo(self, "handleModerationMutePoll", null, CHECK_INTERVAL, false);
    }
}
