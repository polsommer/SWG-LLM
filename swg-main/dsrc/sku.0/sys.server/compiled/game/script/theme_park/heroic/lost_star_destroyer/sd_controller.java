package script.theme_park.heroic.lost_star_destroyer;

import script.dictionary;
import script.library.skill;
import script.library.trial;
import script.library.utils;
import script.obj_id;
import script.*;
import script.library.trial;
import script.dictionary;
import script.library.skill;
import script.library.utils;
import script.library.*;
import java.util.HashSet;
import script.obj_id;

public class sd_controller extends script.base_script
{
    public sd_controller()
    {
    }
    public int lsdDied(obj_id self, obj_id player, dictionary params) throws InterruptedException
    {
        obj_id[] players = trial.getPlayersInDungeon(self);
        dictionary dict = new dictionary();
        dict.put("tokenIndex", 3);
            HashSet theSet = new HashSet();
            obj_id pInv = utils.getInventoryContainer(player);
            theSet.add(static_item.createNewItemFunction("item_tcg_loot_reward_series8_dagobah_garden", pInv));
            theSet.add(static_item.createNewItemFunction("item_tcg_loot_reward_series8_r2d2_dagobah", pInv));
        utils.messageTo(players, "handleAwardtoken", dict, 0, false);
        obj_id group = getGroupObject(players[0]);
        int calendarTime = getCalendarTime();
        String realTime = getCalendarTimeStringLocal(calendarTime);
        CustomerServiceLog("instance-heroic_lost_star_destroyer", "heroic_lost_star_destroyer Defeated in instance (" + self + ") by group_id (" + group + ") at " + realTime);
        CustomerServiceLog("instance-heroic_lost_star_destroyer", "Group (" + group + ") consists of: ");
        for (int i = 0; i < players.length; ++i)
        {
            String strProfession = skill.getProfessionName(getSkillTemplate(players[i]));
            CustomerServiceLog("instance-heroic_lost_star_destroyer", "Group (" + group + ") member " + i + " " + getFirstName(players[i]) + "'s(" + players[i] + ") profession is " + strProfession + ".");
        }
        return SCRIPT_CONTINUE;
    }
}
