package script.systems.economy;

import script.dictionary;
import script.library.ai_lib;
import script.library.economy_stabilizer;
import script.library.utils;
import script.library.vendor_lib;
import script.location;
import script.obj_id;

import java.util.Vector;

public class vendor_stabilizer_heartbeat extends script.base_script
{
    public vendor_stabilizer_heartbeat()
    {
    }

    public static final String VAR_ROOT = "economy.vendorHeartbeat";
    public static final String VAR_INTERVAL_SECONDS = VAR_ROOT + ".intervalSeconds";
    public static final String VAR_SCAN_RADIUS = VAR_ROOT + ".scanRadius";
    public static final String VAR_TERMINAL_LIMIT = VAR_ROOT + ".maxTerminalsPerPulse";
    public static final String VAR_NPC_SCAN_RADIUS = VAR_ROOT + ".npcScanRadius";

    public static final int DEFAULT_INTERVAL_SECONDS = 120;
    public static final float DEFAULT_SCAN_RADIUS = 4096.0f;
    public static final int DEFAULT_TERMINAL_LIMIT = 24;
    public static final float DEFAULT_NPC_SCAN_RADIUS = 160.0f;

    public static final String MSG_HEARTBEAT = "vendorEconomyHeartbeat";

    public int OnAttach(obj_id self) throws InterruptedException
    {
        scheduleHeartbeat(self, 10.0f);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        scheduleHeartbeat(self, 15.0f);
        return SCRIPT_CONTINUE;
    }

    public int vendorEconomyHeartbeat(obj_id self, dictionary params) throws InterruptedException
    {
        int intervalSeconds = Math.max(30, getIntObjVarOrDefault(self, VAR_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS));
        scheduleHeartbeat(self, intervalSeconds);

        if (!isIdValid(self) || self == obj_id.NULL_ID || !exists(self))
        {
            return SCRIPT_CONTINUE;
        }

        location center = getLocation(self);
        if (center == null || center.area == null)
        {
            return SCRIPT_CONTINUE;
        }

        float scanRadius = getFloatObjVarOrDefault(self, VAR_SCAN_RADIUS, DEFAULT_SCAN_RADIUS);
        scanRadius = Math.max(128.0f, scanRadius);

        int terminalLimit = Math.max(1, getIntObjVarOrDefault(self, VAR_TERMINAL_LIMIT, DEFAULT_TERMINAL_LIMIT));
        float npcScanRadius = getFloatObjVarOrDefault(self, VAR_NPC_SCAN_RADIUS, DEFAULT_NPC_SCAN_RADIUS);
        npcScanRadius = Math.max(32.0f, npcScanRadius);

        obj_id[] terminals = collectTerminals(center, scanRadius);
        int processed = 0;

        for (int i = 0; i < terminals.length && processed < terminalLimit; i++)
        {
            obj_id terminal = terminals[i];
            if (!isIdValid(terminal) || terminal == obj_id.NULL_ID || !exists(terminal))
            {
                continue;
            }
            if (!isTerminalEligibleForListing(terminal))
            {
                continue;
            }

            processed++;
            processTerminalHeartbeat(self, terminal, npcScanRadius);
        }

        CustomerServiceLog("economy_stabilizer", "STAB_HB_PULSE area=" + center.area + " scanned=" + terminals.length + " processed=" + processed + " radius=" + scanRadius);
        return SCRIPT_CONTINUE;
    }

    private void processTerminalHeartbeat(obj_id self, obj_id terminalOrVendor, float npcScanRadius) throws InterruptedException
    {
        obj_id sharedState = vendor_lib.getEconomyStateObject(terminalOrVendor);
        if (!isIdValid(sharedState) || sharedState == obj_id.NULL_ID || !exists(sharedState))
        {
            sharedState = vendor_lib.getEconomyStateObject(self);
        }
        if (!isIdValid(sharedState) || sharedState == obj_id.NULL_ID || !exists(sharedState))
        {
            CustomerServiceLog("economy_stabilizer", "STAB_HB_SKIP_STATE vendor=" + terminalOrVendor + " detail=missing_shared_state");
            return;
        }

        location center = getLocation(terminalOrVendor);
        if (center == null || center.area == null)
        {
            CustomerServiceLog("economy_stabilizer", "STAB_HB_SKIP_STATE vendor=" + terminalOrVendor + " detail=invalid_location");
            return;
        }

        dictionary economyMetrics = economy_stabilizer.tickEconomy(sharedState, center, 256.0f, 120);
        if (economyMetrics == null || economyMetrics.isEmpty())
        {
            economyMetrics = economy_stabilizer.evaluateEconomy(center, 256.0f);
        }
        if (economyMetrics == null)
        {
            economyMetrics = new dictionary();
        }

        String economyMode = economyMetrics.getString("mode");
        if (economyMode == null || economyMode.length() < 1)
        {
            economyMode = economy_stabilizer.MODE_BALANCED;
        }
        int onlineCount = Math.max(0, economyMetrics.getInt("onlineCount"));
        int shortageCount = Math.max(0, economyMetrics.getInt("shortageCount"));

        String shortageCategory = vendor_lib.getPreferredShortageCategoryForVendor(terminalOrVendor);
        String shortageLabel = vendor_lib.getShortageCategoryLabel(shortageCategory);
        int shortageDeficit = Math.max(0, vendor_lib.getShortageDeficitMagnitudeForVendor(terminalOrVendor, shortageCategory));

        int spawnCapDaily = 4;
        float floorMultiplier = 0.87f;
        float ceilingMultiplier = 1.16f;
        int staleSeconds = 21600;

        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            spawnCapDaily = 6;
            floorMultiplier = 0.80f;
            ceilingMultiplier = 1.30f;
            staleSeconds = 10800;
        }
        else if (economy_stabilizer.MODE_INFLATION_CONTROL.equals(economyMode))
        {
            spawnCapDaily = 2;
            floorMultiplier = 0.93f;
            ceilingMultiplier = 1.08f;
            staleSeconds = 32400;
        }

        spawnCapDaily += Math.min(4, (shortageCount + 1) / 2);
        spawnCapDaily += Math.min(3, shortageDeficit / 6);
        if (onlineCount > 200)
        {
            spawnCapDaily += 1;
        }
        floorMultiplier = Math.max(0.70f, floorMultiplier - Math.min(0.08f, (shortageDeficit * 0.004f)));
        ceilingMultiplier = Math.min(1.45f, ceilingMultiplier + Math.min(0.16f, (shortageDeficit * 0.008f)));
        staleSeconds = Math.max(7200, staleSeconds - Math.min(7200, shortageDeficit * 240));
        if (ceilingMultiplier < floorMultiplier)
        {
            ceilingMultiplier = floorMultiplier;
        }

        int day = getGameTime() / 86400;
        String dailyPath = vendor_lib.AI_LISTING_TRACK_DAILY_ROOT + "." + day + "." + shortageCategory;
        int alreadySpawned = hasObjVar(terminalOrVendor, dailyPath) ? Math.max(0, getIntObjVar(terminalOrVendor, dailyPath)) : 0;
        int remainingDaily = Math.max(0, spawnCapDaily - alreadySpawned);
        if (remainingDaily < 1)
        {
            CustomerServiceLog("economy_stabilizer", "STAB_HB_SKIP_CAP vendor=" + terminalOrVendor + " category=" + shortageCategory + " spawned=" + alreadySpawned + " cap=" + spawnCapDaily);
            return;
        }

        int attemptBudget = 1 + Math.min(4, shortageDeficit / 5);
        if (shortageCount > 1)
        {
            attemptBudget++;
        }
        if (economy_stabilizer.MODE_RECOVERY.equals(economyMode))
        {
            attemptBudget++;
        }
        attemptBudget = Math.max(1, Math.min(6, attemptBudget));
        attemptBudget = Math.min(attemptBudget, remainingDaily);

        obj_id seller = selectNearbyAdaptiveSeller(self, terminalOrVendor, npcScanRadius);
        if (!isIdValid(seller) || seller == obj_id.NULL_ID || !exists(seller))
        {
            CustomerServiceLog("economy_stabilizer", "STAB_HB_SKIP_SELLER vendor=" + terminalOrVendor + " category=" + shortageCategory + " detail=no_adaptive_npc");
            return;
        }

        dictionary listingParams = new dictionary();
        listingParams.put("seller", seller);
        listingParams.put("step", "heartbeat_listing");
        listingParams.put("source", vendor_lib.AI_LISTING_SOURCE);
        listingParams.put("listingOrigin", vendor_lib.AI_LISTING_SOURCE);
        listingParams.put("economyState", sharedState);
        listingParams.put("listingCategory", shortageCategory);
        listingParams.put("shortageCategoryLabel", shortageLabel);
        listingParams.put("economyMode", economyMode);
        listingParams.put("onlineCount", onlineCount);
        listingParams.put("shortageCount", shortageCount);
        int essentialShortageFlag = Math.max(0, economyMetrics.getInt("shortageEssential." + shortageCategory));
        listingParams.put("essentialShortage", essentialShortageFlag);
        listingParams.put("shortageEssential." + shortageCategory, essentialShortageFlag);
        listingParams.put("shortageDeficitMagnitude", shortageDeficit);
        listingParams.put("spawnCapDaily", spawnCapDaily);
        listingParams.put("floorMultiplier", floorMultiplier);
        listingParams.put("ceilingMultiplier", ceilingMultiplier);
        listingParams.put("staleSeconds", staleSeconds);
        listingParams.put("baseListingFee", 20);
        listingParams.put("feeWaived", 1);

        boolean created = false;
        int attempts = 0;
        for (int attempt = 0; attempt < attemptBudget; attempt++)
        {
            attempts++;
            created = vendor_lib.handleNpcVendorListing(terminalOrVendor, listingParams);
            if (created)
            {
                break;
            }
        }

        String code = created ? "STAB_HB_LIST_CREATED" : "STAB_HB_LIST_FAILED";
        CustomerServiceLog("economy_stabilizer", code + " vendor=" + terminalOrVendor + " actor=" + seller + " category=" + shortageCategory + " attempts=" + attempts + " remainingDaily=" + remainingDaily);
    }

    private obj_id selectNearbyAdaptiveSeller(obj_id heartbeatController, obj_id terminalOrVendor, float npcScanRadius) throws InterruptedException
    {
        location loc = getLocation(terminalOrVendor);
        if (loc == null || loc.area == null)
        {
            return obj_id.NULL_ID;
        }

        float[] searchRadii = new float[]{
            Math.max(32.0f, npcScanRadius),
            Math.max(96.0f, npcScanRadius * 2.0f),
            Math.max(256.0f, npcScanRadius * 4.0f)
        };

        for (int pass = 0; pass < searchRadii.length; pass++)
        {
            obj_id[] npcs = getAllObjectsWithScript(loc, searchRadii[pass], "npc.simulation.adaptive_archetype_controller");
            if (npcs == null || npcs.length < 1)
            {
                continue;
            }
            obj_id best = chooseBestAdaptiveSeller(npcs, loc);
            if (isIdValid(best) && best != obj_id.NULL_ID && exists(best))
            {
                return best;
            }
        }

        if (isIdValid(heartbeatController) && heartbeatController != obj_id.NULL_ID && exists(heartbeatController))
        {
            location hub = getLocation(heartbeatController);
            if (hub != null && hub.area != null)
            {
                float bazaarRadius = getFloatObjVarOrDefault(heartbeatController, VAR_SCAN_RADIUS, DEFAULT_SCAN_RADIUS);
                bazaarRadius = Math.max(256.0f, bazaarRadius);
                obj_id[] zoneNpcs = getAllObjectsWithScript(hub, bazaarRadius, "npc.simulation.adaptive_archetype_controller");
                if (zoneNpcs != null)
                {
                    obj_id best = chooseBestAdaptiveSeller(zoneNpcs, loc);
                    if (isIdValid(best) && best != obj_id.NULL_ID && exists(best))
                    {
                        return best;
                    }
                }
            }
        }
        return obj_id.NULL_ID;
    }

    private obj_id chooseBestAdaptiveSeller(obj_id[] candidates, location anchor) throws InterruptedException
    {
        if (candidates == null || candidates.length < 1)
        {
            return obj_id.NULL_ID;
        }
        obj_id best = obj_id.NULL_ID;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < candidates.length; i++)
        {
            obj_id candidate = candidates[i];
            int score = scoreAdaptiveSellerCandidate(candidate, anchor);
            if (score > bestScore)
            {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int scoreAdaptiveSellerCandidate(obj_id candidate, location anchor) throws InterruptedException
    {
        if (!isIdValid(candidate) || candidate == obj_id.NULL_ID || !exists(candidate))
        {
            return Integer.MIN_VALUE;
        }
        if (!ai_lib.isNpc(candidate) || isDead(candidate) || isIncapacitated(candidate))
        {
            return Integer.MIN_VALUE;
        }
        obj_id inv = utils.getInventoryContainer(candidate);
        if (!isIdValid(inv) || inv == obj_id.NULL_ID || !exists(inv))
        {
            return Integer.MIN_VALUE;
        }

        int score = 1000;
        int created = hasObjVar(candidate, "npc.simProfile.vendor.listing.listing_created") ? Math.max(0, getIntObjVar(candidate, "npc.simProfile.vendor.listing.listing_created")) : 0;
        int failed = hasObjVar(candidate, "npc.simProfile.vendor.listing.listing_failed") ? Math.max(0, getIntObjVar(candidate, "npc.simProfile.vendor.listing.listing_failed")) : 0;
        score += Math.min(250, created * 4);
        score -= Math.min(300, failed * 6);

        if (hasObjVar(candidate, "npc.simProfile.vendor.listing.lastSuccess") && getIntObjVar(candidate, "npc.simProfile.vendor.listing.lastSuccess") == 1)
        {
            score += 80;
        }

        if (anchor != null)
        {
            location target = getLocation(candidate);
            if (target != null && target.area != null && target.area.equals(anchor.area))
            {
                score -= Math.min(300, (int)getDistance(anchor, target));
            }
        }

        return score;
    }

    private boolean isTerminalEligibleForListing(obj_id terminal) throws InterruptedException
    {
        if (!isIdValid(terminal) || terminal == obj_id.NULL_ID || !exists(terminal))
        {
            return false;
        }
        if (hasScript(terminal, "terminal.bazaar") || hasScript(terminal, "terminal.vendor") || hasScript(terminal, "terminal.npc_vendor") || hasScript(terminal, "terminal.nonvendor"))
        {
            return true;
        }
        return false;
    }

    private obj_id[] collectTerminals(location center, float scanRadius) throws InterruptedException
    {
        obj_id[] bazaars = getAllObjectsWithScript(center, scanRadius, "terminal.bazaar");
        obj_id[] vendors = getAllObjectsWithScript(center, scanRadius, "terminal.vendor");
        obj_id[] npcVendors = getAllObjectsWithScript(center, scanRadius, "terminal.npc_vendor");
        obj_id[] nonVendors = getAllObjectsWithScript(center, scanRadius, "terminal.nonvendor");

        Vector merged = new Vector();
        appendUniqueIds(merged, bazaars);
        appendUniqueIds(merged, vendors);
        appendUniqueIds(merged, npcVendors);
        appendUniqueIds(merged, nonVendors);

        obj_id[] output = new obj_id[merged.size()];
        merged.toArray(output);
        return output;
    }

    private void appendUniqueIds(Vector merged, obj_id[] input) throws InterruptedException
    {
        if (merged == null || input == null)
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

            boolean duplicate = false;
            for (int j = 0; j < merged.size(); j++)
            {
                obj_id existing = (obj_id)merged.get(j);
                if (existing == candidate)
                {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate)
            {
                merged.add(candidate);
            }
        }
    }

    private int getIntObjVarOrDefault(obj_id self, String path, int fallback) throws InterruptedException
    {
        if (!isIdValid(self) || self == obj_id.NULL_ID || !exists(self) || path == null || path.length() < 1)
        {
            return fallback;
        }
        if (!hasObjVar(self, path))
        {
            return fallback;
        }
        return getIntObjVar(self, path);
    }

    private float getFloatObjVarOrDefault(obj_id self, String path, float fallback) throws InterruptedException
    {
        if (!isIdValid(self) || self == obj_id.NULL_ID || !exists(self) || path == null || path.length() < 1)
        {
            return fallback;
        }
        if (!hasObjVar(self, path))
        {
            return fallback;
        }
        return getFloatObjVar(self, path);
    }

    private void scheduleHeartbeat(obj_id self, float delaySeconds) throws InterruptedException
    {
        if (!isIdValid(self) || self == obj_id.NULL_ID || !exists(self))
        {
            return;
        }
        messageTo(self, MSG_HEARTBEAT, null, Math.max(5.0f, delaySeconds), false);
    }
}
