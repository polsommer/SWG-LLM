package script.terminal;

import script.*;
import script.library.city;
import script.library.money;
import script.library.sui;
import script.library.utils;

public class bank extends script.terminal.base.base_terminal {
    public bank() {
    }

    public static final String SCRIPTVAR_BANK = "bank";
    public static final string_id SID_BANK_OPTIONS = new string_id("sui", "mnu_bank");
    public static final string_id SID_BANK_CREDITS = new string_id("sui", "bank_credits");
    public static final string_id SID_BANK_ITEMS = new string_id("sui", "bank_items");
    public static final string_id SID_BANK_DEPOSITALL = new string_id("sui", "bank_depositall");
    public static final string_id SID_BANK_WITHDRAWALL = new string_id("sui", "bank_withdrawall");
    public static final string_id SID_CITY_BANK_BANNED = new string_id("city/city", "bank_banned");

    private static final int PAYOUT_COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes in milliseconds
    private static final int INTEREST_PERIOD_MS = 24 * 60 * 60 * 1000; // 1 day in milliseconds
    private static final double INTEREST_RATE = 0.005; // 5% interest rate for holding deposits for 24 hours
    private static final String PAYOUT_TIMESTAMP_VAR = "bank_payout_timestamp";
    private static final String LAST_DEPOSIT_TIMESTAMP_VAR = "bank_last_deposit_timestamp";
    private static final String LAST_DEPOSIT_AMOUNT_VAR = "bank_last_deposit_amount";

    public int OnInitialize(obj_id self) throws InterruptedException {
        setObjVar(self, "banking_bankid", getCurrentSceneName());
        return super.OnInitialize(self);
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException {
        setObjVar(player, "banking_bankid", getCurrentSceneName());
        menu_info_data mid = mi.getMenuItemByType(menu_info_types.ITEM_USE);
        if (mid == null) {
            return super.OnObjectMenuRequest(self, player, mi);
        }
        int mnu = mid.getId();
        int subBankCredits = mi.addSubMenu(mnu, menu_info_types.SERVER_MENU1, SID_BANK_CREDITS);
        obj_id bankTerminal = self;
        int subBankItems = mi.addSubMenu(mnu, menu_info_types.SERVER_MENU2, SID_BANK_ITEMS);
        int cash = getCashBalance(player);
        if (cash > 0) {
            mi.addSubMenu(mnu, menu_info_types.SERVER_MENU3, SID_BANK_DEPOSITALL);
        }
        int bank = getBankBalance(player);
        if (bank > 0) {
            mi.addSubMenu(mnu, menu_info_types.SERVER_MENU4, SID_BANK_WITHDRAWALL);
        }
        return super.OnObjectMenuRequest(self, player, mi);
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException {
        setObjVar(player, "banking_bankid", getCurrentSceneName());
        int city_id = getCityAtLocation(getLocation(self), 0);
        if ((city_id > 0) && city.isCityBanned(player, city_id)) {
            sendSystemMessage(player, SID_CITY_BANK_BANNED);
            return SCRIPT_CONTINUE;
        }

        if (item == menu_info_types.ITEM_USE || item == menu_info_types.SERVER_MENU1) {
            openBankMenu(player);
        } else if (item == menu_info_types.SERVER_MENU2) {
            openBankContainer(self, player);
        } else if (item == menu_info_types.SERVER_MENU3) {
            depositAllCashToBank(player);
        } else if (item == menu_info_types.SERVER_MENU4) {
            withdrawAllFromBank(player);
        }

        return SCRIPT_CONTINUE;
    }

    private void depositAllCashToBank(obj_id player) throws InterruptedException {
        int cash = getCashBalance(player);
        if (cash == 0) {
            money.nullTransactionError(player);
            return;
        }
        
        // Attempt to deposit cash
        if (money.deposit(player, cash)) {

            closeBankTransferSui(getSelf(), player);

            // Record the deposit amount and timestamp for interest calculation
            setObjVar(player, LAST_DEPOSIT_AMOUNT_VAR, cash);
            setObjVar(player, LAST_DEPOSIT_TIMESTAMP_VAR, (int)System.currentTimeMillis());
            
            // Random payout chance
            if (canReceivePayout(player) && rand(1, 10) == 1) {  // 20% chance
                int randomPayout = rand(1000, 10000);  // Random amount between 1000 and 5000
                handleRandomPayout(player, randomPayout);
                setObjVar(player, PAYOUT_TIMESTAMP_VAR, (int)System.currentTimeMillis());  // Update cooldown timestamp
            }
        } else {
            debugSpeakMsg(player, "Transaction aborted by system...");
        }
    }

    private void withdrawAllFromBank(obj_id player) throws InterruptedException {
        int bankBalance = getBankBalance(player);
        if (bankBalance == 0) {
            money.nullTransactionError(player);
            return;
        }
        
        // Calculate and apply interest if deposit was held for 24 hours
        if (shouldApplyInterest(player)) {
            double interest = calculateInterest(player);
            if (interest > 0) {
                int interestAmount = (int) interest;
                money.deposit(player, interestAmount);
                sendSystemMessageTestingOnly(player, "You've earned an interest of " + interestAmount + " credits for holding your deposit!");
            }
            // Clear the last deposit amount to prevent interest on the same amount
            removeObjVar(player, LAST_DEPOSIT_AMOUNT_VAR);
            removeObjVar(player, LAST_DEPOSIT_TIMESTAMP_VAR);
        }

        // Withdraw all funds
        if (money.withdraw(player, bankBalance)) {
            closeBankTransferSui(getSelf(), player);
        } else {
            debugSpeakMsg(player, "Transaction aborted by system...");
        }
    }

    private boolean shouldApplyInterest(obj_id player) throws InterruptedException {
        int lastDepositTime = hasObjVar(player, LAST_DEPOSIT_TIMESTAMP_VAR) ? getIntObjVar(player, LAST_DEPOSIT_TIMESTAMP_VAR) : 0;
        long currentTime = System.currentTimeMillis();
        return (currentTime - (long) lastDepositTime) >= INTEREST_PERIOD_MS;
    }

    private double calculateInterest(obj_id player) throws InterruptedException {
        int lastDepositAmount = hasObjVar(player, LAST_DEPOSIT_AMOUNT_VAR) ? getIntObjVar(player, LAST_DEPOSIT_AMOUNT_VAR) : 0;
        return lastDepositAmount * INTEREST_RATE;
    }

    private boolean canReceivePayout(obj_id player) throws InterruptedException {
        int lastPayoutTime = hasObjVar(player, PAYOUT_TIMESTAMP_VAR) ? getIntObjVar(player, PAYOUT_TIMESTAMP_VAR) : 0;
        long currentTime = System.currentTimeMillis();
        return (currentTime - (long) lastPayoutTime) >= PAYOUT_COOLDOWN_MS;
    }

    private void handleRandomPayout(obj_id player, int payoutAmount) throws InterruptedException {
        obj_id bankTerminal = getSelf();

        if (isIdValid(bankTerminal) && getPlayerStationId(bankTerminal) == getPlayerStationId(player)) {
            playMusic(player, "sound/item_buzzing_lp.snd");
            sendSystemMessageTestingOnly(player, "Jabba the Hutt noticed your deposit! He suggests you keep saving for bigger rewards.");
        } else {
            playMusic(player, "sound/item_blasterpack_bomb_act.snd");
            money.systemPayout(money.ACCT_BETA_TEST, player, payoutAmount, money.DICT_PAY_HANDLER, null);
            sendSystemMessageTestingOnly(player, "It's your lucky day! Jabba the Hutt gave you an extra tip of " + payoutAmount + " credits!");
        }
    }

    public boolean openBankMenu(obj_id player) throws InterruptedException {
        obj_id self = getSelf();
        if (!isIdValid(player)) return false;

        String scriptvar = SCRIPTVAR_BANK + "." + player;
        if (utils.hasScriptVar(self, scriptvar)) {
            int oldPid = utils.getIntScriptVar(self, scriptvar);
            sui.closeSUI(player, oldPid);
            utils.removeScriptVar(self, scriptvar);
        }

        int pid = sui.bank(player);
        if (pid > -1) {
            sendSystemMessageTestingOnly(player, "we noticed your trying to deposit! jabba suggests you deposit all for bigger rewards.");
            utils.setScriptVar(player, SCRIPTVAR_BANK + ".terminal", self);
            utils.setScriptVar(self, scriptvar, pid);

            dictionary d = new dictionary();
            d.put("player", player);
            messageTo(self, "forceCloseBankSui", d, 30.0f, false);
            return true;
        }
        return false;
    }

    public int forceCloseBankSui(obj_id self, dictionary params) throws InterruptedException {
        obj_id player = params.getObjId("player");
        if (isIdValid(player)) {
            closeBankTransferSui(self, player);
            obj_id current = utils.getObjIdScriptVar(player, SCRIPTVAR_BANK + ".terminal");
            if (isIdValid(current) && current == self) {
                utils.removeScriptVar(player, SCRIPTVAR_BANK + ".terminal");
            }
        }
        return SCRIPT_CONTINUE;
    }

    public void closeBankTransferSui(obj_id self, obj_id player) throws InterruptedException {
        String scriptvar = SCRIPTVAR_BANK + "." + player;
        if (utils.hasScriptVar(self, scriptvar)) {
            int pid = utils.getIntScriptVar(self, scriptvar);
            sui.closeSUI(player, pid);
            utils.removeScriptVar(self, scriptvar);
        }
    }
}

