package script.systems.events;

import script.*;

public class easter_egg_hunt_data extends script.base_script
{
    public static final String EVENT_ID = "spring_egg_hunt_2026";
    public static final String OBJVAR_ROOT = "events.easterEggHunt";
    public static final String OBJVAR_DISCOVERED = OBJVAR_ROOT + ".discovered";
    public static final String OBJVAR_MAP_COMPLETE = OBJVAR_ROOT + ".mapComplete";
    public static final String OBJVAR_EVENT_COMPLETE = OBJVAR_ROOT + ".eventComplete";
    public static final String OBJVAR_REWARD_GRANTED = OBJVAR_ROOT + ".rewardGranted";
    public static final String OBJVAR_ARCHIVED_EVENT = OBJVAR_ROOT + ".archivedEventId";
    public static final String OBJVAR_CURRENT_EVENT = OBJVAR_ROOT + ".currentEventId";
    public static final String SECRET_TITLE_SKILL = "event_title_easter_eggmaster";
    public static final String SECRET_EMOTE_COMMAND = "easterEggVictory";
    public static final String EGG_TEMPLATE = "object/tangible/loot/collectible/event/easter_egg_hunt_egg.iff";

    public static final String[] TARGET_MAPS = {"tatooine", "naboo", "corellia"};
    // placement data: map, collectibleUniqueId, x, z, y
    public static final String[][] PLACEMENTS = {
        {"tatooine", "TAT_EGG_001", "3521.5", "-4820.1", "5.0"},
        {"naboo", "NAB_EGG_001", "-5520.0", "4301.0", "12.0"},
        {"corellia", "COR_EGG_001", "1012.3", "-2034.2", "28.0"}
    };
}
