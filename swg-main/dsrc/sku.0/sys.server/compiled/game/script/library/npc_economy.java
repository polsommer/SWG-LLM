package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

public class npc_economy extends script.base_script
{
    public npc_economy()
    {
    }

    public static final String OBJVAR_ROOT = "ai.econ";
    public static final String OBJVAR_CREDITS = OBJVAR_ROOT + ".credits";
    public static final String OBJVAR_RESERVE = OBJVAR_ROOT + ".reserve";
    public static final String OBJVAR_INVENTORY_ROOT = OBJVAR_ROOT + ".inventory";
    public static final String OBJVAR_LAST_TRADE = OBJVAR_ROOT + ".lastTrade";
    public static final String OBJVAR_LAST_PROFIT = OBJVAR_ROOT + ".lastProfit";
    public static final String OBJVAR_LAST_RESULT = OBJVAR_ROOT + ".lastResult";
    public static final String OBJVAR_LAST_ITEM = OBJVAR_ROOT + ".lastItem";
    public static final String OBJVAR_LAST_PRICE = OBJVAR_ROOT + ".lastPrice";
    public static final String OBJVAR_LAST_REASON = OBJVAR_ROOT + ".lastReason";
    public static final String OBJVAR_TRADE_COOLDOWN = OBJVAR_ROOT + ".tradeCooldownUntil";
    public static final String OBJVAR_TRADE_COUNT_DAY = OBJVAR_ROOT + ".daily.day";
    public static final String OBJVAR_TRADE_COUNT_VALUE = OBJVAR_ROOT + ".daily.count";
    public static final String OBJVAR_BLACKLIST_ROOT = OBJVAR_ROOT + ".blacklist";
    public static final int DEFAULT_TX_LIMIT = 8;

    public static void initializeEconomy(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            return;
        }
        if (!hasObjVar(npc, OBJVAR_CREDITS))
        {
            setObjVar(npc, OBJVAR_CREDITS, rand(2500, 9000));
        }
        if (!hasObjVar(npc, OBJVAR_RESERVE))
        {
            int credits = getIntObjVar(npc, OBJVAR_CREDITS);
            setObjVar(npc, OBJVAR_RESERVE, Math.max(500, credits / 3));
        }
        if (!hasObjVar(npc, OBJVAR_LAST_TRADE))
        {
            setObjVar(npc, OBJVAR_LAST_TRADE, 0);
        }
        if (!hasObjVar(npc, OBJVAR_LAST_PROFIT))
        {
            setObjVar(npc, OBJVAR_LAST_PROFIT, 0);
        }
        if (!hasObjVar(npc, OBJVAR_TRADE_COOLDOWN))
        {
            setObjVar(npc, OBJVAR_TRADE_COOLDOWN, 0);
        }
        if (!hasObjVar(npc, OBJVAR_TRADE_COUNT_DAY))
        {
            setObjVar(npc, OBJVAR_TRADE_COUNT_DAY, -1);
        }
        if (!hasObjVar(npc, OBJVAR_TRADE_COUNT_VALUE))
        {
            setObjVar(npc, OBJVAR_TRADE_COUNT_VALUE, 0);
        }
    }

    public static int getVendorUtilityModifier(obj_id npc) throws InterruptedException
    {
        initializeEconomy(npc);
        int modifier = 0;
        int now = getGameTime();
        if (hasObjVar(npc, OBJVAR_TRADE_COOLDOWN) && getIntObjVar(npc, OBJVAR_TRADE_COOLDOWN) > now)
        {
            modifier -= 10;
        }
        int credits = getIntObjVar(npc, OBJVAR_CREDITS);
        int reserve = getIntObjVar(npc, OBJVAR_RESERVE);
        if (credits > reserve)
        {
            modifier += 4;
        }
        if (hasObjVar(npc, OBJVAR_LAST_PROFIT))
        {
            int lastProfit = getIntObjVar(npc, OBJVAR_LAST_PROFIT);
            modifier += clamp(lastProfit / 500, -8, 8);
        }
        return modifier;
    }

    public static void processVendorGoal(obj_id npc, dictionary command) throws InterruptedException
    {
        initializeEconomy(npc);
        if (!canTradeNow(npc))
        {
            if (command != null)
            {
                command.put("subgoal", "vendor_cooldown");
            }
            return;
        }

        obj_id listing = chooseBestListing(npc);
        if (!isIdValid(listing) || listing == obj_id.NULL_ID)
        {
            if (command != null)
            {
                command.put("subgoal", "browse_terminal");
            }
            return;
        }

        obj_id vendor = findVendorForListing(npc, listing);

        int price = getListingPrice(listing);
        int value = estimateListingValue(npc, listing, price);
        boolean purchased = false;
        if (value >= price)
        {
            purchased = executePurchase(npc, vendor, listing, price, value - price);
        }
        else
        {
            purchased = trySellSurplusToVendor(npc, vendor);
        }

        if (command != null)
        {
            command.put("target", vendor);
            command.put("subgoal", purchased ? "trade_executed" : "trade_declined");
        }
    }

    public static dictionary findBestVendorOpportunity(obj_id npc, float searchRadius) throws InterruptedException
    {
        initializeEconomy(npc);
        dictionary out = new dictionary();
        out.put("action", "none");
        out.put("reason", "none");
        out.put("price", 0);
        out.put("value", 0);

        if (!canTradeNow(npc))
        {
            out.put("reason", "cooldown");
            return out;
        }

        obj_id listing = chooseBestListing(npc, searchRadius);
        if (!isIdValid(listing) || listing == obj_id.NULL_ID)
        {
            out.put("action", "sell");
            out.put("reason", "no_profitable_listing");
            return out;
        }

        obj_id vendor = findVendorForListing(npc, listing, searchRadius);
        if (!isIdValid(vendor) || vendor == obj_id.NULL_ID)
        {
            out.put("reason", "vendor_not_found");
            return out;
        }

        int price = getListingPrice(listing);
        int value = estimateListingValue(npc, listing, price);
        out.put("vendor", vendor);
        out.put("listing", listing);
        out.put("price", price);
        out.put("value", value);
        if (value >= price)
        {
            out.put("action", "buy");
            out.put("reason", "value_margin");
            return out;
        }
        out.put("action", "sell");
        out.put("reason", "inventory_rotation");
        return out;
    }

    public static boolean executeVendorOpportunity(obj_id npc, dictionary opportunity) throws InterruptedException
    {
        initializeEconomy(npc);
        if (opportunity == null)
        {
            return false;
        }
        String action = opportunity.getString("action");
        String reason = opportunity.getString("reason");
        obj_id vendor = opportunity.getObjId("vendor");
        if (!isIdValid(vendor) || vendor == obj_id.NULL_ID)
        {
            return false;
        }

        if ("buy".equals(action))
        {
            obj_id listing = opportunity.getObjId("listing");
            int price = opportunity.getInt("price");
            int value = opportunity.getInt("value");
            return executePurchase(npc, vendor, listing, price, value - price, reason);
        }
        if ("sell".equals(action))
        {
            return trySellSurplusToVendor(npc, vendor, reason);
        }
        return false;
    }

    private static boolean canTradeNow(obj_id npc) throws InterruptedException
    {
        int now = getGameTime();
        if (hasObjVar(npc, OBJVAR_TRADE_COOLDOWN) && getIntObjVar(npc, OBJVAR_TRADE_COOLDOWN) > now)
        {
            return false;
        }
        int day = now / 86400;
        int storedDay = hasObjVar(npc, OBJVAR_TRADE_COUNT_DAY) ? getIntObjVar(npc, OBJVAR_TRADE_COUNT_DAY) : -1;
        int count = hasObjVar(npc, OBJVAR_TRADE_COUNT_VALUE) ? getIntObjVar(npc, OBJVAR_TRADE_COUNT_VALUE) : 0;
        if (storedDay != day)
        {
            setObjVar(npc, OBJVAR_TRADE_COUNT_DAY, day);
            setObjVar(npc, OBJVAR_TRADE_COUNT_VALUE, 0);
            return true;
        }
        return count < DEFAULT_TX_LIMIT;
    }

    private static obj_id chooseBestListing(obj_id npc) throws InterruptedException
    {
        return chooseBestListing(npc, 36.0f);
    }

    private static obj_id chooseBestListing(obj_id npc, float radius) throws InterruptedException
    {
        location here = getLocation(npc);
        obj_id[] nearby = getObjectsInRange(here, radius);
        if (nearby == null)
        {
            return obj_id.NULL_ID;
        }

        obj_id bestListing = obj_id.NULL_ID;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id terminal = nearby[i];
            if (!isValidVendorTerminal(terminal))
            {
                continue;
            }
            obj_id container = vendor_lib.getAuctionContainer(terminal);
            if (!isIdValid(container) || container == obj_id.NULL_ID)
            {
                continue;
            }
            obj_id[] listings = getContents(container);
            if (listings == null)
            {
                continue;
            }
            for (int j = 0; j < listings.length; j++)
            {
                obj_id listing = listings[j];
                if (!isIdValid(listing) || isBlacklistedListing(npc, terminal, listing))
                {
                    continue;
                }
                int price = getListingPrice(listing);
                if (price < 1)
                {
                    continue;
                }
                int score = estimateListingValue(npc, listing, price) - price;
                if (score > bestScore)
                {
                    bestScore = score;
                    bestListing = listing;
                }
            }
        }
        return bestListing;
    }


    private static obj_id findVendorForListing(obj_id npc, obj_id listing) throws InterruptedException
    {
        return findVendorForListing(npc, listing, 36.0f);
    }

    private static obj_id findVendorForListing(obj_id npc, obj_id listing, float radius) throws InterruptedException
    {
        location here = getLocation(npc);
        obj_id[] nearby = getObjectsInRange(here, radius);
        if (nearby == null)
        {
            return obj_id.NULL_ID;
        }
        for (int i = 0; i < nearby.length; i++)
        {
            obj_id terminal = nearby[i];
            if (!isValidVendorTerminal(terminal))
            {
                continue;
            }
            obj_id container = vendor_lib.getAuctionContainer(terminal);
            if (!isIdValid(container))
            {
                continue;
            }
            obj_id[] listings = getContents(container);
            if (listings == null)
            {
                continue;
            }
            for (int j = 0; j < listings.length; j++)
            {
                if (listings[j] == listing)
                {
                    return terminal;
                }
            }
        }
        return obj_id.NULL_ID;
    }

    private static boolean isValidVendorTerminal(obj_id candidate) throws InterruptedException
    {
        if (!isIdValid(candidate) || !exists(candidate))
        {
            return false;
        }
        return hasScript(candidate, "terminal.npc_vendor") || hasScript(candidate, "terminal.nonvendor");
    }

    private static boolean isBlacklistedListing(obj_id npc, obj_id vendor, obj_id listing) throws InterruptedException
    {
        if (hasObjVar(listing, OBJVAR_BLACKLIST_ROOT + ".global") || hasObjVar(vendor, OBJVAR_BLACKLIST_ROOT + ".global"))
        {
            return true;
        }
        obj_id owner = hasObjVar(vendor, vendor_lib.OWNER_OBJVAR) ? getObjIdObjVar(vendor, vendor_lib.OWNER_OBJVAR) : obj_id.NULL_ID;
        if (isIdValid(owner) && isPlayer(owner))
        {
            String ownerKey = OBJVAR_BLACKLIST_ROOT + ".owner_" + owner;
            if (hasObjVar(npc, ownerKey))
            {
                return true;
            }
            if (hasObjVar(vendor, "vendor.no_npc_trade") || hasObjVar(listing, "vendor.no_npc_trade"))
            {
                return true;
            }
        }
        return false;
    }

    private static int getListingPrice(obj_id listing) throws InterruptedException
    {
        if (hasObjVar(listing, "item.object_for_sale.cash_cost"))
        {
            return Math.max(0, getIntObjVar(listing, "item.object_for_sale.cash_cost"));
        }
        if (hasObjVar(listing, "vendor.price"))
        {
            return Math.max(0, getIntObjVar(listing, "vendor.price"));
        }
        return 0;
    }

    private static int estimateListingValue(obj_id npc, obj_id listing, int price) throws InterruptedException
    {
        int value = price;
        String template = getTemplateName(listing);
        if (template == null)
        {
            template = "";
        }
        String lower = template.toLowerCase();
        if (lower.indexOf("weapon") >= 0 || lower.indexOf("armor") >= 0)
        {
            value += 500;
        }
        if (lower.indexOf("mission") >= 0 || lower.indexOf("waypoint") >= 0)
        {
            value += 350;
        }
        if (lower.indexOf("resource") >= 0 || lower.indexOf("ingredient") >= 0 || lower.indexOf("component") >= 0)
        {
            value += 250;
        }

        int inventoryDemand = hasObjVar(npc, OBJVAR_INVENTORY_ROOT + ".craftingDemand") ? getIntObjVar(npc, OBJVAR_INVENTORY_ROOT + ".craftingDemand") : 0;
        value += inventoryDemand * 15;

        int credits = getIntObjVar(npc, OBJVAR_CREDITS);
        int reserve = getIntObjVar(npc, OBJVAR_RESERVE);
        if (credits - price < reserve)
        {
            value -= 1000;
        }
        return value;
    }

    private static boolean executePurchase(obj_id npc, obj_id vendor, obj_id listing, int price, int expectedProfit, String reason) throws InterruptedException
    {
        if (!isIdValid(vendor) || !exists(vendor))
        {
            return false;
        }
        int credits = getIntObjVar(npc, OBJVAR_CREDITS);
        int reserve = getIntObjVar(npc, OBJVAR_RESERVE);
        if (credits - price < reserve || price <= 0)
        {
            return false;
        }

        boolean paid = money.requestPayment(npc, vendor, price, "no_handler", null, false);
        if (!paid)
        {
            addOwnerBlacklist(npc, vendor);
            recordTrade(npc, false, -price, "payment_failed", listing, price, reason);
            return false;
        }

        obj_id inventory = utils.getInventoryContainer(npc);
        if (isIdValid(inventory) && inventory != obj_id.NULL_ID)
        {
            putIn(listing, inventory);
        }
        setObjVar(npc, OBJVAR_CREDITS, Math.max(0, credits - price));
        incrementInventoryCounter(npc, listing, 1);
        recordTrade(npc, true, expectedProfit, "buy", listing, price, reason);
        return true;
    }

    private static boolean trySellSurplusToVendor(obj_id npc, obj_id vendor, String reason) throws InterruptedException
    {
        if (!isIdValid(vendor) || !exists(vendor))
        {
            return false;
        }
        obj_id inventory = utils.getInventoryContainer(npc);
        if (!isIdValid(inventory) || inventory == obj_id.NULL_ID)
        {
            return false;
        }
        obj_id[] contents = getContents(inventory);
        if (contents == null || contents.length == 0)
        {
            return false;
        }

        obj_id itemToSell = obj_id.NULL_ID;
        int price = 0;
        for (int i = 0; i < contents.length; i++)
        {
            obj_id candidate = contents[i];
            if (!isIdValid(candidate) || hasObjVar(candidate, "noTrade"))
            {
                continue;
            }
            String candidateTemplate = getTemplateName(candidate);
            if (candidateTemplate == null)
            {
                candidateTemplate = "";
            }
            String template = candidateTemplate.toLowerCase();
            if (template.indexOf("resource") >= 0 || template.indexOf("component") >= 0)
            {
                itemToSell = candidate;
                price = 125;
                break;
            }
        }
        if (!isIdValid(itemToSell))
        {
            return false;
        }

        if (!money.bankTo(vendor, npc, price))
        {
            recordTrade(npc, false, 0, "sale_payment_failed", itemToSell, price, reason);
            return false;
        }
        obj_id vendorContainer = vendor_lib.getAuctionContainer(vendor);
        if (isIdValid(vendorContainer) && vendorContainer != obj_id.NULL_ID)
        {
            putIn(itemToSell, vendorContainer);
        }
        int credits = getIntObjVar(npc, OBJVAR_CREDITS);
        setObjVar(npc, OBJVAR_CREDITS, credits + price);
        incrementInventoryCounter(npc, itemToSell, -1);
        recordTrade(npc, true, price, "sell", itemToSell, price, reason);
        return true;
    }

    private static boolean executePurchase(obj_id npc, obj_id vendor, obj_id listing, int price, int expectedProfit) throws InterruptedException
    {
        return executePurchase(npc, vendor, listing, price, expectedProfit, "unspecified");
    }

    private static boolean trySellSurplusToVendor(obj_id npc, obj_id vendor) throws InterruptedException
    {
        return trySellSurplusToVendor(npc, vendor, "unspecified");
    }

    private static void incrementInventoryCounter(obj_id npc, obj_id item, int delta) throws InterruptedException
    {
        String template = getTemplateName(item);
        if (template == null)
        {
            template = "unknown";
        }
        String key = sanitizeTemplateKey(template);
        String objvar = OBJVAR_INVENTORY_ROOT + "." + key;
        int old = hasObjVar(npc, objvar) ? getIntObjVar(npc, objvar) : 0;
        int next = old + delta;
        if (next < 0)
        {
            next = 0;
        }
        setObjVar(npc, objvar, next);
    }

    private static String sanitizeTemplateKey(String template) throws InterruptedException
    {
        String lower = template.toLowerCase();
        String out = "";
        for (int i = 0; i < lower.length(); i++)
        {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))
            {
                out += ch;
            }
            else
            {
                out += '_';
            }
        }
        return out;
    }

    private static void recordTrade(obj_id npc, boolean success, int profit, String result, obj_id item, int price, String reason) throws InterruptedException
    {
        int now = getGameTime();
        setObjVar(npc, OBJVAR_LAST_TRADE, now);
        setObjVar(npc, OBJVAR_LAST_PROFIT, profit);
        setObjVar(npc, OBJVAR_LAST_RESULT, result + (success ? "_ok" : "_fail"));
        setObjVar(npc, OBJVAR_LAST_ITEM, isIdValid(item) ? getTemplateName(item) : "unknown");
        setObjVar(npc, OBJVAR_LAST_PRICE, Math.max(0, price));
        setObjVar(npc, OBJVAR_LAST_REASON, reason == null ? "unspecified" : reason);
        setObjVar(npc, OBJVAR_TRADE_COOLDOWN, now + rand(30, 90));

        int day = now / 86400;
        int storedDay = hasObjVar(npc, OBJVAR_TRADE_COUNT_DAY) ? getIntObjVar(npc, OBJVAR_TRADE_COUNT_DAY) : -1;
        int count = hasObjVar(npc, OBJVAR_TRADE_COUNT_VALUE) ? getIntObjVar(npc, OBJVAR_TRADE_COUNT_VALUE) : 0;
        if (storedDay != day)
        {
            count = 0;
            setObjVar(npc, OBJVAR_TRADE_COUNT_DAY, day);
        }
        setObjVar(npc, OBJVAR_TRADE_COUNT_VALUE, count + 1);
    }

    private static void recordTrade(obj_id npc, boolean success, int profit, String result) throws InterruptedException
    {
        recordTrade(npc, success, profit, result, obj_id.NULL_ID, 0, "unspecified");
    }

    private static void addOwnerBlacklist(obj_id npc, obj_id vendor) throws InterruptedException
    {
        if (!hasObjVar(vendor, vendor_lib.OWNER_OBJVAR))
        {
            return;
        }
        obj_id owner = getObjIdObjVar(vendor, vendor_lib.OWNER_OBJVAR);
        if (!isIdValid(owner) || !isPlayer(owner))
        {
            return;
        }
        setObjVar(npc, OBJVAR_BLACKLIST_ROOT + ".owner_" + owner, getGameTime() + 3600);
    }

    private static int clamp(int value, int low, int high) throws InterruptedException
    {
        if (value < low)
        {
            return low;
        }
        if (value > high)
        {
            return high;
        }
        return value;
    }
}
