package org.openhab.binding.insteonplm.internal.device.messages;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Channel;
import org.openhab.binding.insteonplm.handler.InsteonThingHandler;
import org.openhab.binding.insteonplm.internal.device.DeviceFeature;
import org.openhab.binding.insteonplm.internal.device.MessageHandler;
import org.openhab.binding.insteonplm.internal.message.FieldException;
import org.openhab.binding.insteonplm.internal.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Daniel Pfrommer
 * @author Bernd Pfrommer
 */
public class HiddenDoorSensorDataBatteryWatermarkHandler extends MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(HiddenDoorSensorDataBatteryWatermarkHandler.class);

    HiddenDoorSensorDataBatteryWatermarkHandler(DeviceFeature p) {
        super(p);
    }

    @Override
    public void handleMessage(InsteonThingHandler handler, int group, byte cmd1, Message msg, Channel f) {
        if (!msg.isExtended()) {
            logger.trace("{} device {} ignoring non-extended msg {}", nm(), handler.getAddress(), msg);
            return;
        }
        try {
            int cmd2 = msg.getByte("command2") & 0xff;
            switch (cmd2) {
                case 0x00: // this is a product data response message
                    int batteryLevel = msg.getByte("userData4") & 0xff;
                    int batteryWatermark = msg.getByte("userData7") & 0xff;
                    logger.debug("{}: {} got light level: {}, battery level: {}", nm(), handler.getAddress(),
                            batteryWatermark, batteryLevel);
                    handler.updateFeatureState(f, new DecimalType(batteryWatermark));
                    break;
                default:
                    logger.warn("unknown cmd2 = {} in info reply message {}", cmd2, msg);
                    break;
            }
        } catch (FieldException e) {
            logger.error("error parsing {}: ", msg, e);
        }
    }
}