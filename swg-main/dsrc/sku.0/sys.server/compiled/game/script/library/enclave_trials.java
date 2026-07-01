package script.library;

import script.dictionary;
import script.location;
import script.obj_id;
import script.library.create;
import script.library.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class enclave_trials extends script.base_script
{
    private static final String TRIAL_TABLE = "datatables/jedi/trial_cards.iff";
    public static final location MUSTAFAR_HALL_CENTER = new location(2144.0f, 120.0f, -6050.0f, "mustafar", null);
    public static final float MUSTAFAR_HALL_ALLOWED_RADIUS = 220.0f;
    public static final float MUSTAFAR_HALL_MAX_OWNER_DISTANCE = 280.0f;

    private static final String VAR_DAILY_CARD = "enclave.daily.cardId";
    private static final String VAR_DAILY_ASSIGNED = "enclave.daily.assigned";
    private static final String VAR_DAILY_STREAK = "enclave.daily.streak";
    private static final String VAR_DAILY_LAST_COMPLETED = "enclave.daily.lastCompleted";
    private static final String VAR_WAYPOINT_INDEX = "enclave.waypoints.active";
    private static final String VAR_WAYPOINT_PREFIX = "enclave.waypoints.card.";
    private static final int DAILY_RESET_SECONDS = 60 * 60 * 24;
    private static final int DAILY_STREAK_GRACE_SECONDS = DAILY_RESET_SECONDS * 2;
    private static final String VAR_WAYPOINT_ISSUED_PREFIX = "enclave.waypoints.issued.";

    private static Map<String, TrialDefinition> TRIALS_BY_ID;
    private static final Map<String, obj_id> CONTACTS = new LinkedHashMap<>();

    public static class TrialDefinition
    {
        public final String cardId;
        public final String category;
        public final String alignment;
        public final String phaseRequirement;
        public final int supplyGate;
        public final String objectiveType;
        public final int objectiveTarget;
        public final String description;
        public final int reward;
        public final int influenceReward;
        public final boolean repeatable;
        public final boolean dailyEligible;
        public final int dailyWeight;
        public final String hint;
        public final String objectiveDetail;
        public final String waypointName;
        public final String waypointPlanet;
        public final float waypointX;
        public final float waypointY;
        public final float waypointZ;
        public final location waypoint;
        public final String contactTemplate;
        public final String contactName;
        public final String contactScripts;
        public final String contactPlanet;
        public final float contactX;
        public final float contactY;
        public final float contactZ;
        public final float contactHeading;
        public final location contactLocation;

        public TrialDefinition()
        {
            this("", "", "neutral", "Any", 0, "", 0, "", 0, 0, true, true, 1, "", "", "", "", 0.0f, 0.0f, 0.0f, "", "", "", "", 0.0f, 0.0f, 0.0f, 0.0f);
        }

        public TrialDefinition(String cardId, String category, String alignment, String phaseRequirement, int supplyGate, String objectiveType, int objectiveTarget, String description, int reward, int influenceReward, boolean repeatable, boolean dailyEligible, int dailyWeight, String hint, String objectiveDetail, String waypointName, String waypointPlanet, float waypointX, float waypointY, float waypointZ, String contactTemplate, String contactName, String contactScripts, String contactPlanet, float contactX, float contactY, float contactZ, float contactHeading)
        {
            this.cardId = cardId;
            this.category = category;
            this.alignment = alignment != null ? alignment.toLowerCase() : "neutral";
            this.phaseRequirement = phaseRequirement != null && phaseRequirement.length() > 0 ? phaseRequirement : "Any";
            this.supplyGate = supplyGate;
            this.objectiveType = objectiveType;
            this.objectiveTarget = objectiveTarget;
            this.description = description;
            this.reward = reward;
            this.influenceReward = influenceReward;
            this.repeatable = repeatable;
            this.hint = hint != null ? hint : "";
            this.objectiveDetail = objectiveDetail != null ? objectiveDetail : "";
            int normalizedWeight = dailyWeight > 0 ? dailyWeight : 1;
            boolean allowDaily = dailyEligible && objectiveTarget > 0;
            this.dailyEligible = allowDaily;
            this.dailyWeight = allowDaily ? normalizedWeight : 0;
            this.waypointName = (waypointName != null && waypointName.length() > 0) ? waypointName : formatTrialName(cardId);
            this.waypointPlanet = waypointPlanet != null ? waypointPlanet : "";
            this.waypointX = waypointX;
            this.waypointY = waypointY;
            this.waypointZ = waypointZ;
            if (this.waypointPlanet.length() > 0)
            {
                this.waypoint = new location(this.waypointX, this.waypointY, this.waypointZ, this.waypointPlanet, null);
            }
            else
            {
                this.waypoint = null;
            }
            this.contactTemplate = contactTemplate != null ? contactTemplate : "";
            this.contactName = contactName != null ? contactName : "";
            this.contactScripts = contactScripts != null ? contactScripts : "";
            String normalizedContactPlanet = (contactPlanet != null && contactPlanet.length() > 0) ? contactPlanet : this.waypointPlanet;
            this.contactPlanet = normalizedContactPlanet != null ? normalizedContactPlanet : "";
            this.contactX = contactX;
            this.contactY = contactY;
            this.contactZ = contactZ;
            this.contactHeading = contactHeading;
            boolean hasCustomCoords = contactPlanet != null && contactPlanet.length() > 0;
            if (!hasCustomCoords)
            {
                hasCustomCoords = contactX != 0.0f || contactY != 0.0f || contactZ != 0.0f;
            }
            if (this.contactPlanet.length() > 0)
            {
                float spawnX = hasCustomCoords ? contactX : this.waypointX;
                float spawnY = hasCustomCoords ? contactY : this.waypointY;
                float spawnZ = hasCustomCoords ? contactZ : this.waypointZ;
                this.contactLocation = new location(spawnX, spawnY, spawnZ, this.contactPlanet, null);
            }
            else if (this.waypoint != null)
            {
                this.contactLocation = new location(this.waypointX, this.waypointY, this.waypointZ, this.waypointPlanet, null);
            }
            else
            {
                this.contactLocation = null;
            }
        }

        public boolean hasWaypoint()
        {
            return waypoint != null;
        }

        public boolean hasContact()
        {
            return contactTemplate != null && contactTemplate.length() > 0 && contactLocation != null;
        }
    }

    public enclave_trials()
    {
    }

    private static void ensureLoaded() throws InterruptedException
    {
        if (TRIALS_BY_ID != null)
        {
            return;
        }
        TRIALS_BY_ID = new LinkedHashMap<>();
        int rows = dataTableGetNumRows(TRIAL_TABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(TRIAL_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String cardId = row.getString("cardId");
            if (cardId == null || cardId.length() == 0)
            {
                continue;
            }
            String category = row.getString("category");
            String alignment = row.getString("alignment");
            String phaseRequirement = row.getString("phaseRequirement");
            String objectiveType = row.getString("objectiveType");
            int objectiveTarget = row.getInt("objectiveTarget");
            String description = row.getString("description");
            int reward = row.getInt("reward");
            int influence = row.getInt("influenceReward");
            boolean repeatable = row.getInt("repeatable") > 0;
            boolean dailyEligible = row.getInt("dailyEligible") > 0;
            int dailyWeight = row.getInt("dailyWeight");
            if (!dailyEligible && dailyWeight > 0)
            {
                dailyEligible = true;
            }
            if (!dailyEligible && category != null && category.length() > 0)
            {
                dailyEligible = !"story".equalsIgnoreCase(category);
            }
            String hint = row.getString("hint");
            String detail = row.getString("objectiveDetail");
            if (detail == null || detail.length() == 0)
            {
                detail = row.getString("instructions");
            }
            if (detail == null || detail.length() == 0)
            {
                detail = description;
            }
            String waypointName = row.getString("waypointName");
            String waypointPlanet = row.getString("waypointPlanet");
            float waypointX = row.getFloat("waypointX");
            float waypointY = row.getFloat("waypointY");
            float waypointZ = row.getFloat("waypointZ");
            String contactTemplate = row.getString("contactTemplate");
            String contactName = row.getString("contactName");
            String contactScripts = row.getString("contactScripts");
            String contactPlanet = row.getString("contactPlanet");
            float contactX = row.getFloat("contactX");
            float contactY = row.getFloat("contactY");
            float contactZ = row.getFloat("contactZ");
            float contactHeading = row.getFloat("contactHeading");
            TrialDefinition def = new TrialDefinition(cardId,
                    category,
                    alignment,
                    phaseRequirement,
                    row.getInt("supplyGate"),
                    objectiveType,
                    objectiveTarget,
                    description,
                    reward,
                    influence,
                    repeatable,
                    dailyEligible,
                    dailyWeight,
                    hint,
                    detail,
                    waypointName,
                    waypointPlanet,
                    waypointX,
                    waypointY,
                    waypointZ,
                    contactTemplate,
                    contactName,
                    contactScripts,
                    contactPlanet,
                    contactX,
                    contactY,
                    contactZ,
                    contactHeading);
            TRIALS_BY_ID.put(cardId, def);
        }
        ensureContactsSpawned();
    }

    private static void ensureContactsSpawned() throws InterruptedException
    {
        if (TRIALS_BY_ID == null)
        {
            return;
        }
        for (TrialDefinition def : TRIALS_BY_ID.values())
        {
            ensureContact(def);
        }
    }

    public static TrialDefinition getTrial(String cardId) throws InterruptedException
    {
        ensureLoaded();
        return TRIALS_BY_ID.get(cardId);
    }

    public static String[] getAvailableTrials(String alignment, String phase, float supplyScore) throws InterruptedException
    {
        TrialDefinition[] defs = getAvailableTrialDefinitions(alignment, phase, supplyScore);
        String[] results = new String[defs.length];
        for (int i = 0; i < defs.length; i++)
        {
            results[i] = defs[i].cardId;
        }
        return results;
    }

    public static TrialDefinition[] getAvailableTrialDefinitions(String alignment, String phase, float supplyScore) throws InterruptedException
    {
        ensureLoaded();
        List<TrialDefinition> results = new ArrayList<>();
        String align = alignment != null ? alignment.toLowerCase() : "neutral";
        String phaseName = (phase != null && phase.length() > 0) ? phase : "Preparation";
        for (TrialDefinition def : TRIALS_BY_ID.values())
        {
            if (isTrialEligible(def, align, phaseName, supplyScore))
            {
                results.add(def);
            }
        }
        return results.toArray(new TrialDefinition[0]);
    }

    public static TrialDefinition findTrialByObjectiveType(String objectiveType, String alignment, String phase, float supplyScore) throws InterruptedException
    {
        if (objectiveType == null || objectiveType.length() <= 0)
        {
            return null;
        }
        TrialDefinition[] available = getAvailableTrialDefinitions(alignment, phase, supplyScore);
        if (available == null || available.length <= 0)
        {
            return null;
        }
        for (TrialDefinition def : available)
        {
            if (def == null || def.objectiveType == null)
            {
                continue;
            }
            if (def.objectiveType.equalsIgnoreCase(objectiveType))
            {
                return def;
            }
        }
        return null;
    }

    private static boolean isTrialEligible(TrialDefinition def, String alignment, String phase, float supplyScore)
    {
        if (def == null)
        {
            return false;
        }
        if (!"neutral".equals(def.alignment) && (alignment == null || !def.alignment.equals(alignment)))
        {
            return false;
        }
        if (def.phaseRequirement != null && def.phaseRequirement.length() > 0 && !"Any".equalsIgnoreCase(def.phaseRequirement))
        {
            if (phase == null || !def.phaseRequirement.equalsIgnoreCase(phase))
            {
                return false;
            }
        }
        return supplyScore >= def.supplyGate;
    }

    public static TrialDefinition ensureDailyTrial(obj_id player, String alignment, String phase, float supplyScore, boolean forceRefresh) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(player))
        {
            return null;
        }
        int now = getCalendarTime();
        enforceDailyStreakWindow(player, now);
        String align = alignment != null ? alignment.toLowerCase() : "neutral";
        String phaseName = (phase != null && phase.length() > 0) ? phase : "Preparation";
        String currentCard = null;
        if (hasObjVar(player, VAR_DAILY_CARD))
        {
            currentCard = getStringObjVar(player, VAR_DAILY_CARD);
        }
        TrialDefinition current = currentCard != null ? TRIALS_BY_ID.get(currentCard) : null;
        boolean expired = hasDailyExpired(player, now);
        if (!forceRefresh && current != null && !expired && isTrialEligible(current, align, phaseName, supplyScore))
        {
            return current;
        }
        TrialDefinition replacement = pickDailyTrial(align, phaseName, supplyScore);
        if (replacement == null)
        {
            clearDailyTrial(player);
            return null;
        }
        setObjVar(player, VAR_DAILY_CARD, replacement.cardId);
        setObjVar(player, VAR_DAILY_ASSIGNED, now);
        return replacement;
    }

    public static TrialDefinition getAssignedDailyTrial(obj_id player) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(player) || !hasObjVar(player, VAR_DAILY_CARD))
        {
            return null;
        }
        String cardId = getStringObjVar(player, VAR_DAILY_CARD);
        TrialDefinition def = TRIALS_BY_ID.get(cardId);
        if (def == null)
        {
            clearDailyTrial(player);
        }
        return def;
    }

    public static void clearDailyTrial(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        if (hasObjVar(player, VAR_DAILY_CARD))
        {
            removeObjVar(player, VAR_DAILY_CARD);
        }
        if (hasObjVar(player, VAR_DAILY_ASSIGNED))
        {
            removeObjVar(player, VAR_DAILY_ASSIGNED);
        }
    }

    public static boolean registerCompletion(obj_id player, TrialDefinition def) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(player) || def == null)
        {
            return false;
        }
        if (hasObjVar(player, VAR_DAILY_CARD))
        {
            String dailyCard = getStringObjVar(player, VAR_DAILY_CARD);
            if (dailyCard != null && dailyCard.equals(def.cardId))
            {
                int streak = getIntObjVar(player, VAR_DAILY_STREAK);
                setObjVar(player, VAR_DAILY_STREAK, streak + 1);
                setObjVar(player, VAR_DAILY_LAST_COMPLETED, getCalendarTime());

                // 🔴 CLEANUP HERE
                cleanupTrialWaypoint(player, def.cardId);

                return true;
            }
        }
        if (hasObjVar(player, VAR_DAILY_STREAK))
        {
            removeObjVar(player, VAR_DAILY_STREAK);
        }

        return false;
    }


    public static int getDailyStreak(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return 0;
        }
        return getIntObjVar(player, VAR_DAILY_STREAK);
    }

    public static String getDailyStreakStatus(obj_id player) throws InterruptedException
    {
        int streak = getDailyStreak(player);
        if (streak <= 0)
        {
            return "Complete the daily mission highlighted by the enclave to begin building your reputation.";
        }
        return "Daily mission streak: " + streak + " completed.";
    }

    public static String describeObjective(TrialDefinition def)
    {
        if (def == null)
        {
            return "";
        }
        if (def.objectiveDetail != null && def.objectiveDetail.length() > 0)
        {
            return def.objectiveDetail;
        }
        if (def.description != null && def.description.length() > 0)
        {
            return def.description;
        }
        if (def.objectiveType != null && def.objectiveTarget > 0)
        {
            return "Complete " + def.objectiveTarget + " objective steps of type " + def.objectiveType + ".";
        }
        return "";
    }

    public static String describeRewards(TrialDefinition def)
    {
        if (def == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (def.reward > 0)
        {
            sb.append(def.reward).append(" Campaign Bonds");
        }
        if (def.influenceReward > 0)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(def.influenceReward).append(" Enclave influence");
        }
        if (sb.length() == 0)
        {
            return "";
        }
        sb.append('.');
        return sb.toString();
    }

    public static String describeRequirements(TrialDefinition def)
    {
        if (def == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (def.alignment != null && def.alignment.length() > 0 && !"neutral".equals(def.alignment))
        {
            sb.append("Alignment: ").append(capitalize(def.alignment));
        }
        if (def.phaseRequirement != null && def.phaseRequirement.length() > 0 && !"Any".equalsIgnoreCase(def.phaseRequirement))
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append("Phase: ").append(def.phaseRequirement);
        }
        if (def.supplyGate > 0)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append("Supply score ").append(def.supplyGate).append("+");
        }
        return sb.toString();
    }

    public static String getHint(TrialDefinition def, int progress)
    {
        if (def == null)
        {
            return "";
        }
        if (def.hint != null && def.hint.length() > 0)
        {
            return def.hint;
        }
        if (progress >= def.objectiveTarget && def.objectiveTarget > 0)
        {
            return "Objective complete. Report back to the enclave master.";
        }
        String type = def.objectiveType != null ? def.objectiveType.toLowerCase() : "";
        if (type.contains("kill"))
        {
            return "Defeat opponents tied to the " + def.category + " front on behalf of the Jedi enclave.";
        }
        if (type.contains("space"))
        {
            return "Engage in Jedi space operations supporting the " + def.category + " sector via the enclave starship terminals.";
        }
        if (type.contains("escort"))
        {
            return "Protect allied forces under Jedi orders until they safely reach their destination.";
        }
        if (type.contains("delivery"))
        {
            return "Carry out delivery assignments issued by the enclave mission terminals.";
        }
        if (def.hasWaypoint())
        {
            return "Use the datapad waypoint \"" + def.waypointName + "\" on " + formatPlanetName(def.waypointPlanet) + " to begin this Jedi assignment.";
        }
        if (def.description != null && def.description.length() > 0)
        {
            return def.description;
        }
        return "Consult the enclave terminals for precise directives.";
    }

    public static String getProgressStatus(TrialDefinition def, int progress)
    {
        if (def == null || def.objectiveTarget <= 0)
        {
            return "Awaiting mission data.";
        }
        if (progress <= 0)
        {
            return "No progress recorded. Begin pursuing the objective.";
        }
        if (progress >= def.objectiveTarget)
        {
            return "Objective complete. Return for debrief.";
        }
        int remaining = def.objectiveTarget - progress;
        if (remaining == 1)
        {
            return "One final step remains.";
        }
        return remaining + " steps remain.";
    }

    public static String formatTrialName(String trialId)
    {
        if (trialId == null || trialId.length() == 0)
        {
            return "Trial";
        }
        String spaced = trialId.replace('_', ' ');
        if (spaced.length() == 0)
        {
            return "Trial";
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String capitalize(String value)
    {
        if (value == null || value.length() == 0)
        {
            return "Neutral";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String formatPlanetName(String planet)
    {
        if (planet == null || planet.length() == 0)
        {
            return "the field";
        }
        if (planet.length() == 1)
        {
            return planet.toUpperCase();
        }
        return Character.toUpperCase(planet.charAt(0)) + planet.substring(1);
    }

    private static String resolveWaypointColor(String alignment)
    {
        if (alignment == null)
        {
            return "green";
        }
        if ("dark".equals(alignment))
        {
            return "purple";
        }
        if ("light".equals(alignment))
        {
            return "blue";
        }
        return "green";
    }

    private static boolean hasDailyExpired(obj_id player, int now) throws InterruptedException
    {
        int assignedAt = getIntObjVar(player, VAR_DAILY_ASSIGNED);
        if (assignedAt <= 0)
        {
            return true;
        }
        if (assignedAt > now)
        {
            return true;
        }
        return now - assignedAt >= DAILY_RESET_SECONDS;
    }

    private static void enforceDailyStreakWindow(obj_id player, int now) throws InterruptedException
    {
        if (!hasObjVar(player, VAR_DAILY_LAST_COMPLETED))
        {
            return;
        }
        int last = getIntObjVar(player, VAR_DAILY_LAST_COMPLETED);
        if (last <= 0)
        {
            return;
        }
        if (now - last > DAILY_STREAK_GRACE_SECONDS && hasObjVar(player, VAR_DAILY_STREAK))
        {
            removeObjVar(player, VAR_DAILY_STREAK);
        }
    }

    private static TrialDefinition pickDailyTrial(String alignment, String phase, float supplyScore) throws InterruptedException
    {
        TrialDefinition[] candidates = getAvailableTrialDefinitions(alignment, phase, supplyScore);
        if (candidates == null || candidates.length == 0)
        {
            return null;
        }
        List<TrialDefinition> eligible = new ArrayList<>();
        for (TrialDefinition def : candidates)
        {
            if (def.dailyEligible)
            {
                eligible.add(def);
            }
        }
        List<TrialDefinition> pool = eligible.isEmpty() ? Arrays.asList(candidates) : eligible;
        int totalWeight = 0;
        for (TrialDefinition def : pool)
        {
            totalWeight += Math.max(1, def.dailyWeight);
        }
        int roll = rand(0, Math.max(0, totalWeight - 1));
        int running = 0;
        for (TrialDefinition def : pool)
        {
            running += Math.max(1, def.dailyWeight);
            if (roll < running)
            {
                return def;
            }
        }
        return pool.get(0);
    }

    public static void refreshWaypoints(obj_id player, TrialDefinition daily, TrialDefinition[] available) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(player))
        {
            return;
        }
        Map<String, TrialDefinition> desired = new LinkedHashMap<>();
        if (daily != null)
        {
            desired.put(daily.cardId, daily);
        }
        if (available != null)
        {
            for (TrialDefinition def : available)
            {
                if (def != null)
                {
                    desired.put(def.cardId, def);
                }
            }
        }
        String[] existing = getStringArrayObjVar(player, VAR_WAYPOINT_INDEX);
        if (existing != null)
        {
            for (String cardId : existing)
            {
                if (cardId == null || cardId.length() == 0)
                {
                    continue;
                }
                if (!desired.containsKey(cardId))
                {
                    clearWaypoint(player, cardId);
                }
            }
        }
        List<String> active = new ArrayList<>();
        for (TrialDefinition def : desired.values())
        {
            if (def == null)
            {
                continue;
            }
            ensureContact(def);
            if (!def.hasWaypoint())
            {
                clearWaypoint(player, def.cardId);
                continue;
            }
            boolean highlight = daily != null && def.cardId.equals(daily.cardId);
            obj_id waypoint = ensureWaypoint(player, def, highlight);
            if (!isIdValid(waypoint))
            {
                continue;
            }
            active.add(def.cardId);
        }
        if (!active.isEmpty())
        {
            setObjVar(player, VAR_WAYPOINT_INDEX, active.toArray(new String[0]));
        }
        else if (hasObjVar(player, VAR_WAYPOINT_INDEX))
        {
            removeObjVar(player, VAR_WAYPOINT_INDEX);
        }
    }

    private static obj_id ensureWaypoint(obj_id player, TrialDefinition def, boolean highlight) throws InterruptedException
    {
        String varName = VAR_WAYPOINT_PREFIX + def.cardId;
        obj_id waypoint = obj_id.NULL_ID;
        if (hasObjVar(player, varName))
        {
            waypoint = getObjIdObjVar(player, varName);
            if (!isIdValid(waypoint) || !exists(waypoint))
            {
                removeObjVar(player, varName);
                waypoint = obj_id.NULL_ID;
            }
        }
        String issuedVar = VAR_WAYPOINT_ISSUED_PREFIX + def.cardId;

        if (!hasObjVar(player, issuedVar))
        {
            waypoint = createWaypointInDatapad(player, def.waypoint);
            if (!isIdValid(waypoint))
            {
                return obj_id.NULL_ID;
            }

            setObjVar(player, varName, waypoint);
            setObjVar(player, issuedVar, true);
        }
        else
        {
            // Waypoint was already issued; do NOT recreate it
            return obj_id.NULL_ID;
        }
        String color = highlight ? "yellow" : resolveWaypointColor(def.alignment);
        setWaypointName(waypoint, def.waypointName);
        setWaypointColor(waypoint, color);
        setWaypointActive(waypoint, true);
        setWaypointVisible(waypoint, true);
        return waypoint;
    }
    private static void cleanupTrialWaypoint(obj_id player, String cardId)
    {
        String waypointVar = VAR_WAYPOINT_PREFIX + cardId;
        String issuedVar = VAR_WAYPOINT_ISSUED_PREFIX + cardId;

        if (hasObjVar(player, waypointVar))
        {
            obj_id waypoint = getObjIdObjVar(player, waypointVar);

            if (isIdValid(waypoint) && exists(waypoint))
            {
                destroyWaypointInDatapad(player, waypoint);
            }

            removeObjVar(player, waypointVar);
        }

        if (hasObjVar(player, issuedVar))
        {
            removeObjVar(player, issuedVar);
        }
    }

    private static obj_id ensureContact(TrialDefinition def) throws InterruptedException
    {
        if (def == null || !def.hasContact())
        {
            return obj_id.NULL_ID;
        }
        obj_id existing = CONTACTS.get(def.cardId);
        if (isIdValid(existing) && exists(existing))
        {
            return existing;
        }
        if (existing != null)
        {
            CONTACTS.remove(def.cardId);
        }
        location spawn = def.contactLocation != null ? new location(def.contactLocation.x, def.contactLocation.y, def.contactLocation.z, def.contactLocation.area, def.contactLocation.cell) : def.waypoint;
        if (spawn == null)
        {
            return obj_id.NULL_ID;
        }
        obj_id contact = create.staticObject(def.contactTemplate, spawn);
        if (!isIdValid(contact))
        {
            return obj_id.NULL_ID;
        }
        CONTACTS.put(def.cardId, contact);
        if (def.contactName != null && def.contactName.length() > 0)
        {
            setName(contact, def.contactName);
        }
        else if (def.waypointName != null && def.waypointName.length() > 0)
        {
            setName(contact, def.waypointName);
        }
        setObjVar(contact, "enclave.trialCard", def.cardId);
        if (def.contactName != null && def.contactName.length() > 0)
        {
            setObjVar(contact, "enclave.contactName", def.contactName);
        }
        setYaw(contact, def.contactHeading);
        if (def.contactScripts != null && def.contactScripts.length() > 0)
        {
            String[] scripts = utils.split(def.contactScripts, ',');
            if (scripts != null)
            {
                for (String scriptName : scripts)
                {
                    if (scriptName == null)
                    {
                        continue;
                    }
                    scriptName = scriptName.trim();
                    if (scriptName.length() == 0)
                    {
                        continue;
                    }
                    if (!hasScript(contact, scriptName))
                    {
                        attachScript(contact, scriptName);
                    }
                }
            }
        }
        return contact;
    }

    private static void clearWaypoint(obj_id player, String cardId) throws InterruptedException
    {
        String varName = VAR_WAYPOINT_PREFIX + cardId;
        if (!hasObjVar(player, varName))
        {
            return;
        }
        obj_id waypoint = getObjIdObjVar(player, varName);
        if (isIdValid(waypoint))
        {
            destroyWaypointInDatapad(waypoint, player);
        }
        removeObjVar(player, varName);
    }

    public static void recordProgress(obj_id player, String cardId, int amount) throws InterruptedException
    {
        ensureLoaded();
        if (!isIdValid(player) || cardId == null || cardId.length() == 0)
        {
            return;
        }
        dictionary params = new dictionary();
        params.put("cardId", cardId);
        params.put("amount", amount);
        messageTo(player, "updateTrialProgress", params, 0.0f, false);
    }
}
