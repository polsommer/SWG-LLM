package script.space.structure;

import script.*;
import script.library.player_structure;
import script.library.space_crafting;
import script.library.space_create;
import script.library.space_utils;
import script.library.ship_ai;
import script.library.sui;
import script.library.utils;
import script.library.prose;
import java.util.Vector;
import script.library.space_transition;

public class guild_space_station extends script.base_script
{
    public static final String GREET_VOLUME = "guild_station_greet";
    public static final float GREET_RADIUS = 800.0f;
    public static final float GREET_COOLDOWN = 30.0f;
    public static final String SCRIPTVAR_GREET_TIME = "guild_station.greet.time";
    public static final String SCRIPTVAR_GREET_STATION = "guild_station.greet.station";
    public static final string_id SID_MENU_STATUS = new string_id("space/space_interaction", "station_status");
    public static final string_id SID_MENU_ACCESS = new string_id("space/space_interaction", "station_access");
    public static final string_id SID_MENU_VULN = new string_id("space/space_interaction", "station_vuln_window");
    public static final string_id SID_MENU_DECLARE_SIEGE = new string_id("space/space_interaction", "station_declare_siege");
    public static final string_id SID_MENU_BOARD = new string_id("space/space_interaction", "station_board");
    public static final string_id SID_MENU_HACK = new string_id("space/space_interaction", "station_hack_core");
    public static final string_id SID_MENU_DISABLE_SHIELDS = new string_id("space/space_interaction", "station_disable_shields");
    public static final string_id SID_MENU_CAPTURE = new string_id("space/space_interaction", "station_capture");
    public guild_space_station()
    {
    }
    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, script.library.guild_space_station.OBJVAR_GUILD_STATION))
        {
            setObjVar(self, script.library.guild_space_station.OBJVAR_GUILD_STATION, 1);
        }
        if (!hasTriggerVolume(self, GREET_VOLUME))
        {
            createTriggerVolume(GREET_VOLUME, GREET_RADIUS, true);
        }
        script.library.guild_space_station.startRaidScheduler(self);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, script.library.guild_space_station.OBJVAR_GUILD_STATION))
        {
            setObjVar(self, script.library.guild_space_station.OBJVAR_GUILD_STATION, 1);
        }
        if (!hasTriggerVolume(self, GREET_VOLUME))
        {
            createTriggerVolume(GREET_VOLUME, GREET_RADIUS, true);
        }
        script.library.guild_space_station.startRaidScheduler(self);
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        mi.addRootMenu(menu_info_types.SERVER_MENU1, SID_MENU_STATUS);
        mi.addRootMenu(menu_info_types.SERVER_MENU2, SID_MENU_ACCESS);
        mi.addRootMenu(menu_info_types.SERVER_MENU3, SID_MENU_VULN);
        mi.addRootMenu(menu_info_types.SERVER_MENU4, SID_MENU_BOARD);
        mi.addRootMenu(menu_info_types.SERVER_MENU5, SID_MENU_DECLARE_SIEGE);
        mi.addRootMenu(menu_info_types.SERVER_MENU6, SID_MENU_DISABLE_SHIELDS);
        mi.addRootMenu(menu_info_types.SERVER_MENU7, SID_MENU_HACK);
        mi.addRootMenu(menu_info_types.SERVER_MENU8, SID_MENU_CAPTURE);
        return SCRIPT_CONTINUE;
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
                ship = script.library.space_transition.getContainingShip(who);
            }
        }
        if (!space_utils.isPlayerControlledShip(ship))
        {
            return SCRIPT_CONTINUE;
        }
        Vector occupants = null;
        obj_id pilot = getPilotId(ship);
        if (isIdValid(pilot) && isPlayer(pilot))
        {
            occupants = utils.addElement(occupants, pilot);
        }
        Vector passengers = space_utils.getPassengers(ship);
        if (passengers != null)
        {
            for (Object passenger : passengers) {
                if (utils.getElementPositionInArray(occupants, passenger) == -1) {
                    occupants = utils.addElement(occupants, passenger);
                }
            }
        }
        if (isPlayer(who) && utils.getElementPositionInArray(occupants, who) == -1)
        {
            occupants = utils.addElement(occupants, who);
        }
        if (occupants != null)
        {
            for (Object occupant : occupants) {
                sendStationGreeting(self, (obj_id) occupant);
            }
        }
        return SCRIPT_CONTINUE;
    }
    private void sendStationGreeting(obj_id station, obj_id recipient) throws InterruptedException
    {
        if (!isIdValid(recipient) || !isPlayer(recipient))
        {
            return;
        }
        if (utils.hasScriptVar(recipient, SCRIPTVAR_GREET_TIME))
        {
            float lastTime = utils.getFloatScriptVar(recipient, SCRIPTVAR_GREET_TIME);
            if (getGameTime() - lastTime < GREET_COOLDOWN)
            {
                return;
            }
        }
        utils.setScriptVar(recipient, SCRIPTVAR_GREET_TIME, (float)getGameTime());
        utils.setScriptVar(recipient, SCRIPTVAR_GREET_STATION, station);
        String[] entries = new String[]
        {
            "Repair Ship",
            "Land/Board Station",
            "Move Station",
            "Destroy Station"
        };
        sui.listbox(station, recipient, "Guild Station Services", sui.OK_CANCEL, "Station Control", entries, "handleStationGreeting", false, false);
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
        switch (row)
        {
            case 0:
                if (areServicesDisabled(station, player))
                {
                    return SCRIPT_CONTINUE;
                }
                space_crafting.doStationToShipRepairs(player, station, 1.0f);
                break;
            case 1:
                if (!canAttemptBoarding(station, player))
                {
                    return SCRIPT_CONTINUE;
                }
                script.library.guild_space_station.attemptBoarding(station, player);
                break;
            case 2:
                handleMoveStation(station, player);
                break;
            case 3:
                confirmDestroyStation(station, player);
                break;
            default:
                break;
        }
        return SCRIPT_CONTINUE;
    }
    private void handleMoveStation(obj_id station, obj_id player) throws InterruptedException
    {
        if (!player_structure.isAdmin(station, player))
        {
            sendSystemMessage(player, new string_id("player_structure", "must_be_admin"));
            return;
        }
        if (getIntObjVar(station, script.library.guild_space_station.OBJVAR_SIEGE_ACTIVE) == 1)
        {
            sendSystemMessage(player, new string_id("space/space_interaction", "station_siege_denied"));
            return;
        }
        obj_id ship = script.library.space_transition.getContainingShip(player);
        if (!isIdValid(ship))
        {
            return;
        }
        location loc = getLocation(ship);
        location offset = new location(loc.x, loc.y, loc.z + 800.0f, loc.area, loc.cell);
        setLocation(station, offset);
        setObjVar(station, script.library.guild_space_station.OBJVAR_SECTOR, loc.area);
    }
    private void confirmDestroyStation(obj_id station, obj_id player) throws InterruptedException
    {
        if (!player_structure.isAdmin(station, player))
        {
            sendSystemMessage(player, new string_id("player_structure", "must_be_admin"));
            return;
        }
        utils.setScriptVar(player, "guild_station.destroy_station", station);
        int pid = sui.msgbox(station, player, "Destroying this station is permanent. Are you sure?", sui.OK_CANCEL, "Confirm Station Destruction", "handleDestroyStationConfirm");
        sui.setSUIProperty(pid, sui.MSGBOX_BTN_OK, sui.PROP_TEXT, "Destroy");
        sui.setSUIProperty(pid, sui.MSGBOX_BTN_CANCEL, sui.PROP_TEXT, "Cancel");
    }
    public int handleDestroyStationConfirm(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id player = sui.getPlayerId(params);
        if (!isIdValid(player))
        {
            return SCRIPT_CONTINUE;
        }
        int bp = sui.getIntButtonPressed(params);
        if (bp == sui.BP_CANCEL)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id station = utils.hasScriptVar(player, "guild_station.destroy_station") ? utils.getObjIdScriptVar(player, "guild_station.destroy_station") : obj_id.NULL_ID;
        if (!isIdValid(station))
        {
            return SCRIPT_CONTINUE;
        }
        if (!player_structure.isAdmin(station, player))
        {
            sendSystemMessage(player, new string_id("player_structure", "must_be_admin"));
            return SCRIPT_CONTINUE;
        }
        script.library.guild_space_station.destroyStation(station, player);
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item == menu_info_types.SERVER_MENU1)
        {
            showStatus(self, player);
        }
        if (item == menu_info_types.SERVER_MENU2)
        {
            showAccess(self, player);
        }
        if (item == menu_info_types.SERVER_MENU3)
        {
            showVulnerability(self, player);
        }
        if (item == menu_info_types.SERVER_MENU4)
        {
            if (!canAttemptBoarding(self, player))
            {
                return SCRIPT_CONTINUE;
            }
            script.library.guild_space_station.attemptBoarding(self, player);
        }
        if (item == menu_info_types.SERVER_MENU5)
        {
            script.library.guild_space_station.declareSiege(self, player);
        }
        if (item == menu_info_types.SERVER_MENU6)
        {
            script.library.guild_space_station.disableShieldGenerator(self, player);
        }
        if (item == menu_info_types.SERVER_MENU7)
        {
            script.library.guild_space_station.hackCommandCore(self, player);
        }
        if (item == menu_info_types.SERVER_MENU8)
        {
            script.library.guild_space_station.startCapture(self, player);
        }
        return SCRIPT_CONTINUE;
    }
    private boolean canAttemptBoarding(obj_id station, obj_id player) throws InterruptedException
    {
        if (areServicesDisabled(station, player))
        {
            return false;
        }
        if (getGuildId(player) != getIntObjVar(station, script.library.guild_space_station.OBJVAR_OWNER_GUILD_ID) && !script.library.guild_space_station.isStationAttackable(station, player))
        {
            sendSystemMessage(player, script.library.guild_space_station.SID_STATION_BOARD_DENIED);
            return false;
        }
        return true;
    }
    public int OnObjectDamaged(obj_id self, obj_id attacker, obj_id weapon, int damage) throws InterruptedException
    {
        script.library.guild_space_station.handleDamage(self, attacker, damage);
        return SCRIPT_CONTINUE;
    }
    public int tickCapture(obj_id self, dictionary params) throws InterruptedException
    {
        script.library.guild_space_station.tickCapture(self);
        return SCRIPT_CONTINUE;
    }
    private void showStatus(obj_id station, obj_id player) throws InterruptedException
    {
        int health = getIntObjVar(station, script.library.guild_space_station.OBJVAR_HEALTH);
        int shields = getIntObjVar(station, script.library.guild_space_station.OBJVAR_SHIELDS);
        int fuel = getIntObjVar(station, script.library.guild_space_station.OBJVAR_FUEL);
        int maint = getIntObjVar(station, script.library.guild_space_station.OBJVAR_MAINTENANCE);
        String owner = getStringObjVar(station, script.library.guild_space_station.OBJVAR_OWNER_GUILD_NAME);
        sendSystemMessage(player, "Guild Station Status: Guild=" + owner + " Hull=" + health + " Shields=" + shields + " Fuel=" + fuel + "h Upkeep=" + maint + "/h", null);
    }
    private void showAccess(obj_id station, obj_id player) throws InterruptedException
    {
        if (!player_structure.isAdmin(station, player))
        {
            sendSystemMessage(player, new string_id("player_structure", "must_be_admin"));
            return;
        }
        String[] accessList = player_structure.getEntryList(station);
        String list = formatList(accessList);
        sendSystemMessage(player, "Guild Station Access: " + list, null);
    }
    private void showVulnerability(obj_id station, obj_id player) throws InterruptedException
    {
        int vulnStart = getIntObjVar(station, script.library.guild_space_station.OBJVAR_VULN_START);
        int vulnEnd = getIntObjVar(station, script.library.guild_space_station.OBJVAR_VULN_END);
        sendSystemMessage(player, "Vulnerability Window: " + vulnStart + " - " + vulnEnd, null);
    }
    private String formatList(String[] list)
    {
        if (list == null || list.length == 0)
        {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.length; ++i)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append(list[i]);
        }
        return sb.toString();
    }

    public int tickPveRaidScheduler(obj_id self, dictionary params) throws InterruptedException
    {
        script.library.guild_space_station.tickRaidScheduler(self);
        return SCRIPT_CONTINUE;
    }

    public int startPveRaid(obj_id self, dictionary params) throws InterruptedException
    {
        if (getIntObjVar(self, script.library.guild_space_station.OBJVAR_PVE_RAID_ACTIVE) != 1)
        {
            return SCRIPT_CONTINUE;
        }
        String raidId = params.getString("raidId");
        String displayName = params.getString("displayName");
        String difficultyTier = params.getString("difficultyTier");
        int durationSeconds = params.getInt("durationSeconds");
        int waveCount = params.getInt("waveCount");
        int waveDelaySeconds = params.getInt("waveDelaySeconds");
        String shipList = params.getString("shipList");
        String shipListElite = params.getString("shipListElite");
        int shipsPerWaveMin = params.getInt("shipsPerWaveMin");
        int shipsPerWaveMax = params.getInt("shipsPerWaveMax");
        int rewardGuildPoints = params.getInt("rewardGuildPoints");
        int upkeepDiscountHours = params.getInt("upkeepDiscountHours");
        int failureDisableMinutes = params.getInt("failureDisableMinutes");
        String alertText = params.getString("alertText");
        utils.setScriptVar(self, "guild_station.raid_id", raidId);
        utils.setScriptVar(self, "guild_station.raid_name", displayName);
        utils.setScriptVar(self, "guild_station.raid_difficulty", difficultyTier);
        utils.setScriptVar(self, "guild_station.raid_wave_count", waveCount);
        utils.setScriptVar(self, "guild_station.raid_wave_delay", waveDelaySeconds);
        utils.setScriptVar(self, "guild_station.raid_ship_list", shipList);
        utils.setScriptVar(self, "guild_station.raid_ship_list_elite", shipListElite);
        utils.setScriptVar(self, "guild_station.raid_ships_min", shipsPerWaveMin);
        utils.setScriptVar(self, "guild_station.raid_ships_max", shipsPerWaveMax);
        utils.setScriptVar(self, "guild_station.raid_reward_points", rewardGuildPoints);
        utils.setScriptVar(self, "guild_station.raid_upkeep_hours", upkeepDiscountHours);
        utils.setScriptVar(self, "guild_station.raid_failure_minutes", failureDisableMinutes);
        utils.setScriptVar(self, "guild_station.raid_end_time", getGameTime() + durationSeconds);
        utils.setScriptVar(self, "guild_station.raid_member_count", getOnlineGuildMemberCount(self));
        broadcastRaidAlert(self, alertText, displayName, difficultyTier);
        utils.setScriptVar(self, "guild_station.raid_current_wave", 0);
        utils.setScriptVar(self, "guild_station.raid_ships", new Vector<obj_id>());
        messageTo(self, "advancePveRaidWave", null, 1.0f, false);
        messageTo(self, "checkPveRaidStatus", null, 10.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int advancePveRaidWave(obj_id self, dictionary params) throws InterruptedException
    {
        if (getIntObjVar(self, script.library.guild_space_station.OBJVAR_PVE_RAID_ACTIVE) != 1)
        {
            return SCRIPT_CONTINUE;
        }
        int waveCount = utils.getIntScriptVar(self, "guild_station.raid_wave_count");
        int currentWave = utils.getIntScriptVar(self, "guild_station.raid_current_wave");
        if (currentWave >= waveCount)
        {
            return SCRIPT_CONTINUE;
        }
        spawnRaidWave(self);
        utils.setScriptVar(self, "guild_station.raid_current_wave", currentWave + 1);
        int waveDelay = utils.getIntScriptVar(self, "guild_station.raid_wave_delay");
        if (currentWave + 1 < waveCount)
        {
            messageTo(self, "advancePveRaidWave", null, waveDelay, false);
        }
        return SCRIPT_CONTINUE;
    }

    public int checkPveRaidStatus(obj_id self, dictionary params) throws InterruptedException
    {
        if (getIntObjVar(self, script.library.guild_space_station.OBJVAR_PVE_RAID_ACTIVE) != 1)
        {
            return SCRIPT_CONTINUE;
        }
        int endTime = utils.getIntScriptVar(self, "guild_station.raid_end_time");
        if (getGameTime() >= endTime)
        {
            finishPveRaid(self, false);
            return SCRIPT_CONTINUE;
        }
        boolean anyAlive = false;
        Vector<obj_id> ships = utils.getVectorScriptVar(self, "guild_station.raid_ships");
        if (ships != null)
        {
            Vector<obj_id> cleaned = new Vector<obj_id>();
            for (obj_id ship : ships)
            {
                if (isIdValid(ship) && exists(ship))
                {
                    anyAlive = true;
                    cleaned.add(ship);
                }
            }
            utils.setScriptVar(self, "guild_station.raid_ships", cleaned);
        }
        int waveCount = utils.getIntScriptVar(self, "guild_station.raid_wave_count");
        int currentWave = utils.getIntScriptVar(self, "guild_station.raid_current_wave");
        if (currentWave >= waveCount && !anyAlive)
        {
            finishPveRaid(self, true);
            return SCRIPT_CONTINUE;
        }
        messageTo(self, "checkPveRaidStatus", null, 10.0f, false);
        return SCRIPT_CONTINUE;
    }

    private void spawnRaidWave(obj_id station) throws InterruptedException
    {
        int waveCount = utils.getIntScriptVar(station, "guild_station.raid_wave_count");
        int currentWave = utils.getIntScriptVar(station, "guild_station.raid_current_wave");
        String shipList = utils.getStringScriptVar(station, "guild_station.raid_ship_list");
        String shipListElite = utils.getStringScriptVar(station, "guild_station.raid_ship_list_elite");
        boolean isFinalWave = (currentWave + 1) >= waveCount;
        String[] shipTypes = parseShipList(isFinalWave && shipListElite != null && shipListElite.length() > 0 ? shipListElite : shipList);
        if (shipTypes == null || shipTypes.length == 0)
        {
            return;
        }
        int shipsMin = utils.getIntScriptVar(station, "guild_station.raid_ships_min");
        int shipsMax = utils.getIntScriptVar(station, "guild_station.raid_ships_max");
        int memberCount = utils.getIntScriptVar(station, "guild_station.raid_member_count");
        int memberBonus = Math.min(3, memberCount / 5);
        int successBonus = getIntObjVar(station, script.library.guild_space_station.OBJVAR_PVE_RAID_LAST_SUCCESS) == 1 ? 1 : 0;
        int adjustedMin = Math.max(1, shipsMin + memberBonus + successBonus);
        int adjustedMax = Math.max(adjustedMin, shipsMax + memberBonus + successBonus);
        int count = rand(adjustedMin, adjustedMax);
        Vector<obj_id> ships = utils.getVectorScriptVar(station, "guild_station.raid_ships");
        if (ships == null)
        {
            ships = new Vector<obj_id>();
        }
        for (int i = 0; i < count; ++i)
        {
            String shipType = shipTypes[rand(0, shipTypes.length - 1)];
            transform spawnTransform = space_utils.getRandomPositionInSphere(getTransform_o2p(station), script.library.guild_space_station.RAID_SPAWN_MIN_RANGE, script.library.guild_space_station.RAID_SPAWN_MAX_RANGE, false);
            obj_id raidShip = space_create.createShipHyperspace(shipType, spawnTransform);
            if (isIdValid(raidShip))
            {
                setObjVar(raidShip, "guild_station.raid_target", station);
                ship_ai.unitSetLeashDistance(raidShip, 16000);
                ship_ai.unitSetAttackOrders(raidShip, ship_ai.ATTACK_ORDERS_ATTACK_FREELY);
                ships.add(raidShip);
            }
        }
        utils.setScriptVar(station, "guild_station.raid_ships", ships);
    }

    private String[] parseShipList(String shipList)
    {
        if (shipList == null || shipList.length() == 0)
        {
            return new String[0];
        }
        Vector<String> tokens = new Vector<String>();
        java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(shipList, ",");
        while (tokenizer.hasMoreTokens())
        {
            String token = tokenizer.nextToken().trim();
            if (token.length() > 0)
            {
                tokens.add(token);
            }
        }
        String[] output = new String[tokens.size()];
        for (int i = 0; i < tokens.size(); ++i)
        {
            output[i] = tokens.get(i);
        }
        return output;
    }

    private void finishPveRaid(obj_id station, boolean success) throws InterruptedException
    {
        int rewardPoints = utils.getIntScriptVar(station, "guild_station.raid_reward_points");
        int upkeepHours = utils.getIntScriptVar(station, "guild_station.raid_upkeep_hours");
        int failureMinutes = utils.getIntScriptVar(station, "guild_station.raid_failure_minutes");
        String displayName = utils.getStringScriptVar(station, "guild_station.raid_name");
        script.library.guild_space_station.applyPveRaidOutcome(station, success, rewardPoints, upkeepHours, failureMinutes);
        if (success)
        {
            broadcastRaidAlert(station, "Guild station defense successful: " + displayName + ".", displayName, utils.getStringScriptVar(station, "guild_station.raid_difficulty"));
        }
        else
        {
            broadcastRaidAlert(station, "Guild station defenses failed: " + displayName + ". Services are temporarily disabled.", displayName, utils.getStringScriptVar(station, "guild_station.raid_difficulty"));
        }
        utils.removeScriptVar(station, "guild_station.raid_id");
        utils.removeScriptVar(station, "guild_station.raid_name");
        utils.removeScriptVar(station, "guild_station.raid_difficulty");
        utils.removeScriptVar(station, "guild_station.raid_wave_count");
        utils.removeScriptVar(station, "guild_station.raid_wave_delay");
        utils.removeScriptVar(station, "guild_station.raid_ship_list");
        utils.removeScriptVar(station, "guild_station.raid_ship_list_elite");
        utils.removeScriptVar(station, "guild_station.raid_ships_min");
        utils.removeScriptVar(station, "guild_station.raid_ships_max");
        utils.removeScriptVar(station, "guild_station.raid_reward_points");
        utils.removeScriptVar(station, "guild_station.raid_upkeep_hours");
        utils.removeScriptVar(station, "guild_station.raid_failure_minutes");
        utils.removeScriptVar(station, "guild_station.raid_end_time");
        utils.removeScriptVar(station, "guild_station.raid_current_wave");
        utils.removeScriptVar(station, "guild_station.raid_member_count");
        utils.removeScriptVar(station, "guild_station.raid_ships");
    }

    private void broadcastRaidAlert(obj_id station, String alertText, String displayName, String difficultyTier) throws InterruptedException
    {
        if (alertText == null || alertText.length() == 0)
        {
            alertText = "Guild station alert: " + displayName + ".";
        }
        if (difficultyTier != null && difficultyTier.length() > 0)
        {
            alertText = alertText + " Difficulty: " + difficultyTier + ".";
        }
        int guildId = getIntObjVar(station, script.library.guild_space_station.OBJVAR_OWNER_GUILD_ID);
        obj_id[] members = guildGetMemberIds(guildId);
        if (members == null)
        {
            return;
        }
        for (obj_id member : members)
        {
            if (isIdValid(member) && isPlayer(member))
            {
                sendSystemMessage(member, alertText, null);
            }
        }
    }

    private int getOnlineGuildMemberCount(obj_id station) throws InterruptedException
    {
        return script.library.guild_space_station.getOnlineGuildMemberCount(station);
    }

    private boolean areServicesDisabled(obj_id station, obj_id player) throws InterruptedException
    {
        int disabledUntil = getIntObjVar(station, script.library.guild_space_station.OBJVAR_PVE_RAID_SERVICES_DISABLED_UNTIL);
        if (disabledUntil > getGameTime())
        {
            if (isIdValid(player))
            {
                sendSystemMessage(player, "Station services are temporarily disabled due to recent raid damage.", null);
            }
            return true;
        }
        return false;
    }
}
