package script.turnstile;

import script.dictionary;
import script.library.turnstile;
import script.obj_id;
import script.base_script;  // Ensure correct import for base_script

public class turnstile_cleanup extends base_script {

    // Handler name for expired cleanup
    public static final String HANDLER_EXPIRED_CLEANUP = "handleExpiredCleanup";

    public int OnInitialize(obj_id self) throws InterruptedException {
        scheduleCleanup(self);
        return SCRIPT_CONTINUE;
    }

    public int OnAttach(obj_id self) throws InterruptedException {
        scheduleCleanup(self);
        return SCRIPT_CONTINUE;
    }

    public int OnDetach(obj_id self) throws InterruptedException {
        return SCRIPT_CONTINUE;
    }

    /**
     * Schedules the cleanup task by sending a message to the handler.
     */
    private void scheduleCleanup(obj_id self) {
        if (isIdValid(self)) {
            messageTo(self, HANDLER_EXPIRED_CLEANUP, null, turnstile.TURNSTILE_CLEANUP_HEARTBEAT, false);
        } else {
            debugServerConsoleMsg(self, "Invalid obj_id provided for scheduling cleanup.");
        }
    }

    /**
     * Handles expired cleanup of patrons and reschedules itself.
     */
    public int handleExpiredCleanup(obj_id self, dictionary params) throws InterruptedException {
        try {
            turnstile.cleanupExpiredPatrons(self);
        } catch (Exception e) {
            debugServerConsoleMsg(self, "Error during expired cleanup: " + e.getMessage());
        }

        // Reschedule the next cleanup
        scheduleCleanup(self);
        return SCRIPT_CONTINUE;
    }
}

