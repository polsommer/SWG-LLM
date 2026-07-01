package script.player;

import script.*;
import script.library.behavior_telemetry;

public class player_behavior_telemetry extends script.base_script
{
    public player_behavior_telemetry()
    {
    }

    public static final String VAR_SOCIAL_START = "behaviorTelemetry.socialStart";

    public int OnAttach(obj_id self) throws InterruptedException
    {
        messageTo(self, "behaviorTelemetryPeriodicBuild", null, 120, false);
        return SCRIPT_CONTINUE;
    }

    public int OnLocomotionChanged(obj_id self, int newLocomotion, int oldLocomotion) throws InterruptedException
    {
        String movementToken = "movement_walk";
        if (newLocomotion == LOCOMOTION_RUNNING)
        {
            movementToken = "movement_run";
        }
        else if (newLocomotion == LOCOMOTION_RIDING_CREATURE)
        {
            movementToken = "movement_mount";
        }
        else if (newLocomotion == LOCOMOTION_KNOCKED_DOWN)
        {
            movementToken = "movement_knockdown";
        }
        if (oldLocomotion == LOCOMOTION_RUNNING && newLocomotion == LOCOMOTION_RUNNING)
        {
            movementToken = "movement_loop";
        }
        behavior_telemetry.recordActivityEvent(self, "movement", movementToken, 4);
        return SCRIPT_CONTINUE;
    }

    public int OnEnteredCombat(obj_id self) throws InterruptedException
    {
        behavior_telemetry.recordActivityEvent(self, "combat", "target_priority_open", 20);
        return SCRIPT_CONTINUE;
    }

    public int OnCraftedPrototype(obj_id self, obj_id prototypeObject, draft_schematic manufacturingSchematic) throws InterruptedException
    {
        behavior_telemetry.recordActivityEvent(self, "crafting", "skill_usage_craft_open", 10);
        behavior_telemetry.recordActivityEvent(self, "crafting", "crafted_prototype", 10);
        return SCRIPT_CONTINUE;
    }

    public int OnSpeaking(obj_id self, String text) throws InterruptedException
    {
        if (text != null && text.length() > 180)
        {
            behavior_telemetry.recordActivityEvent(self, "social", "abusive_chat_rate", 60);
            return SCRIPT_CONTINUE;
        }
        behavior_telemetry.recordActivityEvent(self, "social", "social_chat_ping", 15);
        behavior_telemetry.recordActivityEvent(self, "social", "social_interaction_ping", 15);
        if (!hasObjVar(self, VAR_SOCIAL_START))
        {
            setObjVar(self, VAR_SOCIAL_START, getGameTime());
        }
        return SCRIPT_CONTINUE;
    }

    public int OnObjVarChanged(obj_id self, String name) throws InterruptedException
    {
        if (name == null)
        {
            return SCRIPT_CONTINUE;
        }
        if (name.startsWith("crafting.session") || name.startsWith("crafting.tool"))
        {
            behavior_telemetry.recordActivityEvent(self, "crafting", "skill_usage_order_step", 6);
            behavior_telemetry.recordActivityEvent(self, "crafting", "crafting_session_touch", 6);
        }
        if (name.startsWith("vendor.") || name.startsWith("bazaar."))
        {
            behavior_telemetry.recordActivityEvent(self, "shopping", "shopping_routine_visit", 8);
            behavior_telemetry.recordActivityEvent(self, "vendor", "vendor_terminal_use", 8);
        }
        return SCRIPT_CONTINUE;
    }

    public int behaviorTelemetryPeriodicBuild(obj_id self, dictionary params) throws InterruptedException
    {
        if (hasObjVar(self, VAR_SOCIAL_START))
        {
            int dwell = getGameTime() - getIntObjVar(self, VAR_SOCIAL_START);
            if (dwell > 20)
            {
                behavior_telemetry.recordSocialDwell(self, dwell);
            }
            removeObjVar(self, VAR_SOCIAL_START);
        }
        behavior_telemetry.rebuildBehaviorProfile(self);
        messageTo(self, "behaviorTelemetryPeriodicBuild", null, behavior_telemetry.getBehaviorProfileUpdateFrequencySeconds(), false);
        return SCRIPT_CONTINUE;
    }
}
