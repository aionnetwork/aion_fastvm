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
import static org.junit.Assert.assertNull;

import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TransactionResultUnitTest {
    private ResultCode code;
    private long nrgLeft;
    private byte[] output;

    @Before
    public void setup() {
        code = ResultCode.SUCCESS;
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
        TransactionResult result = new TransactionResult(code, nrgLeft, output);
        assertEquals(code, result.getResultCode());
        assertEquals(code, result.getResultCode());
        assertEquals(nrgLeft, result.getEnergyRemaining());
        assertEquals(output, result.getOutput());
    }

    @Test
    public void testGettersBasicNoOutputSpecified() {
        TransactionResult result = new TransactionResult(code, nrgLeft);
        assertEquals(code, result.getResultCode());
        assertEquals(code, result.getResultCode());
        assertEquals(nrgLeft, result.getEnergyRemaining());
    }

    @Test
    public void testSettersBasicWithOutputSpecified() {
        TransactionResult result = new TransactionResult(code, nrgLeft, output);
        int newCode = ResultCode.values()[0].toInt();
        long newNrg = 0;
        result.setResultCodeAndEnergyRemaining(ResultCode.fromInt(newCode), newNrg);
        result.setOutput(null);
        assertEquals(newCode, result.getResultCode().toInt());
        assertEquals(ResultCode.fromInt(newCode), result.getResultCode());
        assertEquals(newNrg, result.getEnergyRemaining());
        assertEquals(0, result.getOutput().length);
    }

    @Test
    public void testSettersBasicNoOutputSpecified() {
        TransactionResult result = new TransactionResult(code, nrgLeft, output);
        int newCode = ResultCode.values()[0].toInt();
        long newNrg = 0;
        result.setResultCodeAndEnergyRemaining(ResultCode.fromInt(newCode), newNrg);
        assertEquals(newCode, result.getResultCode().toInt());
        assertEquals(ResultCode.fromInt(newCode), result.getResultCode());
        assertEquals(newNrg, result.getEnergyRemaining());
    }

    @Test
    public void testGetOutputWhenNoOutputSpecified() {
        TransactionResult result = new TransactionResult(code, nrgLeft);
        assertArrayEquals(new byte[0], result.getOutput());
    }

    @Test
    public void testEncodingResultCodesWithOutputSpecified() {
        for (ResultCode code : ResultCode.values()) {
            checkEncoding(new TransactionResult(code, nrgLeft, output));
        }
    }

    @Test
    public void testEncodingResultCodesNoOutputSpecified() {
        for (ResultCode code : ResultCode.values()) {
            checkEncoding(new TransactionResult(code, nrgLeft));
        }
    }

    @Test
    public void testEncodingMinMaxEnergyLeftWithOutputSpecified() {
        nrgLeft = 0;
        checkEncoding(new TransactionResult(code, nrgLeft, output));
        nrgLeft = Long.MAX_VALUE;
        checkEncoding(new TransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingMinMaxEnergyLeftNoOutputSpecified() {
        nrgLeft = 0;
        checkEncoding(new TransactionResult(code, nrgLeft));
        nrgLeft = Long.MAX_VALUE;
        checkEncoding(new TransactionResult(code, nrgLeft));
    }

    @Test
    public void testEncodingNullOutput() {
        checkEncoding(new TransactionResult(code, nrgLeft, null));
    }

    @Test
    public void testEncodingZeroLengthOutput() {
        output = new byte[0];
        checkEncoding(new TransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingLengthOneOutput() {
        output = new byte[] {(byte) 0x4A};
        checkEncoding(new TransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingLongOutput() {
        output = RandomUtils.nextBytes(10_000);
        checkEncoding(new TransactionResult(code, nrgLeft, output));
    }

    @Test
    public void testCodeToIntToCode() {
        for (ResultCode code : ResultCode.values()) {
            assertEquals(code, ResultCode.fromInt(code.toInt()));
        }
    }

    /**
     * Checks that if original is encoded and then decoded that the decoded object is equal to
     * original. Any test that calls this and this is not true will fail.
     *
     * Note that the encoding is a partial encoding that does not include the
     * {@link org.aion.vm.api.ExecutionSideEffects} or
     * {@link org.aion.vm.api.interfaces.KernelInterface} objects, so these are ignored.
     *
     * @param original The TransactionResult to encode and decode.
     */
    private void checkEncoding(TransactionResult original) {
        byte[] encoding = original.toBytes();
        TransactionResult decodedResult = TransactionResult.fromBytes(encoding);

        assertEquals(original.getResultCode(), decodedResult.getResultCode());
        assertEquals(original.getEnergyRemaining(), decodedResult.getEnergyRemaining());
        assertArrayEquals(original.getOutput(), decodedResult.getOutput());
    }
}
