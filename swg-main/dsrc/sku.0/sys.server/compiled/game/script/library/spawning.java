package script.library;

import script.dictionary;
import script.location;
import script.obj_id;

import java.util.Vector;

public class spawning extends script.base_script
{
    public spawning()
    {
    }
    public static final String APPEARANCE_PROFILE_TABLE = "datatables/spawning/appearance/profiles.iff";
    public static final String APPEARANCE_ASSET_TABLE = "datatables/spawning/appearance/assets.iff";
    public static final String APPEARANCE_COLOR_TABLE = "datatables/spawning/appearance/color_schemes.iff";
    public static final String[] DEFAULT_DISABLED_SLOTS =
    {
        "hold_r",
        "hold_l"
    };

    public static void activateSpawnerHack(obj_id objPlayer) throws InterruptedException
    {
        obj_id[] objSpawners = getAllObjectsWithObjVar(getLocation(objPlayer), 24000, "intSpawnSystem");
        if (objSpawners.length == 0)
        {
            return;
        }
        messageTo(objSpawners[rand(0, objSpawners.length - 1)], "doSpawnEvent", null, 0, false);
    }
    public static location getRandomLocationInCircle(location locTest, float fltSize) throws InterruptedException
    {
        locTest.x = locTest.x + rand(-1 * fltSize, fltSize);
        locTest.z = locTest.z + rand(-1 * fltSize, fltSize);
        return locTest;
    }
    public static location getRandomLocationAtDistance(location locTest, float fltSize) throws InterruptedException
    {
        int position = 1;
        if (rand(0, 1) == 0)
        {
            position = -1;
        }
        if (rand(0, 1) == 1)
        {
            locTest.x = locTest.x + rand(-1 * fltSize, fltSize);
            locTest.z = locTest.z + fltSize * position;
        }
        else 
        {
            locTest.x = locTest.x + fltSize * position;
            locTest.z = locTest.z + rand(-1 * fltSize, fltSize);
        }
        return locTest;
    }
    public static boolean checkSpawnCount(obj_id self) throws InterruptedException
    {
        int intSpawnCount = getIntObjVar(self, "intSpawnCount");
        int intCurrentSpawnCount = utils.getIntScriptVar(self, "intCurrentSpawnCount");
        return intCurrentSpawnCount < intSpawnCount;
    }
    public static void incrementSpawnCount(obj_id self) throws InterruptedException
    {
        int intCurrentSpawnCount = utils.getIntScriptVar(self, "intCurrentSpawnCount");
        int intSpawnCount = getIntObjVar(self, "intSpawnCount");
        intCurrentSpawnCount = intCurrentSpawnCount + 1;
        if (intCurrentSpawnCount <= intSpawnCount)
        {
            utils.setScriptVar(self, "intCurrentSpawnCount", intCurrentSpawnCount);
        }
    }
    public static void addToSpawnDebugList(obj_id self, obj_id spawned) throws InterruptedException
    {
        if(!utils.inDebugMode()) return;
        Vector debugSpawnList;
        if (utils.hasScriptVar(self, "debugSpawnList"))
        {
            debugSpawnList = utils.getResizeableObjIdArrayScriptVar(self, "debugSpawnList");
            debugSpawnList = utils.addElement(debugSpawnList, spawned);
        } else {
            debugSpawnList = utils.addElement(new Vector(), spawned);
        }
        utils.setScriptVar(self, "debugSpawnList", debugSpawnList);
    }
    public static Vector getAllObjectsWithObjVar(location locTest, String strObjVarName) throws InterruptedException
    {
        Vector objArray = new Vector();
        objArray.setSize(0);
        return objArray;
    }
    public static Vector getObjectsWithObjVar(obj_id objParent, String strObjVarName, Vector objArray) throws InterruptedException
    {
        if (hasObjVar(objParent, strObjVarName))
        {
            objArray = utils.addElement(objArray, objParent);
        }
        obj_id[] objContents = getContents(objParent);
        if ((objContents != null) && (objContents.length > 0))
        {
            for (obj_id objContent : objContents) {
                getObjectsWithObjVar(objContent, strObjVarName, objArray);
            }
        }
        return objArray;
    }
    public static obj_id[] getAllContents(obj_id objObject) throws InterruptedException
    {
        Vector objContents = new Vector();
        objContents.setSize(0);
        obj_id[] objCells = getContents(objObject);
        obj_id[] objTestContents;
        for (obj_id objCell : objCells) {
            objTestContents = getContents(objCell);
            if ((objTestContents != null) && (objTestContents.length > 0)) {
                for (obj_id objTestContent : objTestContents) {
                    objContents = utils.addElement(objContents, objTestContent);
                }
            }
        }
        obj_id[] _objContents = new obj_id[0];
        if (objContents != null)
        {
            _objContents = new obj_id[objContents.size()];
            objContents.toArray(_objContents);
        }
        return _objContents;
    }
    public static void planetSpawnersCreatureDied(obj_id spawner, obj_id deadGuy) throws InterruptedException
    {
        if (!isIdValid(spawner))
        {
            CustomerServiceLog("SPAWNER_OVERLOAD", "Spawner " + spawner + " is invalid");
            return;
        }
        int count = utils.getIntScriptVar(spawner, "count");
        count = count - 1;
        if (count < 0)
        {
            CustomerServiceLog("SPAWNER_OVERLOAD", "Count went below 0 on " + spawner + " on Rori. Rori_npc_medium script.");
            count = 0;
        }
        utils.setScriptVar(spawner, "count", count);
        Vector spawnedList = utils.getResizeableObjIdArrayScriptVar(spawner, "myCreations");
        for (int i = 0; i < spawnedList.size(); i++)
        {
            if (spawnedList.get(i) == deadGuy)
            {
                spawnedList.remove(spawnedList.get(i));
                continue;
            }
            if (spawnedList.get(i) == null)
            {
                spawnedList.remove(spawnedList.get(i));
            }
        }
        utils.setScriptVar(spawner, "myCreations", spawnedList);
    }
    public static obj_id createSpawnInLegacyCell(obj_id dungeon, location creatureLocation, String creatureName) throws InterruptedException
    {
        if (!isValidId(dungeon))
        {
            CustomerServiceLog("bad_spawner_data", "createSpawnInLegacyCell - Dungeon passed to function was invalid.");
            return null;
        }
        if (creatureLocation == null)
        {
            CustomerServiceLog("bad_spawner_data", "createSpawnInLegacyCell - Location passed to function was invalid for dungeon: " + dungeon);
            return null;
        }
        if (creatureName == null || creatureName.length() <= 0)
        {
            CustomerServiceLog("bad_spawner_data", "createSpawnInLegacyCell - Creature Name passed to function was invalid for dungeon: " + dungeon);
            return null;
        }
        obj_id creature = create.object(creatureName, creatureLocation);
        if (!isValidId(creature))
        {
            CustomerServiceLog("bad_spawner_data", "createSpawnInLegacyCell - Creature could not be created for dungeon: " + dungeon);
            return null;
        }
        setObjVar(creature, "dungeon", dungeon);
        create.addDestroyMessage(creature, creatureName + "Dead", 300.0f, dungeon);
        return creature;
    }
    public static boolean spawnObjectsInDungeonFromTable(obj_id dungeon, String planet, String table) throws InterruptedException
    {
        if (!isValidId(dungeon))
        {
            return false;
        }
        if (table == null || table.length() <= 0)
        {
            return false;
        }
        if (planet == null || planet.length() <= 0)
        {
            return false;
        }
        int numberOfObjectsToSpawn = dataTableGetNumRows(table);
        if (numberOfObjectsToSpawn <= 0)
        {
            return false;
        }
        dictionary objToSpawn;
        String object;
        String spawnRoom;
        obj_id room;
        obj_id objectCreated;
        String script;
        String[] scripts;
        String objVars;
        String objName;

        for (int i = 0; i < numberOfObjectsToSpawn; i++)
        {
            objToSpawn = dataTableGetRow(table, i);
            if (objToSpawn == null)
            {
                continue;
            }
            object = objToSpawn.getString("object");
            if (object == null || object.length() <= 0)
            {
                continue;
            }
            float xCoord = objToSpawn.getFloat("loc_x");
            float yCoord = objToSpawn.getFloat("loc_y");
            float zCoord = objToSpawn.getFloat("loc_z");
            float yaw = objToSpawn.getFloat("yaw");
            spawnRoom = objToSpawn.getString("room");
            if (spawnRoom == null || spawnRoom.length() <= 0)
            {
                continue;
            }
            room = getCellId(dungeon, spawnRoom);
            if (!isValidId(room))
            {
                continue;
            }
            objectCreated = createObject(object, new location(xCoord, yCoord, zCoord, planet, room));
            if (!isValidId(objectCreated))
            {
                continue;
            }
            setYaw(objectCreated, yaw);
            script = objToSpawn.getString("script");
            if (script != null && script.length() > 0)
            {
                scripts = split(script, ',');
                for (String script1 : scripts) {
                    if (!hasScript(objectCreated, script1)) {
                        attachScript(objectCreated, script1);
                    }
                }
            }
            objVars = objToSpawn.getString("objvar");
            if (objVars != null && objVars.length() > 0)
            {
                utils.setObjVarsListUsingSemiColon(objectCreated, objVars);
            }
            objName = objToSpawn.getString("name");
            if (objName != null && objName.length() > 0)
            {
                setName(objectCreated, objName);
            }
        }

        return true;
    }

    public static boolean applyGeneratedNpcAppearance(obj_id spawner, obj_id npc, String professionFantasy, boolean deterministicByNpcId) throws InterruptedException
    {
        if (!isIdValid(npc) || !isMob(npc))
        {
            return false;
        }

        if (getIntObjVar(npc, "npc.simProfile.identity.applied.appearanceApplied") == 1)
        {
            removeObjVar(npc, "npc.simProfile.identity.reapplyPending");
            return false;
        }
        int species = getSpecies(npc);
        String speciesKey = getSpeciesKey(species);
        String genderKey = (getGender(npc) == GENDER_FEMALE) ? "female" : "male";

        String profileId = pickWeightedProfile(speciesKey, genderKey, professionFantasy, npc, deterministicByNpcId);
        if (profileId == null || profileId.length() <= 0)
        {
            return false;
        }

        String colorPool = dataTableGetString(APPEARANCE_PROFILE_TABLE, profileId, "color_pool");
        String[] colorSelection = pickWeightedColorScheme(colorPool, npc, deterministicByNpcId);
        if (colorSelection != null)
        {
            setObjVar(npc, "appearance.generated.color_family", colorSelection[0]);
            setObjVar(npc, "appearance.generated.palette_primary", colorSelection[1] + ":" + colorSelection[2]);
            setObjVar(npc, "appearance.generated.palette_secondary", colorSelection[3] + ":" + colorSelection[4]);
        }

        Vector usedSlots = new Vector();
        for (String disabledSlot : DEFAULT_DISABLED_SLOTS) {
            usedSlots = utils.addElement(usedSlots, disabledSlot);
        }

        String outfitPool = dataTableGetString(APPEARANCE_PROFILE_TABLE, profileId, "outfit_pool");
        equipWeightedAssetFromPool(npc, speciesKey, genderKey, outfitPool, "outfit", usedSlots, colorSelection, deterministicByNpcId);

        String accessoryPool = dataTableGetString(APPEARANCE_PROFILE_TABLE, profileId, "accessory_pool");
        equipWeightedAssetFromPool(npc, speciesKey, genderKey, accessoryPool, "accessory", usedSlots, colorSelection, deterministicByNpcId);

        String hairPool = dataTableGetString(APPEARANCE_PROFILE_TABLE, profileId, "hair_pool");
        String hairAsset = pickWeightedAssetId(npc, speciesKey, genderKey, hairPool, "hair", usedSlots, deterministicByNpcId);
        if (hairAsset != null && hairAsset.length() > 0)
        {
            setObjVar(npc, "appearance.generated.hair", hairAsset);
        }

        String facePool = dataTableGetString(APPEARANCE_PROFILE_TABLE, profileId, "face_pool");
        String faceAsset = pickWeightedAssetId(npc, speciesKey, genderKey, facePool, "face", usedSlots, deterministicByNpcId);
        if (faceAsset != null && faceAsset.length() > 0)
        {
            setObjVar(npc, "appearance.generated.face", faceAsset);
        }

        setObjVar(npc, "appearance.generated.profile", profileId);
        setObjVar(npc, "appearance.generated.profession_fantasy", professionFantasy);
        setObjVar(npc, "appearance.generated.seed_mode", deterministicByNpcId ? "deterministic" : "randomized");
        if (isIdValid(spawner))
        {
            setObjVar(npc, "appearance.generated.spawner", spawner);
        }
        setObjVar(npc, "npc.simProfile.identity.reapplyPending", 1);
        removeObjVar(npc, "npc.simProfile.identity.applied.appearanceApplied");
        removeObjVar(npc, "npc.simProfile.identity.applied.blockedByGeneratedAppearance");
        return true;
    }

    public static String inferProfessionFantasyFromCreatureType(String creatureType) throws InterruptedException
    {
        if (creatureType == null)
        {
            return "civilian";
        }
        if (creatureType.indexOf("meatlump") >= 0 || creatureType.indexOf("gang") >= 0 || creatureType.indexOf("thug") >= 0)
        {
            return "raider";
        }
        if (creatureType.indexOf("guard") >= 0 || creatureType.indexOf("trooper") >= 0)
        {
            return "militia";
        }
        return "civilian";
    }

    private static String pickWeightedProfile(String speciesKey, String genderKey, String professionFantasy, obj_id npc, boolean deterministicByNpcId) throws InterruptedException
    {
        int rows = dataTableGetNumRows(APPEARANCE_PROFILE_TABLE);
        if (rows <= 0)
        {
            return null;
        }
        Vector profileIds = new Vector();
        Vector weights = new Vector();
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(APPEARANCE_PROFILE_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String rowSpecies = row.getString("species");
            String rowGender = row.getString("gender");
            String rowFantasy = row.getString("profession_fantasy");
            if (!matchesToken(rowSpecies, speciesKey) || !matchesToken(rowGender, genderKey) || !matchesToken(rowFantasy, professionFantasy))
            {
                continue;
            }
            int weight = row.getInt("weight");
            if (weight <= 0)
            {
                continue;
            }
            profileIds = utils.addElement(profileIds, row.getString("profile_id"));
            weights = utils.addElement(weights, weight);
        }
        return (String)weightedPick(profileIds, weights, "profile", npc, deterministicByNpcId);
    }

    private static String pickWeightedAssetId(obj_id npc, String speciesKey, String genderKey, String poolId, String category, Vector usedSlots, boolean deterministicByNpcId) throws InterruptedException
    {
        if (poolId == null || poolId.length() <= 0)
        {
            return null;
        }
        int rows = dataTableGetNumRows(APPEARANCE_ASSET_TABLE);
        Vector assetIds = new Vector();
        Vector weights = new Vector();
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(APPEARANCE_ASSET_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String rowPool = row.getString("pool_id");
            String rowCategory = row.getString("category");
            String rowSpecies = row.getString("species");
            String rowGender = row.getString("gender");
            String rowSlot = row.getString("slot");
            if (!poolId.equals(rowPool) || !category.equals(rowCategory) || !matchesToken(rowSpecies, speciesKey) || !matchesToken(rowGender, genderKey))
            {
                continue;
            }
            if (rowSlot != null && rowSlot.length() > 0 && vectorContains(usedSlots, rowSlot))
            {
                continue;
            }
            int weight = row.getInt("weight");
            if (weight <= 0)
            {
                continue;
            }
            assetIds = utils.addElement(assetIds, row.getString("asset_id"));
            weights = utils.addElement(weights, weight);
        }
        return (String)weightedPick(assetIds, weights, poolId + ":" + category, npc, deterministicByNpcId);
    }

    private static void equipWeightedAssetFromPool(obj_id npc, String speciesKey, String genderKey, String poolId, String category, Vector usedSlots, String[] colorSelection, boolean deterministicByNpcId) throws InterruptedException
    {
        String asset = pickWeightedAssetId(npc, speciesKey, genderKey, poolId, category, usedSlots, deterministicByNpcId);
        if (asset == null || asset.length() <= 0)
        {
            return;
        }
        int row = dataTableSearchColumnForString(asset, "asset_id", APPEARANCE_ASSET_TABLE);
        if (row < 0)
        {
            return;
        }
        String slot = dataTableGetString(APPEARANCE_ASSET_TABLE, row, "slot");
        obj_id piece = createObject(asset, npc, "");
        if (!isIdValid(piece))
        {
            return;
        }
        if (slot != null && slot.length() > 0)
        {
            obj_id alreadyInSlot = getObjectInSlot(npc, slot);
            if (isIdValid(alreadyInSlot))
            {
                destroyObject(piece);
                return;
            }
            if (equip(piece, npc, slot))
            {
                usedSlots = utils.addElement(usedSlots, slot);
            }
            else
            {
                destroyObject(piece);
                return;
            }
        }
        else
        {
            if (!equip(piece, npc))
            {
                destroyObject(piece);
                return;
            }
        }
        if (colorSelection != null)
        {
            hue.setColor(piece, colorSelection[1], utils.stringToInt(colorSelection[2]));
            hue.setColor(piece, colorSelection[3], utils.stringToInt(colorSelection[4]));
        }
    }

    private static String[] pickWeightedColorScheme(String poolId, obj_id npc, boolean deterministicByNpcId) throws InterruptedException
    {
        if (poolId == null || poolId.length() <= 0)
        {
            return null;
        }
        int rows = dataTableGetNumRows(APPEARANCE_COLOR_TABLE);
        Vector entries = new Vector();
        Vector weights = new Vector();
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(APPEARANCE_COLOR_TABLE, i);
            if (row == null)
            {
                continue;
            }
            if (!poolId.equals(row.getString("pool_id")))
            {
                continue;
            }
            int weight = row.getInt("weight");
            if (weight <= 0)
            {
                continue;
            }
            String encoded = row.getString("family") + "|" + row.getString("primary_path") + "|" + row.getInt("primary_index") + "|" + row.getString("secondary_path") + "|" + row.getInt("secondary_index");
            entries = utils.addElement(entries, encoded);
            weights = utils.addElement(weights, weight);
        }
        String encoded = (String)weightedPick(entries, weights, poolId + ":color", npc, deterministicByNpcId);
        if (encoded == null || encoded.length() <= 0)
        {
            return null;
        }
        return split(encoded, '|');
    }

    private static Object weightedPick(Vector candidates, Vector candidateWeights, String salt, obj_id npc, boolean deterministicByNpcId) throws InterruptedException
    {
        if (candidates == null || candidateWeights == null || candidates.size() == 0 || candidateWeights.size() != candidates.size())
        {
            return null;
        }
        int totalWeight = 0;
        for (int i = 0; i < candidateWeights.size(); i++)
        {
            totalWeight += ((Integer)candidateWeights.get(i));
        }
        if (totalWeight <= 0)
        {
            return null;
        }
        int roll;
        if (deterministicByNpcId)
        {
            roll = stableHashRoll(npc, salt, totalWeight);
        }
        else
        {
            roll = rand(0, totalWeight - 1);
        }
        int running = 0;
        for (int i = 0; i < candidateWeights.size(); i++)
        {
            running += ((Integer)candidateWeights.get(i));
            if (roll < running)
            {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static int stableHashRoll(obj_id npc, String salt, int maxExclusive) throws InterruptedException
    {
        if (maxExclusive <= 1)
        {
            return 0;
        }
        String seed = npc.toString() + "|" + salt;
        int hash = 17;
        for (int i = 0; i < seed.length(); i++)
        {
            hash = (hash * 31) + seed.charAt(i);
        }
        if (hash < 0)
        {
            hash = hash * -1;
        }
        return hash % maxExclusive;
    }

    private static boolean matchesToken(String filter, String value) throws InterruptedException
    {
        if (filter == null || filter.length() <= 0 || filter.equals("*"))
        {
            return true;
        }
        String[] possible = split(filter, ',');
        for (String token : possible) {
            if (token.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean vectorContains(Vector values, String value) throws InterruptedException
    {
        if (values == null || value == null)
        {
            return false;
        }
        for (int i = 0; i < values.size(); i++)
        {
            if (value.equals(values.get(i)))
            {
                return true;
            }
        }
        return false;
    }

    private static String getSpeciesKey(int species) throws InterruptedException
    {
        if (species == SPECIES_HUMAN)
        {
            return "human";
        }
        if (species == SPECIES_RODIAN)
        {
            return "rodian";
        }
        if (species == SPECIES_TRANDOSHAN)
        {
            return "trandoshan";
        }
        if (species == SPECIES_TWILEK)
        {
            return "twilek";
        }
        if (species == SPECIES_MON_CALAMARI)
        {
            return "moncal";
        }
        if (species == SPECIES_ZABRAK)
        {
            return "zabrak";
        }
        if (species == SPECIES_BOTHAN)
        {
            return "bothan";
        }
        return "*";
    }
}
