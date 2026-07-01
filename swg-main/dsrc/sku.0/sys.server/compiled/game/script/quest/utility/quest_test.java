package script.quest.utility;

import script.dictionary;
import script.library.groundquests;
import script.library.rodstart_artifact;
import script.library.utils;
import script.location;
import script.obj_id;
import script.quest.task.ground.retrieve_item;

public class quest_test extends script.base_script
{
    public quest_test()
    {
    }
    public static final String RODSTART_QUEST = "quest/rodstart";
    public static final String RETRIEVE_ITEM_TASK_TYPE = "retrieve_item";
    public static final String RETRIEVE_ITEM_OBJVAR_COUNT = "count";
    public static final String DOT = ".";
    public static final String[] RODSTART_TASKS =
    {
        "rodstart_06",
        "rodstart_05",
        "rodstart_04",
        "rodstart_03",
        "rodstart_02",
        "rodstart_01"
    };
    public static String[] OPTIONS = 
    {
        "=========================",
        "HELP",
        "ACTIVATEQUEST QUESTNAME",
        "COMPLETEQUEST QUESTNAME",
        "CLEARQUESTFLAG QUESTNAME",
        "RODSTART_PICKUP_TEST",
        "RODSTART_PERSIST_TEST",
        "RODSTART_SEARCH_MARKER_TEST",
        "========================="
    };
    public int OnAttach(obj_id self) throws InterruptedException
    {
        sendSystemMessageTestingOnly(self, "Ground quest test script attached");
        return SCRIPT_CONTINUE;
    }
    public int OnSpeaking(obj_id self, String text) throws InterruptedException
    {
        String[] parse = split(text, ' ');
        if (parse[0].equalsIgnoreCase("help"))
        {
            showHelp();
        }
        else if (parse[0].equalsIgnoreCase("showBuildoutArea"))
        {
            location here = getLocation(self);
            obj_id containingBuilding = getTopMostContainer(self);
            if (isIdValid(containingBuilding))
            {
                here = getLocation(containingBuilding);
            }
            String buildoutAreaName = getBuildoutAreaName(here.x, here.z);
            sendSystemMessageTestingOnly(self, "You are in buildout area: " + buildoutAreaName);
        }
        else if (parse[0].equalsIgnoreCase("activatequest"))
        {
            if (parse.length < 2)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX ACTIVATEQUEST QUESTNAME " + text);
                return SCRIPT_CONTINUE;
            }
            boolean result = groundquests.isValidQuestName(parse[1]);
            LOG("debug_test", "boolean result returned " + result);
            if (result == false)
            {
                sendSystemMessageTestingOnly(self, "FAILED TO ACTIVATE QUEST ");
            }
            else 
            {
                groundquests.clearQuest(self, parse[1]);
                groundquests.requestGrantQuest(self, parse[1]);
                sendSystemMessageTestingOnly(self, "Granted Quest: " + parse[1]);
            }
        }
        else if (parse[0].equalsIgnoreCase("completequest"))
        {
            if (parse.length < 2)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX COMPLETEQUEST QUESTNAME: " + text);
                return SCRIPT_CONTINUE;
            }
            groundquests.completeQuest(self, parse[1]);
            return SCRIPT_CONTINUE;
        }
        else if (parse[0].equalsIgnoreCase("clearquestflag"))
        {
            if (parse.length < 2)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX CLEARQUESTFLAG QUESTNAME " + text);
                return SCRIPT_CONTINUE;
            }
            if (!groundquests.isQuestActive(self, parse[1]) && !groundquests.hasCompletedQuest(self, parse[1]))
            {
                sendSystemMessageTestingOnly(self, "Unable to verify quest" + text);
            }
            else 
            {
                groundquests.clearQuest(self, parse[1]);
                sendSystemMessageTestingOnly(self, "Quest data for " + parse[1] + " cleared");
            }
            return SCRIPT_CONTINUE;
        }
        else if (parse[0].equalsIgnoreCase("activatetaskname"))
        {
            if (parse.length < 3)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX ACTIVATETASKNAME QUESTNAME TASKNAME " + text);
                return SCRIPT_CONTINUE;
            }
            String questName = parse[1];
            int questCrc = questGetQuestId(questName);
            if (questCrc == 0)
            {
                sendSystemMessageTestingOnly(self, "BAD QUEST NAME");
            }
            else 
            {
                String taskName = parse[2];
                int taskId = groundquests.getTaskId(questCrc, taskName);
                if (taskId == -1)
                {
                    sendSystemMessageTestingOnly(self, "BAD TASK NAME");
                }
                else 
                {
                    questActivateTask(questCrc, taskId, self);
                }
            }
        }
        else if (parse[0].equalsIgnoreCase("completetaskname"))
        {
            if (parse.length < 3)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX COMPLETETASKNAME QUESTNAME TASKNAME " + text);
                return SCRIPT_CONTINUE;
            }
            String questName = parse[1];
            int questCrc = questGetQuestId(questName);
            if (questCrc == 0)
            {
                sendSystemMessageTestingOnly(self, "BAD QUEST NAME");
            }
            else 
            {
                String taskName = parse[2];
                int taskId = groundquests.getTaskId(questCrc, taskName);
                if (taskId == -1)
                {
                    sendSystemMessageTestingOnly(self, "BAD TASK NAME");
                }
                else 
                {
                    questCompleteTask(questCrc, taskId, self);
                }
            }
        }
        else if (parse[0].equalsIgnoreCase("failtaskname"))
        {
            if (parse.length < 3)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX FAILTASKNAME QUESTNAME TASKNAME " + text);
                return SCRIPT_CONTINUE;
            }
            String questName = parse[1];
            int questCrc = questGetQuestId(questName);
            if (questCrc == 0)
            {
                sendSystemMessageTestingOnly(self, "BAD QUEST NAME");
            }
            else 
            {
                String taskName = parse[2];
                int taskId = groundquests.getTaskId(questCrc, taskName);
                if (taskId == -1)
                {
                    sendSystemMessageTestingOnly(self, "BAD TASK NAME");
                }
                else 
                {
                    questFailTask(questCrc, taskId, self);
                }
            }
        }
        else if (parse[0].equalsIgnoreCase("activatetaskid"))
        {
            if (parse.length < 3)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX ACTIVATETASKID QUESTNAME TASKID " + text);
                return SCRIPT_CONTINUE;
            }
            String questName = parse[1];
            int questCrc = questGetQuestId(questName);
            if (questCrc == 0)
            {
                sendSystemMessageTestingOnly(self, "BAD QUEST NAME");
            }
            else 
            {
                int taskId = utils.stringToInt(parse[2]);
                questActivateTask(questCrc, taskId, self);
            }
        }
        else if (parse[0].equalsIgnoreCase("completetaskid"))
        {
            if (parse.length < 3)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX COMPLETETASKID QUESTNAME TASKID " + text);
                return SCRIPT_CONTINUE;
            }
            String questName = parse[1];
            int questCrc = questGetQuestId(questName);
            if (questCrc == 0)
            {
                sendSystemMessageTestingOnly(self, "BAD QUEST NAME");
            }
            else 
            {
                int taskId = utils.stringToInt(parse[2]);
                questCompleteTask(questCrc, taskId, self);
            }
        }
        else if (parse[0].equalsIgnoreCase("failtaskid"))
        {
            if (parse.length < 3)
            {
                sendSystemMessageTestingOnly(self, "SYNTAX FAILTASKID QUESTNAME TASKID " + text);
                return SCRIPT_CONTINUE;
            }
            String questName = parse[1];
            int questCrc = questGetQuestId(questName);
            if (questCrc == 0)
            {
                sendSystemMessageTestingOnly(self, "BAD QUEST NAME");
            }
            else 
            {
                int taskId = utils.stringToInt(parse[2]);
                questFailTask(questCrc, taskId, self);
            }
        }
        else if (parse[0].equalsIgnoreCase("sendsignal"))
        {
            if (parse.length < 2)
            {
                return SCRIPT_CONTINUE;
            }
            groundquests.sendSignal(self, parse[1]);
        }
        else if (parse[0].equalsIgnoreCase("rodstart_pickup_test"))
        {
            runRodstartPickupTest(self);
        }
        else if (parse[0].equalsIgnoreCase("rodstart_persist_test"))
        {
            runRodstartPersistTest(self);
        }
        else if (parse[0].equalsIgnoreCase("rodstart_search_marker_test"))
        {
            runRodstartSearchMarkerTest(self);
        }
        return SCRIPT_CONTINUE;
    }
    public void showHelp() throws InterruptedException
    {
        for (String option : OPTIONS) {
            sendSystemMessageTestingOnly(getSelf(), option);
        }
        return;
    }

    private int ensureRodstartQuestActive(obj_id player) throws InterruptedException
    {
        if (!groundquests.isQuestActive(player, RODSTART_QUEST))
        {
            groundquests.clearQuest(player, RODSTART_QUEST);
            groundquests.requestGrantQuest(player, RODSTART_QUEST);
        }
        return questGetQuestId(RODSTART_QUEST);
    }

    private void runRodstartPickupTest(obj_id player) throws InterruptedException
    {
        int questCrc = ensureRodstartQuestActive(player);
        if (questCrc == 0)
        {
            sendSystemMessageTestingOnly(player, "Rodstart quest not available.");
            return;
        }
        for (String taskName : RODSTART_TASKS)
        {
            int taskId = groundquests.getTaskId(questCrc, taskName);
            if (taskId < 0)
            {
                sendSystemMessageTestingOnly(player, "Rodstart pickup test failed to find task " + taskName);
                continue;
            }
            questActivateTask(questCrc, taskId, player);
            obj_id artifact = rodstart_artifact.spawnArtifactForTask(player, taskName);
            if (!isIdValid(artifact))
            {
                sendSystemMessageTestingOnly(player, "Rodstart pickup test failed to spawn artifact for " + taskName);
                continue;
            }
            if (!groundquests.playerNeedsToRetrieveThisItem(player, artifact))
            {
                sendSystemMessageTestingOnly(player, "Rodstart pickup test: artifact already marked as retrieved for " + taskName);
                destroyObject(artifact);
                continue;
            }
            dictionary params = new dictionary();
            params.put("source", artifact);
            messageTo(player, "questRetrieveItemObjectFound", params, 0, false);
            boolean completed = groundquests.hasCompletedTask(player, RODSTART_QUEST, taskId);
            boolean needsAfter = groundquests.playerNeedsToRetrieveThisItem(player, artifact);
            if (completed && !needsAfter)
            {
                sendSystemMessageTestingOnly(player, "Rodstart pickup test passed for " + taskName);
            }
            else 
            {
                sendSystemMessageTestingOnly(player, "Rodstart pickup test failed for " + taskName + " (completed=" + completed + ", needsAfter=" + needsAfter + ")");
            }
            destroyObject(artifact);
        }
    }

    private void runRodstartPersistTest(obj_id player) throws InterruptedException
    {
        int questCrc = ensureRodstartQuestActive(player);
        if (questCrc == 0)
        {
            sendSystemMessageTestingOnly(player, "Rodstart quest not available.");
            return;
        }
        String taskName = RODSTART_TASKS[0];
        int taskId = groundquests.getTaskId(questCrc, taskName);
        if (taskId < 0)
        {
            sendSystemMessageTestingOnly(player, "Rodstart persistence test failed to find task " + taskName);
            return;
        }
        questActivateTask(questCrc, taskId, player);
        String baseObjVar = groundquests.getBaseObjVar(player, RETRIEVE_ITEM_TASK_TYPE, questGetQuestName(questCrc), taskId);
        String countObjVar = baseObjVar + DOT + RETRIEVE_ITEM_OBJVAR_COUNT;
        if (!hasObjVar(player, countObjVar))
        {
            setObjVar(player, countObjVar, 0);
        }
        rodstart_artifact.removeSearchAreaWaypoint(player, taskName);
        simulateRetrieveItemLogin(player);
        boolean hasWaypoint = hasSearchAreaWaypoint(player, taskName);
        boolean hasCount = hasObjVar(player, countObjVar);
        sendSystemMessageTestingOnly(player, "Rodstart persistence test " + (hasWaypoint && hasCount ? "passed" : "failed") + " (waypoint=" + hasWaypoint + ", count=" + hasCount + ")");
    }

    private void runRodstartSearchMarkerTest(obj_id player) throws InterruptedException
    {
        int questCrc = ensureRodstartQuestActive(player);
        if (questCrc == 0)
        {
            sendSystemMessageTestingOnly(player, "Rodstart quest not available.");
            return;
        }
        String taskName = RODSTART_TASKS[0];
        int taskId = groundquests.getTaskId(questCrc, taskName);
        if (taskId < 0)
        {
            sendSystemMessageTestingOnly(player, "Rodstart search marker test failed to find task " + taskName);
            return;
        }
        questActivateTask(questCrc, taskId, player);
        boolean hasWaypoint = hasSearchAreaWaypoint(player, taskName);
        if (!hasWaypoint)
        {
            sendSystemMessageTestingOnly(player, "Rodstart search marker test failed to create marker for " + taskName);
            return;
        }
        groundquests.completeTask(player, RODSTART_QUEST, taskId);
        boolean hasWaypointAfter = hasSearchAreaWaypoint(player, taskName);
        sendSystemMessageTestingOnly(player, "Rodstart search marker test " + (!hasWaypointAfter ? "passed" : "failed") + " (marker=" + hasWaypointAfter + ")");
    }

    private void simulateRetrieveItemLogin(obj_id player) throws InterruptedException
    {
        dictionary tasks = groundquests.getActiveTasksForTaskType(player, RETRIEVE_ITEM_TASK_TYPE);
        if ((tasks != null) && !tasks.isEmpty())
        {
            java.util.Enumeration keys = tasks.keys();
            while (keys.hasMoreElements())
            {
                String questCrcString = (String)keys.nextElement();
                int questCrc = utils.stringToInt(questCrcString);
                int[] tasksForCurrentQuest = tasks.getIntArray(questCrcString);
                for (int taskId : tasksForCurrentQuest)
                {
                    String baseObjVar = groundquests.getBaseObjVar(player, RETRIEVE_ITEM_TASK_TYPE, questGetQuestName(questCrc), taskId);
                    String objvarNameCount = baseObjVar + DOT + RETRIEVE_ITEM_OBJVAR_COUNT;
                    if (hasObjVar(player, objvarNameCount))
                    {
                        int lootedCount = getIntObjVar(player, objvarNameCount);
                        int itemsTotal = groundquests.getTaskIntDataEntry(questCrc, taskId, retrieve_item.dataTableColumnNumRequired);
                        questSetQuestTaskCounter(player, questGetQuestName(questCrc), taskId, "quest/groundquests:retrieve_item_counter", lootedCount, itemsTotal);
                    }
                    rodstart_artifact.handleTaskActivated(player, questCrc, taskId);
                }
            }
        }
    }

    private boolean hasSearchAreaWaypoint(obj_id player, String taskName) throws InterruptedException
    {
        String objvarName = rodstart_artifact.OBJVAR_SEARCH_WAYPOINT_PREFIX + taskName;
        if (!hasObjVar(player, objvarName))
        {
            return false;
        }
        obj_id waypoint = getObjIdObjVar(player, objvarName);
        return isIdValid(waypoint);
    }
}
