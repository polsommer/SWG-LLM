package script.systems.movement;

import script.dictionary;
import script.library.space_dungeon;
import script.location;
import script.obj_id;

/**
 * Handles setup and data management for public instances.
 */
public class public_instance_setup extends script.base_script {

    public public_instance_setup() {
    }

    /**
     * Initializes the instance by requesting cluster-wide data.
     */
    public int OnInitialize(obj_id self) throws InterruptedException {
        String instanceName = getStringObjVar(self, "instance_name");
        if (instanceName == null || instanceName.isEmpty()) {
            doLogging("OnInitialize", "Instance name is null or empty for object: " + self);
            return SCRIPT_CONTINUE;
        }

        String dataKey = instanceName + "_" + self;
        getClusterWideData("public_instances", dataKey, true, self);
        doLogging("OnInitialize", "Requested cluster wide data for " + dataKey);

        return SCRIPT_CONTINUE;
    }

    /**
     * Handles the response from cluster-wide data request and updates population data.
     */
    public int OnClusterWideDataResponse(obj_id self, String managerName, String dataKey, int requestId, String[] elementNameList, dictionary[] data, int lockKey) throws InterruptedException {
        int playersInZone = space_dungeon.pollZoneOccupantsForInstancePopulation(self);

        dictionary info = new dictionary();
        info.put("building_id", self);
        info.put("population", playersInZone);

        doLogging("OnClusterWideDataResponse", "Updating data with building ID: " + self + " and population: " + playersInZone);

        replaceClusterWideData(managerName, dataKey, info, true, lockKey);
        releaseClusterWideDataLock(managerName, lockKey);

        return SCRIPT_CONTINUE;
    }

    /**
     * Retrieves target coordinates and sends them to the specified return address.
     */
    public int getTargetCoordinates(obj_id self, dictionary params) throws InterruptedException {
        obj_id target = params.getObjId("return_address");
        obj_id player = params.getObjId("player");

        if (target == null || player == null) {
            doLogging("getTargetCoordinates", "Invalid target or player in parameters.");
            return SCRIPT_CONTINUE;
        }

        dictionary dict = new dictionary();
        location destinationRoot = new location(getLocation(self));
        dict.put("location", destinationRoot);
        dict.put("player", player);

        messageTo(target, "recievedTargetCoordinates", dict, 0, false);
        return SCRIPT_CONTINUE;
    }

    /**
     * Logs messages with the class context.
     */
    private void doLogging(String method, String message) {
        LOG("public_instance_setup::" + method, message);
    }
}
