package script.systems.spawning;

import script.*;
import script.library.*;

import java.util.Objects;
import java.util.Vector;

public class spawn_base extends script.base_script
{
    public spawn_base()
    {
    }
    public static final int SPAWN_HEARBEAT_SPAWN_EVENT = 5;
    public static final int SPAWN_PLAYER_DELAY_MIN = 30;
    public static final int SPAWN_PLAYER_DELAY_MAX = 60;
    public static final int SPAWN_DISTANCE_MIN = 12;
    public static final int SPAWN_DISTANCE_MAX = 32;
    public static final String SPAWN_LOCATION_REQUEST_PREFIX = "spawnLocation";
    public static final String SPAWN_INFO_SCRIPT_VAR_PREFIX = "spawning.dctSpawnInfo.";
    public static final int MUSTAFAR_HUMANOID_ENCOUNTER_CHANCE = 45;
    public static final int SPAWN_CHECK_DISTANCE = 64;
    public static final int SPAWN_CHECK_LIMIT = 15;
    public static final int SPAWN_TEMPLATE_CHECK_DISTANCE = 128;
    public static final int SPAWN_CHECK_TEMPLATE_LIMIT = 14;
    public static final int SPAWN_THEATER_CHECK_DISTANCE = 200;
    public static final int SPAWN_CHECK_THEATER_LIMIT = 1;
    public static final int EXTERIOR_SPAWN_CHANCE = 50;
    public static final int EXTERIOR_MAX_NPC = 10;
    public static final int INTERIOR_MAX_NPC = 10;
    public static final int EXTERIOR_MIN_NPC = 4;
    public static final int INTERIOR_MIN_NPC = 4;
    public static final int PLAYER_TO_NPC_RATIO = 1;
    public static final boolean boolFastSpawnEnabled = false;
    public static final int MAXIMUM_SPAWNING_RUN_TIME_RULES = 200;
    public static final float CREATURES_TO_PLAYERS_RATIO = 20;
    public static final String[] INVALID_SPAWNING_AREAS =
    {
        "tutorial"
    };
    public static final int SPAWN_RECENT_HISTORY_LIMIT = 5;
    public static final int SPAWN_DEFAULT_DIFFICULTY_VARIANCE = 5;
    public static final int SPAWN_MIN_DIFFICULTY_VARIANCE = 2;
    public static final int SPAWN_MAX_DIFFICULTY_DELTA = 45;
    public static final int SPAWN_MAX_MISMATCH_PENALTY_STEPS = 3;
    public static final int SPAWN_MATCH_BONUS_PERCENT = 20;
    public static final int SPAWN_MISMATCH_PENALTY_PERCENT = 15;
    public static final int SPAWN_RECENT_TEMPLATE_WEIGHT_PERCENT = 35;
    public static final int SPAWN_CHALLENGE_BONUS_PERCENT = 10;
    public static final int SPAWN_CHALLENGE_OVERFLOW_LIMIT = 10;
    public int[] getValidSpawn(dictionary dctPlayerStats) throws InterruptedException
    {
        if (dctPlayerStats == null)
        {
            return null;
        }
        boolean boolTheatersAllowed = dctPlayerStats.getBoolean("boolTheatersAllowed");
        String strRegionName = dctPlayerStats.getString("strRegionName");
        String strPlanet = dctPlayerStats.getString("strPlanet");
        region rgnSpawnRegion = getRegion(strPlanet, strRegionName);
        obj_id objPlayer = dctPlayerStats.getObjId("objPlayer");
        if (rgnSpawnRegion == null)
        {
            return null;
        }
        String strRegion = regions.translateGeoToString(rgnSpawnRegion.getGeographicalType());
        if (strRegion == null || strRegion.equals(""))
        {
            return null;
        }
        String strFileName = getSpawnListFileName(strPlanet, strRegionName, strRegion, rgnSpawnRegion);
        if (strFileName == null)
        {
            return null;
        }
        String[] strTemplates = dataTableGetStringColumn(strFileName, "strTemplate");
        int[] intMinDifficulties = dataTableGetIntColumn(strFileName, "intMinDifficulty");
        int[] intMaxDifficulties = dataTableGetIntColumn(strFileName, "intMaxDifficulty");
        int[] intGcwFactions = null;
        int[] intGcwThresholds = null;
        int[] intGcwScoreTypes = null;
        String[] strGcwSpecificRegions = null;
        if (dataTableHasColumn(strFileName, "intGCWFaction"))
        {
            intGcwFactions = dataTableGetIntColumn(strFileName, "intGCWFaction");
            intGcwThresholds = dataTableGetIntColumn(strFileName, "intGCWThreshold");
            intGcwScoreTypes = dataTableGetIntColumn(strFileName, "intGCWScoreType");
            strGcwSpecificRegions = dataTableGetStringColumn(strFileName, "strGCWSpecificRegion");
        }
        boolean checkGCWStats = true;
        if (strTemplates == null || strTemplates.length == 0 || intMinDifficulties == null || intMaxDifficulties == null)
        {
            LOG("spawning", "Invalid spawn template, or min/max difficulty values are invalid. Filename = " + strFileName);
            return null;
        }
        location currentLoc = getLocation(objPlayer);
        if (intGcwFactions == null || intGcwThresholds == null || intGcwScoreTypes == null)
        {
            checkGCWStats = false;
        }
        Vector validTemplateIndices = new Vector();
        validTemplateIndices.setSize(0);
        String strSpecificRegion;
        for (int i = 0; i < strTemplates.length; ++i)
        {
            int intMinDifficulty = intMinDifficulties[i];
            int intMaxDifficulty = intMaxDifficulties[i];
            if (checkGCWStats)
            {
                int intGcwFaction = intGcwFactions[i];
                int intGcwThreshold = intGcwThresholds[i];
                int intGcwScoreType = intGcwScoreTypes[i];
                strSpecificRegion = strGcwSpecificRegions == null || strGcwSpecificRegions.length == 0 ? "" : strGcwSpecificRegions[i];
                if (!checkGalacticCivilWarStandings(intGcwFaction, intGcwThreshold, intGcwScoreType, strSpecificRegion, currentLoc))
                {
                    continue;
                }
            }
            if (checkDifficulty(intMinDifficulty, intMaxDifficulty, dctPlayerStats))
            {
                if (!boolTheatersAllowed && strTemplates[i].indexOf("theater") > 0)
                {
                    continue;
                }
                validTemplateIndices = utils.addElement(validTemplateIndices, i);
            }
        }
        int[] _validTemplateIndices = new int[0];
        if (validTemplateIndices != null)
        {
            _validTemplateIndices = new int[validTemplateIndices.size()];
            for (int _i = 0; _i < validTemplateIndices.size(); ++_i)
            {
                _validTemplateIndices[_i] = (Integer) validTemplateIndices.get(_i);
            }
        }
        return _validTemplateIndices;
    }
    public boolean checkDifficulty(int intMinTemplateDifficulty, int intMaxTemplateDifficulty, dictionary dctPlayerStats) throws InterruptedException
    {
        int playerDifficulty = getEffectivePlayerDifficulty(dctPlayerStats);
        if (playerDifficulty <= 0)
        {
            return true;
        }
        int difficultyVariance = SPAWN_DEFAULT_DIFFICULTY_VARIANCE;
        if (dctPlayerStats != null)
        {
            int scriptVariance = dctPlayerStats.getInt("intDifficultyVariance");
            if (scriptVariance > 0)
            {
                difficultyVariance = scriptVariance;
            }
        }
        if (difficultyVariance < SPAWN_MIN_DIFFICULTY_VARIANCE)
        {
            difficultyVariance = SPAWN_MIN_DIFFICULTY_VARIANCE;
        }
        obj_id objPlayer = null;
        if (dctPlayerStats != null)
        {
            objPlayer = dctPlayerStats.getObjId("objPlayer");
        }
        int groupBonus = getGroupChallengeBonus(objPlayer);
        int effectiveMin = intMinTemplateDifficulty - difficultyVariance;
        if (effectiveMin < 0)
        {
            effectiveMin = 0;
        }
        int effectiveMax = intMaxTemplateDifficulty + difficultyVariance + groupBonus;
        boolean allowChallengingSpawns = false;
        if (dctPlayerStats != null)
        {
            allowChallengingSpawns = dctPlayerStats.getBoolean("boolAllowChallengingSpawns");
        }
        if (allowChallengingSpawns)
        {
            effectiveMax += SPAWN_CHALLENGE_OVERFLOW_LIMIT;
        }
        if (playerDifficulty < effectiveMin)
        {
            int difference = effectiveMin - playerDifficulty;
            if (difference > SPAWN_MAX_DIFFICULTY_DELTA)
            {
                return false;
            }
            if (difference > 0)
            {
                int chance = rand(0, difference);
                return chance <= difficultyVariance;
            }
        }
        else if (playerDifficulty > effectiveMax)
        {
            int difference = playerDifficulty - effectiveMax;
            if (difference > SPAWN_MAX_DIFFICULTY_DELTA)
            {
                return false;
            }
            if (!allowChallengingSpawns)
            {
                if (difference > 0)
                {
                    int chance = rand(0, difference);
                    return chance <= difficultyVariance;
                }
            }
        }
        return true;
    }
    public int getPlayerSpawnDiffibculty(obj_id objPlayer) throws InterruptedException
    {
        if (!isIdValid(getGroupObject(objPlayer)))
        {
            return getLevel(objPlayer);
        }
        else 
        {
            return skill.getGroupLevel(objPlayer);
        }
    }
    public obj_id createTemplate(location locSpawnLocation, dictionary params, obj_id objPlayer) throws InterruptedException
    {
        String strRegionName = params.getString("strRegionName");
        String strPlanet = params.getString("strPlanet");
        region rgnSpawnRegion = getRegion(strPlanet, strRegionName);
        if (rgnSpawnRegion == null)
        {
            return null;
        }
        int intPlayerDifficulty = params.getInt("intPlayerDifficulty");
        int intNumToSpawn = params.getInt("intNumToSpawn");
        String strTemplateToSpawn = params.getString("strTemplateToSpawn");
        String strTemplate = params.getString("strTemplate");
        int intMinDifficulty = params.getInt("intMinDifficulty");
        int intMaxDifficulty = params.getInt("intMaxDifficulty");
        String strLairType = params.getString("strLairType");
        String strBuildingType = params.getString("strBuildingType");
        obj_id objCreatedTemplate = createObject(strTemplate, locSpawnLocation);
        if (isIdValid(objCreatedTemplate))
        {
            if (group.isGrouped(objPlayer))
            {
                obj_id objGroup = getGroupObject(objPlayer);
                if (isIdValid(objGroup))
                {
                    int intGroupSize = getGroupSize(objGroup);
                    setObjVar(objCreatedTemplate, "spawning.intGroupSize", intGroupSize);
                }
            }
            attachScript(objCreatedTemplate, "systems.spawning.spawn_template");
            if (!strLairType.equals(""))
            {
                setObjVar(objCreatedTemplate, "spawning.intDifficultyLevel", intPlayerDifficulty);
                String strDifficulty = create.getLairDifficulty(intMinDifficulty, intMaxDifficulty, intPlayerDifficulty);
                setObjVar(objCreatedTemplate, "spawning.lairDifficulty", strDifficulty);
                setObjVar(objCreatedTemplate, "spawning.lairType", strLairType);
                setObjVar(objCreatedTemplate, "spawning.buildingTrackingType", strBuildingType);
                if (!strBuildingType.equals(""))
                {
                    setObjVar(objCreatedTemplate, "spawning.buildingType", strBuildingType);
                }
                obj_id groupObject = getGroupObject(objPlayer);
                if (isIdValid(groupObject))
                {
                    setObjVar(objCreatedTemplate, "spawning.groupSize", getPCGroupSize(groupObject));
                }
                else 
                {
                    setObjVar(objCreatedTemplate, "spawning.groupSize", 1);
                }
            }
            else 
            {
                if (strTemplate.indexOf("lair") > 0)
                {
                    setObjVar(objCreatedTemplate, "numToSpawn", intNumToSpawn);
                    if (!strTemplateToSpawn.equals(""))
                    {
                        setObjVar(objCreatedTemplate, "creatureTemplate", strTemplateToSpawn);
                    }
                }
                if (strTemplate.indexOf("herd") > 0)
                {
                    setObjVar(objCreatedTemplate, "numToSpawn", intNumToSpawn);
                    if ((!strTemplateToSpawn.equals("")))
                    {
                        setObjVar(objCreatedTemplate, "creatureTemplate", strTemplateToSpawn);
                    }
                }
            }
            dictionary dctParams = new dictionary();
            messageTo(objCreatedTemplate, "handle_Spawn_Setup_Complete", dctParams, 0, false);
            dctParams.put("strTemplate", strTemplate);
            dctParams.put("strRegionName", strRegionName);
            dctParams.put("strPlanet", strPlanet);
            dctParams.put("strLairType", strLairType);
            dctParams.put("strBuildingType", strBuildingType);
        }
        else 
        {
            return null;
        }
        return objCreatedTemplate;
    }
    public dictionary chooseWeightedSpawnTemplate(int[] intDataIndex, dictionary dctPlayerStats, String strRegionName, String strPlanetName, region rgnRegion) throws InterruptedException
    {
        String strFileName = getSpawnListFileName(strPlanetName, rgnRegion.getName(), strRegionName, rgnRegion);
        if(strFileName == null){
            return null;
        }
        String[] strTemplates = dataTableGetStringColumn(strFileName, "strTemplate");
        int[] intMinDifficulties = dataTableGetIntColumn(strFileName, "intMinDifficulty");
        int[] intMaxDifficulties = dataTableGetIntColumn(strFileName, "intMaxDifficulty");
        int[] intWeightings = dataTableGetIntColumn(strFileName, "intWeighting");
        int[] intSizes = dataTableGetIntColumn(strFileName, "intSize");
        int[] intNumToSpawns = dataTableGetIntColumn(strFileName, "intNumToSpawn");
        String[] strTemplatesToSpawn = dataTableGetStringColumn(strFileName, "strTemplateToSpawn");
        String[] strLairTypes = dataTableGetStringColumn(strFileName, "strLairType");
        String[] strBuildingTypes = dataTableGetStringColumn(strFileName, "strBuildingType");
        obj_id objPlayer = null;
        if (dctPlayerStats != null)
        {
            objPlayer = dctPlayerStats.getObjId("objPlayer");
        }
        Vector recentTemplates = getRecentSpawnHistory(objPlayer);
        int playerDifficulty = getEffectivePlayerDifficulty(dctPlayerStats);
        int difficultyVariance = SPAWN_DEFAULT_DIFFICULTY_VARIANCE;
        String strPreferredTag = null;
        boolean allowChallengingSpawns = false;
        if (dctPlayerStats != null)
        {
            int scriptVariance = dctPlayerStats.getInt("intDifficultyVariance");
            if (scriptVariance > 0)
            {
                difficultyVariance = scriptVariance;
            }
            strPreferredTag = dctPlayerStats.getString("strPreferredSpawnTag");
            allowChallengingSpawns = dctPlayerStats.getBoolean("boolAllowChallengingSpawns");
        }
        if (difficultyVariance < SPAWN_MIN_DIFFICULTY_VARIANCE)
        {
            difficultyVariance = SPAWN_MIN_DIFFICULTY_VARIANCE;
        }
        int groupBonus = getGroupChallengeBonus(objPlayer);
        int weightingVariance = difficultyVariance + (groupBonus / 2);
        int[] dynamicWeights = new int[intDataIndex.length];
        int selectedIndex = 0;
        int weightingGrandTotal = 0;
        if (intDataIndex.length > 0)
        {
            for (int i = 0; i < intDataIndex.length; ++i)
            {
                int templateIndex = intDataIndex[i];
                int baseWeight = intWeightings[templateIndex];
                int adjustedWeight = adjustTemplateWeight(baseWeight, intMinDifficulties[templateIndex], intMaxDifficulties[templateIndex], playerDifficulty, weightingVariance, recentTemplates, strTemplates[templateIndex], allowChallengingSpawns, strPreferredTag);
                dynamicWeights[i] = adjustedWeight;
                weightingGrandTotal += adjustedWeight;
            }
        }
        if (weightingGrandTotal <= 0)
        {
            weightingGrandTotal = 0;
            for (int i = 0; i < intDataIndex.length; ++i)
            {
                int templateIndex = intDataIndex[i];
                int baseWeight = intWeightings[templateIndex];
                if (baseWeight < 1)
                {
                    baseWeight = 1;
                }
                dynamicWeights[i] = baseWeight;
                weightingGrandTotal += baseWeight;
            }
        }
        if (weightingGrandTotal <= 0)
        {
            return null;
        }
        if (intDataIndex.length != 1)
        {
            int randomValue = rand(0, weightingGrandTotal - 1);
            int previousChance = 0;
            for (int i = 0; i < intDataIndex.length; ++i)
            {
                int currentChance = previousChance + dynamicWeights[i];
                if (randomValue >= previousChance && randomValue < currentChance)
                {
                    selectedIndex = i;
                    break;
                }
                previousChance = currentChance;
            }
        }
        int intIndex = intDataIndex[selectedIndex];
        dictionary dctSpawnInformation = new dictionary();
        dctSpawnInformation.put("intIndex", intIndex);
        dctSpawnInformation.put("strTemplate", strTemplates[intIndex]);
        dctSpawnInformation.put("intMinDifficulty", intMinDifficulties[intIndex]);
        dctSpawnInformation.put("intMaxDifficulty", intMaxDifficulties[intIndex]);
        dctSpawnInformation.put("intSize", intSizes[intIndex]);
        dctSpawnInformation.put("intNumToSpawn", intNumToSpawns[intIndex]);
        dctSpawnInformation.put("strLairType", strLairTypes[intIndex]);
        dctSpawnInformation.put("strBuildingType", strBuildingTypes[intIndex]);
        dctSpawnInformation.put("strTemplateToSpawn", strTemplatesToSpawn[intIndex]);
        String strMustafarEncounterLair = getMustafarHumanoidEncounterLair(strPlanetName, rgnRegion);
        if (strMustafarEncounterLair != null && !strMustafarEncounterLair.equals(""))
        {
            dctSpawnInformation.put("strLairType", strMustafarEncounterLair);
            dctSpawnInformation.put("strBuildingType", "");
            dctSpawnInformation.put("intSize", 25);
        }
        if (isIdValid(objPlayer))
        {
            registerRecentSpawnTemplate(objPlayer, strTemplates[intIndex]);
        }
        return dctSpawnInformation;
    }
    public void sendSpawnSpam(obj_id objPlayer, boolean boolLogFailures, boolean boolVerboseMode, String strSpam) throws InterruptedException
    {
        if (boolVerboseMode && isIdValid(objPlayer))
        {
            deltadictionary dctScriptVars = objPlayer.getScriptVars();
            int intLastSpamTime = dctScriptVars.getInt("intLastSpamTime");
            int intCurrentTime = getGameTime();
            int intDifference = intCurrentTime - intLastSpamTime;
            if (intDifference > 5)
            {
                dctScriptVars.put("intLastSpamTime", intCurrentTime);
                sendSystemMessageTestingOnly(objPlayer, strSpam);
            }
        }
    }
    public boolean getVerboseMode(obj_id objPlayer) throws InterruptedException
    {
        return hasObjVar(objPlayer, "spawning.verboseMode");
    }
    public boolean checkTemplatesInRange(obj_id objPlayer, location locHome, boolean boolCheckForTheaters, String strObjectType) throws InterruptedException
    {
        return true;
    }
    public boolean canSpawn(obj_id objPlayer, location locSpawnLocation, boolean boolCheckForTheaters, String strObjectType) throws InterruptedException
    {
        if (!isSpawningAllowed(locSpawnLocation))
        {
            return false;
        }
        final int intServerSpawnLimit = getServerSpawnLimit();
        final int intNumCreatures = utils.getNumCreaturesForSpawnLimit();
        final int intNumPlayers = getNumPlayers();
        if (intNumPlayers > 0)
        {
            if (intServerSpawnLimit > 0)
            {
                if (intNumCreatures > intServerSpawnLimit)
                {
                    return false;
                }
            }
            else 
            {
                if (intNumCreatures > 5000)
                {
                    return false;
                }
            }
        }
        location locCurrentLocation = getLocation(objPlayer);
        if (Objects.equals(locCurrentLocation.area, "tutorial"))
        {
            return false;
        }
        if (city.isInCity(locCurrentLocation))
        {
            return false;
        }
        region[] rgnCities = getRegionsWithMunicipalAtPoint(locCurrentLocation, regions.MUNI_TRUE);
        if (rgnCities != null)
        {
            return false;
        }
        region rgnBattlefield = battlefield.getBattlefield(locCurrentLocation);
        if (rgnBattlefield != null)
        {
            return false;
        }
        rgnCities = getRegionsWithGeographicalAtPoint(locCurrentLocation, regions.GEO_CITY);
        if (rgnCities != null)
        {
            return false;
        }
        obj_id objMasterSpawner = getPlanetByName(locCurrentLocation.area);
        if (hasObjVar(objMasterSpawner, "boolSpawnerIsOn"))
        {
            boolean boolSpawnerIsOn;
            boolSpawnerIsOn = getBooleanObjVar(objMasterSpawner, "boolSpawnerIsOn");
            if (!boolSpawnerIsOn)
            {
                return false;
            }
        }
        if (locCurrentLocation.cell != obj_id.NULL_ID)
        {
            return false;
        }
        if (hasObjVar(objPlayer, "spawning.locSpawnLocation"))
        {
            int intSpawnedTemplates = 0;
            if (hasObjVar(objPlayer, "spawning.intSpawnedTemplates"))
            {
                intSpawnedTemplates = getIntObjVar(objPlayer, "spawning.intSpawnedTemplates");
            }
            if (intSpawnedTemplates >= SPAWN_CHECK_LIMIT)
            {
                location locLastSpawnLocation = getLocationObjVar(objPlayer, "spawning.locSpawnLocation");
                float fltDistance = utils.getDistance(locLastSpawnLocation, locCurrentLocation);
                if (fltDistance < SPAWN_CHECK_DISTANCE)
                {
                    return false;
                }
            }
        }
        if (locSpawnLocation != null)
        {
            if (city.isInCity(locSpawnLocation))
            {
                return false;
            }
            rgnCities = getRegionsWithMunicipalAtPoint(locSpawnLocation, regions.MUNI_TRUE);
            if (rgnCities != null)
            {
                return false;
            }
            rgnCities = getRegionsWithGeographicalAtPoint(locSpawnLocation, regions.GEO_CITY);
            if (rgnCities != null)
            {
                return false;
            }
            rgnBattlefield = battlefield.getBattlefield(locSpawnLocation);
            if (rgnBattlefield != null)
            {
                return false;
            }
        }
        return true;
    }
    public region getSpawnRegion(obj_id objPlayer) throws InterruptedException
    {
        location locTest = getLocation(objPlayer);
        region[] rgnRegionList = getRegionsWithSpawnableAtPoint(locTest, regions.SPAWN_TRUE);
        if (rgnRegionList == null)
        {
            rgnRegionList = getRegionsWithSpawnableAtPoint(locTest, regions.SPAWN_DEFAULT);
        }
        if (rgnRegionList == null)
        {
            return null;
        }
        region rgnSpawnRegion = locations.getSmallestRegion(rgnRegionList);
        if (rgnSpawnRegion == null)
        {
            return null;
        }
        if (rand(1, 100) > 50)
        {
            region rgnOverloadRegion = null;
            region[] rgnOverloadRegions = getRegionsWithGeographicalAtPoint(locTest, regions.GEO_OVERLOAD);
            if ((rgnOverloadRegions != null) && (rgnOverloadRegions.length > 0))
            {
                rgnOverloadRegion = rgnOverloadRegions[rand(0, rgnOverloadRegions.length - 1)];
            }
            if (rgnOverloadRegion != null)
            {
                return rgnOverloadRegion;
            }
        }
        return rgnSpawnRegion;
    }
    public void preLoadSpawnDataTables(obj_id objMasterSpawner) throws InterruptedException
    {
    }
    public boolean checkSpawnLogFailures() throws InterruptedException {
        String strConfigSetting = getConfigSetting("GameServer", "fastSpawn");
        return strConfigSetting != null && strConfigSetting.equals("true");
    }
    public boolean isSpawningAllowed(location locTest) throws InterruptedException
    {
        if (locTest == null || locTest.area == null)
        {
            return false;
        }
        for (String INVALID_SPAWNING_AREA : INVALID_SPAWNING_AREAS) {
            if (locTest.area.equals(INVALID_SPAWNING_AREA)) {
                return false;
            }
        }
        return true;
    }
    public String getFictionalRegionFileName(String strPlanet, String strFullName) throws InterruptedException
    {
        try {
            String strRegionName = utils.unpackString(strFullName).getAsciiId();
            return "datatables/spawning/spawn_lists/" + strPlanet + "/" + strRegionName + ".iff";
        }
        catch(Exception e) {
            LOG("spawning", "Unable to get spawns for planet (" + strPlanet + ") in region with name (" + strFullName + ").");
            Thread.dumpStack();
            return null;
        }
    }
    public String getMustafarHumanoidEncounterLair(String strPlanet, region rgnRegion) throws InterruptedException
    {
        if (strPlanet == null || !strPlanet.equals("mustafar") || rgnRegion == null)
        {
            return "";
        }
        if (rand(1, 100) > MUSTAFAR_HUMANOID_ENCOUNTER_CHANCE)
        {
            return "";
        }
        String strRegionName = rgnRegion.getName();
        try
        {
            strRegionName = utils.unpackString(strRegionName).getAsciiId();
        }
        catch(Exception e)
        {
        }
        if (strRegionName == null)
        {
            strRegionName = "";
        }
        String[] strEncounterLairs;
        if (strRegionName.indexOf("mining_area") > -1)
        {
            strEncounterLairs = new String[]
            {
                "mustafar_encounter_striking_miners_none_med",
                "mustafar_encounter_salvage_bandits_none_med",
                "mustafar_encounter_phantom_bandits_none_med"
            };
        }
        else if (strRegionName.indexOf("crystal_flats") > -1)
        {
            strEncounterLairs = new String[]
            {
                "mustafar_encounter_salvage_bandits_none_med",
                "mustafar_encounter_treasure_hunters_none_med",
                "mustafar_encounter_phantom_bandits_none_med"
            };
        }
        else if (strRegionName.indexOf("berkens_flow") > -1)
        {
            strEncounterLairs = new String[]
            {
                "mustafar_encounter_blackguard_none_med",
                "mustafar_encounter_coyn_patrol_none_med",
                "mustafar_encounter_storm_lord_followers_none_med",
                "mustafar_encounter_salvage_bandits_none_med"
            };
        }
        else if (strRegionName.indexOf("burning_plains") > -1)
        {
            strEncounterLairs = new String[]
            {
                "mustafar_encounter_salvage_bandits_none_med",
                "mustafar_encounter_blackguard_none_med",
                "mustafar_encounter_storm_lord_followers_none_med"
            };
        }
        else if (strRegionName.indexOf("nesting_grounds") > -1)
        {
            strEncounterLairs = new String[]
            {
                "mustafar_encounter_treasure_hunters_none_med",
                "mustafar_encounter_coyn_patrol_none_med",
                "mustafar_encounter_blackguard_none_med"
            };
        }
        else if (strRegionName.indexOf("smoking_forest") > -1)
        {
            strEncounterLairs = new String[]
            {
                "mustafar_encounter_blackguard_none_med",
                "mustafar_encounter_storm_lord_followers_none_med",
                "mustafar_encounter_salvage_bandits_none_med"
            };
        }
        else
        {
            strEncounterLairs = new String[]
            {
                "mustafar_encounter_salvage_bandits_none_med",
                "mustafar_encounter_phantom_bandits_none_med",
                "mustafar_encounter_treasure_hunters_none_med"
            };
        }
        return strEncounterLairs[rand(0, strEncounterLairs.length - 1)];
    }
    public String getSpawnListFileName(String strPlanet, String strRegionName, String strRegionType, region rgnRegion) throws InterruptedException
    {
        if (strPlanet == null || strPlanet.equals(""))
        {
            return null;
        }
        if (strRegionType == null || strRegionType.equals(""))
        {
            return null;
        }
        if (strRegionName == null || strRegionName.equals(""))
        {
            return null;
        }
        if (strPlanet.equals("mustafar") && rgnRegion != null)
        {
            String strFullName = rgnRegion.getName();
            if (strFullName != null && strFullName.startsWith("@mustafar_region_names:"))
            {
                return getFictionalRegionFileName(strPlanet, strFullName);
            }
        }
        switch (strRegionType) {
            case "fictional":
                return getFictionalRegionFileName(strPlanet, strRegionName);
            case "overload":
                return getOverLoadRegionFileName(strRegionName);
            default:
                return "datatables/spawning/spawn_lists/" + strPlanet + "/" + strPlanet + "_" + strRegionType + ".iff";
        }
    }
    public String getOverLoadRegionFileName(String strRegionName) throws InterruptedException
    {
        int intIndex = strRegionName.indexOf("-");
        if (intIndex < 0)
        {
            return "";
        }
        String strFileName = strRegionName.substring(0, intIndex);
        return "datatables/spawning/spawn_lists/spawn_overloads/" + strFileName + ".iff";
    }
    public void doSpawnEvent(dictionary params) throws InterruptedException
    {
        if (params == null)
        {
            return;
        }
        obj_id objPlayer = params.getObjId("objPlayer");
        if (!isIdValid(objPlayer))
        {
            return;
        }
        String strRegionName = params.getString("strRegionName");
        if (strRegionName == null || strRegionName.length() < 1)
        {
            return;
        }
        String strPlanet = params.getString("strPlanet");
        if (strPlanet == null || strPlanet.length() < 1)
        {
            return;
        }
        region rgnSpawnRegion = getRegion(strPlanet, strRegionName);
        if (rgnSpawnRegion == null)
        {
            LOG("spawning", objPlayer + " GETREGION RETURNED NULL FOR " + strRegionName + " and planet " + strPlanet);
            return;
        }
        int[] validSpawnIndices = getValidSpawn(params);
        if (validSpawnIndices == null || validSpawnIndices.length == 0)
        {
            LOG("DESIGNER_FATAL", "for dictionary " + params.toString() + " objvarids is null");
            LOG("spawn", "for dictionary " + params.toString() + " objvarids is null");
            return;
        }
        String strRegionType = regions.translateGeoToString(rgnSpawnRegion.getGeographicalType());
        if (strRegionType == null || strRegionType.length() < 1)
        {
            return;
        }
        dictionary dctSpawnInformation = chooseWeightedSpawnTemplate(validSpawnIndices, params, strRegionType, strPlanet, rgnSpawnRegion);
        if (dctSpawnInformation == null)
        {
            return;
        }
        dctSpawnInformation.put("intPlayerDifficulty", params.getInt("intDifficulty"));
        dctSpawnInformation.put("strRegionName", strRegionName);
        dctSpawnInformation.put("strPlanet", strPlanet);
        doSpawnTemplate(objPlayer, dctSpawnInformation);
    }
    public void doSpawnTemplate(obj_id self, dictionary params) throws InterruptedException
    {
        String strLairType = params.getString("strLairType");
        dictionary dctParams = new dictionary();
        location locSpawnLocation = getLocation(self);
        if (canSpawn(self, locSpawnLocation, true, strLairType))
        {
            dctParams.put("intMinDifficulty", params.getInt("intMinDifficulty"));
            dctParams.put("intMaxDifficulty", params.getInt("intMaxDifficulty"));
            dctParams.put("strLairType", strLairType);
            dctParams.put("strBuildingType", params.getString("strBuildingType"));
            dctParams.put("strRegionName", params.getString("strRegionName"));
            dctParams.put("strPlanet", params.getString("strPlanet"));
            dctParams.put("intIndex", params.getInt("intIndex"));
            dctParams.put("intPlayerDifficulty", params.getInt("intPlayerDifficulty"));
            dctParams.put("intNumToSpawn", params.getInt("intNumToSpawn"));
            dctParams.put("strTemplateToSpawn", params.getString("strBuildingType"));
            dctParams.put("strTemplate", params.getString("strTemplate"));
            float fltSize = getLocationSize(params.getInt("intSize"));
            String strRequestId = SPAWN_LOCATION_REQUEST_PREFIX + "." + getGameTime() + "." + rand(0, 1000000);
            utils.setScriptVar(self, SPAWN_INFO_SCRIPT_VAR_PREFIX + strRequestId, dctParams);
            float fltRequestRadius = 100.0f;
            if (params.getString("strPlanet").equals("mustafar"))
            {
                fltRequestRadius = rand(120, 220);
            }
            requestLocation(self, strRequestId, locSpawnLocation, fltRequestRadius, fltSize, true, false);
        }
    }
    public int OnLocationReceived(obj_id self, String strId, obj_id objObject, location locSpawnLocation, float fltRadius) throws InterruptedException
    {
        if (strId.equals(SPAWN_LOCATION_REQUEST_PREFIX) || strId.startsWith(SPAWN_LOCATION_REQUEST_PREFIX + "."))
        {
            if (!isIdValid(objObject))
            {
                return SCRIPT_CONTINUE;
            }
            String strScriptVar = SPAWN_INFO_SCRIPT_VAR_PREFIX + strId;
            dictionary dctSpawnInfo = utils.getDictionaryScriptVar(self, strScriptVar);
            if (dctSpawnInfo == null && strId.equals(SPAWN_LOCATION_REQUEST_PREFIX))
            {
                dctSpawnInfo = utils.getDictionaryScriptVar(self, "dctSpawnInfo");
            }
            if (dctSpawnInfo == null)
            {
                return SCRIPT_CONTINUE;
            }
            utils.removeScriptVar(self, strScriptVar);
            obj_id objCreatedTemplate = createTemplate(locSpawnLocation, dctSpawnInfo, self);
            if (isIdValid(objCreatedTemplate))
            {
                setObjVar(objCreatedTemplate, "objLocationReservation", objObject);
                setObjVar(self, "spawning.locSpawnLocation", locSpawnLocation);
                int intSpawnedTemplates = 0;
                if (hasObjVar(self, "spawning.intSpawnedTemplates"))
                {
                    intSpawnedTemplates = getIntObjVar(self, "spawning.intSpawnedTemplates");
                }
                setObjVar(self, "spawning.intSpawnedTemplates", intSpawnedTemplates + 1);
            }
        }
        return SCRIPT_CONTINUE;
    }
    public float getLocationSize(float fltLocation) throws InterruptedException
    {
        if (fltLocation < 32.0f)
        {
            return 32.0f;
        }
        else if (fltLocation >= 32 && fltLocation <= 48)
        {
            return 48.0f;
        }
        else if (fltLocation >= 48 && fltLocation <= 64)
        {
            return 64.0f;
        }
        else if (fltLocation >= 64 && fltLocation <= 80)
        {
            return 80.0f;
        }
        else
        {
            return 96.0f;
        }
    }
    public int getEffectivePlayerDifficulty(dictionary dctPlayerStats) throws InterruptedException
    {
        if (dctPlayerStats == null)
        {
            return 0;
        }
        int playerDifficulty = dctPlayerStats.getInt("intDifficulty");
        if (playerDifficulty <= 0)
        {
            obj_id objPlayer = dctPlayerStats.getObjId("objPlayer");
            if (isIdValid(objPlayer))
            {
                playerDifficulty = getPlayerSpawnDiffibculty(objPlayer);
            }
        }
        if (playerDifficulty < 0)
        {
            playerDifficulty = 0;
        }
        return playerDifficulty;
    }
    public int getGroupChallengeBonus(obj_id objPlayer) throws InterruptedException
    {
        if (!isIdValid(objPlayer))
        {
            return 0;
        }
        obj_id objGroup = getGroupObject(objPlayer);
        if (!isIdValid(objGroup))
        {
            return 0;
        }
        int groupSize = getGroupSize(objGroup);
        if (groupSize <= 1)
        {
            return 0;
        }
        int bonus = (groupSize - 1) * 2;
        int groupLevel = skill.getGroupLevel(objPlayer);
        int soloLevel = getLevel(objPlayer);
        if (groupLevel > soloLevel)
        {
            bonus += (groupLevel - soloLevel) / 2;
        }
        return bonus;
    }
    public Vector getRecentSpawnHistory(obj_id objPlayer) throws InterruptedException
    {
        if (!isIdValid(objPlayer))
        {
            return null;
        }
        Vector recentTemplates = utils.getResizeableStringArrayScriptVar(objPlayer, "spawning.recentTemplates");
        if (recentTemplates == null || recentTemplates.isEmpty())
        {
            return null;
        }
        return recentTemplates;
    }
    public void registerRecentSpawnTemplate(obj_id objPlayer, String strTemplate) throws InterruptedException
    {
        if (!isIdValid(objPlayer) || strTemplate == null || strTemplate.equals(""))
        {
            return;
        }
        Vector recentTemplates = utils.getResizeableStringArrayScriptVar(objPlayer, "spawning.recentTemplates");
        Vector updatedHistory = new Vector();
        if (recentTemplates != null && !recentTemplates.isEmpty())
        {
            for (int i = 0; i < recentTemplates.size(); ++i)
            {
                Object entry = recentTemplates.get(i);
                if (entry instanceof String)
                {
                    String existingTemplate = (String) entry;
                    if (existingTemplate != null && !existingTemplate.equals(strTemplate))
                    {
                        updatedHistory.add(existingTemplate);
                    }
                }
            }
        }
        updatedHistory.add(strTemplate);
        while (updatedHistory.size() > SPAWN_RECENT_HISTORY_LIMIT)
        {
            updatedHistory.remove(0);
        }
        utils.setScriptVar(objPlayer, "spawning.recentTemplates", updatedHistory);
    }
    public int adjustTemplateWeight(int baseWeight, int intMinDifficulty, int intMaxDifficulty, int playerDifficulty, int difficultyVariance, Vector recentTemplates, String strTemplate, boolean allowChallengingSpawns, String strPreferredTag) throws InterruptedException
    {
        int adjustedWeight = baseWeight;
        if (adjustedWeight < 1)
        {
            adjustedWeight = 1;
        }
        if (playerDifficulty > 0)
        {
            int templateFocus = (intMinDifficulty + intMaxDifficulty) / 2;
            int diff = templateFocus - playerDifficulty;
            if (diff < 0)
            {
                diff = -diff;
            }
            int variance = difficultyVariance;
            if (variance < 1)
            {
                variance = 1;
            }
            if (diff <= variance)
            {
                adjustedWeight += Math.max(1, baseWeight * SPAWN_MATCH_BONUS_PERCENT / 100);
            }
            else
            {
                int penaltySteps = (diff - variance) / variance;
                if (penaltySteps < 0)
                {
                    penaltySteps = 0;
                }
                if (penaltySteps > SPAWN_MAX_MISMATCH_PENALTY_STEPS)
                {
                    penaltySteps = SPAWN_MAX_MISMATCH_PENALTY_STEPS;
                }
                int penaltyAmount = (baseWeight * SPAWN_MISMATCH_PENALTY_PERCENT / 100) * (penaltySteps + 1);
                if (penaltyAmount >= adjustedWeight)
                {
                    adjustedWeight = 0;
                }
                else
                {
                    adjustedWeight -= penaltyAmount;
                }
            }
            if (allowChallengingSpawns && playerDifficulty >= intMaxDifficulty)
            {
                adjustedWeight += Math.max(1, baseWeight * SPAWN_CHALLENGE_BONUS_PERCENT / 100);
            }
        }
        if (strPreferredTag != null && !strPreferredTag.equals("") && strTemplate != null && strTemplate.indexOf(strPreferredTag) > -1)
        {
            adjustedWeight += Math.max(1, baseWeight * SPAWN_MATCH_BONUS_PERCENT / 100);
        }
        if (recentTemplates != null && !recentTemplates.isEmpty())
        {
            for (int i = 0; i < recentTemplates.size(); ++i)
            {
                Object entry = recentTemplates.get(i);
                if (entry instanceof String)
                {
                    String recentTemplate = (String) entry;
                    if (recentTemplate != null && recentTemplate.equals(strTemplate))
                    {
                        adjustedWeight = adjustedWeight * SPAWN_RECENT_TEMPLATE_WEIGHT_PERCENT / 100;
                        break;
                    }
                }
            }
        }
        if (adjustedWeight < 0)
        {
            adjustedWeight = 0;
        }
        return adjustedWeight;
    }
    public boolean isGalaticCivilWarWinner(int faction, int threshold, int imperialScore) throws InterruptedException
    {
        if (faction == 1 && threshold < imperialScore)
        {
            return true;
        }
        else if (faction == 2 && threshold < (100 - imperialScore))
        {
            return true;
        }
        return false;
    }
    public boolean checkGalacticCivilWarStandings(int gcwFaction, int gcwThreshold, int gcwScoreType, String gcwSpecificRegion, location loc) throws InterruptedException
    {
        if (gcwFaction > 0 && gcwThreshold > 0)
        {
            if (gcwScoreType == 0)
            {
                int ImperialScore = getGcwGroupImperialScorePercentile(toLower(getCurrentSceneName()));
                return isGalaticCivilWarWinner(gcwFaction, gcwThreshold, ImperialScore);
            }
            else if (gcwScoreType == 1)
            {
                int ImperialScore = 0;
                region[] allRegions = getRegionsAtPoint(loc);
                if (allRegions != null && allRegions.length > 0)
                {
                    for (region gcwRegion : allRegions) {
                        if ((gcwRegion.getName()).startsWith("gcw")) {
                            ImperialScore = getGcwImperialScorePercentile(toLower(gcwRegion.getName()));
                            break;
                        }
                    }
                }
                else
                {
                    return false;
                }
                return isGalaticCivilWarWinner(gcwFaction, gcwThreshold, ImperialScore);
            }
            else if (gcwScoreType == 2)
            {
                if (gcwSpecificRegion == null || gcwSpecificRegion.equals(""))
                {
                    LOG("GCWSpawnError", "Invalid Specific region specified");
                    return false;
                }
                int ImperialScore = getGcwImperialScorePercentile(toLower(gcwSpecificRegion));
                return isGalaticCivilWarWinner(gcwFaction, gcwThreshold, ImperialScore);
            }
            else
            {
                return false;
            }
        }
        return true;
    }
}
