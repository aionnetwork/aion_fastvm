package org.aion;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.fastvm.IExternalCapabilities;
import org.aion.types.AionAddress;

public final class ExternalCapabilitiesForTesting implements IExternalCapabilities {

    @Override
    public AionAddress computeNewContractAddress(AionAddress sender, BigInteger senderNonce) {
        byte[] bytes = mergeByteArrays(sender.toByteArray(), senderNonce.toByteArray());
        byte[] hash = xorWithSelf(bytes);
        return new AionAddress(padToLength32(hash));
    }

    @Override
    public byte[] hash256(byte[] payload) {
        byte[] hash = xorWithSelf(payload);
        return padToLength32(hash);
    }

    private static byte[] mergeByteArrays(byte[] array1, byte[] array2) {
        byte[] array = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, array, array1.length, array2.length);
        return array;
    }

    private static byte[] xorWithSelf(byte[] bytes) {
        int length = bytes.length;
        byte[] xor = new byte[length];

        for (int i = 0; i < length; i++) {
            int j = (i + 1 < length) ? i + 1 : 0;
            xor[i] = (byte) (bytes[i] ^ bytes[j]);
        }
        return xor;
    }

    private static byte[] padToLength32(byte[] bytes) {
        return Arrays.copyOf(bytes, 32);
    }
}
