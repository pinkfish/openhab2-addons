package org.openhab.binding.elkm1.internal.elk.message;

import org.openhab.binding.elkm1.internal.elk.ElkCommand;
import org.openhab.binding.elkm1.internal.elk.ElkMessage;
import org.openhab.binding.elkm1.internal.elk.ElkZoneConfig;
import org.openhab.binding.elkm1.internal.elk.ElkZoneStatus;

public class ZoneChangeUpdate extends ElkMessage {
    ElkZoneConfig config;
    ElkZoneStatus status;
    int zoneNumber;

    public ZoneChangeUpdate(String incomingData) {
        super(ElkCommand.ZoneChangeUpdateReport);

        String zoneNumberStr = incomingData.substring(0, 3);
        zoneNumber = Integer.valueOf(zoneNumberStr, 16);
        String statusStr = incomingData.substring(3, 4);
        int value = Byte.valueOf(statusStr, 16);
        switch (value & 0x03) {
            case 0:
                config = ElkZoneConfig.Unconfigured;
                break;
            case 1:
                config = ElkZoneConfig.Open;
                break;
            case 2:
                config = ElkZoneConfig.EOL;
                break;
            case 3:
                config = ElkZoneConfig.Short;
                break;
        }

        switch ((value >> 2) & 0x03) {
            case 0:
                status = ElkZoneStatus.Normal;
                break;
            case 1:
                status = ElkZoneStatus.Trouble;
                break;
            case 2:
                status = ElkZoneStatus.Violated;
                break;
            case 3:
                status = ElkZoneStatus.Bypassed;
                break;
        }
    }

    @Override
    protected String getData() {
        // TODO Auto-generated method stub
        return "";
    }

}
