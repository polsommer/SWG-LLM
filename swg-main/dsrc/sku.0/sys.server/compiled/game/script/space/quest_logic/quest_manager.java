package script.space.quest_logic;

import script.dictionary;
import script.library.space_quest;
import script.library.utils;
import script.location;
import script.obj_id;

public class quest_manager extends script.base_script {

    public quest_manager() {}

    // ---- constants / keys ----
    private static final String LIST_SUFFIX = "_list";
    private static final String OBJVAR_NAV_NAME = "nav_name";
    private static final String OBJVAR_SPAWNER_NAME = "strSpawnerName";
    private static final String OBJVAR_WP_COLOR = "wp.color";
    private static final String DEFAULT_WAYPOINT_COLOR = "space";
    private static final String LOGP = "[QuestMgr] ";

    // ---- lifecycle ----

    public int OnInitialize(obj_id self) throws InterruptedException {
        registerNamedObject(space_quest.QUEST_MANAGER, self);
        debugServerConsoleMsg(self, LOGP + "Initialized & registered as " + space_quest.QUEST_MANAGER);
        return SCRIPT_CONTINUE;
    }


    public int OnAttach(obj_id self) throws InterruptedException {
        // Ensure we’re registered even after reloads
        registerNamedObject(space_quest.QUEST_MANAGER, self);
        return SCRIPT_CONTINUE;
    }

    // ---- register quest point into a typed list ----
    public int registerQuestLocation(obj_id self, dictionary params) throws InterruptedException {
        if (params == null) return SCRIPT_CONTINUE;

        obj_id point = params.getObjId("point");
        if (!isIdValid(point)) {
            debugServerConsoleMsg(self, LOGP + "registerQuestLocation: invalid 'point'");
            return SCRIPT_CONTINUE;
        }
        String type = params.getString("type");
        if (type == null || type.length() == 0) {
            debugServerConsoleMsg(self, LOGP + "registerQuestLocation: empty 'type' for " + point);
            return SCRIPT_CONTINUE;
        }

        String listKey = type + LIST_SUFFIX;
        String newName = trimOrEmpty(getStringObjVar(point, OBJVAR_NAV_NAME));

        obj_id[] points = utils.getObjIdArrayScriptVar(self, listKey);
        if (points == null || points.length == 0) {
            utils.setScriptVar(self, listKey, new obj_id[]{ point });
            debugServerConsoleMsg(self, LOGP + "Registered first " + type + " point " + point + " (\"" + newName + "\")");
            return SCRIPT_CONTINUE;
        }

        // dedupe by object id
        for (obj_id p : points) {
            if (p == point) {
                debugServerConsoleMsg(self, LOGP + "Ignoring duplicate object for " + type + " : " + point);
                return SCRIPT_CONTINUE;
            }
        }
        // dedupe by nav_name (case/trim)
        for (obj_id p : points) {
            String existing = trimOrEmpty(getStringObjVar(p, OBJVAR_NAV_NAME));
            if (!existing.isEmpty() && !newName.isEmpty() && existing.equalsIgnoreCase(newName)) {
                debugServerConsoleMsg(self, LOGP + "Duplicate name for " + type + " : \"" + newName + "\" (" + point + " vs " + p + ")");
                return SCRIPT_CONTINUE;
            }
        }

        utils.setScriptVar(self, listKey, append(points, point));
        debugServerConsoleMsg(self, LOGP + "Registered " + type + " point " + point + " (\"" + newName + "\") total=" + (points.length + 1));
        return SCRIPT_CONTINUE;
    }

    // ---- create a waypoint to a specific spawner by name ----
    public int createWaypointToSpawner(obj_id self, dictionary params) throws InterruptedException {
        if (params == null) return SCRIPT_CONTINUE;

        obj_id quest   = params.getObjId("quest");
        obj_id player  = params.getObjId("player");
        String name    = params.getString("name");
        String spawner = params.getString("spawner");           // expected OBJVAR_SPAWNER_NAME
        int taskId     = params.getInt("taskId");
        String questName = params.getString("questName");
        int questId      = params.getInt("questId");

        if (!isIdValid(player)) {
            debugServerConsoleMsg(self, LOGP + "createWaypointToSpawner: invalid player");
            return SCRIPT_CONTINUE;
        }
        if (spawner == null || spawner.length() == 0) {
            sendSystemMessageTestingOnly(player, "Error: Missing spawner name.");
            return SCRIPT_CONTINUE;
        }

        obj_id[] navs = utils.getObjIdArrayScriptVar(self, "spawner" + LIST_SUFFIX);
        if (navs == null || navs.length == 0) {
            sendSystemMessageTestingOnly(player, "Error: No spawners registered. Ensure createZoneObjects=1 and registration runs.");
            return SCRIPT_CONTINUE;
        }

        // Try exact match first; fallback to case-insensitive/trim
        obj_id match = null;
        String targetKey = trimOrEmpty(spawner);
        for (obj_id nav : navs) {
            if (!isIdValid(nav)) continue;
            String cur = getStringObjVar(nav, OBJVAR_SPAWNER_NAME);
            if (cur != null && cur.equals(targetKey)) { match = nav; break; }
        }
        if (match == null) {
            for (obj_id nav : navs) {
                if (!isIdValid(nav)) continue;
                String cur = trimOrEmpty(getStringObjVar(nav, OBJVAR_SPAWNER_NAME));
                if (!cur.isEmpty() && cur.equalsIgnoreCase(targetKey)) { match = nav; break; }
            }
        }

        if (match == null) {
            sendSystemMessageTestingOnly(player,
                    "Error: Failed to find spawner '" + spawner + "'. " + navs.length +
                            " spawners searched. Object may not exist or server needs 'createZoneObjects=1'.");
            return SCRIPT_CONTINUE;
        }

        location loc = getLocation(match);
        if (loc == null) {
            sendSystemMessageTestingOnly(player, "Error: Spawner '" + spawner + "' has no valid location.");
            return SCRIPT_CONTINUE;
        }

        obj_id wp = createWaypointInDatapad(player, loc);
        if (!isIdValid(wp)) {
            sendSystemMessageTestingOnly(player, "Error: Could not create waypoint in datapad.");
            return SCRIPT_CONTINUE;
        }

        // Configure waypoint
        setWaypointVisible(wp, true);
        setWaypointActive(wp, true);
        if (name != null && name.length() > 0) setWaypointName(wp, name);
        setWaypointColor(wp, DEFAULT_WAYPOINT_COLOR);
        setObjVar(wp, OBJVAR_WP_COLOR, DEFAULT_WAYPOINT_COLOR); // persist color (our upgraded base_waypoint will reapply)

        // Hook quest/task
        questActivateTask(questId, taskId, player);
        if (questName != null && questName.length() > 0) {
            questSetQuestTaskLocation(player, questName, taskId, loc);
        }

        // Notify quest script
        dictionary out = new dictionary();
        out.put("waypoint", wp);
        messageTo(quest, "createdWaypointToSpawner", out, 1.0f, false);

        debugServerConsoleMsg(self, LOGP + "Created waypoint " + wp + " to spawner '" + spawner + "' for " + player);
        return SCRIPT_CONTINUE;
    }

    // ---- small helpers ----
    private static String trimOrEmpty(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static obj_id[] append(obj_id[] arr, obj_id v) {
        if (arr == null || arr.length == 0) return new obj_id[]{ v };
        obj_id[] out = new obj_id[arr.length + 1];
        System.arraycopy(arr, 0, out, 0, arr.length);
        out[arr.length] = v;
        return out;
    }
}

