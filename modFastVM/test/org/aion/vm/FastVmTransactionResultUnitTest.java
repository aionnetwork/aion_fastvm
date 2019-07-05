package org.aion.vm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.FastVmTransactionResult;
import org.aion.mcf.types.KernelInterface;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FastVmTransactionResultUnitTest {
    private FastVmResultCode code;
    private long nrgLeft;
    private byte[] output;

    @Before
    public void setup() {
        code = FastVmResultCode.SUCCESS;
        nrgLeft = 0x12345678L;
        output = RandomUtils.nextBytes(32);
    }

    @After
    public void tearDown() {
        code = null;
        output = null;
    }

    @Test
    public void testGettersBasicWithOutputSpecified() {
        FastVmTransactionResult result = new FastVmTransactionResult(code, nrgLeft, output);
        assertEquals(code, result.getResultCode());
        assertEquals(code, result.getResultCode());
        assertEquals(nrgLeft, result.getEnergyRemaining());
        assertEquals(output, result.getReturnData());
    }

    @Test
    public void testGettersBasicNoOutputSpecified() {
        FastVmTransactionResult result = new FastVmTransactionResult(code, nrgLeft);
        assertEquals(code, result.getResultCode());
        assertEquals(code, result.getResultCode());
        assertEquals(nrgLeft, result.getEnergyRemaining());
    }

    @Test
    public void testSettersBasicWithOutputSpecified() {
        FastVmTransactionResult result = new FastVmTransactionResult(code, nrgLeft, output);
        int newCode = FastVmResultCode.values()[0].toInt();
        long newNrg = 0;
        result.setResultCodeAndEnergyRemaining(FastVmResultCode.fromInt(newCode), newNrg);
        result.setReturnData(null);
        assertEquals(newCode, result.getResultCode().toInt());
        assertEquals(FastVmResultCode.fromInt(newCode), result.getResultCode());
        assertEquals(newNrg, result.getEnergyRemaining());
        assertEquals(0, result.getReturnData().length);
    }

    @Test
    public void testSettersBasicNoOutputSpecified() {
        FastVmTransactionResult result = new FastVmTransactionResult(code, nrgLeft, output);
        int newCode = FastVmResultCode.values()[0].toInt();
        long newNrg = 0;
        result.setResultCodeAndEnergyRemaining(FastVmResultCode.fromInt(newCode), newNrg);
        assertEquals(newCode, result.getResultCode().toInt());
        assertEquals(FastVmResultCode.fromInt(newCode), result.getResultCode());
        assertEquals(newNrg, result.getEnergyRemaining());
    }

    @Test
    public void testGetOutputWhenNoOutputSpecified() {
        FastVmTransactionResult result = new FastVmTransactionResult(code, nrgLeft);
        assertArrayEquals(new byte[0], result.getReturnData());
    }

    @Test
    public void testEncodingResultCodesWithOutputSpecified() {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            checkEncoding(new FastVmTransactionResult(code, nrgLeft, output));
        }
    }

    @Test
    public void testEncodingResultCodesNoOutputSpecified() {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            checkEncoding(new FastVmTransactionResult(code, nrgLeft));
        }
    }

    @Test
    public void testEncodingMinMaxEnergyLeftWithOutputSpecified() {
        nrgLeft = 0;
        checkEncoding(new FastVmTransactionResult(code, nrgLeft, output));
        nrgLeft = Long.MAX_VALUE;
        checkEncoding(new FastVmTransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingMinMaxEnergyLeftNoOutputSpecified() {
        nrgLeft = 0;
        checkEncoding(new FastVmTransactionResult(code, nrgLeft));
        nrgLeft = Long.MAX_VALUE;
        checkEncoding(new FastVmTransactionResult(code, nrgLeft));
    }

    @Test
    public void testEncodingNullOutput() {
        checkEncoding(new FastVmTransactionResult(code, nrgLeft, null));
    }

    @Test
    public void testEncodingZeroLengthOutput() {
        output = new byte[0];
        checkEncoding(new FastVmTransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingLengthOneOutput() {
        output = new byte[] {(byte) 0x4A};
        checkEncoding(new FastVmTransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingLongOutput() {
        output = RandomUtils.nextBytes(10_000);
        checkEncoding(new FastVmTransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testCodeToIntToCode() {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            assertEquals(code, FastVmResultCode.fromInt(code.toInt()));
        }
    }

    /**
     * Checks that if original is encoded and then decoded that the decoded object is equal to
     * original. Any test that calls this and this is not true will fail.
     *
     * Note that the encoding is a partial encoding that does not include the
     * {@link org.aion.vm.api.ExecutionSideEffects} or
     * {@link KernelInterface} objects, so these are ignored.
     *
     * @param original The TransactionResult to encode and decode.
     */
    private void checkEncoding(FastVmTransactionResult original) {
        byte[] encoding = original.toBytes();
        FastVmTransactionResult decodedResult = FastVmTransactionResult.fromBytes(encoding);

        assertEquals(original.getResultCode(), decodedResult.getResultCode());
        assertEquals(original.getEnergyRemaining(), decodedResult.getEnergyRemaining());
        assertArrayEquals(original.getReturnData(), decodedResult.getReturnData());
    }
}
