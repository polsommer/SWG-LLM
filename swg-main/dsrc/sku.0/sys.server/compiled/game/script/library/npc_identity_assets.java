package script.library;

import script.*;

import java.util.Vector;

public class npc_identity_assets extends script.base_script
{
    public npc_identity_assets()
    {
    }

    private static final String IDENTITY_ROOT = "npc.simProfile.identity";
    private static final String APPLIED_ROOT = IDENTITY_ROOT + ".applied";
    private static final String REAPPLY_PENDING = IDENTITY_ROOT + ".reapplyPending";
    private static final String GENERATED_ROOT = "appearance.generated";
    private static final String DEFAULT_SPECIES = "human";
    private static final String[] HUMAN_FIRST = new String[]{"Jalen", "Kira", "Dax", "Mira", "Torin", "Lyra", "Rhett", "Nia"};
    private static final String[] HUMAN_LAST = new String[]{"Voss", "Renn", "Kade", "Vale", "Sorn", "Dane", "Tallis", "Korr"};
    private static final String[] BOTHAN_FIRST = new String[]{"Borsk", "Feyan", "Trassi", "Neyra", "Kelis", "Voran"};
    private static final String[] RODIAN_FIRST = new String[]{"Greelo", "Nazz", "Rokko", "Vekka", "Jindo", "Tarr"};
    private static final String[] TWILEK_FIRST = new String[]{"Lysa", "Numa", "Rylia", "Vesha", "Kavri", "Talis"};
    private static final String[] ZABRAK_FIRST = new String[]{"Karn", "Vexa", "Rikto", "Nyr", "Drex", "Sava"};
    private static final String[] TRANDOSHAN_FIRST = new String[]{"Sskarr", "Trassk", "Vriss", "Kraass", "Trrik", "Ssar"};
    private static final String[] WEARABLE_SLOT_HINTS = new String[]{"chest2", "jacket", "shirt", "pants1", "pants2", "legs", "shoes", "boots", "feet", "hat", "hair", "gloves", "vest", "belt", "back"};

    public static void applyIdentityAssets(obj_id npc) throws InterruptedException
    {
        applyIdentityAssets(npc, false);
    }

    public static void reapplyIdentityAssets(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        setObjVar(npc, REAPPLY_PENDING, 1);
        applyIdentityAssets(npc, true);
    }

    public static void applyIdentityAssets(obj_id npc, boolean forceReapply) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }

        if (!forceReapply && getIntObjVar(npc, APPLIED_ROOT + ".appearanceApplied") == 1 && getIntObjVar(npc, REAPPLY_PENDING) != 1)
        {
            return;
        }

        if (!forceReapply && hasObjVar(npc, GENERATED_ROOT + ".profile"))
        {
            setObjVar(npc, APPLIED_ROOT + ".blockedByGeneratedAppearance", 1);
            return;
        }

        String species = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".species"));
        String palette = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".palette"));
        String clothingSet = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".clothingSet"));
        String hair = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".hair"));
        String skin = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".skin"));
        String eyes = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".eyes"));
        String lips = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".lips"));

        boolean fallbackUsed = false;
        if (!isKnownSpecies(species))
        {
            species = DEFAULT_SPECIES;
            fallbackUsed = true;
        }
        if (palette.length() < 1)
        {
            palette = "urban";
            fallbackUsed = true;
        }
        if (!isKnownClothingSet(clothingSet))
        {
            clothingSet = getFallbackClothingSet(species);
            fallbackUsed = true;
        }

        String templateVariant = resolveTemplateVariant(species, clothingSet);
        String hairResolved = resolveHairCustomization(species, hair);
        String skinResolved = resolveSkinCustomization(species, skin);
        String eyesResolved = resolveEyeCustomization(species, eyes);
        String lipsResolved = resolveLipCustomization(species, lips);
        String paletteResolved = resolvePaletteCustomization(species, palette);
        String[] wearables = resolveWearables(species, clothingSet, palette);

        setObjVar(npc, APPLIED_ROOT + ".species", species);
        setObjVar(npc, APPLIED_ROOT + ".templateVariant", templateVariant);
        setObjVar(npc, APPLIED_ROOT + ".appearanceCustomization.hair", hairResolved);
        setObjVar(npc, APPLIED_ROOT + ".appearanceCustomization.skin", skinResolved);
        setObjVar(npc, APPLIED_ROOT + ".appearanceCustomization.eyes", eyesResolved);
        setObjVar(npc, APPLIED_ROOT + ".appearanceCustomization.lips", lipsResolved);
        setObjVar(npc, APPLIED_ROOT + ".appearanceCustomization.palette", paletteResolved);
        setObjVar(npc, APPLIED_ROOT + ".wearables", wearables);

        applyTemplateValidation(npc, templateVariant);
        applyVisualCustomizationChannels(npc, hairResolved, skinResolved, eyesResolved, lipsResolved, paletteResolved);
        equipWearablesWithConflictHandling(npc, wearables);

        ensurePlayerLikeDisplayName(npc, species);
        setObjVar(npc, APPLIED_ROOT + ".fallbackUsed", fallbackUsed ? 1 : 0);
        setObjVar(npc, APPLIED_ROOT + ".appearanceApplied", 1);
        removeObjVar(npc, APPLIED_ROOT + ".blockedByGeneratedAppearance");
        removeObjVar(npc, REAPPLY_PENDING);
    }

    private static void applyTemplateValidation(obj_id npc, String templateVariant) throws InterruptedException
    {
        if (templateVariant == null || templateVariant.length() < 1)
        {
            traceAppearanceFailure(npc, "invalid template variant: empty");
            return;
        }
        if (getObjectTemplateCrc(templateVariant) == 0)
        {
            traceAppearanceFailure(npc, "invalid template variant: " + templateVariant);
        }
    }

    private static void applyVisualCustomizationChannels(obj_id npc, String hair, String skin, String eyes, String lips, String palette) throws InterruptedException
    {
        applyCustomizationChannel(npc, "hair", hair, "/shared_owner/index_style_1");
        applyCustomizationChannel(npc, "skin", skin, "/shared_owner/index_skin");
        applyCustomizationChannel(npc, "eyes", eyes, "/shared_owner/index_color_1");
        applyCustomizationChannel(npc, "lips", lips, "/shared_owner/index_color_2");
        applyCustomizationChannel(npc, "palette", palette, "/private/index_color_1");
    }

    private static void applyCustomizationChannel(obj_id npc, String channelName, String value, String defaultVarPath) throws InterruptedException
    {
        if (value == null || value.length() < 1)
        {
            return;
        }

        String varPath = defaultVarPath;
        int numericValue = parseCustomizationValue(value);
        int separator = value.indexOf(':');
        if (separator > 0)
        {
            varPath = value.substring(0, separator);
            numericValue = parseCustomizationValue(value.substring(separator + 1));
        }

        if (numericValue < 0)
        {
            setObjVar(npc, APPLIED_ROOT + ".appearanceCustomizationRaw." + channelName, value);
            return;
        }

        setRangedIntCustomVarValue(npc, varPath, numericValue);
        setObjVar(npc, APPLIED_ROOT + ".appearanceCustomizationApplied." + channelName, varPath + ":" + numericValue);
    }

    private static int parseCustomizationValue(String value) throws InterruptedException
    {
        if (value == null)
        {
            return -1;
        }
        value = value.trim();
        if (value.length() < 1)
        {
            return -1;
        }
        if (!isNumeric(value))
        {
            return -1;
        }
        return utils.stringToInt(value);
    }

    private static boolean isNumeric(String value) throws InterruptedException
    {
        if (value == null || value.length() < 1)
        {
            return false;
        }
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (i == 0 && c == '-')
            {
                continue;
            }
            if (c < '0' || c > '9')
            {
                return false;
            }
        }
        return true;
    }

    private static void equipWearablesWithConflictHandling(obj_id npc, String[] wearables) throws InterruptedException
    {
        if (wearables == null || wearables.length < 1)
        {
            return;
        }
        for (String wearable : wearables) {
            if (wearable == null || wearable.length() < 1) {
                continue;
            }
            obj_id piece = createObject(wearable, npc, "");
            if (!isIdValid(piece))
            {
                traceAppearanceFailure(npc, "missing wearable asset: " + wearable);
                continue;
            }

            String[] candidateSlots = getCandidateSlotsForTemplate(wearable);
            boolean equipped = false;
            if (candidateSlots != null)
            {
                for (String slot : candidateSlots) {
                    if (canPutInSlot(piece, npc, slot) != CEC_SUCCESS) {
                        continue;
                    }
                    obj_id occupied = getObjectInSlot(npc, slot);
                    if (isIdValid(occupied) && occupied != piece)
                    {
                        traceAppearanceFailure(npc, "slot collision on " + slot + " for " + wearable + ", replacing " + getTemplateName(occupied));
                        destroyObject(occupied);
                    }
                    if (equip(piece, npc, slot))
                    {
                        equipped = true;
                        break;
                    }
                }
            }

            if (!equipped)
            {
                equipped = equip(piece, npc);
            }
            if (!equipped)
            {
                traceAppearanceFailure(npc, "unable to equip wearable: " + wearable);
                destroyObject(piece);
            }
        }
    }

    private static String[] getCandidateSlotsForTemplate(String wearableTemplate) throws InterruptedException
    {
        String template = toLower(wearableTemplate);
        Vector out = new Vector();
        for (String slot : WEARABLE_SLOT_HINTS) {
            if (template.indexOf("/" + slot + "/") >= 0 || template.indexOf("_" + slot + "_") >= 0 || template.indexOf("_" + slot + ".") >= 0)
            {
                out = utils.addElement(out, slot);
            }
        }
        if (out.size() < 1)
        {
            return null;
        }
        String[] slots = new String[out.size()];
        out.toArray(slots);
        return slots;
    }

    private static void traceAppearanceFailure(obj_id npc, String message) throws InterruptedException
    {
        String detail = "npc_identity_assets: npc=" + npc + " " + message;
        LOG("npc_identity_assets", detail);
        CustomerServiceLog("npc_identity_assets", detail);
    }

    private static void ensurePlayerLikeDisplayName(obj_id npc, String species) throws InterruptedException
    {
        if (!isIdValid(npc) || getIntObjVar(npc, APPLIED_ROOT + ".nameApplied") == 1)
        {
            return;
        }

        String namingStyle = normalizeToken(getStringObjVar(npc, IDENTITY_ROOT + ".namingStyle"));
        int signature = Math.abs(getIntObjVar(npc, IDENTITY_ROOT + ".lookSignatureHash"));
        if (signature < 1)
        {
            signature = Math.abs((npc + "").hashCode());
            setObjVar(npc, IDENTITY_ROOT + ".lookSignatureHash", signature);
        }

        String generated = generatePlayerLikeName(species, namingStyle, signature);
        if (generated.length() > 1)
        {
            setName(npc, generated);
            setObjVar(npc, APPLIED_ROOT + ".nameApplied", 1);
            setObjVar(npc, APPLIED_ROOT + ".displayName", generated);
        }
    }

    private static String generatePlayerLikeName(String species, String namingStyle, int seed) throws InterruptedException
    {
        if ("surname_compound".equals(namingStyle) || "human".equals(species))
        {
            String first = pickBySeed(HUMAN_FIRST, seed);
            String last = pickBySeed(HUMAN_LAST, seed / 3 + 7);
            return first + " " + last;
        }
        if ("clan_short".equals(namingStyle) || "bothan".equals(species))
        {
            return pickBySeed(BOTHAN_FIRST, seed) + " " + pickBySeed(HUMAN_LAST, seed / 5 + 11);
        }
        if ("rodian_click".equals(namingStyle) || "rodian".equals(species))
        {
            return pickBySeed(RODIAN_FIRST, seed) + " " + pickBySeed(HUMAN_LAST, seed / 4 + 9);
        }
        if ("twilek_fluid".equals(namingStyle) || "twilek".equals(species))
        {
            return pickBySeed(TWILEK_FIRST, seed) + " " + pickBySeed(HUMAN_LAST, seed / 6 + 5);
        }
        if ("zabrak_sharp".equals(namingStyle) || "zabrak".equals(species))
        {
            return pickBySeed(ZABRAK_FIRST, seed) + " " + pickBySeed(HUMAN_LAST, seed / 8 + 3);
        }
        if ("trandoshan_hiss".equals(namingStyle) || "trandoshan".equals(species))
        {
            return pickBySeed(TRANDOSHAN_FIRST, seed);
        }
        return pickBySeed(HUMAN_FIRST, seed) + " " + pickBySeed(HUMAN_LAST, seed / 2 + 13);
    }

    private static String pickBySeed(String[] source, int seed) throws InterruptedException
    {
        if (source == null || source.length < 1)
        {
            return "Citizen";
        }
        return source[Math.abs(seed) % source.length];
    }

    private static String normalizeToken(String token) throws InterruptedException
    {
        if (token == null)
        {
            return "";
        }
        return toLower(token.trim());
    }

    private static boolean isKnownSpecies(String species) throws InterruptedException
    {
        return "human".equals(species) || "twilek".equals(species) || "rodian".equals(species) || "zabrak".equals(species) || "bothan".equals(species) || "trandoshan".equals(species);
    }

    private static boolean isKnownClothingSet(String clothingSet) throws InterruptedException
    {
        return "patrol_armor_mix".equals(clothingSet) || "mercenary_layered".equals(clothingSet) || "frontline_light".equals(clothingSet) || "artisan_utility".equals(clothingSet) || "merchant_formal".equals(clothingSet) || "terminal_runner".equals(clothingSet) || "cantina_casual".equals(clothingSet) || "traveler_wrap".equals(clothingSet) || "city_citizen".equals(clothingSet);
    }

    private static String getFallbackClothingSet(String species) throws InterruptedException
    {
        if ("trandoshan".equals(species))
        {
            return "frontline_light";
        }
        if ("bothan".equals(species))
        {
            return "merchant_formal";
        }
        return "city_citizen";
    }

    private static String resolveTemplateVariant(String species, String clothingSet) throws InterruptedException
    {
        if ("twilek".equals(species))
        {
            return "object/mobile/shared_dressed_commoner_naboo_twilek_female_01.iff";
        }
        if ("rodian".equals(species))
        {
            return "object/mobile/shared_dressed_commoner_tatooine_rodian_male_01.iff";
        }
        if ("zabrak".equals(species))
        {
            return "object/mobile/shared_dressed_commoner_naboo_zabrak_female_01.iff";
        }
        if ("bothan".equals(species))
        {
            return "object/mobile/shared_dressed_commoner_naboo_bothan_male_01.iff";
        }
        if ("trandoshan".equals(species))
        {
            return "object/mobile/shared_dressed_commoner_tatooine_trandoshan_male_01.iff";
        }
        if ("patrol_armor_mix".equals(clothingSet) || "frontline_light".equals(clothingSet))
        {
            return "object/mobile/shared_dressed_commoner_naboo_human_male_02.iff";
        }
        return "object/mobile/shared_dressed_commoner_naboo_human_male_01.iff";
    }

    private static String resolveHairCustomization(String species, String hair) throws InterruptedException
    {
        if ("twilek".equals(species))
        {
            return hair.startsWith("crest") ? hair : "crest_smooth";
        }
        return hair.length() > 0 ? hair : "short";
    }

    private static String resolveSkinCustomization(String species, String skin) throws InterruptedException
    {
        if (skin.length() > 0)
        {
            return skin;
        }
        if ("trandoshan".equals(species))
        {
            return "olive_scale";
        }
        return "tan";
    }

    private static String resolveEyeCustomization(String species, String eyes) throws InterruptedException
    {
        if (eyes.length() > 0)
        {
            return eyes;
        }
        if ("trandoshan".equals(species))
        {
            return "gold";
        }
        return "brown";
    }

    private static String resolveLipCustomization(String species, String lips) throws InterruptedException
    {
        if (lips.length() > 0)
        {
            return lips;
        }
        if ("trandoshan".equals(species))
        {
            return "dark_umber";
        }
        return "neutral";
    }

    private static String resolvePaletteCustomization(String species, String palette) throws InterruptedException
    {
        if (palette.length() > 0)
        {
            return palette;
        }
        if ("twilek".equals(species))
        {
            return "desert_pastel";
        }
        return "urban";
    }

    private static String[] resolveWearables(String species, String clothingSet, String palette) throws InterruptedException
    {
        if ("patrol_armor_mix".equals(clothingSet) || "frontline_light".equals(clothingSet))
        {
            return new String[]{
                "object/tangible/wearables/armor/padded/armor_padded_s01_chest_plate.iff",
                "object/tangible/wearables/armor/padded/armor_padded_s01_leggings.iff",
                "object/tangible/wearables/armor/padded/armor_padded_s01_boots.iff"
            };
        }
        if ("artisan_utility".equals(clothingSet) || "terminal_runner".equals(clothingSet))
        {
            return new String[]{
                "object/tangible/wearables/shirt/shirt_s07.iff",
                "object/tangible/wearables/pants/pants_s12.iff",
                "object/tangible/wearables/boots/boots_s12.iff"
            };
        }
        if ("merchant_formal".equals(clothingSet))
        {
            return new String[]{
                "object/tangible/wearables/vest/vest_s05.iff",
                "object/tangible/wearables/shirt/shirt_s10.iff",
                "object/tangible/wearables/pants/pants_s08.iff"
            };
        }
        if ("twilek".equals(species) || "city_teal".equals(palette))
        {
            return new String[]{
                "object/tangible/wearables/shirt/shirt_s26.iff",
                "object/tangible/wearables/pants/pants_s21.iff",
                "object/tangible/wearables/shoes/shoes_s03.iff"
            };
        }
        return new String[]{
            "object/tangible/wearables/shirt/shirt_s07.iff",
            "object/tangible/wearables/pants/pants_s05.iff",
            "object/tangible/wearables/boots/boots_s22.iff"
        };
    }
}
