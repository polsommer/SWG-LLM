package script.event;

import java.util.Vector;

import script.dictionary;
import script.library.ai_lib;
import script.library.create;
import script.library.firework;
import script.library.locations;
import script.library.utils;
import script.library.chat;
import script.location;
import script.obj_id;

public class live_launch_celebration extends script.base_script
{
    public live_launch_celebration()
    {
    }

    private static final String CONFIG_SECTION = "GameServer";
    private static final String CONFIG_FLAG = "live_launch_event";
    private static final float FIREWORK_INTERVAL = 25.0f;
    private static final float MUSIC_INTERVAL = 160.0f;
    private static final float MUSIC_RANGE = 120.0f;
    private static final float AMBIENT_INTERVAL = 30.0f;
    private static final String MUSIC_TRACK = "sound/music_ceremony_1.snd";
    private static final String VAR_ACTIVE = "liveLaunch.active";
    private static final String VAR_PLAN_INDEX = "liveLaunch.planIndex";
    private static final String VAR_SPAWNS = "liveLaunch.spawns";
    private static final String VAR_NPCS = "liveLaunch.npcs";
    private static final String OBJVAR_PLAN_ID = "liveLaunch.planId";

    private static final String[] AMBIENT_ANIMATIONS =
    {
        "celebrate",
        "cheer",
        "dance_celebration",
        "clap",
        "wave1"
    };

    private static final float NPC_CHAT_DELAY_MIN = 2.5f;
    private static final float NPC_CHAT_DELAY_MAX = 7.0f;
    private static final float FUN_EVENT_INTERVAL_MIN = 14.0f;
    private static final float FUN_EVENT_INTERVAL_MAX = 36.0f;
    private static final float[][] GRAND_FIREWORK_PATTERN = new float[][]
    {
        {0.0f, 0.0f},
        {20.0f, 16.0f},
        {-22.0f, 14.0f},
        {14.0f, -20.0f},
        {-16.0f, -18.0f},
        {28.0f, 6.0f},
        {-30.0f, 8.0f},
        {8.0f, 28.0f},
        {-8.0f, 32.0f},
        {36.0f, 34.0f},
        {-38.0f, 36.0f},
        {52.0f, -12.0f},
        {-54.0f, -16.0f},
        {0.0f, -40.0f},
        {18.0f, 50.0f}
    };
    private static final String[] FUN_EFFECTS =
    {
        "appearance/pt_fireworks_complete_s04_ring.prt",
        "appearance/pt_fireworks_trailing_sparks_02.prt",
        "clienteffect/droid_effect_confetti.cef"
    };
    private static final String[] FUN_EVENT_BROADCASTS =
    {
        "%s festival volunteers toss glowsticks into the crowd!",
        "A surprise squadron streaks over %s in tight formation!",
        "Street performers ignite the party spirit across %s!",
        "Vendors across %s cheer as the celebration keeps rolling!"
    };

    private static final NpcPlacement[] GENERAL_FESTIVAL_STAFF = new NpcPlacement[]
    {
        new NpcPlacement("object/mobile/dressed_entertainer_trainer_human_female_01.iff", 2.5f, -8.0f, 35.0f, "%s Launch Entertainer", chat.MOOD_JOYFUL),
        new NpcPlacement("object/mobile/dressed_entertainer_trainer_twk_female_01.iff", -4.5f, -6.2f, -140.0f, "Festival Twi'lek Dancer", chat.MOOD_EXUBERANT),
        new NpcPlacement("object/mobile/dressed_entertainer_trainer_twk_male_01.iff", 5.0f, -3.5f, 80.0f, "Galactic Bassist", chat.MOOD_ENTHUSIASTIC),
        new NpcPlacement("object/mobile/dressed_entertainer_gcw_hum_f_02.iff", -6.0f, -1.5f, 25.0f, "%s Parade Conductor", chat.MOOD_CONFIDENT),
        new NpcPlacement("object/mobile/dressed_story_loot_theed.iff", 7.2f, 2.8f, 135.0f, "Festival Storyteller", chat.MOOD_JOYFUL),
        new NpcPlacement("object/mobile/dressed_biologist_01.iff", -5.5f, 3.8f, -120.0f, "Festival Naturalist", chat.MOOD_HOPEFUL),
        new NpcPlacement("object/mobile/r2d2.iff", 3.0f, 1.2f, -90.0f, "Celebration Droid R2", chat.MOOD_HAPPY),
        new NpcPlacement("object/mobile/3po_protocol_droid.iff", -1.8f, 2.4f, 140.0f, "Ceremony Protocol Droid", chat.MOOD_JOYFUL),
        new NpcPlacement("object/mobile/dressed_entertainer_trainer_human_female_01.iff", 1.8f, 6.0f, -10.0f, "%s Festival Soloist", chat.MOOD_JOYFUL),
        new NpcPlacement("object/mobile/dressed_entertainer_trainer_twk_female_01.iff", -2.5f, 7.2f, -40.0f, "Festival Choreographer", chat.MOOD_EXUBERANT),
        new NpcPlacement("object/mobile/dressed_entertainer_gcw_hum_f_02.iff", 0.0f, -10.5f, 180.0f, "Parade Coordinator", chat.MOOD_PROUD),
        new NpcPlacement("object/mobile/dressed_story_loot_theed.iff", 8.5f, -2.0f, 95.0f, "%s Lore Keeper", chat.MOOD_JOYFUL),
        new NpcPlacement("object/mobile/dressed_entertainer_gcw_f_zab_01.iff", -9.5f, 10.0f, -130.0f, "%s Festival Acrobat", chat.MOOD_ECSTATIC),
        new NpcPlacement("object/mobile/dressed_entertainer_gcw_hum_f_imp_01.iff", 11.0f, -13.5f, 55.0f, "Festival Brass Leader", chat.MOOD_CHEERFUL),
        new NpcPlacement("object/mobile/npc_dressed_entertainer_gcw_f_twk.iff", -14.0f, 14.5f, 15.0f, "%s Glowstaff Dancer", chat.MOOD_EXUBERANT),
        new NpcPlacement("object/mobile/npc_dressed_entertainer_ith_m_01_reb.iff", 15.0f, 8.5f, -40.0f, "%s Drum Master", chat.MOOD_PLAYFUL),
        new NpcPlacement("object/mobile/pit_droid.iff", 13.0f, -6.5f, -115.0f, "Celebration Pit Droid", chat.MOOD_CURIOUS),
        new NpcPlacement("object/mobile/r5.iff", -13.5f, 9.0f, 110.0f, "%s Firework Loader", chat.MOOD_FRIENDLY),
        new NpcPlacement("object/mobile/dressed_entertainer_trainergcw_f_twk_reb.iff", 5.5f, 15.0f, 85.0f, "%s Laser Harpist", chat.MOOD_ENTHUSIASTIC)
    };

    private static final float[][] LOCAL_NPC_POSITIONS = new float[][]
    {
        {-8.5f, -3.0f, 45.0f},
        {-7.2f, 4.0f, 90.0f},
        {7.8f, 4.5f, -70.0f},
        {9.2f, -4.0f, -110.0f},
        {0.0f, 9.5f, 10.0f},
        {-2.0f, -12.0f, 165.0f},
        {11.5f, 11.0f, -90.0f},
        {-12.5f, 10.5f, 130.0f},
        {4.5f, 14.0f, -65.0f},
        {-5.0f, 13.5f, 60.0f}
    };

    private static final PropPlacement[] BASE_PROP_LAYOUT = new PropPlacement[]
    {
        new PropPlacement("object/static/space/ship/nebulon_frigate.iff", 120.0f, 254.0f, -120.0f, 25.0f),
        new PropPlacement("object/static/space/ship/cargo_freighter.iff", 70.0f, 244.0f, 210.0f, 15.0f),
        new PropPlacement("object/static/vehicle/static_lambda_shuttle.iff", -120.0f, 240.0f, -200.0f, 75.0f),
        new PropPlacement("object/static/vehicle/static_tie_fighter.iff", 40.0f, 236.0f, -240.0f, -15.0f),
        new PropPlacement("object/static/vehicle/static_tie_bomber.iff", -170.0f, 242.0f, -150.0f, 120.0f),
        new PropPlacement("object/static/vehicle/static_yt_1300.iff", -190.0f, 240.0f, 80.0f, 160.0f),
        new PropPlacement("object/static/vehicle/static_tie_fighter.iff", -70.0f, 238.0f, 230.0f, 45.0f),
        new PropPlacement("object/static/space/ship/nebulon_frigate.iff", -240.0f, 258.0f, 110.0f, -8.0f),
        new PropPlacement("object/static/vehicle/static_tie_bomber.iff", 140.0f, 244.0f, -260.0f, 20.0f),
        new PropPlacement("object/static/vehicle/static_lambda_shuttle.iff", 230.0f, 250.0f, 170.0f, -110.0f),
        new PropPlacement("object/static/vehicle/static_flare_s.iff", -210.0f, 246.0f, 210.0f, 40.0f),
        new PropPlacement("object/static/vehicle/player_shuttle.iff", 250.0f, 252.0f, -90.0f, 10.0f),
    };

    private static final PropPlacement[] MOS_EISLEY_EXTRA_PROPS = new PropPlacement[]
    {
        new PropPlacement("object/static/vehicle/static_lambda_shuttle.iff", -150.0f, 198.0f, -60.0f, 55.0f),
        new PropPlacement("object/static/vehicle/static_speeder_sorob_lars.iff", 18.0f, 2.0f, -18.0f, -20.0f),
        new PropPlacement("object/static/vehicle/static_hoverlifter.iff", -20.0f, 3.0f, 25.0f, 35.0f),
        new PropPlacement("object/static/structure/tatooine/guild_banner_free_style_01.iff", 10.0f, 0.0f, -8.0f, -90.0f),
        new PropPlacement("object/static/structure/tatooine/tato_imprv_bannerpole_s01.iff", -12.0f, 0.0f, -5.0f, 70.0f),
        new PropPlacement("object/static/structure/general/banner_tatooine_style_01.iff", 4.0f, 0.0f, 12.0f, 2.0f),
        new PropPlacement("object/static/structure/general/banner_rebel_style_01.iff", -6.0f, 0.0f, 14.0f, -2.0f),
        new PropPlacement("object/static/structure/general/streetlamp_medium_style_01_on.iff", 7.0f, 0.0f, -1.0f, 0.0f),
        new PropPlacement("object/static/structure/general/streetlamp_medium_style_01_on.iff", -7.0f, 0.0f, -1.0f, 180.0f),
        new PropPlacement("object/static/vehicle/static_flare_s.iff", 170.0f, 210.0f, -110.0f, 0.0f)
    };

    private static final PropPlacement[][] MOS_EISLEY_RANDOM_DECO = new PropPlacement[][]
    {
        new PropPlacement[]
        {
            new PropPlacement("object/tangible/event_perk/frn_tato_fruit_stand_small_style_01.iff", 22.0f, 0.0f, 26.0f, -60.0f),
            new PropPlacement("object/tangible/event_perk/frn_tato_meat_rack.iff", 25.5f, 0.0f, 20.5f, 35.0f),
            new PropPlacement("object/static/structure/tatooine/debris_tatt_crate_1.iff", 20.0f, 0.0f, 22.0f, 25.0f)
        },
        new PropPlacement[]
        {
            new PropPlacement("object/static/structure/general/streetlamp_medium_style_01_on.iff", -22.0f, 0.0f, 22.0f, 55.0f),
            new PropPlacement("object/static/structure/general/streetlamp_medium_style_01_on.iff", -18.5f, 0.0f, 26.5f, -35.0f),
            new PropPlacement("object/static/structure/general/banner_tatooine_style_01.iff", -20.0f, 0.0f, 18.0f, 10.0f)
        },
        new PropPlacement[]
        {
            new PropPlacement("object/tangible/furniture/tatooine/frn_tato_table_small_style_01.iff", 15.5f, 0.0f, -22.0f, 90.0f),
            new PropPlacement("object/tangible/furniture/tatooine/frn_tato_chair_cafe_style_01.iff", 14.0f, 0.0f, -20.0f, -45.0f),
            new PropPlacement("object/tangible/furniture/tatooine/frn_tato_chair_cafe_style_02.iff", 17.0f, 0.0f, -20.5f, 120.0f),
            new PropPlacement("object/static/structure/tatooine/debris_tatt_drum_storage_2.iff", 13.5f, 0.0f, -24.0f, -10.0f)
        },
        new PropPlacement[]
        {
            new PropPlacement("object/static/structure/general/banner_rebel_style_01.iff", -10.5f, 0.0f, -22.5f, -20.0f),
            new PropPlacement("object/static/structure/general/banner_tatooine_style_01.iff", -14.5f, 0.0f, -18.5f, 160.0f),
            new PropPlacement("object/static/structure/general/streetlamp_medium_style_01_on.iff", -12.5f, 0.0f, -20.5f, 40.0f)
        }
    };

    private static final PropPlacement[] THEED_EXTRA_PROPS = new PropPlacement[]
    {
        new PropPlacement("object/static/vehicle/player_shuttle.iff", 220.0f, 250.0f, -140.0f, -12.0f),
        new PropPlacement("object/static/vehicle/static_lambda_shuttle.iff", -220.0f, 256.0f, 150.0f, 18.0f),
        new PropPlacement("object/static/vehicle/static_yt_1300.iff", 170.0f, 244.0f, 220.0f, -140.0f),
        new PropPlacement("object/static/structure/naboo/obelisk_naboo_theed_style_1.iff", 14.0f, 0.0f, -18.0f, 45.0f),
        new PropPlacement("object/static/structure/naboo/obelisk_naboo_theed_style_1.iff", -14.0f, 0.0f, -18.0f, -45.0f),
        new PropPlacement("object/static/structure/naboo/nboo_imprv_bannerpole_s01.iff", 8.0f, 0.0f, 14.0f, 0.0f),
        new PropPlacement("object/static/structure/naboo/nboo_imprv_bannerpole_s01.iff", -8.0f, 0.0f, 14.0f, 180.0f),
        new PropPlacement("object/static/structure/general/streetlamp_medium_green_style_02.iff", 6.0f, 0.0f, -10.0f, 90.0f),
        new PropPlacement("object/static/structure/general/streetlamp_medium_green_style_02.iff", -6.0f, 0.0f, -10.0f, -90.0f),
        new PropPlacement("object/static/structure/naboo/arbor_pillar_s01.iff", 20.0f, 0.0f, 20.0f, 15.0f),
        new PropPlacement("object/static/structure/naboo/arbor_pillar_s01.iff", -20.0f, 0.0f, 20.0f, -15.0f),
        new PropPlacement("object/static/vehicle/static_yt_2400_shuttle.iff", -170.0f, 242.0f, -220.0f, 75.0f),
        new PropPlacement("object/static/vehicle/static_yt_2400_ground.iff", 24.0f, 2.5f, 30.0f, -75.0f)
    };

    private static final PropPlacement[][] THEED_RANDOM_DECO = new PropPlacement[][]
    {
        new PropPlacement[]
        {
            new PropPlacement("object/static/structure/naboo/garden_gazebo_sml_s01.iff", 28.0f, 0.0f, 26.0f, 45.0f),
            new PropPlacement("object/static/structure/general/streetlamp_medium_green_style_02.iff", 24.5f, 0.0f, 20.5f, -60.0f),
            new PropPlacement("object/static/structure/naboo/planter_naboo_theed_style_1.iff", 30.5f, 0.0f, 22.0f, 20.0f)
        },
        new PropPlacement[]
        {
            new PropPlacement("object/static/structure/naboo/arbor_long_s01.iff", -26.0f, 0.0f, 26.0f, -35.0f),
            new PropPlacement("object/static/structure/naboo/arbor_pillar_s01.iff", -30.5f, 0.0f, 20.5f, -15.0f),
            new PropPlacement("object/static/structure/naboo/arbor_pillar_s01.iff", -21.5f, 0.0f, 28.0f, -120.0f)
        },
        new PropPlacement[]
        {
            new PropPlacement("object/static/structure/general/planter_generic_style_1.iff", 16.0f, 0.0f, -18.0f, 0.0f),
            new PropPlacement("object/static/structure/general/planter_generic_style_4.iff", 20.5f, 0.0f, -22.5f, 65.0f),
            new PropPlacement("object/static/structure/naboo/nboo_imprv_bannerpole_s01.iff", 18.0f, 0.0f, -16.0f, 180.0f)
        },
        new PropPlacement[]
        {
            new PropPlacement("object/static/structure/general/streetlamp_medium_green_style_02.iff", -18.0f, 0.0f, -20.0f, 75.0f),
            new PropPlacement("object/static/structure/general/streetlamp_medium_green_style_02.iff", -22.0f, 0.0f, -16.0f, -45.0f),
            new PropPlacement("object/static/structure/naboo/planter_naboo_theed_style_1.iff", -20.0f, 0.0f, -22.0f, 10.0f)
        }
    };

    private static final CelebrationPlan[] PLANS;

    static
    {
        CityPlanData[] planData = new CityPlanData[]
        {
            new CityPlanData(
                "mos_eisley",
                "tatooine",
                "@tatooine_region_names:mos_eisley",
                "Mos Eisley",
                3528.0f,
                7.0f,
                -4804.0f,
                new CityNpc[]
                {
                    new CityNpc("object/mobile/space_greeter_mos_eisley_smuggler_fat.iff", "Mos Eisley Launch Host", chat.MOOD_JOYFUL),
                    new CityNpc("object/mobile/space_greeter_mos_eisley_smuggler_skinny.iff", "Skyway Spotter", chat.MOOD_ENTHUSIASTIC),
                    new CityNpc("object/mobile/space_greeter_mos_eisley_smuggler_nervous.iff", "Dockside Lookout", chat.MOOD_HOPEFUL),
                    new CityNpc("object/mobile/dressed_eisley_officer_zabrak_female_01.iff", "%s Flight Marshal", chat.MOOD_CONFIDENT),
                    new CityNpc("object/mobile/dressed_eisley_officer_aqualish_male_01.iff", "%s Launch Marshal", chat.MOOD_PROUD),
                    new CityNpc("object/mobile/dressed_story_loot_eisley_01.iff", "Canyon Storyteller", chat.MOOD_JOYFUL),
                    new CityNpc("object/mobile/dressed_eisley_officer_bothan_female_01.iff", "Celebration Quartermaster", chat.MOOD_CHEERFUL),
                    new CityNpc("object/mobile/dressed_eisley_officer_twilek_male_01.iff", "%s Harbor Herald", chat.MOOD_ENTHUSIASTIC)
                },
                MOS_EISLEY_EXTRA_PROPS,
                MOS_EISLEY_RANDOM_DECO,
                new String[]
                {
                    "Mos Eisley is roaring with launch night energy!",
                    "Sandcrawler DJs keep the Mos Eisley block party shaking!",
                    "Glow banners ripple through every Mos Eisley plaza tonight!",
                    "Street performers juggle flares while starships dive over Mos Eisley!",
                    "Look up! The Live Launch fireworks are blazing over Mos Eisley!",
                    "Smugglers and pilgrims dance together under Mos Eisley's sky parade!"
                }
            ),
            new CityPlanData(
                "theed",
                "naboo",
                "@naboo_region_names:theed",
                "Theed",
                -4856.0f,
                6.0f,
                4162.0f,
                new CityNpc[]
                {
                    new CityNpc("object/mobile/typho.iff", "Captain Typho", chat.MOOD_CONFIDENT),
                    new CityNpc("object/mobile/royal_guard.iff", "Royal Honor Guard", chat.MOOD_PROUD),
                    new CityNpc("object/mobile/dressed_theed_palace_chamberlain.iff", "Palace Chamberlain", chat.MOOD_PROUD),
                    new CityNpc("object/mobile/space_greeter_theed_freighter_captain.iff", "%s Starport Greeter", chat.MOOD_ENTHUSIASTIC),
                    new CityNpc("object/mobile/naboo_theed_gurdun.iff", "Theed Festival Minister", chat.MOOD_RESPECTFUL),
                    new CityNpc("object/mobile/dressed_story_loot_theed.iff", "%s Archive Storyteller", chat.MOOD_JOYFUL),
                    new CityNpc("object/mobile/dressed_entertainer_gcw_f_zab_01.iff", "Celebration Acrobat", chat.MOOD_ECSTATIC),
                    new CityNpc("object/mobile/space_greeter_tyrena_pilot_friend.iff", "Royal Flight Liaison", chat.MOOD_ENCOURAGING)
                },
                THEED_EXTRA_PROPS,
                THEED_RANDOM_DECO,
                new String[]
                {
                    "Theed Plaza sparkles for the Live Launch festivities!",
                    "Theed's waterfalls shimmer with holo-illumination for launch night!",
                    "Royal symphonies echo as cruisers drift above Theed!",
                    "Pilots corkscrew past Theed's spires in dazzling formation!",
                    "Fountains erupt in time with the fireworks over Theed!",
                    "Families picnic in Theed's plazas as celebration skiffs arc overhead!"
                }
            )
        };
        PLANS = new CelebrationPlan[planData.length];
        for (int i = 0; i < planData.length; i++)
        {
            CityPlanData entry = planData[i];
            SpawnInfo[] npcSpawns = createFestivalNpcs(entry.anchorX, entry.anchorY, entry.anchorZ, entry.cityDisplayName, entry.localNpcs);
            SpawnInfo[] propSpawns = createFestivalProps(entry.anchorX, entry.anchorY, entry.anchorZ, entry.extraProps, entry.randomDecor);
            PLANS[i] = new CelebrationPlan(entry.planId, entry.planet, entry.regionName, entry.cityDisplayName, entry.anchorX, entry.anchorY, entry.anchorZ, npcSpawns, propSpawns, cloneOffsets(GRAND_FIREWORK_PATTERN), entry.chatLines);
        }
    }


    public int OnInitialize(obj_id self) throws InterruptedException
    {
        ensureEventState(self);
        return SCRIPT_CONTINUE;
    }


    public int OnAttach(obj_id self) throws InterruptedException
    {
        ensureEventState(self);
        return SCRIPT_CONTINUE;
    }


    public int OnDestroy(obj_id self) throws InterruptedException
    {
        stopCelebration(self, false);
        return SCRIPT_CONTINUE;
    }


    public int OnHearSpeech(obj_id self, obj_id speaker, String text) throws InterruptedException
    {
        if (!isGod(speaker))
        {
            return SCRIPT_CONTINUE;
        }
        if (text == null)
        {
            return SCRIPT_CONTINUE;
        }
        String lower = text.toLowerCase();
        if ("startlivelauch".equals(lower) || "startlivelaunch".equals(lower))
        {
            startCelebration(self);
            sendSystemMessageTestingOnly(speaker, "Live Launch celebration manually activated.");
            return SCRIPT_OVERRIDE;
        }
        if ("stoplivelauch".equals(lower) || "stoplivelaunch".equals(lower))
        {
            stopCelebration(self, true);
            sendSystemMessageTestingOnly(speaker, "Live Launch celebration manually deactivated.");
            return SCRIPT_OVERRIDE;
        }
        return SCRIPT_CONTINUE;
    }

    public int handleLiveLaunchCheck(obj_id self, dictionary params) throws InterruptedException
    {
        ensureEventState(self);
        return SCRIPT_CONTINUE;
    }

    public int burstFireworks(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationActive(self))
        {
            return SCRIPT_CONTINUE;
        }
        CelebrationPlan plan = getPlan(self);
        if (plan == null)
        {
            return SCRIPT_CONTINUE;
        }
        location here = getLocation(self);
        location anchor = new location(plan.anchorX, plan.anchorY, plan.anchorZ, here.area);
        for (float[] offset : plan.fireworkOffsets)
        {
            location base = new location(anchor);
            base.x += offset[0];
            base.z += offset[1];
            location drop = utils.getRandomLocationInRing(base, 6.0f, 32.0f);
            if (drop == null)
            {
                drop = new location(base);
            }
            drop.area = here.area;
            drop.y = base.y;
            int totalRows = dataTableGetNumRows(firework.TBL_FX);
            if (totalRows > 0)
            {
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

    public int playCelebrationMusic(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationActive(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id[] players = getPlayerCreaturesInRange(self, MUSIC_RANGE);
        if (players != null)
        {
            for (obj_id player : players)
            {
                playMusic(player, MUSIC_TRACK);
            }
        }
        messageTo(self, "playCelebrationMusic", null, MUSIC_INTERVAL, false);
        return SCRIPT_CONTINUE;
    }

    public int performAmbientCelebration(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationActive(self))
        {
            return SCRIPT_CONTINUE;
        }
        CelebrationPlan plan = getPlan(self);
        if (plan == null)
        {
            return SCRIPT_CONTINUE;
        }
        if (utils.hasScriptVar(self, VAR_NPCS))
        {
            Vector npcs = utils.getResizeableObjIdArrayScriptVar(self, VAR_NPCS);
            if (npcs != null)
            {
                for (Object entry : npcs)
                {
                    obj_id npc = (obj_id) entry;
                    if (!isIdValid(npc) || !exists(npc))
                    {
                        continue;
                    }
                    if (rand(0, 100) < 60)
                    {
                        String anim = AMBIENT_ANIMATIONS[rand(0, AMBIENT_ANIMATIONS.length - 1)];
                        doAnimationAction(npc, anim);
                    }
                    if (plan.chatLines != null && plan.chatLines.length > 0 && rand(0, 100) < 35)
                    {
                        int chatIndex = rand(0, plan.chatLines.length - 1);
                        queueNpcChat(self, npc, plan.chatLines[chatIndex]);
                    }
                }
            }
        }
        messageTo(self, "performAmbientCelebration", null, AMBIENT_INTERVAL, false);
        return SCRIPT_CONTINUE;
    }

    private void queueNpcChat(obj_id self, obj_id npc, String line) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc) || line == null || line.length() == 0)
        {
            return;
        }
        dictionary chatParams = new dictionary();
        chatParams.put("npc", npc);
        chatParams.put("line", line);
        float delay = randomFloat(NPC_CHAT_DELAY_MIN, NPC_CHAT_DELAY_MAX);
        messageTo(self, "deliverNpcChat", chatParams, delay, false);
    }

    public int deliverNpcChat(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationActive(self))
        {
            return SCRIPT_CONTINUE;
        }
        if (params == null)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id npc = params.getObjId("npc");
        String line = params.getString("line");
        if (!isIdValid(npc) || !exists(npc) || line == null || line.length() == 0)
        {
            return SCRIPT_CONTINUE;
        }
        chat.chat(npc, line);
        return SCRIPT_CONTINUE;
    }

    public int triggerFunMoments(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isCelebrationActive(self))
        {
            return SCRIPT_CONTINUE;
        }
        CelebrationPlan plan = getPlan(self);
        if (plan == null)
        {
            return SCRIPT_CONTINUE;
        }
        location here = getLocation(self);
        location anchor = new location(plan.anchorX, plan.anchorY, plan.anchorZ, here.area);
        int eventType = rand(0, 3);
        switch (eventType)
        {
            case 0:
            {
                obj_id[] players = getPlayerCreaturesInRange(self, MUSIC_RANGE);
                if (players != null && players.length > 0)
                {
                    String effect = FUN_EFFECTS[rand(0, FUN_EFFECTS.length - 1)];
                    playClientEffectLoc(players, effect, anchor, 0.0f);
                    String broadcast = FUN_EVENT_BROADCASTS[rand(0, FUN_EVENT_BROADCASTS.length - 1)];
                    broadcast = formatName(broadcast, plan.cityDisplayName);
                    for (obj_id player : players)
                    {
                        sendSystemMessage(player, broadcast, null);
                    }
                }
                break;
            }
            case 1:
            {
                for (int i = 0; i < 3; i++)
                {
                    location drop = utils.getRandomLocationInRing(anchor, 8.0f, 32.0f);
                    if (drop == null)
                    {
                        drop = new location(anchor);
                    }
                    drop.area = here.area;
                    drop.y = anchor.y + 4.0f;
                    int totalRows = dataTableGetNumRows(firework.TBL_FX);
                    if (totalRows > 0)
                    {
                        int row = rand(1, totalRows);
                        String template = dataTableGetString(firework.TBL_FX, row, "template");
                        obj_id effect = create.object(template, drop);
                        if (isIdValid(effect))
                        {
                            attachScript(effect, firework.SCRIPT_FIREWORK_CLEANUP);
                        }
                    }
                }
                break;
            }
            case 2:
            {
                if (utils.hasScriptVar(self, VAR_NPCS))
                {
                    Vector npcs = utils.getResizeableObjIdArrayScriptVar(self, VAR_NPCS);
                    if (npcs != null && !npcs.isEmpty())
                    {
                        int index = rand(0, npcs.size() - 1);
                        obj_id npc = (obj_id) npcs.get(index);
                        if (isIdValid(npc) && exists(npc))
                        {
                            String anim = AMBIENT_ANIMATIONS[rand(0, AMBIENT_ANIMATIONS.length - 1)];
                            doAnimationAction(npc, anim);
                            if (plan.chatLines != null && plan.chatLines.length > 0)
                            {
                                queueNpcChat(self, npc, plan.chatLines[rand(0, plan.chatLines.length - 1)]);
                            }
                        }
                    }
                }
                break;
            }
            default:
            {
                obj_id[] players = getPlayerCreaturesInRange(self, MUSIC_RANGE);
                if (players != null && players.length > 0 && plan.chatLines != null && plan.chatLines.length > 0)
                {
                    String message = plan.chatLines[rand(0, plan.chatLines.length - 1)];
                    for (obj_id player : players)
                    {
                        sendSystemMessage(player, message, null);
                    }
                }
                break;
            }
        }
        float delay = randomFloat(FUN_EVENT_INTERVAL_MIN, FUN_EVENT_INTERVAL_MAX);
        messageTo(self, "triggerFunMoments", null, delay, false);
        return SCRIPT_CONTINUE;
    }

    private float randomFloat(float min, float max) throws InterruptedException
    {
        if (max <= min)
        {
            return min;
        }
        return min + (rand(0, 1000) / 1000.0f) * (max - min);
    }

    private void ensureEventState(obj_id self) throws InterruptedException
    {
        if (isEventEnabled())
        {
            startCelebration(self);
            scheduleCheck(self, 180.0f);
        }
        else
        {
            if (isCelebrationActive(self))
            {
                stopCelebration(self, false);
            }
            scheduleCheck(self, 180.0f);
        }
    }

    private void startCelebration(obj_id self) throws InterruptedException
    {
        if (isCelebrationActive(self))
        {
            return;
        }
        CelebrationPlan plan = resolvePlan(self);
        if (plan == null)
        {
            scheduleCheck(self, 180.0f);
            return;
        }
        utils.setScriptVar(self, VAR_PLAN_INDEX, plan.planIndex);
        spawnGroup(self, plan.npcSpawns, true);
        spawnGroup(self, plan.propSpawns, false);
        utils.setScriptVar(self, VAR_ACTIVE, true);
        announceCelebration(self, plan);
        messageTo(self, "burstFireworks", null, 5.0f, false);
        messageTo(self, "performAmbientCelebration", null, 10.0f, false);
        messageTo(self, "playCelebrationMusic", null, 6.0f, false);
        messageTo(self, "triggerFunMoments", null, 12.0f, false);
    }

    private void stopCelebration(obj_id self, boolean announce) throws InterruptedException
    {
        utils.removeScriptVar(self, VAR_ACTIVE);
        cleanupSpawns(self, VAR_SPAWNS);
        cleanupSpawns(self, VAR_NPCS);
        utils.removeScriptVar(self, VAR_PLAN_INDEX);
        if (announce)
        {
            obj_id[] players = getPlayerCreaturesInRange(self, MUSIC_RANGE);
            if (players != null)
            {
                for (obj_id player : players)
                {
                    sendSystemMessage(player, "The Live Launch festivities wind down.", null);
                }
            }
        }
        scheduleCheck(self, 300.0f);
    }

    private static SpawnInfo[] createFestivalNpcs(float anchorX, float anchorY, float anchorZ, String cityDisplayName, CityNpc[] cityNpcs)
    {
        Vector npcList = new Vector();
        for (int i = 0; i < GENERAL_FESTIVAL_STAFF.length; i++)
        {
            NpcPlacement placement = GENERAL_FESTIVAL_STAFF[i];
            if (placement == null)
            {
                continue;
            }
            String name = formatName(placement.namePattern, cityDisplayName);
            npcList.add(npcOffset(placement.template, anchorX, anchorY, anchorZ, placement.offsetX, placement.offsetZ, placement.yaw, name, placement.mood));
        }
        if (cityNpcs != null)
        {
            int limit = Math.min(cityNpcs.length, LOCAL_NPC_POSITIONS.length);
            for (int i = 0; i < limit; i++)
            {
                CityNpc npc = cityNpcs[i];
                if (npc == null)
                {
                    continue;
                }
                float[] pos = LOCAL_NPC_POSITIONS[i];
                String name = formatName(npc.name, cityDisplayName);
                npcList.add(npcOffset(npc.template, anchorX, anchorY, anchorZ, pos[0], pos[1], pos[2], name, npc.mood));
            }
        }
        SpawnInfo[] result = new SpawnInfo[npcList.size()];
        for (int i = 0; i < npcList.size(); i++)
        {
            result[i] = (SpawnInfo) npcList.get(i);
        }
        return result;
    }

    private static SpawnInfo[] createFestivalProps(float anchorX, float anchorY, float anchorZ, PropPlacement[] extraProps, PropPlacement[][] randomDecor)
    {
        Vector propList = new Vector();
        for (int i = 0; i < BASE_PROP_LAYOUT.length; i++)
        {
            PropPlacement placement = BASE_PROP_LAYOUT[i];
            if (placement == null)
            {
                continue;
            }
            propList.add(propOffset(placement.template, anchorX, anchorY, anchorZ, placement.offsetX, placement.offsetY, placement.offsetZ, placement.yaw));
        }
        if (extraProps != null)
        {
            for (int i = 0; i < extraProps.length; i++)
            {
                PropPlacement placement = extraProps[i];
                if (placement == null)
                {
                    continue;
                }
                propList.add(propOffset(placement.template, anchorX, anchorY, anchorZ, placement.offsetX, placement.offsetY, placement.offsetZ, placement.yaw));
            }
        }
        if (randomDecor != null)
        {
            for (int i = 0; i < randomDecor.length; i++)
            {
                PropPlacement[] cluster = randomDecor[i];
                if (cluster == null || cluster.length == 0)
                {
                    continue;
                }
                if (Math.random() < 0.7d)
                {
                    for (int j = 0; j < cluster.length; j++)
                    {
                        PropPlacement placement = cluster[j];
                        if (placement == null)
                        {
                            continue;
                        }
                        propList.add(propOffset(placement.template, anchorX, anchorY, anchorZ, placement.offsetX, placement.offsetY, placement.offsetZ, placement.yaw));
                    }
                }
            }
        }
        SpawnInfo[] result = new SpawnInfo[propList.size()];
        for (int i = 0; i < propList.size(); i++)
        {
            result[i] = (SpawnInfo) propList.get(i);
        }
        return result;
    }

    private static String formatName(String pattern, String cityDisplayName)
    {
        if (pattern == null)
        {
            return "";
        }
        if (cityDisplayName == null)
        {
            cityDisplayName = "";
        }
        if (pattern.indexOf("%s") >= 0)
        {
            return pattern.replace("%s", cityDisplayName);
        }
        return pattern;
    }

    private static float[][] cloneOffsets(float[][] source)
    {
        if (source == null)
        {
            return null;
        }
        float[][] copy = new float[source.length][];
        for (int i = 0; i < source.length; i++)
        {
            float[] entry = source[i];
            if (entry != null && entry.length >= 2)
            {
                copy[i] = new float[]
                {
                    entry[0],
                    entry[1]
                };
            }
            else
            {
                copy[i] = new float[]
                {
                    0.0f,
                    0.0f
                };
            }
        }
        return copy;
    }

    private static SpawnInfo npcOffset(String template, float anchorX, float anchorY, float anchorZ, float offsetX, float offsetZ, float yaw, String name, String mood)
    {
        return SpawnInfo.npc(template, anchorX + offsetX, anchorY, anchorZ + offsetZ, yaw, name, mood);
    }

    private static SpawnInfo propOffset(String template, float anchorX, float anchorY, float anchorZ, float offsetX, float offsetY, float offsetZ, float yaw)
    {
        return SpawnInfo.prop(template, anchorX + offsetX, anchorY + offsetY, anchorZ + offsetZ, yaw);
    }

    private void cleanupSpawns(obj_id self, String scriptVar) throws InterruptedException
    {
        if (!utils.hasScriptVar(self, scriptVar))
        {
            return;
        }
        Vector spawns = utils.getResizeableObjIdArrayScriptVar(self, scriptVar);
        utils.removeScriptVar(self, scriptVar);
        if (spawns == null)
        {
            return;
        }
        for (Object entry : spawns)
        {
            obj_id obj = (obj_id) entry;
            if (isIdValid(obj) && exists(obj))
            {
                destroyObject(obj);
            }
        }
    }

    private boolean isEventEnabled() throws InterruptedException
    {
        String value = getConfigSetting(CONFIG_SECTION, CONFIG_FLAG);
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1"));
    }

    private boolean isCelebrationActive(obj_id self) throws InterruptedException
    {
        return utils.hasScriptVar(self, VAR_ACTIVE) && utils.getBooleanScriptVar(self, VAR_ACTIVE);
    }

    private CelebrationPlan resolvePlan(obj_id self) throws InterruptedException
    {
        CelebrationPlan plan = getPlan(self);
        if (plan != null)
        {
            return plan;
        }
        plan = getPlanFromObjVar(self);
        if (plan != null)
        {
            return plan;
        }
        location here = getLocation(self);
        String region = locations.getGuardSpawnerRegionName(here);
        for (int i = 0; i < PLANS.length; i++)
        {
            CelebrationPlan candidate = PLANS[i];
            if ((region != null && region.equals(candidate.regionName)) || (here != null && here.area != null && here.area.equals(candidate.planet) && getDistance(new location(candidate.anchorX, candidate.anchorY, candidate.anchorZ, here.area), here) < 600.0f))
            {
                return selectPlan(self, candidate, i);
            }
        }
        return null;
    }

    private CelebrationPlan getPlanFromObjVar(obj_id self) throws InterruptedException
    {
        if (!hasObjVar(self, OBJVAR_PLAN_ID))
        {
            return null;
        }
        String planId = getStringObjVar(self, OBJVAR_PLAN_ID);
        if (planId == null || planId.length() == 0)
        {
            return null;
        }
        for (int i = 0; i < PLANS.length; i++)
        {
            CelebrationPlan candidate = PLANS[i];
            if (candidate.planId != null && candidate.planId.equalsIgnoreCase(planId))
            {
                return selectPlan(self, candidate, i);
            }
        }
        return null;
    }

    private CelebrationPlan selectPlan(obj_id self, CelebrationPlan candidate, int index) throws InterruptedException
    {
        candidate.planIndex = index;
        utils.setScriptVar(self, VAR_PLAN_INDEX, index);
        return candidate;
    }

    private CelebrationPlan getPlan(obj_id self) throws InterruptedException
    {
        if (utils.hasScriptVar(self, VAR_PLAN_INDEX))
        {
            int idx = utils.getIntScriptVar(self, VAR_PLAN_INDEX);
            if (idx >= 0 && idx < PLANS.length)
            {
                PLANS[idx].planIndex = idx;
                return PLANS[idx];
            }
        }
        return null;
    }

    private void spawnGroup(obj_id self, SpawnInfo[] entries, boolean recordAsNpc) throws InterruptedException
    {
        if (entries == null || entries.length == 0)
        {
            return;
        }
        location here = getLocation(self);
        Vector allSpawns = utils.hasScriptVar(self, VAR_SPAWNS) ? utils.getResizeableObjIdArrayScriptVar(self, VAR_SPAWNS) : new Vector();
        Vector npcSpawns = recordAsNpc && utils.hasScriptVar(self, VAR_NPCS) ? utils.getResizeableObjIdArrayScriptVar(self, VAR_NPCS) : new Vector();
        for (SpawnInfo info : entries)
        {
            location spawnLoc = new location(info.x, info.y, info.z, here.area);
            obj_id spawned = null;
            if (info.useStaticObject)
            {
                spawned = create.staticObject(info.template, spawnLoc);
            }
            else if (info.spawnWithAi)
            {
                spawned = create.object(info.template, spawnLoc, true);
            }
            else
            {
                spawned = create.object(info.template, spawnLoc);
            }
            if (!isIdValid(spawned))
            {
                continue;
            }
            setYaw(spawned, info.yaw);
            if (info.displayName != null && info.displayName.length() > 0)
            {
                setName(spawned, info.displayName);
            }
            setObjVar(spawned, "liveLaunch.controller", self);
            if (info.spawnWithAi)
            {
                setInvulnerable(spawned, true);
                ai_lib.setDefaultCalmBehavior(spawned, ai_lib.BEHAVIOR_LOITER);
                if (info.mood != null && info.mood.length() > 0)
                {
                    ai_lib.setMood(spawned, info.mood);
                }
                npcSpawns = utils.addElement(npcSpawns, spawned);
            }
            allSpawns = utils.addElement(allSpawns, spawned);
        }
        if (allSpawns != null && !allSpawns.isEmpty())
        {
            utils.setScriptVar(self, VAR_SPAWNS, allSpawns);
        }
        if (recordAsNpc && npcSpawns != null && !npcSpawns.isEmpty())
        {
            utils.setScriptVar(self, VAR_NPCS, npcSpawns);
        }
    }

    private void announceCelebration(obj_id self, CelebrationPlan plan) throws InterruptedException
    {
        if (plan == null)
        {
            return;
        }
        obj_id[] players = getPlayerCreaturesInRange(self, MUSIC_RANGE);
        if (players != null)
        {
            String message = "The Live Launch celebration ignites in " + plan.cityDisplayName + "!";
            for (obj_id player : players)
            {
                sendSystemMessage(player, message, null);
            }
        }
    }

    private void scheduleCheck(obj_id self, float delay) throws InterruptedException
    {
        if (!hasMessageTo(self, "handleLiveLaunchCheck"))
        {
            messageTo(self, "handleLiveLaunchCheck", null, delay, false);
        }
    }

    public static final class CityPlanData
    {
        public final String planId;
        public final String planet;
        public final String regionName;
        public final String cityDisplayName;
        public final float anchorX;
        public final float anchorY;
        public final float anchorZ;
        public final CityNpc[] localNpcs;
        public final PropPlacement[] extraProps;
        public final PropPlacement[][] randomDecor;
        public final String[] chatLines;

        public CityPlanData()
        {
            this(null, null, null, null, 0.0f, 0.0f, 0.0f, null, null, null, null);
        }

        public CityPlanData(String planId, String planet, String regionName, String cityDisplayName, float anchorX, float anchorY, float anchorZ, CityNpc[] localNpcs, PropPlacement[] extraProps, PropPlacement[][] randomDecor, String[] chatLines)
        {
            this.planId = planId;
            this.planet = planet;
            this.regionName = regionName;
            this.cityDisplayName = cityDisplayName;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.localNpcs = localNpcs;
            this.extraProps = extraProps;
            this.randomDecor = randomDecor;
            this.chatLines = chatLines;
        }
    }

    public static final class CityNpc
    {
        public final String template;
        public final String name;
        public final String mood;

        public CityNpc()
        {
            this(null, null, null);
        }

        public CityNpc(String template, String name, String mood)
        {
            this.template = template;
            this.name = name;
            this.mood = mood;
        }
    }

    public static final class NpcPlacement
    {
        public final String template;
        public final float offsetX;
        public final float offsetZ;
        public final float yaw;
        public final String namePattern;
        public final String mood;

        public NpcPlacement()
        {
            this(null, 0.0f, 0.0f, 0.0f, null, null);
        }

        public NpcPlacement(String template, float offsetX, float offsetZ, float yaw, String namePattern, String mood)
        {
            this.template = template;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.yaw = yaw;
            this.namePattern = namePattern;
            this.mood = mood;
        }
    }

    public static final class PropPlacement
    {
        public final String template;
        public final float offsetX;
        public final float offsetY;
        public final float offsetZ;
        public final float yaw;

        public PropPlacement()
        {
            this(null, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        public PropPlacement(String template, float offsetX, float offsetY, float offsetZ, float yaw)
        {
            this.template = template;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.yaw = yaw;
        }
    }

    public static final class CelebrationPlan
    {
        public final String planId;
        public final String planet;
        public final String regionName;
        public final String cityDisplayName;
        public final float anchorX;
        public final float anchorY;
        public final float anchorZ;
        public final SpawnInfo[] npcSpawns;
        public final SpawnInfo[] propSpawns;
        public final float[][] fireworkOffsets;
        public final String[] chatLines;
        public int planIndex;

        public CelebrationPlan()
        {
            this(null, null, null, null, 0.0f, 0.0f, 0.0f, null, null, null, null);
        }

        public CelebrationPlan(String planId, String planet, String regionName, String cityDisplayName, float anchorX, float anchorY, float anchorZ, SpawnInfo[] npcSpawns, SpawnInfo[] propSpawns, float[][] fireworkOffsets, String[] chatLines)
        {
            this.planId = planId;
            this.planet = planet;
            this.regionName = regionName;
            this.cityDisplayName = cityDisplayName;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.npcSpawns = npcSpawns;
            this.propSpawns = propSpawns;
            this.fireworkOffsets = fireworkOffsets;
            this.chatLines = chatLines;
        }
    }

    public static final class SpawnInfo
    {
        public final String template;
        public final float x;
        public final float y;
        public final float z;
        public final float yaw;
        public final boolean useStaticObject;
        public final boolean spawnWithAi;
        public final String displayName;
        public final String mood;

        public SpawnInfo()
        {
            this(null, 0.0f, 0.0f, 0.0f, 0.0f, false, false, null, null);
        }

        private SpawnInfo(String template, float x, float y, float z, float yaw, boolean useStaticObject, boolean spawnWithAi, String displayName, String mood)
        {
            this.template = template;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.useStaticObject = useStaticObject;
            this.spawnWithAi = spawnWithAi;
            this.displayName = displayName;
            this.mood = mood;
        }

        public static SpawnInfo npc(String template, float x, float y, float z, float yaw, String name, String mood)
        {
            return new SpawnInfo(template, x, y, z, yaw, false, true, name, mood);
        }

        public static SpawnInfo prop(String template, float x, float y, float z, float yaw)
        {
            return new SpawnInfo(template, x, y, z, yaw, true, false, null, null);
        }
    }
}
