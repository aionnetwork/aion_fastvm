package org.aion.util;

import java.io.IOException;
import java.io.OutputStream;

public final class HexEncoder {
    private static final byte[] ENCODING_TABLE = {
        (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6',
        (byte) '7',
        (byte) '8', (byte) '9', (byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd', (byte) 'e',
        (byte) 'f'
    };

    private static final byte[] DECODING_TABLE = new byte[128];

    static {
        initializeDecodingTable();
    }

    private static void initializeDecodingTable() {
        for (int i = 0; i < DECODING_TABLE.length; i++) {
            DECODING_TABLE[i] = (byte) 0xff;
        }

        for (int i = 0; i < ENCODING_TABLE.length; i++) {
            DECODING_TABLE[ENCODING_TABLE[i]] = (byte) i;
        }

        DECODING_TABLE['A'] = DECODING_TABLE['a'];
        DECODING_TABLE['B'] = DECODING_TABLE['b'];
        DECODING_TABLE['C'] = DECODING_TABLE['c'];
        DECODING_TABLE['D'] = DECODING_TABLE['d'];
        DECODING_TABLE['E'] = DECODING_TABLE['e'];
        DECODING_TABLE['F'] = DECODING_TABLE['f'];
    }

    /**
     * encode the input data producing a Hex output stream.
     *
     * @return the number of bytes produced.
     */
    public static int encode(byte[] data, int off, int length, OutputStream out) throws IOException {
        for (int i = off; i < (off + length); i++) {
            int v = data[i] & 0xff;
            out.write(ENCODING_TABLE[(v >>> 4)]);
            out.write(ENCODING_TABLE[v & 0xf]);
        }
        return length * 2;
    }

    /**
     * decode the Hex encoded String data writing it to the given output stream, whitespace
     * characters will be ignored.
     *
     * @return the number of bytes produced.
     */
    public static int decode(String data, OutputStream out) throws IOException {
        byte b1, b2;
        int length = 0;

        int end = data.length();

        while (end > 0) {
            if (!ignore(data.charAt(end - 1))) {
                break;
            }

            --end;
        }

        int i = 0;
        while (i < end) {
            while (i < end && ignore(data.charAt(i))) {
                ++i;
            }

            b1 = DECODING_TABLE[data.charAt(i++)];

            while (i < end && ignore(data.charAt(i))) {
                ++i;
            }

            b2 = DECODING_TABLE[data.charAt(i++)];

            if ((b1 | b2) < 0) {
                throw new IllegalArgumentException("invalid characters encountered in Hex string");
            }

            out.write((b1 << 4) | b2);

            ++length;
        }

        return length;
    }

    private static boolean ignore(char c) {
        return c == '\n' || c == '\r' || c == '\t' || c == ' ';
    }
}
