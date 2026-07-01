package script.systems.jedi;

import script.dictionary;
import script.library.buff;
import script.obj_id;
import script.location;

public class enclave_ruins_guardian_behavior extends script.base_script
{
    private static final String VAR_OWNER = "enclave.ruins.owner";
    private static final String VAR_ROLE = "enclave.ruins.guardianRole";
    private static final String ROLE_KNOCKBACK = "knockback";
    private static final String ROLE_SHIELD = "shield";
    private static final String ROLE_AREA_DENIAL = "area_denial";
    private static final String ROLE_INTERRUPT = "interrupt";
    private static final String ROLE_COMBO_ANCHOR = "combo_anchor";
    private static final String MSG_ABILITY_TICK = "guardianAbilityTick";
    private static final String MSG_EXECUTE_KNOCKBACK = "executeKnockback";
    private static final String MSG_EXECUTE_SHIELD = "executeShield";
    private static final String MSG_EXECUTE_SABER_THROW = "executeSaberThrow";
    private static final String MSG_PREP_AREA_DENIAL = "prepareAreaDenial";
    private static final String MSG_EXECUTE_AREA_DENIAL = "executeAreaDenial";
    private static final String MSG_PREP_INTERRUPT = "prepareInterruptCast";
    private static final String MSG_EXECUTE_INTERRUPT = "executeInterruptCast";
    private static final String MSG_PREP_COMBO = "prepareComboWindow";
    private static final String MSG_EXECUTE_COMBO = "executeComboWindow";
    private static final String VAR_CASTING_UNTIL = "enclave.ruins.castingUntil";
    private static final String VAR_COMBO_READY_UNTIL = "enclave.ruins.comboReadyUntil";
    private static final String VAR_COMBO_TARGET = "enclave.ruins.comboTarget";
    private static final int INTERRUPT_WINDOW_SECONDS = 2;

    public enclave_ruins_guardian_behavior()
    {
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        messageTo(self, MSG_ABILITY_TICK, null, 2.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnEnteredCombat(obj_id self) throws InterruptedException
    {
        messageTo(self, MSG_ABILITY_TICK, null, 1.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int guardianAbilityTick(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target))
        {
            messageTo(self, MSG_ABILITY_TICK, null, 3.0f, false);
            return SCRIPT_CONTINUE;
        }

        int now = getGameTime();
        String role = hasObjVar(self, VAR_ROLE) ? getStringObjVar(self, VAR_ROLE) : ROLE_KNOCKBACK;

        if (ROLE_KNOCKBACK.equals(role) && now >= getIntObjVar(self, "enclave.ruins.nextKnockbackAt"))
        {
            setObjVar(self, "enclave.ruins.nextKnockbackAt", now + 14);
            telegraph(self, target, "The guardian gathers force for a crushing push!");
            messageTo(self, MSG_EXECUTE_KNOCKBACK, null, 2.25f, false);
        }

        if (ROLE_SHIELD.equals(role) && now >= getIntObjVar(self, "enclave.ruins.nextShieldAt"))
        {
            setObjVar(self, "enclave.ruins.nextShieldAt", now + 18);
            telegraph(self, target, "The guardian raises a shimmering force barrier.");
            messageTo(self, MSG_EXECUTE_SHIELD, null, 2.0f, false);
        }

        if (ROLE_AREA_DENIAL.equals(role) && now >= getIntObjVar(self, "enclave.ruins.nextAreaDenialAt"))
        {
            setObjVar(self, "enclave.ruins.nextAreaDenialAt", now + 17);
            telegraph(self, target, "Run! The guardian traces a volatile force pulse around your position.");
            messageTo(self, MSG_PREP_AREA_DENIAL, null, 0.4f, false);
            messageTo(self, MSG_EXECUTE_AREA_DENIAL, null, 2.0f, false);
        }

        if (ROLE_INTERRUPT.equals(role) && now >= getIntObjVar(self, "enclave.ruins.nextInterruptCastAt"))
        {
            setObjVar(self, "enclave.ruins.nextInterruptCastAt", now + 16);
            telegraph(self, target, "The guardian begins channeling Force Rend - interrupt now!");
            messageTo(self, MSG_PREP_INTERRUPT, null, 0.1f, false);
            messageTo(self, MSG_EXECUTE_INTERRUPT, null, 2.2f, false);
        }

        if (ROLE_COMBO_ANCHOR.equals(role) && now >= getIntObjVar(self, "enclave.ruins.nextComboAt"))
        {
            setObjVar(self, "enclave.ruins.nextComboAt", now + 20);
            telegraph(self, target, "Guardians sync their stance for a combo strike - break line of sight!");
            messageTo(self, MSG_PREP_COMBO, null, 0.15f, false);
            messageTo(self, MSG_EXECUTE_COMBO, null, 1.75f, false);
        }

        if (now >= getIntObjVar(self, "enclave.ruins.nextSaberThrowAt"))
        {
            setObjVar(self, "enclave.ruins.nextSaberThrowAt", now + 9);
            telegraph(self, target, "The guardian cocks a saber throw - prepare to sidestep!");
            messageTo(self, MSG_EXECUTE_SABER_THROW, null, 1.6f, false);
        }

        messageTo(self, MSG_ABILITY_TICK, null, 3.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int executeKnockback(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(self) || isDead(target))
        {
            return SCRIPT_CONTINUE;
        }
        location from = getLocation(self);
        location to = getLocation(target);
        if (from != null && to != null && from.area != null && from.area.equals(to.area))
        {
            float dx = to.x - from.x;
            float dz = to.z - from.z;
            float mag = (float)Math.sqrt((dx * dx) + (dz * dz));
            if (mag > 0.1f)
            {
                float push = 4.0f;
                location knockbackLoc = new location(to.x + ((dx / mag) * push), to.y, to.z + ((dz / mag) * push), to.area, to.cell);
                setLocation(target, knockbackLoc);
            }
        }
        doAnimationAction(self, "force_push");
        playClientEffectObj(target, "clienteffect/pl_force_blast.cef", target, "");
        int burstDamage = getCappedBurstDamage(self, 280, 420);
        damage(target, DAMAGE_KINETIC, HIT_LOCATION_BODY, burstDamage);
        return SCRIPT_CONTINUE;
    }

    public int executeShield(obj_id self, dictionary params) throws InterruptedException
    {
        if (isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        playClientEffectObj(self, "clienteffect/pl_force_absorb_self.cef", self, "");
        int shieldHeal = getCappedBurstDamage(self, 450, 720);
        addToHealth(self, shieldHeal);
        buff.applyBuff(self, self, "jedi_force_run_1", 6.0f);
        return SCRIPT_CONTINUE;
    }

    public int executeSaberThrow(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(self) || isDead(target))
        {
            return SCRIPT_CONTINUE;
        }
        doAnimationAction(self, "throw_grenade");
        queueCommand(self, getStringCrc("saberthrow2"), target, "", COMMAND_PRIORITY_DEFAULT);
        playClientEffectObj(target, "clienteffect/pl_force_damage_single.cef", target, "");
        int burstDamage = getCappedBurstDamage(self, 220, 360);
        damage(target, DAMAGE_ENERGY, HIT_LOCATION_BODY, burstDamage);
        return SCRIPT_CONTINUE;
    }

    public int prepareAreaDenial(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || isDead(self) || isDead(target))
        {
            return SCRIPT_CONTINUE;
        }
        playClientEffectObj(target, "clienteffect/pl_force_resist_bleeding_self.cef", target, "");
        return SCRIPT_CONTINUE;
    }

    public int executeAreaDenial(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(self) || isDead(target))
        {
            return SCRIPT_CONTINUE;
        }
        playClientEffectObj(target, "clienteffect/pl_force_disease_area.cef", target, "");
        playClientEffectObj(self, "clienteffect/pl_force_disease_area.cef", self, "");
        int burstDamage = getCappedBurstDamage(self, 240, 390);
        location targetLoc = getLocation(target);
        location selfLoc = getLocation(self);
        if (targetLoc != null && selfLoc != null && targetLoc.area != null && targetLoc.area.equals(selfLoc.area) && getDistance(targetLoc, selfLoc) <= 14.0f)
        {
            damage(target, DAMAGE_ELEMENTAL_HEAT, HIT_LOCATION_BODY, burstDamage);
            sendSystemMessage(target, "You are caught in the edge of the force pulse. Moving away would have reduced its impact.", "");
        }
        return SCRIPT_CONTINUE;
    }

    public int prepareInterruptCast(obj_id self, dictionary params) throws InterruptedException
    {
        if (isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        setObjVar(self, VAR_CASTING_UNTIL, getGameTime() + INTERRUPT_WINDOW_SECONDS);
        doAnimationAction(self, "force_choke");
        playClientEffectObj(self, "clienteffect/pl_force_absorb_self.cef", self, "");
        return SCRIPT_CONTINUE;
    }

    public int executeInterruptCast(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(self) || isDead(target))
        {
            removeObjVar(self, VAR_CASTING_UNTIL);
            return SCRIPT_CONTINUE;
        }
        if (!hasObjVar(self, VAR_CASTING_UNTIL) || getGameTime() <= getIntObjVar(self, VAR_CASTING_UNTIL))
        {
            removeObjVar(self, VAR_CASTING_UNTIL);
            return SCRIPT_CONTINUE;
        }
        removeObjVar(self, VAR_CASTING_UNTIL);
        playClientEffectObj(target, "clienteffect/pl_force_choke.cef", target, "");
        int burstDamage = getCappedBurstDamage(self, 300, 470);
        damage(target, DAMAGE_ENERGY, HIT_LOCATION_BODY, burstDamage);
        sendSystemMessage(target, "Force Rend lands because the cast was not interrupted.", "");
        return SCRIPT_CONTINUE;
    }

    public int prepareComboWindow(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id owner = hasObjVar(self, VAR_OWNER) ? getObjIdObjVar(self, VAR_OWNER) : obj_id.NULL_ID;
        obj_id target = getHateTarget(self);
        if (!isIdValid(owner) || !isIdValid(target) || !isPlayer(owner))
        {
            return SCRIPT_CONTINUE;
        }
        setObjVar(owner, VAR_COMBO_READY_UNTIL, getGameTime() + 3);
        setObjVar(owner, VAR_COMBO_TARGET, target);
        playClientEffectObj(self, "clienteffect/pl_force_resist_attack_self.cef", self, "");
        return SCRIPT_CONTINUE;
    }

    public int executeComboWindow(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id owner = hasObjVar(self, VAR_OWNER) ? getObjIdObjVar(self, VAR_OWNER) : obj_id.NULL_ID;
        if (!isIdValid(owner) || !isPlayer(owner) || !hasObjVar(owner, VAR_COMBO_READY_UNTIL))
        {
            return SCRIPT_CONTINUE;
        }
        if (getGameTime() > getIntObjVar(owner, VAR_COMBO_READY_UNTIL))
        {
            removeObjVar(owner, VAR_COMBO_READY_UNTIL);
            removeObjVar(owner, VAR_COMBO_TARGET);
            return SCRIPT_CONTINUE;
        }
        obj_id target = getObjIdObjVar(owner, VAR_COMBO_TARGET);
        if (!isIdValid(target) || !isPlayer(target) || isDead(target) || isDead(self))
        {
            removeObjVar(owner, VAR_COMBO_READY_UNTIL);
            removeObjVar(owner, VAR_COMBO_TARGET);
            return SCRIPT_CONTINUE;
        }
        queueCommand(self, getStringCrc("forcelightning2"), target, "", COMMAND_PRIORITY_DEFAULT);
        playClientEffectObj(target, "clienteffect/pl_force_lightning_hit.cef", target, "");
        int burstDamage = getCappedBurstDamage(self, 250, 420);
        damage(target, DAMAGE_ELEMENTAL_ELECTRICAL, HIT_LOCATION_BODY, burstDamage);
        sendSystemMessage(target, "The guardians chain a combo strike. Break their cadence when it is telegraphed.", "");
        removeObjVar(owner, VAR_COMBO_READY_UNTIL);
        removeObjVar(owner, VAR_COMBO_TARGET);
        return SCRIPT_CONTINUE;
    }

    public int OnCreatureDamaged(obj_id self, obj_id attacker, obj_id weapon, int[] damageData) throws InterruptedException
    {
        if (!hasObjVar(self, VAR_CASTING_UNTIL))
        {
            return SCRIPT_CONTINUE;
        }
        int amount = 0;
        if (damageData != null && damageData.length > 0)
        {
            amount = damageData[0];
        }
        if (amount >= 120 && isIdValid(attacker) && isPlayer(attacker))
        {
            removeObjVar(self, VAR_CASTING_UNTIL);
            playClientEffectObj(self, "clienteffect/pl_force_feedback_hit.cef", self, "");
            sendSystemMessage(attacker, "You interrupt the guardian's cast!", "");
            obj_id owner = hasObjVar(self, VAR_OWNER) ? getObjIdObjVar(self, VAR_OWNER) : obj_id.NULL_ID;
            if (isIdValid(owner) && owner != attacker && isPlayer(owner))
            {
                sendSystemMessage(owner, "A guardian cast has been interrupted - capitalize on the opening!", "");
            }
        }
        return SCRIPT_CONTINUE;
    }

    private void telegraph(obj_id self, obj_id target, String msg) throws InterruptedException
    {
        playClientEffectObj(self, "clienteffect/pl_force_resist_attack_self.cef", self, "");
        obj_id owner = hasObjVar(self, VAR_OWNER) ? getObjIdObjVar(self, VAR_OWNER) : obj_id.NULL_ID;
        if (isIdValid(owner) && isPlayer(owner))
        {
            sendSystemMessage(owner, msg, "");
        }
        if (isIdValid(target) && isPlayer(target) && target != owner)
        {
            sendSystemMessage(target, msg, "");
        }
    }

    private int getCappedBurstDamage(obj_id self, int floor, int cap) throws InterruptedException
    {
        int raw = getLevel(self) * 7;
        if (raw < floor)
        {
            return floor;
        }
        if (raw > cap)
        {
            return cap;
        }
        return raw;
    }
}
