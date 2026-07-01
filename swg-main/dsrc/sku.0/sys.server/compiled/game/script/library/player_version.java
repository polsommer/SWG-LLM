package script.library;

import script.obj_id;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class player_version extends script.base_script {
    
    public player_version() {
    }

    public static boolean updateSkills(obj_id player) throws InterruptedException {
        return isIdValid(player) && isPlayer(player);
    }

    public static void zeroPlayerSkillData(obj_id player) throws InterruptedException {
        if (!isIdValid(player)) {
            return;
        }

        String[] mods = getSkillStatModListingForPlayer(player);
        if (mods != null && mods.length > 0) {
            for (String mod : mods) {
                applySkillStatisticModifier(player, mod, -getSkillStatMod(player, mod));
            }
        }

        String[] cmds = getCommandListingForPlayer(player);
        if (cmds != null && cmds.length > 0) {
            for (String cmd : cmds) {
                revokeCommand(player, cmd);
            }
        }

        int[] schematics = getSchematicListingForPlayer(player);
        if (schematics != null && schematics.length > 0) {
            for (int schematic : schematics) {
                revokeSchematic(player, schematic);
            }
        }
    }

    public static String[] orderSkillListForRevoke(String[] skillList) throws InterruptedException {
        if (skillList == null || skillList.length == 0) {
            return new String[0];
        }

        List<String> orderedSkills = new ArrayList<>();

        for (String skill : skillList) {
            LOG("playerVersion", "Ordering skill = " + skill);
            String[] reqs = getSkillPrerequisiteSkills(skill);
            if (reqs == null || reqs.length == 0) {
                LOG("playerVersion", "\treqs = null || length = 0... appending...");
                orderedSkills.add(skill);
            } else {
                int idx = orderedSkills.size();
                for (String req : reqs) {
                    LOG("playerVersion", "\ttesting list for: " + req);
                    int pos = orderedSkills.indexOf(req);
                    if (pos > -1) {
                        LOG("playerVersion", "**\tfound req(" + req + ") at index = " + pos);
                        idx = Math.min(idx, pos);
                    }
                }
                LOG("playerVersion", "\t- inserting " + skill + " at index = " + idx);
                orderedSkills.add(idx, skill);
            }
        }
        return orderedSkills.toArray(new String[0]);
    }
}

