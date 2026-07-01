package script.library;

import script.datatable_writer;
import script.dictionary;
import script.location;
import script.obj_id;

public class datatable extends script.base_script {

    public datatable() {
    }

    public static String getRandomTemplate(String datatable) throws InterruptedException {
        String[] templateFilenames = dataTableGetStringColumn(datatable, "templateFilename");
        if (templateFilenames == null || templateFilenames.length == 0) {
            debugServerConsoleMsg(null, "No template filenames found in datatable: " + datatable);
            return null;
        }
        int row = rand(0, templateFilenames.length - 1);
        return templateFilenames[row];
    }

    public static obj_id createRandomTemplateInWorld(String datatable) throws InterruptedException {
        String templateFilename = getRandomTemplate(datatable);
        if (templateFilename == null) {
            debugServerConsoleMsg(null, "Template filename is null. Cannot create object.");
            return null;
        }
        obj_id myObjects = createObject(templateFilename, getLocation(getSelf()));
        if (myObjects == null) {
            debugServerConsoleMsg(null, "Failed to create object with template: " + templateFilename);
        }
        return myObjects;
    }

    public static obj_id createRandomTemplateAtTarget(String datatable, obj_id target) throws InterruptedException {
        String templateFilename = getRandomTemplate(datatable);
        if (target == null || templateFilename == null) {
            debugServerConsoleMsg(null, "Target or template filename is null.");
            return null;
        }
        obj_id myObject = createObjectAt(templateFilename, target);
        if (myObject == null) {
            debugServerConsoleMsg(null, "Failed to create object at target with template: " + templateFilename);
        }
        return myObject;
    }

    public static obj_id createRandomTemplateAtTarget(String datatable, location loc) throws InterruptedException {
        String templateFilename = getRandomTemplate(datatable);
        if (loc == null || templateFilename == null) {
            debugServerConsoleMsg(null, "Location or template filename is null.");
            return null;
        }
        obj_id myObject = createObject(templateFilename, loc);
        if (myObject == null) {
            debugServerConsoleMsg(null, "Failed to create object at location with template: " + templateFilename);
        }
        return myObject;
    }

    public static boolean createDataTable(String strFileName, String[] strHeaders, String[] strHeaderTypes) throws InterruptedException {
        if (strFileName == null) {
            LOG("ERROR", "Null string passed into createDataTable");
            return false;
        }
        if (!strFileName.endsWith(".tab")) {
            LOG("ERROR", "Datatables need a .tab extension");
            return false;
        }
        if (strHeaders.length != strHeaderTypes.length) {
            LOG("ERROR", "Header and header type arrays must be of equal length");
            return false;
        }

        strFileName = "../../dsrc/sku.0/sys.server/compiled/game/" + strFileName;
        StringBuilder strHeaderString = new StringBuilder();
        StringBuilder strHeaderTypeString = new StringBuilder();

        for (int i = 0; i < strHeaders.length; i++) {
            String type = strHeaderTypes[i];
            if (!type.matches("[isfhcebpx]")) {
                LOG("ERROR", "Invalid header type at index " + i + ": " + type);
                return false;
            }
            strHeaderString.append(strHeaders[i]);
            strHeaderTypeString.append(type);
            if (i < strHeaders.length - 1) {
                strHeaderString.append("\t");
                strHeaderTypeString.append("\t");
            }
        }

        strHeaderString.append("\n");
        strHeaderTypeString.append("\n");

        String result = datatable_writer.makeDataTable(strFileName, strHeaderString.toString(), strHeaderTypeString.toString());
        LOG("NOT_ERROR", "Datatable filename is " + strFileName);
        return result != null;
    }

    public static void serverDataTableAddRow(String strFileName, dictionary dctParams) throws InterruptedException {
        strFileName = "../../dsrc/sku.0/sys.server/compiled/game/" + strFileName;
        dataTableAddRow(strFileName, dctParams);
    }
}
