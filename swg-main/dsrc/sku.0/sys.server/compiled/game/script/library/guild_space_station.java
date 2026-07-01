package script.library;

import script.*;
import script.library.space_transition;
import script.library.space_utils;
import script.library.utils;

import java.util.Vector;

public class guild_space_station extends script.base_script
{
    public guild_space_station()
    {
    }
    public static final String STATION_TEMPLATE = "object/ship/spacestation_neutral.iff";
    public static final String STATION_INTERIOR_TEMPLATE = "object/building/player/guild_space_station_interior.iff";
    public static final String STATION_SCRIPT = "space.structure.guild_space_station";
    public static final String CONTROL_DEVICE_TEMPLATE = "object/intangible/space/guild_space_station_control_device.iff";
    public static final String CONTROL_DEVICE_SCRIPT = "space.structure.guild_space_station_control_device";
    public static final String OBJVAR_GUILD_STATION = "guild_station";
    public static final String OBJVAR_CONTROL_DEVICE_STATION = OBJVAR_GUILD_STATION + ".control_device.station_id";
    public static final String OBJVAR_OWNER_GUILD_ID = OBJVAR_GUILD_STATION + ".owner.guild_id";
    public static final String OBJVAR_OWNER_GUILD_NAME = OBJVAR_GUILD_STATION + ".owner.guild_name";
    public static final String OBJVAR_DEPLOYED_BY = OBJVAR_GUILD_STATION + ".deployed_by";
    public static final String OBJVAR_DEED_ID = OBJVAR_GUILD_STATION + ".deed_id";
    public static final String OBJVAR_SECTOR = OBJVAR_GUILD_STATION + ".sector";
    public static final String OBJVAR_HEALTH = OBJVAR_GUILD_STATION + ".health";
    public static final String OBJVAR_SHIELDS = OBJVAR_GUILD_STATION + ".shields";
    public static final String OBJVAR_FUEL = OBJVAR_GUILD_STATION + ".fuel";
    public static final String OBJVAR_MAINTENANCE = OBJVAR_GUILD_STATION + ".maintenance";
    public static final String OBJVAR_VULN_START = OBJVAR_GUILD_STATION + ".vulnerability.start";
    public static final String OBJVAR_VULN_END = OBJVAR_GUILD_STATION + ".vulnerability.end";
    public static final String OBJVAR_SIEGE_ACTIVE = OBJVAR_GUILD_STATION + ".siege.active";
    public static final String OBJVAR_SIEGE_ATTACKER_GUILD = OBJVAR_GUILD_STATION + ".siege.attacker_guild";
    public static final String OBJVAR_SIEGE_WARMUP_END = OBJVAR_GUILD_STATION + ".siege.warmup_end";
    public static final String OBJVAR_SIEGE_END = OBJVAR_GUILD_STATION + ".siege.end";
    public static final String OBJVAR_CAPTURE_PROGRESS = OBJVAR_GUILD_STATION + ".capture.progress";
    public static final String OBJVAR_CAPTURE_ATTACKER_GUILD = OBJVAR_GUILD_STATION + ".capture.attacker_guild";
    public static final String OBJVAR_CAPTURE_HOLD_END = OBJVAR_GUILD_STATION + ".capture.hold_end";
    public static final String OBJVAR_LAST_ATTACK = OBJVAR_GUILD_STATION + ".last_attack";
    public static final String OBJVAR_COOLDOWN_END = OBJVAR_GUILD_STATION + ".cooldown_end";
    public static final String OBJVAR_ALARM_ACTIVE = OBJVAR_GUILD_STATION + ".alarm.active";
    public static final String OBJVAR_LOCKDOWN = OBJVAR_GUILD_STATION + ".lockdown";
    public static final String OBJVAR_LANDING_POINT = OBJVAR_GUILD_STATION + ".landing.point";
    public static final String OBJVAR_SHIELD_GENERATOR_DISABLED = OBJVAR_GUILD_STATION + ".shield_generator_disabled";
    public static final String OBJVAR_COMMAND_CORE_HACKED = OBJVAR_GUILD_STATION + ".command_core_hacked";
    public static final String OBJVAR_SELF_DESTRUCT = OBJVAR_GUILD_STATION + ".self_destruct";
    public static final String OBJVAR_REPAIR_COST = "fltCostPerDamagePoint";
    public static final String OBJVAR_PVE_RAID_ACTIVE = OBJVAR_GUILD_STATION + ".pve_raid.active";
    public static final String OBJVAR_PVE_RAID_ID = OBJVAR_GUILD_STATION + ".pve_raid.id";
    public static final String OBJVAR_PVE_RAID_END = OBJVAR_GUILD_STATION + ".pve_raid.end";
    public static final String OBJVAR_PVE_RAID_NEXT = OBJVAR_GUILD_STATION + ".pve_raid.next";
    public static final String OBJVAR_PVE_RAID_SCHEDULER = OBJVAR_GUILD_STATION + ".pve_raid.scheduler";
    public static final String OBJVAR_PVE_RAID_SERVICES_DISABLED_UNTIL = OBJVAR_GUILD_STATION + ".pve_raid.services_disabled_until";
    public static final String OBJVAR_PVE_RAID_UPKEEP_BASE = OBJVAR_GUILD_STATION + ".pve_raid.upkeep_base";
    public static final String OBJVAR_PVE_RAID_UPKEEP_DISCOUNT_END = OBJVAR_GUILD_STATION + ".pve_raid.upkeep_discount_end";
    public static final String OBJVAR_PVE_RAID_GUILD_POINTS = OBJVAR_GUILD_STATION + ".pve_raid.guild_points";
    public static final String OBJVAR_PVE_RAID_LAST_SUCCESS = OBJVAR_GUILD_STATION + ".pve_raid.last_success";
    public static final String OBJVAR_STATION_TIER = OBJVAR_GUILD_STATION + ".tier";
    public static final String RAID_DATATABLE = "datatables/space/guild_station_raids.tab";
    public static final int MAX_STATIONS_PER_GUILD = 1;
    public static final float MIN_DISTANCE_FROM_POI = 8000.0f;
    public static final float MIN_DISTANCE_FROM_STATIONS = 5000.0f;
    public static final int DEFAULT_MAX_HEALTH = 250000;
    public static final int DEFAULT_MAX_SHIELDS = 150000;
    public static final int DEFAULT_FUEL = 720; // hours
    public static final int DEFAULT_MAINTENANCE = 5000; // credits per hour
    public static final int SIEGE_WARMUP_SECONDS = 900;
    public static final int SIEGE_DURATION_SECONDS = 7200;
    public static final int CAPTURE_HOLD_SECONDS = 600;
    public static final int SIEGE_COOLDOWN_SECONDS = 14400;
    public static final int RAID_SCHEDULER_INTERVAL_SECONDS = 900;
    public static final int RAID_SPAWN_MIN_RANGE = 600;
    public static final int RAID_SPAWN_MAX_RANGE = 1200;
    public static final float RAID_UPKEEP_DISCOUNT_PERCENT = 0.25f;
    public static final string_id SID_NOT_IN_GUILD = new string_id("guild", "create_fail_in_guild");
    public static final string_id SID_NOT_IN_SPACE = new string_id("space/space_interaction", "no_placing_structures_in_space");
    public static final string_id SID_STATION_DEPLOYED = new string_id("space/space_interaction", "station_deployed");
    public static final string_id SID_STATION_DEPLOY_FAIL = new string_id("space/space_interaction", "station_deploy_fail");
    public static final string_id SID_STATION_SIEGE_DECLARED = new string_id("space/space_interaction", "station_siege_declared");
    public static final string_id SID_STATION_SIEGE_DENIED = new string_id("space/space_interaction", "station_siege_denied");
    public static final string_id SID_STATION_CAPTURED = new string_id("space/space_interaction", "station_captured");
    public static final string_id SID_STATION_DESTROYED = new string_id("space/space_interaction", "station_destroyed");
    public static final string_id SID_STATION_BOARD_DENIED = new string_id("space/space_interaction", "station_board_denied");
    public static final String[] ALLOWED_SPACE_SCENES = new String[]
    {
        "space_tatooine",
        "space_naboo",
        "space_corellia",
        "space_dantooine",
        "space_dathomir",
        "space_lok",
        "space_yavin4",
        "space_endor"
    };
    public static boolean isGuildStation(obj_id station) throws InterruptedException
    {
        return isIdValid(station) && hasObjVar(station, OBJVAR_GUILD_STATION);
    }
    public static boolean isAllowedScene(String sceneName)
    {
        if (sceneName == null)
        {
            return false;
        }
        for (String allowed : ALLOWED_SPACE_SCENES)
        {
            if (sceneName.equals(allowed))
            {
                return true;
            }
        }
        return false;
    }
    public static boolean canPlaceStation(obj_id player, location loc, obj_id deed) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return false;
        }
        if (!isSpaceScene())
        {
            sendSystemMessage(player, SID_NOT_IN_SPACE);
            return false;
        }
        if (!isAllowedScene(getCurrentSceneName()))
        {
            sendSystemMessage(player, new string_id("space/space_interaction", "invalid_deploy_scene"));
            return false;
        }
        int guildId = getGuildId(player);
        if (guildId == 0)
        {
            sendSystemMessage(player, SID_NOT_IN_GUILD);
            return false;
        }
        if (getGuildStationCountInRange(loc, guildId) >= MAX_STATIONS_PER_GUILD)
        {
            sendSystemMessage(player, new string_id("space/space_interaction", "station_guild_limit"));
            return false;
        }
        if (isNearOtherStations(loc))
        {
            sendSystemMessage(player, new string_id("space/space_interaction", "station_too_close"));
            return false;
        }
        if (isNearProtectedPoi(loc))
        {
            sendSystemMessage(player, new string_id("space/space_interaction", "station_safe_zone"));
            return false;
        }
        return true;
    }
    public static boolean isNearOtherStations(location loc) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(loc, MIN_DISTANCE_FROM_STATIONS);
        if (nearby == null)
        {
            return false;
        }
        for (obj_id obj : nearby)
        {
            if (isGuildStation(obj))
            {
                return true;
            }
        }
        return false;
    }
    public static boolean isNearProtectedPoi(location loc) throws InterruptedException
    {
        obj_id[] nearby = getObjectsInRange(loc, MIN_DISTANCE_FROM_POI);
        if (nearby == null)
        {
            return false;
        }
        for (obj_id obj : nearby)
        {
            if (hasObjVar(obj, "space.poi.safe_zone"))
            {
                return true;
            }
        }
        return false;
    }
    public static int getGuildStationCountInRange(location loc, int guildId) throws InterruptedException
    {
        int count = 0;
        obj_id[] nearby = getObjectsInRange(loc, 20000.0f);
        if (nearby == null)
        {
            return count;
        }
        for (obj_id obj : nearby)
        {
            if (isGuildStation(obj) && getIntObjVar(obj, OBJVAR_OWNER_GUILD_ID) == guildId)
            {
                ++count;
            }
        }
        return count;
    }
    public static obj_id spawnStation(obj_id player, obj_id deed, location loc, float yaw) throws InterruptedException
    {
        if (!canPlaceStation(player, loc, deed))
        {
            return obj_id.NULL_ID;
        }
        obj_id station = createObject(STATION_TEMPLATE, loc);
        if (!isIdValid(station))
        {
            sendSystemMessage(player, SID_STATION_DEPLOY_FAIL);
            return obj_id.NULL_ID;
        }
        setYaw(station, yaw);
        attachScript(station, STATION_SCRIPT);
        initializeStation(station, player, deed);
        createControlDevice(player, station);
        if (isIdValid(deed))
        {
            destroyObject(deed);
        }
        sendSystemMessage(player, SID_STATION_DEPLOYED);
        return station;
    }
    public static void initializeStation(obj_id station, obj_id player, obj_id deed) throws InterruptedException
    {
        if (!isIdValid(station))
        {
            return;
        }
        setObjVar(station, OBJVAR_GUILD_STATION, 1);
        int guildId = getGuildId(player);
        setObjVar(station, OBJVAR_OWNER_GUILD_ID, guildId);
        setObjVar(station, OBJVAR_OWNER_GUILD_NAME, guild.guildGetName(guildId));
        setObjVar(station, OBJVAR_DEPLOYED_BY, player);
        if (isIdValid(deed))
        {
            setObjVar(station, OBJVAR_DEED_ID, deed);
        }
        setObjVar(station, OBJVAR_SECTOR, getCurrentSceneName());
        setObjVar(station, OBJVAR_HEALTH, DEFAULT_MAX_HEALTH);
        setObjVar(station, OBJVAR_SHIELDS, DEFAULT_MAX_SHIELDS);
        setObjVar(station, OBJVAR_FUEL, DEFAULT_FUEL);
        setObjVar(station, OBJVAR_MAINTENANCE, DEFAULT_MAINTENANCE);
        setObjVar(station, OBJVAR_REPAIR_COST, 5.0f);
        setObjVar(station, OBJVAR_VULN_START, 0);
        setObjVar(station, OBJVAR_VULN_END, 0);
        removeObjVar(station, OBJVAR_PVE_RAID_ACTIVE);
        removeObjVar(station, OBJVAR_PVE_RAID_ID);
        removeObjVar(station, OBJVAR_PVE_RAID_END);
        removeObjVar(station, OBJVAR_PVE_RAID_NEXT);
        removeObjVar(station, OBJVAR_PVE_RAID_SCHEDULER);
        removeObjVar(station, OBJVAR_PVE_RAID_SERVICES_DISABLED_UNTIL);
        removeObjVar(station, OBJVAR_PVE_RAID_UPKEEP_BASE);
        removeObjVar(station, OBJVAR_PVE_RAID_UPKEEP_DISCOUNT_END);
        removeObjVar(station, OBJVAR_PVE_RAID_GUILD_POINTS);
        player_structure.modifyList(station, getPlayerName(player), null, player_structure.VAR_ADMIN_LIST, false);
        player_structure.modifyList(station, "guild:" + guild.guildGetName(guildId), null, player_structure.VAR_ENTER_LIST, false);
        setOwner(station, player);
    }
    public static boolean declareSiege(obj_id station, obj_id attacker) throws InterruptedException
    {
        if (!isGuildStation(station) || !isIdValid(attacker))
        {
            return false;
        }
        int attackerGuild = getGuildId(attacker);
        int defenderGuild = getIntObjVar(station, OBJVAR_OWNER_GUILD_ID);
        if (!areGuildsAtWar(attackerGuild, defenderGuild))
        {
            sendSystemMessage(attacker, SID_STATION_SIEGE_DENIED);
            return false;
        }
        int now = getGameTime();
        int cooldown = getIntObjVar(station, OBJVAR_COOLDOWN_END);
        if (cooldown > now)
        {
            sendSystemMessage(attacker, SID_STATION_SIEGE_DENIED);
            return false;
        }
        if (getIntObjVar(station, OBJVAR_SIEGE_ACTIVE) == 1)
        {
            sendSystemMessage(attacker, SID_STATION_SIEGE_DENIED);
            return false;
        }
        setObjVar(station, OBJVAR_SIEGE_ACTIVE, 1);
        setObjVar(station, OBJVAR_SIEGE_ATTACKER_GUILD, attackerGuild);
        setObjVar(station, OBJVAR_SIEGE_WARMUP_END, now + SIEGE_WARMUP_SECONDS);
        setObjVar(station, OBJVAR_SIEGE_END, now + SIEGE_WARMUP_SECONDS + SIEGE_DURATION_SECONDS);
        setObjVar(station, OBJVAR_LAST_ATTACK, now);
        sendSystemMessage(attacker, SID_STATION_SIEGE_DECLARED);
        return true;
    }
    public static boolean isStationAttackable(obj_id station, obj_id attacker) throws InterruptedException
    {
        if (!isGuildStation(station) || !isIdValid(attacker))
        {
            return false;
        }
        int attackerGuild = getGuildId(attacker);
        int defenderGuild = getIntObjVar(station, OBJVAR_OWNER_GUILD_ID);
        if (!areGuildsAtWar(attackerGuild, defenderGuild))
        {
            return false;
        }
        int now = getGameTime();
        int vulnStart = getIntObjVar(station, OBJVAR_VULN_START);
        int vulnEnd = getIntObjVar(station, OBJVAR_VULN_END);
        boolean inVuln = vulnStart > 0 && vulnEnd > 0 && now >= vulnStart && now <= vulnEnd;
        boolean siegeActive = getIntObjVar(station, OBJVAR_SIEGE_ACTIVE) == 1 && now >= getIntObjVar(station, OBJVAR_SIEGE_WARMUP_END);
        return inVuln || siegeActive;
    }
    public static void handleDamage(obj_id station, obj_id attacker, int damage) throws InterruptedException
    {
        if (!isStationAttackable(station, attacker))
        {
            return;
        }
        int shields = getIntObjVar(station, OBJVAR_SHIELDS);
        int health = getIntObjVar(station, OBJVAR_HEALTH);
        if (shields > 0)
        {
            shields = Math.max(0, shields - damage);
            setObjVar(station, OBJVAR_SHIELDS, shields);
            if (shields == 0)
            {
                triggerAlarm(station, attacker);
            }
            return;
        }
        health = Math.max(0, health - damage);
        setObjVar(station, OBJVAR_HEALTH, health);
        if (health == 0)
        {
            destroyStation(station, attacker);
        }
    }
    public static void triggerAlarm(obj_id station, obj_id attacker) throws InterruptedException
    {
        if (!isIdValid(station))
        {
            return;
        }
        if (getIntObjVar(station, OBJVAR_ALARM_ACTIVE) == 1)
        {
            return;
        }
        setObjVar(station, OBJVAR_ALARM_ACTIVE, 1);
        playClientEffectObj(attacker, "clienteffect/space_station_alarm.cef", station, "");
    }
    public static boolean attemptBoarding(obj_id station, obj_id player) throws InterruptedException
    {
        obj_id ship = space_transition.getContainingShip(player);
        if (isIdValid(ship) && getPilotId(ship) == player)
        {
            Vector occupants = getShipOccupants(ship);
            if (occupants != null)
            {
                for (Object occupant : occupants)
                {
                    attemptBoardingForPlayer(station, (obj_id) occupant);
                }
                return true;
            }
        }
        return attemptBoardingForPlayer(station, player);
    }

    private static Vector getShipOccupants(obj_id ship) throws InterruptedException
    {
        Vector occupants = null;
        obj_id pilot = getPilotId(ship);
        if (isIdValid(pilot) && isPlayer(pilot))
        {
            occupants = utils.addElement(occupants, pilot);
        }
        Vector passengers = space_utils.getPassengers(ship);
        if (passengers != null)
        {
            for (Object passenger : passengers)
            {
                if (utils.getElementPositionInArray(occupants, passenger) == -1)
                {
                    occupants = utils.addElement(occupants, passenger);
                }
            }
        }
        return occupants;
    }

    private static boolean attemptBoardingForPlayer(obj_id station, obj_id player) throws InterruptedException
    {
        if (getGuildId(player) != getIntObjVar(station, OBJVAR_OWNER_GUILD_ID) && !isStationAttackable(station, player))
        {
            sendSystemMessage(player, SID_STATION_BOARD_DENIED);
            return false;
        }
        String landingPoint = getStringObjVar(station, OBJVAR_LANDING_POINT);
        if (landingPoint != null && landingPoint.length() > 0)
        {
            space_content.landPlayer(player, station, landingPoint);
            return true;
        }
        if (hasObjVar(player, "homingBeacon.planet") || hasObjVar(player, "homingBeacon.houseLoc"))
        {
            space_content.landPlayerHoming(player, station);
            return true;
        }
        space_transition.teleportPlayerToLaunchLoc(player);
        return true;
    }
    public static void startCapture(obj_id station, obj_id attacker) throws InterruptedException
    {
        if (!isStationAttackable(station, attacker))
        {
            return;
        }
        if (getIntObjVar(station, OBJVAR_SHIELD_GENERATOR_DISABLED) == 0 || getIntObjVar(station, OBJVAR_COMMAND_CORE_HACKED) == 0)
        {
            return;
        }
        int attackerGuild = getGuildId(attacker);
        setObjVar(station, OBJVAR_CAPTURE_ATTACKER_GUILD, attackerGuild);
        setObjVar(station, OBJVAR_CAPTURE_PROGRESS, 1);
        setObjVar(station, OBJVAR_CAPTURE_HOLD_END, getGameTime() + CAPTURE_HOLD_SECONDS);
        messageTo(station, "tickCapture", null, 5.0f, false);
    }
    public static void tickCapture(obj_id station) throws InterruptedException
    {
        if (!isGuildStation(station))
        {
            return;
        }
        int holdEnd = getIntObjVar(station, OBJVAR_CAPTURE_HOLD_END);
        if (holdEnd <= getGameTime())
        {
            completeCapture(station);
            return;
        }
        messageTo(station, "tickCapture", null, 5.0f, false);
    }
    public static void completeCapture(obj_id station) throws InterruptedException
    {
        int attackerGuild = getIntObjVar(station, OBJVAR_CAPTURE_ATTACKER_GUILD);
        if (attackerGuild == 0)
        {
            return;
        }
        setObjVar(station, OBJVAR_OWNER_GUILD_ID, attackerGuild);
        setObjVar(station, OBJVAR_OWNER_GUILD_NAME, guild.guildGetName(attackerGuild));
        clearSiegeState(station);
        clearCaptureState(station);
    }
    public static void clearCaptureState(obj_id station) throws InterruptedException
    {
        removeObjVar(station, OBJVAR_CAPTURE_ATTACKER_GUILD);
        removeObjVar(station, OBJVAR_CAPTURE_PROGRESS);
        removeObjVar(station, OBJVAR_CAPTURE_HOLD_END);
        removeObjVar(station, OBJVAR_COMMAND_CORE_HACKED);
        removeObjVar(station, OBJVAR_SHIELD_GENERATOR_DISABLED);
    }
    public static void clearSiegeState(obj_id station) throws InterruptedException
    {
        removeObjVar(station, OBJVAR_SIEGE_ACTIVE);
        removeObjVar(station, OBJVAR_SIEGE_ATTACKER_GUILD);
        removeObjVar(station, OBJVAR_SIEGE_WARMUP_END);
        removeObjVar(station, OBJVAR_SIEGE_END);
        setObjVar(station, OBJVAR_COOLDOWN_END, getGameTime() + SIEGE_COOLDOWN_SECONDS);
    }
    public static void destroyStation(obj_id station, obj_id attacker) throws InterruptedException
    {
        if (!isIdValid(station))
        {
            return;
        }
        playClientEffectObj(attacker, "clienteffect/space_station_explode.cef", station, "");
        destroyObject(station);
    }
    public static boolean areGuildsAtWar(int guildA, int guildB) throws InterruptedException
    {
        if (guildA == 0 || guildB == 0 || guildA == guildB)
        {
            return false;
        }
        int[] enemiesA = guild.guildGetEnemies(guildA);
        int[] enemiesB = guild.getGuildsAtWarWith(guildA);
        if (enemiesA != null)
        {
            for (int enemy : enemiesA)
            {
                if (enemy == guildB)
                {
                    return true;
                }
            }
        }
        if (enemiesB != null)
        {
            for (int enemy : enemiesB)
            {
                if (enemy == guildB)
                {
                    return true;
                }
            }
        }
        return false;
    }
    public static boolean deployFromDatapad(obj_id player, obj_id controlDevice) throws InterruptedException
    {
        if (!isSpaceScene())
        {
            sendSystemMessage(player, SID_NOT_IN_SPACE);
            return false;
        }
        if (!isIdValid(controlDevice))
        {
            sendSystemMessage(player, SID_STATION_DEPLOY_FAIL);
            return false;
        }
        obj_id station = getStationForControlDevice(controlDevice);
        if (isIdValid(station) && exists(station))
        {
            sendSystemMessage(player, SID_STATION_DEPLOY_FAIL);
            return false;
        }
        obj_id ship = space_transition.getContainingShip(player);
        location loc = isIdValid(ship) ? getLocation(ship) : getLocation(player);
        location offset = new location(loc.x, loc.y, loc.z + 500.0f, loc.area, loc.cell);
        float yaw = isIdValid(ship) ? getYaw(ship) : 0.0f;
        if (!canPlaceStation(player, offset, obj_id.NULL_ID))
        {
            return false;
        }
        station = createObject(STATION_TEMPLATE, offset);
        if (!isIdValid(station))
        {
            sendSystemMessage(player, SID_STATION_DEPLOY_FAIL);
            return false;
        }
        setYaw(station, yaw);
        attachScript(station, STATION_SCRIPT);
        initializeStation(station, player, obj_id.NULL_ID);
        if (isIdValid(controlDevice))
        {
            setObjVar(controlDevice, OBJVAR_CONTROL_DEVICE_STATION, station);
        }
        sendSystemMessage(player, SID_STATION_DEPLOYED);
        return true;
    }
    public static obj_id getStationForControlDevice(obj_id controlDevice) throws InterruptedException
    {
        if (!isIdValid(controlDevice))
        {
            return obj_id.NULL_ID;
        }
        if (!hasObjVar(controlDevice, OBJVAR_CONTROL_DEVICE_STATION))
        {
            return obj_id.NULL_ID;
        }
        return getObjIdObjVar(controlDevice, OBJVAR_CONTROL_DEVICE_STATION);
    }
    public static obj_id createControlDevice(obj_id player, obj_id station) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return obj_id.NULL_ID;
        }
        obj_id datapad = utils.getPlayerDatapad(player);
        if (!isIdValid(datapad))
        {
            return obj_id.NULL_ID;
        }
        obj_id controlDevice = createObjectOverloaded(CONTROL_DEVICE_TEMPLATE, datapad);
        if (!isIdValid(controlDevice))
        {
            return obj_id.NULL_ID;
        }
        attachScript(controlDevice, CONTROL_DEVICE_SCRIPT);
        setObjVar(controlDevice, OBJVAR_CONTROL_DEVICE_STATION, station);
        return controlDevice;
    }
    public static boolean recallStation(obj_id player, obj_id controlDevice, obj_id station) throws InterruptedException
    {
        if (!isIdValid(station))
        {
            return false;
        }
        destroyStation(station, player);
        if (isIdValid(controlDevice))
        {
            removeObjVar(controlDevice, OBJVAR_CONTROL_DEVICE_STATION);
        }
        return true;
    }
    public static obj_id findGuildStationDeed(obj_id player) throws InterruptedException
    {
        obj_id inventory = utils.getInventoryContainer(player);
        if (!isIdValid(inventory))
        {
            return obj_id.NULL_ID;
        }
        obj_id[] contents = getContents(inventory);
        if (contents == null)
        {
            return obj_id.NULL_ID;
        }
        for (obj_id item : contents)
        {
            if (getTemplateName(item).equals("object/tangible/deed/guild_deed/guild_space_station_deed.iff"))
            {
                return item;
            }
        }
        return obj_id.NULL_ID;
    }
    public static boolean hackCommandCore(obj_id station, obj_id player) throws InterruptedException
    {
        if (!isStationAttackable(station, player))
        {
            return false;
        }
        setObjVar(station, OBJVAR_COMMAND_CORE_HACKED, 1);
        return true;
    }
    public static boolean disableShieldGenerator(obj_id station, obj_id player) throws InterruptedException
    {
        if (!isStationAttackable(station, player))
        {
            return false;
        }
        setObjVar(station, OBJVAR_SHIELD_GENERATOR_DISABLED, 1);
        return true;
    }

    public static void startRaidScheduler(obj_id station) throws InterruptedException
    {
        if (!isGuildStation(station))
        {
            return;
        }
        if (getIntObjVar(station, OBJVAR_PVE_RAID_SCHEDULER) == 1)
        {
            return;
        }
        setObjVar(station, OBJVAR_PVE_RAID_SCHEDULER, 1);
        messageTo(station, "tickPveRaidScheduler", null, RAID_SCHEDULER_INTERVAL_SECONDS, false);
    }

    public static void tickRaidScheduler(obj_id station) throws InterruptedException
    {
        if (!isGuildStation(station))
        {
            return;
        }
        int now = getGameTime();
        messageTo(station, "tickPveRaidScheduler", null, RAID_SCHEDULER_INTERVAL_SECONDS, false);
        if (hasObjVar(station, OBJVAR_PVE_RAID_UPKEEP_DISCOUNT_END))
        {
            int discountEnd = getIntObjVar(station, OBJVAR_PVE_RAID_UPKEEP_DISCOUNT_END);
            if (discountEnd > 0 && now >= discountEnd)
            {
                int baseUpkeep = getIntObjVar(station, OBJVAR_PVE_RAID_UPKEEP_BASE);
                if (baseUpkeep > 0)
                {
                    setObjVar(station, OBJVAR_MAINTENANCE, baseUpkeep);
                }
                removeObjVar(station, OBJVAR_PVE_RAID_UPKEEP_DISCOUNT_END);
                removeObjVar(station, OBJVAR_PVE_RAID_UPKEEP_BASE);
            }
        }
        if (getIntObjVar(station, OBJVAR_PVE_RAID_ACTIVE) == 1)
        {
            return;
        }
        if (getIntObjVar(station, OBJVAR_SIEGE_ACTIVE) == 1)
        {
            return;
        }
        int nextRaid = getIntObjVar(station, OBJVAR_PVE_RAID_NEXT);
        if (nextRaid > now)
        {
            return;
        }
        int fuel = getIntObjVar(station, OBJVAR_FUEL);
        int health = getIntObjVar(station, OBJVAR_HEALTH);
        int guildPoints = getIntObjVar(station, OBJVAR_PVE_RAID_GUILD_POINTS);
        int stationTier = hasObjVar(station, OBJVAR_STATION_TIER) ? getIntObjVar(station, OBJVAR_STATION_TIER) : 1;
        int onlineMembers = getOnlineGuildMemberCount(station);
        int rowCount = dataTableGetNumRows(RAID_DATATABLE);
        if (rowCount <= 0)
        {
            return;
        }
        Vector<Integer> eligibleRows = new Vector<Integer>();
        for (int i = 0; i < rowCount; ++i)
        {
            dictionary row = dataTableGetRow(RAID_DATATABLE, i);
            int minFuel = row.getInt("minFuel");
            int minHealth = row.getInt("minHealth");
            int minGuildPoints = row.getInt("minGuildPoints");
            int minMembersOnline = row.getInt("minMembersOnline");
            int minStationTier = row.getInt("stationTier");
            if (fuel >= minFuel && health >= minHealth && guildPoints >= minGuildPoints && onlineMembers >= minMembersOnline && stationTier >= minStationTier)
            {
                eligibleRows.add(i);
            }
        }
        if (eligibleRows.isEmpty())
        {
            return;
        }
        int selectedIndex = rand(0, eligibleRows.size() - 1);
        int row = eligibleRows.get(selectedIndex);
        dictionary raidRow = dataTableGetRow(RAID_DATATABLE, row);
        String raidId = raidRow.getString("raidId");
        String displayName = raidRow.getString("displayName");
        String difficultyTier = raidRow.getString("difficultyTier");
        String shipListElite = raidRow.getString("shipListElite");
        int durationSeconds = raidRow.getInt("durationSeconds");
        int cooldownMinutes = raidRow.getInt("cooldownMinutes");
        int waveCount = raidRow.getInt("waveCount");
        int waveDelaySeconds = raidRow.getInt("waveDelaySeconds");
        String shipList = raidRow.getString("shipList");
        int shipsPerWaveMin = raidRow.getInt("shipsPerWaveMin");
        int shipsPerWaveMax = raidRow.getInt("shipsPerWaveMax");
        int rewardGuildPoints = raidRow.getInt("rewardGuildPoints");
        int upkeepDiscountHours = raidRow.getInt("upkeepDiscountHours");
        int failureDisableMinutes = raidRow.getInt("failureDisableMinutes");
        String alertText = raidRow.getString("alertText");
        setObjVar(station, OBJVAR_PVE_RAID_ACTIVE, 1);
        setObjVar(station, OBJVAR_PVE_RAID_ID, raidId);
        setObjVar(station, OBJVAR_PVE_RAID_END, now + durationSeconds);
        setObjVar(station, OBJVAR_PVE_RAID_NEXT, now + (cooldownMinutes * 60));
        dictionary params = new dictionary();
        params.put("raidId", raidId);
        params.put("displayName", displayName);
        params.put("difficultyTier", difficultyTier);
        params.put("durationSeconds", durationSeconds);
        params.put("waveCount", waveCount);
        params.put("waveDelaySeconds", waveDelaySeconds);
        params.put("shipList", shipList);
        params.put("shipListElite", shipListElite);
        params.put("shipsPerWaveMin", shipsPerWaveMin);
        params.put("shipsPerWaveMax", shipsPerWaveMax);
        params.put("rewardGuildPoints", rewardGuildPoints);
        params.put("upkeepDiscountHours", upkeepDiscountHours);
        params.put("failureDisableMinutes", failureDisableMinutes);
        params.put("alertText", alertText);
        messageTo(station, "startPveRaid", params, 1.0f, false);
    }

    public static int getOnlineGuildMemberCount(obj_id station) throws InterruptedException
    {
        if (!isGuildStation(station))
        {
            return 0;
        }
        int guildId = getIntObjVar(station, OBJVAR_OWNER_GUILD_ID);
        obj_id[] members = guildGetMemberIds(guildId);
        if (members == null)
        {
            return 0;
        }
        int onlineCount = 0;
        for (obj_id member : members)
        {
            if (isIdValid(member) && isPlayer(member) && exists(member))
            {
                onlineCount++;
            }
        }
        return onlineCount;
    }

    public static void clearPveRaidState(obj_id station) throws InterruptedException
    {
        removeObjVar(station, OBJVAR_PVE_RAID_ACTIVE);
        removeObjVar(station, OBJVAR_PVE_RAID_ID);
        removeObjVar(station, OBJVAR_PVE_RAID_END);
    }

    public static void applyPveRaidOutcome(obj_id station, boolean success, int rewardGuildPoints, int upkeepDiscountHours, int failureDisableMinutes) throws InterruptedException
    {
        if (!isGuildStation(station))
        {
            return;
        }
        clearPveRaidState(station);
        int now = getGameTime();
        if (success)
        {
            setObjVar(station, OBJVAR_PVE_RAID_LAST_SUCCESS, 1);
            if (rewardGuildPoints > 0)
            {
                int currentPoints = getIntObjVar(station, OBJVAR_PVE_RAID_GUILD_POINTS);
                setObjVar(station, OBJVAR_PVE_RAID_GUILD_POINTS, currentPoints + rewardGuildPoints);
            }
            if (upkeepDiscountHours > 0)
            {
                if (!hasObjVar(station, OBJVAR_PVE_RAID_UPKEEP_BASE))
                {
                    setObjVar(station, OBJVAR_PVE_RAID_UPKEEP_BASE, getIntObjVar(station, OBJVAR_MAINTENANCE));
                }
                int baseUpkeep = getIntObjVar(station, OBJVAR_PVE_RAID_UPKEEP_BASE);
                int discounted = Math.max(0, Math.round(baseUpkeep * (1.0f - RAID_UPKEEP_DISCOUNT_PERCENT)));
                setObjVar(station, OBJVAR_MAINTENANCE, discounted);
                setObjVar(station, OBJVAR_PVE_RAID_UPKEEP_DISCOUNT_END, now + (upkeepDiscountHours * 3600));
            }
        }
        else
        {
            setObjVar(station, OBJVAR_PVE_RAID_LAST_SUCCESS, 0);
            if (failureDisableMinutes > 0)
            {
                setObjVar(station, OBJVAR_PVE_RAID_SERVICES_DISABLED_UNTIL, now + (failureDisableMinutes * 60));
            }
        }
    }
}
