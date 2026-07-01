package script.planet;

import script.dictionary;
import script.obj_id;
import script.library.utils;

import java.util.Random;

public class planet_weather extends script.base_script {
    public planet_weather() {}

    public static final String PARAM_WEATHER_DESIRED = "desired";
    public static final String PARAM_WEATHER_CURRENT = "current";
    public static final int WEATHER_GOOD = 0;
    public static final int WEATHER_MILD = 1;
    public static final int WEATHER_SEVERE = 2;
    public static final int WEATHER_EXTREME = 3;

    public static final int SEASON_WINTER = 0;
    public static final int SEASON_SPRING = 1;
    public static final int SEASON_SUMMER = 2;
    public static final int SEASON_FALL = 3;

    private int season = SEASON_SPRING;
    private int planetMood = 50;  // 0 = Calm, 100 = Chaotic
    private int timeOfDay = 0;

    public int OnAttach(obj_id self) {
        if (isInWorld(self)) {
            debugServerConsoleMsg(null, "Starting intelligent weather from OnAttach()");
            startWeather(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) {
        debugServerConsoleMsg(null, "Starting intelligent weather from OnInitialize()");
        startWeather(self);
        return SCRIPT_CONTINUE;
    }

    public int OnDetach(obj_id self) {
        resetWeather();
        return SCRIPT_CONTINUE;
    }

    private void resetWeather() {
        setWeatherData(WEATHER_GOOD, 0.0f, 0.0f);
    }

    private void startWeather(obj_id self) {
        updateTimeOfDay();
        season = determineSeason();

        int initialWeather = WEATHER_GOOD;
        float windX = rand(1.0f, 2.0f);
        float windZ = rand(1.0f, 2.0f);

        setWeatherData(initialWeather, windX, windZ);
        int duration = getRandomWeatherDuration(initialWeather, false);
        debugServerConsoleMsg(null, "Setting initial intelligent weather to " + initialWeather + " for " + duration + " seconds");

        dictionary params = createWeatherParams(initialWeather, initialWeather);
        messageTo(self, "updateWeather", params, duration, false);
    }

    public int updateWeather(obj_id self, dictionary params) {
        updateTimeOfDay();
        adjustPlanetMood();
        int desiredWeather = params.getInt(PARAM_WEATHER_DESIRED);
        int currentWeather = params.getInt(PARAM_WEATHER_CURRENT);

        desiredWeather = calculateNewDesiredWeather(desiredWeather);
        currentWeather = adjustCurrentWeather(currentWeather, desiredWeather);

        int duration = getRandomWeatherDuration(currentWeather, desiredWeather != currentWeather);
        float windX = rand(1.0f, 2.0f);
        float windZ = rand(1.0f, 2.0f);

        setWeatherData(currentWeather, windX, windZ);
        debugServerConsoleMsg(null, "Setting intelligent weather to " + currentWeather + " (" + desiredWeather + ") for " + duration + " seconds");

        dictionary newParams = createWeatherParams(desiredWeather, currentWeather);
        messageTo(self, "updateWeather", newParams, duration, false);

        return SCRIPT_CONTINUE;
    }

    private int adjustCurrentWeather(int current, int desired) {
        if (current != desired) {
            return current > desired ? --current : ++current;
        }
        return desired > 0 ? --current : current;
    }

    private int calculateNewDesiredWeather(int previousDesired) {
        int weightedMood = planetMood - (timeOfDay < 6 || timeOfDay > 18 ? 10 : 0);  // Favor calmer weather at night
        int randValue = rand(1, 100);

        if (weightedMood < 30) {
            return WEATHER_GOOD;
        } else if (weightedMood < 60) {
            return (randValue <= 60) ? WEATHER_MILD : WEATHER_GOOD;
        } else if (weightedMood < 80) {
            return (randValue <= 50) ? WEATHER_SEVERE : WEATHER_MILD;
        } else {
            return (randValue <= 40) ? WEATHER_EXTREME : WEATHER_SEVERE;
        }
    }

    private int getRandomWeatherDuration(int weatherType) {
        return getRandomWeatherDuration(weatherType, false);
    }

    private int getRandomWeatherDuration(int weatherType, boolean transition) {
        int baseMin = getDurationLimits(weatherType)[0];
        int baseMax = getDurationLimits(weatherType)[1];
        int duration = rand(baseMin, baseMax);
        return transition ? duration / 2 : duration;
    }

    private int[] getDurationLimits(int weatherType) {
        switch (weatherType) {
            case WEATHER_GOOD:
                return new int[]{1200, 2400};
            case WEATHER_MILD:
                return new int[]{240, 480};
            case WEATHER_SEVERE:
                return new int[]{120, 300};
            case WEATHER_EXTREME:
                return new int[]{60, 180};
            default:
                return new int[]{240, 480};
        }
    }

    private void adjustPlanetMood() {
        int change = (season == SEASON_SUMMER ? 5 : season == SEASON_WINTER ? -5 : 0);
        planetMood = Math.max(0, Math.min(100, planetMood + change));
    }

    private void updateTimeOfDay() {
        // Temporarily hardcoded time of day
        timeOfDay = 12;  // Assume midday
    }

    private int determineSeason() {
        // Temporarily hardcoded month to simulate seasonal changes
        int month = 3;  // Example month (April - Spring)
        if (month >= 2 && month <= 4) {
            return SEASON_SPRING;
        } else if (month >= 5 && month <= 7) {
            return SEASON_SUMMER;
        } else if (month >= 8 && month <= 10) {
            return SEASON_FALL;
        } else {
            return SEASON_WINTER;
        }
    }

    private dictionary createWeatherParams(int desired, int current) {
        dictionary params = new dictionary();
        params.put(PARAM_WEATHER_DESIRED, desired);
        params.put(PARAM_WEATHER_CURRENT, current);
        return params;
    }
}

