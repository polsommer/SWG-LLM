package script.dev;
import script.library.money;   // For transferBankCreditsTo, constants like DICT_TARGET_ID, RET_SUCCESS
import script.library.utils;   // For stringToInt and possibly isIdValid
import script.obj_id;
import script.location;
import script.discord.ModerationWebhook;
import script.system_process;
import script.systems.moderation.moderation_lib;

public class omni extends script.base_script {

    private static final int CREDIT_ALERT_THRESHOLD = 10000000;

    public omni() {}

    public int OnAttach(obj_id self) throws InterruptedException {
        sendSystemMessageTestingOnly(self, "OMNI Admin Tool Loaded. Say INFO for commands.");
        return SCRIPT_CONTINUE;
    }

    public int OnSpeaking(obj_id self, String text) throws InterruptedException {
        if (!isGod(self)) {
            return SCRIPT_CONTINUE;
        }

        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() == 0) {
            return SCRIPT_CONTINUE;
        }

        String cmd;
        String args;
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx == -1) {
            cmd = trimmed.toLowerCase();
            args = "";
        } else {
            cmd = trimmed.substring(0, spaceIdx).toLowerCase();
            args = trimmed.substring(spaceIdx + 1).trim();
        }

        switch (cmd) {
            case "syscmd":
                runSystemCommand(self, args);
                break;
            case "movedungeon":
                moveDungeon(self, args);
                break;
            case "getplayerid":
                getPlayerIdByName(self, firstArg(args));
                break;
            case "fsintro":
                startFsIntro(self, firstArg(args));
                break;
            case "wipeitems":
                wipePlayerItems(self, firstArg(args));
                break;
            case "getresourcecrate": {
                String[] parts = splitArgs(args, 2);
                if (parts.length < 2) {
                    sendSystemMessageTestingOnly(self, "Usage: getresourcecrate <resourceName> <amount>");
                } else {
                    giveResourceCrate(self, parts[0], parts[1]);
                }
                break;
            }
            case "flagcheater":
                flagCheater(self, firstArg(args));
                break;
            case "unflagcheater":
                unflagCheater(self, firstArg(args));
                break;
            case "info":
                sendSystemMessageTestingOnly(self, "Commands: syscmd, movedungeon, getplayerid, fsintro, wipeitems, getresourcecrate, flagcheater, unflagcheater, teleto, bring, kick, warn, mute, unmute, note, history, invis, vis, track, listplayers, scanscene, unstuck, event, givecredits, checkcredits, mapinfo, startnpe, mindmod, showmind, setattrib, modattrib");
                break;
            case "teleto":
                teleportToPlayer(self, firstArg(args));
                break;
            case "bring":
                bringPlayer(self, firstArg(args));
                break;
            case "kick":
                kickPlayer(self, args);
                break;
            case "warn":
                warnPlayer(self, args);
                break;
            case "mute":
                mutePlayer(self, args);
                break;
            case "unmute":
                unmutePlayer(self, args);
                break;
            case "note":
                addStaffNote(self, args);
                break;
            case "history":
                showModerationHistory(self, args);
                break;
            case "invis":
                setInvis(self, true);
                sendSystemMessageTestingOnly(self, "Invisibility enabled.");
                ModerationWebhook.sendPlainMessage("🕵️ Admin " + getFirstName(self) + " enabled invisibility.");
                break;
            case "vis":
                setInvis(self, false);
                sendSystemMessageTestingOnly(self, "Invisibility disabled.");
                ModerationWebhook.sendPlainMessage("👁️ Admin " + getFirstName(self) + " became visible.");
                break;
            case "track":
                trackPlayer(self, firstArg(args));
                break;
            case "listplayers":
                listPlayers(self);
                break;
            case "scanscene":
                scanSceneForCheats(self);
                break;
            case "unstuck":
                setLocation(self, getGoodLocation(getLocation(self)));
                sendSystemMessageTestingOnly(self, "You have been unstuck.");
                ModerationWebhook.sendPlainMessage("📬 " + getFirstName(self) + " used /unstuck at " + getLocation(self));
                break;
            case "event":
                sendSystemMessageTestingOnly(self, "Event started: " + args);
                ModerationWebhook.sendPlainMessage("🎉 Event started by " + getFirstName(self) + ": " + args);
                break;
            case "givecredits":
                giveCredits(self, args);
                break;
            case "checkcredits":
                checkCredits(self, firstArg(args));
                break;
            case "mapinfo":
                mapLookup(self, args);
                break;
            case "startnpe":
                startNpeFlow(self, firstArg(args));
                break;
            case "mindmod": {
                String[] parts = splitArgs(args, 4);
                if (parts.length < 4) {
                    sendSystemMessageTestingOnly(self, "Usage: mindmod <player> <type> <value> <duration>");
                } else {
                    applyMindMod(self, parts[0], parts[1], parts[2], parts[3]);
                }
                break;
            }
            case "showmind":
                showMindState(self, firstArg(args));
                break;
            case "setattrib": {
                String[] parts = splitArgs(args, 3);
                if (parts.length < 3) {
                    sendSystemMessageTestingOnly(self, "Usage: setattrib <player> <attrId> <value>");
                } else {
                    setAttribValue(self, parts[0], parts[1], parts[2]);
                }
                break;
            }
            case "modattrib": {
                String[] parts = splitArgs(args, 4);
                if (parts.length < 4) {
                    sendSystemMessageTestingOnly(self, "Usage: modattrib <player> <attrId> <value> <duration>");
                } else {
                    modAttribTemp(self, parts[0], parts[1], parts[2], parts[3]);
                }
                break;
            }
            default:
                sendSystemMessageTestingOnly(self, "Unknown command: " + cmd);
        }

        return SCRIPT_CONTINUE;
    }

    private void teleportToPlayer(obj_id self, String name) throws InterruptedException {
        obj_id target = findPlayerByName(self, name);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        setLocation(self, getLocation(target));
        sendSystemMessageTestingOnly(self, "Teleported to " + getFirstName(target));
        ModerationWebhook.sendPlainMessage("🚀 Admin " + getFirstName(self) + " teleported to " + getFirstName(target));
    }

    private void bringPlayer(obj_id self, String name) throws InterruptedException {
        obj_id target = findPlayerByName(self, name);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        setLocation(target, getLocation(self));
        sendSystemMessageTestingOnly(self, getFirstName(target) + " brought to you.");
        ModerationWebhook.sendPlainMessage("📦 Admin " + getFirstName(self) + " brought player " + getFirstName(target));
    }

    private void kickPlayer(obj_id self, String args) throws InterruptedException {
        String[] parts = splitArgs(args, 2);
        if (parts.length == 0) {
            sendSystemMessageTestingOnly(self, "Usage: kick <player> [reason]");
            return;
        }
        String targetName = parts[0];
        String reason = parts.length > 1 ? parts[1] : "Kicked by staff.";
        obj_id target = findPlayerByName(self, targetName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        sendSystemMessage(target, "You have been kicked by " + getFirstName(self) + ". Reason: " + reason, "");
        disconnectPlayer(target);
        sendSystemMessageTestingOnly(self, getFirstName(target) + " kicked.");
        CustomerServiceLog("Moderation", getFirstName(self) + " kicked " + getName(target) + ": " + reason, target);
        ModerationWebhook.sendModerationAction("Kick", self, getFirstName(self), target, reason, 0);
    }

    private void trackPlayer(obj_id self, String name) throws InterruptedException {
        obj_id target = findPlayerByName(self, name);
        if (isIdValid(target)) {
            location loc = getLocation(target);
            sendSystemMessageTestingOnly(self, getFirstName(target) + " is at: " + loc);
        } else {
            sendSystemMessageTestingOnly(self, "Player not found.");
        }
    }

    private void listPlayers(obj_id self) throws InterruptedException {
        obj_id[] players = getPlayerCreaturesInRange(self, 500000);
        if (players == null || players.length == 0) {
            sendSystemMessageTestingOnly(self, "No players found.");
            return;
        }
        sendSystemMessageTestingOnly(self, "Players nearby:");
        for (obj_id player : players) {
            sendSystemMessageTestingOnly(self, "- " + getFirstName(player));
        }
    }

    private void scanSceneForCheats(obj_id self) throws InterruptedException {
        obj_id[] players = getPlayerCreaturesInRange(self, 1000000);
        for (obj_id p : players) {
            location loc = getLocation(p);
            if (loc.y < -500) {
                sendSystemMessageTestingOnly(self, "[ALERT] " + getFirstName(p) + " is under the map.");
                ModerationWebhook.sendModerationAction("Cheat alert", self, getFirstName(self), p, "Detected below world at " + loc, 0);
            }
        }
    }

    private void checkCredits(obj_id self, String targetName) throws InterruptedException {
        obj_id target = findPlayerByName(self, targetName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        int cash = getCashBalance(target);
        int bank = getBankBalance(target);
        int total = cash + bank;
        sendSystemMessageTestingOnly(self, targetName + " - Cash: " + cash + ", Bank: " + bank + ", Total: " + total);
        if (total >= CREDIT_ALERT_THRESHOLD) {
            ModerationWebhook.sendModerationAction("Credit audit", self, getFirstName(self), target, "Balance check: " + total + " credits.", 0);
        }
    }

    private void mapLookup(obj_id self, String template) throws InterruptedException {
        sendSystemMessageTestingOnly(self, "[MapInfo] Template search: " + template + " (hookup to IFF lookup to enhance)");
    }

    private void startNpeFlow(obj_id self, String playerName) throws InterruptedException {
        obj_id target = findPlayerByName(self, playerName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        attachScript(target, "npe.npe_instance_travel_player");
        sendSystemMessageTestingOnly(self, "Started NPE script for: " + getFirstName(target));
        ModerationWebhook.sendPlainMessage("🚀 Admin " + getFirstName(self) + " started NPE for " + getFirstName(target));
    }

    private void applyMindMod(obj_id self, String playerName, String typeStr, String valueStr, String durationStr) throws InterruptedException {
        obj_id target = findPlayerByName(self, playerName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        int type = utils.stringToInt(typeStr);
        float value = utils.stringToFloat(valueStr);
        float duration = utils.stringToFloat(durationStr);
        // Hook into mental_state_mod if available
        sendSystemMessageTestingOnly(self, "[MindMod] Type: " + type + ", Value: " + value + ", Duration: " + duration);
    }

    private void showMindState(obj_id self, String playerName) throws InterruptedException {
        obj_id target = findPlayerByName(self, playerName);
        if (isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "[MindState] Functionality available if integrated.");
        } else {
            sendSystemMessageTestingOnly(self, "Player not found.");
        }
    }

    private void setAttribValue(obj_id self, String playerName, String attrIdStr, String valueStr) throws InterruptedException {
        obj_id target = findPlayerByName(self, playerName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        int attrId = utils.stringToInt(attrIdStr);
        int value = utils.stringToInt(valueStr);
        sendSystemMessageTestingOnly(self, "[SetAttrib] Attr: " + attrId + ", Value: " + value);
    }

    private void modAttribTemp(obj_id self, String playerName, String attrIdStr, String valueStr, String durationStr) throws InterruptedException {
        obj_id target = findPlayerByName(self, playerName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        int attrId = utils.stringToInt(attrIdStr);
        int value = utils.stringToInt(valueStr);
        float duration = utils.stringToFloat(durationStr);
        sendSystemMessageTestingOnly(self, "[ModAttrib] Attr: " + attrId + ", Value: " + value + ", Duration: " + duration);
    }

    private obj_id findPlayerByName(obj_id self, String name) throws InterruptedException {
        if (name == null || name.length() == 0) {
            return null;
        }
        obj_id direct = getPlayerIdFromFirstName(name.toLowerCase());
        if (isIdValid(direct)) {
            return direct;
        }
        obj_id[] players = getPlayerCreaturesInRange(self, 50000);
        if (players != null) {
            for (obj_id p : players) {
                if (getFirstName(p).equalsIgnoreCase(name)) {
                    return p;
                }
            }
        }
        return null;
    }

    private location getGoodLocation(location current) {
        current.y = 5.0f;
        return current;
    }

    private void giveResourceCrate(obj_id self, String resourceName, String amountStr) throws InterruptedException {
        if (!isGod(self)) return;
        int amount = utils.stringToInt(amountStr);
        if (amount <= 0 || amount > 100000) {
            sendSystemMessageTestingOnly(self, "Amount must be between 1 and 100000.");
            return;
        }
        obj_id crate = createObject("object/resource_container.iff", getLocation(self));
        if (isIdValid(crate)) {
            setObjVar(crate, "resource.name", resourceName);
            setCount(crate, amount);
            sendSystemMessageTestingOnly(self, "Created resource crate: " + resourceName + " x" + amount);
            ModerationWebhook.sendPlainMessage("📦 Admin " + getFirstName(self) + " spawned resource crate: " + resourceName + " (" + amount + ")");
        }
    }
    private void flagCheater(obj_id self, String playerName) throws InterruptedException {
        obj_id target = findPlayerByName(self, playerName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }

        attachScript(target, "csr.cheater");
        setObjVar(target, "cheater_days", 7);
        CustomerServiceLog("SuspectedCheaterChannel", getName(target) + " flagged as suspected cheater.");
        sendSystemMessageTestingOnly(self, getFirstName(target) + " has been flagged as a suspected cheater.");

        ModerationWebhook.sendModerationAction("Cheater flagged", self, getFirstName(self), target, "Flagged for monitoring.", 0);
    }

    private void unflagCheater(obj_id self, String playerName) throws InterruptedException {
        obj_id target = findPlayerByName(self, playerName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }

        removeObjVar(target, "cheater");
        removeObjVar(target, "cheater_days");
        detachScript(target, "csr.cheater");
        CustomerServiceLog("SuspectedCheaterChannel", getName(target) + " cleared of cheating suspicion.");
        sendSystemMessageTestingOnly(self, getFirstName(target) + " is no longer flagged as a cheater.");

        ModerationWebhook.sendModerationAction("Cheater cleared", self, getFirstName(self), target, "Flag removed.", 0);
    }

    private void moveDungeon(obj_id self, String dungeonName) throws InterruptedException {
        setObjVar(self, "space_dungeon.move_dungeon", dungeonName);
        sendSystemMessageTestingOnly(self, "Dungeon move command set for: " + dungeonName);
        ModerationWebhook.sendPlainMessage("📡 Admin " + getFirstName(self) + " set dungeon move target to: " + dungeonName);
    }

private void getPlayerIdByName(obj_id self, String name) throws InterruptedException {
    if (name == null || name.length() == 0) {
        sendSystemMessageTestingOnly(self, "Usage: getplayerid <name>");
        return;
    }
    obj_id id = getPlayerIdFromFirstName(name.toLowerCase());
    if (isIdValid(id)) {
        sendSystemMessageTestingOnly(self, name + " has ID: " + id);
    } else {
        sendSystemMessageTestingOnly(self, "Player not found.");
    }
}

private void startFsIntro(obj_id self, String playerName) throws InterruptedException {
    obj_id target = findPlayerByName(self, playerName);
    if (!isIdValid(target)) {
        sendSystemMessageTestingOnly(self, "Player not found.");
        return;
    }
    if (!hasScript(target, "quest.force_sensitive.fs_kickoff")) {
        attachScript(target, "quest.force_sensitive.fs_kickoff");
    }
    setObjVar(target, "fs_kickoff_stage", 2);
    messageTo(target, "meetOldMan", null, 10.0f, false);
    sendSystemMessageTestingOnly(self, "FS Intro started for " + getFirstName(target));
    ModerationWebhook.sendPlainMessage("✨ Admin " + getFirstName(self) + " started FS intro for " + getFirstName(target));
}

private void wipePlayerItems(obj_id self, String playerName) throws InterruptedException {
    obj_id target = findPlayerByName(self, playerName);
    if (!isIdValid(target)) {
        sendSystemMessageTestingOnly(self, "Player not found.");
        return;
    }
    obj_id inventory = utils.getInventoryContainer(target);
    if (!isIdValid(inventory)) {
        sendSystemMessageTestingOnly(self, "Unable to access inventory.");
        return;
    }
    obj_id[] contents = getContents(inventory);
    if (contents != null) {
        for (obj_id item : contents) {
            destroyObject(item);
        }
    }
    sendSystemMessageTestingOnly(self, "Wiped all items from " + getFirstName(target));
    ModerationWebhook.sendModerationAction("Inventory wipe", self, getFirstName(self), target, "Manual wipe executed.", 0);
}


private void runSystemCommand(obj_id self, String fullCmd) throws InterruptedException {
    if (!isGod(self)) {
        sendSystemMessageTestingOnly(self, "Access denied.");
        return;
    }
    String result = system_process.runAndGetOutput(fullCmd);
    sendSystemMessageTestingOnly(self, "[SYS] Output: " + result);
    ModerationWebhook.sendPlainMessage("🖥️ Admin " + getFirstName(self) + " ran syscmd: `" + fullCmd + "` -> " + result);
}

    private void warnPlayer(obj_id self, String args) throws InterruptedException {
        String[] parts = splitArgs(args, 2);
        if (parts.length < 2) {
            sendSystemMessageTestingOnly(self, "Usage: warn <player> <reason>");
            return;
        }
        obj_id target = findPlayerByName(self, parts[0]);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        moderation_lib.addWarning(self, target, parts[1]);
    }

    private void addStaffNote(obj_id self, String args) throws InterruptedException {
        String[] parts = splitArgs(args, 2);
        if (parts.length < 2) {
            sendSystemMessageTestingOnly(self, "Usage: note <player> <note text>");
            return;
        }
        obj_id target = findPlayerByName(self, parts[0]);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        moderation_lib.addNote(self, target, parts[1]);
    }

    private void mutePlayer(obj_id self, String args) throws InterruptedException {
        String[] parts = splitArgs(args, 3);
        if (parts.length < 2) {
            sendSystemMessageTestingOnly(self, "Usage: mute <player> <minutes> [reason]");
            return;
        }
        int minutes = utils.stringToInt(parts[1]);
        if (minutes <= 0) {
            sendSystemMessageTestingOnly(self, "Minutes must be greater than zero.");
            return;
        }
        String reason = parts.length > 2 ? parts[2] : "Muted by staff.";
        obj_id target = findPlayerByName(self, parts[0]);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        moderation_lib.applyMute(self, target, minutes, reason);
    }

    private void unmutePlayer(obj_id self, String args) throws InterruptedException {
        String[] parts = splitArgs(args, 2);
        if (parts.length == 0) {
            sendSystemMessageTestingOnly(self, "Usage: unmute <player> [reason]");
            return;
        }
        String reason = parts.length > 1 ? parts[1] : "Mute removed by staff.";
        obj_id target = findPlayerByName(self, parts[0]);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        moderation_lib.clearMute(self, target, reason);
    }

    private void showModerationHistory(obj_id self, String args) throws InterruptedException {
        String targetName = firstArg(args);
        if (targetName == null || targetName.length() == 0) {
            sendSystemMessageTestingOnly(self, "Usage: history <player>");
            return;
        }
        obj_id target = findPlayerByName(self, targetName);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        moderation_lib.sendHistoryToAdmin(self, target);
    }

    private void giveCredits(obj_id self, String args) throws InterruptedException {
        String[] parts = splitArgs(args, 2);
        if (parts.length < 2) {
            sendSystemMessageTestingOnly(self, "Usage: givecredits <player> <amount>");
            return;
        }
        obj_id target = findPlayerByName(self, parts[0]);
        if (!isIdValid(target)) {
            sendSystemMessageTestingOnly(self, "Player not found.");
            return;
        }
        int amount = utils.stringToInt(parts[1]);
        if (amount <= 0) {
            sendSystemMessageTestingOnly(self, "Amount must be greater than zero.");
            return;
        }
        if (amount > 1000000000) {
            sendSystemMessageTestingOnly(self, "Amount is too large. Please split the transfer into smaller chunks.");
            return;
        }
        if (money.bankTo(money.ACCT_CUSTOMER_SERVICE, target, amount)) {
            sendSystemMessageTestingOnly(self, "Transferred " + amount + " credits to " + getFirstName(target) + ".");
            sendSystemMessage(target, amount + " credits have been deposited to your bank account by staff.", "");
            CustomerServiceLog("Moderation", getFirstName(self) + " granted " + amount + " credits to " + getName(target), target);
            ModerationWebhook.sendModerationAction("Credit grant", self, getFirstName(self), target, amount + " credits granted.", 0);
        } else {
            sendSystemMessageTestingOnly(self, "Credit transfer failed.");
        }
    }

    private String firstArg(String args) {
        String[] parts = splitArgs(args, 1);
        return parts.length > 0 ? parts[0] : "";
    }

    private String[] splitArgs(String args, int limit) {
        if (args == null) {
            return new String[0];
        }
        String trimmed = args.trim();
        if (trimmed.length() == 0) {
            return new String[0];
        }
        if (limit <= 0) {
            return trimmed.split("\\s+");
        }
        return trimmed.split("\\s+", limit);
    }
    private static final String INVIS_ACTIVE_VAR = "omni.invis.active";
    private static final String INVIS_PREV_INVULN_VAR = "omni.invis.prevInvulnerable";
    private static final String INVIS_PREV_MAP_VAR = "omni.invis.prevMapVisibility";
    private static final String INVIS_PREV_COVER_STATE_VAR = "omni.invis.prevCoverState";
    private static final String INVIS_PREV_COVER_VIS_VAR = "omni.invis.prevCoverVisibility";

    private void setInvis(obj_id id, boolean enabled) throws InterruptedException {
        if (!isIdValid(id)) {
            return;
        }

        if (enabled) {
            if (!utils.hasScriptVar(id, INVIS_ACTIVE_VAR)) {
                utils.setScriptVar(id, INVIS_ACTIVE_VAR, true);
                utils.setScriptVar(id, INVIS_PREV_INVULN_VAR, isInvulnerable(id));
                utils.setScriptVar(id, INVIS_PREV_MAP_VAR, getVisibleOnMapAndRadar(id));
                utils.setScriptVar(id, INVIS_PREV_COVER_STATE_VAR, getState(id, STATE_COVER) != 0);
                utils.setScriptVar(id, INVIS_PREV_COVER_VIS_VAR, getCreatureCoverVisibility(id));
            }

            setInvulnerable(id, true);
            setState(id, STATE_COVER, true);
            setCreatureCoverVisibility(id, false);
            setVisibleOnMapAndRadar(id, false);
        } else {
            boolean wasInvulnerable = utils.hasScriptVar(id, INVIS_PREV_INVULN_VAR) && utils.getBooleanScriptVar(id, INVIS_PREV_INVULN_VAR);
            boolean wasVisibleOnMap = !utils.hasScriptVar(id, INVIS_PREV_MAP_VAR) || utils.getBooleanScriptVar(id, INVIS_PREV_MAP_VAR);
            boolean wasCovering = utils.hasScriptVar(id, INVIS_PREV_COVER_STATE_VAR) && utils.getBooleanScriptVar(id, INVIS_PREV_COVER_STATE_VAR);
            boolean wasCoverVisible = !utils.hasScriptVar(id, INVIS_PREV_COVER_VIS_VAR) || utils.getBooleanScriptVar(id, INVIS_PREV_COVER_VIS_VAR);

            setInvulnerable(id, wasInvulnerable);
            setState(id, STATE_COVER, wasCovering);
            setCreatureCoverVisibility(id, wasCoverVisible);
            setVisibleOnMapAndRadar(id, wasVisibleOnMap);

            utils.removeScriptVar(id, INVIS_ACTIVE_VAR);
            utils.removeScriptVar(id, INVIS_PREV_INVULN_VAR);
            utils.removeScriptVar(id, INVIS_PREV_MAP_VAR);
            utils.removeScriptVar(id, INVIS_PREV_COVER_STATE_VAR);
            utils.removeScriptVar(id, INVIS_PREV_COVER_VIS_VAR);
        }
    }
}
