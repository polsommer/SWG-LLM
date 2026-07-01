package script.systems.jedi;

import script.dictionary;
import script.location;
import script.obj_id;
import script.library.create;
import script.library.enclave_trials;
import script.library.factions;
import script.library.force_rank;
import script.library.utils;

public class enclave_trial_mustafar_hall_boss extends script.base_script
{
    private static final String TRIAL_MUSTAFAR_HALL = "trial_mustafar_hall_dark_jedi_master";
    private static final String VAR_ACTIVE_CARD = "enclave.activeCard";
    private static final String VAR_ENCLAVE_PHASE = "enclave.phase";
    private static final String VAR_STORY_HANDOFF = "enclave.story.handoff";
    private static final String VAR_ACTIVE_BOSS = "enclave.mustafar.hall.activeBoss";
    private static final String VAR_OWNER = "enclave.mustafar.owner";
    private static final String VAR_CARD_ID = "enclave.mustafar.cardId";
    private static final String VAR_MECHANICS_PROFILE = "enclave.mustafar.mechanicsProfile";
    private static final String VAR_MUTATORS = "enclave.mustafar.mutators";
    private static final String VAR_MUSTAFAR_WAYPOINT = "enclave.ruins.mustafarWaypoint";
    private static final String VAR_PHASE = "enclave.mustafar.phase";
    private static final String VAR_NEXT_SABER_STORM = "enclave.mustafar.nextSaberStorm";
    private static final String VAR_NEXT_FORCE_CHOKE = "enclave.mustafar.nextForceChoke";
    private static final String VAR_NEXT_LAVA_SHOCKWAVE = "enclave.mustafar.nextLavaShockwave";
    private static final String VAR_ALIGNMENT = "enclave.alignment";
    private static final String VAR_ENCLAVE_DARK_ACCESS = "enclave.access.dark";
    private static final String VAR_ENCLAVE_LIGHT_ACCESS = "enclave.access.light";
    private static final String VAR_REWARD_MULT = "enclave.mustafar.rewardMult";
    private static final String VAR_INFLUENCE_MULT = "enclave.mustafar.influenceMult";
    private static final String VAR_WAVE_SPAWNED_PHASE_2 = "enclave.mustafar.waveSpawnedPhase2";
    private static final String VAR_WAVE_SPAWNED_PHASE_3 = "enclave.mustafar.waveSpawnedPhase3";
    private static final String VAR_PARTICIPATION_STAMP = "enclave.mustafar.lastParticipation";
    private static final String VAR_PARTICIPATION_BOSS = "enclave.mustafar.lastBoss";
    private static final String VAR_PARTICIPATION_CARD = "enclave.mustafar.lastCard";
    private static final String MSG_ABILITY_TICK = "mustafarHallBossAbilityTick";
    private static final String MSG_EXECUTE_SABER_STORM = "mustafarHallExecuteSaberStorm";
    private static final String MSG_EXECUTE_FORCE_CHOKE = "mustafarHallExecuteForceChoke";
    private static final String MSG_EXECUTE_LAVA_SHOCKWAVE = "mustafarHallExecuteLavaShockwave";
    private static final float GROUP_CREDIT_RADIUS = 90.0f;

    public enclave_trial_mustafar_hall_boss()
    {
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        int now = getGameTime();
        setObjVar(self, VAR_PHASE, 1);
        setObjVar(self, VAR_NEXT_SABER_STORM, now + 4);
        setObjVar(self, VAR_NEXT_FORCE_CHOKE, now + 7);
        setObjVar(self, VAR_NEXT_LAVA_SHOCKWAVE, now + 11);
        messageTo(self, MSG_ABILITY_TICK, null, 1.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnEnteredCombat(obj_id self) throws InterruptedException
    {
        messageTo(self, MSG_ABILITY_TICK, null, 0.5f, false);
        return SCRIPT_CONTINUE;
    }

    public int OnCreatureDamaged(obj_id self, obj_id attacker, obj_id weapon, int[] damageData) throws InterruptedException
    {
        if (!isIdValid(attacker) || !isPlayer(attacker))
        {
            return SCRIPT_CONTINUE;
        }
        noteParticipant(self, attacker);
        return SCRIPT_CONTINUE;
    }

    public int mustafarHallBossAbilityTick(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id owner = getBossOwner(self);
        if (!isValidOwner(owner) || hasOwnerLeft(owner))
        {
            clearActiveBoss(owner, self);
            return SCRIPT_CONTINUE;
        }

        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(target))
        {
            target = owner;
        }
        if (!isIdValid(target) || isDead(target))
        {
            messageTo(self, MSG_ABILITY_TICK, null, 2.0f, false);
            return SCRIPT_CONTINUE;
        }

        int now = getGameTime();
        int phase = updatePhase(self, owner);

        if (now >= getIntObjVar(self, VAR_NEXT_SABER_STORM))
        {
            setObjVar(self, VAR_NEXT_SABER_STORM, now + getSaberStormCadence(self, phase));
            telegraph(self, owner, target, "The hall boss whips into a saber storm burst!");
            messageTo(self, MSG_EXECUTE_SABER_STORM, null, getTelegraphDelay(self, 1.35f), false);
        }

        if (phase >= 2 && now >= getIntObjVar(self, VAR_NEXT_FORCE_CHOKE))
        {
            setObjVar(self, VAR_NEXT_FORCE_CHOKE, now + getForceChokeCadence(self, phase));
            telegraph(self, owner, target, "Crackling force pressure coils around your throat.");
            messageTo(self, MSG_EXECUTE_FORCE_CHOKE, null, getTelegraphDelay(self, 1.7f), false);
        }

        if (phase >= 3 && now >= getIntObjVar(self, VAR_NEXT_LAVA_SHOCKWAVE))
        {
            setObjVar(self, VAR_NEXT_LAVA_SHOCKWAVE, now + getLavaShockwaveCadence(self, phase));
            telegraph(self, owner, target, "Molten energy erupts beneath the boss - incoming lava shockwave!");
            messageTo(self, MSG_EXECUTE_LAVA_SHOCKWAVE, null, getTelegraphDelay(self, 2.2f), false);
        }

        messageTo(self, MSG_ABILITY_TICK, null, 2.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int mustafarHallExecuteSaberStorm(obj_id self, dictionary params) throws InterruptedException
    {
        if (isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id owner = getBossOwner(self);
        if (!isValidOwner(owner) || hasOwnerLeft(owner))
        {
            clearActiveBoss(owner, self);
            return SCRIPT_CONTINUE;
        }
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(target))
        {
            target = owner;
        }
        if (!isIdValid(target) || isDead(target))
        {
            return SCRIPT_CONTINUE;
        }

        doAnimationAction(self, "berserk");
        queueCommand(self, getStringCrc("saberthrow2"), target, "", COMMAND_PRIORITY_DEFAULT);
        playClientEffectObj(target, "clienteffect/pl_force_damage_single.cef", target, "");
        int burstDamage = getCappedAbilityDamage(target, 250, 430);
        if (hasMutator(self, "empowered_force_phase") && getIntObjVar(self, VAR_PHASE) >= 3)
        {
            burstDamage = (int)(burstDamage * 1.2f);
        }
        damage(target, DAMAGE_ENERGY, HIT_LOCATION_BODY, burstDamage);
        return SCRIPT_CONTINUE;
    }

    public int mustafarHallExecuteForceChoke(obj_id self, dictionary params) throws InterruptedException
    {
        if (isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id owner = getBossOwner(self);
        if (!isValidOwner(owner) || hasOwnerLeft(owner))
        {
            clearActiveBoss(owner, self);
            return SCRIPT_CONTINUE;
        }
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(target))
        {
            target = owner;
        }
        if (!isIdValid(target) || isDead(target))
        {
            return SCRIPT_CONTINUE;
        }

        doAnimationAction(self, "force_choke");
        playClientEffectObj(target, "clienteffect/pl_force_damage_single.cef", target, "");
        int pulseDamage = getCappedAbilityDamage(target, 300, 520);
        if (hasMutator(self, "empowered_force_phase") && getIntObjVar(self, VAR_PHASE) >= 2)
        {
            pulseDamage = (int)(pulseDamage * 1.35f);
        }
        damage(target, DAMAGE_STUN, HIT_LOCATION_BODY, pulseDamage);
        queueCommand(self, getStringCrc("forcelightning2"), target, "", COMMAND_PRIORITY_DEFAULT);
        return SCRIPT_CONTINUE;
    }

    public int mustafarHallExecuteLavaShockwave(obj_id self, dictionary params) throws InterruptedException
    {
        if (isDead(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id owner = getBossOwner(self);
        if (!isValidOwner(owner) || hasOwnerLeft(owner))
        {
            clearActiveBoss(owner, self);
            return SCRIPT_CONTINUE;
        }
        obj_id target = getHateTarget(self);
        if (!isIdValid(target) || !isPlayer(target) || isDead(target))
        {
            target = owner;
        }
        if (!isIdValid(target) || isDead(target))
        {
            return SCRIPT_CONTINUE;
        }

        doAnimationAction(self, "force_push");
        playClientEffectObj(self, "clienteffect/pl_force_blast.cef", self, "");
        playClientEffectObj(target, "clienteffect/pl_force_blast.cef", target, "");
        int shockDamage = getCappedAbilityDamage(target, 360, 620);
        damage(target, DAMAGE_KINETIC, HIT_LOCATION_BODY, shockDamage);
        return SCRIPT_CONTINUE;
    }

    public int OnIncapacitated(obj_id self, obj_id killer) throws InterruptedException
    {
        obj_id owner = hasObjVar(self, VAR_OWNER) ? getObjIdObjVar(self, VAR_OWNER) : obj_id.NULL_ID;
        clearActiveBoss(owner, self);
        obj_id[] eligible = getEligibleCreditRecipients(self, owner);
        if (eligible == null || eligible.length <= 0)
        {
            return SCRIPT_CONTINUE;
        }
        for (int i = 0; i < eligible.length; i++)
        {
            completeForPlayer(self, eligible[i]);
        }
        return SCRIPT_CONTINUE;
    }

    private void completeForPlayer(obj_id boss, obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        String cardId = getBossCardId(boss);
        if (hasObjVar(player, VAR_MUSTAFAR_WAYPOINT))
        {
            obj_id waypoint = getObjIdObjVar(player, VAR_MUSTAFAR_WAYPOINT);
            if (isIdValid(waypoint) && exists(waypoint))
            {
                setWaypointActive(waypoint, false);
                setWaypointVisible(waypoint, false);
            }
            removeObjVar(player, VAR_MUSTAFAR_WAYPOINT);
        }
        dictionary params = new dictionary();
        params.put("cardId", cardId);
        params.put("amount", 1);
        messageTo(player, "updateTrialProgress", params, 0.0f, false);
        applyDefinitionBonus(player, boss, cardId);

        setObjVar(player, VAR_ACTIVE_CARD, cardId);
        setObjVar(player, VAR_ENCLAVE_PHASE, "MustafarHallComplete");
        String nextCard = getNextCard(cardId);
        if (nextCard.length() > 0)
        {
            setObjVar(player, VAR_STORY_HANDOFF, nextCard);
            if (!hasObjVar(player, "enclave.progress." + nextCard))
            {
                setObjVar(player, "enclave.progress." + nextCard, 0);
            }
            sendSystemMessage(player, "Hall tier complete. Next story card unlocked: " + formatTierName(nextCard) + ".", "");
        }
        else
        {
            removeObjVar(player, VAR_STORY_HANDOFF);
            grantEnclaveAccessEntitlement(player);
            force_rank.refreshPlayerEnclaveAccess(player);
            sendSystemMessage(player, "The final Mustafar hall boss has fallen. Return to the enclave for debriefing.", "");
        }
    }

    private void applyDefinitionBonus(obj_id player, obj_id boss, String cardId) throws InterruptedException
    {
        float rewardMult = hasObjVar(boss, VAR_REWARD_MULT) ? getFloatObjVar(boss, VAR_REWARD_MULT) : 1.0f;
        float influenceMult = hasObjVar(boss, VAR_INFLUENCE_MULT) ? getFloatObjVar(boss, VAR_INFLUENCE_MULT) : 1.0f;
        if (rewardMult <= 1.0f && influenceMult <= 1.0f)
        {
            return;
        }
        script.library.enclave_trials.TrialDefinition def = enclave_trials.getTrial(cardId);
        if (def == null)
        {
            return;
        }
        int extraReward = (int)(def.reward * (rewardMult - 1.0f));
        int extraInfluence = (int)(def.influenceReward * (influenceMult - 1.0f));
        if (extraReward > 0)
        {
            int bonds = getIntObjVar(player, "gcwCampaign.bonds");
            setObjVar(player, "gcwCampaign.bonds", bonds + extraReward);
        }
        if (extraInfluence > 0)
        {
            int influence = getIntObjVar(player, "enclave.influence");
            setObjVar(player, "enclave.influence", influence + extraInfluence);
        }
        if (extraReward > 0 || extraInfluence > 0)
        {
            sendSystemMessage(player, "Hall modifier bonus awarded: +" + extraReward + " bonds, +" + extraInfluence + " influence.", "");
        }
    }

    private void grantEnclaveAccessEntitlement(obj_id owner) throws InterruptedException
    {
        String alignment = determineAlignment(owner);
        if ("dark".equals(alignment))
        {
            setObjVar(owner, VAR_ENCLAVE_DARK_ACCESS, 1);
            removeObjVar(owner, VAR_ENCLAVE_LIGHT_ACCESS);
            sendSystemMessage(owner, "Your choices have opened the Dark Enclave to you.", "");
            return;
        }
        if ("light".equals(alignment))
        {
            setObjVar(owner, VAR_ENCLAVE_LIGHT_ACCESS, 1);
            removeObjVar(owner, VAR_ENCLAVE_DARK_ACCESS);
            sendSystemMessage(owner, "Your choices have opened the Light Enclave to you.", "");
            return;
        }
        sendSystemMessage(owner, "You completed the trial, but your enclave alignment is unresolved. Speak with an enclave master to finalize access.", "");
    }

    private String determineAlignment(obj_id player) throws InterruptedException
    {
        String alignment = utils.getStringScriptVar(player, VAR_ALIGNMENT);
        if (alignment != null)
        {
            alignment = alignment.toLowerCase();
            if ("light".equals(alignment) || "dark".equals(alignment))
            {
                return alignment;
            }
        }
        if (force_rank.isForceRanked(player))
        {
            int council = force_rank.getCouncilAffiliation(player);
            if (council == force_rank.LIGHT_COUNCIL)
            {
                return "light";
            }
            if (council == force_rank.DARK_COUNCIL)
            {
                return "dark";
            }
        }
        String factionName = factions.getFactionNameByHashCode(pvpGetAlignedFaction(player));
        if (factionName != null)
        {
            if (factionName.equalsIgnoreCase(factions.FACTION_REBEL))
            {
                return "light";
            }
            if (factionName.equalsIgnoreCase(factions.FACTION_IMPERIAL))
            {
                return "dark";
            }
        }
        return "neutral";
    }

    private int updatePhase(obj_id self, obj_id owner) throws InterruptedException
    {
        int maxHealth = getMaxAttrib(self, HEALTH);
        int currentHealth = getAttrib(self, HEALTH);
        if (maxHealth <= 0)
        {
            return getIntObjVar(self, VAR_PHASE);
        }
        int healthPct = (currentHealth * 100) / maxHealth;
        int newPhase = 1;
        if (healthPct <= 20)
        {
            newPhase = 4;
        }
        else if (healthPct <= 45)
        {
            newPhase = 3;
        }
        else if (healthPct <= 75)
        {
            newPhase = 2;
        }

        int oldPhase = getIntObjVar(self, VAR_PHASE);
        if (newPhase > oldPhase)
        {
            setObjVar(self, VAR_PHASE, newPhase);
            String msg = "The hall boss grows more desperate and aggressive.";
            if (newPhase == 3)
            {
                msg = "The boss channels deeper darkness - molten tremors begin to surge.";
            }
            else if (newPhase == 4)
            {
                msg = "Final phase! The boss unleashes relentless fury.";
            }
            telegraph(self, owner, getHateTarget(self), msg);
            int now = getGameTime();
            setObjVar(self, VAR_NEXT_SABER_STORM, now + 2);
            setObjVar(self, VAR_NEXT_FORCE_CHOKE, now + 3);
            setObjVar(self, VAR_NEXT_LAVA_SHOCKWAVE, now + 4);
            maybeSpawnWaveAdds(self, owner, newPhase);
            return newPhase;
        }
        return oldPhase > 0 ? oldPhase : newPhase;
    }

    private void maybeSpawnWaveAdds(obj_id self, obj_id owner, int phase) throws InterruptedException
    {
        if (!hasMutator(self, "add_waves"))
        {
            return;
        }
        if (phase == 2)
        {
            if (hasObjVar(self, VAR_WAVE_SPAWNED_PHASE_2))
            {
                return;
            }
            setObjVar(self, VAR_WAVE_SPAWNED_PHASE_2, 1);
            spawnAddWave(self, owner, 1);
            return;
        }
        if (phase >= 3)
        {
            if (hasObjVar(self, VAR_WAVE_SPAWNED_PHASE_3))
            {
                return;
            }
            setObjVar(self, VAR_WAVE_SPAWNED_PHASE_3, 1);
            spawnAddWave(self, owner, 2);
        }
    }

    private void spawnAddWave(obj_id self, obj_id owner, int waveSize) throws InterruptedException
    {
        location center = getLocation(self);
        if (center == null)
        {
            return;
        }
        int addLevel = getLevel(self) - 6;
        if (addLevel < 40)
        {
            addLevel = 40;
        }
        for (int i = 0; i < waveSize; i++)
        {
            location spawn = new location(center.x + (3.0f * (i + 1)), center.y, center.z - (2.0f * (i + 1)), center.area, center.cell);
            obj_id add = create.createCreature("dark_adept", spawn, addLevel, true, false);
            if (!isIdValid(add))
            {
                continue;
            }
            startCombat(add, owner);
        }
        telegraph(self, owner, owner, "Reinforcement adepts surge into the hall!");
    }

    private void telegraph(obj_id self, obj_id owner, obj_id target, String msg) throws InterruptedException
    {
        playClientEffectObj(self, "clienteffect/pl_force_resist_attack_self.cef", self, "");
        if (isIdValid(owner) && isPlayer(owner))
        {
            sendSystemMessage(owner, msg, "");
        }
        if (isIdValid(target) && isPlayer(target) && target != owner)
        {
            sendSystemMessage(target, msg, "");
        }
    }

    private int getSaberStormCadence(obj_id self, int phase) throws InterruptedException
    {
        int base = 10;
        if (phase >= 4)
        {
            base = 6;
        }
        else if (phase >= 2)
        {
            base = 8;
        }
        return applyCadenceModifier(self, base, "enclave.mustafar.cadence.saber");
    }

    private int getForceChokeCadence(obj_id self, int phase) throws InterruptedException
    {
        int base = 14;
        if (phase >= 4)
        {
            base = 8;
        }
        else if (phase >= 3)
        {
            base = 11;
        }
        return applyCadenceModifier(self, base, "enclave.mustafar.cadence.force");
    }

    private int getLavaShockwaveCadence(obj_id self, int phase) throws InterruptedException
    {
        int base = 14;
        if (phase >= 4)
        {
            base = 10;
        }
        return applyCadenceModifier(self, base, "enclave.mustafar.cadence.lava");
    }

    private int applyCadenceModifier(obj_id self, int base, String objVar) throws InterruptedException
    {
        float mod = hasObjVar(self, objVar) ? getFloatObjVar(self, objVar) : 1.0f;
        if (mod <= 0.0f)
        {
            mod = 1.0f;
        }
        int adjusted = (int)(base * mod);
        return adjusted < 3 ? 3 : adjusted;
    }

    private float getTelegraphDelay(obj_id self, float baseDelay) throws InterruptedException
    {
        if (!hasMutator(self, "shorter_telegraphs"))
        {
            return baseDelay;
        }
        float adjusted = baseDelay * 0.65f;
        return adjusted < 0.45f ? 0.45f : adjusted;
    }

    private int getCappedAbilityDamage(obj_id player, int floor, int cap) throws InterruptedException
    {
        int raw = getLevel(player) * 8;
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

    private void noteParticipant(obj_id self, obj_id attacker) throws InterruptedException
    {
        setObjVar(attacker, VAR_PARTICIPATION_STAMP, getGameTime());
        setObjVar(attacker, VAR_PARTICIPATION_BOSS, self);
        setObjVar(attacker, VAR_PARTICIPATION_CARD, getBossCardId(self));
    }

    private obj_id[] getEligibleCreditRecipients(obj_id self, obj_id owner) throws InterruptedException
    {
        obj_id[] solo = new obj_id[]{owner};
        if (!isValidOwner(owner))
        {
            return solo;
        }
        obj_id groupId = getGroupObject(owner);
        if (!isIdValid(groupId))
        {
            return isEligibleForCredit(self, owner) ? solo : new obj_id[0];
        }
        obj_id[] members = getGroupMemberIds(groupId);
        if (members == null || members.length <= 0)
        {
            return isEligibleForCredit(self, owner) ? solo : new obj_id[0];
        }
        obj_id[] eligible = new obj_id[0];
        for (int i = 0; i < members.length; i++)
        {
            obj_id member = members[i];
            if (!isEligibleForCredit(self, member))
            {
                continue;
            }
            obj_id[] updatedEligible = new obj_id[eligible.length + 1];
            System.arraycopy(eligible, 0, updatedEligible, 0, eligible.length);
            updatedEligible[eligible.length] = member;
            eligible = updatedEligible;
        }
        return eligible;
    }

    private boolean isEligibleForCredit(obj_id boss, obj_id member) throws InterruptedException
    {
        if (!isIdValid(member) || !isPlayer(member) || isDead(member))
        {
            return false;
        }
        String cardId = getBossCardId(boss);
        if (!hasObjVar(member, VAR_ACTIVE_CARD) || !cardId.equals(getStringObjVar(member, VAR_ACTIVE_CARD)))
        {
            return false;
        }
        if (!hasObjVar(member, VAR_PARTICIPATION_BOSS) || getObjIdObjVar(member, VAR_PARTICIPATION_BOSS) != boss)
        {
            return false;
        }
        int lastParticipation = hasObjVar(member, VAR_PARTICIPATION_STAMP) ? getIntObjVar(member, VAR_PARTICIPATION_STAMP) : 0;
        if (lastParticipation <= 0 || (getGameTime() - lastParticipation) > 900)
        {
            return false;
        }
        String participationCard = hasObjVar(member, VAR_PARTICIPATION_CARD) ? getStringObjVar(member, VAR_PARTICIPATION_CARD) : "";
        if (!cardId.equals(participationCard))
        {
            return false;
        }
        location bossLoc = getLocation(boss);
        location memberLoc = getLocation(member);
        if (bossLoc == null || memberLoc == null || memberLoc.area == null || !memberLoc.area.equals(bossLoc.area))
        {
            return false;
        }
        return getDistance(memberLoc, bossLoc) <= GROUP_CREDIT_RADIUS;
    }

    private String getBossCardId(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, VAR_CARD_ID))
        {
            return TRIAL_MUSTAFAR_HALL;
        }
        String cardId = getStringObjVar(self, VAR_CARD_ID);
        return cardId == null || cardId.length() <= 0 ? TRIAL_MUSTAFAR_HALL : cardId;
    }

    private boolean hasMutator(obj_id self, String mutator) throws InterruptedException
    {
        if (mutator == null || mutator.length() <= 0 || !hasObjVar(self, VAR_MUTATORS))
        {
            return false;
        }
        String raw = getStringObjVar(self, VAR_MUTATORS);
        if (raw == null || raw.length() <= 0)
        {
            return false;
        }
        return raw.indexOf(mutator) >= 0;
    }

    private String getNextCard(String cardId)
    {
        if (TRIAL_MUSTAFAR_HALL.equals(cardId))
        {
            return "trial_mustafar_hall_warden";
        }
        if ("trial_mustafar_hall_warden".equals(cardId))
        {
            return "trial_mustafar_hall_master_ascendant";
        }
        return "";
    }

    private String formatTierName(String cardId)
    {
        if ("trial_mustafar_hall_warden".equals(cardId))
        {
            return "Hall Warden";
        }
        if ("trial_mustafar_hall_master_ascendant".equals(cardId))
        {
            return "Hall Master Ascendant";
        }
        return "Hall Initiate";
    }

    private obj_id getBossOwner(obj_id self) throws InterruptedException
    {
        return hasObjVar(self, VAR_OWNER) ? getObjIdObjVar(self, VAR_OWNER) : obj_id.NULL_ID;
    }

    private boolean isValidOwner(obj_id owner) throws InterruptedException
    {
        return isIdValid(owner) && isPlayer(owner);
    }

    private boolean hasOwnerLeft(obj_id owner) throws InterruptedException
    {
        if (!isValidOwner(owner) || !isInWorld(owner))
        {
            return true;
        }
        location ownerLoc = getLocation(owner);
        if (ownerLoc == null || ownerLoc.area == null || !"mustafar".equals(ownerLoc.area))
        {
            return true;
        }
        float ownerDistance = getDistance(ownerLoc, enclave_trials.MUSTAFAR_HALL_CENTER);
        boolean outOfBounds = ownerDistance > enclave_trials.MUSTAFAR_HALL_MAX_OWNER_DISTANCE;
        LOG("enclave", "Mustafar hall leash check for " + owner + ": distance=" + ownerDistance + ", center=" + enclave_trials.MUSTAFAR_HALL_CENTER + ", outOfBounds=" + outOfBounds);
        return outOfBounds;
    }

    private void clearActiveBoss(obj_id owner, obj_id boss) throws InterruptedException
    {
        if (isIdValid(owner) && hasObjVar(owner, VAR_ACTIVE_BOSS))
        {
            obj_id activeBoss = getObjIdObjVar(owner, VAR_ACTIVE_BOSS);
            if (activeBoss == boss)
            {
                removeObjVar(owner, VAR_ACTIVE_BOSS);
            }
        }
    }
}
