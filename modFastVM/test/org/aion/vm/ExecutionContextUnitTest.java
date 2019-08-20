package org.aion.vm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.aion.fastvm.FvmDataWord;
import org.aion.fastvm.TransactionKind;
import org.aion.types.AionAddress;
import org.aion.fastvm.ExecutionContext;
import org.aion.util.HexUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExecutionContextUnitTest {
    private AionAddress recipient, origin, caller, coinbase;
    private FvmDataWord blockDifficulty;
    private BigInteger callValue;
    private long nrgPrice;
    private byte[] txHash, callData;
    private long nrgLimit, blockNumber, blockTimestamp, blockNrgLimit;
    private int depth, flags;
    private TransactionKind kind;

    @Before
    public void setup() {
        recipient =
                new AionAddress(HexUtil.hexStringToBytes("1111111111111111111111111111111111111111111111111111111111111111"));
        origin =
                new AionAddress(HexUtil.hexStringToBytes("2222222222222222222222222222222222222222222222222222222222222222"));
        caller =
                new AionAddress(HexUtil.hexStringToBytes("3333333333333333333333333333333333333333333333333333333333333333"));
        coinbase =
                new AionAddress(HexUtil.hexStringToBytes("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        blockDifficulty = FvmDataWord.fromLong(16);
        nrgPrice = 4;
        callValue = BigInteger.valueOf(6);
        txHash = RandomUtils.nextBytes(32);
        callData = new byte[] {0x07};
        depth = 8;
        kind = TransactionKind.CALL;
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
        callValue = null;
        txHash = null;
        callData = null;
    }

    @Test
    public void testToBytesInGeneral() {
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesRandomBlockDifficulty() {
        blockDifficulty = FvmDataWord.fromLong(RandomUtils.nextLong(1, 10_000));
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesRandomNrgPrice() {
        nrgPrice = RandomUtils.nextLong(1, 100);
        ExecutionContext context = newExecutionContext();
        checkEncoding(context, context.toBytes());
    }

    @Test
    public void testToBytesRandomCallValue() {
        callValue = BigInteger.valueOf(RandomUtils.nextLong(1, 100));
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
        AionAddress newRecipient = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        context.setDestinationAddress(newRecipient);
        assertEquals(newRecipient, context.getDestinationAddress());
    }

    // <-------------------------------------HELPERS BELOW----------------------------------------->

    /**
     * Returns a new ExecutionContext whose fields will be initialized using the fields of this
     * class.
     */
    private ExecutionContext newExecutionContext() {
        return ExecutionContext.from(
                txHash,
                recipient,
                origin,
                caller,
                nrgPrice,
                nrgLimit,
                callValue,
                callData,
                depth,
                kind,
                flags,
                coinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
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
        int end = AionAddress.LENGTH;
        ByteBuffer longBuf = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
        ByteBuffer intBuf = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        assertEquals(
                context.getDestinationAddress(),
                new AionAddress(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += AionAddress.LENGTH;
        assertEquals(
                context.getOriginAddress(),
                new AionAddress(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += AionAddress.LENGTH;
        assertEquals(
                context.getSenderAddress(),
                new AionAddress(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += FvmDataWord.SIZE;
        assertEquals(
                context.getTransactionEnergyPrice(),
            FvmDataWord.fromBytes(Arrays.copyOfRange(encoding, start, end)).toLong());
        start = end;
        end += Long.BYTES;
        longBuf.put(Arrays.copyOfRange(encoding, start, end));
        longBuf.flip();
        assertEquals(context.getTransactionEnergy(), longBuf.getLong());
        longBuf.clear();
        start = end;
        end += FvmDataWord.SIZE;
        assertEquals(
                FvmDataWord.fromBigInteger(context.getTransferValue()),
                FvmDataWord.fromBytes(Arrays.copyOfRange(encoding, start, end)));
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getTransactionData().length, intBuf.getInt());
        intBuf.clear();
        start = end;
        end += context.getTransactionData().length;
        assertArrayEquals(context.getTransactionData(), Arrays.copyOfRange(encoding, start, end));
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getTransactionStackDepth(), intBuf.getInt());
        intBuf.clear();
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getTransactionKind(), TransactionKind.fromInt(intBuf.getInt()));
        intBuf.clear();
        start = end;
        end += Integer.BYTES;
        intBuf.put(Arrays.copyOfRange(encoding, start, end));
        intBuf.flip();
        assertEquals(context.getFlags(), intBuf.getInt());
        start = end;
        end += AionAddress.LENGTH;
        assertEquals(
                context.getMinerAddress(),
                new AionAddress(Arrays.copyOfRange(encoding, start, end)));
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
        assertEquals(context.getBlockEnergyLimit(), longBuf.getLong());
        start = end;
        end += FvmDataWord.SIZE;
        assertEquals(
                context.getBlockDifficulty(),
                FvmDataWord.fromBytes(Arrays.copyOfRange(encoding, start, end)));
    }
}
