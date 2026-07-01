package script.creature;

import script.library.hue;
import script.obj_id;
import script.ranged_int_custom_var;

public class droid_setup extends script.base_script {

    public int OnAttach(obj_id self) throws InterruptedException {
        assignDroidName(self);
        setDroidHue(self);
        return SCRIPT_CONTINUE;
    }

    private void assignDroidName(obj_id self) throws InterruptedException {
        String baseName = getName(self);
        String newName = null;

        switch (baseName) {
            case "mob/creature_names:protocol_droid_3po":
                newName = randomLetter() + randomDigit() + "-P0";
                break;
            case "mob/creature_names:r2":
                newName = "R2-" + randomLetter() + randomDigit();
                break;
            case "mob/creature_names:r3":
                newName = "R3-" + randomLetter() + randomDigit();
                break;
            case "mob/creature_names:r4":
                newName = "R4-" + randomLetter() + randomDigit();
                break;
            case "mob/creature_names:r5":
                newName = "R5-" + randomLetter() + randomDigit();
                break;
            case "mob/creature_names:eg6_power_droid":
                newName = "E" + randomLetter() + "-" + randomDigit();
                break;
            case "mob/creature_names:wed_treadwell":
                newName = "WED-" + randomLetter() + randomDigit();
                break;
            case "mob/creature_names:le_repair_droid":
                newName = "LE-" + randomLetter() + randomLetter() + randomDigit();
                break;
            case "mob/creature_names:ra7_bug_droid":
                newName = "RA7-" + randomLetter() + randomDigit();
                break;
            default:
                debugServerConsoleMsg(self, "Unknown droid type: " + baseName);
                break;
        }

        if (newName != null) {
            setName(self, newName);
        }
    }

    private char randomLetter() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return alphabet.charAt(rand(0, alphabet.length() - 1));
    }

    private int randomDigit() {
        return rand(0, 9);
    }

    public void setDroidHue(obj_id self) throws InterruptedException {
        ranged_int_custom_var[] c = hue.getPalcolorVars(self);
        if (c != null) {
            for (ranged_int_custom_var aC : c) {
                aC.setValue(rand(0, 63));
            }
        }
    }
} 
