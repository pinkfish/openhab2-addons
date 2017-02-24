package org.openhab.binding.elkm1.internal.elk;

import org.openhab.binding.elkm1.internal.elk.message.VersionReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElkMessageFactory {
    private final Logger logger = LoggerFactory.getLogger(ElkMessageFactory.class);

    public ElkMessage createMessage(String input) {
        ElkData data = new ElkData(input);
        if (!verifyCrc(data)) {
            return null;
        }
        // Figure out the elk message to create.
        switch (input.substring(2, 4)) {
            case "VN":
                return new VersionReply(input);
        }
        return null;
    }

    private boolean verifyCrc(ElkData data) {
        // First two chars is length.
        logger.debug("Checksum val {}  {}", data.getChecksum(), data.getCalculatedChecksum());
        return data.getChecksum() == data.getCalculatedChecksum();
    }

    class ElkData {
        private final int length;
        private final int checksum;
        private final String command;
        private final String data;
        private final int calculatedChecksum;

        ElkData(String input) {
            length = Integer.valueOf(input.substring(0, 2), 16);
            logger.error("Len: {}, str len: {}", length, input.length());
            if (length + 2 < input.length()) {
                checksum = -1;
                calculatedChecksum = 0;
                command = "  ";
                data = "";
            } else {
                if (!input.substring(length - 2, length).equals("00")) {
                    checksum = -1;
                    calculatedChecksum = 0;
                } else {
                    checksum = Integer.valueOf(input.substring(length, length + 2), 16);
                    calculatedChecksum = calculateChecksum(input, length);
                }
                command = input.substring(2, 4);
                // Last two bits should just be 00
                data = input.substring(4, length - 2);
            }
            logger.error("Len: {}, checksum: {}, command: {}, data: {}", length, checksum, command, data);
        }

        public int getLength() {
            return length;
        }

        public int getChecksum() {
            return checksum;
        }

        public int getCalculatedChecksum() {
            return calculatedChecksum;
        }

        public String getCommand() {
            return command;
        }

        public String getData() {
            return data;
        }

        private int calculateChecksum(String input, int len) {
            int checksum = 0;

            for (char ch : input.substring(0, len).toCharArray()) {
                checksum += ch;
            }
            logger.error("checksum cal: {}", (~checksum + 1) & 0xff);
            return (~checksum + 1) & 0xff;
        }
    }
}
