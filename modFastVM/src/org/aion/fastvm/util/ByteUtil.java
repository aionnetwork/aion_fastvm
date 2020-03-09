package org.aion.fastvm.util;

import java.math.BigInteger;
import java.util.Arrays;

public final class ByteUtil {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * Returns an array that is equal to all of the given input arrays merged together.
     *
     * @param arrays The arrays to merge
     * @return the merged array
     */
    public static byte[] merge(byte[]... arrays) {
        int totalLengthOfAllArrays = 0;
        for (byte[] array : arrays) {
            totalLengthOfAllArrays += array.length;
        }

        // Create new array and copy all array contents
        byte[] mergedArray = new byte[totalLengthOfAllArrays];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, mergedArray, offset, array.length);
            offset += array.length;
        }
        return mergedArray;
    }

    public static byte[] bigIntegerToBytesSigned(BigInteger bigInteger, int numBytes) {
        if (bigInteger == null) {
            throw new NullPointerException("Cannot convert null big integer!");
        }
        byte[] bytes = new byte[numBytes];
        Arrays.fill(bytes, bigInteger.signum() < 0 ? (byte) 0xFF : 0x00);
        byte[] biBytes = bigInteger.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    public static BigInteger bytesToBigInteger(byte[] bb) {
        return bb.length == 0 ? BigInteger.ZERO : new BigInteger(1, bb);
    }

    public static byte[] stripLeadingZeroes(byte[] bytes) {
        int indexOfFirstNonZeroByte = indexOfFirstNonZeroByte(bytes);
        if (indexOfFirstNonZeroByte == bytes.length) {
            return new byte[1];
        }

        byte[] strippedBytes = new byte[bytes.length - indexOfFirstNonZeroByte];
        System.arraycopy(bytes, indexOfFirstNonZeroByte, strippedBytes, 0, bytes.length - indexOfFirstNonZeroByte);
        return strippedBytes;
    }

    private static int indexOfFirstNonZeroByte(byte[] bytes) {
        int index = 0;
        for (byte singleByte : bytes) {
            if (singleByte != 0x0) {
                return index;
            }
            index++;
        }
        return index;
    }
}
