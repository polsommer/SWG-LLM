package script.library;

import script.location;
import script.obj_id;

public class npc_simulation extends script.base_script
{
    public npc_simulation()
    {
    }

    public static final String ADAPTIVE_CONTROLLER_SCRIPT = "npc.simulation.adaptive_archetype_controller";
    public static final String OBJVAR_ENABLE = "npc.simulation.enableAdaptiveArchetypeController";
    public static final String OBJVAR_DISABLE = "npc.simulation.disableAdaptiveArchetypeController";
    public static final String OBJVAR_SPECIAL_UNIQUE = "npc.simulation.specialUniqueAiNpc";
    public static final String OBJVAR_ADAPTIVE_ELIGIBLE_SPAWNED = "npc.simulation.adaptiveEligibleSpawned";
    public static final String OBJVAR_TEMPLATE_ALLOWLIST = "npc.simulation.adaptiveController.templates";
    public static final String OBJVAR_ZONE_ALLOWLIST = "npc.simulation.adaptiveController.zones";
    public static final String CONFIG_ENABLE = "enableAdaptiveNpcArchetypeController";
    public static final String CONFIG_GLOBAL_DISABLE = "disableAdaptiveNpcArchetypeController";
    public static final String CONFIG_TEMPLATE_ALLOWLIST = "adaptiveNpcArchetypeTemplates";
    public static final String CONFIG_ZONE_ALLOWLIST = "adaptiveNpcArchetypeZones";
    public static final String DEFAULT_ADAPTIVE_TEMPLATE_ALLOWLIST = "commoner,commoner_fat,commoner_naboo,commoner_old,commoner_tatooine,commoner_technician,commoner_twk_female,commoner_twk_male,villager,businessman,noble";
    public static final String DEFAULT_ADAPTIVE_ZONE_ALLOWLIST = "corellia_coronet,naboo_keren,naboo_theed,talus_dearic,tatooine_anchorhead,tatooine_bestine,tatooine_mos_eisley,tatooine_mos_entha,tatooine_mos_espa,tatooine_wayfar";

    public static void attachAdaptiveArchetypeControllerIfAllowed(obj_id npc, String creatureName, obj_id source) throws InterruptedException
    {
        if (!shouldUseAdaptiveArchetypeController(npc, creatureName, source))
        {
            return;
        }
        if (!hasScript(npc, ADAPTIVE_CONTROLLER_SCRIPT))
        {
            attachScript(npc, ADAPTIVE_CONTROLLER_SCRIPT);
        }
    }

    public static boolean shouldUseAdaptiveArchetypeController(obj_id npc, String creatureName, obj_id source) throws InterruptedException
    {
        if (!isIdValid(npc) || isPlayer(npc))
        {
            logAdaptiveDecision(npc, source, false, "invalidNpcOrPlayer");
            return false;
        }
        if (isDisabledViaObjVar(npc) || isDisabledViaObjVar(source))
        {
            logAdaptiveDecision(npc, source, false, "disabledViaObjVar");
            return false;
        }

        if (utils.checkConfigFlag("GameServer", CONFIG_GLOBAL_DISABLE))
        {
            logAdaptiveDecision(npc, source, false, "disabledViaConfig:" + CONFIG_GLOBAL_DISABLE);
            return false;
        }

        boolean specialUniqueOverride = isSpecialUniqueNpc(npc, source);
        boolean enabledViaObjVar = isEnabledViaObjVar(npc) || isEnabledViaObjVar(source);

        if (specialUniqueOverride)
        {
            logAdaptiveDecision(npc, source, true, "enabledViaSpecialUniqueOverride");
            return true;
        }

        if (enabledViaObjVar)
        {
            logAdaptiveDecision(npc, source, true, "enabledViaObjVar");
            return true;
        }

        if (!isAdaptiveConfigEnabled())
        {
            logAdaptiveDecision(npc, source, false, "disabledViaConfig:" + CONFIG_ENABLE);
            return false;
        }

        if (!isAdaptiveDefaultRolloutEligible(npc))
        {
            logAdaptiveDecision(npc, source, false, "notEligible:missingAdaptiveEligibleMarker");
            return false;
        }

        String zone = getSceneNameSafe(npc);
        String npcTemplate = getTemplateName(npc);
        String templateToken = creatureName;

        boolean templateMatches = matchesAnyAllowlist(templateToken, npcTemplate, getStringObjVar(source, OBJVAR_TEMPLATE_ALLOWLIST)) ||
                matchesAnyAllowlist(templateToken, npcTemplate, getStringObjVar(npc, OBJVAR_TEMPLATE_ALLOWLIST)) ||
                matchesAnyAllowlist(templateToken, npcTemplate, getTemplateAllowlist());

        boolean zoneMatches = matchesAllowlistValue(zone, getStringObjVar(source, OBJVAR_ZONE_ALLOWLIST)) ||
                matchesAllowlistValue(zone, getStringObjVar(npc, OBJVAR_ZONE_ALLOWLIST)) ||
                matchesAllowlistValue(zone, getZoneAllowlist());

        if (templateMatches)
        {
            logAdaptiveDecision(npc, source, true, "enabledViaAllowlist:template");
            return true;
        }
        if (zoneMatches)
        {
            logAdaptiveDecision(npc, source, true, "enabledViaAllowlist:zone:" + zone);
            return true;
        }

        logAdaptiveDecision(npc, source, false, "notEligible:missingEnableObjVarAndAllowlist");
        return false;
    }

    private static boolean isAdaptiveDefaultRolloutEligible(obj_id npc) throws InterruptedException
    {
        return isIdValid(npc) &&
                hasObjVar(npc, OBJVAR_ADAPTIVE_ELIGIBLE_SPAWNED) &&
                utils.hasScriptVar(npc, "spawnedBy");
    }

    private static boolean isAdaptiveConfigEnabled() throws InterruptedException
    {
        String configuredEnabled = getConfigSetting("GameServer", CONFIG_ENABLE);
        if (configuredEnabled == null || configuredEnabled.length() < 1)
        {
            return true;
        }
        return utils.checkConfigFlag("GameServer", CONFIG_ENABLE);
    }

    private static String getTemplateAllowlist() throws InterruptedException
    {
        String configuredAllowlist = getConfigSetting("GameServer", CONFIG_TEMPLATE_ALLOWLIST);
        if (configuredAllowlist == null || configuredAllowlist.length() < 1)
        {
            return DEFAULT_ADAPTIVE_TEMPLATE_ALLOWLIST;
        }
        return configuredAllowlist;
    }

    private static String getZoneAllowlist() throws InterruptedException
    {
        String configuredAllowlist = getConfigSetting("GameServer", CONFIG_ZONE_ALLOWLIST);
        if (configuredAllowlist == null || configuredAllowlist.length() < 1)
        {
            return DEFAULT_ADAPTIVE_ZONE_ALLOWLIST;
        }
        return configuredAllowlist;
    }

    private static void logAdaptiveDecision(obj_id npc, obj_id source, boolean enabled, String reason) throws InterruptedException
    {
        String zone = isIdValid(npc) ? getSceneNameSafe(npc) : "";
        LOG("npcSimulation", "adaptiveArchetypeController enabled=" + enabled + " reason=" + reason + " npc=" + npc + " source=" + source + " zone=" + zone);
    }

    private static boolean isDisabledViaObjVar(obj_id source) throws InterruptedException
    {
        return isIdValid(source) && hasObjVar(source, OBJVAR_DISABLE);
    }

    private static boolean isEnabledViaObjVar(obj_id source) throws InterruptedException
    {
        return isIdValid(source) && hasObjVar(source, OBJVAR_ENABLE);
    }

    private static boolean isSpecialUniqueNpc(obj_id npc, obj_id source) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return false;
        }

        if (hasObjVar(npc, OBJVAR_SPECIAL_UNIQUE))
        {
            return true;
        }

        // Never inherit special/unique status from a spawner or static source object,
        // otherwise entire static NPC pools can be promoted accidentally.
        return isIdValid(source) && source == npc && hasObjVar(source, OBJVAR_SPECIAL_UNIQUE);
    }

    private static String getSceneNameSafe(obj_id npc) throws InterruptedException
    {
        location loc = getLocation(npc);
        if (loc == null || loc.area == null)
        {
            return "";
        }
        return toLower(loc.area);
    }

    private static boolean matchesAnyAllowlist(String creatureName, String templateName, String allowlist) throws InterruptedException
    {
        return matchesAllowlistValue(creatureName, allowlist) || matchesAllowlistValue(templateName, allowlist);
    }

    private static boolean matchesAllowlistValue(String value, String allowlist) throws InterruptedException
    {
        if (value == null || value.length() < 1 || allowlist == null || allowlist.length() < 1)
        {
            return false;
        }
        String normalizedValue = toLower(value);
        String[] tokens = split(allowlist, ',');
        if (tokens == null || tokens.length < 1)
        {
            return false;
        }
        for (String token : tokens)
        {
            if (token == null)
            {
                continue;
            }
            String normalizedToken = toLower(token.trim());
            if (normalizedToken.length() < 1)
            {
                continue;
            }
            if (normalizedToken.equals("*") || normalizedValue.indexOf(normalizedToken) > -1)
            {
                return true;
            }
        }
        return false;
    }
}
