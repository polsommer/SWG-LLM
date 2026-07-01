package script.space.content_tools;

import script.*;
import script.library.space_content;
import script.library.space_quest;
import script.library.space_transition;
import script.library.space_utils;
import script.library.prose;
import script.library.sui;
import script.library.utils;

import script.library.sui;
import script.library.utils;
import script.library.prose;


import java.util.Vector;

public class spacestation extends script.base_script
{
    public static final String GREET_VOLUME = "station_greet";
    public static final float GREET_RADIUS = 800.0f;
    public static final float GREET_COOLDOWN = 30.0f;
    public static final String SCRIPTVAR_GREET_TIME = "station.greet.time";
    public static final String SCRIPTVAR_GREET_STATION = "station.greet.station";
    public static final String LAUNCH_LOCATION_COLUMN_STATION_NAME = "spaceStationName";

    public spacestation()
    {
    }
    public int OnAttach(obj_id self) throws InterruptedException
    {
        requestPreloadCompleteTrigger(self);
        setObjVar(self, "intInvincible", 1);
        if (!hasTriggerVolume(self, GREET_VOLUME))
        {
            createTriggerVolume(GREET_VOLUME, GREET_RADIUS, true);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        requestPreloadCompleteTrigger(self);
        if (!hasTriggerVolume(self, GREET_VOLUME))
        {
            createTriggerVolume(GREET_VOLUME, GREET_RADIUS, true);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnPreloadComplete(obj_id self) throws InterruptedException
    {
        messageTo(self, "registerStation", null, 2, false);
        String strName = getStringObjVar(self, "strName");
        if (strName != null)
        {
            string_id strSpam = new string_id("space/space_mobile_type", strName);
            setName(self, strSpam);
        }
        return SCRIPT_CONTINUE;
    }
    public int registerStation(obj_id self, dictionary params) throws InterruptedException
    {
        LOG("space", "Registering space station");
        obj_id objQuestManager = getNamedObject(space_quest.QUEST_MANAGER);
        if (!isIdValid(objQuestManager))
        {
            LOG("space", "NO QUEST MANAGER OBJECT FOUND!");
            return SCRIPT_CONTINUE;
        }
        registerStationWithManager(objQuestManager, self);
        return SCRIPT_CONTINUE;
    }
    public void registerStationWithManager(obj_id objManager, obj_id objStation) throws InterruptedException
    {
        LOG("space", "Registering with " + objManager);
        Vector objSpaceStations = utils.getResizeableObjIdArrayScriptVar(objManager, "objSpaceStations");
        if ((objSpaceStations == null) || (objSpaceStations.size() == 0))
        {
            objSpaceStations = utils.addElement(objSpaceStations, objStation);
        }
        else 
        {
            int intIndex = utils.getElementPositionInArray(objSpaceStations, objStation);
            if (intIndex < 0)
            {
                objSpaceStations = utils.addElement(objSpaceStations, objStation);
            }
        }
        utils.setScriptVar(objManager, "objSpaceStations", objSpaceStations);
    }

    public int OnTriggerVolumeEntered(obj_id self, String volumeName, obj_id who) throws InterruptedException
    {
        if (!GREET_VOLUME.equals(volumeName))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id ship = who;
        if (!space_utils.isPlayerControlledShip(ship))
        {
            if (isPlayer(who))
            {
                ship = space_transition.getContainingShip(who);
            }
        }
        if (!space_utils.isPlayerControlledShip(ship))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id pilot = getPilotId(ship);
        if (!isIdValid(pilot))
        {
            return SCRIPT_CONTINUE;
        }
        if (utils.hasScriptVar(pilot, SCRIPTVAR_GREET_TIME))
        {
            float lastTime = utils.getFloatScriptVar(pilot, SCRIPTVAR_GREET_TIME);
            if (getGameTime() - lastTime < GREET_COOLDOWN)
            {
                return SCRIPT_CONTINUE;
            }
        }
        utils.setScriptVar(pilot, SCRIPTVAR_GREET_TIME, (float)getGameTime());
        utils.setScriptVar(pilot, SCRIPTVAR_GREET_STATION, self);
        String[] entries = new String[]
        {
            "Land/Board Station"
        };
        sui.listbox(self, pilot, "Station Services", sui.OK_CANCEL, "Station Control", entries, "handleStationGreeting", false, false);
        return SCRIPT_CONTINUE;
    }

    public int handleStationGreeting(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id player = sui.getPlayerId(params);
        if (!isIdValid(player))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id station = utils.hasScriptVar(player, SCRIPTVAR_GREET_STATION) ? utils.getObjIdScriptVar(player, SCRIPTVAR_GREET_STATION) : obj_id.NULL_ID;
        if (!isIdValid(station))
        {
            return SCRIPT_CONTINUE;
        }
        int bp = sui.getIntButtonPressed(params);
        if (bp == sui.BP_CANCEL)
        {
            return SCRIPT_CONTINUE;
        }
        int row = sui.getListboxSelectedRow(params);
        if (row == 0)
        {
            handleLandStation(station, player);
        }
        return SCRIPT_CONTINUE;
    }

    private void handleLandStation(obj_id station, obj_id player) throws InterruptedException
    {
        String landingPoint = getDefaultLandingPoint(station);
        if (landingPoint != null && landingPoint.length() > 0)
        {
            space_content.landPlayer(player, station, landingPoint);
            return;
        }
        String stationName = getStringObjVar(station, "strName");
        if (stationName == null || stationName.length() == 0)
        {
            stationName = "this station";
        }
        prose_package ppBadTravelPoint = prose.getPackage(space_content.SID_BAD_TRAVEL_POINT);
        prose.setTO(ppBadTravelPoint, stationName);
        sendSystemMessageProse(player, ppBadTravelPoint);
    }

    private String getDefaultLandingPoint(obj_id station) throws InterruptedException
    {
        String stationName = getStringObjVar(station, "strName");
        if (stationName == null || stationName.length() == 0)
        {
            return null;
        }
        int rowCount = dataTableGetNumRows(space_content.LAUNCH_LOCATION_DATATABLE_NAME);
        for (int row = 0; row < rowCount; row++)
        {
            String rowStation = dataTableGetString(space_content.LAUNCH_LOCATION_DATATABLE_NAME, row, LAUNCH_LOCATION_COLUMN_STATION_NAME);
            if (stationName.equals(rowStation))
            {
                return dataTableGetString(space_content.LAUNCH_LOCATION_DATATABLE_NAME, row, space_content.LAUNCH_LOCATION_COLUMN_POINTNAME);
            }
        }
        return null;
    }
}
