package script.systems.loot;

import script.obj_id;
import script.library.collection;

/**
 * SWG+ rare item attribute decorator (backward compatible).
 * Adds dynamic lines when corresponding objvars exist.
 */
public class rare_item extends script.base_script {

    public int OnGetAttributes(obj_id self, obj_id player, String[] names, String[] attribs) throws InterruptedException {
        if (names == null || attribs == null || names.length != attribs.length) {
            return SCRIPT_CONTINUE;
        }

        // Legacy category line
        addAttr(names, attribs, "rare_loot_category", color("#ed8d16", "Rare Item"));

        // Rarity
        int rarity = getRarity(self);
        if (rarity > 1) addAttr(names, attribs, "rare_loot_tier", coloredRarity(rarity));

        // Source
        String pool = getStringObjVarSafe(self, "rls.pool");
        String chest = getStringObjVarSafe(self, "rls.sourceChest");
        String source = !pool.isEmpty() ? pool : chest;
        if (!source.isEmpty()) addAttr(names, attribs, "rare_loot_source", source);

        // Binding
        boolean bound = hasObjVar(self, "noTrade") || hasObjVar(self, "item.bind") || hasObjVar(self, "item.nodrop");
        addAttr(names, attribs, "item_binding", bound ? color("#ff6666", "Bound") : color("#66ff88", "Tradable"));

        // Collection progress (uses long to avoid narrowing)
        String colSlot = getStringObjVarSafe(self, "collection.slot");
        if (!colSlot.isEmpty() && player != null) {
            long cur = collection.getCollectionSlotValue(player, colSlot);
            long max = hasObjVar(self, "collection.max") ? Math.max(1L, (long)getIntObjVar(self, "collection.max")) : 1L;
            String val = (cur >= max)
                    ? color("#66ff88", cur + "/" + max + " (Complete)")
                    : (cur + "/" + max);
            addAttr(names, attribs, "collection_progress", val);
        }

        // Drop time
        if (hasObjVar(self, "rls.dropTime")) {
            int dropped = getIntObjVar(self, "rls.dropTime");
            int now = getGameTime();
            if (dropped > 0 && now >= dropped) addAttr(names, attribs, "drop_time", timeAgo(now - dropped));
        }

        // UID
        if (hasObjVar(self, "rls.uid")) {
            String uid = getStringObjVarSafe(self, "rls.uid");
            if (uid.isEmpty()) uid = String.valueOf(getIntObjVar(self, "rls.uid"));
            if (!uid.isEmpty()) addAttr(names, attribs, "unique_id", uid);
        }

        // Pity indicator
        if (asBool(self, "rls.pityUsed")) addAttr(names, attribs, "luck_bonus", color("#ffd700", "Pity Bonus Applied"));

        // Affixes
        String aff = getStringObjVarSafe(self, "rls.affixes");
        if (!aff.isEmpty()) {
            String[] parts = splitCsv(aff);
            for (int i = 0; i < parts.length; i++) {
                if (!addAttr(names, attribs, "affix_" + (i + 1), parts[i])) break;
            }
        }

        return SCRIPT_CONTINUE;
    }

    // ===== helpers (unchanged) =====
    private static int getRarity(obj_id self) throws InterruptedException {
        if (hasObjVar(self, "rls.rarity")) {
            int r = getIntObjVar(self, "rls.rarity");
            return (r < 1) ? 1 : r;
        }
        return 1;
    }
    private static String coloredRarity(int rarity) {
        if (rarity >= 3) return color("#ffd700", "Legendary");
        if (rarity == 2) return color("#b784ff", "Exceptional");
        return color("#ed8d16", "Rare");
    }
    private static String color(String hex, String text) { return "\\#" + hex.replace("#", "") + text; }
    private static String getStringObjVarSafe(obj_id self, String key) throws InterruptedException {
        return hasObjVar(self, key) ? nonNull(getStringObjVar(self, key)) : "";
    }
    private static String nonNull(String s) { return (s == null) ? "" : s.trim(); }
    private static boolean asBool(obj_id self, String key) throws InterruptedException {
        if (!hasObjVar(self, key)) return false;
        try { return getIntObjVar(self, key) != 0; }
        catch (Throwable t) {
            try { String v = getStringObjVar(self, key); return "true".equalsIgnoreCase(v) || "1".equals(v); }
            catch (Throwable ignored) { return true; }
        }
    }
    private static String[] splitCsv(String csv) {
        java.util.Vector out = new java.util.Vector();
        int start = 0;
        for (int i = 0; i <= csv.length(); i++) {
            if (i == csv.length() || csv.charAt(i) == ',') {
                String seg = csv.substring(start, i).trim();
                if (seg.length() > 0) out.addElement(seg);
                start = i + 1;
            }
        }
        String[] arr = new String[out.size()];
        out.copyInto(arr);
        return arr;
    }
    private static String timeAgo(int seconds) {
        if (seconds < 60) return seconds + "s ago";
        int mins = seconds / 60;
        if (mins < 60) return mins + "m ago";
        int hrs = mins / 60;
        if (hrs < 48) return hrs + "h ago";
        int days = hrs / 24;
        return days + "d ago";
    }
    private static boolean addAttr(String[] names, String[] attribs, String name, String value) {
        int idx = firstFree(names, attribs);
        if (idx < 0) return false;
        names[idx] = name;
        attribs[idx] = value;
        return true;
    }
    private static int firstFree(String[] names, String[] attribs) {
        int n = Math.min(names.length, attribs.length);
        for (int i = 0; i < n; i++) if (names[i] == null || names[i].length() == 0) return i;
        return -1;
    }
}
