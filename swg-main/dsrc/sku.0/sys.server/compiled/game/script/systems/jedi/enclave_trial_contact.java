package script.systems.jedi;

import script.dictionary;
import script.menu_info;
import script.menu_info_data;
import script.menu_info_types;
import script.obj_id;
import script.library.ai_lib;
import script.library.enclave_trials;
import script.library.enclave_trials.TrialDefinition;
import script.library.sui;
import script.location;
import script.library.create;

public class enclave_trial_contact extends script.base_script
{
    private static final String VAR_CARD = "enclave.trialCard";
    private static final String VAR_NAME = "enclave.contactName";
    private static final String TRIAL_RUINS = "trial_dantooine_ruins_artifact";
    private static final String TRIAL_MUSTAFAR_HALL = "trial_mustafar_hall_dark_jedi_master";
    private static final String OBJ_MUSTAFAR_HALL_DEFEAT = "mustafar_hall_master_defeat";
    private static final String VAR_ACTIVE_ARTIFACT = "enclave.ruins.activeArtifact";
    private static final float RUINS_RADIUS = 250.0f;
    private static final String OBJ_RUINS_RECON = "ruins_recon";
    private static final String OBJ_RUINS_RELIC_EXTRACTION = "ruins_relic_extraction";

    public enclave_trial_contact()
    {
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        initializeContact(self);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        initializeContact(self);
        return SCRIPT_CONTINUE;
    }

    private void initializeContact(obj_id self) throws InterruptedException
    {
        setInvulnerable(self, true);
        setCreatureStatic(self, true);
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_SENTINEL);
        setCondition(self, CONDITION_CONVERSABLE);
        setCondition(self, CONDITION_INTERESTING);
        if (hasObjVar(self, VAR_NAME))
        {
            String customName = getStringObjVar(self, VAR_NAME);
            if (customName != null && customName.length() > 0)
            {
                setName(self, customName);
            }
        }
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
        presentBriefing(self, player);
        return SCRIPT_CONTINUE;
    }

    public int OnStartNpcConversation(obj_id self, obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return SCRIPT_CONTINUE;
        }
        presentBriefing(self, player);
        npcEndConversation(player);
        return SCRIPT_CONTINUE;
    }

    private void presentBriefing(obj_id self, obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !isPlayer(player))
        {
            return;
        }
        String cardId = getStringObjVar(self, VAR_CARD);
        if (cardId == null || cardId.length() == 0)
        {
            sendSystemMessage(player, "This contact is awaiting updated enclave directives.", "");
            return;
        }
        TrialDefinition def = enclave_trials.getTrial(cardId);
        if (def == null)
        {
            sendSystemMessage(player, "The enclave contact cannot locate their briefing details right now.", "");
            return;
        }
        if (TRIAL_RUINS.equals(def.cardId) && tryStartRuinsArtifactEncounter(player, def))
        {
            return;
        }
        if ((TRIAL_MUSTAFAR_HALL.equals(def.cardId) || OBJ_MUSTAFAR_HALL_DEFEAT.equals(def.objectiveType)) && tryLaunchMustafarHallTrial(self, player, def))
        {
            return;
        }
        faceToBehavior(self, player);
        StringBuilder briefing = new StringBuilder();
        briefing.append("Trial: ").append(enclave_trials.formatTrialName(def.cardId)).append('\n');
        String requirements = enclave_trials.describeRequirements(def);
        if (requirements != null && requirements.length() > 0)
        {
            briefing.append("Requirements: ").append(requirements).append('\n');
        }
        String objective = enclave_trials.describeObjective(def);
        if (objective != null && objective.length() > 0)
        {
            briefing.append("Objective: ").append(objective).append('\n');
        }
        int progress = getIntObjVar(player, "enclave.progress." + def.cardId);
        String hint = enclave_trials.getHint(def, progress);
        if (hint != null && hint.length() > 0)
        {
            briefing.append("Hint: ").append(hint).append('\n');
        }
        String rewards = enclave_trials.describeRewards(def);
        if (rewards != null && rewards.length() > 0)
        {
            briefing.append("Rewards: ").append(rewards).append('\n');
        }
        int streak = enclave_trials.getDailyStreak(player);
        if (streak > 0)
        {
            String streakStatus = enclave_trials.getDailyStreakStatus(player);
            if (streakStatus != null && streakStatus.length() > 0)
            {
                briefing.append(streakStatus).append('\n');
            }
        }
        String message = briefing.toString().trim();
        if (message.length() == 0)
        {
            message = "This contact has no additional instructions at this time.";
        }
        sui.msgbox(self, player, message, sui.OK_ONLY, "Enclave Trial Briefing", "handleEnclaveTrialContact");
        messageTo(player, "requestEnclaveTrialStatus", null, 0.5f, false);
    }

    private boolean tryStartRuinsArtifactEncounter(obj_id player, TrialDefinition def) throws InterruptedException
    {
        if (!isIdValid(player) || def == null)
        {
            return false;
        }
        if (getIntObjVar(player, "enclave.progress." + def.cardId) >= def.objectiveTarget)
        {
            return false;
        }
        if (hasObjVar(player, "enclave.ruins.guardiansRemaining"))
        {
            sendSystemMessage(player, "The guardians are already pursuing you. Defeat them before trying to recover another artifact.", "");
            return true;
        }
        if (hasObjVar(player, VAR_ACTIVE_ARTIFACT))
        {
            obj_id existingArtifact = getObjIdObjVar(player, VAR_ACTIVE_ARTIFACT);
            if (isIdValid(existingArtifact) && exists(existingArtifact))
            {
                sendSystemMessage(player, "Your artifact marker is already placed in the ruins. Touch it to recover the relic.", "");
                return true;
            }
            removeObjVar(player, VAR_ACTIVE_ARTIFACT);
        }
        location here = getLocation(player);
        location ruinsCenter = resolveRuinsCenter(def);
        if (here == null || here.area == null || ruinsCenter == null || ruinsCenter.area == null || !ruinsCenter.area.equals(here.area) || getDistance(here, ruinsCenter) > RUINS_RADIUS)
        {
            sendSystemMessage(player, "Travel to the abandoned ruins waypoint on Dantooine before placing the Jedi artifact.", "");
            return true;
        }
        obj_id artifact = create.object("object/tangible/loot/quest/wind_crystal.iff", here);
        if (!isIdValid(artifact))
        {
            sendSystemMessage(player, "The ritual failed to manifest the Jedi artifact. Try again in a moment.", "");
            return true;
        }
        setName(artifact, "Placed Jedi Artifact");
        setObjVar(artifact, "enclave.ruins.cardId", def.cardId);
        setObjVar(artifact, "enclave.ruins.owner", player);
        attachScript(artifact, "systems.jedi.enclave_trial_ruins_artifact");
        setObjVar(player, VAR_ACTIVE_ARTIFACT, artifact);
        recordObjectiveProgress(player, OBJ_RUINS_RECON, 1);
        recordObjectiveProgress(player, OBJ_RUINS_RELIC_EXTRACTION, 1);
        sendSystemMessage(player, "You place the Jedi artifact in the abandoned ruins. Recover it to trigger the guardian trial.", "");
        return true;
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

    private location resolveRuinsCenter(TrialDefinition def)
    {
        if (def == null)
        {
            return null;
        }
        if (def.contactLocation != null)
        {
            return def.contactLocation;
        }
        return def.waypoint;
    }

    private boolean tryLaunchMustafarHallTrial(obj_id self, obj_id player, TrialDefinition def) throws InterruptedException
    {
        if (!isIdValid(self) || !isIdValid(player) || def == null)
        {
            return false;
        }
        dictionary params = new dictionary();
        params.put("player", player);
        params.put("cardId", def.cardId);
        messageTo(self, "launchMustafarHallTrial", params, 0.0f, false);
        return true;
    }

}
