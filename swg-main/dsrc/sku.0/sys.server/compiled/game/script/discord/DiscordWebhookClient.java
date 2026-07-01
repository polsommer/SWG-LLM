package script.discord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import script.base_script;

/**
 * Utility for dispatching payloads to Discord webhooks.
 */
public final class DiscordWebhookClient extends base_script {

    private static final String CONFIG_SECTION = "Discord";

    public DiscordWebhookClient() {
    }

    /**
     * Sends a plaintext message to the webhook referenced by the provided configuration key.
     *
     * @param configKey configuration entry that stores the webhook URL.
     * @param message   content to send to Discord.
     * @return {@code true} when the payload was successfully dispatched.
     */
    public static boolean sendPlainMessage(String configKey, String message) {
        if (message == null || message.length() == 0) {
            return false;
        }
        return sendJson(configKey, "{\"content\":\"" + escape(message) + "\"}");
    }

    /**
     * Sends a raw JSON payload to the webhook referenced by the provided configuration key.
     *
     * @param configKey configuration entry that stores the webhook URL.
     * @param payload   JSON payload to send.
     * @return {@code true} when the payload was successfully dispatched.
     */
    public static boolean sendJson(String configKey, String payload) {
        if (payload == null || payload.length() == 0) {
            return false;
        }
        String url = resolveWebhookUrl(configKey);
        if (url == null) {
            return false;
        }
        return postPayload(configKey, url, payload);
    }

    /**
     * Escapes user-provided content so it can safely be embedded in JSON strings.
     *
     * @param value text to escape.
     * @return escaped text suitable for JSON payloads.
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String resolveWebhookUrl(String configKey) {
        if (configKey == null || configKey.length() == 0) {
            debugServerConsoleMsg(null, "Discord webhook configuration key is missing.");
            return null;
        }
        String url = getConfigSetting(CONFIG_SECTION, configKey);
        if (url == null || url.length() == 0) {
            debugServerConsoleMsg(null, "Discord webhook '" + configKey + "' is not configured.");
            return null;
        }
        return url;
    }

    private static boolean postPayload(String configKey, String urlString, String payload) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int response = connection.getResponseCode();
            if (response >= 200 && response < 300) {
                return true;
            }
            debugServerConsoleMsg(null, "Discord webhook '" + configKey + "' responded with HTTP " + response);
        } catch (IOException e) {
            debugServerConsoleMsg(null, "Discord webhook '" + configKey + "' error: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }
}
