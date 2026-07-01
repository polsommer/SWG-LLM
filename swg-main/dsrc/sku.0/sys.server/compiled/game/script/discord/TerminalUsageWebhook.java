package script.discord;

import java.text.SimpleDateFormat;
import java.util.Date;

import script.base_script;
import script.obj_id;

/**
 * Publishes terminal interactions to a Discord webhook for auditing.
 */
public class TerminalUsageWebhook extends base_script {

    private static final String CONFIG_KEY = "terminalUsageWebhook";

    public TerminalUsageWebhook() {
    }

    /**
     * Logs terminal usage to Discord via webhook.
     *
     * @param player   The player accessing the terminal.
     * @param terminal The terminal being accessed.
     * @param action   The action performed by the player.
     */
    public static void logTerminalUsage(obj_id player, obj_id terminal, String action) {
        String terminalName = resolveName(terminal, "Unknown Terminal");
        String playerName = resolveName(player, "Unknown Player");
        String resolvedAction = (action == null || action.length() == 0) ? "Performed an action" : action;
        String timestamp = getCurrentTimestamp();

        String message = String.format(
                "**Terminal Access Log**\n" +
                "💻 **Terminal:** %s\n" +
                "🧑 **Player:** %s\n" +
                "🛠️ **Action:** %s\n" +
                "⏱️ **Time:** %s",
                terminalName,
                playerName,
                resolvedAction,
                timestamp);

        if (!DiscordWebhookClient.sendPlainMessage(CONFIG_KEY, message)) {
            debugServerConsoleMsg(null, "TerminalUsageWebhook: failed to dispatch webhook for key '" + CONFIG_KEY + "'.");
        }
    }

    private static String resolveName(obj_id object, String fallback) {
        if (object != null && isIdValid(object)) {
            String firstName = getFirstName(object);
            if (firstName != null && firstName.length() > 0) {
                return firstName;
            }
            String name = getName(object);
            if (name != null && name.length() > 0) {
                return name;
            }
        }
        return fallback;
    }

    /**
     * Gets the current timestamp in a readable format.
     *
     * @return The current timestamp.
     */
    private static String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }
}
