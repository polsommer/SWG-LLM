package script.waypoint;

import script.*;
import script.library.utils;

public class base_waypoint extends script.base_script
{
    public base_waypoint() {}

    // ===== Constants / objvar keys =====
    public static final string_id MNU_SET_COLOR = new string_id("sui", "set_color");
    private static final String OBJVAR_COLOR       = "wp.color";
    private static final String OBJVAR_CLIENTPATH  = "hasClientPath"; // legacy key already used in your code

    // ===== Engine event: build the radial menu =====
   
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        // Add top-level "Set Color"
        final int root = mi.addRootMenu(menu_info_types.SERVER_MENU1, MNU_SET_COLOR);

        // Get available color ids (string table keys). Be defensive.
        final String[] colors = safeColors();
        if (colors.length == 0)
        {
            // If no colors defined, still return cleanly.
            return SCRIPT_CONTINUE;
        }

        // Add a sub-menu entry for each color
        for (int i = 0; i < colors.length; i++)
        {
            // Items are SERVER_MENU1 + i + 1 (kept the same mapping as your original code)
            mi.addSubMenu(root, menu_info_types.SERVER_MENU1 + i + 1, new string_id("sui", colors[i]));
        }

        return SCRIPT_CONTINUE;
    }

    // ===== Engine event: a radial menu item was chosen =====
   
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        final String[] colors = safeColors();
        if (colors.length == 0) return SCRIPT_CONTINUE;

        // Original mapping: item ∈ (SERVER_MENU1, SERVER_MENU1 + colors.length + 1)
        final int min = menu_info_types.SERVER_MENU1 + 1;
        final int maxExclusive = menu_info_types.SERVER_MENU1 + colors.length + 1;
        if (item > menu_info_types.SERVER_MENU1 && item < maxExclusive)
        {
            final int idx = item - menu_info_types.SERVER_MENU1 - 1;
            if (idx >= 0 && idx < colors.length)
            {
                final String colorKey = colors[idx];
                setWaypointColor(self, colorKey);      // engine-native color set
                setObjVar(self, OBJVAR_COLOR, colorKey); // persist selection for reapply
            }
        }
        return SCRIPT_CONTINUE;
    }

    // ===== Engine event: object destroyed / moved; clear any client path =====
   
    public int OnDestroy(obj_id self) throws InterruptedException
    {
        clearClientPath(self);
        return SCRIPT_CONTINUE;
    }


    public int OnAboutToBeTransferred(obj_id self, obj_id destContainer, obj_id transferer) throws InterruptedException
    {
        clearClientPath(self);
        return SCRIPT_CONTINUE;
    }

    // ===== Engine event: toggle waypoint active state (already wired to native) =====
   
    public int OnSetWaypointActive(obj_id self, dictionary params) throws InterruptedException
    {
        final boolean isActive = params.getBoolean("isActive");
        final obj_id waypoint  = params.getObjId("waypoint");
        if (isIdValid(waypoint))
        {
            _setWaypointActiveNative(waypoint, isActive);
        }
        return SCRIPT_CONTINUE;
    }

    // ===== Engine event: set a region for the waypoint (kept, but safely no-op without native support) =====
   
    public int OnSetWaypointRegion(obj_id self, dictionary params) throws InterruptedException
    {
        // Parameters are present in your original signature. Keep method for engine calls.
        final obj_id w = params.getObjId("waypoint");
        final region r = params.getRegion("region");
        // If you wire a native region visualizer later, call it here.
        // For now: do nothing safely (legacy behavior).
        return SCRIPT_CONTINUE;
    }

    // ===== Legacy misspelled visible handler (kept); add a corrected alias below =====
   
    public int OnSetWaypoinVisible(obj_id self, dictionary params) throws InterruptedException
    {
        // No native call in the original. Keep it a safe no-op for compatibility.
        // If you later add native toggle visibility, you can forward here.
        // Persist desired visibility if provided, so we can re-apply when support exists.
        if (params.containsKey("isVisible") && params.containsKey("waypoint"))
        {
            final boolean isVisible = params.getBoolean("isVisible");
            // Optional: store intended visibility for future use
            setObjVar(self, "wp.visible", isVisible);
        }
        return SCRIPT_CONTINUE;
    }

    // ===== New: correct-spelling alias that forwards to the legacy handler (non-breaking) =====
    public int OnSetWaypointVisible(obj_id self, dictionary params) throws InterruptedException
    {
        return OnSetWaypoinVisible(self, params);
    }

    // ===== New: re-apply saved color on (re)attach/init so color survives reloads/moves =====
   
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        reapplySavedColor(self);
        return SCRIPT_CONTINUE;
    }

   
    public int OnAttach(obj_id self) throws InterruptedException
    {
        reapplySavedColor(self);
        return SCRIPT_CONTINUE;
    }

    // ===== Helpers =====

    /** Clear the client path for this object and remove the tracking objvar if present. */
    public void clearClientPath(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, OBJVAR_CLIENTPATH))
        {
            final obj_id target = getObjIdObjVar(self, OBJVAR_CLIENTPATH);
            if (isIdValid(target))
            {
                destroyClientPath(target);
            }
            removeObjVar(self, OBJVAR_CLIENTPATH); // make sure we don’t leak stale state
        }
    }

    /** Try to reapply the saved color (if any) from OBJVAR_COLOR. */
    private void reapplySavedColor(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_COLOR)) return;
        final String saved = getStringObjVar(self, OBJVAR_COLOR);
        if (saved == null || saved.length() == 0) return;

        // Only apply if it’s still a valid color in the current table
        final int idx = colorIndex(saved);
        if (idx >= 0)
        {
            setWaypointColor(self, saved);
        }
        else
        {
            // If config changed and color no longer exists, drop the objvar so we don’t reapply bad values.
            removeObjVar(self, OBJVAR_COLOR);
        }
    }

    /** Return a defensive copy of the available color keys from utils, or empty array if unavailable. */
    private String[] safeColors()
    {
        try
        {
            if (utils.WAYPOINT_COLORS == null || utils.WAYPOINT_COLORS.length == 0) return new String[0];
            // defensive copy so we don’t risk accidental external mutation
            String[] out = new String[utils.WAYPOINT_COLORS.length];
            System.arraycopy(utils.WAYPOINT_COLORS, 0, out, 0, utils.WAYPOINT_COLORS.length);
            return out;
        }
        catch (Throwable t)
        {
            // Any unexpected linkage/class init error → behave gracefully.
            return new String[0];
        }
    }

    /** Find the index of a color key in the current table (case-sensitive to match string_id keys). */
    private int colorIndex(String key)
    {
        final String[] colors = safeColors();
        for (int i = 0; i < colors.length; i++)
        {
            if (key.equals(colors[i])) return i;
        }
        return -1;
    }
}

