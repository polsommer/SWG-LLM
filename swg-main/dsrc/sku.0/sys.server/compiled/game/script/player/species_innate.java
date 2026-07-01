package script.player;

import script.dictionary;
import script.library.innate;
import script.library.prose;
import script.obj_id;
import script.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class species_innate extends script.base_script
{
    public species_innate()
    {
    }
    public static final String SCRIPT_ME = "player.species_innate";
    public static final string_id SID_NONE = new string_id("innate", "none");
    public static final string_id SID_VALID_INNATE_PARAMS = new string_id("innate", "valid_innate_params");
    public static final string_id SID_NOT_VALID_PARAM_INNATE = new string_id("innate", "not_valid_param_innate");
    public static final string_id SID_INNATE_ABILITY_FAILED = new string_id("innate", "innate_ability_failed");
    private static final String DATATABLE = "datatables/player/species_innate.iff";

    private static final Map<Integer, SpeciesInnateDefinition> DEFINITIONS_BY_ID = new HashMap<>();
    private static final List<SpeciesInnateDefinition> DEFINITIONS_BY_TOKEN = new ArrayList<>();
    private static boolean definitionsLoaded = false;
    static final String[] NO_LANGUAGES = new String[0];

    public static final class SpeciesInnateDefinition
    {
        public final int speciesId;
        public final String templateToken;
        public final String innateSkill;
        public final String[] languageSkills;

        public SpeciesInnateDefinition()
        {
            this(-1, "", "", null);
        }

        public SpeciesInnateDefinition(int speciesId, String templateToken, String innateSkill, String[] languageSkills)
        {
            this.speciesId = speciesId;
            this.templateToken = templateToken;
            this.innateSkill = innateSkill;
            this.languageSkills = (languageSkills != null) ? languageSkills : new String[0];
        }
    }

    private void ensureDefinitionsLoaded() throws InterruptedException
    {
        if (definitionsLoaded)
        {
            return;
        }
        DEFINITIONS_BY_ID.clear();
        DEFINITIONS_BY_TOKEN.clear();
        int rows = dataTableGetNumRows(DATATABLE);
        for (int i = 0; i < rows; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE, i);
            if (row == null)
            {
                continue;
            }
            String token = row.getString("templateToken");
            if ((token == null) || (token.length() == 0))
            {
                continue;
            }
            token = token.toLowerCase();
            int speciesId = row.getInt("speciesId");
            String innateSkill = row.getString("speciesSkill");
            String languageData = row.getString("languageSkills");
            String[] languages = parseDelimitedList(languageData);
            SpeciesInnateDefinition definition = new SpeciesInnateDefinition(speciesId, token, innateSkill, languages);
            if (speciesId >= 0)
            {
                DEFINITIONS_BY_ID.put(speciesId, definition);
            }
            DEFINITIONS_BY_TOKEN.add(definition);
        }
        definitionsLoaded = true;
    }

    private String[] parseDelimitedList(String data)
    {
        if ((data == null) || (data.length() == 0))
        {
            return NO_LANGUAGES;
        }
        String[] rawTokens = split(data, ';');
        List<String> values = new ArrayList<>();
        for (String rawToken : rawTokens)
        {
            if (rawToken == null)
            {
                continue;
            }
            String trimmed = rawToken.trim();
            if (trimmed.length() > 0)
            {
                values.add(trimmed);
            }
        }
        return values.toArray(new String[values.size()]);
    }

    private SpeciesInnateDefinition findDefinitionFor(obj_id self) throws InterruptedException
    {
        ensureDefinitionsLoaded();
        int species = getSpecies(self);
        if (DEFINITIONS_BY_ID.containsKey(species))
        {
            return DEFINITIONS_BY_ID.get(species);
        }
        String template = getTemplateName(self);
        if ((template != null) && (template.length() > 0))
        {
            String lowered = template.toLowerCase();
            for (SpeciesInnateDefinition definition : DEFINITIONS_BY_TOKEN)
            {
                if ((definition.templateToken != null) && (definition.templateToken.length() > 0) && lowered.contains(definition.templateToken))
                {
                    return definition;
                }
            }
        }
        return null;
    }

    private void applyInnateDefinition(obj_id self, SpeciesInnateDefinition definition) throws InterruptedException
    {
        if (definition == null)
        {
            return;
        }
        if ((definition.innateSkill != null) && (definition.innateSkill.length() > 0) && !hasSkill(self, definition.innateSkill))
        {
            grantSkill(self, definition.innateSkill);
        }
        if (definition.languageSkills != null)
        {
            for (String language : definition.languageSkills)
            {
                if ((language == null) || (language.length() == 0))
                {
                    continue;
                }
                if (!hasSkill(self, language))
                {
                    grantSkill(self, language);
                }
            }
        }
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (hasObjVar(self, innate.VAR_INNATE_BASE))
        {
            obj_var_list ovl = getObjVarList(self, innate.VAR_INNATE_BASE);
            if (ovl != null)
            {
                int now = getGameTime();
                int maxStamp = now + (2 * innate.ONE_HOUR);
                int numItems = ovl.getNumItems();
                for (int i = 0; i < numItems; i++)
                {
                    obj_var ov = ovl.getObjVar(i);
                    if (ov.getIntData() > maxStamp)
                    {
                        setObjVar(self, innate.VAR_INNATE_BASE + "." + ov.getName(), now - innate.ONE_HOUR);
                    }
                }
            }
        }
        return SCRIPT_CONTINUE;
    }
    public int OnAttach(obj_id self) throws InterruptedException
    {
        SpeciesInnateDefinition definition = findDefinitionFor(self);
        if (definition == null)
        {
            detachScript(self, SCRIPT_ME);
            return SCRIPT_CONTINUE;
        }
        applyInnateDefinition(self, definition);
        return SCRIPT_CONTINUE;
    }
    public int cmdInnate(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        if ((params == null) || (params.equals("")))
        {
            String msg = "";
            String[] skillMods = getSkillStatModListingForPlayer(self);
            if ((skillMods != null) && (skillMods.length > 0))
            {
                for (String skillMod : skillMods) {
                    if (skillMod.startsWith("private_innate_")) {
                        String[] s = split(skillMod, '_');
                        msg += s[s.length - 1] + ", ";
                    }
                }
            }
            prose_package ppValidInnate = prose.getPackage(SID_VALID_INNATE_PARAMS);
            if ((msg == null) || (msg.equals("")))
            {
                prose.setTO(ppValidInnate, SID_NONE);
            }
            else 
            {
                if (msg.endsWith(", "))
                {
                    msg = msg.substring(0, msg.length() - 2);
                }
                prose.setTO(ppValidInnate, msg);
            }
            return SCRIPT_CONTINUE;
        }
        String cmd = innate.parseInnateCommand(params);
        if (cmd == null)
        {
            prose_package ppNotValid = prose.getPackage(SID_NOT_VALID_PARAM_INNATE);
            prose.setTO(ppNotValid, params);
            return SCRIPT_CONTINUE;
        }
        else 
        {
            int modval = getSkillStatMod(self, "private_innate_" + cmd);
            if (modval > 0)
            {
                switch (cmd) {
                    case innate.REGEN:
                        queueCommand(self, (1397846664), null, "", COMMAND_PRIORITY_DEFAULT);
                        return SCRIPT_CONTINUE;
                    case innate.ROAR:
                        queueCommand(self, (-1223315403), null, "", COMMAND_PRIORITY_DEFAULT);
                        return SCRIPT_CONTINUE;
                    case innate.EQUIL:
                        queueCommand(self, (136144656), null, "", COMMAND_PRIORITY_DEFAULT);
                        return SCRIPT_CONTINUE;
                    case innate.VIT:
                        queueCommand(self, (1431834648), null, "", COMMAND_PRIORITY_DEFAULT);
                        return SCRIPT_CONTINUE;
                }
            }
        }
        prose_package pp = prose.getPackage(innate.PROSE_INNATE_NA, cmd);
        return SCRIPT_CONTINUE;
    }
    public int cmdInnateFail(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        return SCRIPT_CONTINUE;
    }
    public int cmdRegeneration(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        int mod = getSkillStatMod(self, "private_innate_regeneration");
        if (mod == 1)
        {
            innate.regeneration();
        }
        return SCRIPT_CONTINUE;
    }
    public int cmdVitalize(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        int mod = getSkillStatMod(self, "private_innate_vitalize");
        if (mod == 1)
        {
            innate.vitalize();
        }
        return SCRIPT_CONTINUE;
    }
    public int cmdEquilibrium(obj_id self, obj_id target, String params, float defaultTime) throws InterruptedException
    {
        int mod = getSkillStatMod(self, "private_innate_equilibrium");
        if (mod == 1)
        {
            innate.equilibrium();
        }
        return SCRIPT_CONTINUE;
    }
}
