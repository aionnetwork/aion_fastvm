package org.aion.fastvm.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class HexUtil {

    /**
     * Returns a hex string representing the input byte array.
     *
     * @param data The byte array to convert.
     * @return the hex string.
     */
    public static String toHexString(byte[] data) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            HexEncoder.encode(data, 0, data.length, stream);
            return new String(stream.toByteArray());
        } catch (IOException e) {
            // A method like this shouldn't be throwing an IOException, this should get refactored to
            // not rely on the stream to do this work, it's a strange exception to see.
            throw new IllegalArgumentException("Failed to convert bytes to hex string!");
        }
    }

    /**
     * Decodes the Hex encoded String data, ignoring any whitespace.
     *
     * @return a byte array representing the decoded data.
     */
    public static byte[] decode(String data) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            HexEncoder.decode(data, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            // A method like this shouldn't be throwing an IOException, this should get refactored to
            // not rely on the stream to do this work, it's a strange exception to see.
            throw new IllegalArgumentException("Failed to decode the hex string into bytes!");
        }
    }

    /**
     * Converts string hex representation to data bytes Accepts following hex: - with or without 0x
     * prefix - with no leading 0, like 0xabc v.s. 0x0abc
     *
     * @param data String like '0xa5e..' or just 'a5e..'
     * @return decoded bytes array
     */
    public static byte[] hexStringToBytes(String data) {
        if (data == null) {
            return new byte[0];
        }
        if (data.startsWith("0x")) {
            data = data.substring(2);
        }
        if ((data.length() & 1) == 1) {
            data = "0" + data;
        }
        return HexUtil.decode(data);
    }
}
