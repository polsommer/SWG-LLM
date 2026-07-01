package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

public class npc_presence extends script.base_script
{
    public npc_presence()
    {
    }

    // Primary /who population remains in native service. This cluster feed is a dedicated
    // secondary channel for simulated player-like NPC entries.
    public static final String WHO_SIMULATED_MANAGER = "who_simulated_presence";
    public static final String WHO_SIMULATED_KEY_PREFIX = "simNpc.";
    public static final String INTERNAL_MARKER = "simulated_npc";

    public static void registerOrUpdateSimulatedPresence(obj_id npc, String sourceEvent) throws InterruptedException
    {
        if (!isEligibleSimulatedPresenceNpc(npc))
        {
            removeSimulatedPresence(npc);
            return;
        }

        dictionary entry = new dictionary();
        entry.put("name", getPresenceName(npc));
        entry.put("zone", getPresenceZone(npc));
        entry.put("faction", getPresenceFaction(npc));
        entry.put("profession", behavior_telemetry.getNpcProfessionPath(npc));
        entry.put("objectId", npc);
        entry.put("internalMarker", INTERNAL_MARKER); // moderation/admin tooling only
        entry.put("sourceEvent", sourceEvent);
        replaceClusterWideData(WHO_SIMULATED_MANAGER, getPresenceKey(npc), entry, false, -1);
    }

    public static void removeSimulatedPresence(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        removeClusterWideData(WHO_SIMULATED_MANAGER, getPresenceKey(npc), -1);
    }

    public static boolean isEligibleSimulatedPresenceNpc(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || isPlayer(npc) || pet_lib.isPet(npc) || isIncapacitated(npc) || isDead(npc))
        {
            return false;
        }

        String creatureType = getStringObjVar(npc, "creature_type");
        if (!npc_simulation.shouldUseAdaptiveArchetypeController(npc, creatureType, npc))
        {
            return false;
        }

        return hasScript(npc, "city.city_wander") || hasScript(npc, "city.city_pathing_npc") || hasScript(npc, npc_simulation.ADAPTIVE_CONTROLLER_SCRIPT);
    }

    private static String getPresenceKey(obj_id npc) throws InterruptedException
    {
        return WHO_SIMULATED_KEY_PREFIX + npc;
    }

    private static String getPresenceName(obj_id npc) throws InterruptedException
    {
        String name = getName(npc);
        if (name == null || name.length() < 1)
        {
            return "Citizen";
        }
        return name;
    }

    private static String getPresenceZone(obj_id npc) throws InterruptedException
    {
        location loc = getLocation(npc);
        if (loc == null || loc.area == null)
        {
            return "unknown";
        }
        return toLower(loc.area);
    }

    private static String getPresenceFaction(obj_id npc) throws InterruptedException
    {
        int faction = factions.getFactionFlag(npc);
        if (faction == factions.FACTION_FLAG_IMPERIAL)
        {
            return "imperial";
        }
        if (faction == factions.FACTION_FLAG_REBEL)
        {
            return "rebel";
        }
        return "neutral";
    }
}
