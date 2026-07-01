package script.npc.npcII;

import script.obj_id;
import script.string_id;
import script.library.ai_lib;

public class npcII_conversation extends script.base_script
{
    public npcII_conversation()
    {
    }

    private static final String OBJVAR_ROOT = "npcII.conversation";
    private static final String OBJVAR_NPC_LAST_UNSOLICITED = OBJVAR_ROOT + ".lastUnsolicitedTs";
    private static final String OBJVAR_NPC_RECENT_LINES = OBJVAR_ROOT + ".recentLineIds";
    private static final String OBJVAR_NPC_PLAYER_ROOT = OBJVAR_ROOT + ".players";

    private static final String OBJVAR_PLAYER_ROOT = "npcII.playerConversation";
    private static final String OBJVAR_PLAYER_LAST_TOPIC = OBJVAR_PLAYER_ROOT + ".lastTopic";
    private static final String OBJVAR_PLAYER_AFFINITY = OBJVAR_PLAYER_ROOT + ".affinity";
    private static final String OBJVAR_PLAYER_LAST_INTERACTION = OBJVAR_PLAYER_ROOT + ".lastInteractionTs";
    private static final String OBJVAR_PLAYER_RECENT_RESPONSES = OBJVAR_PLAYER_ROOT + ".recentResponses";

    private static final int MIN_UNSOLICITED_INTERVAL = 20;
    private static final int CONTEXT_COOLDOWN_SECONDS = 90;
    private static final int RECENT_LINE_RING_SIZE = 8;
    private static final int RECENT_RESPONSE_RING_SIZE = 6;

    private static final String TOPIC_GREETING = "greeting";
    private static final String TOPIC_WORK = "work";
    private static final String TOPIC_RUMORS = "rumors";
    private static final String TOPIC_COMBAT = "combat";

    private static final string_id SID_GREETING = new string_id("npc/npcII_convo", "greeting");
    private static final string_id SID_WORK_NEUTRAL = new string_id("npc/npcII_convo", "work_neutral");
    private static final string_id SID_WORK_TRUSTED = new string_id("npc/npcII_convo", "work_trusted");
    private static final string_id SID_RUMORS_NEUTRAL = new string_id("npc/npcII_convo", "rumors_neutral");
    private static final string_id SID_RUMORS_TRUSTED = new string_id("npc/npcII_convo", "rumors_trusted");
    private static final string_id SID_COMBAT = new string_id("npc/npcII_convo", "combat");
    private static final string_id SID_FAREWELL = new string_id("npc/npcII_convo", "farewell");

    private static final string_id RESP_WORK = new string_id("npc/npcII_convo", "resp_work");
    private static final string_id RESP_RUMORS = new string_id("npc/npcII_convo", "resp_rumors");
    private static final string_id RESP_COMBAT = new string_id("npc/npcII_convo", "resp_combat");
    private static final string_id RESP_BYE = new string_id("npc/npcII_convo", "resp_bye");

    public int OnStartNpcConversation(obj_id self, obj_id speaker) throws InterruptedException
    {
        if (!isValidConversationState(self, speaker))
        {
            npcEndConversation(speaker);
            return SCRIPT_CONTINUE;
        }

        int now = getGameTime();
        if (!passesUnsolicitedThrottle(self, now))
        {
            return SCRIPT_CONTINUE;
        }

        String playerKey = getPlayerKey(speaker);
        String topic = pickStartTopic(self, speaker, playerKey, now);
        string_id opening = chooseLineForTopic(self, speaker, playerKey, topic);
        if (opening == null)
        {
            return SCRIPT_CONTINUE;
        }

        trackNpcLine(self, opening);
        rememberPlayerState(self, speaker, playerKey, topic, "start", now, true);

        string_id[] responses = new string_id[4];
        responses[0] = RESP_WORK;
        responses[1] = RESP_RUMORS;
        responses[2] = RESP_COMBAT;
        responses[3] = RESP_BYE;

        npcStartConversation(speaker, self, "npcIIConversation", opening, responses);
        return SCRIPT_CONTINUE;
    }

    public int OnNpcConversationResponse(obj_id self, String convo, obj_id player, string_id response) throws InterruptedException
    {
        if (!isValidConversationState(self, player))
        {
            npcEndConversation(player);
            return SCRIPT_CONTINUE;
        }

        int now = getGameTime();
        String playerKey = getPlayerKey(player);
        String responseId = response != null ? response.getAsciiId() : "";
        if (responseId == null)
        {
            responseId = "";
        }

        pushRingStringObjVar(player, OBJVAR_PLAYER_RECENT_RESPONSES, responseId, RECENT_RESPONSE_RING_SIZE);

        if (responseId.equals("resp_bye"))
        {
            trackNpcLine(self, SID_FAREWELL);
            rememberPlayerState(self, player, playerKey, TOPIC_GREETING, responseId, now, false);
            npcSpeak(player, SID_FAREWELL);
            npcEndConversation(player);
            return SCRIPT_CONTINUE;
        }

        String topic = mapResponseToTopic(responseId);
        string_id line = chooseLineForTopic(self, player, playerKey, topic);
        if (line == null)
        {
            line = SID_WORK_NEUTRAL;
            topic = TOPIC_WORK;
        }

        if (!isLineRecentlyUsed(self, line))
        {
            trackNpcLine(self, line);
            npcSpeak(player, line);
        }

        rememberPlayerState(self, player, playerKey, topic, responseId, now, false);

        string_id[] responses = new string_id[4];
        responses[0] = RESP_WORK;
        responses[1] = RESP_RUMORS;
        responses[2] = RESP_COMBAT;
        responses[3] = RESP_BYE;
        npcSetConversationResponses(player, responses);
        return SCRIPT_CONTINUE;
    }

    public int OnEndNpcConversation(obj_id self, obj_id speaker) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || !isIdValid(speaker) || !exists(speaker))
        {
            return SCRIPT_CONTINUE;
        }
        setObjVar(speaker, OBJVAR_PLAYER_LAST_INTERACTION, getGameTime());
        return SCRIPT_CONTINUE;
    }

    private boolean isValidConversationState(obj_id self, obj_id speaker) throws InterruptedException
    {
        if (!isIdValid(self) || !exists(self) || !isIdValid(speaker) || !exists(speaker))
        {
            return false;
        }
        if (!isPlayer(speaker))
        {
            return false;
        }
        if (ai_lib.isInCombat(self) || ai_lib.isInCombat(speaker))
        {
            return false;
        }
        return true;
    }

    private boolean passesUnsolicitedThrottle(obj_id self, int now) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_NPC_LAST_UNSOLICITED))
        {
            return true;
        }
        int last = getIntObjVar(self, OBJVAR_NPC_LAST_UNSOLICITED);
        return (now - last) >= MIN_UNSOLICITED_INTERVAL;
    }

    private String pickStartTopic(obj_id self, obj_id speaker, String playerKey, int now) throws InterruptedException
    {
        String base = TOPIC_GREETING;
        int workWeight = getPlayerTopicWeight(self, playerKey, TOPIC_WORK);
        int rumorWeight = getPlayerTopicWeight(self, playerKey, TOPIC_RUMORS);
        int combatWeight = getPlayerTopicWeight(self, playerKey, TOPIC_COMBAT);

        int total = 2 + workWeight + rumorWeight + combatWeight;
        int roll = rand(1, total);
        if (roll <= 2)
        {
            base = TOPIC_GREETING;
        }
        else if (roll <= (2 + workWeight))
        {
            base = TOPIC_WORK;
        }
        else if (roll <= (2 + workWeight + rumorWeight))
        {
            base = TOPIC_RUMORS;
        }
        else
        {
            base = TOPIC_COMBAT;
        }

        String cooldownPath = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".cooldown." + base;
        if (hasObjVar(self, cooldownPath))
        {
            int last = getIntObjVar(self, cooldownPath);
            if ((now - last) < CONTEXT_COOLDOWN_SECONDS)
            {
                return TOPIC_WORK;
            }
        }
        return base;
    }

    private string_id chooseLineForTopic(obj_id self, obj_id speaker, String playerKey, String topic) throws InterruptedException
    {
        int affinity = getPlayerAffinity(self, speaker, playerKey);
        if (TOPIC_GREETING.equals(topic))
        {
            return SID_GREETING;
        }
        if (TOPIC_WORK.equals(topic))
        {
            return affinity >= 3 ? SID_WORK_TRUSTED : SID_WORK_NEUTRAL;
        }
        if (TOPIC_RUMORS.equals(topic))
        {
            return affinity >= 5 ? SID_RUMORS_TRUSTED : SID_RUMORS_NEUTRAL;
        }
        return SID_COMBAT;
    }

    private String mapResponseToTopic(String responseId) throws InterruptedException
    {
        if ("resp_rumors".equals(responseId))
        {
            return TOPIC_RUMORS;
        }
        if ("resp_combat".equals(responseId))
        {
            return TOPIC_COMBAT;
        }
        return TOPIC_WORK;
    }

    private void rememberPlayerState(obj_id self, obj_id speaker, String playerKey, String topic, String responseId, int now, boolean unsolicited) throws InterruptedException
    {
        String lastTopicPath = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".lastTopic";
        String affinityPath = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".affinity";
        String lastPath = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".lastInteractionTs";
        String topicWeightPath = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".topicWeight." + topic;
        String cooldownPath = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".cooldown." + topic;
        String branchRoot = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".branch." + topic;

        setObjVar(self, lastTopicPath, topic);
        setObjVar(self, lastPath, now);
        setObjVar(self, cooldownPath, now);

        int topicWeight = hasObjVar(self, topicWeightPath) ? getIntObjVar(self, topicWeightPath) : 0;
        setObjVar(self, topicWeightPath, clamp(topicWeight + 1, 0, 50));

        int branchTotal = hasObjVar(self, branchRoot + ".total") ? getIntObjVar(self, branchRoot + ".total") : 0;
        setObjVar(self, branchRoot + ".total", branchTotal + 1);

        int affinity = hasObjVar(self, affinityPath) ? getIntObjVar(self, affinityPath) : 0;
        if ("resp_bye".equals(responseId))
        {
            affinity -= 1;
        }
        else if (TOPIC_RUMORS.equals(topic) || TOPIC_WORK.equals(topic))
        {
            affinity += 1;
            int branchWins = hasObjVar(self, branchRoot + ".success") ? getIntObjVar(self, branchRoot + ".success") : 0;
            setObjVar(self, branchRoot + ".success", branchWins + 1);
        }
        else if (TOPIC_COMBAT.equals(topic))
        {
            affinity -= 1;
        }

        affinity = clamp(affinity, -20, 20);
        setObjVar(self, affinityPath, affinity);

        setObjVar(speaker, OBJVAR_PLAYER_LAST_TOPIC, topic);
        setObjVar(speaker, OBJVAR_PLAYER_AFFINITY, affinity);
        setObjVar(speaker, OBJVAR_PLAYER_LAST_INTERACTION, now);
        if (unsolicited)
        {
            setObjVar(self, OBJVAR_NPC_LAST_UNSOLICITED, now);
        }
    }

    private int getPlayerTopicWeight(obj_id self, String playerKey, String topic) throws InterruptedException
    {
        String path = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".topicWeight." + topic;
        int learned = hasObjVar(self, path) ? getIntObjVar(self, path) : 0;

        String branchRoot = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".branch." + topic;
        int success = hasObjVar(self, branchRoot + ".success") ? getIntObjVar(self, branchRoot + ".success") : 0;
        int total = hasObjVar(self, branchRoot + ".total") ? getIntObjVar(self, branchRoot + ".total") : 0;
        int outcomeBonus = total > 0 ? (success * 3) / total : 0;

        int result = 1 + learned + outcomeBonus;
        if (result < 1)
        {
            return 1;
        }
        if (result > 20)
        {
            return 20;
        }
        return result;
    }

    private int getPlayerAffinity(obj_id self, obj_id speaker, String playerKey) throws InterruptedException
    {
        String affinityPath = OBJVAR_NPC_PLAYER_ROOT + "." + playerKey + ".affinity";
        if (hasObjVar(self, affinityPath))
        {
            return getIntObjVar(self, affinityPath);
        }
        if (hasObjVar(speaker, OBJVAR_PLAYER_AFFINITY))
        {
            return getIntObjVar(speaker, OBJVAR_PLAYER_AFFINITY);
        }
        return 0;
    }

    private void trackNpcLine(obj_id self, string_id line) throws InterruptedException
    {
        if (line == null)
        {
            return;
        }
        pushRingStringObjVar(self, OBJVAR_NPC_RECENT_LINES, line.getAsciiId(), RECENT_LINE_RING_SIZE);
    }

    private boolean isLineRecentlyUsed(obj_id self, string_id line) throws InterruptedException
    {
        if (line == null || !hasObjVar(self, OBJVAR_NPC_RECENT_LINES))
        {
            return false;
        }
        String[] lines = getStringArrayObjVar(self, OBJVAR_NPC_RECENT_LINES);
        if (lines == null)
        {
            return false;
        }
        String id = line.getAsciiId();
        for (int i = 0; i < lines.length; i++)
        {
            if (id.equals(lines[i]))
            {
                return true;
            }
        }
        return false;
    }

    private void pushRingStringObjVar(obj_id target, String path, String value, int maxSize) throws InterruptedException
    {
        if (target == null || value == null || value.length() == 0)
        {
            return;
        }

        String[] prior = hasObjVar(target, path) ? getStringArrayObjVar(target, path) : null;
        int priorLength = prior != null ? prior.length : 0;
        int newLength = priorLength + 1;
        if (newLength > maxSize)
        {
            newLength = maxSize;
        }

        String[] updated = new String[newLength];
        updated[0] = value;

        int write = 1;
        for (int i = 0; i < priorLength && write < newLength; i++)
        {
            if (value.equals(prior[i]))
            {
                continue;
            }
            updated[write] = prior[i];
            write++;
        }

        setObjVar(target, path, updated);
    }

    private String getPlayerKey(obj_id player) throws InterruptedException
    {
        return player.toString();
    }

    private int clamp(int value, int min, int max) throws InterruptedException
    {
        if (value < min)
        {
            return min;
        }
        if (value > max)
        {
            return max;
        }
        return value;
    }
}
