package script.quest.rodstart;

import script.dictionary;
import script.library.groundquests;
import script.library.rodstart_artifact;
import script.obj_id;

public class artifact_spawner extends script.base_script
{
    public artifact_spawner()
    {
    }
    public static final String MSG_SPAWN = "rodstartArtifactSpawn";
    public static final String MSG_DESPAWN = "rodstartArtifactDespawn";
    public static final int RETRY_DELAY_SECONDS = 5;
    public static final int MAX_RETRIES = 5;

    public int rodstartArtifactSpawn(obj_id self, dictionary params) throws InterruptedException
    {
        String taskName = params.getString("taskName");
        if (taskName == null || taskName.length() == 0)
        {
            return SCRIPT_CONTINUE;
        }
        CustomerServiceLog("rodstart_artifact", "Spawn attempt: player=" + self + " task=" + taskName + " object=none");
        if (!groundquests.isTaskActive(self, rodstart_artifact.QUEST_NAME, taskName))
        {
            rodstart_artifact.clearSpawn(self, taskName);
            return SCRIPT_CONTINUE;
        }
        if (rodstart_artifact.hasActiveSpawn(self, taskName))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id spawn = rodstart_artifact.spawnArtifactForTask(self, taskName);
        if (!isIdValid(spawn))
        {
            CustomerServiceLog("rodstart_artifact", "Spawn failure: player=" + self + " task=" + taskName + " object=invalid");
            int attempts = rodstart_artifact.incrementSpawnRetry(self, taskName);
            if (attempts <= MAX_RETRIES)
            {
                dictionary retryParams = new dictionary();
                retryParams.put("taskName", taskName);
                messageTo(self, MSG_SPAWN, retryParams, RETRY_DELAY_SECONDS, false);
            }
            else 
            {
                CustomerServiceLog("rodstart_artifact", "Spawn failed after retries: player=" + self + " task=" + taskName + " object=invalid attempts=" + attempts);
            }
            return SCRIPT_CONTINUE;
        }
        CustomerServiceLog("rodstart_artifact", "Spawn success: player=" + self + " task=" + taskName + " object=" + spawn);
        rodstart_artifact.clearSpawnRetry(self, taskName);
        rodstart_artifact.trackSpawn(self, taskName, spawn);
        int despawnSeconds = rodstart_artifact.getSpawnDespawnSeconds(taskName);
        if (despawnSeconds > 0)
        {
            dictionary despawnParams = new dictionary();
            despawnParams.put("taskName", taskName);
            messageTo(self, MSG_DESPAWN, despawnParams, despawnSeconds, false);
        }
        return SCRIPT_CONTINUE;
    }

    public int rodstartArtifactDespawn(obj_id self, dictionary params) throws InterruptedException
    {
        String taskName = params.getString("taskName");
        if (taskName == null || taskName.length() == 0)
        {
            return SCRIPT_CONTINUE;
        }
        rodstart_artifact.clearSpawn(self, taskName);
        if (!groundquests.isTaskActive(self, rodstart_artifact.QUEST_NAME, taskName))
        {
            return SCRIPT_CONTINUE;
        }
        int respawnSeconds = rodstart_artifact.getSpawnRespawnSeconds(taskName);
        if (respawnSeconds > 0)
        {
            dictionary respawnParams = new dictionary();
            respawnParams.put("taskName", taskName);
            messageTo(self, MSG_SPAWN, respawnParams, respawnSeconds, false);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnDetach(obj_id self) throws InterruptedException
    {
        rodstart_artifact.clearAllSpawns(self);
        return SCRIPT_CONTINUE;
    }
}
