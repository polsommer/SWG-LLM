package script.systems.tcg;

import script.*;
import script.library.utils;
import script.library.vendor_lib;

public class contriplague_contract extends script.base_script
{
    public contriplague_contract()
    {
    }
    public static final String CONTRIPLAGUE_GREETER_TYPE = "greeter_royal_guard";
    public static final String CONTRIPLAGUE_GREETER_SCRIPT = "systems.tcg.contriplague_greeter";
    public static final string_id SID_INVALID_GREETER_DEED = new string_id("player_structure", "invalid_greeter_deed");
    public static final string_id SID_INVALID_GREETER_LOCATION = new string_id("player_structure", "invalid_greeter_location");
    public static final string_id SID_GREETER_VALIDATION_FAILED = new string_id("player_structure", "greeter_validation_failed");
    public static final string_id SID_INVALID_LOCATION_SAME_CELL = new string_id("player_vendor", "greeter_same_cell_only");
    public static final string_id SID_GREETER_STATUS = new string_id("player_structure", "greeter_status");
    public int OnAttach(obj_id self) throws InterruptedException
    {
        vendor_lib.setObjectOwner(self);
        primeContriplague(self);
        bindExistingGreeter(self);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        obj_id owner = vendor_lib.getObjectOwner(self);
        primeContriplague(self);
        if (isValidId(owner) && hasObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR))
        {
            obj_id childObject = getObjIdObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR);
            if ((!isValidId(childObject) || !exists(childObject)) && vendor_lib.controllerContainmentCheck(self))
            {
                if (hasObjVar(self, vendor_lib.GREETER_LOCATION_OBJVAR))
                {
                    vendor_lib.recreateObjectAtLocation(self, owner, getLocationObjVar(self, vendor_lib.GREETER_LOCATION_OBJVAR));
                }
                else
                {
                    vendor_lib.recreateObject(self, owner);
                }
            }
        }
        bindExistingGreeter(self);
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        obj_id owner = vendor_lib.getObjectOwner(self);
        if (!isValidId(owner) || player != owner || !vendor_lib.controllerContainmentCheck(self))
        {
            return SCRIPT_CONTINUE;
        }
        int root = mi.addRootMenu(menu_info_types.SERVER_MENU3, SID_GREETER_STATUS);
        if (hasObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR))
        {
            mi.addSubMenu(root, menu_info_types.SERVER_MENU2, new string_id("player_structure", "menu_cleanup_greeter"));
            mi.addSubMenu(root, menu_info_types.SERVER_MENU4, new string_id("player_structure", "customize_greeter"));
        }
        else
        {
            mi.addSubMenu(root, menu_info_types.SERVER_MENU1, new string_id("player_structure", "menu_place_greeter"));
        }
        return SCRIPT_CONTINUE;
    }
    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        obj_id owner = vendor_lib.getObjectOwner(self);
        if (!isValidId(owner) || player != owner)
        {
            return SCRIPT_CONTINUE;
        }
        if (item == menu_info_types.SERVER_MENU1)
        {
            deployContriplague(self, player);
        }
        else if (item == menu_info_types.SERVER_MENU2)
        {
            cleanupContriplague(self);
            sendDirtyObjectMenuNotification(self);
        }
        else if (item == menu_info_types.SERVER_MENU4)
        {
            bindExistingGreeter(self);
        }
        else if (item == menu_info_types.SERVER_MENU3)
        {
            bindExistingGreeter(self);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnTransferred(obj_id self, obj_id sourceContainer, obj_id destContainer, obj_id transferer) throws InterruptedException
    {
        if (!vendor_lib.controllerContainmentCheck(self))
        {
            cleanupContriplague(self);
        }
        return SCRIPT_CONTINUE;
    }
    public int OnDestroy(obj_id self) throws InterruptedException
    {
        cleanupContriplague(self);
        return SCRIPT_CONTINUE;
    }
    public int OnPack(obj_id self, dictionary params) throws InterruptedException
    {
        cleanupContriplague(self);
        return SCRIPT_CONTINUE;
    }
    public int bindContriplagueGreeter(obj_id self, dictionary params) throws InterruptedException
    {
        primeContriplague(self);
        if (!hasObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR))
        {
            int retries = 0;
            if (params != null && params.containsKey("retries"))
            {
                retries = params.getInt("retries");
            }
            if (retries < 5)
            {
                dictionary retry = new dictionary();
                retry.put("retries", retries + 1);
                messageTo(self, "bindContriplagueGreeter", retry, 1, false);
            }
            return SCRIPT_CONTINUE;
        }
        bindExistingGreeter(self);
        return SCRIPT_CONTINUE;
    }
    private void primeContriplague(obj_id self) throws InterruptedException
    {
        setObjVar(self, vendor_lib.GREETER_DEED_OBJVAR, CONTRIPLAGUE_GREETER_TYPE);
    }
    private void deployContriplague(obj_id self, obj_id player) throws InterruptedException
    {
        if (!vendor_lib.controllerContainmentCheck(self))
        {
            return;
        }
        if (!vendor_lib.isObjectInSameCellAsController(self, player))
        {
            sendSystemMessage(player, SID_INVALID_LOCATION_SAME_CELL);
            return;
        }
        if (hasObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR))
        {
            cleanupContriplague(self);
        }
        String creatureType = vendor_lib.getGreeterNonVendorCreatureType(player, CONTRIPLAGUE_GREETER_TYPE);
        if (creatureType == null || creatureType.equals(""))
        {
            sendSystemMessage(player, SID_GREETER_VALIDATION_FAILED);
            return;
        }
        if (!vendor_lib.validateNpcPlacementInStructure(player))
        {
            sendSystemMessage(player, SID_INVALID_GREETER_LOCATION);
            return;
        }
        if (!setupGreeterData(self, creatureType))
        {
            sendSystemMessage(player, SID_INVALID_GREETER_DEED);
            return;
        }
        String ownerName = getFirstName(player);
        if (ownerName == null || ownerName.equals(""))
        {
            ownerName = "donor";
        }
        setObjVar(self, vendor_lib.GREETER_CUSTOM_NAME_OBJVAR, "Contriplague");
        setObjVar(self, vendor_lib.GREETER_CREATURE_TYPE_OBJVAR, creatureType);
        if (!vendor_lib.buildNpcInPlayerStructure(self, player, vendor_lib.GREETER_VAR_PREFIX, true))
        {
            sendSystemMessage(player, new string_id("player_structure", "create_failed"));
            return;
        }
        dictionary data = new dictionary();
        data.put("retries", 0);
        messageTo(self, "bindContriplagueGreeter", data, 1, false);
        sendDirtyObjectMenuNotification(self);
    }
    private boolean setupGreeterData(obj_id self, String creatureType) throws InterruptedException
    {
        int row = dataTableSearchColumnForString(creatureType, vendor_lib.COL_CREATURE_TYPE, vendor_lib.TBL_GREETER_NONVENDOR_TABLE);
        if (row < 0)
        {
            return false;
        }
        dictionary greeterDict = dataTableGetRow(vendor_lib.TBL_GREETER_NONVENDOR_TABLE, row);
        if (greeterDict == null || greeterDict.isEmpty())
        {
            return false;
        }
        String greeterNames = greeterDict.getString(vendor_lib.COL_GREETER_CREATURE_NAME);
        String greeterStrIds = greeterDict.getString(vendor_lib.COL_GRTR_STRING_ID);
        utils.setObjVar(self, vendor_lib.GREETER_NAMES_OBJVAR, greeterNames);
        utils.setObjVar(self, vendor_lib.GREETER_STRING_ID_OBJVAR, greeterStrIds);
        utils.setObjVar(self, vendor_lib.GREETER_TYPE_NICHE, greeterDict.getInt(vendor_lib.COL_NICHE));
        utils.setObjVar(self, vendor_lib.GREETER_CAN_DRESS_OBJVAR, greeterDict.getInt(vendor_lib.COL_DRESSED));
        utils.setObjVar(self, vendor_lib.GREETER_TYPE_SPEAKBASIC_OBJVAR, greeterDict.getInt(vendor_lib.COL_SPEAK_BASIC));
        utils.setObjVar(self, vendor_lib.GREETER_HAS_CHAT_OBJVAR, greeterDict.getInt(vendor_lib.COL_SAY_CHAT));
        utils.setObjVar(self, vendor_lib.GREETER_HAS_ANIMS_OBJVAR, greeterDict.getInt(vendor_lib.COL_ANIMATES));
        utils.setObjVar(self, vendor_lib.GREETER_HAS_VO_OBJVAR, greeterDict.getInt(vendor_lib.COL_VO));
        utils.setObjVar(self, vendor_lib.GREETER_HAS_SOUND_OBJVAR, greeterDict.getInt(vendor_lib.COL_SOUNDS));
        utils.setObjVar(self, vendor_lib.GREETER_MOODS_OBJVAR, greeterDict.getInt(vendor_lib.COL_MOODS));
        utils.setObjVar(self, vendor_lib.GREETER_COLOR_OBJVAR, greeterDict.getInt(vendor_lib.COL_COLOR));
        String[] appearanceChoices = split(greeterStrIds, ',');
        String[] appearanceCreatureNames = split(greeterNames, ',');
        if (appearanceChoices == null || appearanceCreatureNames == null || appearanceChoices.length == 0 || appearanceCreatureNames.length == 0)
        {
            return false;
        }
        utils.setObjVar(self, vendor_lib.GREETER_SELECTION_OBJVAR, appearanceChoices[0]);
        utils.setObjVar(self, vendor_lib.GREETER_CREATURE_NAME_OBJVAR, appearanceCreatureNames[0]);
        utils.setObjVar(self, vendor_lib.GREETER_APPEARANCE_LIST, appearanceChoices);
        utils.setObjVar(self, vendor_lib.GREETER_CREATURENAME_LIST_OBJVAR, appearanceCreatureNames);
        return true;
    }
    private void cleanupContriplague(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR))
        {
            return;
        }
        obj_id greeter = getObjIdObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR);
        vendor_lib.removeObjectFromController(self, greeter);
    }
    private void bindExistingGreeter(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR))
        {
            return;
        }
        obj_id greeter = getObjIdObjVar(self, vendor_lib.CHILD_GREETER_NONVENDOR_ID_OBJVAR);
        if (!isValidId(greeter) || !exists(greeter))
        {
            return;
        }
        obj_id owner = getObjIdObjVar(greeter, vendor_lib.GREETER_OWNER_OBJVAR);
        String ownerName = "the donor";
        if (isValidId(owner) && exists(owner))
        {
            ownerName = getFirstName(owner);
        }
        setName(greeter, "Contriplague of " + ownerName);
        setObjVar(greeter, "contriplague.ownerName", ownerName);
        if (!hasScript(greeter, CONTRIPLAGUE_GREETER_SCRIPT))
        {
            attachScript(greeter, CONTRIPLAGUE_GREETER_SCRIPT);
        }
    }
}
