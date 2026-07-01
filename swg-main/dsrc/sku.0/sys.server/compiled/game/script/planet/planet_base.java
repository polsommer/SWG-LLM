package script.planet;

import script.dictionary;
import script.library.cloninglib;
import script.library.gcw;
import script.library.scheduled_drop;
import script.library.utils;
import script.location;
import script.obj_id;
import script.discord.GCWEventAnnouncer;

import java.util.Vector;

public class planet_base extends script.base_script {
    public planet_base() {}

    public int OnUniverseComplete(obj_id self) throws InterruptedException {
        CustomerServiceLog("holidayEvent", "planet_base.OnUniverseComplete: Initialization triggered.");
        messageTo(self, "doSpawnSetup", new dictionary(), 60, true);
        return SCRIPT_CONTINUE;
    }

    public int doSpawnSetup(obj_id self, dictionary params) throws InterruptedException {
        String planetName = getNameForPlanetObject(self);

        if (planetName == null) {
            logPlanetError("doSpawnSetup", "Planet name is NULL. Please check with development.", self);
            return SCRIPT_CONTINUE;
        }

        logPlanetInit("doSpawnSetup", planetName, self);

        if (!planetName.equals("tutorial") && !hasScript(self, "systems.spawning.spawn_master")) {
            attachScript(self, "systems.spawning.spawn_master");
            CustomerServiceLog("holidayEvent", "Non-tutorial Planet detected. Attached systems.spawning.spawn_master.");
        }

        attachNpcIISpawner(self, planetName);
        attachTatooineHandler(self);
        return SCRIPT_CONTINUE;
    }

    public int OnDetach(obj_id self) throws InterruptedException {
        String planetName = getCurrentSceneName();
        if (planetName == null) {
            debugServerConsoleMsg(self, "Scene name is null. Please notify development.");
            return SCRIPT_CONTINUE;
        }

        String regionScript = "systems.spawning.spawn_regions.regions_" + planetName;
        debugServerConsoleMsg(self, "Detaching script: " + regionScript);
        detachScript(self, regionScript);
        return SCRIPT_CONTINUE;
    }

    public int registerCloningFacility(obj_id self, dictionary params) throws InterruptedException {
        cloneFacilityOperations(self, params, true);
        return SCRIPT_CONTINUE;
    }

    public int unregisterCloningFacility(obj_id self, dictionary params) throws InterruptedException {
        cloneFacilityOperations(self, params, false);
        return SCRIPT_CONTINUE;
    }

    private void cloneFacilityOperations(obj_id self, dictionary params, boolean isRegister) throws InterruptedException {
        obj_id facilityId = params.getObjId("id");
        Vector idList = utils.getResizeableObjIdArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_ID);

        if (isRegister) {
            String cloneName = params.getString("name");
            String areaName = params.getString("buildout");
            obj_id areaId = params.getObjId("areaId");
            location facilityLoc = params.getLocation("loc");
            location facilityRespawn = params.getLocation("respawn");
            int cloneType = params.getInt("type");

            updateCloneData(self, facilityId, cloneName, areaName, areaId, facilityLoc, facilityRespawn, cloneType, idList);
        } else {
            removeCloneData(self, facilityId, idList);
        }
    }

    private void updateCloneData(obj_id self, obj_id facilityId, String cloneName, String areaName, obj_id areaId,
                                 location facilityLoc, location facilityRespawn, int cloneType, Vector idList) throws InterruptedException {
        Vector nameList = utils.getResizeableStringArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_NAME);
        Vector areaList = utils.getResizeableStringArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_AREA);
        Vector areaIdList = utils.getResizeableObjIdArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_AREA_ID);
        Vector locList = utils.getResizeableLocationArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_LOC);
        Vector respawnList = utils.getResizeableLocationArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_RESPAWN);
        Vector cloneTypeList = utils.getResizeableIntArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_TYPE);

        int pos = utils.getElementPositionInArray(idList, facilityId);
        if (pos >= 0) {
            nameList.set(pos, cloneName);
            areaList.set(pos, areaName);
            areaIdList.set(pos, areaId);
            locList.set(pos, facilityLoc);
            respawnList.set(pos, facilityRespawn);
            cloneTypeList.set(pos, cloneType);
        } else {
            idList = utils.addElement(idList, facilityId);
            nameList = utils.addElement(nameList, cloneName);
            areaList = utils.addElement(areaList, areaName);
            areaIdList = utils.addElement(areaIdList, areaId);
            locList = utils.addElement(locList, facilityLoc);
            respawnList = utils.addElement(respawnList, facilityRespawn);
            cloneTypeList = utils.addElement(cloneTypeList, cloneType);
        }

        setCloneScriptVars(self, idList, nameList, areaList, areaIdList, locList, respawnList, cloneTypeList);
    }

    private void removeCloneData(obj_id self, obj_id facilityId, Vector idList) throws InterruptedException {
        Vector nameList = utils.getResizeableStringArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_NAME);
        Vector areaList = utils.getResizeableStringArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_AREA);
        Vector areaIdList = utils.getResizeableObjIdArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_AREA_ID);
        Vector locList = utils.getResizeableLocationArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_LOC);
        Vector respawnList = utils.getResizeableLocationArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_RESPAWN);
        Vector cloneTypeList = utils.getResizeableIntArrayScriptVar(self, cloninglib.VAR_PLANET_CLONE_TYPE);

        int pos = utils.getElementPositionInArray(idList, facilityId);
        if (pos >= 0) {
            idList = utils.removeElementAt(idList, pos);
            nameList = utils.removeElementAt(nameList, pos);
            areaList = utils.removeElementAt(areaList, pos);
            areaIdList = utils.removeElementAt(areaIdList, pos);
            locList = utils.removeElementAt(locList, pos);
            respawnList = utils.removeElementAt(respawnList, pos);
            cloneTypeList = utils.removeElementAt(cloneTypeList, pos);

            setCloneScriptVars(self, idList, nameList, areaList, areaIdList, locList, respawnList, cloneTypeList);
        }
    }

private void setCloneScriptVars(obj_id self, Vector idList, Vector nameList, Vector areaList,
                                Vector areaIdList, Vector locList, Vector respawnList, Vector cloneTypeList) {
    try {
        utils.setScriptVar(self, cloninglib.VAR_PLANET_CLONE_ID, idList);
        utils.setScriptVar(self, cloninglib.VAR_PLANET_CLONE_NAME, nameList);
        utils.setScriptVar(self, cloninglib.VAR_PLANET_CLONE_AREA, areaList);
        utils.setScriptVar(self, cloninglib.VAR_PLANET_CLONE_AREA_ID, areaIdList);
        utils.setScriptVar(self, cloninglib.VAR_PLANET_CLONE_LOC, locList);
        utils.setScriptVar(self, cloninglib.VAR_PLANET_CLONE_RESPAWN, respawnList);
        utils.setScriptVar(self, cloninglib.VAR_PLANET_CLONE_TYPE, cloneTypeList);
    } catch (InterruptedException e) {
        debugServerConsoleMsg(self, "Error setting clone script variables: " + e.getMessage());
        CustomerServiceLog("planet_base", "Failed to set clone script variables due to interruption: " + e);
        Thread.currentThread().interrupt();  // Restore interrupted status
    }
}

    private void attachNpcIISpawner(obj_id self, String planetName) throws InterruptedException {
        if (planetName.equals("tutorial") || planetName.startsWith("space_")) {
            return;
        }

        if (!hasScript(self, "systems.npcii.npcii_spawner")) {
            attachScript(self, "systems.npcii.npcii_spawner");
            CustomerServiceLog("npcii", "planet_base.attachNpcIISpawner: Attached systems.npcii.npcii_spawner for planet " + planetName + ".");
        }
    }

    private void attachTatooineHandler(obj_id self) throws InterruptedException {
        obj_id tatooinePlanet = getPlanetByName("tatooine");
        if (isIdValid(tatooinePlanet) && exists(tatooinePlanet)) {
            if (!hasScript(tatooinePlanet, "event.planet_event_handler")) {
                attachScript(tatooinePlanet, "event.planet_event_handler");
            }
            if (scheduled_drop.isSystemEnabled()) {
                scheduled_drop.instantiatePromotionsOnCluster();
            }
        } else {
            CustomerServiceLog("holidayEvent", "Tatooine Planet not found. Notify development.");
        }
    }

public void gcwInvasionMessage(obj_id self, obj_id citySequencer, String city) throws InterruptedException {
    if (isIdValid(self) && exists(self) && isIdValid(citySequencer)) {
        LOG("gcwlog", "planet_base gcwInvasionTracker citySequencer: " + citySequencer);
        utils.setScriptVar(self, "gcw.time." + city, getGameTime());
        utils.setScriptVar(self, "gcw.object." + city, citySequencer);
        utils.setScriptVar(self, "gcw.calendar_time." + city, getCalendarTime());

        // Announce the GCW invasion via Discord
        script.discord.GCWEventAnnouncer.announceGCWInvasion(self, city);

        messageTo(citySequencer, "beginInvasion", null, 1.0f, false);
    }
}

    public int gcwInvasionTracker(obj_id self, dictionary params) throws InterruptedException {
        String city = params.getString("city");
        if (gcw.gcwIsInvasionCityOn(city) && validateInvasionCycle(self, params, city)) {
            obj_id sequencer = params.getObjId("sequencer");
            int gameTime = getGameTime();
            params.put("gameTime" + city, gameTime);
            utils.setScriptVar(self, "gcw.lastTrackTime." + city, gameTime);

            if (gcw.gcwHasInvasionInCycle(city, gcw.gcwCalculateInvasionCycle())) {
                gcwInvasionMessage(self, sequencer, city);
            } else {
                messageTo(self, "gcwInvasionTracker", params, gcw.gcwGetNextInvasionTime(city), false);
            }
        }
        return SCRIPT_CONTINUE;
    }

    private boolean validateInvasionCycle(obj_id self, dictionary params, String city) throws InterruptedException {
        int messageGameTime = params.getInt("gameTime" + city);
        int lastTrackTime = utils.getIntScriptVar(self, "gcw.lastTrackTime." + city);
        return messageGameTime == lastTrackTime;
    }

    public int gcwGetInvasionObject(obj_id self, dictionary params) throws InterruptedException {
        String cityName = params.getString("city");
        obj_id whoToMessage = params.getObjId("object");
        if (utils.hasScriptVar(self, "gcw.object." + cityName)) {
            dictionary newParams = new dictionary();
            newParams.put("invasionObject", utils.getObjIdScriptVar(self, "gcw.object." + cityName));
            messageTo(whoToMessage, params.getString("messageHandler"), newParams, 1.0f, false);
        }
        return SCRIPT_CONTINUE;
    }

    private void logPlanetInit(String handler, String planetName, obj_id self) {
        CustomerServiceLog("holidayEvent", "planet_base." + handler + ": Planet initialization for " + planetName);
        debugServerConsoleMsg(self, "Planet initialization for " + planetName);
    }

    private void logPlanetError(String handler, String errorMsg, obj_id self) {
        CustomerServiceLog("holidayEvent", "planet_base." + handler + ": " + errorMsg);
        debugServerConsoleMsg(self, errorMsg);
    }
}

