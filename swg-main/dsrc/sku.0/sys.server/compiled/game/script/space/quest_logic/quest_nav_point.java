package script.space.quest_logic;

import script.dictionary;
import script.library.space_quest;
import script.library.space_utils;
import script.library.utils;
import script.obj_id;

public class quest_nav_point extends script.base_script
{
    public quest_nav_point() {}

    // ---- Config / keys ----
    private static final String LOGP = "[QuestNavPoint] ";
    private static final String KEY_TYPE_OVR   = "quest_point.type";   // optional objvar override ("nav","spawner",...)
    private static final String KEY_RETRY_SV   = "quest_nav_point.retry"; // scriptVar (non-persistent)
    private static final float  RETRY_DELAY_S  = 5.0f;   // seconds between retries
    private static final int    RETRY_MAX      = 12;     // ~1 minute total
    private static final String DEFAULT_TYPE   = "nav";  // fallback

    // Optional discovery hints used by quest_manager/spawner logic
    private static final String OBJVAR_SPAWNER_NAME = "strSpawnerName"; // present on spawners
    private static final String OBJVAR_NAV_NAME     = "nav_name";       // recommended label

    // ---- Engine: init / preload ----
    
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        // ensures we get OnPreloadComplete even for zone objects
        requestPreloadCompleteTrigger(self);
        // Also schedule a registration attempt shortly after init (in case manager already exists)
        messageTo(self, "doRegister", null, 0.5f, false);
        return SCRIPT_CONTINUE;
    }

    
    public int OnPreloadComplete(obj_id self) throws InterruptedException
    {
        // Primary registration path once the object is fully loaded
        registerSelf(self);
        return SCRIPT_CONTINUE;
    }

    // Optional resilience: if this script is reattached at runtime
    
    public int OnAttach(obj_id self) throws InterruptedException
    {
        requestPreloadCompleteTrigger(self);
        messageTo(self, "doRegister", null, 0.5f, false);
        return SCRIPT_CONTINUE;
    }

    // ---- Clean up: inform manager we’re going away (future-proof; safe if unhandled) ----
    
    public int OnDestroy(obj_id self) throws InterruptedException
    {
        notifyUnregister(self);
        return SCRIPT_CONTINUE;
    }

    
    public int OnAboutToBeTransferred(obj_id self, obj_id destContainer, obj_id transferer) throws InterruptedException
    {
        // If this nav point leaves the world/container, let the manager prune it
        notifyUnregister(self);
        return SCRIPT_CONTINUE;
    }

    // ---- Message: delayed/retry registration ----
    public int doRegister(obj_id self, dictionary params) throws InterruptedException
    {
        registerSelf(self);
        return SCRIPT_CONTINUE;
    }

    // ==========================================================
    // Core: register this object with the quest manager (robust)
    // ==========================================================
    private void registerSelf(obj_id self) throws InterruptedException
    {
        obj_id mgr = getNamedObject(space_quest.QUEST_MANAGER);
        if (!isIdValid(mgr))
        {
            // Retry with backoff (bounded)
            int tries = utils.getIntScriptVar(self, KEY_RETRY_SV);
            if (tries < RETRY_MAX)
            {
                utils.setScriptVar(self, KEY_RETRY_SV, tries + 1);
                debugServerConsoleMsg(self, LOGP + "Quest manager not found; retry " + (tries + 1) + "/" + RETRY_MAX);
                messageTo(self, "doRegister", null, RETRY_DELAY_S, false);
            }
            else
            {
                debugServerConsoleMsg(self, LOGP + "Giving up after " + RETRY_MAX + " retries (manager not found).");
            }
            return;
        }

        // Build registration payload
        dictionary out = new dictionary();
        out.put("point", self);
        out.put("type", resolveType(self)); // "nav" (default) or "spawner" if strSpawnerName exists
        // Extra context (manager safely ignores unknown keys)
        if (hasObjVar(self, OBJVAR_NAV_NAME))       out.put("name", getStringObjVar(self, OBJVAR_NAV_NAME));
        if (hasObjVar(self, OBJVAR_SPAWNER_NAME))   out.put("spawner", getStringObjVar(self, OBJVAR_SPAWNER_NAME));

        // Send (uses your existing helper which handles cross-object invocation)
        space_utils.notifyObject(mgr, "registerQuestLocation", out);

        // Success → clear retry counter
        utils.removeScriptVar(self, KEY_RETRY_SV);
        debugServerConsoleMsg(self, LOGP + "Registered with manager=" + mgr + " type=" + out.getString("type"));
    }

    // Determine registration type: explicit objvar wins; else infer from presence of spawner name
    private String resolveType(obj_id self) throws InterruptedException
    {
        String ovr = hasObjVar(self, KEY_TYPE_OVR) ? getStringObjVar(self, KEY_TYPE_OVR) : null;
        if (ovr != null && ovr.length() > 0) return ovr;
        if (hasObjVar(self, OBJVAR_SPAWNER_NAME))   return "spawner";
        return DEFAULT_TYPE;
    }

    // Politely tell manager we’re being removed (safe no-op if manager lacks a handler)
    private void notifyUnregister(obj_id self) throws InterruptedException
    {
        obj_id mgr = getNamedObject(space_quest.QUEST_MANAGER);
        if (!isIdValid(mgr)) return;

        dictionary out = new dictionary();
        out.put("point", self);
        out.put("type", resolveType(self));
        // If you later add quest_manager.unregisterQuestLocation, it will Just Work™
        space_utils.notifyObject(mgr, "unregisterQuestLocation", out);
        debugServerConsoleMsg(self, LOGP + "Unregister requested from manager=" + mgr);
    }
}

