package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

import java.util.Vector;

public class economy_stabilizer extends script.base_script
{
    public economy_stabilizer()
    {
    }

    public static final String OBJVAR_ECONOMY_MODE = "economy.mode";
    public static final String OBJVAR_ECONOMY_SCORE = "economy.score";
    public static final String OBJVAR_ECONOMY_LAST_TICK = "economy.lastTick";
    public static final String OBJVAR_ECONOMY_ONLINE = "economy.onlineCount";
    public static final String OBJVAR_ECONOMY_LISTINGS = "economy.listings";
    public static final String OBJVAR_ECONOMY_CATEGORY_BALANCE = "economy.categoryBalance";
    public static final String OBJVAR_ECONOMY_SHORTAGE_ROOT = "economy.shortage";
    public static final String OBJVAR_FEE_ADJUST_ROOT = "economy.feeAdjust";

    public static final String MODE_RECOVERY = "Recovery";
    public static final String MODE_BALANCED = "Balanced";
    public static final String MODE_INFLATION_CONTROL = "Inflation Control";

    public static final String DATATABLE_ECONOMY_MODES = "datatables/economy/economy_health_modes.iff";
    public static final String DATATABLE_CATEGORY_DEMAND = "datatables/commodity/category_demand_coefficients.iff";
    public static final int CATEGORY_TABLE_VALIDATION_VERSION = 2;
    public static final String OBJVAR_CATEGORY_TABLE_VALIDATION = "economy.categoryTableValidation";

    public static final int DEFAULT_MIN_TICK_SECONDS = 300;
    public static final int DEFAULT_MODE_ADJUST_COOLDOWN_SECONDS = 600;
    public static final int DEFAULT_ONLINE_BASELINE = 200;
    public static final int DEFAULT_LISTINGS_PER_PLAYER = 6;
    public static final String[] SUPPLY_CATEGORIES = new String[]
    {
        "weapon",
        "weapon_melee",
        "weapon_ranged",
        "weapon_heavy",
        "armor",
        "armor_clothing",
        "armor_segment",
        "resource",
        "resource_inorganic",
        "resource_organic",
        "misc",
        "misc_consumable",
        "misc_component"
    };

    public static dictionary tickEconomy(obj_id sharedStateObj, location bazaarCenter, float scanRadius) throws InterruptedException
    {
        return tickEconomy(sharedStateObj, bazaarCenter, scanRadius, DEFAULT_MIN_TICK_SECONDS);
    }

    public static dictionary tickEconomy(obj_id sharedStateObj, location bazaarCenter, float scanRadius, int minTickSeconds) throws InterruptedException
    {
        validateCategoryDemandTable(sharedStateObj);
        dictionary metrics = new dictionary();
        metrics.put("mode", MODE_BALANCED);
        metrics.put("score", 0);
        metrics.put("tickSkipped", 0);
        if (!isIdValid(sharedStateObj) || sharedStateObj == obj_id.NULL_ID || !exists(sharedStateObj))
        {
            metrics.put("error", "invalid_shared_state");
            return metrics;
        }

        int now = getGameTime();
        int lastTick = hasObjVar(sharedStateObj, OBJVAR_ECONOMY_LAST_TICK) ? getIntObjVar(sharedStateObj, OBJVAR_ECONOMY_LAST_TICK) : 0;
        if ((now - lastTick) < Math.max(10, minTickSeconds))
        {
            metrics.put("tickSkipped", 1);
            metrics.put("mode", hasObjVar(sharedStateObj, OBJVAR_ECONOMY_MODE) ? getStringObjVar(sharedStateObj, OBJVAR_ECONOMY_MODE) : MODE_BALANCED);
            metrics.put("score", hasObjVar(sharedStateObj, OBJVAR_ECONOMY_SCORE) ? getIntObjVar(sharedStateObj, OBJVAR_ECONOMY_SCORE) : 0);
            return metrics;
        }

        metrics = evaluateEconomy(bazaarCenter, scanRadius);

        String mode = metrics.getString("mode");
        int score = metrics.getInt("score");
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_MODE, mode);
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_SCORE, score);
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_LAST_TICK, now);
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_ONLINE, metrics.getInt("onlineCount"));
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_LISTINGS, metrics.getInt("listingCount"));
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_CATEGORY_BALANCE, metrics.getInt("categorySupply"));
        persistShortageFlags(sharedStateObj, metrics);
        return metrics;
    }

    public static dictionary evaluateEconomy(location bazaarCenter, float scanRadius) throws InterruptedException
    {
        dictionary metrics = new dictionary();
        int onlineCount = Math.max(0, getNumPlayers());
        int listingCount = getListingCountFromBazaarTerminals(bazaarCenter, scanRadius);
        dictionary categoryMetrics = getCategorySupplyMetrics(bazaarCenter, scanRadius, onlineCount);
        int categorySupply = categoryMetrics.getInt("categorySupply");

        int activityScore = getListingActivityScore(listingCount, onlineCount);
        int populationScore = getPopulationScore(onlineCount);
        int score = clamp((int)Math.round((activityScore * 0.55f) + (populationScore * 0.2f) + (categorySupply * 0.25f)), 0, 100);
        String mode = getModeForScore(score);

        metrics.put("onlineCount", onlineCount);
        metrics.put("listingCount", listingCount);
        metrics.put("categorySupply", categorySupply);
        metrics.put("listingActivity", activityScore);
        metrics.put("populationPressure", populationScore);
        metrics.put("score", score);
        metrics.put("mode", mode);
        copyShortageMetrics(categoryMetrics, metrics);
        return metrics;
    }

    private static int getListingActivityScore(int listingCount, int onlineCount) throws InterruptedException
    {
        int targetPerPlayer = getModeTuningInt("Balanced", "targetListingsPerPlayer", DEFAULT_LISTINGS_PER_PLAYER);
        int denominator = Math.max(1, Math.max(onlineCount, 1) * Math.max(1, targetPerPlayer));
        float ratio = (float)listingCount / (float)denominator;
        return clamp((int)Math.round(100.0f * ratio), 0, 130);
    }

    private static int getPopulationScore(int onlineCount) throws InterruptedException
    {
        int baseline = getModeTuningInt("Balanced", "targetOnline", DEFAULT_ONLINE_BASELINE);
        if (baseline <= 0)
        {
            baseline = DEFAULT_ONLINE_BASELINE;
        }
        return clamp((onlineCount * 100) / baseline, 0, 130);
    }

    private static int getListingCountFromBazaarTerminals(location bazaarCenter, float scanRadius) throws InterruptedException
    {
        obj_id[] terminals = collectTerminals(bazaarCenter, scanRadius);
        if (terminals == null || terminals.length == 0)
        {
            return 0;
        }

        int listingCount = 0;
        for (int i = 0; i < terminals.length; i++)
        {
            obj_id terminal = terminals[i];
            if (!isIdValid(terminal) || terminal == obj_id.NULL_ID || !exists(terminal))
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
            listingCount += listings.length;
        }
        return Math.max(0, listingCount);
    }

    private static dictionary getCategorySupplyMetrics(location bazaarCenter, float scanRadius, int onlineCount) throws InterruptedException
    {
        validateCategoryDemandTable(obj_id.NULL_ID);
        dictionary categoryMetrics = new dictionary();
        obj_id[] terminals = collectTerminals(bazaarCenter, scanRadius);
        if (terminals == null || terminals.length == 0)
        {
            categoryMetrics.put("categorySupply", 0);
            return categoryMetrics;
        }

        int[] pools = new int[SUPPLY_CATEGORIES.length];

        for (int i = 0; i < terminals.length; i++)
        {
            obj_id terminal = terminals[i];
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
                if (!isIdValid(listing) || listing == obj_id.NULL_ID)
                {
                    continue;
                }
                String category = getListingCategory(listing);
                incrementSupplyPool(pools, category);
            }
        }

        dictionary shortageData = buildShortageMetrics(pools, onlineCount);

        int total = 0;
        int shortageCount = 0;
        int shortageEssentialCount = 0;
        for (int i = 0; i < SUPPLY_CATEGORIES.length; i++)
        {
            String category = SUPPLY_CATEGORIES[i];
            total += pools[i];
            shortageCount += shortageData.getInt("shortage." + category);
            shortageEssentialCount += shortageData.getInt("shortageEssential." + category);
        }
        shortageData.put("totalSupply", total);
        shortageData.put("shortageAny", shortageCount > 0 ? 1 : 0);
        shortageData.put("shortageEssentialAny", shortageEssentialCount > 0 ? 1 : 0);
        shortageData.put("shortageCount", shortageCount);
        shortageData.put("shortageEssentialCount", shortageEssentialCount);
        copyShortageMetrics(shortageData, categoryMetrics);
        if (total <= 0)
        {
            categoryMetrics.put("categorySupply", 0);
            return categoryMetrics;
        }

        int weapons = getPoolValue(pools, "weapon");
        int armor = getPoolValue(pools, "armor");
        int resources = getPoolValue(pools, "resource");
        int misc = getPoolValue(pools, "misc");
        int[] rootPools = new int[]{weapons, armor, resources, misc};
        float average = total / 4.0f;
        float deviation = 0.0f;
        for (int i = 0; i < rootPools.length; i++)
        {
            deviation += Math.abs(rootPools[i] - average);
        }
        float normalizedDeviation = deviation / Math.max(1.0f, total);
        int balanceScore = clamp((int)Math.round(100.0f - (normalizedDeviation * 100.0f)), 0, 100);

        int scarcityPenalty = 0;
        int minimumCategoryShare = getModeTuningInt("Recovery", "minCategoryShare", 10);
        for (int i = 0; i < rootPools.length; i++)
        {
            int share = (int)Math.round((rootPools[i] * 100.0f) / Math.max(1.0f, total));
            if (share < minimumCategoryShare)
            {
                scarcityPenalty += (minimumCategoryShare - share);
            }
        }

        categoryMetrics.put("categorySupply", clamp(balanceScore - scarcityPenalty, 0, 100));
        return categoryMetrics;
    }

    private static dictionary buildShortageMetrics(int[] liveSupply, int onlineCount) throws InterruptedException
    {
        dictionary shortageData = new dictionary();
        int totalSupply = 0;
        if (liveSupply != null)
        {
            for (int i = 0; i < liveSupply.length; i++)
            {
                totalSupply += Math.max(0, liveSupply[i]);
            }
        }

        for (int i = 0; i < SUPPLY_CATEGORIES.length; i++)
        {
            String category = SUPPLY_CATEGORIES[i];
            dictionary demandRow = dataTableGetRow(DATATABLE_CATEGORY_DEMAND, category);
            String rootCategory = vendor_lib.getEconomyCategoryRoot(category);
            if ((demandRow == null || demandRow.isEmpty()) && !rootCategory.equals(category))
            {
                demandRow = dataTableGetRow(DATATABLE_CATEGORY_DEMAND, rootCategory);
            }
            int baseTarget = 10;
            float playerFactor = 0.35f;
            int minPrice = 0;
            int maxPrice = 0;
            int essentialFlag = 0;
            if (demandRow != null && !demandRow.isEmpty())
            {
                baseTarget = demandRow.getInt("base_target");
                playerFactor = demandRow.getFloat("player_factor");
                minPrice = demandRow.getInt("min_price");
                maxPrice = demandRow.getInt("max_price");
                essentialFlag = demandRow.getInt("essential_flag");
            }

            int computedTarget = Math.max(0, Math.round(baseTarget + (playerFactor * onlineCount)));
            if (minPrice > 0 && maxPrice >= minPrice)
            {
                int priceWindow = Math.max(1, maxPrice - minPrice);
                int pressure = clamp((100 * minPrice) / Math.max(1, priceWindow), 0, 100);
                computedTarget += Math.round((computedTarget * pressure) / 500.0f);
            }

            int live = liveSupply != null && i < liveSupply.length ? Math.max(0, liveSupply[i]) : 0;
            int shortage = live < computedTarget ? 1 : 0;
            int shortageEssential = shortage == 1 && essentialFlag == 1 ? 1 : 0;

            shortageData.put("supply." + category, live);
            shortageData.put("target." + category, computedTarget);
            shortageData.put("shortage." + category, shortage);
            shortageData.put("shortageEssential." + category, shortageEssential);
        }
        shortageData.put("totalSupply", totalSupply);
        return shortageData;
    }

    private static void copyShortageMetrics(dictionary source, dictionary destination) throws InterruptedException
    {
        if (source == null || source.isEmpty() || destination == null)
        {
            return;
        }
        for (int i = 0; i < SUPPLY_CATEGORIES.length; i++)
        {
            String category = SUPPLY_CATEGORIES[i];
            destination.put("supply." + category, source.getInt("supply." + category));
            destination.put("target." + category, source.getInt("target." + category));
            destination.put("shortage." + category, source.getInt("shortage." + category));
            destination.put("shortageEssential." + category, source.getInt("shortageEssential." + category));
        }
        destination.put("shortageAny", source.getInt("shortageAny"));
        destination.put("shortageEssentialAny", source.getInt("shortageEssentialAny"));
        destination.put("shortageCount", source.getInt("shortageCount"));
        destination.put("shortageEssentialCount", source.getInt("shortageEssentialCount"));
        destination.put("totalSupply", source.getInt("totalSupply"));
    }

    private static void persistShortageFlags(obj_id sharedStateObj, dictionary metrics) throws InterruptedException
    {
        if (!isIdValid(sharedStateObj) || sharedStateObj == obj_id.NULL_ID || !exists(sharedStateObj))
        {
            return;
        }
        if (metrics == null || metrics.isEmpty())
        {
            return;
        }

        for (int i = 0; i < SUPPLY_CATEGORIES.length; i++)
        {
            String category = SUPPLY_CATEGORIES[i];
            setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + category + ".live", metrics.getInt("supply." + category));
            setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + category + ".target", metrics.getInt("target." + category));
            setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + category + ".flag", metrics.getInt("shortage." + category));
            setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + category + ".essential", metrics.getInt("shortageEssential." + category));
        }

        setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + ".any", metrics.getInt("shortageAny"));
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + ".essentialAny", metrics.getInt("shortageEssentialAny"));
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + ".count", metrics.getInt("shortageCount"));
        setObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + ".essentialCount", metrics.getInt("shortageEssentialCount"));
    }

    public static int getCategoryShortageFlag(obj_id sharedStateObj, String category) throws InterruptedException
    {
        if (!isIdValid(sharedStateObj) || sharedStateObj == obj_id.NULL_ID || !exists(sharedStateObj))
        {
            return 0;
        }
        if (category == null || category.length() < 1)
        {
            return 0;
        }
        String normalizedCategory = toLower(category);
        return hasObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + normalizedCategory + ".flag") ? getIntObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + normalizedCategory + ".flag") : 0;
    }

    public static int getCategoryShortageTarget(obj_id sharedStateObj, String category) throws InterruptedException
    {
        if (!isIdValid(sharedStateObj) || sharedStateObj == obj_id.NULL_ID || !exists(sharedStateObj))
        {
            return 0;
        }
        if (category == null || category.length() < 1)
        {
            return 0;
        }
        String normalizedCategory = toLower(category);
        return hasObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + normalizedCategory + ".target") ? getIntObjVar(sharedStateObj, OBJVAR_ECONOMY_SHORTAGE_ROOT + "." + normalizedCategory + ".target") : 0;
    }

    private static obj_id[] collectTerminals(location bazaarCenter, float scanRadius) throws InterruptedException
    {
        float safeRadius = Math.max(16.0f, scanRadius);
        obj_id[] bazaars = getAllObjectsWithScript(bazaarCenter, safeRadius, "terminal.bazaar");
        obj_id[] npcVendors = getAllObjectsWithScript(bazaarCenter, safeRadius, "terminal.npc_vendor");
        obj_id[] nonVendors = getAllObjectsWithScript(bazaarCenter, safeRadius, "terminal.nonvendor");

        Vector merged = new Vector();
        appendUniqueIds(merged, bazaars);
        appendUniqueIds(merged, npcVendors);
        appendUniqueIds(merged, nonVendors);

        obj_id[] out = new obj_id[merged.size()];
        merged.toArray(out);
        return out;
    }

    private static void appendUniqueIds(Vector merged, obj_id[] input) throws InterruptedException
    {
        if (input == null || merged == null)
        {
            return;
        }
        for (int i = 0; i < input.length; i++)
        {
            obj_id candidate = input[i];
            if (!isIdValid(candidate) || candidate == obj_id.NULL_ID)
            {
                continue;
            }
            boolean alreadyFound = false;
            for (int j = 0; j < merged.size(); j++)
            {
                obj_id existing = (obj_id)merged.get(j);
                if (existing == candidate)
                {
                    alreadyFound = true;
                    break;
                }
            }
            if (!alreadyFound)
            {
                merged.add(candidate);
            }
        }
    }

    private static String getListingCategory(obj_id listing) throws InterruptedException
    {
        return vendor_lib.normalizeListingCategory(vendor_lib.getListingCategoryForTemplateValue(getTemplateName(listing)));
    }

    private static void incrementSupplyPool(int[] pools, String category) throws InterruptedException
    {
        if (pools == null || pools.length < 1)
        {
            return;
        }
        String normalized = vendor_lib.normalizeListingCategory(category);
        String rootCategory = vendor_lib.getEconomyCategoryRoot(normalized);
        int categoryIndex = getSupplyCategoryIndex(normalized);
        if (categoryIndex > -1)
        {
            pools[categoryIndex] += 1;
        }
        if (!rootCategory.equals(normalized))
        {
            int rootIndex = getSupplyCategoryIndex(rootCategory);
            if (rootIndex > -1)
            {
                pools[rootIndex] += 1;
            }
        }
    }

    private static int getSupplyCategoryIndex(String category) throws InterruptedException
    {
        String normalized = vendor_lib.normalizeListingCategory(category);
        for (int i = 0; i < SUPPLY_CATEGORIES.length; i++)
        {
            if (SUPPLY_CATEGORIES[i].equals(normalized))
            {
                return i;
            }
        }
        return getSupplyCategoryIndexRaw("misc");
    }

    private static int getSupplyCategoryIndexRaw(String category)
    {
        for (int i = 0; i < SUPPLY_CATEGORIES.length; i++)
        {
            if (SUPPLY_CATEGORIES[i].equals(category))
            {
                return i;
            }
        }
        return -1;
    }

    private static int getPoolValue(int[] pools, String category) throws InterruptedException
    {
        int index = getSupplyCategoryIndex(category);
        if (index < 0 || pools == null || index >= pools.length)
        {
            return 0;
        }
        return Math.max(0, pools[index]);
    }

    private static void validateCategoryDemandTable(obj_id sharedStateObj) throws InterruptedException
    {
        if (isIdValid(sharedStateObj) && sharedStateObj != obj_id.NULL_ID && exists(sharedStateObj))
        {
            int version = hasObjVar(sharedStateObj, OBJVAR_CATEGORY_TABLE_VALIDATION + ".version") ? getIntObjVar(sharedStateObj, OBJVAR_CATEGORY_TABLE_VALIDATION + ".version") : 0;
            if (version >= CATEGORY_TABLE_VALIDATION_VERSION)
            {
                return;
            }
        }

        for (int i = 0; i < SUPPLY_CATEGORIES.length; i++)
        {
            String category = SUPPLY_CATEGORIES[i];
            String rootCategory = vendor_lib.getEconomyCategoryRoot(category);
            dictionary row = dataTableGetRow(DATATABLE_CATEGORY_DEMAND, category);
            if ((row == null || row.isEmpty()) && !category.equals(rootCategory))
            {
                row = dataTableGetRow(DATATABLE_CATEGORY_DEMAND, rootCategory);
            }
            if (row == null || row.isEmpty())
            {
                CustomerServiceLog("economy_stabilizer", "STAB_TABLE_WARN_DEMAND category=" + category + " fallback=misc");
            }
        }

        if (isIdValid(sharedStateObj) && sharedStateObj != obj_id.NULL_ID && exists(sharedStateObj))
        {
            setObjVar(sharedStateObj, OBJVAR_CATEGORY_TABLE_VALIDATION + ".version", CATEGORY_TABLE_VALIDATION_VERSION);
            setObjVar(sharedStateObj, OBJVAR_CATEGORY_TABLE_VALIDATION + ".time", getGameTime());
        }
    }

    private static String getModeForScore(int score) throws InterruptedException
    {
        String[] modes = new String[]
        {
            MODE_RECOVERY,
            MODE_BALANCED,
            MODE_INFLATION_CONTROL
        };

        for (int i = 0; i < modes.length; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE_ECONOMY_MODES, modes[i]);
            if (row == null || row.isEmpty())
            {
                continue;
            }
            int minScore = row.getInt("minScore");
            int maxScore = row.getInt("maxScore");
            if (score >= minScore && score <= maxScore)
            {
                return modes[i];
            }
        }

        return MODE_BALANCED;
    }

    private static int getModeTuningInt(String mode, String column, int fallback) throws InterruptedException
    {
        dictionary row = dataTableGetRow(DATATABLE_ECONOMY_MODES, mode);
        if (row == null || row.isEmpty())
        {
            return fallback;
        }
        return row.getInt(column);
    }

    public static String getActiveEconomyMode(obj_id contextObj, obj_id sharedStateObj) throws InterruptedException
    {
        if (isIdValid(sharedStateObj) && sharedStateObj != obj_id.NULL_ID && exists(sharedStateObj) && hasObjVar(sharedStateObj, OBJVAR_ECONOMY_MODE))
        {
            String mode = getStringObjVar(sharedStateObj, OBJVAR_ECONOMY_MODE);
            if (mode != null && mode.length() > 0)
            {
                return mode;
            }
        }
        if (isIdValid(contextObj) && contextObj != obj_id.NULL_ID && exists(contextObj) && hasObjVar(contextObj, OBJVAR_ECONOMY_MODE))
        {
            String mode = getStringObjVar(contextObj, OBJVAR_ECONOMY_MODE);
            if (mode != null && mode.length() > 0)
            {
                return mode;
            }
        }
        return MODE_BALANCED;
    }

    public static float getAuctionFeeMultiplier(obj_id contextObj, obj_id sharedStateObj) throws InterruptedException
    {
        return getModeAwareFeeMultiplier(contextObj, sharedStateObj, "auction", 0.90f, 1.00f, 1.15f, 0.70f, 1.50f, DEFAULT_MODE_ADJUST_COOLDOWN_SECONDS);
    }

    public static float getMaintenanceSinkMultiplier(obj_id contextObj, obj_id sharedStateObj) throws InterruptedException
    {
        return getModeAwareFeeMultiplier(contextObj, sharedStateObj, "maintenance", 0.85f, 1.00f, 1.20f, 0.65f, 1.65f, DEFAULT_MODE_ADJUST_COOLDOWN_SECONDS);
    }

    private static float getModeAwareFeeMultiplier(obj_id contextObj, obj_id sharedStateObj, String channel, float recoveryMultiplier, float balancedMultiplier, float inflationMultiplier, float minClamp, float maxClamp, int cooldownSeconds) throws InterruptedException
    {
        String desiredMode = getActiveEconomyMode(contextObj, sharedStateObj);
        if (!isIdValid(contextObj) || contextObj == obj_id.NULL_ID || !exists(contextObj))
        {
            return clampModeMultiplier(desiredMode, recoveryMultiplier, balancedMultiplier, inflationMultiplier, minClamp, maxClamp);
        }

        String modePath = OBJVAR_FEE_ADJUST_ROOT + "." + channel + ".mode";
        String changedPath = OBJVAR_FEE_ADJUST_ROOT + "." + channel + ".lastChanged";
        String multiplierPath = OBJVAR_FEE_ADJUST_ROOT + "." + channel + ".multiplier";
        int now = getGameTime();
        int safeCooldown = Math.max(0, cooldownSeconds);
        String activeMode = hasObjVar(contextObj, modePath) ? getStringObjVar(contextObj, modePath) : desiredMode;
        int lastChanged = hasObjVar(contextObj, changedPath) ? getIntObjVar(contextObj, changedPath) : 0;

        if (activeMode == null || activeMode.length() < 1)
        {
            activeMode = MODE_BALANCED;
        }
        if (!activeMode.equals(desiredMode) && (now - lastChanged) < safeCooldown)
        {
            desiredMode = activeMode;
        }
        else if (!activeMode.equals(desiredMode))
        {
            setObjVar(contextObj, modePath, desiredMode);
            setObjVar(contextObj, changedPath, now);
        }
        else if (!hasObjVar(contextObj, modePath))
        {
            setObjVar(contextObj, modePath, desiredMode);
            setObjVar(contextObj, changedPath, now);
        }

        float resolvedMultiplier = clampModeMultiplier(desiredMode, recoveryMultiplier, balancedMultiplier, inflationMultiplier, minClamp, maxClamp);
        float previousMultiplier = hasObjVar(contextObj, multiplierPath) ? getFloatObjVar(contextObj, multiplierPath) : resolvedMultiplier;
        float blendedMultiplier = previousMultiplier + ((resolvedMultiplier - previousMultiplier) * 0.5f);
        blendedMultiplier = Math.max(minClamp, Math.min(maxClamp, blendedMultiplier));
        setObjVar(contextObj, multiplierPath, blendedMultiplier);
        return blendedMultiplier;
    }

    private static float clampModeMultiplier(String mode, float recoveryMultiplier, float balancedMultiplier, float inflationMultiplier, float minClamp, float maxClamp) throws InterruptedException
    {
        float multiplier = balancedMultiplier;
        if (MODE_RECOVERY.equals(mode))
        {
            multiplier = recoveryMultiplier;
        }
        else if (MODE_INFLATION_CONTROL.equals(mode))
        {
            multiplier = inflationMultiplier;
        }
        return Math.max(minClamp, Math.min(maxClamp, multiplier));
    }

    private static int clamp(int value, int min, int max)
    {
        if (max < min)
        {
            int swap = min;
            min = max;
            max = swap;
        }
        return Math.max(min, Math.min(max, value));
    }
}
