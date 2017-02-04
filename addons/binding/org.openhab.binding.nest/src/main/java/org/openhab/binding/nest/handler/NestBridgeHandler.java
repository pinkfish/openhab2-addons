package org.openhab.binding.nest.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.openhab.binding.nest.NestBindingConstants;
import org.openhab.binding.nest.config.NestBridgeConfiguration;
import org.openhab.binding.nest.discovery.NestDiscoveryService;
import org.openhab.binding.nest.internal.NestAccessToken;
import org.openhab.binding.nest.internal.NestDeviceAddedListener;
import org.openhab.binding.nest.internal.NestUpdateRequest;
import org.openhab.binding.nest.internal.data.Camera;
import org.openhab.binding.nest.internal.data.NestDevices;
import org.openhab.binding.nest.internal.data.Thermostat;
import org.openhab.binding.nest.internal.data.TopLevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class NestBridgeHandler extends BaseBridgeHandler {

    private Logger logger = LoggerFactory.getLogger(NestThermostatHandler.class);

    private List<NestDeviceAddedListener> listeners = new ArrayList<NestDeviceAddedListener>();

    // Will refresh the data each time it runs.
    private Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            refreshData();
        }
    };

    private ScheduledFuture<?> pollingJob;
    private NestAccessToken accessToken;
    private GsonBuilder builder = new GsonBuilder();
    private List<NestUpdateRequest> nestUpdateRequests = new ArrayList<>();

    public NestBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        logger.debug("Initialize the Nest bridge handler");

        NestBridgeConfiguration config = getConfigAs(NestBridgeConfiguration.class);
        startAutomaticRefresh(config.refreshInterval);
        accessToken = new NestAccessToken(config);
    }

    @Override
    public void dispose() {
        logger.debug("Nest bridge disposed");
        stopAutomaticRefresh();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub
        if (command instanceof RefreshType) {
            logger.debug("Refresh command received");
            refreshData();
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler handler, Thing thing) {
        // Called when a new thing is created.
    }

    @Override
    public void childHandlerDisposed(ThingHandler handler, Thing thing) {
        // Called when a thing is disposed.
    }

    /**
     * Read the data from nest and then parse it into something useful.
     */
    private void refreshData() {
        NestBridgeConfiguration config = getConfigAs(NestBridgeConfiguration.class);
        try {
            String uri = buildQueryString(config);
            String data = jsonFromGetUrl(uri);
            // Now convert the incoming data into something more useful.
            Gson gson = builder.create();
            TopLevelData newData = gson.fromJson(data, TopLevelData.class);
            // Turn this new data into things and stuff.
            compareThings(newData.getDevices());
        } catch (URIException e) {
            logger.error("Error parsing nest url", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            logger.error("Error connecting to nest", e);
        }

    }

    private Thing getDevice(String deviceId, List<Thing> things) {
        for (Thing thing : things) {
            String thingDeviceId = thing.getProperties().get(NestBindingConstants.PROPERTY_ID);
            if (thingDeviceId.equals(deviceId)) {
                return thing;
            }
        }
        return null;
    }

    private void compareThings(NestDevices devices) {
        Bridge bridge = getThing();

        List<Thing> things = bridge.getThings();

        for (Thermostat thermostat : devices.getThermostats().values()) {
            Thing thingThermostat = getDevice(thermostat.getDeviceId(), things);
            if (thingThermostat != null) {
                NestThermostatHandler handler = (NestThermostatHandler) thingThermostat.getHandler();
                handler.updateThermostat(thermostat);
            } else {
                for (NestDeviceAddedListener listener : listeners) {
                    listener.onThermostatAdded(thermostat);
                }
            }
        }
        for (Camera camera : devices.getCameras().values()) {
            Thing thingCamera = getDevice(camera.getDeviceId(), things);
            if (thingCamera != null) {
                NestCameraHandler handler = (NestCameraHandler) thingCamera.getHandler();
                handler.updateCamera(camera);
            } else {
                for (NestDeviceAddedListener listener : listeners) {
                    listener.onCameraAdded(camera);
                }
            }
        }
    }

    private String buildQueryString(NestBridgeConfiguration config) throws URIException, IOException {
        StringBuilder urlBuilder = new StringBuilder(NestBindingConstants.NEST_URL);
        urlBuilder.append("?auth=");
        urlBuilder.append(accessToken.getAccessToken());
        return URIUtil.encodeQuery(urlBuilder.toString());
    }

    private String jsonFromGetUrl(final String url) throws IOException {
        logger.debug("connecting to " + url);
        return HttpUtil.executeUrl(HttpMethod.GET.toString(), url, 120);
    }

    private synchronized void startAutomaticRefresh(int refreshInterval) {
        if (pollingJob == null || pollingJob.isCancelled()) {
            pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, 0, refreshInterval, TimeUnit.SECONDS);
        }
    }

    private synchronized void stopAutomaticRefresh() {
        if (pollingJob != null && !pollingJob.isCancelled()) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
    }

    public void addDeviceAddedListener(NestDeviceAddedListener nestDiscoveryService) {
        this.listeners.add(nestDiscoveryService);
    }

    public void removeDeviceAddedListener(NestDiscoveryService nestDiscoveryService) {
        this.listeners.remove(nestDiscoveryService);
    }

    /** Adds the update request into the queue for doing something with, send immedigately if the queue is empty. */
    public void addUpdateRequest(NestUpdateRequest request) {
        nestUpdateRequests.add(request);
    }
}