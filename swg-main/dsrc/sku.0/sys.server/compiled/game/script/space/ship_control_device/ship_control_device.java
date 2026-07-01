// ════════════════════════════════════════════════════════════════════════════
//   File         : ship_control_device.java
//   Module       : Space Systems - Ship Deployment & Navigation
//   Author       : tford / patrick
//   Purpose      : Handles ship control device logic (unpack, pack, control link)
//
//   © 2004-2025 Sony Online Entertainment + SWG+ Dev Team
// ════════════════════════════════════════════════════════════════════════════

package script.space.ship_control_device;

import script.*;
import script.library.*;
import java.util.HashMap;
import java.util.Map;
import script.developer.buildout_utility;

import java.util.Vector;

public class ship_control_device extends script.base_script
{
    public static final int HEIGHT_THRESHOLD = 600; // Height to trigger space transition
    public ship_control_device()
    {
    }
    public static final string_id RENAME_SHIP = new string_id("sui", "rename_ship");
    public static final string_id PACK_SHIP = new string_id("sui", "pack_ship");
    public static final string_id PROMPT1 = new string_id("sui", "rename_ship_text");
    private static final Map<String, Float> PLANET_GROUND_LEVELS = new HashMap<>();

    static {
        PLANET_GROUND_LEVELS.put("tatooine", 0.0f);
        PLANET_GROUND_LEVELS.put("yavin4", 20.0f);
        PLANET_GROUND_LEVELS.put("mustafar", 50.0f);
        PLANET_GROUND_LEVELS.put("endor", 15.0f);
        PLANET_GROUND_LEVELS.put("talus", 5.0f);
        PLANET_GROUND_LEVELS.put("corellia", 8.0f);
        PLANET_GROUND_LEVELS.put("rori", 6.0f);
        PLANET_GROUND_LEVELS.put("dathomir", 18.0f);
        PLANET_GROUND_LEVELS.put("dantooine", 12.0f);
        PLANET_GROUND_LEVELS.put("kashyyyk_main", 30.0f);
        PLANET_GROUND_LEVELS.put("hoth2", 40.0f);
        PLANET_GROUND_LEVELS.put("naboo", 10.0f);
        PLANET_GROUND_LEVELS.put("lok", 7.0f);
    }
    public static final String[] ignoreRules = new String[]
    {
        "name_declined_number"
    };
    public static final String SPACE_MINING = "space_mining";
    public static final String IN_USE_OBJVAR = "ship_redeed.inUse";
    public static final int MAX_RESOURCE = 1000000;
    public static final boolean DEBUG_MODE = false; // Enable debug messages
    public static final string_id SID_TERMINAL_REDEED_STORAGE = new string_id("player_structure", "redeed_storage");
    public static final string_id SID_STORAGE_INCREASE_REDEED_TITLE = new string_id("player_structure", "sui_storage_redeed_title");
    public static final string_id SID_STORAGE_INCREASE_REDEED_PROMPT = new string_id("player_structure", "sui_storage_redeed_prompt");
    public int OnAttach(obj_id self) throws InterruptedException
    {
        setObjVar(self, "noTrade", 1);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        pobShipLotRefunder(self);
        setObjVar(self, "noTrade", 1);
        removeObjVar(self, IN_USE_OBJVAR);
        messageTo(self, "checkCollectionReactor", null, 2, false);
        return SCRIPT_CONTINUE;
    }
    public int OnGetAttributes(obj_id self, obj_id objPlayer, String[] strNames, String[] strAttribs) throws InterruptedException
    {
        int intIndex = utils.getValidAttributeIndex(strNames);
        if (intIndex == -1)
        {
            return SCRIPT_CONTINUE;
        }
        String strName = getAssignedName(self);
        if ((strName != null) && (!strName.equals("")))
        {
            strNames[intIndex] = "ship_name";
            strAttribs[intIndex] = strName;
            intIndex++;
        }
        return SCRIPT_CONTINUE;
    }
    public int OnTransferred(obj_id self, obj_id source, obj_id destination, obj_id transferer) throws InterruptedException
    {
        if (isIdValid(destination))
        {
            obj_id player = utils.getContainingPlayer(destination);
            if (isIdValid(player))
            {
                if (!isPlayer(player))
                {
                    return SCRIPT_CONTINUE;
                }
                obj_id ship = space_transition.getShipFromShipControlDevice(self);
                setOwner(ship, player);
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!utils.isNestedWithin(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        mi.addRootMenu(menu_info_types.SERVER_MENU1, RENAME_SHIP);
        obj_id objShip = space_transition.getShipFromShipControlDevice(self);
        if (isIdValid(objShip))
        {
            gunshipCheck(objShip);
            if (hasObjVar(objShip, player_structure.OBJVAR_STRUCTURE_STORAGE_INCREASE))
            {
                mi.addRootMenu(menu_info_types.DICE_ROLL, SID_TERMINAL_REDEED_STORAGE);
            }
            else 
            {
                mi.addRootMenu(menu_info_types.SERVER_MENU2, PACK_SHIP);
            }
            String strChassisType = getShipChassisType(objShip);
            if (strChassisType.equals("player_sorosuub_space_yacht"))
            {
                menu_info_data data = mi.getMenuItemByType(menu_info_types.ITEM_USE);
                string_id strSpam = new string_id("space/space_interaction", "repair");
                mi.addRootMenu(menu_info_types.ITEM_USE, strSpam);
            }
            if (isShipSlotInstalled(objShip, ship_chassis_slot_type.SCST_cargo_hold))
            {
                string_id strSpam = new string_id("space/space_interaction", "view");
                mi.addRootMenu(menu_info_types.SERVER_MENU3, strSpam);
                string_id strSpam2 = new string_id("space/space_interaction", "unload");
                mi.addRootMenu(menu_info_types.SERVER_MENU4, strSpam2);
            }
        }
   if (!isSpaceScene()) {
    obj_id pilotShip = getPilotedShip(player);
    String planet = getCurrentSceneName();

    // player must be either piloting a ship to land it or in world to launch it.
    if (!isIdValid(pilotShip) && isInWorld(player)) {
        if (!planet.equals("tutorial") && !planet.equals("dungeon1")) {
            string_id atmo = new string_id("space/space_interaction", "launch_ship");
            mi.addRootMenu(menu_info_types.SERVER_MENU5, atmo);
        }
    } else if (isIdValid(pilotShip)) {
        string_id atmo = new string_id("space/space_interaction", "land_ship");
        mi.addRootMenu(menu_info_types.SERVER_MENU6, atmo);
    }
}
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (!utils.isNestedWithin(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        if (item == menu_info_types.ITEM_USE)
        {
            obj_id objShip = space_transition.getShipFromShipControlDevice(self);
            if (isIdValid(objShip))
            {
                String strChassisType = getShipChassisType(objShip);
                if (strChassisType.equals("player_sorosuub_space_yacht"))
                {
                    string_id strSpam = new string_id("space/space_interaction", "complete_repair");
                    sendSystemMessage(player, strSpam);
                    space_crafting.repairDamage(player, objShip, 1.0f);
                }
            }
        }
        else if (item == menu_info_types.SERVER_MENU1)
        {
            if (isSpaceScene())
            {
                string_id strSpam = new string_id("space/space_interaction", "no_rename_space");
                sendSystemMessage(player, strSpam);
                return SCRIPT_CONTINUE;
            }
            sui.inputbox(self, player, utils.packStringId(PROMPT1), sui.OK_CANCEL, utils.packStringId(RENAME_SHIP), sui.INPUT_NORMAL, null, "renameShip", null);
        }
        else if (item == menu_info_types.SERVER_MENU2)
        {
            obj_id objShip = space_transition.getShipFromShipControlDevice(self);
            if (!isIdValid(objShip))
            {
                return SCRIPT_CONTINUE;
            }
            int[] installed = space_crafting.getShipInstalledSlots(objShip);
            if (getIntObjVar(self, IN_USE_OBJVAR) == 1)
            {
                return SCRIPT_CONTINUE;
            }
            setObjVar(self, IN_USE_OBJVAR, 1);
            if ((getShipChassisType(objShip)).equals("player_sorosuub_space_yacht"))
            {
                sendSystemMessage(player, new string_id("space/space_interaction", "space_yacht"));
                removeObjVar(self, IN_USE_OBJVAR);
                return SCRIPT_CONTINUE;
            }
            if ((getShipChassisType(objShip)).equals("player_prototype_z95") || (getShipChassisType(objShip)).equals("player_prototype_tiefighter") || (getShipChassisType(objShip)).equals("player_prototype_hutt_light"))
            {
                sendSystemMessage(player, new string_id("space/space_interaction", "newbie_ship"));
                removeObjVar(self, "ship_redeed.inUse");
                return SCRIPT_CONTINUE;
            }
            if ((getShipChassisType(objShip)).equals("player_basic_z95") || (getShipChassisType(objShip)).equals("player_basic_tiefighter") || (getShipChassisType(objShip)).equals("player_basic_hutt_light"))
            {
                sendSystemMessage(player, new string_id("space/space_interaction", "newbie_ship"));
                removeObjVar(self, IN_USE_OBJVAR);
                return SCRIPT_CONTINUE;
            }
            if (space_utils.isShipWithInterior(objShip))
            {
                int intItemCount = player_structure.getStructureNumItems(objShip);
                if (intItemCount > 0)
                {
                    sendSystemMessage(player, new string_id("space/space_interaction", "items_in_ship"));
                    removeObjVar(self, IN_USE_OBJVAR);
                    return SCRIPT_CONTINUE;
                }
            }
            if (installed.length == 0)
            {
                if (hasObjVar(self, space_crafting.TCG_SHIP_TYPE))
                {
                    String staticItemName = getStringObjVar(self, space_crafting.TCG_SHIP_DEED_STATIC_ITEM_NAME);
                    if (staticItemName == null || staticItemName.equals(""))
                    {
                        CustomerServiceLog("ship_redeed", "PLAYER " + player + " FAILED TO REDEED SHIP " + objShip + " FROM SCD " + self + " BECAUSE OF DEED STATIC ITEM NAME BEING INVALID");
                        removeObjVar(self, IN_USE_OBJVAR);
                        return SCRIPT_CONTINUE;
                    }
                    obj_id pInv = utils.getInventoryContainer(player);
                    if (!isValidId(pInv) || !exists(pInv))
                    {
                        CustomerServiceLog("ship_redeed", "PLAYER " + player + " FAILED TO REDEED SHIP " + objShip + " FROM SCD " + self + " BECAUSE OF PLAYER INVENTORY WAS INVALID");
                        removeObjVar(self, IN_USE_OBJVAR);
                        return SCRIPT_CONTINUE;
                    }
                    obj_id deedOid = static_item.createNewItemFunction(staticItemName, pInv);
                    if (!isValidId(deedOid) || !exists(deedOid))
                    {
                        CustomerServiceLog("ship_redeed", "PLAYER " + player + " FAILED TO REDEEDED SHIP " + objShip + " FROM SCD " + self + " TO MAKE TCG STATIC ITEM DEED " + staticItemName + ". The static item could not be created in player inventory: " + pInv);
                        removeObjVar(self, IN_USE_OBJVAR);
                        return SCRIPT_CONTINUE;
                    }
                    sendSystemMessage(player, new string_id("space/space_interaction", "packed"));
                    CustomerServiceLog("ship_redeed", "PLAYER " + player + " REDEEDED SHIP " + objShip + " FROM SCD " + self + " TO MAKE TCG STATIC ITEM DEED " + staticItemName);
                    destroyObject(self);
                    return SCRIPT_CONTINUE;
                }
                obj_id newShip = packShipDeed(self, objShip, player);
                if (isIdValid(newShip))
                {
                    sendSystemMessage(player, new string_id("space/space_interaction", "packed"));
                    CustomerServiceLog("ship_redeed", "PLAYER " + player + " REDEEDED SHIP " + objShip + " FROM SCD " + self + " TO MAKE DEED " + newShip);
                    destroyObject(self);
                    return SCRIPT_CONTINUE;
                }
                else 
                {
                    removeObjVar(self, IN_USE_OBJVAR);
                    return SCRIPT_CONTINUE;
                }
            }
            else 
            {
                sendSystemMessage(player, new string_id("space/space_interaction", "components_installed"));
                removeObjVar(self, IN_USE_OBJVAR);
                return SCRIPT_CONTINUE;
            }
        }
        else if (item == menu_info_types.SERVER_MENU3)
        {
            obj_id objShip = space_transition.getShipFromShipControlDevice(self);
            if (!isIdValid(objShip))
            {
                return SCRIPT_CONTINUE;
            }
            if (getShipCargoHoldContentsCurrent(objShip) <= 0)
            {
                sendSystemMessage(player, new string_id("space/space_interaction", "empty_hold"));
                return SCRIPT_CONTINUE;
            }
            obj_id[] resourceTypes = getShipCargoHoldContentsResourceTypes(objShip);
            String[] entries = new String[resourceTypes.length];
            for (int i = 0; i < entries.length; i++)
            {
                string_id stIdResourceName = utils.unpackString(getResourceName(resourceTypes[i]));
                entries[i] = getShipCargoHoldContent(objShip, resourceTypes[i]) + " " + localize(stIdResourceName);
            }
            String prompt = utils.packStringId(new string_id(SPACE_MINING, "prompt"));
            String title = utils.packStringId(new string_id(SPACE_MINING, "title"));
            int pid = sui.listbox(self, player, prompt, sui.OK_ONLY, title, entries, "cargoList", false, false);
            showSUIPage(pid);
            return SCRIPT_CONTINUE;
        }
        else if (item == menu_info_types.SERVER_MENU4)
        {
            obj_id objShip = space_transition.getShipFromShipControlDevice(self);
            obj_id pInv = utils.getInventoryContainer(player);
            int maxInventorySpace = getVolumeFree(pInv);
            if (getShipCargoHoldContentsCurrent(objShip) <= 0)
            {
                sendSystemMessage(player, new string_id("space/space_interaction", "empty_hold"));
                return SCRIPT_CONTINUE;
            }
            obj_id[] resourceTypes = getShipCargoHoldContentsResourceTypes(objShip);
            boolean resourcesGiven = false;
            for (obj_id resourceType : resourceTypes) {
                if (!giveResourceReward(resourceType, player, getShipCargoHoldContent(objShip, resourceType), objShip)) {
                    sendSystemMessage(player, new string_id("space/space_interaction", "full_inventory"));
                    break;
                } else {
                    resourcesGiven = true;
                }
            }
            if (resourcesGiven)
            {
                sendSystemMessage(player, new string_id("space/space_interaction", "resources_transferred"));
            }
            return SCRIPT_CONTINUE;
        }
else if (item == menu_info_types.SERVER_MENU5)
{
    obj_id ship = space_transition.getShipFromShipControlDevice(self);
    if (isIdValid(ship)) {
        location loc = getLocation(player);
        space_transition.unpackShipForPlayer(player, ship);
        setLocation(ship, new location(loc.x, loc.y + 10, loc.z));

        dictionary params = new dictionary();
        params.put("ship", ship);
        params.put("player", player);
        messageTo(self, "checkShipZCoordinate", params, 5, false); // Check every 5s
    }
}
else if (item == menu_info_types.SERVER_MENU6)
{
    obj_id playerShip = getPilotedShip(player);
    // player must be piloting ship in order to land/pack it.
    if (isIdValid(playerShip)) {
        obj_id containingShip = space_transition.getContainingShip(player);
        if (isIdValid(containingShip)) {
            // Pack the ship
            space_transition.packShip(containingShip);

            // Get player's current location
            location playerLoc = getLocation(player);
            location shipLoc = getLocation(containingShip);
            if (playerLoc != null && shipLoc != null) {
                float groundLevel = shipLoc.y; // default fallback
                boolean groundSet = false;

                if (Math.abs(shipLoc.x) <= 8192 && Math.abs(shipLoc.z) <= 8192) {
                    try {
                        groundLevel = getHeightAtLocation(shipLoc.x, shipLoc.z);
                        groundSet = true;
                    } catch (Exception e) {
                        groundSet = false;
                    }
                }

                if (!groundSet) {
                    String planet = getCurrentSceneName();
                    if (PLANET_GROUND_LEVELS.containsKey(planet)) {
                        groundLevel = PLANET_GROUND_LEVELS.get(planet);
                        groundSet = true;
                    }
                }

                if (groundSet) {
                    location safeGroundLoc = new location(shipLoc.x, groundLevel + 1.0f, shipLoc.z); // Add small buffer
                    setLocation(player, safeGroundLoc);
                } else {
                    setLocation(player, new location(shipLoc.x, shipLoc.y, shipLoc.z));
                }
            }
        }
    }
}
        else if (item == menu_info_types.DICE_ROLL)
        {
            obj_id objShip = space_transition.getShipFromShipControlDevice(self);
            if (!hasObjVar(objShip, player_structure.OBJVAR_STRUCTURE_STORAGE_INCREASE))
            {
                return SCRIPT_CONTINUE;
            }
            player_structure.displayAvailableNonGenericStorageTypes(player, self, objShip);
        }
        return SCRIPT_CONTINUE;
    }
    
    public void debug(String message) {
        if (DEBUG_MODE) {
            LOG("space_debug", message);
        }
    }

    private boolean shouldDebug(obj_id player) throws InterruptedException {
        return (DEBUG_MODE && isSpaceScene()) || isGod(player);
    }

   public int checkShipZCoordinate(obj_id self, dictionary params) throws InterruptedException {
    if (params == null) {
        debug("checkShipZCoordinate called with null params");
        return SCRIPT_CONTINUE;
    }

    obj_id ship = params.getObjId("ship");
    obj_id player = params.getObjId("player");

    if (!isIdValid(ship) || !exists(ship) || !isIdValid(player) || !exists(player)) {
        debug("Invalid ship or player object in params.");
        return SCRIPT_CONTINUE;
    }

    if (space_transition.getContainingShip(player) != ship) {
        debug("Player is no longer in the ship. Skipping transition check.");
        return SCRIPT_CONTINUE;
    }

    location playerLoc = getLocation(player);
    location shipLoc = getLocation(ship);

    if (playerLoc == null || shipLoc == null) {
        debug("Player or Ship location is null.");
        return SCRIPT_CONTINUE;
    }

    float groundLevel = 0.0f;
    boolean groundLevelSet = false;
    String groundSource = "unknown";

    // Prefer terrain height when within map bounds
    if (Math.abs(playerLoc.x) <= 8192 && Math.abs(playerLoc.z) <= 8192) {
        groundLevel = getHeightAtLocation(playerLoc.x, playerLoc.z);
        groundSource = "terrain_height";
        groundLevelSet = true;
    }

    if (!groundLevelSet && Math.abs(playerLoc.x) <= 8192 && Math.abs(playerLoc.z) <= 8192) {
        try {
            String buildoutArea = getBuildoutAreaName(playerLoc.x, playerLoc.z, getCurrentSceneName());
            location groundCoords = buildout_utility.getBuildoutRootCoords(buildoutArea);
            groundLevel = groundCoords.y;
            groundSource = "buildout_utility";
            groundLevelSet = true;
        } catch (Exception e) {
            debug("Buildout ground level lookup failed. Will try fallback.");
        }
    }

    // Fallback to static map
    if (!groundLevelSet) {
        String planet = getCurrentSceneName();
        if (PLANET_GROUND_LEVELS.containsKey(planet)) {
            groundLevel = PLANET_GROUND_LEVELS.get(planet);
            groundSource = "PLANET_GROUND_LEVELS";
            groundLevelSet = true;
        }
    }

    // Optional: Try terrain height as last resort (pseudo-method, implement if engine supports)
    /*
    if (!groundLevelSet) {
        try {
            groundLevel = getTerrainHeight(getCurrentSceneName(), playerLoc.x, playerLoc.y);
            groundSource = "getTerrainHeight";
            groundLevelSet = true;
        } catch (Exception e) {
            debug("Terrain height fetch failed.");
        }
    }
    */
//   Update Version : 1.3 - Implemented safe height calculation using fallback and clamp for ship spawn


    // Final fallback to current ship Y
    if (!groundLevelSet) {
        groundLevel = shipLoc.y;
        groundSource = "ship Y fallback";
    }

    // Retrieve or set initial position
    float initialZ = params.containsKey("initialZ") ? params.getFloat("initialZ") : shipLoc.y;
    float baseHeight = Math.max(groundLevel, initialZ);
    float transitionHeight = baseHeight + HEIGHT_THRESHOLD;

    float currentAltitude = shipLoc.y - groundLevel;
    float percentToTransition = ((shipLoc.y - baseHeight) / (transitionHeight - baseHeight)) * 100.0f;

    if (shouldDebug(player)) {
        debug("---- GPWS DEBUG ----");
        debug("Ground Level: " + groundLevel + " (Source: " + groundSource + ")");
        debug("Ship Y: " + shipLoc.y);
        debug("Initial Y: " + initialZ);
        debug("Base Height: " + baseHeight);
        debug("Transition Height: " + transitionHeight);
        debug("Current Altitude: " + currentAltitude);
        debug("Progress to Transition: " + (int)percentToTransition + "%");

        sendSystemMessageTestingOnly(player, "DEBUG: Altitude = " + (int)currentAltitude + " meters");
        sendSystemMessageTestingOnly(player, "DEBUG: " + (int)percentToTransition + "% to transition");
    }

if (shipLoc.y >= transitionHeight) {
    // Extra check: is player still in the ship?
if (space_transition.getContainingShip(player) == ship) {
    if (shouldDebug(player)) {
        sendSystemMessage(player, new string_id("space/space_interaction", "debug_height_reached"));
        debug("Ship has reached transition height. Initiating space transition...");
    }
    transitionToSpace(player, ship);
} else {
    debug("Player is no longer in the ship. Skipping transition.");
}
    return SCRIPT_CONTINUE;
}

    // Store position
    params.put("initialZ", initialZ);

    // Schedule next check
    messageTo(self, "checkShipZCoordinate", params, 5, false);

    return SCRIPT_CONTINUE;
}

public void transitionToSpace(obj_id player, obj_id ship) throws InterruptedException {
    if (!isIdValid(player) || !isIdValid(ship)) {
        LOG("space_debug", "Invalid player or ship in transitionToSpace");
        return;
    }

    obj_id shipControlDevice = utils.getObjIdLocalVar(player, "objControlDevice");
    location warpLocation = (shipControlDevice != null) ? utils.getLocationScriptVar(shipControlDevice, "space.loc.space") : null;

    if (warpLocation == null) {
        // Fallback: get the current planet and build a default space location from it
        location currentLocation = getLocation(player);
        String planet = (currentLocation != null && currentLocation.area != null) ? currentLocation.area : "tatooine";
        String spacePlanet = "space_" + planet.toLowerCase();

        warpLocation = new location(1000.0f, 2000.0f, 3000.0f, spacePlanet);
    }

    location locFinalDestination = warpLocation;
    if (locFinalDestination == null) {
        sendSystemMessage(player, new string_id("shared_hyperspace", "zone_too_full_use_travel"));
        return;
    }

    sendSystemMessage(player, new string_id("space/space_interaction", "launching_to_space"));
    sendSystemMessage(player, new string_id("space/space_interaction", "hyperspace_route_done"));

    String launchHyperspacePoint = "ground_to_space";
    if (warpLocation != null && warpLocation.area != null && !warpLocation.area.equals("")) {
        launchHyperspacePoint = warpLocation.area;
    }
    hyperspacePrepareShipOnClient(player, launchHyperspacePoint);
    playMusic(player, "sound/ship_hyperspace_countdown.snd");

    obj_id[] membersApprovedByShipOwner = new obj_id[0];

    space_transition.clearOvertStatus(ship);
    if (callable.hasAnyCallable(player)) {
        callable.storeCallables(player);
    }
    stealth.checkForAndMakeVisible(player);

    int shapechange = buff.getBuffOnTargetFromGroup(player, "shapechange");
    if (shapechange != 0) {
        buff.removeBuff(player, shapechange);
        sendSystemMessage(player, event_perk.SHAPECHANGE_SPACE);
    }

    space_transition.launch(player, ship, membersApprovedByShipOwner, locFinalDestination, null);
}

    public int OnAboutToBeTransferred(obj_id self, obj_id destContainer, obj_id transferer) throws InterruptedException
    {
        if (isIdValid(getContainedBy(self)))
        {
            obj_id[] contents = getContents(self);
            if (contents == null || contents.length == 0)
            {
                return SCRIPT_OVERRIDE;
            }
        }
        if (isIdValid(destContainer))
        {
            obj_id player = utils.getContainedBy(destContainer);
            if (isIdValid(player))
            {
                if (!isPlayer(player))
                {
                    sendSystemMessage(player, new string_id("space/space_interaction", "noadd"));
                    return SCRIPT_OVERRIDE;
                }
                if (utils.hasLocalVar(player, "ctsBeingUnpacked"))
                {
                    return SCRIPT_CONTINUE;
                }
                boolean belowLimit = space_transition.isPlayerBelowShipLimit(player, destContainer);
                if (belowLimit == true)
                {
                    return SCRIPT_CONTINUE;
                }
                else 
                {
                    sendSystemMessage(player, new string_id("space/space_interaction", "toomanyships"));
                    return SCRIPT_OVERRIDE;
                }
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int OnDestroy(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, IN_USE_OBJVAR))
        {
            obj_id ship = space_transition.getShipFromShipControlDevice(self);
            if (isIdValid(ship))
            {
                String type = getShipChassisType(ship);
                obj_id player = utils.getContainingPlayer(self);
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int forceCollectionReactorInit(obj_id self, dictionary params) throws InterruptedException
    {
        messageTo(self, "checkCollectionReactor", null, 2, false);
        return SCRIPT_CONTINUE;
    }
    public int checkCollectionReactor(obj_id self, dictionary params) throws InterruptedException
    {
        CustomerServiceLog("ShipComponents", "Initializing Collection reactor check for: (" + self + ") " + getName(self) + " a ship control device in player datapad.");
        blog("component_fix", "SCD - CHECKING SHIP " + self + " FOR COLLECTION REACTOR");
        obj_id ship = space_transition.getShipFromShipControlDevice(self);
        if (!isIdValid(ship))
        {
            return SCRIPT_CONTINUE;
        }
        boolean success = space_crafting.checkForCollectionReactor(self, ship);
        if (success)
        {
            CustomerServiceLog("ShipComponents", "Collection reactor found and handled for: (" + self + ") " + getName(self));
            blog("component_fix", "SCD - checkCollectionReactor: SUCCESS FROM space_crafting library");
            return SCRIPT_CONTINUE;
        }
        CustomerServiceLog("ShipComponents", "Collection reactor NOT found or failed to uninstall for: (" + self + ") " + getName(self));
        blog("component_fix", "SCD - checkCollectionReactor: FAIL FROM space_crafting library");
        return SCRIPT_CONTINUE;
    }
    public int handleStorageRedeedChoice(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id player = sui.getPlayerId(params);
        String accessFee = sui.getInputBoxText(params);
        int btn = sui.getIntButtonPressed(params);
        if (btn == sui.BP_CANCEL)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id objShip = space_transition.getShipFromShipControlDevice(self);
        if (!isIdValid(objShip) || getOwner(objShip) != player)
        {
            return SCRIPT_CONTINUE;
        }
        if (hasObjVar(objShip, player_structure.OBJVAR_STRUCTURE_STORAGE_INCREASE))
        {
            int storageRedeedSelected = 0;
            if (params.containsKey(sui.LISTBOX_LIST + "." + sui.PROP_SELECTEDROW))
            {
                storageRedeedSelected = sui.getListboxSelectedRow(params);
                if (storageRedeedSelected < 0)
                {
                    return SCRIPT_CONTINUE;
                }
            }
            if (player_structure.decrementStorageAmount(player, objShip, self, storageRedeedSelected))
            {
                sendSystemMessage(player, new string_id("player_structure", "storage_increase_redeeded"));
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int cargoList(obj_id self, dictionary params) throws InterruptedException
    {
        return SCRIPT_CONTINUE;
    }
    public int renameShip(obj_id self, dictionary params) throws InterruptedException
    {
        int intButton = sui.getIntButtonPressed(params);
        if (intButton == sui.BP_CANCEL)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id player = sui.getPlayerId(params);
        String newShipName = sui.getInputBoxText(params);
        if (isNameReserved(newShipName, ignoreRules) != true && newShipName.length() < 21)
        {
            setName(self, newShipName);
            obj_id objShip = space_transition.getShipFromShipControlDevice(self);
            if (isIdValid(objShip))
            {
                space_transition.setShipName(objShip, player, self);
            }
            return SCRIPT_CONTINUE;
        }
        else 
        {
            string_id msg2 = new string_id("sui", "rename_ship_reserved");
            sendSystemMessage(player, msg2);
            return SCRIPT_CONTINUE;
        }
    }
    public obj_id packShipDeed(obj_id pcd, obj_id ship, obj_id player) throws InterruptedException
    {
        String type = getShipChassisType(ship);
        String newType = type.substring(7, type.length());
        obj_id inventory = utils.getInventoryContainer(player);
        float mass = getChassisComponentMassMaximum(ship);
        float hp = getShipMaximumChassisHitPoints(ship);
        float currentHp = getShipCurrentChassisHitPoints(ship);
        obj_id newDeed = createObject("object/tangible/ship/crafted/chassis/" + newType + "_deed.iff", inventory, "");
        if (isIdValid(newDeed))
        {
            setObjVar(newDeed, "ship_chassis.mass", mass);
            setObjVar(newDeed, "ship_chassis.hp", hp);
            setObjVar(newDeed, "ship_chassis.currentHp", currentHp);
            setObjVar(newDeed, "ship_chassis.type", newType);
            return newDeed;
        }
        else 
        {
            return null;
        }
    }
    public void pobShipLotRefunder(obj_id pcd) throws InterruptedException
    {
        obj_id player = utils.getContainingPlayer(pcd);
        if (!isIdValid(player))
        {
            return;
        }
        if (!hasObjVar(pcd, "lotReqRemoved"))
        {
            obj_id ship = space_transition.getShipFromShipControlDevice(pcd);
            if (!isIdValid(ship))
            {
                return;
            }
            String type = getShipChassisType(ship);
            if (space_utils.isPobType(type))
            {
                adjustLotCount(getPlayerObject(player), -1);
                setObjVar(pcd, "lotReqRemoved", true);
                string_id strSpam = new string_id("space/space_interaction", "pob_lot_returned");
                sendSystemMessage(player, strSpam);
            }
        }
        return;
    }
    public boolean giveResourceReward(obj_id objResourceId, obj_id player, int intAmount, obj_id objShip) throws InterruptedException
    {
        obj_id objContainer = utils.getInventoryContainer(player);
        obj_id objStack = getResourceStack(objContainer, objResourceId);
        if (isIdValid(objStack))
        {
            int intCount = getResourceContainerQuantity(objStack);
            intCount += intAmount;
            if (intCount > MAX_RESOURCE)
            {
                intCount = intCount - MAX_RESOURCE;
                if (shouldDebug(player)) {
                    sendSystemMessageTestingOnly(player, "Add the Diff!  " + intCount);
                }
                intAmount = intAmount - intCount;
                addResourceToContainer(objStack, objResourceId, intAmount, null);
                objStack = null;
            }
            else 
            {
                addResourceToContainer(objStack, objResourceId, intAmount, null);
                if (shouldDebug(player)) {
                    sendSystemMessageTestingOnly(player, "Incrementing count!");
                }
            }
        }
        else 
        {
            objStack = createResourceCrate(objResourceId, intAmount, objContainer);
            if (objStack == null)
            {
                return false;
            }
        }
        setShipCargoHoldContent(objShip, objResourceId, 0);
        return true;
    }
    public obj_id getResourceStack(obj_id objContainer, obj_id objResource) throws InterruptedException
    {
        if (!isIdValid(objContainer))
        {
            return null;
        }
        obj_id[] objContents = getContents(objContainer);
        if (objContents == null)
        {
            return null;
        }
        for (obj_id objContent : objContents) {
            obj_id objType = getResourceContainerResourceType(objContent);
            if (objType == objResource) {
                int intCount = getResourceContainerQuantity(objContent);
                if (intCount < MAX_RESOURCE) {
                    return objContent;
                }
            }
        }
        return null;
    }
    public void gunshipCheck(obj_id objShip) throws InterruptedException
    {
        if (!isIdValid(objShip))
        {
            return;
        }
        String type = getShipChassisType(objShip);
        if (type.startsWith("player_gunship") && (!hasObjVar(objShip, "structure.capacity_override")))
        {
            int newCapacity;
            if (hasObjVar(objShip, "structureChange.storageIncrease"))
            {
                newCapacity = getIntObjVar(objShip, "structureChange.storageIncrease");
                newCapacity = newCapacity - 50;
                if (newCapacity <= 0)
                {
                    removeObjVar(objShip, "structureChange.storageIncrease");
                }
                else if (newCapacity > 0)
                {
                    setObjVar(objShip, "structureChange.storageIncrease", newCapacity);
                }
                setObjVar(objShip, "structure.capacity_override", 150);
            }
        }
        return;
    }
    public boolean blog(String category, String msg) throws InterruptedException
    {
        return true;
    }
}
