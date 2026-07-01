package script.discord;

import script.base_script;
import script.obj_id;

/**
 * Announces Galactic Civil War invasions in-game and through Discord.
 */
public class GCWEventAnnouncer extends base_script {

    private static final String CONFIG_KEY = "gcwEventWebhook";

    public GCWEventAnnouncer() {
    }

    /**
     * Announces the start of a GCW invasion both in-game and via Discord webhook.
     *
     * @param self The object reference.
     * @param city The city where the GCW invasion is happening.
     */
    public static void announceGCWInvasion(obj_id self, String city) {
        if (city == null || city.length() == 0) {
            debugServerConsoleMsg(self, "GCWEventAnnouncer: city name is missing.");
            return;
        }

        String message = "⚔️ Galactic Civil War Invasion is now happening in **" + city + "**! ⚔️";

        sendSystemMessageToAll(message);
        sendWebhookNotification(message);
    }

    /**
     * Sends an in-game system message to all players.
     *
     * @param message The message to broadcast.
     */
    private static void sendSystemMessageToAll(String message) {
        if (message == null || message.length() == 0) {
            return;
        }
        sendSystemMessageGalaxyTestingOnly(message);
    }

    /**
     * Sends a notification to Discord using the configured webhook.
     *
     * @param message The message to send via the webhook.
     */
    private static void sendWebhookNotification(String message) {
        if (!DiscordWebhookClient.sendPlainMessage(CONFIG_KEY, message)) {
            debugServerConsoleMsg(null, "GCWEventAnnouncer: failed to dispatch webhook for key '" + CONFIG_KEY + "'.");
        }
    }
}
