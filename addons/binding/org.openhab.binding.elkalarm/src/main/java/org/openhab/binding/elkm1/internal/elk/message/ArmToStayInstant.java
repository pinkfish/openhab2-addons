package org.openhab.binding.elkm1.internal.elk.message;

import org.openhab.binding.elkm1.internal.elk.ElkCommand;
import org.openhab.binding.elkm1.internal.elk.ElkMessage;

/**
 * The arm away class, to put the elk into armed away mode.
 *
 * @author David Bennett - Initial Contribution
 *
 */
public class ArmToStayInstant extends ElkMessage {
    private final int area;
    private final String pincode;

    public ArmToStayInstant(int area, String pincode) {
        super(ElkCommand.ArmToStayInstant);
        this.area = area;
        if (area > 8) {
            area = 8;
        }
        if (area < 0) {
            area = 0;
        }
        if (pincode.length() == 4) {
            pincode = "00" + pincode;
        }
        this.pincode = pincode;
    }

    @Override
    public String getData() {
        return area + pincode;
    }
}
