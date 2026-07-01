package script.conversation;

import script.library.*;
import script.obj_id;

public class townperson_social_dispatcher extends script.base_script
{
    public townperson_social_dispatcher()
    {
    }

    public static boolean handlePlayerInteraction(obj_id npc, obj_id player, String goal) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc) || !isIdValid(player) || !exists(player))
        {
            return false;
        }
        if (!npc_social_memory.canStartInteraction(npc, player, npc_social_memory.PLAYER_INTERACTION_COOLDOWN))
        {
            ai_lib.doAction(npc, "dismiss");
            chat.chat(npc, "Hold on, friend, we just spoke.");
            return false;
        }

        String topic = npc_social_memory.selectTopic(npc, player, goal);
        int affinity = npc_social_memory.getAffinity(npc, player);
        int affinityDelta = 1;
        boolean hostile = false;

        if (topic.equals("combat"))
        {
            ai_lib.setMood(npc, ai_lib.MOOD_NERVOUS);
            doAnimationAction(npc, "point_away");
            chat.chat(npc, "Keep your eyes up. Trouble has been circling nearby.");
            npc_social_memory.noteEvent(npc, "combat");
            affinityDelta = 2;
        }
        else if (topic.equals("trade"))
        {
            ai_lib.setMood(npc, chat.MOOD_HAPPY);
            doAnimationAction(npc, "beckon");
            chat.chat(npc, "Best bargains are moving through the bazaar today.");
            npc_social_memory.noteEvent(npc, "trade");
            affinityDelta = 2;
        }
        else if (topic.equals("mission"))
        {
            ai_lib.setMood(npc, ai_lib.MOOD_CALM);
            doAnimationAction(npc, "point_forward");
            chat.chat(npc, "If you're mission-bound, keep to the lit routes.");
            npc_social_memory.noteEvent(npc, "mission");
            affinityDelta = 1;
        }
        else if (topic.equals("rumor"))
        {
            ai_lib.setMood(npc, chat.MOOD_CURIOUS);
            doAnimationAction(npc, "shrug_hands");
            chat.chat(npc, "Heard any good rumors from the cantina crowd?");
            affinityDelta = 1;
        }
        else if (topic.equals("travel"))
        {
            ai_lib.setMood(npc, ai_lib.MOOD_CALM);
            doAnimationAction(npc, "point_away");
            chat.chat(npc, "The wilderness paths are rough today. Stay alert out there.");
            affinityDelta = 1;
        }
        else
        {
            ai_lib.setMood(npc, ai_lib.MOOD_CALM);
            doAnimationAction(npc, "wave");
            chat.chat(npc, "Good to see a friendly face.");
        }

        if (affinity < -30)
        {
            hostile = true;
            affinityDelta = -2;
            ai_lib.setMood(npc, chat.MOOD_ANGRY);
            doAnimationAction(npc, "threaten");
            chat.chat(npc, "I remember you. Keep your distance.");
        }

        npc_social_memory.recordInteraction(npc, player, topic, affinityDelta, hostile);
        return true;
    }
}
