package script.systems.spawning.dropship;

import script.dictionary;
import script.library.create;
import script.library.utils;
import script.location;
import script.obj_id;

import java.util.logging.Logger;
import java.util.Random;

public class lambda extends script.systems.spawning.dropship.base {

    private static final Logger LOGGER = Logger.getLogger(lambda.class.getName());
    private static final float ATTACH_DELAY_SECONDS = 2.0f;
    private static final float PAYLOAD_SPAWN_DELAY_SECONDS = 20.0f;
    private static final int COMMAND_DEPLOY = -1465754503;
    private static final int POSTURE_PRONE = 3;
    private static final int POSTURE_UPRIGHT = 1;

    public lambda() {
    }

    @Override
    public int OnAttach(obj_id self) throws InterruptedException {
        LOGGER.info("Lambda attached to object: " + self);
        messageTo(self, "handleAttachDelay", null, ATTACH_DELAY_SECONDS, false);
        return super.OnAttach(self);
    }

    public int handleAttachDelay(obj_id self, dictionary params) throws InterruptedException {
        LOGGER.info("Handling attach delay for: " + self);

        if (!isValid(self)) {
            LOGGER.warning("Invalid object in handleAttachDelay: " + self);
            return SCRIPT_CONTINUE;
        }

        stop(self);
        setPosture(self, POSTURE_PRONE);

        float dynamicDelay = calculatePayloadDelay(self);
        LOGGER.info("Calculated dynamic payload delay: " + dynamicDelay);

        messageTo(self, "spawnPayload", null, dynamicDelay, true);

        if (canDeploy(self)) {
            LOGGER.info("Deploying with command ID: " + COMMAND_DEPLOY);
            queueCommand(self, COMMAND_DEPLOY, self, "", COMMAND_PRIORITY_FRONT);
        } else {
            LOGGER.warning("Deployment conditions not met for: " + self);
        }

        return SCRIPT_CONTINUE;
    }

    public int changePosture(obj_id self, dictionary params) throws InterruptedException {
        LOGGER.info("Changing posture to upright for: " + self);

        if (!isValid(self)) {
            LOGGER.warning("Invalid object in changePosture: " + self);
            return SCRIPT_CONTINUE;
        }

        setPosture(self, POSTURE_UPRIGHT);
        messageTo(self, "onPostureChanged", null, 0.0f, false);
        return SCRIPT_CONTINUE;
    }

    private float calculatePayloadDelay(obj_id self) {
        float randomFactor = new Random().nextFloat();
        float adjustedDelay = PAYLOAD_SPAWN_DELAY_SECONDS * (0.8f + 0.4f * randomFactor);
        return adjustedDelay;
    }

    private boolean canDeploy(obj_id self) {
        boolean clearZone = checkSurroundings(self);
        boolean safeLanding = checkCollisionFreeLanding(self);

        LOGGER.info("Deployment readiness - Clear Zone: " + clearZone + ", Safe Landing: " + safeLanding);
        return clearZone && safeLanding;
    }

    private boolean checkSurroundings(obj_id self) {
        if (!isValid(self)) {
            return false;
        }

        obj_id[] nearbyObjects = getObjectsInRange(self, 50.0f);
        int hostileCount = 0;
        for (int i = 0; i < nearbyObjects.length; i++) {
            obj_id obj = nearbyObjects[i];
            if (hasObjVar(obj, "isHostile") && getBooleanObjVar(obj, "isHostile")) {
                hostileCount++;
            }
        }

        LOGGER.info("Hostile entities nearby: " + hostileCount);
        return hostileCount == 0;
    }

    private boolean checkCollisionFreeLanding(obj_id self) {
        if (!isValid(self)) {
            return false;
        }

        obj_id[] obstacles = getObjectsInRange(self, 5.0f);
        for (int i = 0; i < obstacles.length; i++) {
            obj_id obj = obstacles[i];
            if (!obj.equals(self) && isCollidable(obj)) {
                LOGGER.warning("Potential collision with object: " + obj);
                return false;
            }
        }

        return true;
    }

    private boolean isCollidable(obj_id obj) {
        return isValid(obj) && !utils.isIncapacitated(obj) && !utils.isDead(obj);
    }

    private boolean isValid(obj_id obj) {
        return obj != null && utils.isValidId(obj);
    }
} 

