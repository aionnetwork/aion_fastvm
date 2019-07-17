package org.aion.fastvm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.aion.util.conversions.Hex;

/**
 * A data word implementation consisting of 16 bytes.
 */
public final class FvmDataWord {
    public static final int SIZE = 16;
    private final byte[] data;

    private FvmDataWord(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("Cannot create data word using null bytes.");
        }

        if (bytes.length == SIZE) {
            this.data = Arrays.copyOf(bytes, bytes.length);
        } else if (bytes.length < SIZE) {
            this.data = rightPadBytesWithZeroes(bytes);
        } else {
            throw new IllegalArgumentException("Data word length cannot exceed 16 bytes!");
        }
    }

    /**
     * Returns a new data word that wraps the given bytes. If the length of the given bytes is less
     * than 16 they will be right-padded with zero bytes.
     *
     * @param bytes The bytes to wrap.
     * @return the data word.
     */
    public static FvmDataWord fromBytes(byte[] bytes) {
        return new FvmDataWord(bytes);
    }

    /**
     * Returns a new data word whose underlying byte array consists of the bytes of the big integer.
     *
     * @param bigInteger The big integer.
     * @return the data word.
     */
    public static FvmDataWord fromBigInteger(BigInteger bigInteger) {
        // NOTE: DataWordImpl.value() produces a signed positive BigInteger. The byte array
        // representation of such a number must prepend a zero byte so that this can be decoded
        // correctly. This means that a 16-byte array with a non-zero starting bit will become 17
        // bytes when BigInteger::toByteArray is called, and therefore we must remove any leading
        // zero bytes from this representation for full compatibility.
        return new FvmDataWord(removeLargeBigIntegerLeadingZeroByte(bigInteger));
    }

    /**
     * Returns a new data word whose underlying byte array consists of 8 zero bytes (in the
     * left-most bytes) followed by the 8 bytes of the given long.
     *
     * @param number The long.
     * @return the data word.
     */
    public static FvmDataWord fromLong(long number) {
        return new FvmDataWord(ByteBuffer.allocate(SIZE).position(8).putLong(number).array());
    }

    /**
     * Returns a new data word whose underlying byte array consists of 12 zero bytes (in the
     * left-most bytes) followed by the 8 bytes of the given int.
     *
     * @param number The int.
     * @return the data word.
     */
    public static FvmDataWord fromInt(int number) {
        return new FvmDataWord(ByteBuffer.allocate(SIZE).position(12).putInt(number).array());
    }

    /**
     * Returns a copy of the underlying byte array.
     *
     * @return the underlying bytes.
     */
    public byte[] copyOfData() {
        return Arrays.copyOf(this.data, this.data.length);
    }

    /**
     * Returns the underlying byte array interpreted as an integer. The 4 right-most bytes are the
     * only bytes interpreted as an integer.
     *
     * @return the value.
     */
    public int toInt() {
        return ByteBuffer.wrap(this.data).flip().limit(SIZE).position(12).getInt();
    }

    /**
     * Returns the underlying byte array interpreted as a long. The 8 right-most bytes are the only
     * bytes interpreted as a long.
     *
     * @return the value.
     */
    public long toLong() {
        return ByteBuffer.wrap(this.data).flip().limit(SIZE).position(8).getLong();
    }

    /**
     * Returns the underlying byte array as a positively-signed {@link BigInteger}.
     *
     * @return the big integer.
     */
    public BigInteger toBigInteger() {
        return new BigInteger(1, this.data);
    }

    @Override
    public String toString() {
        return Hex.toHexString(this.data);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FvmDataWord)) {
            return false;
        } else if (other == this) {
            return true;
        }

        FvmDataWord otherWord = (FvmDataWord) other;
        return Arrays.equals(this.data, otherWord.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    /**
     * Similar to {@code stripLeadingZeroes} but more specialized to be more efficient in a specific
     * necessary situation.
     *
     * <p>Essentially this method will always return {@code number.toByteArray()} UNLESS this byte
     * array is length {@value SIZE} + 1 (that is, 17), and its initial byte is a zero byte. In
     * this single case, the leading zero byte will be stripped.
     *
     * @param number The {@link BigInteger} whose byte array representation is to be possibly truncated.
     * @return The re-formatted {@link BigInteger#toByteArray()} representation as here specified.
     */
    private static byte[] removeLargeBigIntegerLeadingZeroByte(BigInteger number) {
        byte[] bytes = number.toByteArray();
        boolean isLength17leadingZero = ((bytes.length == (FvmDataWord.SIZE + 1)) && (bytes[0] == 0x0));
        return (isLength17leadingZero) ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static byte[] rightPadBytesWithZeroes(byte[] bytes) {
        byte[] paddedBytes = new byte[SIZE];
        System.arraycopy(bytes, 0, paddedBytes, SIZE - bytes.length, bytes.length);
        return paddedBytes;
    }
}
