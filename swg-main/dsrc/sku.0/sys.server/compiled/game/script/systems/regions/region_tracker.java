package script.systems.regions;

import script.library.locations;
import script.obj_id;

public class region_tracker extends script.base_script {

    private static final String CITY_NAME_VAR = "strCity";
    private static final String REGION_SIZE_VAR = "intSize";

    public region_tracker() {}


    public int OnInitialize(obj_id self) throws InterruptedException {
        debugServerConsoleMsg(self, "Initializing region tracker for object ID: " + self);
        setCityAndRegionSize(self);
        return SCRIPT_CONTINUE;
    }


    public int OnAttach(obj_id self) throws InterruptedException {
        setCityAndRegionSize(self);
        return SCRIPT_CONTINUE;
    }

    private void setCityAndRegionSize(obj_id self) throws InterruptedException {
        if (!hasObjVar(self, CITY_NAME_VAR)) {
            String cityName = locations.getCityName(getLocation(self));

            if (cityName != null) {
                debugServerConsoleMsg(self, "Detected city name: " + cityName);
                setObjVar(self, CITY_NAME_VAR, cityName);

                if (hasObjVar(self, REGION_SIZE_VAR)) {
                    int regionSize = getIntObjVar(self, REGION_SIZE_VAR);
                    setObjVar(self, REGION_SIZE_VAR, regionSize / 2);
                } else {
                    debugServerConsoleMsg(self, "Region size not found. Default size will be used.");
                }
            } else {
                debugServerConsoleMsg(self, "Object ID " + self + " is outside any city. It should be reviewed for deletion.");
            }
        } else {
            String existingCityName = getStringObjVar(self, CITY_NAME_VAR);
            LOG("regions", "Existing city name found: " + existingCityName);
        }
    }
}

