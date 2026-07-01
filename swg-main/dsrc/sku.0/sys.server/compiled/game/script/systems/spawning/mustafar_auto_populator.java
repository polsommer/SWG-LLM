package script.systems.spawning;

import script.dictionary;
import script.library.utils;
import script.location;
import script.obj_id;

public class mustafar_auto_populator extends script.base_script
{
    public mustafar_auto_populator()
    {
    }

    public static final String PLANET = "mustafar";
    public static final String SPAWNER_TEMPLATE = "object/tangible/ground_spawning/area_spawner.iff";
    public static final String AUTO_OBJVAR = "mustafar.autoPopulation.v2";
    public static final String AUTO_VERSION = "mustafar_auto_population_v2";

    public static final String TYPE_CRYSTAL = "mustafar/auto_crystal_flats";
    public static final String TYPE_MINING = "mustafar/auto_mining_area";
    public static final String TYPE_NESTING = "mustafar/auto_nesting_grounds";
    public static final String TYPE_SMOKING = "mustafar/auto_smoking_forest";
    public static final String TYPE_BURNING = "mustafar/auto_burning_plains";
    public static final String TYPE_BERKENS = "mustafar/auto_berkens_flow";
    public static final String TYPE_MENSIX = "mustafar/auto_mensix_mining";
    public static final String TYPE_MIXED = "mustafar/auto_wild_mixed";

    public static final float[][] SPAWNER_POINTS =
    {
        {-6050.0f, 22.0f, 1820.0f, 260.0f, 9.0f, 300.0f, 600.0f, 0.0f},
        {-5425.0f, 90.0f, 510.0f, 280.0f, 10.0f, 300.0f, 600.0f, 0.0f},
        {-4860.0f, 85.0f, 2100.0f, 260.0f, 9.0f, 300.0f, 600.0f, 0.0f},
        {-4020.0f, 105.0f, 2380.0f, 260.0f, 9.0f, 300.0f, 600.0f, 0.0f},

        {-3380.0f, 80.0f, 520.0f, 240.0f, 9.0f, 240.0f, 480.0f, 1.0f},
        {-2860.0f, 120.0f, 70.0f, 250.0f, 10.0f, 240.0f, 480.0f, 1.0f},
        {-2335.0f, 140.0f, 1015.0f, 280.0f, 11.0f, 240.0f, 480.0f, 1.0f},
        {-1825.0f, 95.0f, 1810.0f, 260.0f, 10.0f, 240.0f, 480.0f, 1.0f},
        {-1320.0f, 75.0f, 160.0f, 260.0f, 10.0f, 240.0f, 480.0f, 1.0f},
        {-520.0f, 80.0f, 170.0f, 250.0f, 9.0f, 240.0f, 480.0f, 1.0f},

        {-2669.0f, 230.0f, 1730.0f, 110.0f, 16.0f, 120.0f, 300.0f, 6.0f},
        {-2470.0f, 230.0f, 1622.0f, 130.0f, 14.0f, 120.0f, 300.0f, 6.0f},
        {-2850.0f, 210.0f, 1580.0f, 145.0f, 14.0f, 120.0f, 300.0f, 6.0f},
        {-2300.0f, 180.0f, 1900.0f, 145.0f, 13.0f, 120.0f, 300.0f, 6.0f},
        {-2660.0f, 200.0f, 2050.0f, 160.0f, 13.0f, 180.0f, 360.0f, 6.0f},

        {-2030.0f, 80.0f, 4200.0f, 230.0f, 9.0f, 300.0f, 600.0f, 2.0f},
        {-1810.0f, 90.0f, 3300.0f, 260.0f, 10.0f, 300.0f, 600.0f, 2.0f},
        {-1490.0f, 80.0f, 2500.0f, 240.0f, 9.0f, 300.0f, 600.0f, 2.0f},
        {-1260.0f, 85.0f, 3500.0f, 250.0f, 10.0f, 300.0f, 600.0f, 2.0f},
        {-1110.0f, 75.0f, 2860.0f, 220.0f, 8.0f, 300.0f, 600.0f, 2.0f},

        {-6000.0f, 160.0f, 5800.0f, 280.0f, 10.0f, 300.0f, 600.0f, 3.0f},
        {-5550.0f, 170.0f, 4650.0f, 300.0f, 12.0f, 300.0f, 600.0f, 3.0f},
        {-5200.0f, 145.0f, 3350.0f, 260.0f, 10.0f, 300.0f, 600.0f, 3.0f},
        {-4725.0f, 130.0f, 4625.0f, 260.0f, 10.0f, 300.0f, 600.0f, 3.0f},
        {-4400.0f, 120.0f, 5450.0f, 230.0f, 9.0f, 300.0f, 600.0f, 3.0f},

        {-4550.0f, 120.0f, 5925.0f, 260.0f, 10.0f, 300.0f, 600.0f, 4.0f},
        {-3850.0f, 115.0f, 5100.0f, 280.0f, 11.0f, 300.0f, 600.0f, 4.0f},
        {-3180.0f, 105.0f, 5700.0f, 280.0f, 11.0f, 300.0f, 600.0f, 4.0f},
        {-2450.0f, 95.0f, 5325.0f, 250.0f, 10.0f, 300.0f, 600.0f, 4.0f},

        {-1600.0f, 80.0f, 5750.0f, 300.0f, 12.0f, 300.0f, 600.0f, 5.0f},
        {-950.0f, 70.0f, 4850.0f, 290.0f, 11.0f, 300.0f, 600.0f, 5.0f},
        {-520.0f, 75.0f, 3900.0f, 280.0f, 11.0f, 300.0f, 600.0f, 5.0f},
        {-400.0f, 75.0f, 2600.0f, 250.0f, 10.0f, 300.0f, 600.0f, 5.0f},
        {220.0f, 80.0f, 2140.0f, 260.0f, 10.0f, 300.0f, 600.0f, 5.0f},

        {-3600.0f, 100.0f, 3600.0f, 280.0f, 10.0f, 360.0f, 750.0f, 7.0f},
        {-2500.0f, 95.0f, 3000.0f, 280.0f, 10.0f, 360.0f, 750.0f, 7.0f},
        {-900.0f, 80.0f, 3400.0f, 260.0f, 9.0f, 360.0f, 750.0f, 7.0f},
        {-5200.0f, 120.0f, 2700.0f, 260.0f, 9.0f, 360.0f, 750.0f, 7.0f}
    };

    public static final float[][] CLUSTER_OFFSETS =
    {
        {0.0f, 0.0f, 1.0f},
        {115.0f, 85.0f, 0.85f},
        {-115.0f, -85.0f, 0.85f}
    };

    public static final float[][] MENSIX_CLUSTER_OFFSETS =
    {
        {0.0f, 0.0f, 1.0f},
        {65.0f, 40.0f, 0.9f},
        {-65.0f, -45.0f, 0.9f},
        {35.0f, -70.0f, 0.85f}
    };

    public static final String[] SPAWNER_TYPES =
    {
        TYPE_CRYSTAL,
        TYPE_MINING,
        TYPE_NESTING,
        TYPE_SMOKING,
        TYPE_BURNING,
        TYPE_BERKENS,
        TYPE_MENSIX,
        TYPE_MIXED
    };

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if (!getCurrentSceneName().equals(PLANET))
        {
            return SCRIPT_CONTINUE;
        }
        messageTo(self, "populateMustafar", null, 5.0f, false);
        return SCRIPT_CONTINUE;
    }

    public int populateMustafar(obj_id self, dictionary params) throws InterruptedException
    {
        if (utils.hasScriptVar(self, AUTO_VERSION))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id[] existing = getAllObjectsWithObjVar(getLocation(self), 9000.0f, AUTO_OBJVAR);
        if (existing != null && existing.length > 0)
        {
            utils.setScriptVar(self, AUTO_VERSION, 1);
            return SCRIPT_CONTINUE;
        }
        for (int i = 0; i < SPAWNER_POINTS.length; i++)
        {
            float[] data = SPAWNER_POINTS[i];
            float[][] offsets = ((int)data[7] == 6) ? MENSIX_CLUSTER_OFFSETS : CLUSTER_OFFSETS;
            for (int j = 0; j < offsets.length; j++)
            {
                createAutoSpawner(i, j, offsets[j]);
            }
        }
        utils.setScriptVar(self, AUTO_VERSION, 1);
        return SCRIPT_CONTINUE;
    }

    public void createAutoSpawner(int index, int clusterIndex, float[] offset) throws InterruptedException
    {
        float[] data = SPAWNER_POINTS[index];
        String spawnType = SPAWNER_TYPES[(int)data[7]];
        float x = data[0] + offset[0];
        float y = data[1];
        float z = data[2] + offset[1];
        location spawnLoc = new location(x, y, z, PLANET, null);
        spawnLoc.y = getHeightAtLocation(spawnLoc.x, spawnLoc.z);
        obj_id spawner = createObject(SPAWNER_TEMPLATE, spawnLoc);
        if (!isIdValid(spawner))
        {
            return;
        }
        setObjVar(spawner, AUTO_OBJVAR, 1);
        setObjVar(spawner, "registerWithController", 1);
        setObjVar(spawner, "intSpawnSystem", 1);
        setObjVar(spawner, "intGoodLocationSpawner", 1);
        setObjVar(spawner, "intDefaultBehavior", 0);
        setObjVar(spawner, "intSpawnCount", getSpawnCount(data, clusterIndex));
        setObjVar(spawner, "fltRadius", getSpawnRadius(data, offset));
        setObjVar(spawner, "fltMinSpawnTime", getMinSpawnTime(data));
        setObjVar(spawner, "fltMaxSpawnTime", getMaxSpawnTime(data));
        setObjVar(spawner, "strSpawnerType", "area");
        setObjVar(spawner, "strSpawns", spawnType);
        setObjVar(spawner, "strName", "mustafar_auto_populator_v2_" + index + "_" + clusterIndex);
        attachScript(spawner, "systems.spawning.spawner_area");
    }

    public int getSpawnCount(float[] data, int clusterIndex) throws InterruptedException
    {
        int baseCount = (int)data[4];
        if ((int)data[7] == 6)
        {
            return baseCount + 6;
        }
        if (clusterIndex == 0)
        {
            return baseCount + 3;
        }
        return Math.max(6, baseCount - 1);
    }

    public float getSpawnRadius(float[] data, float[] offset) throws InterruptedException
    {
        float radius = data[3] * offset[2];
        if ((int)data[7] == 6)
        {
            return Math.min(radius, 95.0f);
        }
        return Math.min(radius, 175.0f);
    }

    public float getMinSpawnTime(float[] data) throws InterruptedException
    {
        if ((int)data[7] == 6)
        {
            return 75.0f;
        }
        return Math.min(data[5], 150.0f);
    }

    public float getMaxSpawnTime(float[] data) throws InterruptedException
    {
        if ((int)data[7] == 6)
        {
            return 180.0f;
        }
        return Math.min(data[6], 300.0f);
    }
}
