package org.aion.types;

import java.math.BigInteger;
import org.aion.fastvm.FvmDataWord;
import org.junit.Assert;
import org.junit.Test;

public class FvmDataWordTest {

    @Test(expected = IllegalArgumentException.class)
    public void testFromBytesUsingByteArrayGreaterThanLength16() {
        FvmDataWord.fromBytes(new byte[17]);
    }

    @Test
    public void testFromBytesUsingByteArrayLessThanLength16() {
        byte[] bytes = new byte[]{ 0x7, 0x3, 0xa, 0xc, 0x2 };

        byte[] paddedBytes = new byte[16];
        System.arraycopy(bytes, 0, paddedBytes, 16 - bytes.length, bytes.length);

        FvmDataWord dataWord = FvmDataWord.fromBytes(bytes);
        Assert.assertArrayEquals(paddedBytes, dataWord.copyOfData());
    }

    @Test
    public void testFromBytesUsingByteArrayLength16() {
        byte[] bytes = new byte[]{ 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf };
        FvmDataWord dataWord = FvmDataWord.fromBytes(bytes);
        Assert.assertArrayEquals(bytes, dataWord.copyOfData());
    }

    @Test
    public void testFromLongToLong() {
        long value = 216_812_323_238_679_182L;
        FvmDataWord dataWord = FvmDataWord.fromLong(value);
        Assert.assertEquals(value, dataWord.toLong());
    }

    @Test
    public void testFromIntToInt() {
        int value = 1_612_398_741;
        FvmDataWord dataWord = FvmDataWord.fromInt(value);
        Assert.assertEquals(value, dataWord.toInt());
    }

    @Test
    public void testFromBigIntegerToBigInteger() {
        BigInteger value = BigInteger.valueOf(32_986_523_235L);
        FvmDataWord dataWord = FvmDataWord.fromBigInteger(value);
        Assert.assertEquals(value, dataWord.toBigInteger());
    }

    @Test
    public void testEqualityOfDataWords() {
        int value = 1_231_984_345;

        FvmDataWord dataWordFromInt = FvmDataWord.fromInt(value);
        FvmDataWord dataWordFromLong = FvmDataWord.fromLong(value);
        FvmDataWord dataWordFromBigInteger = FvmDataWord.fromBigInteger(BigInteger.valueOf(value));

        Assert.assertEquals(dataWordFromInt, dataWordFromLong);
        Assert.assertEquals(dataWordFromInt, dataWordFromBigInteger);
    }
}
