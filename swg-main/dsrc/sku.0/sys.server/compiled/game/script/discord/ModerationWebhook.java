package script.discord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import script.base_script;
import script.obj_id;

public class ModerationWebhook extends base_script {

    private static final String CONFIG_KEY = "moderationWebhook";

    public ModerationWebhook() {
    }

    public static void sendPlainMessage(String message) {
        if (message == null || message.length() == 0) {
            return;
        }
        if (!DiscordWebhookClient.sendPlainMessage(CONFIG_KEY, message)) {
            debugServerConsoleMsg(null, "ModerationWebhook: failed to dispatch webhook for key '" + CONFIG_KEY + "'.");
        }
    }

    public static void sendModerationAction(String action, obj_id admin, String adminName, obj_id target, String reason,
                                            long durationSeconds) throws InterruptedException {
        String resolvedAction = (action == null || action.length() == 0) ? "Moderation" : action;
        String resolvedReason = (reason == null || reason.length() == 0) ? "No reason provided." : reason;
        List<String> fields = new ArrayList<String>();
        fields.add(makeField("Action", resolvedAction, true));
        fields.add(makeField("Target", formatIdentity(target), true));
        fields.add(makeField("Actor", resolveActor(admin, adminName), true));
        if (durationSeconds > 0) {
            fields.add(makeField("Duration", formatDuration(durationSeconds), true));
        }
        fields.add(makeField("Reason", trimToLength(resolvedReason, 1024), false));
        StringBuilder payload = new StringBuilder();
        payload.append("{\"embeds\":[{\"title\":\"")
               .append(DiscordWebhookClient.escape(resolvedAction))
               .append("\",\"color\":")
               .append(colorForAction(resolvedAction))
               .append(",\"timestamp\":\"")
               .append(Instant.now().toString())
               .append("\",\"fields\":[");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                payload.append(',');
            }
            payload.append(fields.get(i));
        }
        payload.append("]}]}");
        postJson(payload.toString());
    }

    public static void sendStaffNote(obj_id admin, String adminName, obj_id target, String note)
            throws InterruptedException {
        if (note == null || note.length() == 0) {
            return;
        }
        List<String> fields = new ArrayList<String>();
        fields.add(makeField("Target", formatIdentity(target), true));
        fields.add(makeField("Author", resolveActor(admin, adminName), true));
        fields.add(makeField("Note", trimToLength(note, 1024), false));
        StringBuilder payload = new StringBuilder();
        payload.append("{\"embeds\":[{\"title\":\"Staff Note\",\"color\":")
               .append(COLOR_NOTE)
               .append(",\"timestamp\":\"")
               .append(Instant.now().toString())
               .append("\",\"fields\":[");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                payload.append(',');
            }
            payload.append(fields.get(i));
        }
        payload.append("]}]}");
        postJson(payload.toString());
    }

    private static String resolveActor(obj_id admin, String adminName) throws InterruptedException {
        if (isIdValid(admin)) {
            return formatIdentity(admin);
        }
        if (adminName != null && adminName.length() > 0) {
            return DiscordWebhookClient.escape(adminName);
        }
        return "System";
    }

    private static String makeField(String name, String value, boolean inline) {
        return "{\"name\":\"" + DiscordWebhookClient.escape(name) + "\",\"value\":\""
                + DiscordWebhookClient.escape(value) + "\",\"inline\":" + inline + "}";
    }

    private static String trimToLength(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }

    private static int colorForAction(String action) {
        if (action == null) {
            return COLOR_INFO;
        }
        String lower = action.toLowerCase();
        if (lower.contains("warn")) {
            return COLOR_WARNING;
        }
        if (lower.contains("mute")) {
            if (lower.contains("unmute") || lower.contains("expire")) {
                return COLOR_SUCCESS;
            }
            return COLOR_WARNING;
        }
        if (lower.contains("kick") || lower.contains("ban") || lower.contains("cheater")) {
            return COLOR_DANGER;
        }
        if (lower.contains("note")) {
            return COLOR_NOTE;
        }
        return COLOR_INFO;
    }

    private static String formatDuration(long seconds) {
        long remaining = Math.max(0L, seconds);
        long days = remaining / 86400;
        remaining %= 86400;
        long hours = remaining / 3600;
        remaining %= 3600;
        long minutes = remaining / 60;
        long secs = remaining % 60;
        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(secs).append("s");
        return builder.toString().trim();
    }

    private static String formatIdentity(obj_id id) throws InterruptedException {
        if (!isIdValid(id)) {
            return "Unknown";
        }
        String name = getFirstName(id);
        if (name == null || name.length() == 0) {
            name = getName(id);
        }
        if (name == null || name.length() == 0) {
            name = "Unknown";
        }
        return name + " (" + id + ")";
    }

    private static void postJson(String payload) {
        if (payload == null || payload.length() == 0) {
            return;
        }
        if (!DiscordWebhookClient.sendJson(CONFIG_KEY, payload)) {
            debugServerConsoleMsg(null, "ModerationWebhook: failed to dispatch webhook for key '" + CONFIG_KEY + "'.");
        }
    }

    private static final int COLOR_INFO = 0x3498db;
    private static final int COLOR_SUCCESS = 0x2ecc71;
    private static final int COLOR_WARNING = 0xe67e22;
    private static final int COLOR_DANGER = 0xe74c3c;
    private static final int COLOR_NOTE = 0x7f8c8d;
}
