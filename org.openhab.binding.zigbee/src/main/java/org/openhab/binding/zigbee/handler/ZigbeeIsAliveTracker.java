package org.openhab.binding.zigbee.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service which starts timeout tasks to set a Thing to OFFLINE if it doesn't reset the timer before it runs out
 */
@Component(immediate = true, service = ZigbeeIsAliveTracker.class)
public class ZigbeeIsAliveTracker {

    private final Logger logger = LoggerFactory.getLogger(ZigbeeIsAliveTracker.class);

    private Map<ZigBeeThingHandler, Integer> handlerIntervalMapping = new ConcurrentHashMap<>();
    private Map<ZigBeeThingHandler, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    protected final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool("ZigbeeIsAliveTracker");

    /**
     * Adds a mapping from {@link ZigBeeThingHandler} to the interval in which it should have communicated
     *
     * @param zigBeeThingHandler      the {@link ZigBeeThingHandler}
     * @param expectedUpdateInterval the interval in which the device should have communicated with us
     */
    public void addHandler(ZigBeeThingHandler zigBeeThingHandler, int expectedUpdateInterval) {
        handlerIntervalMapping.put(zigBeeThingHandler, expectedUpdateInterval);
        resetTimer(zigBeeThingHandler);
    }

    /**
     * Removes a {@link ZigBeeThingHandler} from the map so it is not tracked anymore
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     */
    public void removeHandler(ZigBeeThingHandler zigBeeThingHandler) {
        cancelTask(zigBeeThingHandler);
        handlerIntervalMapping.remove(zigBeeThingHandler);
    }

    /**
     * Reset the timer for a {@link ZigBeeThingHandler}, i.e. expressing that some communication has just occurred
     *
     * @param zigBeeThingHandler the {@link ZigBeeThingHandler}
     */
    public void resetTimer(ZigBeeThingHandler zigBeeThingHandler) {
        logger.debug("Reset timeout for handler with thingUID={}", zigBeeThingHandler.getThing().getUID());
        cancelTask(zigBeeThingHandler);
        scheduleTask(zigBeeThingHandler);
    }

    private void scheduleTask(ZigBeeThingHandler handler) {
        ScheduledFuture<?> existingTask = scheduledTasks.get(handler);
        if (existingTask == null) {
            int interval = handlerIntervalMapping.get(handler);
            logger.debug("Scheduling timeout task for thingUID={} in {} seconds",
                    handler.getThing().getUID().getAsString(), interval);
            ScheduledFuture<?> task = scheduler.schedule(() -> {
                logger.debug("Timeout has been reached for thingUID={}", handler.getThing().getUID().getAsString());
                handler.aliveTimeoutReached();
                scheduledTasks.remove(handler);
            }, interval, TimeUnit.SECONDS);

            scheduledTasks.put(handler, task);
        }
    }

    private void cancelTask(ZigBeeThingHandler handler) {
        ScheduledFuture<?> task = scheduledTasks.get(handler);
        if (task != null) {
            logger.debug("Canceling timeout task for thingUID={}", handler.getThing().getUID().getAsString());
            task.cancel(true);
            scheduledTasks.remove(handler);
        }
    }
}
