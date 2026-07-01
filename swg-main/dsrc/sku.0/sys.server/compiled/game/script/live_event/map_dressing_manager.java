package script.live_event;

import script.*;
import script.library.utils;

import java.util.GregorianCalendar;
import java.util.TimeZone;

public class map_dressing_manager extends script.base_script
{
    public static final String DRESSING_TABLE = "datatables/live_event/map_event_dressing.iff";
    public static final String VAR_ROOT = "live_event.dressing.";

    public int applyMapDressing(obj_id player, String mapId) throws InterruptedException
    {
        if (!isIdValid(player) || mapId == null || mapId.length() == 0)
        {
            return SCRIPT_CONTINUE;
        }

        int row = dataTableSearchColumnForString(mapId, "map_id", DRESSING_TABLE);
        if (row < 0)
        {
            rollbackToFallback(player, "baseline_default");
            return SCRIPT_CONTINUE;
        }

        if (!isEventActive(row) || !getDataTableBoolean(row, "event_dressing_enabled"))
        {
            rollbackToFallback(player, dataTableGetString(DRESSING_TABLE, row, "fallback_profile"));
            return SCRIPT_CONTINUE;
        }

        applySkyOverlay(player, row);
        applyFlyover(player, row);
        applyAnnouncementLoop(player, row);
        return SCRIPT_CONTINUE;
    }

    public int clearMapDressing(obj_id player, String mapId) throws InterruptedException
    {
        int row = dataTableSearchColumnForString(mapId, "map_id", DRESSING_TABLE);
        if (row >= 0)
        {
            rollbackToFallback(player, dataTableGetString(DRESSING_TABLE, row, "fallback_profile"));
        }
        return SCRIPT_CONTINUE;
    }

    private boolean isEventActive(int row) throws InterruptedException
    {
        String start = dataTableGetString(DRESSING_TABLE, row, "start_time_utc");
        String end = dataTableGetString(DRESSING_TABLE, row, "end_time_utc");
        if (start == null || end == null || start.length() == 0 || end.length() == 0)
        {
            return false;
        }

        int now = getCalendarTime();
        int startEpoch = parseDateTimeUtc(start);
        int endEpoch = parseDateTimeUtc(end);
        return now >= startEpoch && now <= endEpoch;
    }

    private int parseDateTimeUtc(String utcTime)
    {
        if (utcTime == null || utcTime.length() == 0)
        {
            return 0;
        }

        if (Character.isDigit(utcTime.charAt(0)))
        {
            try
            {
                return Integer.parseInt(utcTime);
            }
            catch (NumberFormatException ignored)
            {
            }
        }

        if (utcTime.length() < 20)
        {
            return 0;
        }

        try
        {
            int year = Integer.parseInt(utcTime.substring(0, 4));
            int month = Integer.parseInt(utcTime.substring(5, 7));
            int day = Integer.parseInt(utcTime.substring(8, 10));
            int hour = Integer.parseInt(utcTime.substring(11, 13));
            int minute = Integer.parseInt(utcTime.substring(14, 16));
            int second = Integer.parseInt(utcTime.substring(17, 19));

            GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            calendar.set(year, month - 1, day, hour, minute, second);
            calendar.set(GregorianCalendar.MILLISECOND, 0);
            return (int)(calendar.getTimeInMillis() / 1000L);
        }
        catch (RuntimeException ignored)
        {
            return 0;
        }
    }

    private void applySkyOverlay(obj_id player, int row) throws InterruptedException
    {
        if (!isComponentEnabled(row, "sky_toggle", "sky"))
        {
            return;
        }
        String profile = dataTableGetString(DRESSING_TABLE, row, "sky_profile");
        utils.setScriptVar(player, VAR_ROOT + "sky_profile", profile);
    }

    private void applyFlyover(obj_id player, int row) throws InterruptedException
    {
        if (!isComponentEnabled(row, "flyover_toggle", "flyover"))
        {
            return;
        }
        String profile = dataTableGetString(DRESSING_TABLE, row, "flyover_profile");
        utils.setScriptVar(player, VAR_ROOT + "flyover_profile", profile);
        messageTo(player, "triggerLiveEventFlyover", null, rand(10.0f, 60.0f), false);
    }

    private void applyAnnouncementLoop(obj_id player, int row) throws InterruptedException
    {
        if (!isComponentEnabled(row, "announcement_toggle", "announcement"))
        {
            return;
        }
        String profile = dataTableGetString(DRESSING_TABLE, row, "announcement_profile");
        utils.setScriptVar(player, VAR_ROOT + "announcement_profile", profile);
        messageTo(player, "triggerLiveEventAnnouncement", null, 2.0f, false);
    }


    private boolean getDataTableBoolean(int row, String column) throws InterruptedException
    {
        String value = dataTableGetString(DRESSING_TABLE, row, column);
        if (value == null)
        {
            return false;
        }

        value = value.trim();
        return value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("y");
    }

    private boolean isComponentEnabled(int row, String perMapColumn, String componentKey) throws InterruptedException
    {
        if (!getDataTableBoolean(row, perMapColumn))
        {
            return false;
        }
        String globalVar = "live_event.disable_component." + componentKey;
        return !utils.getBooleanScriptVar(getSelf(), globalVar);
    }

    public int setComponentEnabled(obj_id admin, String componentKey, boolean enabled) throws InterruptedException
    {
        if (!isGod(admin))
        {
            sendSystemMessage(admin, "Live Event: insufficient permissions", null);
            return SCRIPT_CONTINUE;
        }

        String globalVar = "live_event.disable_component." + componentKey;
        utils.setScriptVar(getSelf(), globalVar, !enabled);
        sendSystemMessage(admin, "Live Event component " + componentKey + " enabled=" + enabled, null);
        return SCRIPT_CONTINUE;
    }

    private void rollbackToFallback(obj_id player, String fallbackProfile) throws InterruptedException
    {
        utils.setScriptVar(player, VAR_ROOT + "active_fallback", fallbackProfile);
        utils.removeScriptVar(player, VAR_ROOT + "sky_profile");
        utils.removeScriptVar(player, VAR_ROOT + "flyover_profile");
        utils.removeScriptVar(player, VAR_ROOT + "announcement_profile");
        messageTo(player, "restoreBaselineSky", null, 0.0f, false);
        messageTo(player, "restoreBaselineAudio", null, 0.0f, false);
    }
}
