package script.library;

import script.dictionary;
import script.obj_id;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class gcw_campaign extends script.base_script
{
    private static final String CAMPAIGN_TABLE = "datatables/gcw/campaign_states.iff";

    private static Map<String, List<CampaignPhase>> PHASES_BY_PLANET;
    private static Map<String, CampaignContext> CONTEXT_BY_PLANET;

    public static final class CampaignPhase
    {
        public final String planet;
        public final int order;
        public final String name;
        public final int durationHours;
        public final int supplyBaseline;
        public final float spaceWeight;
        public final float groundWeight;
        public final float enclaveWeight;

        public CampaignPhase()
        {
            this("", 0, "", 0, 0, 0.0f, 0.0f, 0.0f);
        }

        public CampaignPhase(String planet, int order, String name, int durationHours, int supplyBaseline, float spaceWeight, float groundWeight, float enclaveWeight)
        {
            this.planet = planet;
            this.order = order;
            this.name = name;
            this.durationHours = durationHours;
            this.supplyBaseline = supplyBaseline;
            this.spaceWeight = spaceWeight;
            this.groundWeight = groundWeight;
            this.enclaveWeight = enclaveWeight;
        }
    }

    public static final class CampaignContext
    {
        public int index;
        public int phaseStart;
        public float imperialSupply;
        public float rebelSupply;
        public float neutralSupply;

        public CampaignContext()
        {
            this(0, 0);
            resetSupplies();
        }

        public CampaignContext(int index, int phaseStart)
        {
            this.index = index;
            this.phaseStart = phaseStart;
        }

        public void resetSupplies()
        {
            imperialSupply = 0.0f;
            rebelSupply = 0.0f;
            neutralSupply = 0.0f;
        }
    }

    public gcw_campaign()
    {
    }

    private static void ensureLoaded() throws InterruptedException
    {
        if (PHASES_BY_PLANET != null)
        {
            return;
        }
        PHASES_BY_PLANET = new HashMap<>();
        CONTEXT_BY_PLANET = new HashMap<>();
        int rows = dataTableGetNumRows(CAMPAIGN_TABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(CAMPAIGN_TABLE, i);
            if (row == null)
            {
                continue;
            }
            String planet = row.getString("planet");
            if (planet == null || planet.length() == 0)
            {
                continue;
            }
            planet = planet.toLowerCase();
            CampaignPhase phase = new CampaignPhase(planet,
                    row.getInt("phaseOrder"),
                    row.getString("phaseName"),
                    row.getInt("durationHours"),
                    row.getInt("supplyBaseline"),
                    row.getFloat("spaceWeight"),
                    row.getFloat("groundWeight"),
                    row.getFloat("enclaveWeight"));
            List<CampaignPhase> phases = PHASES_BY_PLANET.get(planet);
            if (phases == null)
            {
                phases = new ArrayList<>();
                PHASES_BY_PLANET.put(planet, phases);
            }
            phases.add(phase);
        }
        for (List<CampaignPhase> phases : PHASES_BY_PLANET.values())
        {
            phases.sort((a, b) -> Integer.compare(a.order, b.order));
        }
    }

    private static CampaignContext ensureContext(String planet) throws InterruptedException
    {
        ensureLoaded();
        planet = normalizePlanet(planet);
        CampaignContext ctx = CONTEXT_BY_PLANET.get(planet);
        if (ctx == null)
        {
            ctx = new CampaignContext(0, getCalendarTime());
            ctx.resetSupplies();
            CONTEXT_BY_PLANET.put(planet, ctx);
        }
        advancePhaseIfNeeded(planet, ctx);
        return ctx;
    }

    private static void advancePhaseIfNeeded(String planet, CampaignContext ctx) throws InterruptedException
    {
        List<CampaignPhase> phases = PHASES_BY_PLANET.get(planet);
        if (phases == null || phases.isEmpty())
        {
            return;
        }
        int now = getCalendarTime();
        CampaignPhase phase = phases.get(ctx.index % phases.size());
        int duration = Math.max(1, phase.durationHours) * 3600;
        if (now - ctx.phaseStart >= duration)
        {
            ctx.index = (ctx.index + 1) % phases.size();
            ctx.phaseStart = now;
            ctx.resetSupplies();
            LOG("gcw_campaign", "Phase advanced on " + planet + " to " + phases.get(ctx.index).name);
        }
    }

    private static CampaignPhase getCurrentPhase(String planet) throws InterruptedException
    {
        ensureLoaded();
        planet = normalizePlanet(planet);
        List<CampaignPhase> phases = PHASES_BY_PLANET.get(planet);
        if (phases == null || phases.isEmpty())
        {
            return null;
        }
        CampaignContext ctx = ensureContext(planet);
        return phases.get(ctx.index % phases.size());
    }

    public static void recordSupplyContribution(String planet, int faction, float amount, String source) throws InterruptedException
    {
        recordSupplyContribution(planet, resolveFactionName(faction), amount, source);
    }

    public static void recordSupplyContribution(String planet, String factionName, float amount, String source) throws InterruptedException
    {
        CampaignContext ctx = ensureContext(planet);
        CampaignPhase phase = getCurrentPhase(planet);
        if (phase == null)
        {
            return;
        }
        float weighted = amount;
        if (source != null)
        {
            String lower = source.toLowerCase();
            if (lower.contains("space"))
            {
                weighted *= phase.spaceWeight;
            }
            else if (lower.contains("ground"))
            {
                weighted *= phase.groundWeight;
            }
            else if (lower.contains("enclave"))
            {
                weighted *= phase.enclaveWeight;
            }
        }
        String normalized = normalizeFactionName(factionName);
        if (isImperial(normalized))
        {
            ctx.imperialSupply += weighted;
        }
        else if (isRebel(normalized))
        {
            ctx.rebelSupply += weighted;
        }
        else
        {
            ctx.neutralSupply += weighted;
        }
    }

    public static float getEffectiveSupply(String planet, int faction) throws InterruptedException
    {
        return getEffectiveSupply(planet, resolveFactionName(faction));
    }

    public static float getEffectiveSupply(String planet, String factionName) throws InterruptedException
    {
        CampaignContext ctx = ensureContext(planet);
        CampaignPhase phase = getCurrentPhase(planet);
        if (phase == null)
        {
            return 0.0f;
        }
        float baseline = phase.supplyBaseline;
        String normalized = normalizeFactionName(factionName);
        if (isImperial(normalized))
        {
            return baseline + ctx.imperialSupply;
        }
        if (isRebel(normalized))
        {
            return baseline + ctx.rebelSupply;
        }
        return baseline + ctx.neutralSupply;
    }

    public static String getCurrentPhaseName(String planet) throws InterruptedException
    {
        CampaignPhase phase = getCurrentPhase(planet);
        return phase != null ? phase.name : "Preparation";
    }

    public static int getSecondsUntilPhaseAdvance(String planet) throws InterruptedException
    {
        ensureLoaded();
        planet = normalizePlanet(planet);
        CampaignContext ctx = ensureContext(planet);
        List<CampaignPhase> phases = PHASES_BY_PLANET.get(planet);
        if (phases == null || phases.isEmpty())
        {
            return 0;
        }
        CampaignPhase phase = phases.get(ctx.index % phases.size());
        int duration = Math.max(1, phase.durationHours) * 3600;
        int elapsed = getCalendarTime() - ctx.phaseStart;
        return Math.max(0, duration - elapsed);
    }

    public static dictionary getCampaignSummary(String planet) throws InterruptedException
    {
        CampaignContext ctx = ensureContext(planet);
        CampaignPhase phase = getCurrentPhase(planet);
        dictionary d = new dictionary();
        if (phase != null)
        {
            d.put("phase", phase.name);
            d.put("baseline", phase.supplyBaseline);
            d.put("spaceWeight", phase.spaceWeight);
            d.put("groundWeight", phase.groundWeight);
            d.put("enclaveWeight", phase.enclaveWeight);
        }
        d.put("imperialSupply", ctx.imperialSupply);
        d.put("rebelSupply", ctx.rebelSupply);
        d.put("neutralSupply", ctx.neutralSupply);
        d.put("secondsToNextPhase", getSecondsUntilPhaseAdvance(planet));
        return d;
    }

    private static String normalizePlanet(String planet)
    {
        if (planet == null || planet.length() == 0)
        {
            return "tatooine";
        }
        return planet.toLowerCase();
    }

    private static String resolveFactionName(int faction) throws InterruptedException
    {
        String factionName = factions.getFactionNameByHashCode(faction);
        if (factionName == null || factionName.length() == 0)
        {
            return factions.FACTION_NEUTRAL;
        }
        return factionName;
    }

    private static String normalizeFactionName(String factionName)
    {
        if (factionName == null)
        {
            return factions.FACTION_NEUTRAL;
        }
        String trimmed = factionName.trim();
        return trimmed.length() > 0 ? trimmed : factions.FACTION_NEUTRAL;
    }

    private static boolean isImperial(String factionName)
    {
        return factionName != null && factionName.equalsIgnoreCase(factions.FACTION_IMPERIAL);
    }

    private static boolean isRebel(String factionName)
    {
        return factionName != null && factionName.equalsIgnoreCase(factions.FACTION_REBEL);
    }
}
