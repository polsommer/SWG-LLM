package script.space.terminal;

import script.*;
import script.library.*;
import java.util.Vector;

public class ace_terminal_space extends script.terminal.base.base_terminal {

    public ace_terminal_space() {}

    public static final string_id SID_LAUNCH_SHIP = new string_id("space/space_terminal", "launch_ship");
    public static final string_id SID_MUSTAFAR = new string_id("space/space_terminal", "mustafar_exception");
    public static final string_id SID_NOT_IN_COMBAT = new string_id("travel", "not_in_combat");

    public int OnInitialize(obj_id self) throws InterruptedException {
        requestPreloadCompleteTrigger(self);
        return SCRIPT_CONTINUE;
    }

    public int OnPreloadComplete(obj_id self) throws InterruptedException {
        // Default launch location and scene for universal use
        float defaultSpaceX = 5000.0f;
        float defaultSpaceY = 5000.0f;
        float defaultSpaceZ = 5000.0f;
        String defaultSpaceScene = "space_generic";

        String defaultGroundScene = "naboo";
        float defaultGroundX = 500.0f;
        float defaultGroundY = 500.0f;
        float defaultGroundZ = 500.0f;
        String defaultPointName = "Theed Spaceport";

        dictionary dctTeleportInfo = new dictionary();
        dctTeleportInfo.put("spaceX", defaultSpaceX);
        dctTeleportInfo.put("spaceY", defaultSpaceY);
        dctTeleportInfo.put("spaceZ", defaultSpaceZ);
        dctTeleportInfo.put("spaceScene", defaultSpaceScene);
        dctTeleportInfo.put("groundX", defaultGroundX);
        dctTeleportInfo.put("groundY", defaultGroundY);
        dctTeleportInfo.put("groundZ", defaultGroundZ);
        dctTeleportInfo.put("groundScene", defaultGroundScene);
        dctTeleportInfo.put("pointName", defaultPointName);

        // Set script variables for universal space and ground locations
        utils.setScriptVar(self, "space.loc.space", new location(dctTeleportInfo.getFloat("spaceX"), dctTeleportInfo.getFloat("spaceY"), dctTeleportInfo.getFloat("spaceZ"), dctTeleportInfo.getString("spaceScene")));
        utils.setScriptVar(self, "space.locationName", "Universal Space Launch Point");
        utils.setScriptVar(self, "space.loc.ground", new location(dctTeleportInfo.getFloat("groundX"), dctTeleportInfo.getFloat("groundY"), dctTeleportInfo.getFloat("groundZ"), dctTeleportInfo.getString("groundScene")));
        utils.setScriptVar(self, "ground.locationName", dctTeleportInfo.getString("pointName"));

        return SCRIPT_CONTINUE;
    }

    public int OnAboutToLaunchIntoSpace(obj_id self, obj_id player, obj_id shipControlDevice, obj_id[] membersApprovedByShipOwner, String destinationGroundPlanet, String destinationGroundTravelPoint) throws InterruptedException {
        LOG("space", "triggered OnAboutToLaunchIntoSpace");

        if (!doSpacePrecheck(player)) {
            return SCRIPT_CONTINUE;
        }

        if (!features.isSpaceEdition(player)) {
            LOG("space", "NO EXPANSION");
            string_id strSpam = new string_id("space/space_interaction", "no_space_expansion");
            sendSystemMessage(player, strSpam);
            return SCRIPT_CONTINUE;
        }

        if (ai_lib.isInCombat(player)) {
            sendSystemMessage(player, SID_NOT_IN_COMBAT);
            return SCRIPT_CONTINUE;
        }

        boolean isStarportToStarportLaunch = destinationGroundPlanet != null && !destinationGroundPlanet.isEmpty();
        if (travel.isTravelBlocked(player, !isStarportToStarportLaunch)) {
            return SCRIPT_CONTINUE;
        }

        location warpLocation = utils.getLocationScriptVar(self, "space.loc.space");
        if (warpLocation == null) {
            LOG("space", "OnAboutToLaunchIntoSpace: Warp location 'space.loc.space' is null on terminal");
            return SCRIPT_CONTINUE;
        }

        if (isIdValid(shipControlDevice)) {
            obj_id ship = space_transition.getShipFromShipControlDevice(shipControlDevice);
            if (isIdValid(ship)) {
                utils.setLocalVar(player, "objControlDevice", shipControlDevice);
                callable.storeCallables(player);
                vehicle.storeAllVehicles(player);

                if (isStarportToStarportLaunch) {
                    doStarportToStarportLaunch(player, ship, membersApprovedByShipOwner, destinationGroundPlanet, destinationGroundTravelPoint);
                } else {
                    location locDestination = space_utils.getRandomLocationInSphere(warpLocation, 150, 300);
                    location locFinalDestination = getFinalHyperspaceLocation(player, locDestination);
                    if (locFinalDestination == null) {
                        string_id tooFull = new string_id("shared_hyperspace", "zone_too_full_use_travel");
                        sendSystemMessage(player, tooFull);
                        return SCRIPT_CONTINUE;
                    }
                    utils.setScriptVar(player, "strLaunchPointName", utils.getStringScriptVar(self, "space.locationName"));
                    location groundLoc = utils.getLocationScriptVar(self, "space.loc.ground");
                    launch(player, ship, membersApprovedByShipOwner, locFinalDestination, groundLoc);
                }
            }
        }
        return SCRIPT_CONTINUE;
    }

    public boolean doSpacePrecheck(obj_id player) throws InterruptedException {
        // Check if the player is incapacitated
        if (isIncapacitated(player)) {
            string_id strSpam = new string_id("space/space_interaction", "no_use_terminal_incapacitated");
            sendSystemMessage(player, strSpam);
            return false;
        }

        // Check if the player is dead
        if (isDead(player)) {
            string_id strSpam = new string_id("space/space_interaction", "no_use_terminal_dead");
            sendSystemMessage(player, strSpam);
            return false;
        }

        // Check if the player is in combat
        if (ai_lib.isInCombat(player)) {
            sendSystemMessage(player, SID_NOT_IN_COMBAT);
            return false;
        }

        return true;
    }

    public location getFinalHyperspaceLocation(obj_id player, location initialLocation) throws InterruptedException {
        // Perform additional checks or modifications to determine the final destination
        return initialLocation; // For now, return the initial location directly
    }

    public void doStarportToStarportLaunch(obj_id player, obj_id ship, obj_id[] membersApprovedByShipOwner, String planet, String pointName) throws InterruptedException {
        if (!getPlanetTravelPointInterplanetary(planet, pointName)) {
            return;
        }

        if (planet.equals("kashyyyk_main") && !features.hasEpisode3Expansion(player)) {
            sendSystemMessage(player, travel.SID_KASHYYYK_UNAUTHORIZED);
            return;
        }

        if (space_utils.isBasicShip(ship)) {
            location locTest = getLocation(player);
            if (!planet.equals(locTest.area)) {
                string_id strSpam = new string_id("space/space_interaction", "no_travel_basic");
                sendSystemMessage(player, strSpam);
                return;
            }
        }

        Vector groupMembersToWarp = utils.addElement(null, player);
        Vector groupMemberStartIndex = utils.addElement(null, 0);
        Vector shipStartLocations = space_transition.getShipStartLocations(ship);

        if (shipStartLocations != null && shipStartLocations.size() > 0) {
            int startIndex = 0;
            location playerLoc = getLocation(player);
            if (isIdValid(playerLoc.cell)) {
                for (obj_id member : membersApprovedByShipOwner) {
                    if (member != player && exists(member) && getLocation(member).cell == playerLoc.cell) {
                        startIndex = space_transition.getNextStartIndex(shipStartLocations, startIndex);
                        if (startIndex <= shipStartLocations.size()) {
                            groupMembersToWarp = utils.addElement(groupMembersToWarp, member);
                            groupMemberStartIndex = utils.addElement(groupMemberStartIndex, startIndex);
                        }
                        if (callable.hasAnyCallable(member)) {
                            callable.storeCallables(member);
                        }
                    }
                }
            }
        }

        for (Object o : groupMembersToWarp) {
            travel.movePlayerToDestination((obj_id) o, planet, pointName);
        }
    }

    public void launch(obj_id player, obj_id ship, obj_id[] membersApprovedByShipOwner, location warpLocation, location groundLoc) throws InterruptedException {
        callable.storeCallables(player);
        stealth.checkForAndMakeVisible(player);
        buff.removeBuff(player, buff.getBuffOnTargetFromGroup(player, "shapechange"));
        space_transition.clearOvertStatus(ship);

        Vector groupMembersToWarp = utils.addElement(null, player);
        Vector groupMemberStartIndex = utils.addElement(null, 0);
        Vector shipStartLocations = space_transition.getShipStartLocations(ship);

        if (shipStartLocations != null && shipStartLocations.size() > 0) {
            int startIndex = 0;
            location playerLoc = getLocation(player);
            if (isIdValid(playerLoc.cell)) {
                for (obj_id member : membersApprovedByShipOwner) {
                    if (member != player && exists(member) && getLocation(member).cell == playerLoc.cell) {
                        if (features.isSpaceEdition(member)) {
                            startIndex = space_transition.getNextStartIndex(shipStartLocations, startIndex);
                            if (startIndex <= shipStartLocations.size()) {
                                groupMembersToWarp = utils.addElement(groupMembersToWarp, member);
                                groupMemberStartIndex = utils.addElement(groupMemberStartIndex, startIndex);
                            }
                        } else {
                            sendSystemMessage(member, new string_id("space/space_interaction", "no_space_expansion"));
                        }
                    }
                }
            }
        }

        for (int i = 0; i < groupMembersToWarp.size(); ++i) {
            callable.storeCallables((obj_id) groupMembersToWarp.get(i));
            stealth.checkForAndMakeVisible((obj_id) groupMembersToWarp.get(i));
            buff.removeBuff((obj_id) groupMembersToWarp.get(i), buff.getBuffOnTargetFromGroup((obj_id) groupMembersToWarp.get(i), "shapechange"));
            space_transition.setLaunchInfo((obj_id) groupMembersToWarp.get(i), ship, (Integer) groupMemberStartIndex.get(i), groundLoc);
            warpPlayer((obj_id) groupMembersToWarp.get(i), warpLocation.area, warpLocation.x, warpLocation.y, warpLocation.z, null, warpLocation.x, warpLocation.y, warpLocation.z);
        }
    }
}

