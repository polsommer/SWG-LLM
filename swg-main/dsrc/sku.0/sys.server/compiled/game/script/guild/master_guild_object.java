package script.guild;

import script.dictionary;
import script.library.guild;
import script.library.objvar_mangle;
import script.library.utils;
import script.obj_id;

import java.util.HashMap;
import java.util.Map;

public class master_guild_object extends script.base_script
{
    public master_guild_object()
    {
    }
    public static int guildProcessInterval = -1;
    public static int guildUpdateInterval = -1;
    public static final int DEFAULT_GUILD_PROCESS_INTERVAL = 60 * 60;
    public static final int DEFAULT_GUILD_UPDATE_INTERVAL = 60 * 60 * 24 * 7;
    public static final int DEFAULT_GUILD_EVENT_MIN_INTERVAL = 60 * 60 * 2;
    public static final int DEFAULT_GUILD_EVENT_MAX_INTERVAL = 60 * 60 * 6;
    public static final int MIN_GUILD_MEMBERS = 5;
    public static final String VAR_NAMECHANGE = "guildNamechange";
    public static final String VAR_NAMECHANGE_IDS = VAR_NAMECHANGE + ".ids";
    public static final String VAR_NAMECHANGE_TIMES = VAR_NAMECHANGE + ".times";
    public static final String VAR_NAMECHANGE_NAMES = VAR_NAMECHANGE + ".names";
    public static final String VAR_NAMECHANGE_ABBREVS = VAR_NAMECHANGE + ".abbrevs";
    public static final String VAR_NAMECHANGE_CHANGERS = VAR_NAMECHANGE + ".changers";
    public static final String VAR_GUILD_EVENT_IDS = "guildEvent.ids";
    public static final String VAR_GUILD_EVENT_TIMES = "guildEvent.times";
    public static final int GUILD_EVENT_TIER_SMALL = 0;
    public static final int GUILD_EVENT_TIER_MEDIUM = 1;
    public static final int GUILD_EVENT_TIER_LARGE = 2;
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "onGuildUpdatePulse", null, getGuildProcessInterval(), false);
        return SCRIPT_CONTINUE;
    }
    public int onGuildUpdatePulse(obj_id self, dictionary params) throws InterruptedException
    {
        updateGuilds(self, false);
        updateGuildNameChanges(self, false);
        handleGuildEventPulse(self);
        messageTo(self, "onGuildUpdatePulse", null, getGuildProcessInterval(), false);
        return SCRIPT_CONTINUE;
    }
    public int forceUpdateGuilds(obj_id self, dictionary params) throws InterruptedException
    {
        updateGuilds(self, true);
        return SCRIPT_CONTINUE;
    }
    public int forceUpdateGuildNameChanges(obj_id self, dictionary params) throws InterruptedException
    {
        updateGuildNameChanges(self, true);
        return SCRIPT_CONTINUE;
    }
    public int initiateGuildNameChange(obj_id self, dictionary params) throws InterruptedException
    {
        int guildId = params.getInt("guildId");
        String newName = params.getString("newName");
        String newAbbrev = params.getString("newAbbrev");
        obj_id changerId = params.getObjId("changerId");
        int[] guildNamechangeIds = objvar_mangle.getMangledIntArrayObjVar(self, VAR_NAMECHANGE_IDS);
        int[] guildNamechangeTimes = objvar_mangle.getMangledIntArrayObjVar(self, VAR_NAMECHANGE_TIMES);
        String[] guildNamechangeNames = objvar_mangle.getMangledStringArrayObjVar(self, VAR_NAMECHANGE_NAMES);
        String[] guildNamechangeAbbrevs = objvar_mangle.getMangledStringArrayObjVar(self, VAR_NAMECHANGE_ABBREVS);
        obj_id[] guildNamechangeChangers = objvar_mangle.getMangledObjIdArrayObjVar(self, VAR_NAMECHANGE_CHANGERS);
        if (guildNamechangeChangers == null && guildNamechangeIds != null)
        {
            guildNamechangeChangers = new obj_id[guildNamechangeIds.length];
            for (int i = 0; i < guildNamechangeChangers.length; ++i)
            {
                guildNamechangeChangers[i] = obj_id.NULL_ID;
            }
        }
        int nextTime = getGameTime() + getGuildUpdateInterval();
        if (guildNamechangeIds != null && guildNamechangeTimes != null && guildNamechangeNames != null && guildNamechangeAbbrevs != null && guildNamechangeChangers != null && guildNamechangeIds.length == guildNamechangeTimes.length && guildNamechangeIds.length == guildNamechangeNames.length && guildNamechangeIds.length == guildNamechangeAbbrevs.length && guildNamechangeIds.length == guildNamechangeChangers.length)
        {
            for (int i = 0; i < guildNamechangeIds.length; ++i)
            {
                if (guildNamechangeIds[i] == guildId)
                {
                    guildNamechangeTimes[i] = nextTime;
                    guildNamechangeNames[i] = newName;
                    guildNamechangeAbbrevs[i] = newAbbrev;
                    guildNamechangeChangers[i] = changerId;
                    objvar_mangle.setMangledIntArrayObjVar(self, VAR_NAMECHANGE_TIMES, guildNamechangeTimes);
                    objvar_mangle.setMangledStringArrayObjVar(self, VAR_NAMECHANGE_NAMES, guildNamechangeNames);
                    objvar_mangle.setMangledStringArrayObjVar(self, VAR_NAMECHANGE_ABBREVS, guildNamechangeAbbrevs);
                    objvar_mangle.setMangledObjIdArrayObjVar(self, VAR_NAMECHANGE_CHANGERS, guildNamechangeChangers);
                    return SCRIPT_CONTINUE;
                }
            }
        }
        else 
        {
            guildNamechangeIds = null;
            guildNamechangeTimes = null;
            guildNamechangeNames = null;
            guildNamechangeAbbrevs = null;
            guildNamechangeChangers = null;
        }
        int newCount = 1;
        if (guildNamechangeIds != null)
        {
            newCount = guildNamechangeIds.length + 1;
        }
        int[] newGuildNamechangeIds = new int[newCount];
        int[] newGuildNamechangeTimes = new int[newCount];
        String[] newGuildNamechangeNames = new String[newCount];
        String[] newGuildNamechangeAbbrevs = new String[newCount];
        obj_id[] newGuildNamechangeChangers = new obj_id[newCount];
        for (int i = 0; i < newCount - 1; ++i)
        {
            newGuildNamechangeIds[i] = guildNamechangeIds[i];
            newGuildNamechangeTimes[i] = guildNamechangeTimes[i];
            newGuildNamechangeNames[i] = guildNamechangeNames[i];
            newGuildNamechangeAbbrevs[i] = guildNamechangeAbbrevs[i];
            newGuildNamechangeChangers[i] = guildNamechangeChangers[i];
        }
        newGuildNamechangeIds[newCount - 1] = guildId;
        newGuildNamechangeTimes[newCount - 1] = nextTime;
        newGuildNamechangeNames[newCount - 1] = newName;
        newGuildNamechangeAbbrevs[newCount - 1] = newAbbrev;
        newGuildNamechangeChangers[newCount - 1] = changerId;
        objvar_mangle.setMangledIntArrayObjVar(self, VAR_NAMECHANGE_IDS, newGuildNamechangeIds);
        objvar_mangle.setMangledIntArrayObjVar(self, VAR_NAMECHANGE_TIMES, newGuildNamechangeTimes);
        objvar_mangle.setMangledStringArrayObjVar(self, VAR_NAMECHANGE_NAMES, newGuildNamechangeNames);
        objvar_mangle.setMangledStringArrayObjVar(self, VAR_NAMECHANGE_ABBREVS, newGuildNamechangeAbbrevs);
        objvar_mangle.setMangledObjIdArrayObjVar(self, VAR_NAMECHANGE_CHANGERS, newGuildNamechangeChangers);
        return SCRIPT_CONTINUE;
    }
    public void updateGuilds(obj_id self, boolean force) throws InterruptedException
    {
        int[] guildUpdateIds = objvar_mangle.getMangledIntArrayObjVar(self, "guildUpdate.ids");
        int[] guildUpdateTimes = objvar_mangle.getMangledIntArrayObjVar(self, "guildUpdate.times");
        int currentTime = getGameTime();
        int nextTime = currentTime + getGuildUpdateInterval();
        if (guildUpdateIds != null && guildUpdateTimes != null && guildUpdateIds.length == guildUpdateTimes.length)
        {
            for (int i = 0; i < guildUpdateIds.length; ++i)
            {
                if (force || guildUpdateTimes[i] <= currentTime)
                {
                    guildUpdateTimes[i] = nextTime;
                    handleGuildUpdate(guildUpdateIds[i]);
                }
            }
        }
        else 
        {
            guildUpdateIds = null;
            guildUpdateTimes = null;
        }
        int[] guildIds = getAllGuildIds();
        int[] newGuildUpdateTimes = new int[guildIds.length];
        for (int i = 0; i < guildIds.length; ++i)
        {
            int pos = findIntOffsetInTable(guildUpdateIds, guildIds[i]);
            if (pos == -1)
            {
                newGuildUpdateTimes[i] = nextTime;
            }
            else 
            {
                newGuildUpdateTimes[i] = guildUpdateTimes[pos];
            }
        }
        objvar_mangle.setMangledIntArrayObjVar(self, "guildUpdate.ids", guildIds);
        objvar_mangle.setMangledIntArrayObjVar(self, "guildUpdate.times", newGuildUpdateTimes);
    }
    public void updateGuildNameChanges(obj_id self, boolean force) throws InterruptedException
    {
        int currentTime = getGameTime();
        int nextTime = currentTime + getGuildUpdateInterval();
        int[] guildNamechangeIds = objvar_mangle.getMangledIntArrayObjVar(self, VAR_NAMECHANGE_IDS);
        int[] guildNamechangeTimes = objvar_mangle.getMangledIntArrayObjVar(self, VAR_NAMECHANGE_TIMES);
        String[] guildNamechangeNames = objvar_mangle.getMangledStringArrayObjVar(self, VAR_NAMECHANGE_NAMES);
        String[] guildNamechangeAbbrevs = objvar_mangle.getMangledStringArrayObjVar(self, VAR_NAMECHANGE_ABBREVS);
        obj_id[] guildNamechangeChangers = objvar_mangle.getMangledObjIdArrayObjVar(self, VAR_NAMECHANGE_CHANGERS);
        if (guildNamechangeChangers == null && guildNamechangeIds != null)
        {
            guildNamechangeChangers = new obj_id[guildNamechangeIds.length];
            for (int i = 0; i < guildNamechangeChangers.length; ++i)
            {
                guildNamechangeChangers[i] = obj_id.NULL_ID;
            }
        }
        if (guildNamechangeIds != null && guildNamechangeTimes != null && guildNamechangeNames != null && guildNamechangeAbbrevs != null && guildNamechangeChangers != null && guildNamechangeIds.length == guildNamechangeTimes.length && guildNamechangeIds.length == guildNamechangeNames.length && guildNamechangeIds.length == guildNamechangeAbbrevs.length && guildNamechangeIds.length == guildNamechangeChangers.length)
        {
            int newCount = 0;
            for (int i = 0; i < guildNamechangeIds.length; ++i)
            {
                if (force || guildNamechangeTimes[i] <= currentTime)
                {
                    guild.handleGuildNameChange(guildNamechangeIds[i], guildNamechangeNames[i], guildNamechangeAbbrevs[i], guildNamechangeChangers[i]);
                }
                else 
                {
                    ++newCount;
                }
            }
            if (newCount == 0)
            {
                clearNamechanges(self);
            }
            else if (newCount != guildNamechangeIds.length)
            {
                int[] newGuildNamechangeIds = new int[newCount];
                int[] newGuildNamechangeTimes = new int[newCount];
                String[] newGuildNamechangeNames = new String[newCount];
                String[] newGuildNamechangeAbbrevs = new String[newCount];
                obj_id[] newGuildNamechangeChangers = new obj_id[newCount];
                int pos = 0;
                for (int i = 0; i < guildNamechangeIds.length; ++i)
                {
                    if (guildNamechangeTimes[i] > currentTime)
                    {
                        newGuildNamechangeIds[pos] = guildNamechangeIds[i];
                        newGuildNamechangeTimes[pos] = guildNamechangeTimes[i];
                        newGuildNamechangeNames[pos] = guildNamechangeNames[i];
                        newGuildNamechangeAbbrevs[pos] = guildNamechangeAbbrevs[i];
                        newGuildNamechangeChangers[pos] = guildNamechangeChangers[i];
                        ++pos;
                    }
                }
                objvar_mangle.setMangledIntArrayObjVar(self, VAR_NAMECHANGE_IDS, newGuildNamechangeIds);
                objvar_mangle.setMangledIntArrayObjVar(self, VAR_NAMECHANGE_TIMES, newGuildNamechangeTimes);
                objvar_mangle.setMangledStringArrayObjVar(self, VAR_NAMECHANGE_NAMES, newGuildNamechangeNames);
                objvar_mangle.setMangledStringArrayObjVar(self, VAR_NAMECHANGE_ABBREVS, newGuildNamechangeAbbrevs);
                objvar_mangle.setMangledObjIdArrayObjVar(self, VAR_NAMECHANGE_CHANGERS, newGuildNamechangeChangers);
            }
        }
        else 
        {
            clearNamechanges(self);
        }
    }
    public void clearNamechanges(obj_id self) throws InterruptedException
    {
        removeObjVar(self, VAR_NAMECHANGE);
    }
    public void handleGuildUpdate(int guildId) throws InterruptedException
    {
        obj_id[] memberIds = guildGetMemberIds(guildId);
        for (obj_id memberId : memberIds) {
            if (!playerExists(memberId)) {
                guildRemoveMember(guildId, memberId);
            }
        }
        memberIds = guild.getMemberIds(guildId, false, true);
        if (memberIds.length < MIN_GUILD_MEMBERS)
        {
            guild.disbandForNotEnoughMembers(guildId);
        }
    }
    public void handleGuildEventPulse(obj_id self) throws InterruptedException
    {
        int currentTime = getGameTime();
        int[] guildEventIds = objvar_mangle.getMangledIntArrayObjVar(self, VAR_GUILD_EVENT_IDS);
        int[] guildEventTimes = objvar_mangle.getMangledIntArrayObjVar(self, VAR_GUILD_EVENT_TIMES);
        int[] guildIds = getAllGuildIds();
        Map<Integer, String> guildCityMap = buildGuildCityMap();
        int[] newGuildEventTimes = new int[guildIds.length];
        for (int i = 0; i < guildIds.length; ++i)
        {
            int guildId = guildIds[i];
            int pos = findIntOffsetInTable(guildEventIds, guildId);
            int nextEventTime = currentTime;
            if (pos != -1)
            {
                nextEventTime = guildEventTimes[pos];
            }
            if (nextEventTime <= currentTime)
            {
                int memberCount = guildGetCountMembersOnly(guildId);
                int tier = getGuildEventTier(memberCount);
                if (shouldTriggerGuildEvent(tier))
                {
                    String cityName = guildCityMap.get(guildId);
                    triggerGuildEvent(guildId, memberCount, cityName);
                }
                nextEventTime = currentTime + getGuildEventIntervalSeconds(tier);
            }
            newGuildEventTimes[i] = nextEventTime;
        }
        objvar_mangle.setMangledIntArrayObjVar(self, VAR_GUILD_EVENT_IDS, guildIds);
        objvar_mangle.setMangledIntArrayObjVar(self, VAR_GUILD_EVENT_TIMES, newGuildEventTimes);
    }
    private Map<Integer, String> buildGuildCityMap() throws InterruptedException
    {
        Map<Integer, String> guildCityMap = new HashMap<>();
        int[] cityIds = getAllCityIds();
        if (cityIds == null || cityIds.length == 0)
        {
            return guildCityMap;
        }
        for (int cityId : cityIds)
        {
            obj_id mayor = cityGetLeader(cityId);
            if (!isIdValid(mayor))
            {
                continue;
            }
            int guildId = getGuildId(mayor);
            if (guildId > 0 && !guildCityMap.containsKey(guildId))
            {
                guildCityMap.put(guildId, cityGetName(cityId));
            }
        }
        return guildCityMap;
    }
    private int getGuildEventTier(int memberCount) throws InterruptedException
    {
        if (memberCount <= 10)
        {
            return GUILD_EVENT_TIER_SMALL;
        }
        if (memberCount <= 25)
        {
            return GUILD_EVENT_TIER_MEDIUM;
        }
        return GUILD_EVENT_TIER_LARGE;
    }
    private boolean shouldTriggerGuildEvent(int tier) throws InterruptedException
    {
        int chance = 10;
        if (tier == GUILD_EVENT_TIER_SMALL)
        {
            chance = 35;
        }
        else if (tier == GUILD_EVENT_TIER_MEDIUM)
        {
            chance = 20;
        }
        return rand(1, 100) <= chance;
    }
    private int getGuildEventIntervalSeconds(int tier) throws InterruptedException
    {
        int minInterval = DEFAULT_GUILD_EVENT_MIN_INTERVAL;
        int maxInterval = DEFAULT_GUILD_EVENT_MAX_INTERVAL;
        if (tier == GUILD_EVENT_TIER_MEDIUM)
        {
            minInterval += 60 * 60;
            maxInterval += 60 * 60;
        }
        else if (tier == GUILD_EVENT_TIER_LARGE)
        {
            minInterval += 60 * 60 * 2;
            maxInterval += 60 * 60 * 2;
        }
        return minInterval + rand(0, Math.max(0, maxInterval - minInterval));
    }
    private void triggerGuildEvent(int guildId, int memberCount, String cityName) throws InterruptedException
    {
        obj_id[] memberIds = guild.getMemberIds(guildId, false, true);
        if (memberIds == null || memberIds.length == 0)
        {
            return;
        }
        String message = buildGuildEventMessage(guildId, memberCount, cityName);
        for (obj_id memberId : memberIds)
        {
            if (playerExists(memberId))
            {
                sendSystemMessage(memberId, message, "");
            }
        }
    }
    private String buildGuildEventMessage(int guildId, int memberCount, String cityName) throws InterruptedException
    {
        String guildName = guildGetName(guildId);
        int tier = getGuildEventTier(memberCount);
        int factionId = guildGetCurrentFaction(guildId);
        String factionPrefix = "Independent scouts";
        if (factionId == (-615855020))
        {
            factionPrefix = "Imperial Intelligence";
        }
        else if (factionId == (370444368))
        {
            factionPrefix = "Alliance spies";
        }
        String tierFlavor = "a quick strike";
        if (tier == GUILD_EVENT_TIER_MEDIUM)
        {
            tierFlavor = "a coordinated push";
        }
        else if (tier == GUILD_EVENT_TIER_LARGE)
        {
            tierFlavor = "a full-scale offensive";
        }
        String[] objectives = new String[]
        {
            "pirate raiders threatening hyperspace lanes",
            "a rogue droid enclave sparking trouble",
            "a spice ring hiding in the wilds",
            "a bounty target with a dangerous escort",
            "a skirmish zone lighting up the frontier"
        };
        String objective = objectives[rand(0, objectives.length - 1)];
        String message = factionPrefix + " flag " + objective + " for " + guildName + ". Rally for " + tierFlavor + " and claim the glory!";
        if (cityName != null && cityName.length() > 0)
        {
            message += " Alert from " + cityName + ": something big is brewing on your streets.";
        }
        return message;
    }
    public int findIntOffsetInTable(int[] from, int find) throws InterruptedException
    {
        if (from != null)
        {
            for (int i = 0; i < from.length; ++i)
            {
                if (from[i] == find)
                {
                    return i;
                }
            }
        }
        return -1;
    }
    public int getGuildProcessInterval() throws InterruptedException
    {
        if (guildProcessInterval == -1)
        {
            String strGuildProcessInterval = getConfigSetting("GameServer", "guildProcessInterval");
            if (strGuildProcessInterval != null)
            {
                guildProcessInterval = utils.stringToInt(strGuildProcessInterval);
            }
            if (guildProcessInterval <= 0)
            {
                guildProcessInterval = DEFAULT_GUILD_PROCESS_INTERVAL;
            }
            debugServerConsoleMsg(null, "guildProcessInterval set to " + guildProcessInterval + " seconds.");
        }
        return guildProcessInterval;
    }
    public int getGuildUpdateInterval() throws InterruptedException
    {
        if (guildUpdateInterval == -1)
        {
            String strGuildUpdateInterval = getConfigSetting("GameServer", "guildUpdateInterval");
            if (strGuildUpdateInterval != null)
            {
                guildUpdateInterval = utils.stringToInt(strGuildUpdateInterval);
            }
            if (guildUpdateInterval <= 0)
            {
                guildUpdateInterval = DEFAULT_GUILD_UPDATE_INTERVAL;
            }
            debugServerConsoleMsg(null, "guildUpdateInterval set to " + guildUpdateInterval + " seconds.");
        }
        return guildUpdateInterval;
    }
}
