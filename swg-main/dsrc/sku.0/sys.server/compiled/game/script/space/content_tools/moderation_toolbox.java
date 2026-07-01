package script.space.content_tools;

import script.dictionary;
import script.library.datatable;
import script.library.dump;
import script.library.player_structure;
import script.library.space_utils;
import script.library.utils;
import script.location;
import script.obj_id;
import script.transform;
import script.vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class moderation_toolbox extends script.base_script
{
    private static final float DEFAULT_RADIUS = 128.0f;
    private static final int DEFAULT_TEMPLATE_LIMIT = 12;
    private static final String SPAWNER_TEMPLATE = "object/tangible/space/content_infrastructure/basic_spawner.iff";

    public moderation_toolbox()
    {
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        sendSystemMessageTestingOnly(self, "Moderation toolbox ready. Say 'toolHelp' for command list.");
        return SCRIPT_CONTINUE;
    }

    public int OnSpeaking(obj_id self, String text) throws InterruptedException
    {
        if ((text == null) || (text.length() == 0))
        {
            return SCRIPT_CONTINUE;
        }
        String[] tokens = split(text, ' ');
        if ((tokens == null) || (tokens.length == 0))
        {
            return SCRIPT_CONTINUE;
        }
        String command = tokens[0].toLowerCase();
        switch (command)
        {
            case "toolhelp":
                handleHelp(self);
                break;
            case "scanplayers":
                handleScanPlayers(self, tokens);
                break;
            case "inspecttarget":
                handleInspectTarget(self);
                break;
            case "listobjvars":
                handleListObjvars(self);
                break;
            case "listscripts":
                handleListScripts(self);
                break;
            case "listspawners":
                handleListSpawners(self, tokens);
                break;
            case "templatesummary":
                handleTemplateSummary(self, tokens);
                break;
            case "exportselection":
                handleExportSelection(self, tokens);
                break;
            default:
                break;
        }
        return SCRIPT_CONTINUE;
    }

    private void handleHelp(obj_id self) throws InterruptedException
    {
        sendSystemMessageTestingOnly(self, "toolHelp - show this list");
        sendSystemMessageTestingOnly(self, "scanPlayers [radius] - list players in range with distance and area");
        sendSystemMessageTestingOnly(self, "inspectTarget - summarize current look-at target");
        sendSystemMessageTestingOnly(self, "listObjvars - readable objvars for look-at target");
        sendSystemMessageTestingOnly(self, "listScripts - scripts attached to look-at target");
        sendSystemMessageTestingOnly(self, "listSpawners [radius] - show nearby space spawners");
        sendSystemMessageTestingOnly(self, "templateSummary [radius] [limit] - count dumpable objects in area");
        sendSystemMessageTestingOnly(self, "exportSelection <radius> [file] - dump objects in range to datatable backup");
    }

    private void handleScanPlayers(obj_id self, String[] tokens) throws InterruptedException
    {
        float radius = parseFloat(tokens, 1, DEFAULT_RADIUS);
        obj_id[] players = getPlayerCreaturesInRange(self, radius);
        if ((players == null) || (players.length == 0))
        {
            sendSystemMessageTestingOnly(self, "No players found within " + radius + "m.");
            return;
        }
        sendSystemMessageTestingOnly(self, "Players within " + radius + "m: " + players.length);
        for (obj_id player : players)
        {
            if (!isIdValid(player) || isDead(player))
            {
                continue;
            }
            float distance = getDistance(self, player);
            location loc = getLocation(player);
            StringBuilder line = new StringBuilder();
            line.append(getFirstName(player)).append(" (").append(player).append(") ");
            line.append(String.format("%.1fm", distance));
            line.append(" @ ").append(loc.area).append(" ");
            line.append(String.format("(%.1f, %.1f, %.1f)", loc.x, loc.y, loc.z));
            if (isGod(player))
            {
                line.append(" [GOD]");
            }
            if (isIncapacitated(player))
            {
                line.append(" [INCAPPED]");
            }
            sendSystemMessageTestingOnly(self, line.toString());
        }
    }

    private void handleInspectTarget(obj_id self) throws InterruptedException
    {
        obj_id target = getLookAtTarget(self);
        if (!isIdValid(target))
        {
            target = self;
        }
        if (!isIdValid(target))
        {
            sendSystemMessageTestingOnly(self, "No valid target to inspect.");
            return;
        }
        location loc = getLocation(target);
        sendSystemMessageTestingOnly(self, "Inspecting " + target + " template=" + getTemplateName(target));
        sendSystemMessageTestingOnly(self, "Location: " + loc.area + " (" + loc.x + ", " + loc.y + ", " + loc.z + ")");
        if (isPlayer(target))
        {
            sendSystemMessageTestingOnly(self, "Player name: " + getFirstName(target));
        }
        sendMultiLine(self, "Scripts", dump.getReadableScripts(target));
        sendMultiLine(self, "Objvars", dump.getReadableObjVars(target));
    }

    private void handleListObjvars(obj_id self) throws InterruptedException
    {
        obj_id target = getLookAtTarget(self);
        if (!isIdValid(target))
        {
            target = self;
        }
        if (!isIdValid(target))
        {
            sendSystemMessageTestingOnly(self, "No valid target for objvar listing.");
            return;
        }
        sendMultiLine(self, "Objvars", dump.getReadableObjVars(target));
    }

    private void handleListScripts(obj_id self) throws InterruptedException
    {
        obj_id target = getLookAtTarget(self);
        if (!isIdValid(target))
        {
            target = self;
        }
        if (!isIdValid(target))
        {
            sendSystemMessageTestingOnly(self, "No valid target for script listing.");
            return;
        }
        sendMultiLine(self, "Scripts", dump.getReadableScripts(target));
    }

    private void handleListSpawners(obj_id self, String[] tokens) throws InterruptedException
    {
        float radius = parseFloat(tokens, 1, DEFAULT_RADIUS);
        obj_id[] objects = getObjectsInRange(self, radius);
        if ((objects == null) || (objects.length == 0))
        {
            sendSystemMessageTestingOnly(self, "No objects found within " + radius + "m.");
            return;
        }
        int count = 0;
        for (obj_id object : objects)
        {
            if (!isIdValid(object))
            {
                continue;
            }
            if (!SPAWNER_TEMPLATE.equals(getTemplateName(object)))
            {
                continue;
            }
            ++count;
            String name = hasObjVar(object, "strSpawnerName") ? getStringObjVar(object, "strSpawnerName") : "(unnamed)";
            String type = hasObjVar(object, "strSpawnerType") ? getStringObjVar(object, "strSpawnerType") : "unknown";
            String behavior = hasObjVar(object, "strDefaultBehavior") ? getStringObjVar(object, "strDefaultBehavior") : "n/a";
            int spawnCount = hasObjVar(object, "intSpawnCount") ? getIntObjVar(object, "intSpawnCount") : 0;
            float minTime = hasObjVar(object, "fltMinSpawnTime") ? getFloatObjVar(object, "fltMinSpawnTime") : 0.0f;
            float maxTime = hasObjVar(object, "fltMaxSpawnTime") ? getFloatObjVar(object, "fltMaxSpawnTime") : 0.0f;
            sendSystemMessageTestingOnly(self, "Spawner " + name + " type=" + type + " behavior=" + behavior + " count=" + spawnCount + " min=" + minTime + " max=" + maxTime);
        }
        if (count == 0)
        {
            sendSystemMessageTestingOnly(self, "No spawners found within " + radius + "m.");
        }
        else
        {
            sendSystemMessageTestingOnly(self, "Total spawners in range: " + count);
        }
    }

    private void handleTemplateSummary(obj_id self, String[] tokens) throws InterruptedException
    {
        float radius = parseFloat(tokens, 1, DEFAULT_RADIUS);
        int limit = parseInt(tokens, 2, DEFAULT_TEMPLATE_LIMIT);
        obj_id[] objects = getObjectsInRange(self, radius);
        if ((objects == null) || (objects.length == 0))
        {
            sendSystemMessageTestingOnly(self, "No objects found within " + radius + "m.");
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        int considered = 0;
        for (obj_id object : objects)
        {
            if (!isIdValid(object) || !object.isLoaded())
            {
                continue;
            }
            if (!isDumpable(object, false))
            {
                continue;
            }
            String template = getTemplateName(object);
            if ((template == null) || template.equals(""))
            {
                continue;
            }
            counts.put(template, counts.getOrDefault(template, 0) + 1);
            ++considered;
        }
        if (counts.isEmpty())
        {
            sendSystemMessageTestingOnly(self, "No dumpable objects found within " + radius + "m.");
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        Collections.sort(entries, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        sendSystemMessageTestingOnly(self, "Template summary within " + radius + "m (" + considered + " objects considered):");
        int shown = 0;
        for (Map.Entry<String, Integer> entry : entries)
        {
            sendSystemMessageTestingOnly(self, entry.getKey() + ": " + entry.getValue());
            ++shown;
            if (shown >= limit)
            {
                break;
            }
        }
    }

    private void handleExportSelection(obj_id self, String[] tokens) throws InterruptedException
    {
        if (tokens.length < 2)
        {
            sendSystemMessageTestingOnly(self, "Usage: exportSelection <radius> [file]");
            return;
        }
        float radius = parseFloat(tokens, 1, DEFAULT_RADIUS);
        String baseName = (tokens.length > 2) ? tokens[2] : "selection";
        if (!baseName.endsWith(".tab"))
        {
            baseName += ".tab";
        }
        location here = getLocation(self);
        String area = here.area;
        String path = "datatables/space_zones/buildout/backups/" + area + "_" + baseName;
        if (!createDataTable(path))
        {
            sendSystemMessageTestingOnly(self, "Unable to create datatable " + path);
            return;
        }
        obj_id[] objects = getObjectsInRange(self, radius);
        if ((objects == null) || (objects.length == 0))
        {
            sendSystemMessageTestingOnly(self, "No objects to export within " + radius + "m.");
            return;
        }
        int saved = 0;
        for (obj_id object : objects)
        {
            if (!isIdValid(object) || !object.isLoaded() || !isDumpable(object, false))
            {
                continue;
            }
            transform xform = getTransform_o2p(object);
            vector j = xform.getLocalFrameJ_p();
            vector k = xform.getLocalFrameK_p();
            vector p = xform.getPosition_p();
            dictionary row = new dictionary();
            row.put("strObject", getTemplateName(object));
            row.put("fltJX", j.x);
            row.put("fltJY", j.y);
            row.put("fltJZ", j.z);
            row.put("fltKX", k.x);
            row.put("fltKY", k.y);
            row.put("fltKZ", k.z);
            row.put("fltPX", p.x);
            row.put("fltPY", p.y);
            row.put("fltPZ", p.z);
            row.put("strObjVars", getPackedObjvars(object));
            row.put("strScripts", utils.getPackedScripts(object));
            datatable.serverDataTableAddRow(path, row);
            ++saved;
        }
        sendSystemMessageTestingOnly(self, "Saved " + saved + " objects to " + path);
    }

    private float parseFloat(String[] tokens, int index, float defaultValue)
    {
        if ((tokens == null) || (tokens.length <= index))
        {
            return defaultValue;
        }
        try
        {
            return Float.parseFloat(tokens[index]);
        }
        catch (NumberFormatException err)
        {
            return defaultValue;
        }
    }

    private int parseInt(String[] tokens, int index, int defaultValue)
    {
        if ((tokens == null) || (tokens.length <= index))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(tokens[index]);
        }
        catch (NumberFormatException err)
        {
            return defaultValue;
        }
    }

    private boolean isDumpable(obj_id object, boolean includeCells) throws InterruptedException
    {
        if (!isIdValid(object) || isPlayer(object))
        {
            return false;
        }
        if (hasObjVar(object, "intAlwaysDump"))
        {
            return true;
        }
        if (player_structure.isBuilding(object) || player_structure.isInstallation(object))
        {
            return true;
        }
        if (space_utils.isPlayerControlledShip(object))
        {
            return false;
        }
        String template = getTemplateName(object);
        if ((template == null) || (template.length() == 0))
        {
            return false;
        }
        if (template.contains("object/cell"))
        {
            return false;
        }
        if (hasObjVar(object, "intNoDump"))
        {
            return false;
        }
        if (!includeCells)
        {
            location loc = getLocation(object);
            if ((loc != null) && isIdValid(loc.cell))
            {
                return false;
            }
        }
        return true;
    }

    private boolean createDataTable(String path) throws InterruptedException
    {
        String[] types =
        {
            "s",
            "f",
            "f",
            "f",
            "f",
            "f",
            "f",
            "f",
            "f",
            "f",
            "s",
            "s"
        };
        String[] headers =
        {
            "strObject",
            "fltJX",
            "fltJY",
            "fltJZ",
            "fltKX",
            "fltKY",
            "fltKZ",
            "fltPX",
            "fltPY",
            "fltPZ",
            "strObjVars",
            "strScripts"
        };
        return datatable.createDataTable(path, headers, types);
    }

    private void sendMultiLine(obj_id self, String header, String data) throws InterruptedException
    {
        if ((data == null) || (data.length() == 0))
        {
            sendSystemMessageTestingOnly(self, header + ": <none>");
            return;
        }
        sendSystemMessageTestingOnly(self, header + ":");
        String[] rows = split(data, '\n');
        for (String row : rows)
        {
            if ((row != null) && (row.trim().length() > 0))
            {
                sendSystemMessageTestingOnly(self, row.trim());
            }
        }
    }
}
