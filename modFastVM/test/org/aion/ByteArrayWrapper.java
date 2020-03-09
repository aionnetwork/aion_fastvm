package org.aion;

import java.util.Arrays;
import org.aion.fastvm.util.HexUtil;

public final class ByteArrayWrapper {
    private final byte[] bytes;

    public ByteArrayWrapper(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public byte[] copyOfBytes() {
        return Arrays.copyOf(this.bytes, this.bytes.length);
    }

    @Override
    public String toString() {
        return "ByteArrayWrapper { " + HexUtil.toHexString(this.bytes) + " }";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ByteArrayWrapper)) {
            return false;
        } else if (other == this) {
            return true;
        }

        ByteArrayWrapper otherWrapper = (ByteArrayWrapper) other;
        return Arrays.equals(this.bytes, otherWrapper.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }
}
