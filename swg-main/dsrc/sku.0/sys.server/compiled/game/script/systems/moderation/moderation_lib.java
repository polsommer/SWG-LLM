package script.systems.moderation;

import java.util.Vector;

import script.base_script;
import script.library.utils;
import script.obj_id;
import script.discord.ModerationWebhook;

public class moderation_lib extends base_script {

    private static final String OBJVAR_WARNINGS = "moderation.warnings";
    private static final String OBJVAR_NOTES = "moderation.notes";
    private static final String OBJVAR_MUTE_UNTIL = "moderation.mute.expires";
    private static final String OBJVAR_MUTE_REASON = "moderation.mute.reason";
    private static final String OBJVAR_MUTE_ADMIN = "moderation.mute.admin";
    private static final String VAR_LAST_NOTIFY = "moderation.mute.lastNotify";
    public static final String MONITOR_SCRIPT = "systems.moderation.moderation_monitor";
    private static final int NOTIFY_INTERVAL = 15;

    private moderation_lib() {
    }

    public static void addWarning(obj_id admin, obj_id target, String reason) throws InterruptedException {
        String sanitized = sanitize(reason);
        appendPersistentEntry(target, OBJVAR_WARNINGS, buildEntry(admin, sanitized));
        sendSystemMessage(target, "You have received a warning: " + sanitized, "");
        sendSystemMessageTestingOnly(admin, "Warned " + getNameSafe(target) + ".");
        CustomerServiceLog("Moderation", buildLog(admin, target, "WARNING", sanitized));
        ModerationWebhook.sendModerationAction("Warning", admin, getActorName(admin), target, sanitized, 0);
    }

    public static void addNote(obj_id admin, obj_id target, String note) throws InterruptedException {
        String sanitized = sanitize(note);
        appendPersistentEntry(target, OBJVAR_NOTES, buildEntry(admin, sanitized));
        sendSystemMessageTestingOnly(admin, "Added note for " + getNameSafe(target) + ".");
        CustomerServiceLog("Moderation", buildLog(admin, target, "NOTE", sanitized));
        ModerationWebhook.sendStaffNote(admin, getActorName(admin), target, sanitized);
    }

    public static void applyMute(obj_id admin, obj_id target, int minutes, String reason) throws InterruptedException {
        int clampedMinutes = Math.max(1, minutes);
        int durationSeconds = clampedMinutes * 60;
        String sanitized = sanitize(reason);
        setObjVar(target, OBJVAR_MUTE_UNTIL, getGameTime() + durationSeconds);
        setObjVar(target, OBJVAR_MUTE_REASON, sanitized);
        setObjVar(target, OBJVAR_MUTE_ADMIN, getActorName(admin));
        utils.removeScriptVar(target, VAR_LAST_NOTIFY);
        appendPersistentEntry(target, OBJVAR_NOTES, buildEntry(admin, "Muted for " + clampedMinutes + " minute(s): " + sanitized));
        refreshMuteState(target);
        sendSystemMessage(target, "You have been muted for " + clampedMinutes + " minute(s). Reason: " + sanitized, "");
        sendSystemMessageTestingOnly(admin, "Muted " + getNameSafe(target) + " for " + clampedMinutes + " minute(s).");
        CustomerServiceLog("Moderation", buildLog(admin, target, "MUTE", sanitized + " (" + clampedMinutes + "m)"));
        ModerationWebhook.sendModerationAction("Mute", admin, getActorName(admin), target, sanitized, durationSeconds);
    }

    public static void clearMute(obj_id admin, obj_id target, String reason) throws InterruptedException {
        boolean wasMuted = hasObjVar(target, OBJVAR_MUTE_UNTIL);
        String sanitized = sanitize(reason);
        removeMuteData(target);
        refreshMuteState(target);
        if (wasMuted) {
            sendSystemMessage(target, "Your mute has been lifted.", "");
            sendSystemMessageTestingOnly(admin, "Unmuted " + getNameSafe(target) + ".");
            CustomerServiceLog("Moderation", buildLog(admin, target, "UNMUTE", sanitized));
            ModerationWebhook.sendModerationAction("Unmute", admin, getActorName(admin), target, sanitized, 0);
        }
    }

    public static boolean isMuted(obj_id target) throws InterruptedException {
        if (!hasObjVar(target, OBJVAR_MUTE_UNTIL)) {
            return false;
        }
        int expires = getIntObjVar(target, OBJVAR_MUTE_UNTIL);
        if (expires <= getGameTime()) {
            handleMuteExpiry(target);
            return false;
        }
        return true;
    }

    public static int handleMuteOnSpeak(obj_id target, String text) throws InterruptedException {
        if (!isMuted(target)) {
            refreshMuteState(target);
            return SCRIPT_CONTINUE;
        }
        int expires = getIntObjVar(target, OBJVAR_MUTE_UNTIL);
        int remaining = expires - getGameTime();
        if (remaining <= 0) {
            refreshMuteState(target);
            return SCRIPT_CONTINUE;
        }
        int lastNotify = utils.getIntScriptVar(target, VAR_LAST_NOTIFY);
        if (getGameTime() - lastNotify >= NOTIFY_INTERVAL) {
            utils.setScriptVar(target, VAR_LAST_NOTIFY, getGameTime());
            String reason = hasObjVar(target, OBJVAR_MUTE_REASON) ? getStringObjVar(target, OBJVAR_MUTE_REASON) : "No reason recorded.";
            sendSystemMessage(target, "You are muted for another " + utils.formatTimeVerbose(remaining) + ". Reason: " + reason, "");
        }
        return SCRIPT_OVERRIDE;
    }

    public static void refreshMuteState(obj_id target) throws InterruptedException {
        if (isMuted(target)) {
            if (!hasScript(target, MONITOR_SCRIPT)) {
                attachScript(target, MONITOR_SCRIPT);
            }
        } else {
            removeMuteData(target);
            if (hasScript(target, MONITOR_SCRIPT)) {
                detachScript(target, MONITOR_SCRIPT);
            }
        }
    }

    public static void sendHistoryToAdmin(obj_id admin, obj_id target) throws InterruptedException {
        sendSystemMessageTestingOnly(admin, "---- Moderation history for " + getNameSafe(target) + " ----");
        sendEntryList(admin, "Warnings", getStringArrayObjVar(target, OBJVAR_WARNINGS));
        sendEntryList(admin, "Notes", getStringArrayObjVar(target, OBJVAR_NOTES));
        if (isMuted(target)) {
            int expires = getIntObjVar(target, OBJVAR_MUTE_UNTIL);
            int remaining = Math.max(0, expires - getGameTime());
            String reason = hasObjVar(target, OBJVAR_MUTE_REASON) ? getStringObjVar(target, OBJVAR_MUTE_REASON) : "No reason recorded.";
            String adminName = hasObjVar(target, OBJVAR_MUTE_ADMIN) ? getStringObjVar(target, OBJVAR_MUTE_ADMIN) : "Unknown";
            sendSystemMessageTestingOnly(admin, "Active mute: " + utils.formatTimeVerbose(remaining) + " remaining. Reason: " + reason + ". By: " + adminName);
        } else {
            sendSystemMessageTestingOnly(admin, "Active mute: none.");
        }
    }

    private static void sendEntryList(obj_id admin, String title, String[] entries) throws InterruptedException {
        if (entries == null || entries.length == 0) {
            sendSystemMessageTestingOnly(admin, title + ": none.");
            return;
        }
        sendSystemMessageTestingOnly(admin, title + ":");
        for (String entry : entries) {
            sendSystemMessageTestingOnly(admin, " - " + formatEntry(entry));
        }
    }

    private static void appendPersistentEntry(obj_id target, String objvar, String entry) throws InterruptedException {
        Vector data = new Vector();
        String[] existing = getStringArrayObjVar(target, objvar);
        if (existing != null) {
            for (String value : existing) {
                data = utils.addElement(data, value);
            }
        }
        data = utils.addElement(data, entry);
        setObjVar(target, objvar, data, resizeableArrayTypestring);
    }

    private static void handleMuteExpiry(obj_id target) throws InterruptedException {
        if (!hasObjVar(target, OBJVAR_MUTE_UNTIL)) {
            return;
        }
        String reason = hasObjVar(target, OBJVAR_MUTE_REASON) ? getStringObjVar(target, OBJVAR_MUTE_REASON) : "Mute expired.";
        String adminName = hasObjVar(target, OBJVAR_MUTE_ADMIN) ? getStringObjVar(target, OBJVAR_MUTE_ADMIN) : "Unknown";
        removeMuteData(target);
        sendSystemMessage(target, "Your mute has expired.", "");
        CustomerServiceLog("Moderation", buildLog(obj_id.NULL_ID, target, "MUTE_EXPIRED", reason));
        ModerationWebhook.sendModerationAction("Mute expired", obj_id.NULL_ID, adminName, target, reason, 0);
    }

    private static void removeMuteData(obj_id target) throws InterruptedException {
        if (hasObjVar(target, OBJVAR_MUTE_UNTIL)) {
            removeObjVar(target, OBJVAR_MUTE_UNTIL);
        }
        if (hasObjVar(target, OBJVAR_MUTE_REASON)) {
            removeObjVar(target, OBJVAR_MUTE_REASON);
        }
        if (hasObjVar(target, OBJVAR_MUTE_ADMIN)) {
            removeObjVar(target, OBJVAR_MUTE_ADMIN);
        }
        utils.removeScriptVar(target, VAR_LAST_NOTIFY);
    }

    private static String sanitize(String reason) {
        if (reason == null || reason.trim().length() == 0) {
            return "No reason provided.";
        }
        return reason.trim();
    }

    private static String buildEntry(obj_id admin, String text) throws InterruptedException {
        return currentTimestamp() + "|" + getActorName(admin) + "|" + text;
    }

    private static String formatEntry(String entry) {
        if (entry == null) {
            return "";
        }
        String[] parts = entry.split("\\|", 3);
        if (parts.length < 3) {
            return entry;
        }
        return "[" + parts[0] + "] " + parts[1] + ": " + parts[2];
    }

    private static String getNameSafe(obj_id id) throws InterruptedException {
        if (!isIdValid(id)) {
            return "Unknown";
        }
        String first = getFirstName(id);
        if (first != null && first.length() > 0) {
            return first;
        }
        String name = getName(id);
        return name != null ? name : String.valueOf(id);
    }

    private static String getActorName(obj_id admin) throws InterruptedException {
        if (!isIdValid(admin)) {
            return "System";
        }
        String first = getFirstName(admin);
        if (first != null && first.length() > 0) {
            return first;
        }
        String name = getName(admin);
        return name != null ? name : String.valueOf(admin);
    }

    private static String currentTimestamp() throws InterruptedException {
        return getCalendarTimeStringLocal_YYYYMMDDHHMMSS(getCalendarTime());
    }

    private static String buildLog(obj_id admin, obj_id target, String action, String details) throws InterruptedException {
        return "[" + action + "] Admin=" + getActorName(admin) + " Target=" + getNameSafe(target) + " (" + target + ") Details=" + details;
    }
}
