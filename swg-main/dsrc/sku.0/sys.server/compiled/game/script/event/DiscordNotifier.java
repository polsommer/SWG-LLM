package script.event;

import script.base_script;
import script.discord.DiscordWebhookClient;

public class DiscordNotifier extends base_script {

    private static final String CONFIG_KEY = "eventWebhook";

    public DiscordNotifier() {
    }

    public static void sendDiscordMessage(String message) {
        if (message == null || message.length() == 0) {
            return;
        }
        if (!DiscordWebhookClient.sendPlainMessage(CONFIG_KEY, message)) {
            debugServerConsoleMsg(null, "DiscordNotifier: failed to dispatch webhook for key '" + CONFIG_KEY + "'.");
        }
    }

    public static void sendStartupTest() {
        sendDiscordMessage("Startup test: server is online.");
    }

    public static void announceEvent(String details) {
        sendDiscordMessage("Event Announcement: " + details);
    }
}
