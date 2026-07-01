package script.systems.loot;

import script.library.collection;
import script.library.loot;
import script.library.utils;
import script.menu_info;
import script.menu_info_types;
import script.obj_id;
import script.string_id;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SWG+ Rare Loot Chest (dynamic)
 * - Backward compatible: OnObjectMenuRequest / OnObjectMenuSelect / handleRareLootCollection unchanged
 * - New: dynamic rarity & pools (objvars), dupe/open guards, pity mechanic, safer spawning, rich logging
 */
public class rare_loot_chest extends script.base_script {

    // ----- UI -----
    private static final string_id SID_USE = new string_id("npe", "crate_use");
    private static final string_id SID_FULL_INV = new string_id("loot/loot", "inventory_full");
    private static final string_id SID_OPENED = new string_id("loot/loot", "crate_opened");
    private static final string_id SID_BUSY = new string_id("loot/loot", "crate_busy");

    // ----- ObjVar keys (optional configuration) -----
    private static final String OVR_RARITY          = "rls.rarity";           // int (1..N), overrides template parsing
    private static final String OVR_POOLS           = "rls.pools";            // comma list (e.g. "proto,artifact,legendary")
    private static final String OVR_MIN_ITEMS       = "rls.minItems";         // int
    private static final String OVR_MAX_ITEMS       = "rls.maxItems";         // int
    private static final String OVR_PITY_THRESHOLD  = "rls.pityThreshold";    // int, default 6
    private static final String OVR_LOG             = "rls.debug";            // 1 = extra logging

    // ----- ScriptVar / ObjVar runtime guards -----
    private static final String SVR_OPEN_LOCK       = "rls.open.lock";        // on chest: slicer/open lock
    private static final String OVR_OPENED          = "rls.opened";           // on chest: persistent opened flag
    private static final String PITY_PREFIX         = "rls.pity.";            // on player: e.g. rls.pity.3 -> count since last “top-pool” hit at rarity 3

    // ----- Defaults -----
    private static final int    DEFAULT_MIN_ITEMS   = 1;
    private static final int    DEFAULT_MAX_ITEMS   = 2;
    private static final int    DEFAULT_PITY        = 6; // guarantee top-pool at least every N chests of same rarity

    // =========================
    // Radial Menu
    // =========================
    
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException {
        // only usable when the chest is in the player's inventory (legacy behavior)
        if (utils.getContainingPlayer(self) == player) {
            mi.addRootMenu(menu_info_types.ITEM_USE, SID_USE);
        }
        return SCRIPT_CONTINUE;
    }

    // =========================
    // Selection / Open
    // =========================
    
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException {
        sendDirtyObjectMenuNotification(self);
        if (item != menu_info_types.ITEM_USE) {
            return SCRIPT_CONTINUE;
        }

        // Prevent double-activation (concurrency + persistence guards)
        if (utils.hasScriptVar(self, SVR_OPEN_LOCK)) {
            sendSystemMessage(player, SID_BUSY);
            return SCRIPT_CONTINUE;
        }
        if (hasObjVar(self, OVR_OPENED)) {
            // Already opened somehow; just destroy quietly for safety
            destroyObject(self);
            return SCRIPT_CONTINUE;
        }
        utils.setScriptVar(self, SVR_OPEN_LOCK, player);

        try {
            // Validate inventory container
            obj_id inv = utils.getInventoryContainer(player);
            if (!isIdValid(inv)) {
                sendSystemMessage(player, SID_FULL_INV);
                return SCRIPT_CONTINUE;
            }

            // Determine rarity & pools
            int rarityIndex = computeRarityIndex(self); // 0..N-1
            String[] pools = resolvePools(self, rarityIndex); // dynamic or fallback to CHEST_TYPES slice

            // Figure out item count (configurable)
            int minItems = hasObjVar(self, OVR_MIN_ITEMS) ? Math.max(0, getIntObjVar(self, OVR_MIN_ITEMS)) : DEFAULT_MIN_ITEMS;
            int maxItems = hasObjVar(self, OVR_MAX_ITEMS) ? Math.max(minItems, getIntObjVar(self, OVR_MAX_ITEMS)) : DEFAULT_MAX_ITEMS;
            int numberOfItems = rand(minItems, maxItems);

            // Pity logic: guarantee at least one from the "top" pool every N chests at this rarity
            boolean forceTop = shouldTriggerPity(player, rarityIndex, getPityThreshold(self));
            int topIndex = pools.length - 1; // last pool is the “best” for this rarity slice

            List<obj_id> items = new ArrayList<>();
            int safety = 0, maxSafety = numberOfItems * 5; // avoid infinite loops on failing spawns

            while (items.size() < numberOfItems && safety++ < maxSafety) {
                int pick = forceTop && items.isEmpty() ? topIndex : rand(0, pools.length - 1);
                String group = "rls/" + pools[pick] + "_loot";

                obj_id created = loot.makeRareLootItem(inv, group);
                if (isIdValid(created)) {
                    items.add(created);
                }
            }

            // If we never spawned anything, fail gracefully
            if (items.isEmpty()) {
                sendSystemMessage(player, SID_FULL_INV);
                log(self, "spawn failed: no items (inv=" + inv + ", pools=" + Arrays.toString(pools) + ")");
                return SCRIPT_CONTINUE;
            }

            // Update pity counters
            if (forceTop) {
                resetPity(player, rarityIndex);
            } else {
                // If any item came from top pool, reset; else increment
                boolean hitTop = containsTopPool(items, pools[topIndex]);
                if (hitTop) resetPity(player, rarityIndex); else incPity(player, rarityIndex);
            }

            // Show UI and finalize
            showLootBox(player, items.toArray(new obj_id[0]));
            sendSystemMessage(player, SID_OPENED);
            setObjVar(self, OVR_OPENED, 1);
            handleRareLootCollection(player, rarityIndex + 1);
            LOG("rare_loot", "Player (" + getName(player) + ":" + player + ") opened RLS chest rarity=" + safePoolName(pools, topIndex) + " idx=" + rarityIndex);

            destroyObject(self);
        } finally {
            utils.removeScriptVar(self, SVR_OPEN_LOCK);
        }
        return SCRIPT_CONTINUE;
    }

    // =========================
    // Collections (legacy method kept)
    // =========================
    private void handleRareLootCollection(obj_id player, int lootType) throws InterruptedException {
        String typeOpenedOne  = "rare_loot_opened_one_"  + lootType;
        String typeOpenedFive = "rare_loot_opened_five_" + lootType;

        modifyCollectionSlotValue(player, typeOpenedOne, 1);
        modifyCollectionSlotValue(player, typeOpenedFive, 1);

        // When the “five” counter hits 5, remove the completed collection (legacy behavior retained)
        if (getCollectionSlotValue(player, typeOpenedFive) == 5) {
            switch (lootType) {
                case 1: collection.removeCompletedCollection(player, "col_rare_loot_five");       break;
                case 2: collection.removeCompletedCollection(player, "col_exceptional_loot_five"); break;
                case 3: collection.removeCompletedCollection(player, "col_legendary_loot_five");   break;
            }
        }
    }

    // =========================
    // Helpers (new)
    // =========================
    private int computeRarityIndex(obj_id self) throws InterruptedException {
        // 1) explicit override
        if (hasObjVar(self, OVR_RARITY)) {
            int r = getIntObjVar(self, OVR_RARITY);
            return Math.max(0, r - 1);
        }
        // 2) parse from template name: object/tangible/item/rare_loot_chest_<N>.iff
        try {
            String template = getTemplateName(self);
            if (template != null) {
                int i = template.lastIndexOf('_');
                int j = template.lastIndexOf('.');
                if (i >= 0 && j > i) {
                    int parsed = Integer.parseInt(template.substring(i + 1, j));
                    return Math.max(0, parsed - 1);
                }
            }
        } catch (Exception ignored) {}
        // 3) fallback
        return 0;
    }

    private String[] resolvePools(obj_id self, int rarityIndex) throws InterruptedException {
        // 1) explicit pools override (comma separated)
        if (hasObjVar(self, OVR_POOLS)) {
            String csv = getStringObjVar(self, OVR_POOLS);
            if (csv != null && csv.trim().length() > 0) {
                String[] out = Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
                if (out.length > 0) return out;
            }
        }
        // 2) fallback to default CHEST_TYPES up to rarity
        String[] base = loot.CHEST_TYPES;
        if (base == null || base.length == 0) return new String[]{ "rare" }; // extreme fallback
        int hi = Math.min(rarityIndex, base.length - 1);
        String[] slice = new String[hi + 1];
        System.arraycopy(base, 0, slice, 0, hi + 1);
        return slice;
    }

    private boolean containsTopPool(List<obj_id> created, String topPoolName) throws InterruptedException {
        if (created == null || created.isEmpty()) return false;
        // Heuristic: we can’t easily recover which group was used post-create; instead
        // we accept forceTop ensures at least first item came from top; otherwise we mark pity++.
        // If you maintain an objvar on the spawned item indicating its source group, check it here.
        return false;
    }

    private int getPityThreshold(obj_id self) throws InterruptedException {
        return hasObjVar(self, OVR_PITY_THRESHOLD) ? Math.max(1, getIntObjVar(self, OVR_PITY_THRESHOLD)) : DEFAULT_PITY;
    }

    private boolean shouldTriggerPity(obj_id player, int rarityIndex, int threshold) throws InterruptedException {
        String key = PITY_PREFIX + rarityIndex;
        int cur = hasObjVar(player, key) ? getIntObjVar(player, key) : 0;
        return cur >= threshold;
    }

    private void resetPity(obj_id player, int rarityIndex) throws InterruptedException {
        removeObjVar(player, PITY_PREFIX + rarityIndex);
    }

    private void incPity(obj_id player, int rarityIndex) throws InterruptedException {
        String key = PITY_PREFIX + rarityIndex;
        int cur = hasObjVar(player, key) ? getIntObjVar(player, key) : 0;
        setObjVar(player, key, cur + 1);
    }

    private void log(obj_id self, String msg) throws InterruptedException {
        if (hasObjVar(self, OVR_LOG) && getIntObjVar(self, OVR_LOG) != 0) {
            debugServerConsoleMsg(self, "[RLS] " + msg);
        }
    }

    private String safePoolName(String[] pools, int idx) {
        if (pools == null || pools.length == 0) return "unknown";
        idx = Math.max(0, Math.min(idx, pools.length - 1));
        return pools[idx];
    }
}
