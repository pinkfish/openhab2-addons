/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.insteonplm.handler;

import static org.openhab.binding.insteonplm.InsteonPLMBindingConstants.CHANNEL_1;

import java.io.IOException;
import java.util.Map;
import java.util.PriorityQueue;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.insteonplm.internal.config.InsteonPLMBridgeConfiguration;
import org.openhab.binding.insteonplm.internal.device.DeviceFeatureFactory;
import org.openhab.binding.insteonplm.internal.device.InsteonAddress;
import org.openhab.binding.insteonplm.internal.driver.IOStream;
import org.openhab.binding.insteonplm.internal.driver.MessageListener;
import org.openhab.binding.insteonplm.internal.driver.Port;
import org.openhab.binding.insteonplm.internal.driver.SerialIOStream;
import org.openhab.binding.insteonplm.internal.driver.TcpIOStream;
import org.openhab.binding.insteonplm.internal.driver.hub.HubIOStream;
import org.openhab.binding.insteonplm.internal.message.FieldException;
import org.openhab.binding.insteonplm.internal.message.Message;
import org.openhab.binding.insteonplm.internal.message.MessageFactory;
import org.openhab.binding.insteonplm.internal.message.ModemMessageType;
import org.openhab.binding.insteonplm.internal.message.modem.AllLinkRecordResponse;
import org.openhab.binding.insteonplm.internal.message.modem.BaseModemMessage;
import org.openhab.binding.insteonplm.internal.message.modem.GetFirstAllLinkingRecord;
import org.openhab.binding.insteonplm.internal.message.modem.GetIMInfo;
import org.openhab.binding.insteonplm.internal.message.modem.GetNextAllLinkingRecord;
import org.openhab.binding.insteonplm.internal.message.modem.StandardMessageReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link InsteonPLMBridgeHandler} is responsible for dealing with talking to the serial
 * port, finding the insteon devices and co-ordinating calls with the internal things.
 *
 * @author David Bennett - Initial contribution
 */
public class InsteonPLMBridgeHandler extends BaseBridgeHandler implements MessageListener {
    private Logger logger = LoggerFactory.getLogger(InsteonPLMBridgeHandler.class);
    private DeviceFeatureFactory deviceFeatureFactory;
    private MessageFactory messageFactory;
    private IOStream ioStream;
    private Port port;
    private PriorityQueue<InsteonBridgeThingQEntry> messagesToSend = new PriorityQueue<>();
    private Thread messageQueueThread;
    private InsteonAddress modemAddress;
    private boolean doingLinking = false;

    public InsteonPLMBridgeHandler(Bridge thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(CHANNEL_1)) {
            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    @Override
    public void initialize() {
        // Connect to the port.
        deviceFeatureFactory = new DeviceFeatureFactory();

        startupPort();
    }

    @Override
    public void dispose() {
        super.dispose();
        if (this.port != null) {
            this.port.stop();
        }
        this.port = null;
        if (this.ioStream != null) {
            this.ioStream.close();
        }
        this.ioStream = null;
        this.deviceFeatureFactory = null;
        this.messageFactory = null;
        this.messagesToSend.clear();
        this.messagesToSend = null;
        if (this.messageQueueThread != null) {
            this.messageQueueThread.interrupt();
        }
        this.messageQueueThread = null;
    }

    private void startupPort() {
        InsteonPLMBridgeConfiguration config = getConfigAs(InsteonPLMBridgeConfiguration.class);
        logger.error("config {}", config);

        switch (config.getPortType()) {
            case Hub:
                ioStream = new HubIOStream(config);
                break;
            case SerialPort:
                ioStream = new SerialIOStream(config);
                if (!ioStream.open()) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Unable to open port " + config.getSerialPort());
                    return;
                }
                break;
            case Tcp:
                ioStream = new TcpIOStream(config);
                break;
            default:
                logger.error("Invalid type of port for insteon plm.");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Invalid type of port");
                return;
        }
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Connecting to port");
        port = new Port(ioStream, messageFactory);
        port.addListener(this);
        modemAddress = null;
        if (port.start()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Port Started");
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Unable to start communicating on " + config.getSerialPort());
        }

        // Start downloading the link db.
        try {
            port.writeMessage(new GetIMInfo());
        } catch (IOException e) {
            logger.error("error sending link record query ", e);
        }

    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        // Stop the port then restart it to pick up the new configuration.
        if (this.port != null) {
            this.port.stop();
        }
        startupPort();
    }

    @Override
    public void processMessage(BaseModemMessage message) {
        // Got a message, go online. Yay!
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE);
            messageQueueThread = new Thread(new RequestQueueReader());
            messageQueueThread.start();
        }
        Bridge bridge = getThing();
        switch (message.getMessageType()) {
            case StandardMessageReceived:
                StandardMessageReceived messReceived = (StandardMessageReceived) message;
                // Go and find the device to send this message to.
                // Send the message to all the handlers. This is a little inefficent, leave it for now.
                for (Thing thing : bridge.getThings()) {
                    if (thing.getHandler() instanceof InsteonThingHandler) {
                        InsteonThingHandler handler = (InsteonThingHandler) thing.getHandler();
                        // Only send on messages to that specific thing.
                        if (messReceived.getFromAddress().equals(handler.getAddress())
                                || messReceived.getToAddress().equals(handler.getAddress())) {
                            try {
                                handler.handleMessage(messReceived);
                            } catch (FieldException e) {
                                logger.error("Error handling message {}", message, e);
                            }
                        }
                    }
                }
                break;
            case GetImInfo:
                GetIMInfo info = (GetIMInfo) message;
                // add the modem to the device list
                modemAddress = info.getModemAddress();
                logger.info("Found the modem address {}", modemAddress);
                try {
                    port.writeMessage(new GetFirstAllLinkingRecord());
                    doingLinking = true;
                } catch (IOException e) {
                    logger.error("Unable to send first linking message");
                }
                return;
            case AllLinkRecordResponse:
                AllLinkRecordResponse response = (AllLinkRecordResponse) message;
                // Found a device msg.getAddress("LinkAddr")
                logger.error("Found device {}", response.getAddress());
                // Send a request for the next one.
                try {
                    port.writeMessage(new GetNextAllLinkingRecord());
                } catch (IOException e) {
                    logger.error("Unable to send next all link record");
                }
                return;
            case PureNack:
                // Explicit nack.
                logger.info("Pure nack recieved.");
                break;
            case GetFirstAllLinkRecord:
            case GetNextAllLinkRecord:
                if (message.isNack()) {
                    logger.debug("got all link records.");
                    doingLinking = false;
                }
                return;
            default:
                logger.warn("Unhandled insteon message {}", message.getMessageType());
                break;
        }
    }

    private boolean handleLinkingMessages(Message message) throws FieldException {
        switch (ModemMessageType.fromCommand(message.getByte("Cmd"))) {
            case AllLinkRecordResponse:
                // Found a device msg.getAddress("LinkAddr")
                logger.error("Found device {}", message.getAddress("LinkAddr"));
                // Send a request for the next one.
                try {
                    port.writeMessage(messageFactory.makeMessage("GetNextALLLinkRecord"));
                } catch (IOException e) {
                    logger.error("Unable to send next all link record");
                }
                return true;
            case PureNack:
                // Explicit nack.
                logger.info("Pure nack recieved.");
                break;
            case GetFirstAllLinkRecord:
            case GetNextAllLinkRecord:
                if (message.getByte("ACK/NACK") == ModemMessageType.PureNack.getCommand()) {
                    logger.debug("got all link records.");
                    doingLinking = false;
                }
                return true;
            default:
                break;
        }
        return false;
    }

    private void startLinking() {

    }

    private void handleAllLinkDatabaseResponse(Message message) {
        // Pull the data out of the all link record.

    }

    /** Gets the thing associated with this address. */
    public InsteonThingHandler getDevice(InsteonAddress a) {
        for (Thing thing : getBridge().getThings()) {
            if (thing.getHandler() instanceof InsteonThingHandler) {
                InsteonThingHandler handler = (InsteonThingHandler) thing.getHandler();
                if (handler.getAddress().equals(a)) {
                    return handler;
                }
            }
        }
        return null;
    }

    public void startScan() {
        // Create a message to query the modem for devices.
    }

    /** The factory to make device features. */
    public DeviceFeatureFactory getDeviceFeatureFactory() {
        return deviceFeatureFactory;
    }

    /** The message factory to use when making messages. */
    public MessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * Add device to global request queue.
     *
     * @param dev the device to add
     * @param time the time when the queue should be processed
     */
    public void addThingToSendingQueue(InsteonThingHandler handler, long time) {
        synchronized (messagesToSend) {
            // See if we can find the entry first.
            for (InsteonBridgeThingQEntry entry : messagesToSend) {
                if (entry.getThingHandler() == handler) {
                    long expTime = entry.getExpirationTime();
                    if (expTime > time) {
                        entry.setExpirationTime(time);
                    }
                    messagesToSend.remove(entry);
                    messagesToSend.add(entry);
                    messagesToSend.notify();
                    logger.trace("updating request for device {} in {} msec", handler.getAddress(),
                            time - System.currentTimeMillis());
                    return;
                }
            }
            logger.trace("scheduling request for device {} in {} msec", handler.getAddress(),
                    time - System.currentTimeMillis());
            InsteonBridgeThingQEntry entry = new InsteonBridgeThingQEntry(handler, time);
            // add the queue back in after (maybe) having modified
            // the expiration time
            messagesToSend.add(entry);
            messagesToSend.notify();
        }
    }

    class RequestQueueReader implements Runnable {
        @Override
        public void run() {
            logger.debug("starting request queue thread");
            // Run while we are online.
            while (getThing().getStatus() == ThingStatus.ONLINE) {
                InsteonBridgeThingQEntry entry = messagesToSend.peek();
                try {
                    if (messagesToSend.size() > 0) {
                        long now = System.currentTimeMillis();
                        long expTime = entry.getExpirationTime();
                        if (expTime > now) {
                            //
                            // The head of the queue is not up for processing yet, wait().
                            //
                            logger.trace("request queue head: {} must wait for {} msec",
                                    entry.getThingHandler().getAddress(), expTime - now);
                            //
                            // note that the wait() can also return because of changes to
                            // the queue, not just because the time expired!
                            //
                            continue;
                        }
                        //
                        // The head of the queue has expired and can be processed!
                        //
                        entry = messagesToSend.poll(); // remove front element
                        long nextExp = entry.getThingHandler().processRequestQueue(now);
                        if (nextExp > 0) {
                            InsteonBridgeThingQEntry newEntry = new InsteonBridgeThingQEntry(entry.getThingHandler(),
                                    nextExp);
                            messagesToSend.add(newEntry);
                            logger.trace("device queue for {} rescheduled in {} msec",
                                    entry.getThingHandler().getAddress(), nextExp - now);
                        } else {
                            // remove from hash since queue is no longer scheduled
                            logger.debug("device queue for {} is empty!", entry.getThingHandler().getAddress());
                        }
                    }
                    logger.trace("waiting for request queues to fill");
                    messagesToSend.wait();
                } catch (InterruptedException e) {
                    logger.error("request queue thread got interrupted, breaking..", e);
                    break;
                }
            }
            logger.error("exiting request queue thread");
        }
    }
}