/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.vm;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.aion.base.type.Address;
import org.aion.mcf.vm.types.DataWord;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class ExecutionContextTest {
    private Address recipient, origin, caller, coinbase;
    private DataWord blockDifficulty, nrgPrice, callValue;
    private byte[] txHash, callData;
    private long nrgLimit, blockNumber, blockTimestamp, blockNrgLimit;
    private int depth, kind, flags;

    @Before
    public void setup() {
        recipient = new Address("1111111111111111111111111111111111111111111111111111111111111111");
        origin = new Address("2222222222222222222222222222222222222222222222222222222222222222");
        caller = new Address("3333333333333333333333333333333333333333333333333333333333333333");
        coinbase = new Address("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        blockDifficulty = new DataWord(Hex.decode("0000000000000000000000000000000f"));
        nrgPrice = new DataWord(Hex.decode("00000000000000000000000000000004"));
        callValue = new DataWord(Hex.decode("00000000000000000000000000000006"));
        txHash = RandomUtils.nextBytes(32);
        callData = new byte[]{0x07};
        depth = 8;
        kind = 9;
        flags = 10;
        nrgLimit = 5;
        blockNumber = 12;
        blockTimestamp = 13;
        blockNrgLimit = 14;
    }

    @After
    public void tearDown() {
        recipient = null;
        origin = null;
        caller = null;
        coinbase = null;
        blockDifficulty = null;
        nrgPrice = null;
        callValue = null;
        txHash = null;
        callData = null;
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullRecipient() {
        recipient = null;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullOrigin() {
        origin = null;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullCaller() {
        caller = null;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullCoinbase() {
        coinbase = null;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullBlockDifficulty() {
        blockDifficulty = null;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullNrgPrice() {
        nrgPrice = null;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullCallValue() {
        callValue = null;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testExecutionContextWithNullCallData() {
        callData = null;
        newExecutionContext();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecutionContextWithTxHashLessThan32Bytes() {
        txHash = new byte[31];
        newExecutionContext();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecutionContextWithTxHashGreaterThan32Bytes() {
        txHash = new byte[33];
        newExecutionContext();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecutionContextWithNegativeDepth() {
        depth = -1;
        newExecutionContext();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecutionContextWithNegativeNrgLimit() {
        nrgLimit = -1;
        newExecutionContext();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecutionContextWithNegativeBlockNumber() {
        blockNumber = -1;
        newExecutionContext();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecutionContextWithNegativeBlockTimestamp() {
        blockTimestamp = -1;
        newExecutionContext();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecutionContextWithNegativeBlockNrgLimit() {
        nrgLimit = -1;
        newExecutionContext();
    }

    @Test(expected = NullPointerException.class)
    public void testSetNullRecipient() {
        ExecutionContext context = newExecutionContext();
        context.setRecipient(null);
    }

    @Test
    public void testToBytesInGeneral() {
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesRandomBlockDifficulty() {
        blockDifficulty = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesRandomNrgPrice() {
        nrgPrice = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesRandomCallValue() {
        callValue = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesZeroLengthCallData() {
        callData = new byte[0];
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesLongCallData() {
        callData = new byte[1000];
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesMinMaxDepth() {
        depth = 0;
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
        depth = Integer.MAX_VALUE;
        context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesMinMaxKind() {
        kind = Integer.MIN_VALUE;
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
        kind = Integer.MAX_VALUE;
        context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesMinMaxFlags() {
        flags = Integer.MIN_VALUE;
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
        flags = Integer.MAX_VALUE;
        context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesMinMaxNrgLimit() {
        nrgLimit = 0;
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
        nrgLimit = Long.MAX_VALUE;
        context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesMinMaxBlockNumber() {
        blockNumber = 0;
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
        blockNumber = Long.MAX_VALUE;
        context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesMinMaxTimestamp() {
        blockTimestamp = 0;
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
        blockTimestamp = Long.MAX_VALUE;
        context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesMinMaxBlockNrgLimit() {
        blockNrgLimit = 0;
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
        blockNrgLimit = Long.MAX_VALUE;
        context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testGetTxHash() {
        ExecutionContext context = newExecutionContext();
        assertArrayEquals(txHash, context.getTransactionHash());
    }

    @Test
    public void testSetRecipient() {
        ExecutionContext context = newExecutionContext();
        Address newRecipient = new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
        context.setRecipient(newRecipient);
        assertEquals(newRecipient, context.getRecipient());
    }

    // <-------------------------------------HELPERS BELOW----------------------------------------->

    /**
     * Returns a new ExecutionContext whose fields will be initialized using the fields of this class.
     */
    private ExecutionContext newExecutionContext() {
        return new ExecutionContext(txHash, recipient, origin, caller, nrgPrice, nrgLimit, callValue,
            callData, depth, kind, flags, coinbase, blockNumber, blockTimestamp, blockNrgLimit,
            blockDifficulty);
    }

    /**
     * Checks that encoding has correctly encoded context. If the encoding is incorrect then the
     * calling test will fail.
     *
     * @param context The ExecutionContext which encoding is a big-endian binary encoding of.
     * @param encoding The big-endian binary encoding of context.
     */
    private void checkEncoding(ExecutionContext context, byte[] encoding) {
        int start = 0;
        int end = Address.ADDRESS_LEN;
        ByteBuffer longBuf = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
        ByteBuffer intBuf = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        assertEquals(context.getRecipient(), new Address(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += Address.ADDRESS_LEN;
        assertEquals(context.getOrigin(), new Address(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += Address.ADDRESS_LEN;
        assertEquals(context.getCaller(), new Address(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += DataWord.BYTES;
        assertEquals(context.getNrgPrice(), new DataWord(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += Long.BYTES;
        longBuf.put(Arrays.copyOfRange(encoding, start, end));
        longBuf.flip();
        assertEquals(context.getNrgLimit(), longBuf.getLong());
        longBuf.clear();
        start = end;
        end += DataWord.BYTES;
        assertEquals(context.getCallValue(), new DataWord(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getCallData().length, intBuf.getInt());
        intBuf.clear();
        start = end;
        end += context.getCallData().length;
        assertArrayEquals(context.getCallData(), Arrays.copyOfRange(encoding, start, end));
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getDepth(), intBuf.getInt());
        intBuf.clear();
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getKind(), intBuf.getInt());
        intBuf.clear();
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getFlags(), intBuf.getInt());
        start = end;
        end += Address.ADDRESS_LEN;
        assertEquals(context.getBlockCoinbase(), new Address(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += Long.BYTES;
        longBuf.put(Arrays.copyOfRange(encoding, start, end));
        longBuf.flip();
        assertEquals(context.getBlockNumber(), longBuf.getLong());
        longBuf.clear();
        start = end;
        end += Long.BYTES;
        longBuf.put(Arrays.copyOfRange(encoding, start, end));
        longBuf.flip();
        assertEquals(context.getBlockTimestamp(), longBuf.getLong());
        longBuf.clear();
        start = end;
        end += Long.BYTES;
        longBuf.put(Arrays.copyOfRange(encoding, start, end));
        longBuf.flip();
        assertEquals(context.getBlockNrgLimit(), longBuf.getLong());
        start = end;
        end += DataWord.BYTES;
        assertEquals(context.getBlockDifficulty(), new DataWord(Arrays.copyOfRange(encoding, start, end)));
    }

}