package org.openhab.binding.elkm1.discovery;

import java.util.Map;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.elkm1.ElkM1BindingConstants;
import org.openhab.binding.elkm1.handler.ElkM1BridgeHandler;
import org.openhab.binding.elkm1.internal.ElkM1HandlerListener;
import org.openhab.binding.elkm1.internal.elk.ElkTypeToRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/**
 * Sets up the discovery results and details from the elk m1 when it is found.
 *
 * @author David Bennett - Initial Contribution
 */
public class ElkM1DiscoveryHandler extends AbstractDiscoveryService implements ElkM1HandlerListener {
    private Logger logger = LoggerFactory.getLogger(ElkM1DiscoveryHandler.class);

    private ElkM1BridgeHandler bridge;

    public ElkM1DiscoveryHandler(ElkM1BridgeHandler bridge) throws IllegalArgumentException {
        super(60);
        this.bridge = bridge;
    }

    @Override
    public void activate(Map<String, Object> configProperties) {
        logger.info("Activate!");
        super.activate(configProperties);
        this.bridge.addListener(this);
    }

    @Override
    public void deactivate() {
        logger.info("Deactivate!");
        super.deactivate();
        this.bridge.removeListener(this);
    }

    @Override
    public void onZoneDiscovered(int areaNum, String label) {
        logger.info("Zone discovered {} {}", areaNum, label);
        ThingUID thingUID = new ThingUID(ElkM1BindingConstants.THING_TYPE_ZONE, bridge.getThing().getUID(),
                Integer.toString(areaNum));
        Map<String, Object> properties = Maps.newHashMap();
        properties.put(ElkM1BindingConstants.PROPERTY_TYPE_ID, ElkTypeToRequest.Zone.toString());
        properties.put(ElkM1BindingConstants.PROPERTY_ZONE_NUM, Integer.toString(areaNum));
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridge.getThing().getUID())
                .withLabel(label).withProperties(properties).build();
        thingDiscovered(result);
    }

    @Override
    protected void startScan() {
        this.bridge.startScan();
    }

    @Override
    public void onAreaDiscovered(int areaNum, String label) {
        logger.info("Area discovered {} {}", areaNum, label);
        ThingUID thingUID = new ThingUID(ElkM1BindingConstants.THING_TYPE_AREA, bridge.getThing().getUID(),
                Integer.toString(areaNum));
        Map<String, Object> properties = Maps.newHashMap();
        properties.put(ElkM1BindingConstants.PROPERTY_TYPE_ID, ElkTypeToRequest.Zone.toString());
        properties.put(ElkM1BindingConstants.PROPERTY_ZONE_NUM, Integer.toString(areaNum));
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridge.getThing().getUID())
                .withLabel(label).withProperties(properties).build();
        thingDiscovered(result);

    }

}
