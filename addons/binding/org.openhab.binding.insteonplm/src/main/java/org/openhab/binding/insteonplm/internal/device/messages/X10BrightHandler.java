package org.openhab.binding.insteonplm.internal.device.messages;

import org.eclipse.smarthome.core.thing.Channel;
import org.openhab.binding.insteonplm.handler.InsteonThingHandler;
import org.openhab.binding.insteonplm.internal.device.DeviceFeature;
import org.openhab.binding.insteonplm.internal.device.InsteonAddress;
import org.openhab.binding.insteonplm.internal.device.MessageHandler;
import org.openhab.binding.insteonplm.internal.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Daniel Pfrommer
 * @author Bernd Pfrommer
 */
public class X10BrightHandler extends MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(X10BrightHandler.class);

    X10BrightHandler(DeviceFeature p) {
        super(p);
    }

    @Override
    public void handleMessage(InsteonThingHandler handler, int group, byte cmd1, Message msg, Channel f) {
        InsteonAddress a = handler.getAddress();
        logger.debug("{}: ignoring brighten message for device {}", nm(), a);
    }
}