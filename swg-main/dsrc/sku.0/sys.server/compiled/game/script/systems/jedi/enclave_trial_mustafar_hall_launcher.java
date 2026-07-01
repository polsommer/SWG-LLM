package script.systems.jedi;

import script.dictionary;
import script.location;
import script.obj_id;
import script.library.create;
import script.library.enclave_trials;

public class enclave_trial_mustafar_hall_launcher extends script.base_script
{
    private static final String TRIAL_RUINS = "trial_dantooine_ruins_artifact";
    private static final String TRIAL_MUSTAFAR_HALL = "trial_mustafar_hall_dark_jedi_master";
    private static final String VAR_ACTIVE_CARD = "enclave.activeCard";
    private static final String VAR_ENCLAVE_PHASE = "enclave.phase";
    private static final String VAR_STORY_HANDOFF = "enclave.story.handoff";
    private static final String VAR_ACTIVE_BOSS = "enclave.mustafar.hall.activeBoss";
    private static final String BOSS_TABLE = "datatables/jedi/mustafar_hall_bosses.iff";
    private static final String DEFAULT_BOSS_ID = "hall_initiate";

    public enclave_trial_mustafar_hall_launcher()
    {
    }

    public int launchMustafarHallTrial(obj_id self, dictionary params) throws InterruptedException
    {
        if (params == null || !params.containsKey("player"))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id player = params.getObjId("player");
        if (!isIdValid(player) || !isPlayer(player))
        {
            return SCRIPT_CONTINUE;
        }
        String requestedCardId = params.getString("cardId");
        if (requestedCardId == null || requestedCardId.length() <= 0)
        {
            return SCRIPT_CONTINUE;
        }
        dictionary bossDef = resolveBossDefinition(player, requestedCardId);
        if (bossDef == null)
        {
            sendSystemMessage(player, "The hall challenge codex is unavailable. Try again shortly.", "");
            return SCRIPT_CONTINUE;
        }
        String bossCardId = bossDef.getString("cardId");
        if (!hasMustafarHandoff(player, bossCardId))
        {
            sendSystemMessage(player, "Complete the prior enclave story step before invoking this Mustafar hall challenge.", "");
            return SCRIPT_CONTINUE;
        }
        obj_id activeBoss = getObjIdObjVar(player, VAR_ACTIVE_BOSS);
        if (isIdValid(activeBoss) && exists(activeBoss))
        {
            sendSystemMessage(player, "A hall boss is already active. Finish that duel first.", "");
            return SCRIPT_CONTINUE;
        }
        if (isIdValid(activeBoss) && !exists(activeBoss))
        {
            removeObjVar(player, VAR_ACTIVE_BOSS);
        }
        location here = getLocation(player);
        float launchDistance = here != null ? getDistance(here, enclave_trials.MUSTAFAR_HALL_CENTER) : 99999.0f;
        if (here == null || here.area == null || !"mustafar".equals(here.area) || launchDistance > enclave_trials.MUSTAFAR_HALL_ALLOWED_RADIUS)
        {
            LOG("enclave", "Mustafar hall launch denied for " + player + ": distance=" + launchDistance + ", center=" + enclave_trials.MUSTAFAR_HALL_CENTER);
            sendSystemMessage(player, "Travel to the Mustafar abandoned ruins hall waypoint before invoking this challenge.", "");
            return SCRIPT_CONTINUE;
        }

        int playerLevel = getLevel(player);
        int bossLevel = playerLevel + 2;
        if (bossLevel < 48)
        {
            bossLevel = 48;
        }
        if (bossLevel > 90)
        {
            bossLevel = 90;
        }
        location spawnLoc = new location(here.x + 9.0f, here.y, here.z + 3.0f, here.area, here.cell);
        obj_id boss = create.createCreature(bossDef.getString("template"), spawnLoc, bossLevel, true, false);
        if (!isIdValid(boss))
        {
            sendSystemMessage(player, "The Mustafar hall remains silent. The challenge did not answer your call.", "");
            return SCRIPT_CONTINUE;
        }
        setObjVar(boss, "enclave.mustafar.owner", player);
        setObjVar(boss, "enclave.mustafar.cardId", bossCardId);
        setObjVar(boss, "enclave.mustafar.bossId", bossDef.getString("bossId"));
        setObjVar(boss, "enclave.mustafar.mechanicsProfile", bossDef.getString("mechanicsProfile"));
        setObjVar(boss, "enclave.mustafar.cadence.saber", bossDef.getFloat("saberCadenceMod"));
        setObjVar(boss, "enclave.mustafar.cadence.force", bossDef.getFloat("forceCadenceMod"));
        setObjVar(boss, "enclave.mustafar.cadence.lava", bossDef.getFloat("lavaCadenceMod"));
        setObjVar(boss, "enclave.mustafar.rewardMult", bossDef.getFloat("rewardMultiplier"));
        setObjVar(boss, "enclave.mustafar.influenceMult", bossDef.getFloat("influenceMultiplier"));
        setObjVar(boss, "enclave.mustafar.mutators", bossDef.getString("mutators"));
        attachScript(boss, "systems.jedi.enclave_trial_mustafar_hall_boss");

        setObjVar(player, VAR_ACTIVE_BOSS, boss);
        setObjVar(player, VAR_ACTIVE_CARD, bossCardId);
        setObjVar(player, VAR_ENCLAVE_PHASE, "MustafarHall");
        setObjVar(player, VAR_STORY_HANDOFF, bossCardId);
        sendSystemMessage(player, "The " + bossDef.getString("displayName") + " emerges in the ruins hall. Defeat this foe to advance the Mustafar story chain.", "");
        startCombat(boss, player);
        return SCRIPT_CONTINUE;
    }

    private boolean hasMustafarHandoff(obj_id player, String cardId) throws InterruptedException
    {
        int ruinsCompletions = getIntObjVar(player, "enclave.history." + TRIAL_RUINS);
        String handoff = hasObjVar(player, VAR_STORY_HANDOFF) ? getStringObjVar(player, VAR_STORY_HANDOFF) : "";
        if (cardId != null && cardId.equals(handoff))
        {
            return true;
        }
        if (TRIAL_MUSTAFAR_HALL.equals(cardId))
        {
            return ruinsCompletions > 0 || TRIAL_MUSTAFAR_HALL.equals(handoff);
        }
        String prerequisite = getPreviousCard(cardId);
        if (prerequisite == null || prerequisite.length() <= 0)
        {
            return ruinsCompletions > 0;
        }
        return getIntObjVar(player, "enclave.history." + prerequisite) > 0 || cardId.equals(handoff);
    }

    private String getPreviousCard(String cardId)
    {
        if ("trial_mustafar_hall_warden".equals(cardId))
        {
            return TRIAL_MUSTAFAR_HALL;
        }
        if ("trial_mustafar_hall_master_ascendant".equals(cardId))
        {
            return "trial_mustafar_hall_warden";
        }
        return "";
    }

    private dictionary resolveBossDefinition(obj_id player, String requestedCardId) throws InterruptedException
    {
        int row = dataTableSearchColumnForString(requestedCardId, "cardId", BOSS_TABLE);
        if (row >= 0)
        {
            return buildDefinition(row);
        }
        if (!TRIAL_MUSTAFAR_HALL.equals(requestedCardId))
        {
            return null;
        }
        int rowCount = dataTableGetNumRows(BOSS_TABLE);
        if (rowCount <= 0)
        {
            row = dataTableSearchColumnForString(DEFAULT_BOSS_ID, 0, BOSS_TABLE);
            return row >= 0 ? buildDefinition(row) : null;
        }
        int[] rotationRows = new int[rowCount];
        int[] rotationIndices = new int[rowCount];
        int rotationCount = 0;
        for (int i = 0; i < rowCount; ++i)
        {
            int rotationIndex = dataTableGetInt(BOSS_TABLE, i, "rotationIndex");
            if (rotationIndex >= 0)
            {
                rotationRows[rotationCount++] = i;
                rotationIndices[rotationCount - 1] = rotationIndex;
            }
        }
        if (rotationCount <= 0)
        {
            row = dataTableSearchColumnForString(DEFAULT_BOSS_ID, 0, BOSS_TABLE);
            return row >= 0 ? buildDefinition(row) : null;
        }
        for (int i = 1; i < rotationCount; ++i)
        {
            int keyRow = rotationRows[i];
            int keyIndex = rotationIndices[i];
            int j = i - 1;
            while (j >= 0 && (rotationIndices[j] > keyIndex || (rotationIndices[j] == keyIndex && rotationRows[j] > keyRow)))
            {
                rotationRows[j + 1] = rotationRows[j];
                rotationIndices[j + 1] = rotationIndices[j];
                --j;
            }
            rotationRows[j + 1] = keyRow;
            rotationIndices[j + 1] = keyIndex;
        }
        int history = getIntObjVar(player, "enclave.history." + TRIAL_MUSTAFAR_HALL);
        int selected = history % rotationCount;
        return buildDefinition(rotationRows[selected]);
    }

    private dictionary buildDefinition(int row)
    {
        dictionary def = new dictionary();
        String bossId = dataTableGetString(BOSS_TABLE, row, "bossId");
        String cardId = dataTableGetString(BOSS_TABLE, row, "cardId");
        String displayName = dataTableGetString(BOSS_TABLE, row, "displayName");
        String template = dataTableGetString(BOSS_TABLE, row, "template");
        String mechanicsProfile = dataTableGetString(BOSS_TABLE, row, "mechanicsProfile");
        float saberCadenceMod = dataTableGetFloat(BOSS_TABLE, row, "saberCadenceMod");
        float forceCadenceMod = dataTableGetFloat(BOSS_TABLE, row, "forceCadenceMod");
        float lavaCadenceMod = dataTableGetFloat(BOSS_TABLE, row, "lavaCadenceMod");
        float rewardMultiplier = dataTableGetFloat(BOSS_TABLE, row, "rewardMultiplier");
        float influenceMultiplier = dataTableGetFloat(BOSS_TABLE, row, "influenceMultiplier");
        String mutators = dataTableGetString(BOSS_TABLE, row, "mutators");
        if (template == null || template.length() <= 0)
        {
            template = "dark_jedi_master";
        }
        if (displayName == null || displayName.length() <= 0)
        {
            displayName = "Dark Jedi Master";
        }
        if (cardId == null || cardId.length() <= 0)
        {
            cardId = TRIAL_MUSTAFAR_HALL;
        }
        if (mechanicsProfile == null || mechanicsProfile.length() <= 0)
        {
            mechanicsProfile = "baseline";
        }
        def.put("bossId", bossId);
        def.put("cardId", cardId);
        def.put("displayName", displayName);
        def.put("template", template);
        def.put("mechanicsProfile", mechanicsProfile);
        def.put("saberCadenceMod", saberCadenceMod);
        def.put("forceCadenceMod", forceCadenceMod);
        def.put("lavaCadenceMod", lavaCadenceMod);
        def.put("rewardMultiplier", rewardMultiplier);
        def.put("influenceMultiplier", influenceMultiplier);
        def.put("mutators", mutators);
        return def;
    }
}
