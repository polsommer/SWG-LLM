package script.library;

import script.dictionary;
import script.location;
import script.obj_id;
import script.string_id;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class tokenmanager extends script.base_script
{
    public tokenmanager()
    {
    }

    private static final String TOKEN_HOLDER_OBJVAR = "item.set.tokens_held";
    private static final String HEROIC_TOKEN_BOX_TEMPLATE = "item_heroic_token_box_01_01";

    public static final String LIFEDAY_IMPERIAL_TOKEN = "item_event_lifeday_imperial_token";
    public static final String LIFEDAY_REBEL_TOKEN = "item_event_lifeday_rebel_token";

    public static final String[] HEROIC_TOKENS =
            {
                    "item_heroic_token_axkva_01_01",
                    "item_heroic_token_tusken_01_01",
                    "item_heroic_token_ig88_01_01",
                    "item_heroic_token_black_sun_01_01",
                    "item_heroic_token_exar_01_01",
                    "item_heroic_token_echo_base_01_01",
                    "item_battlefield_rebel_token_massassi_isle",
                    "item_battlefield_imperial_token_massassi_isle",
                    "item_battlefield_rebel_token_battlefield2",
                    "item_battlefield_imperial_token_battlefield2",
                    "item_battlefield_rebel_token_battlefield3",
                    "item_battlefield_imperial_token_battlefield3",
                    "item_battlefield_rebel_token_battlefield4",
                    "item_battlefield_imperial_token_battlefield4",
                    "item_pgc_token_01",
                    "item_pgc_token_02",
                    "item_pgc_token_03",
                    "item_gcw_rebel_token",
                    "item_gcw_imperial_token",
                    "item_token_duty_space_01_01",
                    "item_imperial_station_token_01_01",
                    "item_rebel_station_token_01_01",
                    "theme_nightsister_relic",
                    "item_restuss_imperial_commendation_02_01",
                    "item_restuss_rebel_commendation_02_01"
            };

    private static final Map<String, Integer> TOKEN_INDEX_LOOKUP;

    static
    {
        Map<String, Integer> tokenIndex = new HashMap<String, Integer>();
        for (int i = 0; i < HEROIC_TOKENS.length; i++)
        {
            tokenIndex.put(HEROIC_TOKENS[i], i);
        }
        TOKEN_INDEX_LOOKUP = Collections.unmodifiableMap(tokenIndex);
    }

    public static final String SPACE_DUTY_TOKEN = "item_token_duty_space_01_01";

    public static String[] getTokenTypes(String tokenList)
    {
        if (tokenList != null && tokenList.length() > 0)
        {
            String[] parsed = split(tokenList, ',');
            for (int i = 0; i < parsed.length; i++)
            {
                parsed[i] = parsed[i].trim();
            }
            return parsed;
        }

        return HEROIC_TOKENS;
    }

    public static int[] getTokenCostsFromTable(String vendorTable, int row, int tokenTypeCount) throws InterruptedException
    {
        int[] tokenCost = new int[tokenTypeCount];
        for (int j = 0; j < tokenCost.length; j++)
        {
            String tokenColumn = "token" + j;
            if (dataTableHasColumn(vendorTable, tokenColumn))
            {
                tokenCost[j] = dataTableGetInt(vendorTable, row, tokenColumn);
            }
            else
            {
                tokenCost[j] = 0;
            }
        }

        return tokenCost;
    }

    public static String getTokenDisplayName(String tokenName) throws InterruptedException
    {
        if (tokenName == null || tokenName.length() == 0)
        {
            return "Unknown Token";
        }

        String tokenDisplay = getString(new string_id("static_item_n", tokenName));
        if (tokenDisplay == null || tokenDisplay.length() == 0)
        {
            tokenDisplay = tokenName;
        }

        return tokenDisplay;
    }

    public static void initializeBox(obj_id self) throws InterruptedException
    {
        int[] tokenTypes = new int[HEROIC_TOKENS.length];
        Arrays.fill(tokenTypes, 0);
        setObjVar(self, TOKEN_HOLDER_OBJVAR, tokenTypes);
    }

    public static void verifyBox(obj_id self) throws InterruptedException
    {
        int[] tokenTypes = getIntArrayObjVar(self, TOKEN_HOLDER_OBJVAR);
        if (tokenTypes == null)
        {
            initializeBox(self);
        }
        else if (tokenTypes.length < HEROIC_TOKENS.length)
        {
            int[] newTokenTypes = new int[HEROIC_TOKENS.length];
            System.arraycopy(tokenTypes, 0, newTokenTypes, 0, tokenTypes.length);
            Arrays.fill(newTokenTypes, tokenTypes.length, HEROIC_TOKENS.length, 0);
            setObjVar(self, TOKEN_HOLDER_OBJVAR, newTokenTypes);
        }
    }

    public static boolean purchaseTokenItem(obj_id player, int price, String tokenName) throws InterruptedException
    {
        if (!isIdValid(player) || !exists(player) || price < 0 || tokenName == null || tokenName.length() <= 0)
        {
            return false;
        }

        if (getTokenTotal(player, tokenName) < price)
        {
            return false;
        }

        obj_id[] inventoryContents = getInventoryAndEquipment(player);
        if (inventoryContents == null || inventoryContents.length <= 0)
        {
            return false;
        }

        int tokensOwed = price;
        int tokenIndex = getTokenIndex(tokenName);

        List<obj_id> tokenStacks = new ArrayList<obj_id>();
        List<Integer> tokenStackCounts = new ArrayList<Integer>();
        List<obj_id> tokenBoxes = new ArrayList<obj_id>();
        List<int[]> tokenBoxValues = new ArrayList<int[]>();
        boolean foundTokenHolderBox = false;

        for (obj_id inventoryContent : inventoryContents)
        {
            if (!isIdValid(inventoryContent) || !exists(inventoryContent))
            {
                continue;
            }

            if (tokensOwed <= 0)
            {
                break;
            }

            String itemName = getStaticItemName(inventoryContent);
            if (itemName == null || itemName.isEmpty())
            {
                continue;
            }

            if (itemName.equals(tokenName))
            {
                int count = getCount(inventoryContent);
                if (count > 0)
                {
                    int tokensToSpend = Math.min(count, tokensOwed);
                    tokensOwed -= tokensToSpend;
                    tokenStacks.add(inventoryContent);
                    tokenStackCounts.add(count - tokensToSpend);
                }
                continue;
            }

            if (tokenIndex > -1 && !foundTokenHolderBox && itemName.equals(HEROIC_TOKEN_BOX_TEMPLATE))
            {
                int[] virtualTokens = getTokenArray(inventoryContent);
                if (virtualTokens != null && tokenIndex < virtualTokens.length)
                {
                    int available = virtualTokens[tokenIndex];
                    if (available > 0)
                    {
                        int tokensToSpend = Math.min(available, tokensOwed);
                        tokensOwed -= tokensToSpend;

                        int[] newTokenValues = new int[virtualTokens.length];
                        System.arraycopy(virtualTokens, 0, newTokenValues, 0, virtualTokens.length);
                        newTokenValues[tokenIndex] = available - tokensToSpend;

                        tokenBoxes.add(inventoryContent);
                        tokenBoxValues.add(newTokenValues);
                        foundTokenHolderBox = true;
                    }
                }
            }
        }

        if (tokensOwed > 0)
        {
            return false;
        }

        for (int i = 0; i < tokenStacks.size(); i++)
        {
            obj_id tokenStack = tokenStacks.get(i);
            int remaining = tokenStackCounts.get(i);
            if (remaining > 0)
            {
                setCount(tokenStack, remaining);
            }
            else
            {
                destroyObject(tokenStack);
            }
        }

        for (int i = 0; i < tokenBoxes.size(); i++)
        {
            setObjVar(tokenBoxes.get(i), TOKEN_HOLDER_OBJVAR, tokenBoxValues.get(i));
        }

        return true;
    }

    public static int getSpaceDutyTokenPrice(int level) throws InterruptedException
    {
        return level * 5 + 50;
    }

    public static int getTokenTotal(obj_id player, String token) throws InterruptedException
    {
        int tokenCount = 0;
        int tokenIndex = getTokenIndex(token);
        if (!isIdValid(player) || !exists(player) || token == null || token.length() <= 0)
        {
            return 0;
        }

        obj_id[] inventoryContents = getInventoryAndEquipment(player);
        if (inventoryContents == null || inventoryContents.length <= 0)
        {
            return 0;
        }

        boolean foundTokenHolderBox = false;
        for (obj_id inventoryContent : inventoryContents) {
            if (!isIdValid(inventoryContent) || !exists(inventoryContent)) {
                continue;
            }

            String itemName = getStaticItemName(inventoryContent);
            if (itemName != null && !itemName.isEmpty()) {
                if (itemName.equals(token)) {
                    tokenCount += getCount(inventoryContent);
                }

                if (tokenIndex > -1 && !foundTokenHolderBox && itemName.equals(HEROIC_TOKEN_BOX_TEMPLATE)) {
                    int[] virtualTokenArray = getTokenArray(inventoryContent);
                    if (virtualTokenArray != null && tokenIndex < virtualTokenArray.length) {
                        tokenCount += virtualTokenArray[tokenIndex];
                        foundTokenHolderBox = true;
                    }
                }
            }
        }

        return tokenCount;
    }

    private static int getTokenIndex(String tokenName)
    {
        if (tokenName == null || tokenName.length() == 0)
        {
            return -1;
        }

        Integer index = TOKEN_INDEX_LOOKUP.get(tokenName);
        if (index == null)
        {
            return -1;
        }

        return index.intValue();
    }

    private static int[] getTokenArray(obj_id tokenBox) throws InterruptedException
    {
        if (!isIdValid(tokenBox) || !exists(tokenBox))
        {
            return null;
        }

        verifyBox(tokenBox);
        return getIntArrayObjVar(tokenBox, TOKEN_HOLDER_OBJVAR);
    }
}
