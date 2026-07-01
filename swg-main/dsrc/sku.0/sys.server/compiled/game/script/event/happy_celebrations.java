package script.event;

import script.dictionary;
import script.library.create;
import script.library.firework;
import script.library.utils;
import script.location;
import script.obj_id;

public class happy_celebrations extends script.base_script
{
    public happy_celebrations()
    {
    }

    public static final String CONFIG_SECTION = "LocalDefaults";
    public static final String CONFIG_FLAG = "happyCelebrations";
    public static final float MUSIC_LOOP_SECONDS = 120.0f;
    public static final float FIREWORK_INTERVAL = 20.0f;
    public static final float WONDER_INTERVAL = 30.0f;
    public static final float MUSIC_RANGE = 128.0f;
    public static final float EFFECT_RANGE = 64.0f;
    public static final String[] WONDER_EFFECTS =
    {
        "clienteffect/droid_effect_confetti.cef",
        "clienteffect/item_ring_hero_mark.cef",
        "clienteffect/npe_systems_terminal.cef"
    };

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (!isCelebrationEnabled())
        {
            scheduleActivationCheck(self);
            return SCRIPT_CONTINUE;
        }
        startCelebration(self);
        return SCRIPT_CONTINUE;
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        if (!isCelebrationEnabled())
        {
            scheduleActivationCheck(self);
            return SCRIPT_CONTINUE;
        }
        startCelebration(self);
        return SCRIPT_CONTINUE;
    }

    private void startCelebration(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, "happyCelebrations.active"))
        {
            return;
        }
        setObjVar(self, "happyCelebrations.active", 1);
        messageTo(self, "playMusicLoop", null, 1.0f, false);
        messageTo(self, "burstFireworks", null, 5.0f, false);
        messageTo(self, "triggerWonder", null, 10.0f, false);
    }

    private boolean isCelebrationEnabled() throws InterruptedException
    {
        String value = getConfigSetting(CONFIG_SECTION, CONFIG_FLAG);
        return value != null && value.equalsIgnoreCase("true");
    }

    private void scheduleActivationCheck(obj_id self) throws InterruptedException
    {
        if (!hasMessageTo(self, "checkCelebrationActivation"))
        {
            messageTo(self, "checkCelebrationActivation", null, 60.0f, false);
        }
    }

    public int checkCelebrationActivation(obj_id self, dictionary params) throws InterruptedException
    {
        if (isCelebrationEnabled())
        {
            startCelebration(self);
        }
        else
        {
            scheduleActivationCheck(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int playMusicLoop(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationEnabled())
        {
            removeObjVar(self, "happyCelebrations.active");
            scheduleActivationCheck(self);
            return SCRIPT_CONTINUE;
        }
        obj_id[] players = getPlayerCreaturesInRange(self, MUSIC_RANGE);
        if (players != null && players.length > 0)
        {
            for (obj_id player : players)
            {
                playMusic(player, "sound/music_ceremony_1.snd");
            }
        }
        messageTo(self, "playMusicLoop", null, MUSIC_LOOP_SECONDS, false);
        return SCRIPT_CONTINUE;
    }

    public int burstFireworks(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationEnabled())
        {
            removeObjVar(self, "happyCelebrations.active");
            scheduleActivationCheck(self);
            return SCRIPT_CONTINUE;
        }
        location here = getLocation(self);
        int totalRows = dataTableGetNumRows(firework.TBL_FX);
        if (totalRows > 0)
        {
            for (int i = 0; i < 5; i++)
            {
                location drop = utils.getRandomLocationInRing(here, 5.0f, 35.0f);
                int row = rand(1, totalRows);
                String template = dataTableGetString(firework.TBL_FX, row, "template");
                obj_id effect = create.object(template, drop);
                if (isIdValid(effect))
                {
                    attachScript(effect, firework.SCRIPT_FIREWORK_CLEANUP);
                }
            }
        }
        messageTo(self, "burstFireworks", null, FIREWORK_INTERVAL, false);
        return SCRIPT_CONTINUE;
    }

    public int triggerWonder(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationEnabled())
        {
            removeObjVar(self, "happyCelebrations.active");
            scheduleActivationCheck(self);
            return SCRIPT_CONTINUE;
        }
        obj_id[] players = getPlayerCreaturesInRange(self, EFFECT_RANGE);
        if (players != null && players.length > 0)
        {
            for (obj_id player : players)
            {
                int roll = rand(0, 2);
                if (roll == 0)
                {
                    doAnimationAction(player, "celebrate");
                }
                else if (roll == 1)
                {
                    playClientEffectLoc(player, WONDER_EFFECTS[rand(0, WONDER_EFFECTS.length - 1)], getLocation(player), 0.0f);
                }
                else
                {
                    sendSystemMessage(player, "The Happy Celebrations event fills you with joyful energy!", null);
                }
            }
        }
        messageTo(self, "triggerWonder", null, WONDER_INTERVAL, false);
        return SCRIPT_CONTINUE;
    }
}
