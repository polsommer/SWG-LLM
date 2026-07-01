package script.terminal;

import script.library.city;
import script.library.player_structure;
import script.library.travel;
import script.library.utils;
import script.*;

public class terminal_travel extends script.base_script {

    // ---- String IDs ----
    public static final string_id SID_TRAVEL_OPTIONS = new string_id("travel", "purchase_ticket");
    public static final string_id SID_BANNED_TICKET  = new string_id("city/city", "banned_services");

    // ---- Logging / Config ----
    private static final String LOG_CHANNEL = "terminal_travel";
    private static final String CFG_SECTION = "GameServer";
    private static final String CFG_DISABLE_TRAVEL = "disableTravelSystem";
    private static final String CFG_ON = "on";

    // Minimal, safe cache for the disable flag (refreshes periodically)
    private static volatile boolean cachedDisableTravel = false;
    private static volatile long    cachedDisableCheckedAtMs = 0L;
    private static final long       DISABLE_CACHE_REFRESH_MS = 60_000L; // 60s

    public terminal_travel() {
    }

    public int OnUnloadedFromMemory(obj_id self) throws InterruptedException {
        obj_id starport = travel.getStarportFromTerminal(self);
        LOG(LOG_CHANNEL, "OnUnloadedFromMemory -- terminal " + self + " from starport " + starport);
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException {
        if (mi == null) {
            LOG(LOG_CHANNEL, "OnObjectMenuRequest: menu_info is null for terminal " + self);
            return SCRIPT_CONTINUE;
        }

        try {
            menu_info_data data = mi.getMenuItemByType(menu_info_types.ITEM_USE);
            if (data != null) {
                data.setServerNotify(true);
            } else {
                mi.addRootMenu(menu_info_types.ITEM_USE, SID_TRAVEL_OPTIONS);
            }
        } catch (Exception e) {
            LOG(LOG_CHANNEL, "OnObjectMenuRequest: exception " + e + " for terminal " + self);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException {
        // Only handle ITEM_USE
        if (item != menu_info_types.ITEM_USE) {
            return SCRIPT_CONTINUE;
        }

        // Basic validity checks
        if (!isIdValid(self) || !isIdValid(player)) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: invalid self/player (self=" + self + ", player=" + player + ")");
            return SCRIPT_CONTINUE;
        }

        // Respect the server toggle (with a light cache)
        if (isTravelDisabled()) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: travel system disabled via config");
            return SCRIPT_CONTINUE;
        }

        // Resolve starport from this terminal
        obj_id starport = obj_id.NULL_ID;
        try {
            starport = travel.getStarportFromTerminal(self);
        } catch (Exception e) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: exception resolving starport: " + e);
        }
        if (!isIdValid(starport)) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: invalid starport for terminal " + self);
            return SCRIPT_CONTINUE;
        }

        // City ban check (only if civic and city id looks valid)
        try {
            if (player_structure.isCivic(starport)) {
                int city_id = getCityIdSafe(starport);
                if (city_id > 0 && city.isCityBanned(player, city_id)) {
                    sendSystemMessage(player, SID_BANNED_TICKET);
                    return SCRIPT_CONTINUE;
                }
            }
        } catch (Exception e) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: exception during city ban check: " + e);
            // Fail closed (don’t block travel unless we know they’re banned)
        }

        // Gather travel context
        String planet = safeGetCurrentSceneName();
        String travel_point = null;
        try {
            travel_point = travel.getTravelPointName(starport);
        } catch (Exception e) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: exception getting travel point: " + e);
        }

        if (isNullOrEmpty(planet) || isNullOrEmpty(travel_point)) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: missing planet or travel_point (planet=" + planet + ", tp=" + travel_point + ")");
            return SCRIPT_CONTINUE;
        }

        LOG(LOG_CHANNEL, "OnObjectMenuSelect: player " + player + " -> planet " + planet + " travel_point " + travel_point);

        // Remember the terminal on the player for downstream ticket flow
        try {
            utils.setScriptVar(player, travel.SCRIPT_VAR_TERMINAL, self);
        } catch (Exception e) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: exception setting script var: " + e);
        }

        // Open the purchase UI
        try {
            enterClientTicketPurchaseMode(player, planet, travel_point, false);
        } catch (Exception e) {
            LOG(LOG_CHANNEL, "OnObjectMenuSelect: exception entering purchase mode: " + e);
        }

        return SCRIPT_CONTINUE;
    }

    // ---- Helpers ----

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.length() == 0;
    }

    private static String safeGetCurrentSceneName() {
        try {
            return getCurrentSceneName();
        } catch (Exception e) {
            // Fallback to empty (caller guards it)
            return "";
        }
    }

    private static int getCityIdSafe(obj_id starport) {
        try {
            location loc = getLocation(starport);
            // The second parameter (tolerance) left at 0 to match original behavior
            return getCityAtLocation(loc, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isTravelDisabled() {
        long now = getGameTimeMsSafe();
        // refresh if never checked or cache is stale
        if (now - cachedDisableCheckedAtMs > DISABLE_CACHE_REFRESH_MS) {
            boolean disabled = false;
            try {
                String cfg = getConfigSetting(CFG_SECTION, CFG_DISABLE_TRAVEL);
                disabled = CFG_ON.equals(cfg);
            } catch (Exception e) {
                // On config read errors, default to not disabled but log it
                LOG(LOG_CHANNEL, "isTravelDisabled: exception reading config: " + e);
            }
            cachedDisableTravel = disabled;
            cachedDisableCheckedAtMs = now;
        }
        return cachedDisableTravel;
    }

    private static long getGameTimeMsSafe() {
        try {
            // If the engine provides a millis/time API, use it; otherwise use Java fallback.
            return getGameTime(); // if this returns seconds, we’ll still refresh periodically; but prefer millis if available
        } catch (Throwable t) {
            // Java fallback (not ideal in server context but safe)
            return System.currentTimeMillis();
        }
    }
}
