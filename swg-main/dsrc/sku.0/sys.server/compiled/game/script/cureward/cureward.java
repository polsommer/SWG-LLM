package script.cureward;

import script.dictionary;
import script.obj_id;
import java.util.ArrayList;
import java.util.List;

public class cureward extends script.base_script {
    public cureward() {}

    public int OnAttach(obj_id self) throws InterruptedException {
        if (!isCombatUpgradeRewardEnabled()) {
            return SCRIPT_CONTINUE;
        }
        if (!createRewards(self)) {
            scheduleRetryReward(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException {
        if (!isCombatUpgradeRewardEnabled()) {
            detachScript(self, "cureward.cureward");
        }
        return SCRIPT_CONTINUE;
    }

    private boolean isCombatUpgradeRewardEnabled() throws InterruptedException {
        try {
            return getConfigSetting("GameServer", "combatUpgradeReward") != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean createRewards(obj_id self) throws InterruptedException {
        try {
            if (isNewPlayer(self)) {
                return true;
            }

            int bornOnDate = getPlayerBirthDate(self);
            int currentGameDate = getGameTime();
            int ageInDays = currentGameDate - bornOnDate;

            if (ageInDays < 0) {
                return false;
            }

            List<String> rewardTemplates = getRewardTemplatesForAge(ageInDays);
            boolean success = true;

            for (String template : rewardTemplates) {
                if (template == null || template.isEmpty()) {
                    continue;
                }

                obj_id reward = createObjectInInventoryAllowOverload(template, self);
                if (!isIdValid(reward)) {
                    success = false;
                }
            }

            return success;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNewPlayer(obj_id self) throws InterruptedException {
        try {
            int bornOnDate = getPlayerBirthDate(self);
            return bornOnDate > 1579;
        } catch (Exception e) {
            return true; // Assume new player in case of error
        }
    }

    private List<String> getRewardTemplatesForAge(int ageInDays) {
        List<String> rewards = new ArrayList<>();

        try {
            if (ageInDays >= 0) {
                rewards.add("object/tangible/event_perk/frn_loyalty_award_plaque_silver.iff");
            }
            if (ageInDays > 365) {
                rewards.add("object/tangible/event_perk/frn_loyalty_award_plaque_gold.iff");
            }
            // Add more tiers easily here
        } catch (Exception e) {
            // Handle error silently for now
        }

        return rewards;
    }

    private void scheduleRetryReward(obj_id self) throws InterruptedException {
        try {
            messageTo(self, "handleRetryRewardNextLogin", null, 1, false);
        } catch (Exception e) {
            // Handle error silently for now
        }
    }

    public int handleRetryRewardNextLogin(obj_id self, dictionary params) throws InterruptedException {
        try {
            detachScript(self, "cureward.cureward");
        } catch (Exception e) {
            // Handle error silently for now
        }
        return SCRIPT_CONTINUE;
    }
}

