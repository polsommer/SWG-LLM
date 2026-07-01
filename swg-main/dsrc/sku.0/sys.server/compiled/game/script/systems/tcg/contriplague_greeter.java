package script.systems.tcg;

import script.*;
import script.library.chat;
import script.library.vendor_lib;

public class contriplague_greeter extends script.base_script
{
    public contriplague_greeter()
    {
    }
    public static final String VOLUME_NAME = "contriplagueVolume";
    public static final String NEXT_TAUNT_TIME = "contriplague.nextTauntTime";
    public static final String HOLOGRAM_OBJVAR = "contriplague.hologram";
    public static final String HOLOGRAM_TEMPLATE = "object/mobile/hologram/human_male.iff";
    public static final float TAUNT_RADIUS = 8.0f;
    public static final float TAUNT_COOLDOWN = 12.0f;
    public int OnAttach(obj_id self) throws InterruptedException
    {
        setupContriplague(self);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setupContriplague(self);
        return SCRIPT_CONTINUE;
    }
    public int OnDetach(obj_id self) throws InterruptedException
    {
        cleanupHologram(self);
        return SCRIPT_CONTINUE;
    }
    public int OnTriggerVolumeEntered(obj_id self, String volumeName, obj_id breacher) throws InterruptedException
    {
        if (volumeName == null || !volumeName.equals(VOLUME_NAME))
        {
            return SCRIPT_CONTINUE;
        }
        if (!isValidId(breacher) || !exists(breacher) || !isPlayer(breacher))
        {
            return SCRIPT_CONTINUE;
        }
        int now = getGameTime();
        if (hasObjVar(self, NEXT_TAUNT_TIME) && getIntObjVar(self, NEXT_TAUNT_TIME) > now)
        {
            return SCRIPT_CONTINUE;
        }
        tauntTarget(self, breacher);
        setObjVar(self, NEXT_TAUNT_TIME, now + (int)TAUNT_COOLDOWN);
        return SCRIPT_CONTINUE;
    }
    public int OnDestroy(obj_id self) throws InterruptedException
    {
        cleanupHologram(self);
        return SCRIPT_CONTINUE;
    }
    private void setupContriplague(obj_id self) throws InterruptedException
    {
        setInvulnerable(self, true);
        setHologramType(self, HOLOGRAM_TYPE1_QUALITY3);
        if (!hasTriggerVolume(self, VOLUME_NAME))
        {
            createTriggerVolume(VOLUME_NAME, TAUNT_RADIUS, true);
        }
        ensureHologram(self);
    }
    private void tauntTarget(obj_id self, obj_id target) throws InterruptedException
    {
        obj_id speaker = getSpeaker(self);
        obj_id owner = getObjIdObjVar(self, vendor_lib.GREETER_OWNER_OBJVAR);
        String ownerName = getStringObjVar(self, "contriplague.ownerName");
        if ((ownerName == null || ownerName.equals("")) && isValidId(owner) && exists(owner))
        {
            ownerName = getFirstName(owner);
        }
        if (ownerName == null || ownerName.equals(""))
        {
            ownerName = "my generous patron";
        }
        String targetName = getFirstName(target);
        if (targetName == null || targetName.equals(""))
        {
            targetName = "traveler";
        }
        String[] taunts =
        {
            targetName + ", witness the glorious spending habits of " + ownerName + ".",
            ownerName + " funded this shimmering ego beacon, and honestly it was money well spent.",
            "Careful, " + targetName + ", standing near " + ownerName + "'s decor may cause envy.",
            "I am Contriplague, the proudest thing " + ownerName + " has placed in this house today.",
            targetName + ", if your home lacks a holographic taunt greeter, I regret to report you are behind."
        };
        String[] anims =
        {
            "pose_proudly",
            "taunt1",
            "taunt2",
            "point_to_self",
            "wave_finger_warning"
        };
        int selection = rand(0, taunts.length - 1);
        faceTo(speaker, target);
        chat.chat(speaker, target, taunts[selection], chat.ChatFlag_targetOnly);
        doAnimationAction(speaker, anims[selection]);
        playClientEffectObj(speaker, "clienteffect/holoemote_imperial.cef", speaker, "head");
    }
    private obj_id getSpeaker(obj_id self) throws InterruptedException
    {
        ensureHologram(self);
        if (hasObjVar(self, HOLOGRAM_OBJVAR))
        {
            obj_id hologram = getObjIdObjVar(self, HOLOGRAM_OBJVAR);
            if (isValidId(hologram) && exists(hologram))
            {
                return hologram;
            }
        }
        return self;
    }
    private void ensureHologram(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, HOLOGRAM_OBJVAR))
        {
            obj_id hologram = getObjIdObjVar(self, HOLOGRAM_OBJVAR);
            if (isValidId(hologram) && exists(hologram))
            {
                return;
            }
            removeObjVar(self, HOLOGRAM_OBJVAR);
        }
        location baseLoc = getLocation(self);
        if (baseLoc == null)
        {
            return;
        }
        location holoLoc = (location)baseLoc.clone();
        holoLoc.x = holoLoc.x + 0.8f;
        holoLoc.z = holoLoc.z + 0.6f;
        obj_id hologram = createObject(HOLOGRAM_TEMPLATE, holoLoc);
        if (!isValidId(hologram) || !exists(hologram))
        {
            return;
        }
        obj_id owner = getObjIdObjVar(self, vendor_lib.GREETER_OWNER_OBJVAR);
        String ownerName = getStringObjVar(self, "contriplague.ownerName");
        if ((ownerName == null || ownerName.equals("")) && isValidId(owner) && exists(owner))
        {
            ownerName = getFirstName(owner);
        }
        if (ownerName == null || ownerName.equals(""))
        {
            ownerName = "the donor";
        }
        setName(hologram, "Holo-" + ownerName);
        setInvulnerable(hologram, true);
        setHologramType(hologram, HOLOGRAM_TYPE1_QUALITY4);
        clearCondition(hologram, CONDITION_CONVERSABLE);
        clearCondition(hologram, CONDITION_INTERESTING);
        clearCondition(hologram, CONDITION_SPACE_INTERESTING);
        setYaw(hologram, getYaw(self));
        setObjVar(self, HOLOGRAM_OBJVAR, hologram);
    }
    private void cleanupHologram(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, HOLOGRAM_OBJVAR))
        {
            return;
        }
        obj_id hologram = getObjIdObjVar(self, HOLOGRAM_OBJVAR);
        removeObjVar(self, HOLOGRAM_OBJVAR);
        if (isValidId(hologram) && exists(hologram))
        {
            destroyObject(hologram);
        }
    }
}
