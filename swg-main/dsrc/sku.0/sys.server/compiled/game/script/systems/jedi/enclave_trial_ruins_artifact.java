package script.systems.jedi;

import script.menu_info;
import script.menu_info_data;
import script.menu_info_types;
import script.obj_id;
import script.dictionary;
import script.location;
import script.library.create;

import java.util.ArrayList;
import java.util.List;

public class enclave_trial_ruins_artifact extends script.base_script
{
    private static final String TRIAL_RUINS = "trial_dantooine_ruins_artifact";
    private static final String TRIAL_MUSTAFAR_HALL = "trial_mustafar_hall_dark_jedi_master";
    private static final String VAR_MUSTAFAR_WAYPOINT = "enclave.ruins.mustafarWaypoint";
    private static final String VAR_ACTIVE_ARTIFACT = "enclave.ruins.activeArtifact";
    private static final String VAR_GUARDIAN_RETRY_COOLDOWN = "enclave.ruins.guardianRetryCooldown";
    private static final String VAR_ACTIVE_CARD = "enclave.activeCard";
    private static final String VAR_ENCLAVE_PHASE = "enclave.phase";
    private static final String VAR_STORY_HANDOFF = "enclave.story.handoff";
    private static final String VAR_GUARDIAN_VARIANT = "enclave.ruins.guardianVariant";
    private static final String VAR_ENCOUNTER_TOKEN = "enclave.ruins.encounterToken";
    private static final String OBJ_RUINS_MINIBOSS_AMBUSH = "ruins_miniboss_ambush";
    private static final String OBJ_RUINS_BOSS_CHAMBER = "ruins_boss_chamber";
    private static final int GUARDIAN_RETRY_COOLDOWN_SECONDS = 5;
    private static final String MUSTAFAR_WAYPOINT_NAME = "Mustafar Abandoned Ruins - Dark Jedi Hall";
    private static final location MUSTAFAR_DESTINATION = new location(-2215.0f, 120.0f, 3110.0f, "mustafar", null);

    private static final GuardianVariant[] VARIANT_TABLE = new GuardianVariant[]{
        new GuardianVariant("initiates", new String[]{"dark_jedi_knight", "dark_adept"}, new String[]{"knockback", "shield"}, false, 0, 0),
        new GuardianVariant("disciples", new String[]{"dark_jedi_master", "dark_adept"}, new String[]{"area_denial", "interrupt"}, false, 2, 1),
        new GuardianVariant("conclave", new String[]{"dark_jedi_master", "dark_jedi_knight", "dark_adept"}, new String[]{"combo_anchor", "area_denial", "shield"}, true, 4, 2),
        new GuardianVariant("war_council", new String[]{"dark_jedi_master", "dark_jedi_master", "dark_jedi_knight"}, new String[]{"combo_anchor", "interrupt", "area_denial"}, true, 6, 4)
    };

    public enclave_trial_ruins_artifact()
    {
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return SCRIPT_CONTINUE;
        }
        int menu = mi.addRootMenu(menu_info_types.ITEM_USE, null);
        menu_info_data data = mi.getMenuItemById(menu);
        if (data != null)
        {
            data.setServerNotify(true);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item != menu_info_types.ITEM_USE)
        {
            return SCRIPT_CONTINUE;
        }
        triggerGuardianEncounter(self, player);
        return SCRIPT_CONTINUE;
    }

    public int OnIncapacitated(obj_id self, obj_id killer) throws InterruptedException
    {
        handleGuardianDefeat(self, killer);
        return SCRIPT_CONTINUE;
    }

    private void triggerGuardianEncounter(obj_id artifact, obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        obj_id owner = getObjIdObjVar(artifact, "enclave.ruins.owner");
        if (!isIdValid(owner) || owner != player)
        {
            sendSystemMessage(player, "This artifact resonates with another Jedi.", "");
            return;
        }
        if (hasObjVar(player, "enclave.ruins.guardiansRemaining"))
        {
            sendSystemMessage(player, "The guardians are already hunting you.", "");
            return;
        }
        int now = getGameTime();
        int retryAvailableAt = getIntObjVar(player, VAR_GUARDIAN_RETRY_COOLDOWN);
        if (retryAvailableAt > now)
        {
            sendSystemMessage(player, "The artifact is unstable. Wait a moment, then try invoking the guardians again.", "");
            return;
        }
        if (retryAvailableAt > 0)
        {
            removeObjVar(player, VAR_GUARDIAN_RETRY_COOLDOWN);
        }
        int playerLevel = getLevel(player);
        GuardianVariant variant = selectVariant(player, playerLevel);
        int guardianLevel = playerLevel - 2 + variant.levelOffset;
        if (guardianLevel < 35)
        {
            guardianLevel = 35;
        }
        if (guardianLevel > 82)
        {
            guardianLevel = 82;
        }
        location origin = getLocation(artifact);
        if (origin == null)
        {
            origin = getLocation(player);
        }
        if (origin == null)
        {
            return;
        }
        List<obj_id> guardians = new ArrayList<obj_id>();
        String encounterToken = String.valueOf(now) + ":" + player + ":" + variant.id;
        for (int i = 0; i < variant.templates.length; i++)
        {
            if (i >= 2 && !variant.hasThirdAdd)
            {
                continue;
            }
            location spawn = getGuardianSpawnLocation(origin, i);
            obj_id guardian = create.createCreature(variant.templates[i], spawn, guardianLevel, true, false);
            if (!isIdValid(guardian))
            {
                for (obj_id cleanup : guardians)
                {
                    if (isIdValid(cleanup))
                    {
                        destroyObject(cleanup);
                    }
                }
                setObjVar(player, VAR_GUARDIAN_RETRY_COOLDOWN, now + GUARDIAN_RETRY_COOLDOWN_SECONDS);
                sendSystemMessage(player, "The summoning falters and the guardians disperse. Please wait a moment and try the artifact again.", "");
                return;
            }
            markGuardian(guardian, player, variant.roles[i], variant.id, encounterToken);
            guardians.add(guardian);
        }
        if (guardians.size() < 2)
        {
            for (obj_id cleanup : guardians)
            {
                if (isIdValid(cleanup))
                {
                    destroyObject(cleanup);
                }
            }
            setObjVar(player, VAR_GUARDIAN_RETRY_COOLDOWN, now + GUARDIAN_RETRY_COOLDOWN_SECONDS);
            sendSystemMessage(player, "The summoning falters and the guardians disperse. Please wait a moment and try the artifact again.", "");
            return;
        }
        setObjVar(player, "enclave.ruins.guardiansRemaining", guardians.size());
        setObjVar(player, VAR_GUARDIAN_VARIANT, variant.id);
        setObjVar(player, VAR_ENCOUNTER_TOKEN, encounterToken);
        removeObjVar(player, VAR_ACTIVE_ARTIFACT);
        removeObjVar(player, VAR_GUARDIAN_RETRY_COOLDOWN);
        String preview = guardians.size() > 2 ? "Three" : "Two";
        sendSystemMessage(player, preview + " Jedi guardians emerge in a " + variant.id + " formation. Watch for force telegraphs and coordinated assaults.", "");
        for (obj_id guardian : guardians)
        {
            startCombat(guardian, player);
        }
        destroyObject(artifact);
    }

    private void markGuardian(obj_id guardian, obj_id player, String role, String variantId, String encounterToken) throws InterruptedException
    {
        setObjVar(guardian, "enclave.ruins.owner", player);
        setObjVar(guardian, "enclave.ruins.cardId", TRIAL_RUINS);
        setObjVar(guardian, "enclave.ruins.guardianRole", role);
        setObjVar(guardian, "enclave.ruins.variant", variantId);
        setObjVar(guardian, VAR_ENCOUNTER_TOKEN, encounterToken);
        attachScript(guardian, "systems.jedi.enclave_trial_ruins_artifact");
        attachScript(guardian, "systems.jedi.enclave_ruins_guardian_behavior");
    }

    private void handleGuardianDefeat(obj_id guardian, obj_id killer) throws InterruptedException
    {
        if (!hasObjVar(guardian, "enclave.ruins.owner"))
        {
            return;
        }
        obj_id owner = getObjIdObjVar(guardian, "enclave.ruins.owner");
        if (!isIdValid(owner) || !isPlayer(owner))
        {
            return;
        }
        int remaining = getIntObjVar(owner, "enclave.ruins.guardiansRemaining");
        if (remaining <= 0)
        {
            return;
        }
        remaining--;
        if (remaining > 0)
        {
            setObjVar(owner, "enclave.ruins.guardiansRemaining", remaining);
            sendSystemMessage(owner, "A guardian falls. One remains in the ruins.", "");
            return;
        }
        removeObjVar(owner, "enclave.ruins.guardiansRemaining");
        recordObjectiveProgress(owner, OBJ_RUINS_MINIBOSS_AMBUSH, 1);
        dictionary params = new dictionary();
        params.put("cardId", TRIAL_RUINS);
        params.put("amount", 1);
        messageTo(owner, "updateTrialProgress", params, 0.0f, false);
        applyVariantCompletionBonus(owner);
        recordObjectiveProgress(owner, OBJ_RUINS_BOSS_CHAMBER, 1);
        setObjVar(owner, VAR_ACTIVE_CARD, TRIAL_MUSTAFAR_HALL);
        setObjVar(owner, VAR_ENCLAVE_PHASE, "MustafarHall");
        setObjVar(owner, VAR_STORY_HANDOFF, TRIAL_MUSTAFAR_HALL);
        if (!hasObjVar(owner, "enclave.progress." + TRIAL_MUSTAFAR_HALL))
        {
            setObjVar(owner, "enclave.progress." + TRIAL_MUSTAFAR_HALL, 0);
        }
        sendSystemMessage(owner, "The ruins fall silent. A waypoint to Mustafar's abandoned ruins has been transmitted for your next trial against the Dark Jedi Master.", "");
        createMustafarWaypoint(owner);
    }

    private void applyVariantCompletionBonus(obj_id owner) throws InterruptedException
    {
        if (!hasObjVar(owner, VAR_GUARDIAN_VARIANT))
        {
            removeObjVar(owner, VAR_ENCOUNTER_TOKEN);
            return;
        }
        String variantId = getStringObjVar(owner, VAR_GUARDIAN_VARIANT);
        GuardianVariant variant = getVariantById(variantId);
        if (variant == null)
        {
            removeObjVar(owner, VAR_GUARDIAN_VARIANT);
            removeObjVar(owner, VAR_ENCOUNTER_TOKEN);
            return;
        }
        int tokenBonus = variant.tokenBonus;
        int influenceBonus = variant.influenceBonus;
        if (tokenBonus > 0)
        {
            int bonds = getIntObjVar(owner, "gcwCampaign.bonds");
            setObjVar(owner, "gcwCampaign.bonds", bonds + tokenBonus);
        }
        if (influenceBonus > 0)
        {
            int influence = getIntObjVar(owner, "enclave.influence");
            setObjVar(owner, "enclave.influence", influence + influenceBonus);
        }
        if (tokenBonus > 0 || influenceBonus > 0)
        {
            sendSystemMessage(owner, "Harder guardian variant cleared: +" + tokenBonus + " bonus campaign bonds and +" + influenceBonus + " bonus enclave influence.", "");
        }
        removeObjVar(owner, VAR_GUARDIAN_VARIANT);
        removeObjVar(owner, VAR_ENCOUNTER_TOKEN);
    }

    private location getGuardianSpawnLocation(location origin, int index)
    {
        if (index == 0)
        {
            return new location(origin.x + 6.0f, origin.y, origin.z + 2.0f, origin.area, origin.cell);
        }
        if (index == 1)
        {
            return new location(origin.x - 5.0f, origin.y, origin.z - 3.0f, origin.area, origin.cell);
        }
        return new location(origin.x + 1.0f, origin.y, origin.z - 7.0f, origin.area, origin.cell);
    }

    private GuardianVariant selectVariant(obj_id player, int level)
    {
        int tier = 0;
        int completions = getIntObjVar(player, "enclave.history." + TRIAL_RUINS);
        if (completions >= 2)
        {
            tier++;
        }
        if (completions >= 5)
        {
            tier++;
        }
        if (level >= 60)
        {
            tier++;
        }
        if (level >= 80)
        {
            tier++;
        }
        String currentDaily = hasObjVar(player, "enclave.dailyCard") ? getStringObjVar(player, "enclave.dailyCard") : null;
        if (TRIAL_RUINS.equals(currentDaily))
        {
            tier++;
        }
        String phase = hasObjVar(player, VAR_ENCLAVE_PHASE) ? getStringObjVar(player, VAR_ENCLAVE_PHASE) : null;
        if (phase != null && phase.length() > 0 && !"RuinsArtifact".equals(phase))
        {
            tier++;
        }
        if (hasObjVar(player, "enclave.modifier.ruinsGuardianTier"))
        {
            tier += getIntObjVar(player, "enclave.modifier.ruinsGuardianTier");
        }
        if (tier < 0)
        {
            tier = 0;
        }
        if (tier >= VARIANT_TABLE.length)
        {
            tier = VARIANT_TABLE.length - 1;
        }
        return VARIANT_TABLE[tier];
    }

    private GuardianVariant getVariantById(String variantId)
    {
        for (GuardianVariant variant : VARIANT_TABLE)
        {
            if (variant.id.equals(variantId))
            {
                return variant;
            }
        }
        return null;
    }

    public static class GuardianVariant
    {
        public String id;
        public String[] templates;
        public String[] roles;
        public boolean hasThirdAdd;
        public int tokenBonus;
        public int influenceBonus;
        public int levelOffset;

        public GuardianVariant()
        {
        }

        public GuardianVariant(String id, String[] templates, String[] roles, boolean hasThirdAdd, int tokenBonus, int influenceBonus)
        {
            this.id = id;
            this.templates = templates;
            this.roles = roles;
            this.hasThirdAdd = hasThirdAdd;
            this.tokenBonus = tokenBonus;
            this.influenceBonus = influenceBonus;
            this.levelOffset = hasThirdAdd ? 1 : 0;
        }
    }

    private void recordObjectiveProgress(obj_id player, String objectiveType, int amount) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player) || objectiveType == null || objectiveType.length() <= 0)
        {
            return;
        }
        dictionary progress = new dictionary();
        progress.put("objectiveType", objectiveType);
        progress.put("amount", amount);
        messageTo(player, "updateTrialProgress", progress, 0.0f, false);
    }

    private void createMustafarWaypoint(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        obj_id waypoint = obj_id.NULL_ID;
        if (hasObjVar(player, VAR_MUSTAFAR_WAYPOINT))
        {
            waypoint = getObjIdObjVar(player, VAR_MUSTAFAR_WAYPOINT);
            if (!isIdValid(waypoint) || !exists(waypoint))
            {
                removeObjVar(player, VAR_MUSTAFAR_WAYPOINT);
                waypoint = obj_id.NULL_ID;
            }
        }
        if (!isIdValid(waypoint))
        {
            waypoint = findWaypointInDatapad(player, MUSTAFAR_WAYPOINT_NAME, MUSTAFAR_DESTINATION, 32.0f);
        }
        if (!isIdValid(waypoint))
        {
            waypoint = createWaypointInDatapad(player, MUSTAFAR_DESTINATION);
            if (!isIdValid(waypoint))
            {
                return;
            }
        }
        setWaypointName(waypoint, MUSTAFAR_WAYPOINT_NAME);
        setWaypointColor(waypoint, "red");
        setWaypointActive(waypoint, true);
        setWaypointVisible(waypoint, true);
        setObjVar(player, VAR_MUSTAFAR_WAYPOINT, waypoint);
    }

    private obj_id findWaypointInDatapad(obj_id player, String expectedName, location expectedLocation, float tolerance) throws InterruptedException
    {
        obj_id[] waypoints = getWaypointsInDatapad(player);
        if (waypoints == null || waypoints.length == 0)
        {
            return obj_id.NULL_ID;
        }
        for (obj_id waypoint : waypoints)
        {
            if (!isIdValid(waypoint) || !exists(waypoint))
            {
                continue;
            }
            String name = getWaypointName(waypoint);
            if (expectedName != null && expectedName.length() > 0 && (name == null || !expectedName.equals(name)))
            {
                continue;
            }
            location waypointLocation = getWaypointLocation(waypoint);
            if (waypointLocation == null || waypointLocation.area == null || !expectedLocation.area.equals(waypointLocation.area))
            {
                continue;
            }
            if (getDistance(waypointLocation, expectedLocation) <= tolerance)
            {
                return waypoint;
            }
        }
        return obj_id.NULL_ID;
    }
}
