package script.player;

import script.dictionary;
import script.location;
import script.obj_id;
import script.library.buff;
import script.library.utils;
import script.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class species_perk_manager extends script.base_script
{
    private static final String DATATABLE = "datatables/player/species_perks.iff";
    private static final String VAR_PREFIX = "speciesPerk";
    private static final String VAR_TOKEN = VAR_PREFIX + ".token";
    private static final String VAR_COOLDOWN_PREFIX = VAR_PREFIX + ".cooldown.";
    private static final String VAR_DAILY_PREFIX = VAR_PREFIX + ".daily.";
    private static final float EPSILON = 0.001f;

    private static Map<String, PerkDefinition> PERKS;

    public static final class PerkDefinition
    {
        public final String token;
        public final String buffName;
        public final String commandName;
        public final float cooldown;
        public final String cooldownGroup;
        public final Map<String, String> abilityData;
        public final int dailyLimit;

        public PerkDefinition()
        {
            this(null, null, null, 0.0f, null, null, 0);
        }

        public PerkDefinition(String token, String buffName, String commandName, float cooldown, String cooldownGroup, Map<String, String> abilityData, int dailyLimit)
        {
            this.token = token;
            this.buffName = buffName;
            this.commandName = commandName;
            this.cooldown = cooldown;
            this.cooldownGroup = cooldownGroup;
            this.abilityData = abilityData == null ? Collections.<String, String>emptyMap() : abilityData;
            this.dailyLimit = dailyLimit;
        }
    }

    public species_perk_manager()
    {
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        applyPerks(self);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        applyPerks(self);
        return SCRIPT_CONTINUE;
    }

    public int refreshSpeciesPerks(obj_id self, dictionary params) throws InterruptedException
    {
        applyPerks(self);
        return SCRIPT_CONTINUE;
    }

    private void ensureDataLoaded() throws InterruptedException
    {
        if (PERKS != null)
        {
            return;
        }
        Map<String, PerkDefinition> map = new HashMap<>();
        int rows = dataTableGetNumRows(DATATABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE, i);
            if (row == null)
            {
                continue;
            }
            String token = row.getString("templateToken");
            if ((token == null) || (token.length() == 0))
            {
                continue;
            }
            String buffName = row.getString("buffName");
            String commandName = row.getString("commandName");
            float cooldown = row.getFloat("cooldownSeconds");
            String cooldownGroup = row.getString("cooldownGroup");
            if ((cooldownGroup == null) || (cooldownGroup.length() == 0))
            {
                cooldownGroup = commandName;
            }
            String abilityData = row.getString("abilityData");
            int dailyLimit = row.getInt("dailyUseLimit");
            Map<String, String> abilityMap = parseAbilityData(abilityData);
            map.put(token.toLowerCase(), new PerkDefinition(token.toLowerCase(), normalize(buffName), normalize(commandName), cooldown, normalize(cooldownGroup), abilityMap, dailyLimit));
        }
        PERKS = map;
    }

    private String normalize(String value)
    {
        return (value == null || value.length() == 0) ? null : value;
    }

    private Map<String, String> parseAbilityData(String data)
    {
        if ((data == null) || (data.length() == 0))
        {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        String[] pairs = data.split(";");
        for (String raw : pairs)
        {
            if (raw == null)
            {
                continue;
            }
            String entry = raw.trim();
            if (entry.length() == 0)
            {
                continue;
            }
            int idx = entry.indexOf('=');
            if (idx > 0)
            {
                String key = entry.substring(0, idx).trim().toLowerCase();
                String value = entry.substring(idx + 1).trim();
                if (key.length() > 0)
                {
                    map.put(key, value);
                }
            }
            else
            {
                map.put(entry.toLowerCase(), "true");
            }
        }
        return map;
    }

    private void applyPerks(obj_id self) throws InterruptedException
    {
        ensureDataLoaded();
        PerkDefinition def = findPerkForPlayer(self);
        if (def == null)
        {
            utils.removeScriptVar(self, VAR_TOKEN);
            return;
        }
        utils.setScriptVar(self, VAR_TOKEN, def.token);
        if ((def.buffName != null) && (def.buffName.length() > 0) && !buff.hasBuff(self, def.buffName))
        {
            buff.applyBuff(self, def.buffName);
        }
        if ((def.commandName != null) && (def.commandName.length() > 0) && !hasCommand(self, def.commandName))
        {
            grantCommand(self, def.commandName);
        }
    }

    private PerkDefinition findPerkForPlayer(obj_id self) throws InterruptedException
    {
        String template = getTemplateName(self);
        if ((template != null) && (template.length() > 0))
        {
            String lowered = template.toLowerCase();
            for (PerkDefinition def : PERKS.values())
            {
                if (lowered.contains(def.token))
                {
                    return def;
                }
            }
        }
        return null;
    }

    private PerkDefinition getCurrentPerk(obj_id self) throws InterruptedException
    {
        ensureDataLoaded();
        if (utils.hasScriptVar(self, VAR_TOKEN))
        {
            String token = utils.getStringScriptVar(self, VAR_TOKEN);
            if ((token != null) && PERKS.containsKey(token))
            {
                return PERKS.get(token);
            }
        }
        applyPerks(self);
        if (utils.hasScriptVar(self, VAR_TOKEN))
        {
            String token = utils.getStringScriptVar(self, VAR_TOKEN);
            if ((token != null) && PERKS.containsKey(token))
            {
                return PERKS.get(token);
            }
        }
        return null;
    }

    private PerkDefinition requirePerkCommand(obj_id self, String commandName) throws InterruptedException
    {
        PerkDefinition def = getCurrentPerk(self);
        if (def == null || def.commandName == null || def.commandName.length() == 0)
        {
            sendSystemMessage(self, "You do not have an active species perk ability.", "");
            return null;
        }
        if (!def.commandName.equals(commandName))
        {
            sendSystemMessage(self, "Your current species perk does not grant that command.", "");
            return null;
        }
        return def;
    }

    private boolean triggerCooldown(obj_id self, PerkDefinition def) throws InterruptedException
    {
        if (def == null)
        {
            return false;
        }
        if (def.cooldown <= EPSILON)
        {
            return true;
        }
        String keyName = def.commandName != null ? def.commandName : def.cooldownGroup;
        if ((keyName == null) || (keyName.length() == 0))
        {
            keyName = "perk";
        }
        String key = VAR_COOLDOWN_PREFIX + keyName;
        int now = getGameTime();
        int readyTime = utils.getIntScriptVar(self, key);
        if (readyTime > now)
        {
            int remaining = readyTime - now;
            sendSystemMessage(self, "You must wait " + utils.formatTimeVerbose(remaining) + " before using that perk again.", "");
            return false;
        }
        int next = now + (int)Math.ceil(def.cooldown);
        utils.setScriptVar(self, key, next);
        if ((def.cooldownGroup != null) && (def.cooldownGroup.length() > 0))
        {
            int crc = getStringCrc(def.cooldownGroup.toLowerCase());
            sendCooldownGroupTimingOnly(self, crc, def.cooldown);
        }
        return true;
    }

    private int getDailyRemaining(obj_id self, PerkDefinition def) throws InterruptedException
    {
        if (def == null || def.dailyLimit <= 0)
        {
            return Integer.MAX_VALUE;
        }
        String dayKey = VAR_DAILY_PREFIX + def.commandName + ".day";
        String countKey = VAR_DAILY_PREFIX + def.commandName + ".count";
        int day = getCalendarTime() / 86400;
        int storedDay = utils.getIntScriptVar(self, dayKey);
        int count = utils.getIntScriptVar(self, countKey);
        if (storedDay != day)
        {
            count = 0;
        }
        return def.dailyLimit - count;
    }

    private void incrementDailyUse(obj_id self, PerkDefinition def) throws InterruptedException
    {
        if (def == null || def.dailyLimit <= 0)
        {
            return;
        }
        String dayKey = VAR_DAILY_PREFIX + def.commandName + ".day";
        String countKey = VAR_DAILY_PREFIX + def.commandName + ".count";
        int day = getCalendarTime() / 86400;
        int storedDay = utils.getIntScriptVar(self, dayKey);
        int count = utils.getIntScriptVar(self, countKey);
        if (storedDay != day)
        {
            count = 0;
        }
        utils.setScriptVar(self, dayKey, day);
        utils.setScriptVar(self, countKey, count + 1);
    }

    private float getAbilityFloat(PerkDefinition def, String key, float defaultValue)
    {
        if (def == null || def.abilityData == null)
        {
            return defaultValue;
        }
        String value = def.abilityData.get(key.toLowerCase());
        if (value == null || value.length() == 0)
        {
            return defaultValue;
        }
        try
        {
            return Float.parseFloat(value);
        }
        catch (NumberFormatException err)
        {
            return defaultValue;
        }
    }

    private int getAbilityInt(PerkDefinition def, String key, int defaultValue)
    {
        if (def == null || def.abilityData == null)
        {
            return defaultValue;
        }
        String value = def.abilityData.get(key.toLowerCase());
        if (value == null || value.length() == 0)
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException err)
        {
            return defaultValue;
        }
    }

    private String getAbilityString(PerkDefinition def, String key, String defaultValue)
    {
        if (def == null || def.abilityData == null)
        {
            return defaultValue;
        }
        String value = def.abilityData.get(key.toLowerCase());
        return (value == null || value.length() == 0) ? defaultValue : value;
    }

    public int species_packfocus(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        PerkDefinition def = requirePerkCommand(self, "packfocus");
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id groupId = getGroupObject(self);
        if (!isIdValid(groupId))
        {
            sendSystemMessage(self, "You must be grouped to focus your pack.", "");
            return SCRIPT_CONTINUE;
        }
        if (!triggerCooldown(self, def))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id[] members = getGroupMemberIds(groupId);
        if ((members == null) || (members.length == 0))
        {
            sendSystemMessage(self, "No allies are close enough to receive your focus.", "");
            return SCRIPT_CONTINUE;
        }
        location ping = getLocation(self);
        String effect = getAbilityString(def, "effect", "clienteffect/combat_pt_orbitalstrike_low_pt.cef");
        String name = getName(self);
        if (name == null)
        {
            name = "An ally";
        }
        int x = Math.round(ping.x);
        int z = Math.round(ping.z);
        String message = "[Pack Focus] " + name + " pinged their location at (" + x + ", " + z + ").";
        for (obj_id member : members)
        {
            if (!isIdValid(member))
            {
                continue;
            }
            if (isPlayer(member))
            {
                playClientEffectLoc(member, effect, ping, 0);
                sendSystemMessage(member, message, "");
            }
        }
        sendSystemMessage(self, "Your instincts broadcast a tactical ping to the pack.", "");
        return SCRIPT_CONTINUE;
    }

    public int species_marktarget(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        PerkDefinition def = requirePerkCommand(self, "marktarget");
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id actual = target;
        if (!isIdValid(actual))
        {
            actual = getIntendedTarget(self);
        }
        if (!isIdValid(actual))
        {
            actual = getLookAtTarget(self);
        }
        if (!isIdValid(actual))
        {
            sendSystemMessage(self, "You must have a target selected to mark.", "");
            return SCRIPT_CONTINUE;
        }
        if (!triggerCooldown(self, def))
        {
            return SCRIPT_CONTINUE;
        }
        String debuff = getAbilityString(def, "debuff", "species_marktarget_debuff");
        if ((debuff == null) || (debuff.length() == 0))
        {
            debuff = "species_marktarget_debuff";
        }
        buff.applyBuff(actual, debuff);
        playClientEffectLoc(self, "clienteffect/combat_pt_target.cef", getLocation(actual), 0);
        sendSystemMessage(self, "Target marked. Allies deal additional pressure while the mark holds.", "");
        return SCRIPT_CONTINUE;
    }

    public int species_hydrostaticleap(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        PerkDefinition def = requirePerkCommand(self, "hydrostaticleap");
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        if (!triggerCooldown(self, def))
        {
            return SCRIPT_CONTINUE;
        }
        float distance = getAbilityFloat(def, "distance", 12.0f);
        location start = getLocation(self);
        location destination = (location)start.clone();
        float yaw = getYaw(self);
        destination.x += distance * (float)Math.sin(yaw);
        destination.z += distance * (float)Math.cos(yaw);
        destination.y = getHeightAtLocation(destination.x, destination.z);
        clearState(self, STATE_IMMOBILIZED);
        clearState(self, STATE_DIZZY);
        setLocation(self, destination);
        String effect = getAbilityString(def, "effect", "clienteffect/combat_pt_jump.cef");
        playClientEffectLoc(self, effect, destination, 0);
        sendSystemMessage(self, "You surge forward with a hydrostatic leap.", "");
        return SCRIPT_CONTINUE;
    }

    private void clearState(obj_id target, int state)
    {
        setState(target, state, false);
    }

    public int species_impishgrin(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        PerkDefinition def = requirePerkCommand(self, "impishgrin");
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        if (!triggerCooldown(self, def))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id[] haters = getHateList(self);
        if ((haters == null) || (haters.length == 0))
        {
            sendSystemMessage(self, "No one currently holds aggro on you.", "");
            return SCRIPT_CONTINUE;
        }
        float reduction = getAbilityFloat(def, "reduction", 0.85f);
        for (obj_id hater : haters)
        {
            if (!isIdValid(hater))
            {
                continue;
            }
            float hate = getHate(hater, self);
            if (hate > 0)
            {
                setHate(hater, self, hate * reduction);
            }
        }
        String effect = getAbilityString(def, "effect", "clienteffect/combat_pt_escape.cef");
        playClientEffectLoc(self, effect, getLocation(self), 0);
        sendSystemMessage(self, "You flash an impish grin and slip out of the spotlight.", "");
        return SCRIPT_CONTINUE;
    }

    public int species_forcesymmetry(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        PerkDefinition def = requirePerkCommand(self, "forcesymmetry");
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        if (!triggerCooldown(self, def))
        {
            return SCRIPT_CONTINUE;
        }
        String buffName = getAbilityString(def, "buff", "species_mirialan_meditation");
        if ((buffName == null) || (buffName.length() == 0))
        {
            buffName = "species_mirialan_meditation";
        }
        if (buff.hasBuff(self, buffName))
        {
            sendSystemMessage(self, "Force Symmetry is already centering your spirit.", "");
            return SCRIPT_CONTINUE;
        }
        buff.applyBuff(self, buffName);
        sendSystemMessage(self, "You settle into a deep meditation, enhancing Force and craft.", "");
        return SCRIPT_CONTINUE;
    }

    public int species_stalk(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        PerkDefinition def = requirePerkCommand(self, "stalk");
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        if (!triggerCooldown(self, def))
        {
            return SCRIPT_CONTINUE;
        }
        String buffName = getAbilityString(def, "buff", "species_cathar_stalk");
        if ((buffName == null) || (buffName.length() == 0))
        {
            buffName = "species_cathar_stalk";
        }
        buff.applyBuff(self, buffName);
        sendSystemMessage(self, "You break into a low, predatory sprint.", "");
        return SCRIPT_CONTINUE;
    }

    public int species_slicerintuition(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        PerkDefinition def = requirePerkCommand(self, "slicerintuition");
        if (def == null)
        {
            return SCRIPT_CONTINUE;
        }
        int remaining = getDailyRemaining(self, def);
        if ((def.dailyLimit > 0) && (remaining <= 0))
        {
            sendSystemMessage(self, "You have already used all of your intuition charges today.", "");
            return SCRIPT_CONTINUE;
        }
        if (!triggerCooldown(self, def))
        {
            return SCRIPT_CONTINUE;
        }
        incrementDailyUse(self, def);
        String buffName = getAbilityString(def, "buff", "species_bothan_slicer_focus");
        if ((buffName != null) && (buffName.length() > 0))
        {
            buff.applyBuff(self, buffName);
        }
        if (def.dailyLimit > 0)
        {
            int left = getDailyRemaining(self, def);
            sendSystemMessage(self, "Slicer intuition heightens your senses. Remaining uses today: " + left + ".", "");
        }
        else
        {
            sendSystemMessage(self, "Slicer intuition heightens your senses for upcoming slices.", "");
        }
        return SCRIPT_CONTINUE;
    }
}
