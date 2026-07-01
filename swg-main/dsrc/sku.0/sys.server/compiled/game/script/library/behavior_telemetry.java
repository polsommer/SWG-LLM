package script.library;

import script.*;

public class behavior_telemetry extends script.base_script
{
    public behavior_telemetry()
    {
    }

    public static final String TELEMETRY_ROOT = "behaviorTelemetry";
    public static final String PROFILE_ROOT = "behaviorProfile";
    public static final String AGGREGATE_ROOT = "npcBehavior.aggregate";
    public static final String NPC_PROFILE_ROOT = "npc.simProfile";
    public static final String NPC_PROFILE_SCRIPTVAR_ROOT = "npc.simProfile.runtime";
    public static final String NPC_PROGRESSION_ROOT = NPC_PROFILE_ROOT + ".progression";
    public static final String NPC_ACTIVITY_XP_ROOT = NPC_PROGRESSION_ROOT + ".activityXp";
    public static final String NPC_UNLOCKED_ABILITIES = NPC_PROGRESSION_ROOT + ".unlockedAbilities";
    public static final int MAX_SEQUENCE = 24;
    public static final int MAX_POLICY_SEQUENCE = 6;
    public static final int PROFILE_BUILD_RATE_LIMIT_SEC = 300;
    public static final int NPC_PROFILE_DECAY_INTERVAL_SEC = 300;
    public static final int NPC_RECENT_ZONE_WINDOW = 5;
    public static final int MIN_POLICY_SAMPLE_COUNT = 8;
    public static final String DEFAULT_ACTIVITY = "ambient";
    public static final int NPC_ACTIVITY_XP_DECAY = 2;
    public static final int NPC_RECENT_STEP_MEMORY = 8;
    public static final int NPC_HANGOUT_MEMORY = 4;
    public static final int MAX_ZONE_DUPLICATE_LOOKS_PER_100 = 22;
    public static final int MAX_REPEAT_SOCIAL_EVENTS_PER_HOUR = 14;

    public static boolean recordArchetypeEvent(obj_id player, String channel, String token, int cooldownSeconds) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player) || channel == null || token == null)
        {
            return false;
        }
        if (!passesRateLimit(player, channel, cooldownSeconds))
        {
            return false;
        }
        if (isExploitLikeToken(token))
        {
            incrementCounter(player, TELEMETRY_ROOT + ".safety.filtered");
            return false;
        }
        if (isLoopExploit(player, channel, token))
        {
            incrementCounter(player, TELEMETRY_ROOT + ".safety.loopFiltered");
            return false;
        }
        appendSequenceToken(player, channel + ":" + token);
        incrementCounter(player, TELEMETRY_ROOT + ".channel." + channel);
        incrementCounter(player, TELEMETRY_ROOT + ".token." + token);
        setObjVar(player, TELEMETRY_ROOT + ".updatedAt", getGameTime());
        updateRollingObservationProfile(player, channel, token);
        return true;
    }

    public static boolean recordActivityEvent(obj_id player, String activityType, String eventToken, int cooldownSeconds) throws InterruptedException
    {
        String activity = normalizeActivity(activityType);
        if (!recordArchetypeEvent(player, activity, eventToken, cooldownSeconds))
        {
            return false;
        }
        int now = getGameTime();
        String profilePath = PROFILE_ROOT + ".activity." + activity;
        incrementCounter(player, profilePath + ".samples");
        setObjVar(player, profilePath + ".lastAt", now);
        return true;
    }

    public static void recordSocialDwell(obj_id player, int dwellSeconds) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player) || dwellSeconds <= 0)
        {
            return;
        }
        int clamped = Math.min(dwellSeconds, 1200);
        int prior = getIntObjVar(player, TELEMETRY_ROOT + ".social.dwellTotal");
        setObjVar(player, TELEMETRY_ROOT + ".social.dwellTotal", prior + clamped);
        incrementCounter(player, TELEMETRY_ROOT + ".social.samples");
    }

    public static void rebuildBehaviorProfile(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        if (!passesRateLimit(player, "profileBuild", getBehaviorProfileUpdateFrequencySeconds()))
        {
            return;
        }
        int grinder = getIntObjVar(player, TELEMETRY_ROOT + ".channel.combat") * 4 + getIntObjVar(player, TELEMETRY_ROOT + ".channel.movement");
        int crafterRunner = getIntObjVar(player, TELEMETRY_ROOT + ".channel.crafting") * 5 + getIntObjVar(player, TELEMETRY_ROOT + ".channel.vendor") * 3 + getIntObjVar(player, TELEMETRY_ROOT + ".token.vendor_terminal_use") * 2;
        int socialHubIdler = (getIntObjVar(player, TELEMETRY_ROOT + ".social.dwellTotal") / 30) + getIntObjVar(player, TELEMETRY_ROOT + ".channel.social") * 3;

        String archetype = "grinder";
        int top = grinder;
        if (crafterRunner > top)
        {
            top = crafterRunner;
            archetype = "crafter_runner";
        }
        if (socialHubIdler > top)
        {
            archetype = "social_hub_idler";
            top = socialHubIdler;
        }
        int total = Math.max(1, grinder + crafterRunner + socialHubIdler);
        setObjVar(player, PROFILE_ROOT + ".archetype", archetype);
        setObjVar(player, PROFILE_ROOT + ".scores", new int[]{(grinder * 100) / total, (crafterRunner * 100) / total, (socialHubIdler * 100) / total});
        setObjVar(player, PROFILE_ROOT + ".builtAt", getGameTime());
        backfillUnknownObservationSamples(player, mapArchetypeToProfession(archetype));
        publishAggregateProfile(player, archetype);
    }

    public static String selectArchetypeForNpc(obj_id npc) throws InterruptedException
    {
        obj_id aggregateObject = getAggregateObject(npc);
        int grinder = 1;
        int crafter = 1;
        int social = 1;
        if (isIdValid(aggregateObject))
        {
            grinder += getIntObjVar(aggregateObject, AGGREGATE_ROOT + ".grinder");
            crafter += getIntObjVar(aggregateObject, AGGREGATE_ROOT + ".crafter_runner");
            social += getIntObjVar(aggregateObject, AGGREGATE_ROOT + ".social_hub_idler");
        }
        int roll = rand(1, grinder + crafter + social);
        if (roll <= grinder)
        {
            return "grinder";
        }
        if (roll <= grinder + crafter)
        {
            return "crafter_runner";
        }
        return "social_hub_idler";
    }

    public static String[] sampleActionSequenceForNpc(obj_id npc, String archetype, String activityType) throws InterruptedException
    {
        String scene = resolveScene(npc);
        String profession = mapArchetypeToProfession(archetype);
        String activity = normalizeActivity(activityType);
        if (!isLearningZoneEnabled(scene))
        {
            return getFallbackSequence(archetype, activity);
        }

        obj_id aggregateObject = getAggregateObject(npc);
        if (!isIdValid(aggregateObject))
        {
            return getFallbackSequence(archetype, activity);
        }

        String bucketRoot = getObservationBucketRoot(scene, profession, activity);
        int sampleCount = getIntObjVar(aggregateObject, bucketRoot + ".samples");
        int requiredSampleCount = getMinPolicySampleCount();
        if (sampleCount < requiredSampleCount)
        {
            return getFallbackSequence(archetype, activity);
        }

        String faction = getNpcFactionToken(npc);
        String zoneTag = getZoneTagForScene(scene);
        String timeBucket = getDayTimeBucket();
        String[] candidates = buildWeightedPolicyCandidates(archetype, activity, zoneTag, timeBucket, faction);
        int total = 0;
        int[] weights = new int[candidates.length];
        for (int i = 0; i < candidates.length; i++)
        {
            int count = getIntObjVar(aggregateObject, bucketRoot + ".sequence." + candidates[i]);
            if (count < 0)
            {
                count = 0;
            }
            int contextual = getContextualSequenceWeight(candidates[i], archetype, activity, zoneTag, timeBucket, faction);
            int cooldownPenalty = wasRecentlyUsedSequence(npc, candidates[i]) ? 3 : 0;
            int weighted = Math.max(1, count + contextual - cooldownPenalty);
            weights[i] = weighted;
            total += weighted;
        }

        if (total <= 0)
        {
            return getFallbackSequence(archetype, activity);
        }

        int roll = rand(1, total);
        int running = 0;
        for (int i = 0; i < candidates.length; i++)
        {
            running += weights[i];
            if (roll <= running)
            {
                rememberRecentSequence(npc, candidates[i]);
                String[] selected = splitSequence(candidates[i]);
                trackSocialRepetitionMetrics(npc, scene, selected);
                return selected;
            }
        }
        return getFallbackSequence(archetype, activity);
    }

    public static void ensureNpcIdentityProfile(obj_id npc, String archetype) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        if (hasObjVar(npc, NPC_PROFILE_ROOT + ".identity.initialized"))
        {
            return;
        }
        String species = getSpeciesForArchetype(archetype);
        int signature = rand(0, 99999);
        String[][] appearance = getSpeciesAppearancePalette(species);
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.species", species);
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.hair", pick(appearance[0]));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.skin", pick(appearance[1]));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.lips", pick(appearance[2]));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.eyes", pick(appearance[3]));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.palette", pick(appearance[4]));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.clothingSet", pick(getClothingSetForArchetype(archetype)));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.namingStyle", pick(getNamingStyleForSpecies(species)));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.personalityTags", getRandomPersonalityTags(archetype));
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.lookSignature", species + ":" + signature);
        setObjVar(npc, NPC_PROFILE_ROOT + ".identity.initialized", 1);
        npc_identity_assets.applyIdentityAssets(npc);
        bootstrapNpcSocialGraph(npc, archetype);
        validateRepetitionMetrics(npc, resolveScene(npc));
    }

    public static String selectRoutineGoalForNpc(obj_id npc, String archetype) throws InterruptedException
    {
        int hour = (getGameTime() / 3600) % 24;
        String profession = getNpcProfessionPath(npc);
        if (hour >= 0 && hour < 6)
        {
            return "rest";
        }
        if (hour >= 6 && hour < 10)
        {
            return "travel";
        }
        if (hour >= 10 && hour < 14)
        {
            return "mission_terminal";
        }
        if (hour >= 14 && hour < 18)
        {
            return "combat".equals(profession) ? "patrol" : "crafting";
        }
        if (hour >= 18 && hour < 22)
        {
            return "cantina";
        }
        return "trader".equals(profession) || "crafter_runner".equals(archetype) ? "vendor" : "social";
    }

    public static void updateSocialGraphMemory(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        String[] friends = utils.getStringArrayObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.friends", new String[0]);
        String[] rivals = utils.getStringArrayObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.rivals", new String[0]);
        if (friends.length < 2)
        {
            setObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.friends", new String[]{"dock_friend_" + rand(1, 22), "cantina_friend_" + rand(23, 44)});
        }
        if (rivals.length < 1)
        {
            setObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.rivals", new String[]{"rival_" + rand(1, 18)});
        }
        String[] hangouts = appendRollingWindow(utils.getStringArrayObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.hangouts", new String[0]), getPreferredHangout(resolveScene(npc)), NPC_HANGOUT_MEMORY);
        setObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.hangouts", hangouts);
    }

    public static void validateRepetitionMetrics(obj_id npc, String scene) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        obj_id aggregate = getAggregateObject(npc);
        if (!isIdValid(aggregate))
        {
            return;
        }
        String look = getStringObjVar(npc, NPC_PROFILE_ROOT + ".identity.lookSignature");
        if (look != null && look.length() > 0)
        {
            incrementCounter(aggregate, AGGREGATE_ROOT + ".metrics.look." + safeToken(scene) + ".total");
            incrementCounter(aggregate, AGGREGATE_ROOT + ".metrics.look." + safeToken(scene) + ".signature." + safeToken(look));
            int totalLooks = getIntObjVar(aggregate, AGGREGATE_ROOT + ".metrics.look." + safeToken(scene) + ".total");
            int duplicate = getIntObjVar(aggregate, AGGREGATE_ROOT + ".metrics.look." + safeToken(scene) + ".signature." + safeToken(look));
            if (totalLooks >= 25 && ((duplicate * 100) / Math.max(1, totalLooks)) > MAX_ZONE_DUPLICATE_LOOKS_PER_100)
            {
                setObjVar(npc, NPC_PROFILE_ROOT + ".identity.palette", "accent_unique_" + rand(1, 40));
                setObjVar(npc, NPC_PROFILE_ROOT + ".identity.lookSignature", look + "_u" + rand(1, 999));
                npc_identity_assets.applyIdentityAssets(npc);
            }
        }
    }

    public static int getActionDelayForArchetype(String archetype) throws InterruptedException
    {
        if ("grinder".equals(archetype))
        {
            return rand(4, 8);
        }
        if ("crafter_runner".equals(archetype))
        {
            return rand(7, 14);
        }
        return rand(10, 20);
    }

    public static float scoreGoalByArchetype(String archetype, String goalType, float baseScore) throws InterruptedException
    {
        float out = baseScore;
        if ("grinder".equals(archetype))
        {
            if ("combat".equals(goalType))
            {
                out += 2.5f;
            }
            if ("patrol".equals(goalType))
            {
                out += 1.0f;
            }
        }
        else if ("crafter_runner".equals(archetype))
        {
            if ("vendor".equals(goalType) || "crafting".equals(goalType))
            {
                out += 2.0f;
            }
            if ("travel".equals(goalType))
            {
                out += 1.5f;
            }
        }
        else
        {
            if ("social".equals(goalType) || "idle".equals(goalType))
            {
                out += 2.25f;
            }
        }
        return out;
    }

    public static float scoreGoalByArchetype(obj_id npc, String archetype, String goalType, float baseScore) throws InterruptedException
    {
        float out = scoreGoalByArchetype(archetype, goalType, baseScore);
        if (!isIdValid(npc))
        {
            return out;
        }

        int preferred = getIntObjVar(npc, NPC_PROFILE_ROOT + ".preferred." + goalType);
        int wins = getIntObjVar(npc, NPC_PROFILE_ROOT + ".success." + goalType);
        int losses = getIntObjVar(npc, NPC_PROFILE_ROOT + ".failure." + goalType);
        int total = Math.max(1, wins + losses);
        float successRate = (float) wins / (float) total;

        out += Math.min(2.0f, preferred * 0.1f);
        out += (successRate - 0.5f) * 1.5f;

        int lastAt = getIntObjVar(npc, NPC_PROFILE_ROOT + ".cooldown." + goalType + ".lastAt");
        int cooldown = getIntObjVar(npc, NPC_PROFILE_ROOT + ".cooldown." + goalType + ".seconds");
        int age = getGameTime() - lastAt;
        if (lastAt > 0 && cooldown > 0 && age < cooldown)
        {
            out -= 2.5f;
        }

        int novice = getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.novice");
        int adept = getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.adept");
        int expert = getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.expert");
        int masteryTotal = novice + (adept * 2) + (expert * 3);
        out += Math.min(1.5f, masteryTotal * 0.02f);

        initializeNpcProgressionState(npc);
        float activityXp = getNpcActivityXp(npc, goalType);
        out += Math.min(2.2f, activityXp / 45.0f);

        int levelBand = getIntObjVar(npc, NPC_PROGRESSION_ROOT + ".levelBand");
        out += Math.min(2.0f, (float)(Math.max(1, levelBand) - 1) * 0.5f);

        if (hasUnlockedNpcAbility(npc, goalType + "_advanced"))
        {
            out += 0.75f;
        }
        if (hasUnlockedNpcAbility(npc, goalType + "_specialist"))
        {
            out += 0.45f;
        }

        return Math.max(0.1f, out);
    }

    public static void recordNpcGoalCycle(obj_id npc, String goalType, boolean success) throws InterruptedException
    {
        if (!isIdValid(npc) || goalType == null || goalType.length() < 1)
        {
            return;
        }
        int now = getGameTime();
        incrementCounter(npc, NPC_PROFILE_ROOT + ".cycles.total");
        incrementCounter(npc, NPC_PROFILE_ROOT + ".cycles.byGoal." + goalType);
        if (success)
        {
            incrementCounter(npc, NPC_PROFILE_ROOT + ".success." + goalType);
            incrementCounter(npc, NPC_PROFILE_ROOT + ".preferred." + goalType);
        }
        else
        {
            incrementCounter(npc, NPC_PROFILE_ROOT + ".failure." + goalType);
            int preferred = getIntObjVar(npc, NPC_PROFILE_ROOT + ".preferred." + goalType);
            if (preferred > 0)
            {
                setObjVar(npc, NPC_PROFILE_ROOT + ".preferred." + goalType, preferred - 1);
            }
        }
        incrementExperienceBucket(npc, success);
        trackNpcActivityExperience(npc, goalType, success);
        recalculateNpcProgression(npc);
        setObjVar(npc, NPC_PROFILE_ROOT + ".updatedAt", now);
        setObjVar(npc, NPC_PROFILE_ROOT + ".goalState.current", goalType);
        setObjVar(npc, NPC_PROFILE_ROOT + ".goalState.lastAt", now);
        utils.setScriptVar(npc, NPC_PROFILE_SCRIPTVAR_ROOT + ".lastCycleGoal", goalType);
    }

    public static void recordNpcOutcome(obj_id npc, String outcomeType, boolean success) throws InterruptedException
    {
        if (!isIdValid(npc) || outcomeType == null || outcomeType.length() < 1)
        {
            return;
        }
        int now = getGameTime();
        incrementCounter(npc, NPC_PROFILE_ROOT + ".outcome." + outcomeType + ".count");
        if (success)
        {
            incrementCounter(npc, NPC_PROFILE_ROOT + ".outcome." + outcomeType + ".success");
        }
        else
        {
            incrementCounter(npc, NPC_PROFILE_ROOT + ".outcome." + outcomeType + ".failure");
        }
        setObjVar(npc, NPC_PROFILE_ROOT + ".cooldown." + outcomeType + ".lastAt", now);
        setObjVar(npc, NPC_PROFILE_ROOT + ".cooldown." + outcomeType + ".seconds", 180);
        setObjVar(npc, NPC_PROFILE_ROOT + ".updatedAt", now);
    }

    public static void noteNpcTargetZone(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        String zone = getLocation(npc).area;
        if (zone == null || zone.length() < 1)
        {
            return;
        }
        String path = NPC_PROFILE_ROOT + ".recentZones";
        String[] recent = utils.getStringArrayObjVar(npc, path, new String[0]);
        if (recent.length >= NPC_RECENT_ZONE_WINDOW)
        {
            String[] trimmed = new String[NPC_RECENT_ZONE_WINDOW];
            for (int i = 1; i < recent.length; i++)
            {
                trimmed[i - 1] = recent[i];
            }
            trimmed[NPC_RECENT_ZONE_WINDOW - 1] = zone;
            setObjVar(npc, path, trimmed);
            return;
        }
        String[] expanded = new String[recent.length + 1];
        for (int i = 0; i < recent.length; i++)
        {
            expanded[i] = recent[i];
        }
        expanded[expanded.length - 1] = zone;
        setObjVar(npc, path, expanded);
    }

    public static void saveNpcProfileCheckpoint(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        int now = getGameTime();
        int next = getIntObjVar(npc, NPC_PROFILE_ROOT + ".checkpoint.nextAt");
        if (next > now)
        {
            return;
        }
        initializeNpcProgressionState(npc);
        applyNpcProfileDecay(npc);
        applyNpcProgressionDecay(npc);
        setObjVar(npc, NPC_PROFILE_ROOT + ".checkpoint.lastAt", now);
        setObjVar(npc, NPC_PROFILE_ROOT + ".checkpoint.nextAt", now + NPC_PROFILE_DECAY_INTERVAL_SEC);
        LOG("npc_simulation", "checkpoint npc=" + npc + " profile=" + getNpcProfileDebugSummary(npc));
    }

    public static void applyNpcProfileDecay(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        String[] goals = new String[]{"combat", "crafting", "social", "travel", "economic"};
        for (int i = 0; i < goals.length; i++)
        {
            String goal = goals[i];
            int preferred = getIntObjVar(npc, NPC_PROFILE_ROOT + ".preferred." + goal);
            if (preferred > 0)
            {
                setObjVar(npc, NPC_PROFILE_ROOT + ".preferred." + goal, Math.max(0, preferred - 1));
            }
            int cooldown = getIntObjVar(npc, NPC_PROFILE_ROOT + ".cooldown." + goal + ".seconds");
            if (cooldown > 0)
            {
                setObjVar(npc, NPC_PROFILE_ROOT + ".cooldown." + goal + ".seconds", Math.max(60, cooldown - 15));
            }
        }

        String[] recentZones = utils.getStringArrayObjVar(npc, NPC_PROFILE_ROOT + ".recentZones", new String[0]);
        if (recentZones.length >= 4)
        {
            boolean same = true;
            String sample = recentZones[recentZones.length - 1];
            for (int i = 1; i < recentZones.length; i++)
            {
                if (!sample.equals(recentZones[i - 1]))
                {
                    same = false;
                    break;
                }
            }
            if (same)
            {
                setObjVar(npc, NPC_PROFILE_ROOT + ".recentZones", new String[]{sample});
                setObjVar(npc, NPC_PROFILE_ROOT + ".cooldown.travel.seconds", 300);
            }
        }
    }

    public static String getNpcProfileDebugSummary(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return "invalid npc";
        }
        initializeNpcProgressionState(npc);
        return "exp=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.novice") + "/" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.adept") + "/" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.expert") +
        " preferred[cmb=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".preferred.combat") + ",crf=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".preferred.crafting") + ",soc=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".preferred.social") + "]" +
        " outcomes[win=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".outcome.combat_win.success") + ",loss=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".outcome.combat_loss.failure") + ",death=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".outcome.death.failure") +
        ",travel=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".outcome.travel_success.success") + ",econ=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".outcome.economic_interaction.success") + "]" +
        " zones=" + flattenStringArray(utils.getStringArrayObjVar(npc, NPC_PROFILE_ROOT + ".recentZones", new String[0])) +
        " progression[levelBand=" + getIntObjVar(npc, NPC_PROGRESSION_ROOT + ".levelBand") + ",path=" + getStringObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath") + ",tier=" + getIntObjVar(npc, NPC_PROGRESSION_ROOT + ".equipmentTier") + "]" +
        " activityXp[cmb=" + getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".combat") + ",crf=" + getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".crafting") + ",soc=" + getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".social") + "]" +
        " abilities=" + flattenStringArray(utils.getStringArrayObjVar(npc, NPC_UNLOCKED_ABILITIES, new String[0])) +
        " checkpoint=" + getIntObjVar(npc, NPC_PROFILE_ROOT + ".checkpoint.lastAt");
    }


    public static void initializeNpcProgressionState(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        if (getIntObjVar(npc, NPC_PROGRESSION_ROOT + ".initialized") > 0)
        {
            return;
        }
        setObjVar(npc, NPC_PROGRESSION_ROOT + ".initialized", 1);
        if (getIntObjVar(npc, NPC_PROGRESSION_ROOT + ".levelBand") < 1)
        {
            setObjVar(npc, NPC_PROGRESSION_ROOT + ".levelBand", 1);
        }
        if (getIntObjVar(npc, NPC_PROGRESSION_ROOT + ".equipmentTier") < 1)
        {
            setObjVar(npc, NPC_PROGRESSION_ROOT + ".equipmentTier", 1);
        }
        String path = getStringObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath");
        if (path == null || path.length() < 1)
        {
            setObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath", mapArchetypeToProfession(getStringObjVar(npc, NPC_PROFILE_ROOT + ".archetype")));
        }
        if (!hasObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".combat"))
        {
            setObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".combat", 0);
        }
        if (!hasObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".crafting"))
        {
            setObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".crafting", 0);
        }
        if (!hasObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".social"))
        {
            setObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".social", 0);
        }
        if (!hasObjVar(npc, NPC_UNLOCKED_ABILITIES))
        {
            setObjVar(npc, NPC_UNLOCKED_ABILITIES, new String[0]);
        }
        setObjVar(npc, NPC_PROGRESSION_ROOT + ".lastProgressAt", getGameTime());
    }

    public static void trackNpcActivityExperience(obj_id npc, String goalType, boolean success) throws InterruptedException
    {
        if (!isIdValid(npc) || goalType == null || goalType.length() < 1)
        {
            return;
        }
        initializeNpcProgressionState(npc);
        String channel = resolveProgressionActivity(goalType);
        int xp = getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + "." + channel);
        int gain = success ? 10 : 3;
        setObjVar(npc, NPC_ACTIVITY_XP_ROOT + "." + channel, Math.max(0, xp + gain));
        setObjVar(npc, NPC_PROGRESSION_ROOT + ".lastActivity", channel);
        setObjVar(npc, NPC_PROGRESSION_ROOT + ".lastProgressAt", getGameTime());
    }

    public static void applyNpcProgressionDecay(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        initializeNpcProgressionState(npc);
        String[] channels = new String[]{"combat", "crafting", "social"};
        for (int i = 0; i < channels.length; i++)
        {
            String channel = channels[i];
            int xp = getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + "." + channel);
            if (xp > 0)
            {
                setObjVar(npc, NPC_ACTIVITY_XP_ROOT + "." + channel, Math.max(0, xp - NPC_ACTIVITY_XP_DECAY));
            }
        }
        recalculateNpcProgression(npc);
    }

    public static void retrainNpcProgression(obj_id npc, String newProfessionPath) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        initializeNpcProgressionState(npc);
        String resolved = resolveProgressionActivity(newProfessionPath);
        if ("combat".equals(resolved))
        {
            setObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath", "combat");
        }
        else if ("crafting".equals(resolved))
        {
            setObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath", "trader");
        }
        else
        {
            setObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath", "social");
        }
        setObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".combat", 0);
        setObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".crafting", 0);
        setObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".social", 0);
        setObjVar(npc, NPC_UNLOCKED_ABILITIES, new String[0]);
        recalculateNpcProgression(npc);
    }

    public static void resetNpcProgression(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        removeObjVar(npc, NPC_PROGRESSION_ROOT);
        initializeNpcProgressionState(npc);
        recalculateNpcProgression(npc);
    }

    public static int getNpcLevelBand(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return 1;
        }
        initializeNpcProgressionState(npc);
        return Math.max(1, getIntObjVar(npc, NPC_PROGRESSION_ROOT + ".levelBand"));
    }

    public static String getNpcProfessionPath(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return "social";
        }
        initializeNpcProgressionState(npc);
        String path = getStringObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath");
        if (path == null || path.length() < 1)
        {
            return "social";
        }
        return path;
    }

    public static int getNpcActivityXp(obj_id npc, String goalType) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return 0;
        }
        initializeNpcProgressionState(npc);
        String channel = resolveProgressionActivity(goalType);
        return getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + "." + channel);
    }

    private static void recalculateNpcProgression(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        int combatXp = getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".combat");
        int craftingXp = getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".crafting");
        int socialXp = getIntObjVar(npc, NPC_ACTIVITY_XP_ROOT + ".social");
        int total = combatXp + craftingXp + socialXp;

        int levelBand = 1;
        if (total >= 420)
        {
            levelBand = 4;
        }
        else if (total >= 240)
        {
            levelBand = 3;
        }
        else if (total >= 90)
        {
            levelBand = 2;
        }
        setObjVar(npc, NPC_PROGRESSION_ROOT + ".levelBand", levelBand);
        setObjVar(npc, NPC_PROGRESSION_ROOT + ".equipmentTier", Math.max(1, Math.min(4, levelBand)));

        String dominant = "social";
        int top = socialXp;
        if (combatXp > top)
        {
            dominant = "combat";
            top = combatXp;
        }
        if (craftingXp > top)
        {
            dominant = "trader";
        }
        setObjVar(npc, NPC_PROGRESSION_ROOT + ".professionPath", dominant);
        unlockNpcAbilitiesFromProgression(npc, levelBand, combatXp, craftingXp, socialXp);
    }

    private static void unlockNpcAbilitiesFromProgression(obj_id npc, int levelBand, int combatXp, int craftingXp, int socialXp) throws InterruptedException
    {
        String[] unlocked = new String[0];
        if (levelBand >= 2)
        {
            unlocked = appendUniqueAbility(unlocked, "combat_advanced");
            unlocked = appendUniqueAbility(unlocked, "crafting_advanced");
            unlocked = appendUniqueAbility(unlocked, "social_advanced");
        }
        if (combatXp >= 200)
        {
            unlocked = appendUniqueAbility(unlocked, "combat_specialist");
        }
        if (craftingXp >= 200)
        {
            unlocked = appendUniqueAbility(unlocked, "crafting_specialist");
        }
        if (socialXp >= 200)
        {
            unlocked = appendUniqueAbility(unlocked, "social_specialist");
        }
        if (levelBand >= 4)
        {
            unlocked = appendUniqueAbility(unlocked, "route_mastery");
        }
        setObjVar(npc, NPC_UNLOCKED_ABILITIES, unlocked);
    }

    private static String[] appendUniqueAbility(String[] values, String ability) throws InterruptedException
    {
        if (ability == null || ability.length() < 1)
        {
            return values;
        }
        for (int i = 0; i < values.length; i++)
        {
            if (ability.equals(values[i]))
            {
                return values;
            }
        }
        String[] expanded = new String[values.length + 1];
        for (int i = 0; i < values.length; i++)
        {
            expanded[i] = values[i];
        }
        expanded[expanded.length - 1] = ability;
        return expanded;
    }

    private static boolean hasUnlockedNpcAbility(obj_id npc, String ability) throws InterruptedException
    {
        if (!isIdValid(npc) || ability == null || ability.length() < 1)
        {
            return false;
        }
        String[] abilities = utils.getStringArrayObjVar(npc, NPC_UNLOCKED_ABILITIES, new String[0]);
        for (int i = 0; i < abilities.length; i++)
        {
            if (ability.equals(abilities[i]))
            {
                return true;
            }
        }
        return false;
    }

    private static String resolveProgressionActivity(String goalType) throws InterruptedException
    {
        String lower = normalizeActivity(goalType);
        if ("economic".equals(lower) || "crafting".equals(lower) || "vendor".equals(lower))
        {
            return "crafting";
        }
        if ("combat".equals(lower))
        {
            return "combat";
        }
        if ("social".equals(lower))
        {
            return "social";
        }
        return "social";
    }

    private static void incrementExperienceBucket(obj_id npc, boolean success) throws InterruptedException
    {
        if (success)
        {
            incrementCounter(npc, NPC_PROFILE_ROOT + ".exp.novice");
            int novice = getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.novice");
            if (novice > 0 && novice % 8 == 0)
            {
                incrementCounter(npc, NPC_PROFILE_ROOT + ".exp.adept");
            }
            int adept = getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.adept");
            if (adept > 0 && adept % 6 == 0)
            {
                incrementCounter(npc, NPC_PROFILE_ROOT + ".exp.expert");
            }
            return;
        }
        int novice = getIntObjVar(npc, NPC_PROFILE_ROOT + ".exp.novice");
        if (novice > 0)
        {
            setObjVar(npc, NPC_PROFILE_ROOT + ".exp.novice", novice - 1);
        }
    }

    private static String flattenStringArray(String[] values) throws InterruptedException
    {
        if (values == null || values.length == 0)
        {
            return "";
        }
        String out = "";
        for (int i = 0; i < values.length; i++)
        {
            if (values[i] == null)
            {
                continue;
            }
            if (out.length() > 0)
            {
                out += ",";
            }
            out += values[i];
        }
        return out;
    }

    private static void publishAggregateProfile(obj_id player, String archetype) throws InterruptedException
    {
        obj_id aggregateObject = getAggregateObject(player);
        if (!isIdValid(aggregateObject))
        {
            return;
        }
        String stampPath = PROFILE_ROOT + ".aggregateStamp";
        int nextAllowed = getIntObjVar(player, stampPath);
        if (nextAllowed > getGameTime())
        {
            return;
        }
        setObjVar(player, stampPath, getGameTime() + 3600);
        incrementCounter(aggregateObject, AGGREGATE_ROOT + "." + archetype);
        setObjVar(aggregateObject, AGGREGATE_ROOT + ".updatedAt", getGameTime());
    }

    private static obj_id getAggregateObject(obj_id source) throws InterruptedException
    {
        String scene = getCurrentSceneName();
        if (isIdValid(source))
        {
            scene = getLocation(source).area;
        }
        if (scene == null || scene.length() < 1)
        {
            scene = "tatooine";
        }
        obj_id planet = getPlanetByName(scene);
        if (isIdValid(planet))
        {
            return planet;
        }
        return getPlanetByName("tatooine");
    }

    private static void updateRollingObservationProfile(obj_id player, String channel, String token) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        String scene = resolveScene(player);
        if (!isLearningZoneEnabled(scene))
        {
            return;
        }

        obj_id aggregateObject = getAggregateObject(player);
        if (!isIdValid(aggregateObject))
        {
            return;
        }

        String profession = getPlayerProfessionBucket(player);
        if ("unknown".equals(profession))
        {
            profession = inferProfessionBucket(channel, token);
            incrementCounter(aggregateObject, AGGREGATE_ROOT + ".debug.professionWrites.inferred");
        }
        else
        {
            incrementCounter(aggregateObject, AGGREGATE_ROOT + ".debug.professionWrites.profiled");
        }
        if ("unknown".equals(profession))
        {
            incrementCounter(aggregateObject, AGGREGATE_ROOT + ".debug.professionWrites.unknown");
            return;
        }
        String activity = normalizeActivity(channel);
        String bucketRoot = getObservationBucketRoot(scene, profession, activity);

        int learningWeight = Math.max(1, (int)(getLearningAggressiveness() * 2.0f));
        incrementCounterBy(aggregateObject, bucketRoot + ".samples", learningWeight);
        incrementCounterBy(aggregateObject, bucketRoot + ".event." + token, learningWeight);
        setObjVar(aggregateObject, bucketRoot + ".updatedAt", getGameTime());

        String[] recent = utils.getStringArrayObjVar(player, TELEMETRY_ROOT + ".rolling." + activity, new String[0]);
        String compactToken = mapToPolicyToken(activity, token);
        String[] nextRecent = appendRollingWindow(recent, compactToken, 3);
        setObjVar(player, TELEMETRY_ROOT + ".rolling." + activity, nextRecent);
        if (nextRecent.length < 3)
        {
            return;
        }

        String sequenceKey = nextRecent[0] + "|" + nextRecent[1] + "|" + nextRecent[2];
        if (isSuspiciousSequence(sequenceKey))
        {
            incrementCounter(aggregateObject, bucketRoot + ".safety.suspiciousFiltered");
            return;
        }
        incrementCounterBy(aggregateObject, bucketRoot + ".sequence." + sequenceKey, learningWeight);
    }

    private static String[] appendRollingWindow(String[] history, String token, int maxSize) throws InterruptedException
    {
        if (history == null)
        {
            history = new String[0];
        }
        if (history.length >= maxSize)
        {
            String[] trimmed = new String[maxSize];
            for (int i = 1; i < history.length; i++)
            {
                trimmed[i - 1] = history[i];
            }
            trimmed[maxSize - 1] = token;
            return trimmed;
        }
        String[] expanded = new String[history.length + 1];
        for (int i = 0; i < history.length; i++)
        {
            expanded[i] = history[i];
        }
        expanded[expanded.length - 1] = token;
        return expanded;
    }

    private static String mapArchetypeToProfession(String archetype) throws InterruptedException
    {
        if ("grinder".equals(archetype))
        {
            return "combat";
        }
        if ("crafter_runner".equals(archetype))
        {
            return "trader";
        }
        return "social";
    }

    private static String getPlayerProfessionBucket(obj_id player) throws InterruptedException
    {
        String archetype = getStringObjVar(player, PROFILE_ROOT + ".archetype");
        if (archetype == null || archetype.length() < 1)
        {
            return "unknown";
        }
        return mapArchetypeToProfession(archetype);
    }

    private static String inferProfessionBucket(String channel, String token) throws InterruptedException
    {
        String signal = safeToken(channel) + "_" + safeToken(token);
        if (signal.indexOf("combat") > -1 || signal.indexOf("attack") > -1 || signal.indexOf("skill") > -1 || signal.indexOf("mission") > -1)
        {
            return "combat";
        }
        if (signal.indexOf("craft") > -1 || signal.indexOf("vendor") > -1 || signal.indexOf("trade") > -1 || signal.indexOf("shop") > -1)
        {
            return "trader";
        }
        if (signal.indexOf("social") > -1 || signal.indexOf("chat") > -1 || signal.indexOf("emote") > -1 || signal.indexOf("cantina") > -1)
        {
            return "social";
        }

        String activity = normalizeActivity(channel);
        if ("combat".equals(activity) || "movement".equals(activity))
        {
            return "combat";
        }
        if ("economic".equals(activity))
        {
            return "trader";
        }
        if ("social".equals(activity))
        {
            return "social";
        }
        return "combat";
    }

    private static void backfillUnknownObservationSamples(obj_id player, String profession) throws InterruptedException
    {
        if (!isIdValid(player) || profession == null || profession.length() < 1)
        {
            return;
        }
        String scene = resolveScene(player);
        if (!isLearningZoneEnabled(scene))
        {
            return;
        }
        obj_id aggregateObject = getAggregateObject(player);
        if (!isIdValid(aggregateObject))
        {
            return;
        }

        String[] activities = new String[]{"ambient", "movement", "combat", "economic", "social"};
        for (int i = 0; i < activities.length; i++)
        {
            String unknownRoot = getObservationBucketRoot(scene, "unknown", activities[i]);
            int samples = getIntObjVar(aggregateObject, unknownRoot + ".samples");
            if (samples <= 0)
            {
                continue;
            }
            String resolvedRoot = getObservationBucketRoot(scene, profession, activities[i]);
            incrementCounterBy(aggregateObject, resolvedRoot + ".samples", samples);
            setObjVar(aggregateObject, unknownRoot + ".samples", 0);
            setObjVar(aggregateObject, resolvedRoot + ".updatedAt", getGameTime());
            incrementCounter(aggregateObject, AGGREGATE_ROOT + ".debug.professionWrites.backfillBatches");
            incrementCounterBy(aggregateObject, AGGREGATE_ROOT + ".debug.professionWrites.backfillSamples", samples);
        }
    }

    private static String normalizeActivity(String activityType) throws InterruptedException
    {
        if (activityType == null || activityType.length() < 1)
        {
            return DEFAULT_ACTIVITY;
        }
        String lower = toLower(activityType);
        if (lower.indexOf("movement") > -1)
        {
            return "movement";
        }
        if (lower.indexOf("combat") > -1)
        {
            return "combat";
        }
        if (lower.indexOf("craft") > -1 || lower.indexOf("vendor") > -1 || lower.indexOf("shop") > -1)
        {
            return "economic";
        }
        if (lower.indexOf("social") > -1 || lower.indexOf("chat") > -1)
        {
            return "social";
        }
        return lower;
    }

    private static String getObservationBucketRoot(String scene, String profession, String activity) throws InterruptedException
    {
        return AGGREGATE_ROOT + ".observation." + safeToken(scene) + "." + safeToken(profession) + "." + safeToken(activity);
    }

    private static String safeToken(String value) throws InterruptedException
    {
        if (value == null || value.length() < 1)
        {
            return "unknown";
        }
        String lower = toLower(value);
        lower = lower.replace(' ', '_');
        lower = lower.replace('-', '_');
        return lower;
    }

    private static String mapToPolicyToken(String activity, String token) throws InterruptedException
    {
        if ("movement".equals(activity))
        {
            return "move_patrol";
        }
        if ("combat".equals(activity))
        {
            if (token != null && token.indexOf("skill") > -1)
            {
                return "skill_burst";
            }
            return "combat_focus";
        }
        if ("economic".equals(activity))
        {
            if (token != null && token.indexOf("craft") > -1)
            {
                return "craft_step";
            }
            return "trade_browse";
        }
        if ("social".equals(activity))
        {
            return "social_greet";
        }
        return "scan_area";
    }

    private static boolean isSuspiciousSequence(String sequence) throws InterruptedException
    {
        if (sequence == null || sequence.length() < 1)
        {
            return true;
        }
        String lower = toLower(sequence);
        if (lower.indexOf("dupe") > -1 || lower.indexOf("exploit") > -1 || lower.indexOf("abusive") > -1)
        {
            return true;
        }
        return lower.equals("trade_browse|trade_browse|trade_browse") || lower.equals("craft_step|craft_step|craft_step");
    }

    private static String[] getFallbackSequence(String archetype, String activity) throws InterruptedException
    {
        if ("grinder".equals(archetype) || "combat".equals(activity))
        {
            return new String[]{"combat_focus", "skill_burst", "retarget", "patrol_route"};
        }
        if ("crafter_runner".equals(archetype) || "economic".equals(activity))
        {
            return new String[]{"trade_browse", "craft_step", "trade_checkout", "mission_terminal"};
        }
        return new String[]{"social_greet", "social_pause", "social_respond", "cantina_idle"};
    }

    private static void trackSocialRepetitionMetrics(obj_id npc, String scene, String[] selected) throws InterruptedException
    {
        if (!isIdValid(npc) || selected == null || selected.length < 1)
        {
            return;
        }
        obj_id aggregate = getAggregateObject(npc);
        if (!isIdValid(aggregate))
        {
            return;
        }
        int now = getGameTime();
        setObjVar(npc, NPC_PROFILE_ROOT + ".metrics.social.lastAt", now);
        String key = safeToken(scene) + ".hour_" + ((now / 3600) % 24);
        for (int i = 0; i < selected.length; i++)
        {
            String token = safeToken(selected[i]);
            incrementCounter(aggregate, AGGREGATE_ROOT + ".metrics.social." + key + ".token." + token);
            int count = getIntObjVar(aggregate, AGGREGATE_ROOT + ".metrics.social." + key + ".token." + token);
            if (count > MAX_REPEAT_SOCIAL_EVENTS_PER_HOUR)
            {
                setObjVar(npc, NPC_PROFILE_ROOT + ".memory.avoid." + token, now + 1800);
            }
        }
    }

    private static boolean wasRecentlyUsedSequence(obj_id npc, String sequence) throws InterruptedException
    {
        String[] recent = utils.getStringArrayObjVar(npc, NPC_PROFILE_ROOT + ".memory.recentSequences", new String[0]);
        for (int i = 0; i < recent.length; i++)
        {
            if (sequence.equals(recent[i]))
            {
                return true;
            }
        }
        return false;
    }

    private static void rememberRecentSequence(obj_id npc, String sequence) throws InterruptedException
    {
        String[] recent = utils.getStringArrayObjVar(npc, NPC_PROFILE_ROOT + ".memory.recentSequences", new String[0]);
        setObjVar(npc, NPC_PROFILE_ROOT + ".memory.recentSequences", appendRollingWindow(recent, sequence, NPC_RECENT_STEP_MEMORY));
    }

    private static int getContextualSequenceWeight(String sequence, String archetype, String activity, String zoneTag, String timeBucket, String faction) throws InterruptedException
    {
        int weight = 2;
        String lower = toLower(sequence);
        if (lower.indexOf("combat") > -1 && ("grinder".equals(archetype) || "combat".equals(activity)))
        {
            weight += 4;
        }
        if (lower.indexOf("trade") > -1 && ("crafter_runner".equals(archetype) || "economic".equals(activity)))
        {
            weight += 4;
        }
        if (lower.indexOf("social") > -1 && "night".equals(timeBucket))
        {
            weight += 3;
        }
        if (lower.indexOf("cantina") > -1 && "hub".equals(zoneTag))
        {
            weight += 2;
        }
        if (lower.indexOf("patrol") > -1 && "imperial".equals(faction))
        {
            weight += 2;
        }
        return weight;
    }

    private static String[] buildWeightedPolicyCandidates(String archetype, String activity, String zoneTag, String timeBucket, String faction) throws InterruptedException
    {
        if ("grinder".equals(archetype) || "combat".equals(activity))
        {
            return new String[]{"combat_focus|skill_burst|retarget", "patrol_route|scan_area|combat_focus", "travel_hub|patrol_route|combat_focus", "mission_terminal|patrol_route|retarget"};
        }
        if ("crafter_runner".equals(archetype) || "economic".equals(activity))
        {
            return new String[]{"trade_browse|craft_step|trade_checkout", "mission_terminal|trade_browse|travel_hub", "vendor_route|craft_step|trade_checkout", "travel_hub|trade_browse|social_pause"};
        }
        if ("night".equals(timeBucket) || "hub".equals(zoneTag))
        {
            return new String[]{"social_greet|cantina_idle|social_respond", "cantina_idle|social_pause|chat_local", "travel_hub|social_greet|chat_faction", "rest_period|social_pause|cantina_idle"};
        }
        return new String[]{"social_greet|social_pause|social_respond", "travel_hub|social_pause|chat_local", "cantina_idle|chat_local|social_respond", "rest_period|social_pause|travel_hub"};
    }

    private static String getNpcFactionToken(obj_id npc) throws InterruptedException
    {
        int faction = factions.getFactionFlag(npc);
        if (faction == factions.FACTION_FLAG_IMPERIAL)
        {
            return "imperial";
        }
        if (faction == factions.FACTION_FLAG_REBEL)
        {
            return "rebel";
        }
        return "neutral";
    }

    private static String getZoneTagForScene(String scene) throws InterruptedException
    {
        String lower = safeToken(scene);
        if (lower.indexOf("coronet") > -1 || lower.indexOf("mos_eisley") > -1 || lower.indexOf("starport") > -1)
        {
            return "hub";
        }
        return "frontier";
    }

    private static String getDayTimeBucket() throws InterruptedException
    {
        int hour = (getGameTime() / 3600) % 24;
        if (hour >= 20 || hour < 6)
        {
            return "night";
        }
        if (hour >= 6 && hour < 12)
        {
            return "morning";
        }
        return "day";
    }

    private static void bootstrapNpcSocialGraph(obj_id npc, String archetype) throws InterruptedException
    {
        setObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.anchor", getPreferredHangout(resolveScene(npc)));
        setObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.friends", new String[]{archetype + "_friend_" + rand(1, 24), archetype + "_friend_" + rand(25, 48)});
        setObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.rivals", new String[]{archetype + "_rival_" + rand(1, 15)});
        setObjVar(npc, NPC_PROFILE_ROOT + ".socialGraph.hangouts", new String[]{getPreferredHangout(resolveScene(npc))});
    }

    private static String getPreferredHangout(String scene) throws InterruptedException
    {
        String zoneTag = getZoneTagForScene(scene);
        if ("hub".equals(zoneTag))
        {
            return pick(new String[]{"cantina", "mission_terminal", "travel_hub"});
        }
        return pick(new String[]{"outpost_fire", "shuttle_stop", "rest_camp"});
    }

    private static String getSpeciesForArchetype(String archetype) throws InterruptedException
    {
        if ("grinder".equals(archetype))
        {
            return pick(new String[]{"human", "zabrak", "trandoshan"});
        }
        if ("crafter_runner".equals(archetype))
        {
            return pick(new String[]{"bothan", "human", "rodian"});
        }
        return pick(new String[]{"human", "twilek", "rodian"});
    }

    private static String[][] getSpeciesAppearancePalette(String species) throws InterruptedException
    {
        if ("twilek".equals(species))
        {
            return new String[][]{{"crest_smooth", "crest_braided"}, {"aqua", "lavender", "jade"}, {"muted_rose", "soft_plum"}, {"amber", "violet", "cobalt"}, {"neon_trim", "desert_pastel", "city_teal"}};
        }
        if ("trandoshan".equals(species))
        {
            return new String[][]{{"spine_short", "spine_ridged"}, {"olive_scale", "tan_scale", "rust_scale"}, {"dark_umber", "khaki"}, {"gold", "lime", "orange"}, {"hunter_drab", "patrol_steel"}};
        }
        return new String[][]{{"short", "long", "ponytail", "shaved"}, {"fair", "tan", "deep", "olive"}, {"neutral", "ruby", "sand"}, {"brown", "green", "gray", "hazel"}, {"urban", "frontier", "formal", "scoundrel"}};
    }

    private static String[] getClothingSetForArchetype(String archetype) throws InterruptedException
    {
        if ("grinder".equals(archetype))
        {
            return new String[]{"patrol_armor_mix", "mercenary_layered", "frontline_light"};
        }
        if ("crafter_runner".equals(archetype))
        {
            return new String[]{"artisan_utility", "merchant_formal", "terminal_runner"};
        }
        return new String[]{"cantina_casual", "traveler_wrap", "city_citizen"};
    }

    private static String[] getNamingStyleForSpecies(String species) throws InterruptedException
    {
        if ("rodian".equals(species) || "trandoshan".equals(species))
        {
            return new String[]{"clipped_syllables", "apostrophe_clan"};
        }
        if ("twilek".equals(species))
        {
            return new String[]{"soft_dual_name", "flowing_given"};
        }
        return new String[]{"corellian_compound", "frontier_short", "classic_dual"};
    }

    private static String[] getRandomPersonalityTags(String archetype) throws InterruptedException
    {
        if ("grinder".equals(archetype))
        {
            return new String[]{pick(new String[]{"intense", "watchful", "stoic"}), pick(new String[]{"competitive", "duty_bound", "reckless"})};
        }
        if ("crafter_runner".equals(archetype))
        {
            return new String[]{pick(new String[]{"methodical", "deal_focused", "curious"}), pick(new String[]{"helpful", "reserved", "inventive"})};
        }
        return new String[]{pick(new String[]{"chatty", "playful", "laid_back"}), pick(new String[]{"empathetic", "gossipy", "dramatic"})};
    }

    private static String pick(String[] values) throws InterruptedException
    {
        if (values == null || values.length < 1)
        {
            return "unknown";
        }
        return values[rand(0, values.length - 1)];
    }

    private static String[] splitSequence(String sequence) throws InterruptedException
    {
        if (sequence == null || sequence.length() < 1)
        {
            return new String[0];
        }
        String[] parsed = split(sequence, '|');
        if (parsed == null)
        {
            return new String[0];
        }
        if (parsed.length <= MAX_POLICY_SEQUENCE)
        {
            return parsed;
        }
        String[] trimmed = new String[MAX_POLICY_SEQUENCE];
        for (int i = 0; i < MAX_POLICY_SEQUENCE; i++)
        {
            trimmed[i] = parsed[i];
        }
        return trimmed;
    }

    private static String resolveScene(obj_id source) throws InterruptedException
    {
        String scene = getCurrentSceneName();
        if (isIdValid(source))
        {
            scene = getLocation(source).area;
        }
        if (scene == null || scene.length() < 1)
        {
            scene = "tatooine";
        }
        return scene;
    }

    private static boolean isLearningZoneEnabled(String scene) throws InterruptedException
    {
        String allowList = getConfigSetting("GameServer", "behaviorLearningEnabledZones");
        if (allowList != null && allowList.length() > 0)
        {
            return csvContains(allowList, scene);
        }
        String denyList = getConfigSetting("GameServer", "behaviorLearningDisabledZones");
        if (denyList != null && denyList.length() > 0)
        {
            return !csvContains(denyList, scene);
        }
        return true;
    }

    private static int getMinPolicySampleCount() throws InterruptedException
    {
        int configured = utils.stringToInt(getConfigSetting("GameServer", "behaviorPolicyMinSampleCount"));
        return Math.max(MIN_POLICY_SAMPLE_COUNT, configured);
    }

    public static int getBehaviorProfileUpdateFrequencySeconds() throws InterruptedException
    {
        int configured = utils.stringToInt(getConfigSetting("GameServer", "behaviorProfileUpdateFrequencySeconds"));
        if (configured <= 0)
        {
            return PROFILE_BUILD_RATE_LIMIT_SEC;
        }
        return Math.max(30, configured);
    }

    public static float getLearningAggressiveness() throws InterruptedException
    {
        float configured = utils.stringToFloat(getConfigSetting("GameServer", "behaviorLearningAggressiveness"));
        if (configured <= 0.0f)
        {
            return 1.0f;
        }
        return Math.min(3.0f, Math.max(0.25f, configured));
    }

    private static boolean csvContains(String csv, String token) throws InterruptedException
    {
        if (csv == null || token == null)
        {
            return false;
        }
        String[] values = split(csv, ',');
        if (values == null)
        {
            return false;
        }
        String normalizedToken = safeToken(token);
        for (int i = 0; i < values.length; i++)
        {
            if (normalizedToken.equals(safeToken(values[i])))
            {
                return true;
            }
        }
        return false;
    }

    private static void appendSequenceToken(obj_id player, String token) throws InterruptedException
    {
        String path = TELEMETRY_ROOT + ".sequence";
        String[] existing = utils.getStringArrayObjVar(player, path, new String[0]);
        if (existing.length >= MAX_SEQUENCE)
        {
            String[] trimmed = new String[MAX_SEQUENCE];
            for (int i = 1; i < existing.length; i++)
            {
                trimmed[i - 1] = existing[i];
            }
            trimmed[MAX_SEQUENCE - 1] = token;
            setObjVar(player, path, trimmed);
            return;
        }
        String[] expanded = new String[existing.length + 1];
        for (int i = 0; i < existing.length; i++)
        {
            expanded[i] = existing[i];
        }
        expanded[expanded.length - 1] = token;
        setObjVar(player, path, expanded);
    }

    private static boolean passesRateLimit(obj_id actor, String bucket, int cooldownSeconds) throws InterruptedException
    {
        if (cooldownSeconds <= 0)
        {
            return true;
        }
        String key = TELEMETRY_ROOT + ".ratelimit." + bucket;
        int now = getGameTime();
        int nextAllowed = getIntObjVar(actor, key);
        if (nextAllowed > now)
        {
            return false;
        }
        setObjVar(actor, key, now + cooldownSeconds);
        return true;
    }

    private static boolean isExploitLikeToken(String token) throws InterruptedException
    {
        if (token == null)
        {
            return true;
        }
        String lower = toLower(token);
        return lower.indexOf("dupe") > -1 || lower.indexOf("grief") > -1 || lower.indexOf("abusive_chat") > -1 || lower.indexOf("movement_loop") > -1;
    }


    private static boolean isLoopExploit(obj_id player, String channel, String token) throws InterruptedException
    {
        if ("vendor".equals(channel) || "crafting".equals(channel))
        {
            String[] existing = utils.getStringArrayObjVar(player, TELEMETRY_ROOT + ".sequence", new String[0]);
            if (existing.length >= 3)
            {
                String a = existing[existing.length - 1];
                String b = existing[existing.length - 2];
                String c = existing[existing.length - 3];
                if (a != null && b != null && c != null && a.equals(b) && b.equals(c) && a.endsWith(token))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static void incrementCounter(obj_id actor, String path) throws InterruptedException
    {
        incrementCounterBy(actor, path, 1);
    }

    private static void incrementCounterBy(obj_id actor, String path, int amount) throws InterruptedException
    {
        if (amount <= 0)
        {
            return;
        }
        setObjVar(actor, path, getIntObjVar(actor, path) + amount);
    }
}
