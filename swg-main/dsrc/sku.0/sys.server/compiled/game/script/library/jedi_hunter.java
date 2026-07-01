package script.library;

import script.obj_id;

import java.util.Vector;

public class jedi_hunter extends script.base_script
{
    public jedi_hunter()
    {
    }

    public static final int RESULT_NONE = 0;
    public static final int RESULT_NOT_JEDI = 1;
    public static final int RESULT_RELEASED = 2;
    public static final int RESULT_ENGAGE = 3;

    public static final String OBJVAR_POLICY = "jediHunter.policy";
    public static final String OBJVAR_FINE_MAX_LEVEL = OBJVAR_POLICY + ".intJediFineMaxLevel";
    public static final String OBJVAR_FINE_AMOUNT = OBJVAR_POLICY + ".fineAmount";
    public static final String OBJVAR_TARGET_COOLDOWN = OBJVAR_POLICY + ".cooldown";
    public static final String OBJVAR_FINE_COOLDOWN = OBJVAR_POLICY + ".fineCooldown";
    public static final String OBJVAR_LAST_FINE = OBJVAR_POLICY + ".lastFine";
    public static final String OBJVAR_SCAN_RADIUS = OBJVAR_POLICY + ".scanRadius";
    public static final String OBJVAR_TAUNT_COOLDOWN = OBJVAR_POLICY + ".tauntCooldown";
    public static final String OBJVAR_RESPAWN_DELAY = OBJVAR_POLICY + ".respawnDelay";
    public static final String OBJVAR_BARK_NAMESPACE = OBJVAR_POLICY + ".barkNamespace";
    public static final String OBJVAR_SPAWN_MASTER = "jediHunter.master";
    public static final String OBJVAR_SQUAD_ID = "jediHunter.squadId";
    public static final String OBJVAR_SQUAD_DATA_PREFIX = "jediHunter.squadData.";

    public static final String BARK_TABLE = "datatables/spawning/jedi_hunters/barks.tab";
    public static final String DEFAULT_BARK_NAMESPACE = "hunter_barks";

    public static final String BARK_INTERROGATE = "interrogate";
    public static final String BARK_DISMISS_NON_JEDI = "dismiss_non_jedi";
    public static final String BARK_FINE_DEMAND = "fine_demand";
    public static final String BARK_ALERT_ATTACK = "alert_attack";
    public static final String BARK_VICTORY = "victory";
    public static final String BARK_DISENGAGE = "disengage";

    public static final int DEFAULT_FINE_MAX_LEVEL = 45;
    public static final int DEFAULT_FINE_AMOUNT = 2500;
    public static final int DEFAULT_TARGET_COOLDOWN_SECONDS = 180;
    public static final int DEFAULT_FINE_COOLDOWN_SECONDS = 600;
    public static final float DEFAULT_SCAN_RADIUS = 80.0f;
    public static final int DEFAULT_TAUNT_COOLDOWN_SECONDS = 24;
    public static final int DEFAULT_RESPAWN_DELAY_SECONDS = 300;

    public static int evaluatePolicy(obj_id hunter, obj_id target) throws InterruptedException
    {
        if (!isIdValid(hunter) || !isIdValid(target) || !exists(target) || !isPlayer(target))
        {
            return RESULT_NONE;
        }

        if (!isJediTarget(target))
        {
            return RESULT_NOT_JEDI;
        }

        if (isOnCooldown(hunter, target, OBJVAR_TARGET_COOLDOWN, DEFAULT_TARGET_COOLDOWN_SECONDS))
        {
            return RESULT_RELEASED;
        }

        int targetLevel = getLevel(target);
        if (targetLevel <= getJediFineMaxLevel(hunter))
        {
            if (attemptFine(hunter, target))
            {
                applyCooldown(hunter, target, OBJVAR_TARGET_COOLDOWN, DEFAULT_TARGET_COOLDOWN_SECONDS);
                return RESULT_RELEASED;
            }
        }

        alertAndEngageSquad(hunter, target);
        applyCooldown(hunter, target, OBJVAR_TARGET_COOLDOWN, DEFAULT_TARGET_COOLDOWN_SECONDS);
        return RESULT_ENGAGE;
    }

    public static boolean isJediTarget(obj_id target) throws InterruptedException
    {
        if (!isIdValid(target) || !isPlayer(target))
        {
            return false;
        }

        if (isJedi(target) || ai_lib.checkForJedi(target))
        {
            return true;
        }

        return getJediState(target) > 0;
    }

    public static int getJediFineMaxLevel(obj_id hunter) throws InterruptedException
    {
        if (hasObjVar(hunter, OBJVAR_FINE_MAX_LEVEL))
        {
            return getIntObjVar(hunter, OBJVAR_FINE_MAX_LEVEL);
        }

        obj_id director = getDirector(hunter);
        if (isIdValid(director) && hasObjVar(director, OBJVAR_FINE_MAX_LEVEL))
        {
            return getIntObjVar(director, OBJVAR_FINE_MAX_LEVEL);
        }

        String cfg = getConfigSetting("GameServer", "intJediFineMaxLevel");
        if (cfg != null && cfg.length() > 0)
        {
            int parsed = utils.stringToInt(cfg);
            if (parsed > 0)
            {
                return parsed;
            }
        }

        return DEFAULT_FINE_MAX_LEVEL;
    }

    private static boolean attemptFine(obj_id hunter, obj_id target) throws InterruptedException
    {
        if (isOnCooldown(hunter, target, OBJVAR_FINE_COOLDOWN, DEFAULT_FINE_COOLDOWN_SECONDS))
        {
            chat.chat(hunter, getBarkLine(hunter, BARK_DISENGAGE, "Your prior compliance payment is still on record. Move along."));
            return true;
        }

        int fineAmount = getFineAmount(hunter, target);
        chat.chat(hunter, getBarkLine(hunter, BARK_FINE_DEMAND, "Pay " + fineAmount + " credits for restricted-force compliance and you will be released."));

        if (money.hasFunds(target, money.MT_TOTAL, fineAmount))
        {
            money.requestPayment(target, hunter, fineAmount, "jedi_hunter_fine", null);
            setObjVar(hunter, OBJVAR_LAST_FINE + "." + target, getGameTime());
            applyCooldown(hunter, target, OBJVAR_FINE_COOLDOWN, DEFAULT_FINE_COOLDOWN_SECONDS);
            sendSystemMessage(target, "Jedi hunter patrol accepted your payment of " + fineAmount + " credits.", null);
            chat.chat(hunter, getBarkLine(hunter, BARK_DISENGAGE, "Payment accepted. Remain non-combatant and leave this area."));
            return true;
        }

        sendSystemMessage(target, "You do not have enough credits to satisfy the Jedi hunter compliance fine.", null);
        chat.chat(hunter, getBarkLine(hunter, BARK_ALERT_ATTACK, "Insufficient credits detected. Escalating to combat response."));
        return false;
    }

    private static void alertAndEngageSquad(obj_id hunter, obj_id target) throws InterruptedException
    {
        if (!isIdValid(hunter) || !isIdValid(target))
        {
            return;
        }

        jedi.doJediTEF(target);

        obj_id director = getDirector(hunter);
        String squadId = getSquadId(hunter);
        if (isIdValid(director) && squadId != null)
        {
            String basePath = getSquadDataPath(squadId);
            setObjVar(director, basePath + "alertState", "engaged");
            setObjVar(director, basePath + "alertTarget", target);
            setObjVar(director, basePath + "target", target);
            setObjVar(director, basePath + "alertTime", getGameTime());
            if (!hasObjVar(director, basePath + "leader"))
            {
                setObjVar(director, basePath + "leader", hunter);
            }
        }

        Vector members = getSquadMembers(hunter);
        if (members != null)
        {
            for (int i = 0; i < members.size(); i++)
            {
                obj_id member = (obj_id)members.get(i);
                if (!isIdValid(member) || !exists(member) || isDead(member) || isIncapacitated(member))
                {
                    continue;
                }
                startCombat(member, target);
            }
        }

        startCombat(hunter, target);
    }

    private static Vector getSquadMembers(obj_id hunter) throws InterruptedException
    {
        obj_id director = getDirector(hunter);
        String squadId = getSquadId(hunter);
        if (!isIdValid(director) || squadId == null)
        {
            return null;
        }

        String memberPath = getSquadDataPath(squadId) + "members";
        if (!hasObjVar(director, memberPath))
        {
            return null;
        }

        Vector members = new Vector();
        obj_id[] staticMembers = getObjIdArrayObjVar(director, memberPath);
        if (staticMembers != null)
        {
            for (int i = 0; i < staticMembers.length; i++)
            {
                members.add(staticMembers[i]);
            }
        }

        if (members.size() > 0)
        {
            return members;
        }

        return getResizeableObjIdArrayObjVar(director, memberPath);
    }

    private static String getSquadId(obj_id hunter) throws InterruptedException
    {
        if (!hasObjVar(hunter, OBJVAR_SQUAD_ID))
        {
            return null;
        }

        String squadId = getStringObjVar(hunter, OBJVAR_SQUAD_ID);
        if (squadId == null || squadId.length() < 1)
        {
            return null;
        }
        return squadId;
    }

    private static obj_id getDirector(obj_id hunter) throws InterruptedException
    {
        if (!hasObjVar(hunter, OBJVAR_SPAWN_MASTER))
        {
            return null;
        }
        return getObjIdObjVar(hunter, OBJVAR_SPAWN_MASTER);
    }

    private static String getSquadDataPath(String squadId)
    {
        return OBJVAR_SQUAD_DATA_PREFIX + squadId + ".";
    }

    private static int getFineAmount(obj_id hunter, obj_id target) throws InterruptedException
    {
        int configured = 0;
        if (hasObjVar(hunter, OBJVAR_FINE_AMOUNT))
        {
            configured = getIntObjVar(hunter, OBJVAR_FINE_AMOUNT);
        }
        else
        {
            obj_id director = getDirector(hunter);
            if (isIdValid(director) && hasObjVar(director, OBJVAR_FINE_AMOUNT))
            {
                configured = getIntObjVar(director, OBJVAR_FINE_AMOUNT);
            }
        }

        if (configured <= 0)
        {
            configured = DEFAULT_FINE_AMOUNT;
        }

        int scaled = getLevel(target) * 150;
        if (scaled > configured)
        {
            configured = scaled;
        }

        return configured;
    }

    public static float getScanRadius(obj_id hunter) throws InterruptedException
    {
        float configured = getBehaviorFloat(hunter, OBJVAR_SCAN_RADIUS, DEFAULT_SCAN_RADIUS);
        if (configured < 25.0f)
        {
            configured = 25.0f;
        }
        return configured;
    }

    public static int getTauntCooldown(obj_id hunter) throws InterruptedException
    {
        int configured = getBehaviorInt(hunter, OBJVAR_TAUNT_COOLDOWN, DEFAULT_TAUNT_COOLDOWN_SECONDS);
        if (configured < 5)
        {
            configured = 5;
        }
        return configured;
    }

    public static int getRespawnDelay(obj_id hunter) throws InterruptedException
    {
        int configured = getBehaviorInt(hunter, OBJVAR_RESPAWN_DELAY, DEFAULT_RESPAWN_DELAY_SECONDS);
        if (configured < 10)
        {
            configured = 10;
        }
        return configured;
    }

    public static String getBarkLine(obj_id hunter, String key, String fallback) throws InterruptedException
    {
        String namespace = DEFAULT_BARK_NAMESPACE;
        if (hasObjVar(hunter, OBJVAR_BARK_NAMESPACE))
        {
            namespace = getStringObjVar(hunter, OBJVAR_BARK_NAMESPACE);
        }
        else
        {
            obj_id director = getDirector(hunter);
            if (isIdValid(director) && hasObjVar(director, OBJVAR_BARK_NAMESPACE))
            {
                namespace = getStringObjVar(director, OBJVAR_BARK_NAMESPACE);
            }
        }

        String line = getBarkFromTable(namespace, key);
        if (line == null || line.length() < 1)
        {
            return fallback;
        }
        return line;
    }

    private static String getBarkFromTable(String namespace, String key) throws InterruptedException
    {
        if (!dataTableOpen(BARK_TABLE))
        {
            return null;
        }

        int rows = dataTableGetNumRows(BARK_TABLE);
        for (int i = 0; i < rows; i++)
        {
            String rowNamespace = dataTableGetString(BARK_TABLE, i, "namespace");
            String rowKey = dataTableGetString(BARK_TABLE, i, "key");
            if (!namespace.equals(rowNamespace) || !key.equals(rowKey))
            {
                continue;
            }
            return dataTableGetString(BARK_TABLE, i, "line");
        }

        return null;
    }

    private static int getBehaviorInt(obj_id hunter, String objvar, int fallback) throws InterruptedException
    {
        if (hasObjVar(hunter, objvar))
        {
            return getIntObjVar(hunter, objvar);
        }

        obj_id director = getDirector(hunter);
        if (isIdValid(director) && hasObjVar(director, objvar))
        {
            return getIntObjVar(director, objvar);
        }

        return fallback;
    }

    private static float getBehaviorFloat(obj_id hunter, String objvar, float fallback) throws InterruptedException
    {
        if (hasObjVar(hunter, objvar))
        {
            return getFloatObjVar(hunter, objvar);
        }

        obj_id director = getDirector(hunter);
        if (isIdValid(director) && hasObjVar(director, objvar))
        {
            return getFloatObjVar(director, objvar);
        }

        return fallback;
    }

    private static boolean isOnCooldown(obj_id hunter, obj_id target, String cooldownBase, int defaultSeconds) throws InterruptedException
    {
        int now = getGameTime();
        String path = cooldownBase + "." + target;
        if (hasObjVar(hunter, path) && getIntObjVar(hunter, path) > now)
        {
            return true;
        }

        obj_id director = getDirector(hunter);
        if (isIdValid(director) && hasObjVar(director, path) && getIntObjVar(director, path) > now)
        {
            return true;
        }

        return false;
    }

    private static void applyCooldown(obj_id hunter, obj_id target, String cooldownBase, int defaultSeconds) throws InterruptedException
    {
        int expires = getGameTime() + defaultSeconds;
        String path = cooldownBase + "." + target;
        setObjVar(hunter, path, expires);

        obj_id director = getDirector(hunter);
        if (isIdValid(director))
        {
            setObjVar(director, path, expires);
        }
    }
}
