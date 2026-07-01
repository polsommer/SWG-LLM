package script.dev;

import script.dictionary;
import script.library.sui;
import script.library.utils;
import script.location;
import script.obj_id;
import script.transform;
import script.library.locations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * SWG Buildout Utility
 * For World Building in the Release Game Client
 * To Use: attach developer.devtool to your player and say "info" for commands
 * <p>
 * Most usage will be creating an object either with this script or by some other means,
 * targeting it, and saying "getBuildoutInfo" in spatial which will give you an SUI window
 * of everything you need to type in to add that object to a buildout.
 * <p>
 * *************************************************
 * This is a WORK IN PROGRESS
 * I'm adding it now upon request to help a few people get the gist of buildouts.
 * *************************************************
 * <p>
 * Authors: Aconite
 */
public class devtool extends script.base_script {

    public devtool() {

    }

    public int OnAttach(obj_id self) throws InterruptedException {
        sendSystemMessageTestingOnly(self, "Buildout Utility Attached... say INFO for help.");
        return SCRIPT_CONTINUE;
    }

    public int OnSpeaking(obj_id self, String text) throws InterruptedException, IOException {

        if (!isGod(self)) {
            return SCRIPT_CONTINUE;
        }

        java.util.StringTokenizer tok = new java.util.StringTokenizer(text);
        String command1 = tok.nextToken();
        String command2 = "";
        String command3 = "";
        if (tok.hasMoreTokens()) {
            command2 = tok.nextToken();
            if (tok.hasMoreTokens()) {
                command3 = tok.nextToken();
            }
        }

        // ===========================================================================
        // ===== info
        // ===========================================================================
        if (command1.equalsIgnoreCase("info")) {
            sendSystemMessageTestingOnly(self, "Instructions for buildout utility sent to your console.");
            sendConsoleMessage(self, "\\#ffff00 ============ devtool spatial chat/speak commands ============ \\#.");
            sendConsoleMessage(self, "\\#00ffff clearObjects \\#.");
            sendConsoleMessage(self, "destroys all objects within 500m with devtool.write objvar");
            sendConsoleMessage(self, "\\#00ffff createObject \\#bfff00 <template> \\#.");
            sendConsoleMessage(self, "spawns at your location the specified object adding devtool.write objvar automatically");
            sendConsoleMessage(self, "\\#00ffff getBuildoutArea \\#.");
            sendConsoleMessage(self, "returns the current buildout area you are in");
            sendConsoleMessage(self, "\\#00ffff getBuildoutInfo \\#bfff00 [target OR <oid>] \\#.");
            sendConsoleMessage(self, "returns all buildout information for the specified object");
            sendConsoleMessage(self, "\\#00ffff getObjects \\#.");
            sendConsoleMessage(self, "returns all objects within 500m with devtool.write objvar");
            sendConsoleMessage(self, "\\#00ffff addBuildoutEntry \\#bfff00 <tab path> \\#.");
            sendConsoleMessage(self, "appends the targeted object's buildout row to the specified .tab file");
            sendConsoleMessage(self, "\\#00ffff scanBuildoutArea \\#bfff00 <tab path> \\#.");
            sendConsoleMessage(self, "collects all devtool.write objects in the current buildout area and writes them to the tab file");
            sendConsoleMessage(self, "\\#00ffff scanBuildoutArea all \\#bfff00 <tab path> \\#.");
            sendConsoleMessage(self, "scans every buildout area in the scene and appends the rows to the tab file");
            //sendConsoleMessage(self, "\\#00ffff setAllBuildOutObjVars \\#.");
            //sendConsoleMessage(self, "finds all objects in the buildout area and attaches devtool.write objvar if not already attached");
            sendConsoleMessage(self, "\\#00ffff updateServerBuildout \\#.");
            sendConsoleMessage(self, "finds everything in the buildout area that does not have a buildout ID and writes it to a .tab file in the Linux directory");
            //sendConsoleMessage(self, "\\#00ffff writeBuildout \\#bfff00 <file name> \\#.");
            //sendConsoleMessage(self, "writes all objects within 500m range with the devtool.write objvar to a buildout formatted .csv file swg-main/linux/exe");
            sendConsoleMessage(self, "\\#ffff00 ============ ============ ============ ============ \\#.");
            return SCRIPT_CONTINUE;

            // ===========================================================================
            // ===== getBuildoutArea
            // ===========================================================================
        } else if (command1.equalsIgnoreCase("getBuildoutArea")) {
            location here = getLocation(self);
            obj_id containingBuilding = getTopMostContainer(self);
            if (isIdValid(containingBuilding)) {
                here = getLocation(containingBuilding);
            }
            String buildoutAreaName = getBuildoutAreaName(here.x, here.z, getCurrentSceneName());
            sendSystemMessageTestingOnly(self, "You are in buildout area: " + buildoutAreaName);


            // ===========================================================================
            // ===== getBuildoutInfo
            // ===========================================================================
        } else if (command1.equalsIgnoreCase("getBuildoutInfo")) {

            obj_id oid = utils.stringToObjId(command2);

            if (command2.isEmpty()) {
                oid = getTarget(self);
            }

            if (!isIdValid(oid)) {
                sendSystemMessageTestingOnly(self, "OID specified for getBuildoutInfo was not valid.");
                return SCRIPT_CONTINUE;
            }

            StringBuilder sb = new StringBuilder();

            sb.append("Buildout Information for Object: ").append(oid).append("\n\n");

            if (hasObjVar(oid, "buildoutObjectId")) {
                sb.append(getHeaderFormat("Object ID in Buildout File"));
                sb.append("\t").append(getIntObjVar(oid, "buildoutObjectId"));
                sb.append("\n\n");
            }

            sb.append(getHeaderFormat("Server Template")).append("\t").append(getTemplateName(oid)).append("\n\n");
            sb.append(getHeaderFormat("Shared Template")).append("\t").append(getSharedObjectTemplateName(oid)).append("\n\n");

            final location objectLoc = getLocation(oid);
            location topLoc = getLocation(oid);
            sb.append(getHeaderFormat("Buildout Area"));
            if (isIdValid(getTopMostContainer(oid))) {
                topLoc = getLocation(getTopMostContainer(oid));
            }
            final String buildoutAreaName = getBuildoutAreaName(topLoc.x, topLoc.z);
            sb.append("\t").append(buildoutAreaName).append("\n\n");

            sb.append(getHeaderFormat("Container"));
            if (isIdValid(objectLoc.cell)) {
                // if the cell has a buildout object ID, we need to display that instead
                // because that's what actually goes in the buildout
                if (hasObjVar(objectLoc.cell, "buildoutObjectId")) {
                    sb.append("\t").append(getIntObjVar(objectLoc.cell, "buildoutObjectId"));
                } else {
                    sb.append("\t").append(objectLoc.cell);
                }
            } else {
                sb.append("\t").append(0);
            }
            sb.append("\n\n");

            // Cell Index
            sb.append(getHeaderFormat("Cell Index"));
            sb.append("\t").append(getCellIndex(objectLoc.cell));
            sb.append("\n\n");

            boolean isComposite = false;
            // X Y Z coordinates
            float[] buildout = getBuildoutAreaSizeAndCenter(objectLoc.x, objectLoc.z, objectLoc.area, false, false);
            if (buildout == null || buildout.length < 3) {
                isComposite = true;
                buildout = getBuildoutAreaRect(objectLoc.x, objectLoc.z, objectLoc.area, true);
            }
            sb.append(getHeaderFormat("Object to Parent Coordinates (for Buildout) [pX, pY, pZ]"));
            if (isIdValid(objectLoc.cell)) {
                sb.append("\t").append(objectLoc.x).append(",\n");
                sb.append("\t").append(objectLoc.y).append(",\n");
                sb.append("\t").append(objectLoc.z).append(",\n");
            } else {
                if (isComposite) {
                    sb.append("\t").append((objectLoc.x - buildout[0])).append(",\n");
                    sb.append("\t").append(objectLoc.y).append(",\n");
                    sb.append("\t").append((objectLoc.z - buildout[2]));
                } else {
                    sb.append("\t").append((objectLoc.x - (buildout[2] - (buildout[0] / 2)))).append(",\n");
                    sb.append("\t").append(objectLoc.y).append(",\n");
                    sb.append("\t").append((objectLoc.z - (buildout[3] - (buildout[1] / 2))));
                }
            }
            sb.append("\n\n");

            final float[] quaternion = getQuaternion(oid);
            sb.append(getHeaderFormat("Quaternion [qW, qX, qY, qZ]"));
            sb.append("\t").append(quaternion[0]).append(",\n");
            sb.append("\t").append(quaternion[1]).append(",\n");
            sb.append("\t").append(quaternion[2]).append(",\n");
            sb.append("\t").append(quaternion[3]);
            sb.append("\n\n");

            sb.append(getHeaderFormat("Scripts"));
            sb.append(Arrays.toString(getScriptList(oid)));
            sb.append("\n\n");

            sb.append(getHeaderFormat("ObjVars"));
            sb.append(getPackedObjvars(oid));
            sb.append("\n\n");

            final int pid = sui.msgbox(self, self, sb.toString(), sui.OK_ONLY, "getBuildoutInfo", sui.MSG_INFORMATION, "noHandler");
            setSUIProperty(pid, "Prompt.lblPrompt", "Editable", "true");
            setSUIProperty(pid, "Prompt.lblPrompt", "GetsInput", "true");
            flushSUIPage(pid);

            // ===========================================================================
            // ===== writeBuildout
            // ===========================================================================
        } else if (command1.equalsIgnoreCase("writeBuildout")) {

            sendSystemMessageTestingOnly(self, "This feature is currently disabled as it needs repair.");

            /*
            if (command2.isEmpty()) {
                sendSystemMessageTestingOnly(self, "[SYNTAX] writeBuildout <file name>");
                return SCRIPT_CONTINUE;
            }

            List<String> list = Arrays.asList(SCENES);
            if(!list.contains(getCurrentSceneName())) {
                sendSystemMessageTestingOnly(self, "You cannot use this feature in your current scene (likely due to world size mismatch).");
                return SCRIPT_CONTINUE;
            }

            location me = getLocation(self);
            obj_id[] objects = getAllObjectsWithObjVar(me, 500f, "devtool.write");

            PrintWriter writer = new PrintWriter(command2+".csv", StandardCharsets.UTF_8);
            writer.println("objid,container,server_template_crc,cell_index,px,py,pz,qw,qx,qy,qz,scripts,objvars");
            for (obj_id i : objects) {
                String template = getTemplateName(i);
                location wp = getLocation(i);
                obj_id cell = wp.cell;
                boolean isInBuilding = isIdValid(cell);
                obj_id container = null;
                if (isInBuilding) {
                    container = getContainedBy(cell);
                }
                String scripts = Arrays.toString(getScriptList(i));
                String objvars = getPackedObjvars(i);
                float[] q = getQuaternion(i);
                String buildout = getBuildoutAreaName(wp.x, wp.z);
                float coord_x = wp.x - getBuildoutRootCoords(buildout).x;
                float coord_z = wp.z - getBuildoutRootCoords(buildout).z;
                writer.println(i+","+container+","+template+","+cell+","+coord_x+","+wp.y+","+coord_z+","+q[0]+","+q[1]+","+q[2]+","+q[3]+","+scripts+","+objvars);
            }
            writer.close();
            sendSystemMessageTestingOnly(self, "Successfully wrote file "+command2+".csv to swg-main/exe/linux...");
             */

            // ===========================================================================
            // ===== getObjects
            // ===========================================================================\
        } else if (command1.equalsIgnoreCase("getObjects")) {

            location me = getLocation(self);
            obj_id[] objects = getAllObjectsWithObjVar(me, 500f, "devtool.write");

            int count = 0;
            for (obj_id i : objects) {
                count++;
            }

            String header = "Listing all objects within 500m with devtool.write objvar \n Total object count: " + count + "\n\n";
            sui.msgbox(self, self, header + Arrays.toString(objects), sui.OK_ONLY, "getObjects", "noHandler");

            // ===========================================================================
            // ===== clearObjects
            // ===========================================================================
        } else if (command1.equalsIgnoreCase("clearObjects")) {

            location me = getLocation(self);
            obj_id[] objects = getAllObjectsWithObjVar(me, 500f, "devtool.write");
            int count = 0;

            sendSystemMessageTestingOnly(self, "clearObjects: Destroying all objects in range with devtool.write ObjVar...");

            for (obj_id i : objects) {
                sendConsoleMessage(self, "devtool clearObjects: Destroying object " + getName(i) + " (" + i + ")");
                destroyObject(i);
                count++;
            }

            sendSystemMessageTestingOnly(self, "clearObjects: Finished - Deleted " + count + " objects");
            return SCRIPT_CONTINUE;

            // ===========================================================================
            // ===== createObjects
            // ===========================================================================
        } else if (command1.equalsIgnoreCase("createObject")) {

            if (command2.isEmpty()) {
                sendSystemMessageTestingOnly(self, "[SYNTAX] createObject <template>");
                return SCRIPT_CONTINUE;
            }

            location me = getLocation(self);
            obj_id object = createObject(command2, me);
            setObjVar(object, "devtool.write", 1);

            if (isValidId(object)) {
                sendSystemMessageTestingOnly(self, "createObject: Success, new object OID is " + object);
            } else {
                sendSystemMessageTestingOnly(self, "createObject: ERROR creating object. Check your template spelling.");
            }

            return SCRIPT_CONTINUE;
        } else if (command1.equalsIgnoreCase("getBuildoutAreaRect")) {
            location loc = getLocation(self);
            float[] rect = getBuildoutAreaRect(loc.x, loc.z, loc.area, true);
            sendSystemMessageTestingOnly(self, "Area Rect (as x0, x1, y0, y1) is: " + rect[0] + ", " + rect[1] + ", " + rect[2] + ", " + rect[3]);
        } else if (command1.equalsIgnoreCase("getYaw")) {
            sendSystemMessageTestingOnly(self, "Yaw is: " + getYaw(getTarget(self)));
        } else if (command1.equalsIgnoreCase("setYaw")) {
            setYaw(getTarget(self), Float.parseFloat(command2));
        } else if (command1.equalsIgnoreCase("getTransform")) {
            transform t = getTransform_o2p(getTarget(self));
            sendSystemMessageTestingOnly(self, t.getLocalFrameI_p().toString());
            sendSystemMessageTestingOnly(self, t.getLocalFrameJ_p().toString());
            sendSystemMessageTestingOnly(self, t.getLocalFrameK_p().toString());
        } else if (command1.equalsIgnoreCase("updateServerBuildout")) {
            //get the current buildout area
            location here = getLocation(self);
            obj_id containingBuilding = getTopMostContainer(self);
            if (isIdValid(containingBuilding)) {
                here = getLocation(containingBuilding);
            }
            String buildoutAreaName = getBuildoutAreaName(here.x, here.z, getCurrentSceneName());
            String sceneName = getCurrentSceneName();
            sendSystemMessageTestingOnly(self, "Updating buildout for " + buildoutAreaName);
            //updateServerBuildout(self, sceneName, buildoutAreaName);
            return SCRIPT_CONTINUE;
        } else if (command1.equalsIgnoreCase("setAllBuildOutObjVars")) {
            obj_id[] objects = utils.getAllObjectsInBuildoutArea(self);

            int count = 0;
            int addedCount = 0; // Count of objects we added objVar to

            for (obj_id i : objects) {
                //skip player
                if (i == self) {
                    continue;
                }
                count++;

                // Check if the object has buildout objvar
                if (!hasObjVar(i, "devtool.write")) {
                    // Set the objvar on the object
                    setObjVar(i, "devtool.write", 1); // add objVar
                    addedCount++;
                }
            }
            sendSystemMessageTestingOnly(self, "Added devtool.write to " + addedCount + " out of " + count + " total objects in buildout");
        } else if (command1.equalsIgnoreCase("move")) {
            if (!tok.hasMoreTokens()) {
                sendSystemMessageTestingOnly(self, "SYNTAX: move <x> <y> <z>");
                return SCRIPT_CONTINUE;
            }

            // parse floats from the command tokens
            float moveX = 0f;
            float moveY = 0f;
            float moveZ = 0f;
            try {
                moveX = Float.parseFloat(command2); // we already had command2 as nextToken()
                if (tok.hasMoreTokens()) {
                    moveY = Float.parseFloat(tok.nextToken());
                }
                if (tok.hasMoreTokens()) {
                    moveZ = Float.parseFloat(tok.nextToken());
                }
            } catch (Exception e) {
                sendSystemMessageTestingOnly(self, "Error parsing move coordinates. Usage: move <x> <y> <z>");
                return SCRIPT_CONTINUE;
            }

            // get the target object
            obj_id target = getTarget(self);
            if (!isIdValid(target)) {
                sendSystemMessageTestingOnly(self, "You have no valid target!");
                return SCRIPT_CONTINUE;
            }

            // get the target’s current location and move it
            location currentLoc = getLocation(target);
            currentLoc.x += moveX;
            currentLoc.y += moveY;
            currentLoc.z += moveZ;

            // set the target’s new location
            setLocation(target, currentLoc);

            sendSystemMessageTestingOnly(self, "Moved target by (" + moveX + ", " + moveY + ", " + moveZ + ")");
            return SCRIPT_CONTINUE;
        } else if (command1.equalsIgnoreCase("addBuildoutEntry")) {
            if (command2.isEmpty()) {
                sendSystemMessageTestingOnly(self, "SYNTAX: addBuildoutEntry <tab path> (uses your current target)");
                return SCRIPT_CONTINUE;
            }

            obj_id target = getTarget(self);
            if (!isIdValid(target)) {
                sendSystemMessageTestingOnly(self, "addBuildoutEntry: You must have a valid target selected.");
                return SCRIPT_CONTINUE;
            }

            try {
                BuildoutRow row = buildBuildoutRow(target);
                Path written = appendRowsToTab(command2, Arrays.asList(row));
                sendSystemMessageTestingOnly(self, "Added buildout entry for target " + target + " to " + written.normalize());
                sendConsoleMessage(self, describeBuildoutRow(row));
            } catch (IOException e) {
                sendSystemMessageTestingOnly(self, "addBuildoutEntry: Failed to write tab file (" + e.getMessage() + ")");
            }
            return SCRIPT_CONTINUE;
        } else if (command1.equalsIgnoreCase("scanBuildoutArea")) {
            boolean scanAll = command2.equalsIgnoreCase("all");
            String tabPath = scanAll ? command3 : command2;

            if (tabPath.isEmpty()) {
                sendSystemMessageTestingOnly(self, scanAll ? "SYNTAX: scanBuildoutArea all <tab path>" : "SYNTAX: scanBuildoutArea <tab path>");
                return SCRIPT_CONTINUE;
            }

            if (scanAll) {
                scanAllBuildoutAreas(self, tabPath);
            } else {
                scanSingleBuildoutArea(self, tabPath);
            }
            return SCRIPT_CONTINUE;
        }

        return SCRIPT_CONTINUE;
    }

    public static int getCellIndex(obj_id cell) {
        if (isIdValid(cell)) {
            return Arrays.asList(getCellIds(getTopMostContainer(cell))).indexOf(cell);
        }
        return 0;
    }

    public static boolean isWorldCoordinateOffset(String scene) {
        dictionary d = dataTableGetRow("datatables/buildout/buildout_scenes.iff", scene);
        return d != null && d.getInt("adjust_map_coordinates") > 0;
    }

    private static class BuildoutArea {
        int row;
        String name;
        location root;
    }

    private static class BuildoutRow {
        long objId;
        long containerId;
        String template;
        int cellIndex;
        float px;
        float py;
        float pz;
        float[] quaternion;
        String scripts;
        String objvars;
        String buildoutAreaName;

        String toTabLine() {
            return String.format(
                    Locale.ROOT,
                    "%d\t%d\t%s\t%d\t%.4f\t%.4f\t%.4f\t%.6f\t%.6f\t%.6f\t%.6f\t%s\t%s",
                    objId,
                    containerId,
                    template,
                    cellIndex,
                    px,
                    py,
                    pz,
                    quaternion[0],
                    quaternion[1],
                    quaternion[2],
                    quaternion[3],
                    scripts,
                    objvars == null ? "" : objvars);
        }
    }

    private static BuildoutRow buildBuildoutRow(obj_id target) throws InterruptedException {
        BuildoutArea area = getBuildoutAreaForObject(target);
        return buildBuildoutRow(target, area);
    }

    private static BuildoutRow buildBuildoutRow(obj_id target, BuildoutArea area) throws InterruptedException {
        BuildoutRow row = new BuildoutRow();

        row.objId = hasObjVar(target, "buildoutObjectId") ? getIntObjVar(target, "buildoutObjectId") : target.getValue();

        final location objectLoc = getLocation(target);
        location topLoc = objectLoc;
        if (isIdValid(getTopMostContainer(target))) {
            topLoc = getLocation(getTopMostContainer(target));
        }

        if (area == null) {
            row.buildoutAreaName = getBuildoutAreaName(topLoc.x, topLoc.z, topLoc.area);
            if (row.buildoutAreaName == null || row.buildoutAreaName.isEmpty()) {
                row.buildoutAreaName = topLoc.area;
            }
        } else {
            row.buildoutAreaName = area.name != null && !area.name.isEmpty() ? area.name : getBuildoutAreaName(topLoc.x, topLoc.z, topLoc.area);
            if (row.buildoutAreaName == null || row.buildoutAreaName.isEmpty()) {
                row.buildoutAreaName = topLoc.area;
            }
        }

        boolean inCell = isIdValid(objectLoc.cell);
        if (inCell) {
            if (hasObjVar(objectLoc.cell, "buildoutObjectId")) {
                row.containerId = getIntObjVar(objectLoc.cell, "buildoutObjectId");
            } else {
                row.containerId = objectLoc.cell.getValue();
            }
        } else {
            row.containerId = 0;
        }

        row.template = getTemplateName(target);
        row.cellIndex = getCellIndex(objectLoc.cell);

        location buildoutRoot;
        if (area != null && area.root != null) {
            buildoutRoot = area.root;
        } else {
            buildoutRoot = getBuildoutRootCoords(topLoc.area, row.buildoutAreaName, locations.getBuildoutAreaRow(target));
        }
        if (inCell) {
            row.px = objectLoc.x;
            row.py = objectLoc.y;
            row.pz = objectLoc.z;
        } else {
            row.px = objectLoc.x - buildoutRoot.x;
            row.py = objectLoc.y;
            row.pz = objectLoc.z - buildoutRoot.z;
        }

        row.quaternion = getQuaternion(target);

        String[] scriptList = getScriptList(target);
        if (scriptList == null || scriptList.length == 0) {
            row.scripts = "";
        } else {
            row.scripts = String.join(":", scriptList);
        }

        row.objvars = getPackedObjvars(target);

        return row;
    }

    private static String describeBuildoutRow(BuildoutRow row) {
        return "Area " + row.buildoutAreaName + " :: template=" + row.template + " :: coords=" + row.px + ", " + row.py + ", " + row.pz;
    }

    private void scanSingleBuildoutArea(obj_id self, String tabPath) throws InterruptedException {
        obj_id[] objects = utils.getAllObjectsInBuildoutArea(self);
        List<BuildoutRow> rows = new ArrayList<>();

        BuildoutArea area = getBuildoutAreaForObject(self);

        for (obj_id candidate : objects) {
            if (candidate == self) {
                continue;
            }
            if (!hasObjVar(candidate, "devtool.write")) {
                continue;
            }
            rows.add(buildBuildoutRow(candidate, area));
        }

        writeRowsAndReport(self, tabPath, rows, "scanBuildoutArea");
    }

    private void scanAllBuildoutAreas(obj_id self, String tabPath) throws InterruptedException {
        String scene = getLocation(self).area;
        List<BuildoutArea> areas = getBuildoutAreas(scene);
        if (areas.isEmpty()) {
            sendSystemMessageTestingOnly(self, "scanBuildoutArea all: No buildout areas defined for scene " + scene);
            return;
        }

        List<BuildoutRow> rows = new ArrayList<>();
        for (BuildoutArea area : areas) {
            obj_id[] objects = utils.getAllObjectsInBuildoutArea(scene, area.row);
            if (objects == null || objects.length == 0) {
                continue;
            }
            for (obj_id candidate : objects) {
                if (candidate == self) {
                    continue;
                }
                if (!hasObjVar(candidate, "devtool.write")) {
                    continue;
                }
                rows.add(buildBuildoutRow(candidate, area));
            }
        }

        writeRowsAndReport(self, tabPath, rows, "scanBuildoutArea all");
    }

    private void writeRowsAndReport(obj_id self, String tabPath, List<BuildoutRow> rows, String label) {
        if (rows == null || rows.isEmpty()) {
            sendSystemMessageTestingOnly(self, label + ": No objects with devtool.write found to export.");
            return;
        }

        try {
            Path written = appendRowsToTab(tabPath, rows);
            sendSystemMessageTestingOnly(self, label + ": Wrote " + rows.size() + " entries to " + written.normalize());
            for (BuildoutRow row : rows) {
                sendConsoleMessage(self, describeBuildoutRow(row));
            }
        } catch (IOException e) {
            sendSystemMessageTestingOnly(self, label + ": Failed to write tab file (" + e.getMessage() + ")");
        }
    }

    private static BuildoutArea getBuildoutAreaForObject(obj_id obj) throws InterruptedException {
        location loc = getLocation(obj);
        int row = locations.getBuildoutAreaRow(obj);
        BuildoutArea area = buildBuildoutArea(loc.area, row);
        if (area != null) {
            return area;
        }
        area = new BuildoutArea();
        area.row = row;
        area.name = getBuildoutAreaName(loc.x, loc.z, loc.area);
        area.root = getBuildoutRootCoords(loc.area, area.name, row);
        return area;
    }

    private static List<BuildoutArea> getBuildoutAreas(String scene) {
        List<BuildoutArea> areas = new ArrayList<>();
        int count = dataTableGetNumRows("datatables/buildout/areas_" + scene + ".iff");
        for (int i = 0; i < count; i++) {
            BuildoutArea area = buildBuildoutArea(scene, i);
            if (area != null) {
                areas.add(area);
            }
        }
        return areas;
    }

    private static BuildoutArea buildBuildoutArea(String scene, int row) {
        dictionary data = dataTableGetRow("datatables/buildout/areas_" + scene + ".iff", row);
        if (data == null) {
            return null;
        }

        BuildoutArea area = new BuildoutArea();
        area.row = row;
        area.name = data.getString("buildout_area");
        area.root = new location(data.getFloat("x1"), 0f, data.getFloat("z1"), scene);
        return area;
    }

    private static Path appendRowsToTab(String tabFile, List<BuildoutRow> rows) throws IOException {
        Path path = resolveTabPath(tabFile);
        boolean existed = Files.exists(path);

        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        List<String> lines = new ArrayList<>();
        if (!existed) {
            lines.add("objid\tcontainer\tserver_template_crc\tcell_index\tpx\tpy\tpz\tqw\tqx\tqy\tqz\tscripts\tobjvars");
            lines.add("i\ti\th\ti\tf\tf\tf\tf\tf\tf\tf\ts\tp");
        }

        for (BuildoutRow row : rows) {
            lines.add(row.toTabLine());
        }

        if (existed) {
            Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } else {
            Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        }

        return path;
    }

    private static Path resolveTabPath(String requestedPath) {
        if (requestedPath == null || requestedPath.isEmpty()) {
            return Paths.get("sku.0/sys.server/compiled/game/datatables/buildout/auto_buildout.tab");
        }

        Path candidate = Paths.get(requestedPath);
        if (!candidate.isAbsolute()) {
            candidate = Paths.get("sku.0/sys.server/compiled/game/datatables/buildout").resolve(candidate);
        }
        return candidate.normalize();
    }

    public static String getHeaderFormat(String header) {
        return "\\#00FFFF" + header + ":\\#.\n";
    }

    // ===========================================================================
    // ===== getBuildoutRootCoords
    // - BMC: 05/07/2025 - keeping this function here as some other code may still be using it.
    // ===========================================================================
    public static location getBuildoutRootCoords(String scene, String buildoutAreaName, int buildoutRow) throws InterruptedException {
        BuildoutArea area = buildBuildoutArea(scene, buildoutRow);
        if (area != null && area.root != null) {
            return area.root;
        }
        return getBuildoutRootCoords(buildoutAreaName);
    }

    public static location getBuildoutRootCoords(String buildoutAreaName) throws InterruptedException {
        if (buildoutAreaName == null || buildoutAreaName.length() < 3) {
            return new location(0f, 0f, 0f);
        }
        String buildoutAreaNumber = buildoutAreaName.substring(buildoutAreaName.length() - 3);

        switch (buildoutAreaNumber) {
            case "1_1":
                return new location(-8196f, 0f, -8196f);
            case "1_2":
                return new location(-8196f, 0f, -6144f);
            case "1_3":
                return new location(-8196f, 0f, -4096f);
            case "1_4":
                return new location(-8196f, 0f, -2048f);
            case "1_5":
                return new location(-8196f, 0f, 0f);
            case "1_6":
                return new location(-8196f, 0f, 2048f);
            case "1_7":
                return new location(-8196f, 0f, 4096f);
            case "1_8":
                return new location(-8196f, 0f, 6144f);
            case "2_1":
                return new location(-6144f, 0f, -8196f);
            case "2_2":
                return new location(-6144f, 0f, -6144f);
            case "2_3":
                return new location(-6144f, 0f, -4096f);
            case "2_4":
                return new location(-6144f, 0f, -2048f);
            case "2_5":
                return new location(-6144f, 0f, 0f);
            case "2_6":
                return new location(-6144f, 0f, 2048f);
            case "2_7":
                return new location(-6144f, 0f, 4096f);
            case "2_8":
                return new location(-6144f, 0f, 6144f);
            case "3_1":
                return new location(-4096f, 0f, -8196f);
            case "3_2":
                return new location(-4096f, 0f, -6144f);
            case "3_3":
                return new location(-4096f, 0f, -4096f);
            case "3_4":
                return new location(-4096f, 0f, -2048f);
            case "3_5":
                return new location(-4096f, 0f, 0f);
            case "3_6":
                return new location(-4096f, 0f, 2048f);
            case "3_7":
                return new location(-4096f, 0f, 4096f);
            case "3_8":
                return new location(-4096f, 0f, 6144f);
            case "4_1":
                return new location(-2048f, 0f, -8196f);
            case "4_2":
                return new location(-2048f, 0f, -6144f);
            case "4_3":
                return new location(-2048f, 0f, -4096f);
            case "4_4":
                return new location(-2048f, 0f, -2048f);
            case "4_5":
                return new location(-2048f, 0f, 0f);
            case "4_6":
                return new location(-2048f, 0f, 2048f);
            case "4_7":
                return new location(-2048f, 0f, 4096f);
            case "4_8":
                return new location(-2048f, 0f, 6144f);
            case "5_1":
                return new location(0f, 0f, -8196f);
            case "5_2":
                return new location(0f, 0f, -6144f);
            case "5_3":
                return new location(0f, 0f, -4096f);
            case "5_4":
                return new location(0f, 0f, -2048f);
            case "5_5":
                return new location(0f, 0f, 0f);
            case "5_6":
                return new location(0f, 0f, 2048f);
            case "5_7":
                return new location(0f, 0f, 4096f);
            case "5_8":
                return new location(0f, 0f, 6144f);
            case "6_1":
                return new location(2048f, 0f, -8196f);
            case "6_2":
                return new location(2048f, 0f, -6144f);
            case "6_3":
                return new location(2048f, 0f, -4096f);
            case "6_4":
                return new location(2048f, 0f, -2048f);
            case "6_5":
                return new location(2048f, 0f, 0f);
            case "6_6":
                return new location(2048f, 0f, 2048f);
            case "6_7":
                return new location(2048f, 0f, 4096f);
            case "6_8":
                return new location(2048f, 0f, 6144f);
            case "7_1":
                return new location(4096f, 0f, -8196f);
            case "7_2":
                return new location(4096f, 0f, -6144f);
            case "7_3":
                return new location(4096f, 0f, -4096f);
            case "7_4":
                return new location(4096f, 0f, -2048f);
            case "7_5":
                return new location(4096f, 0f, 0f);
            case "7_6":
                return new location(4096f, 0f, 2048f);
            case "7_7":
                return new location(4096f, 0f, 4096f);
            case "7_8":
                return new location(4096f, 0f, 6144f);
            case "8_1":
                return new location(6144f, 0f, -8196f);
            case "8_2":
                return new location(6144f, 0f, -6144f);
            case "8_3":
                return new location(6144f, 0f, -4096f);
            case "8_4":
                return new location(6144f, 0f, -2048f);
            case "8_5":
                return new location(6144f, 0f, 0f);
            case "8_6":
                return new location(6144f, 0f, 2048f);
            case "8_7":
                return new location(6144f, 0f, 4096f);
            case "8_8":
                return new location(6144f, 0f, 6144f);
            default:
                return new location(0f, 0f, 0f);
        }
    }
}