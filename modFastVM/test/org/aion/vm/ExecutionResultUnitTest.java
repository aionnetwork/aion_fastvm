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

import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExecutionResultUnitTest {

    private ResultCode code;
    private long nrgLeft;
    private byte[] output;

    @Before
    public void setup() {
        code = ResultCode.INTERNAL_ERROR;
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
        ExecutionResult result = new ExecutionResult(code, nrgLeft, output);
        assertEquals(code, result.getResultCode());
        assertEquals(code.toInt(), result.getCode());
        assertEquals(nrgLeft, result.getNrgLeft());
        assertEquals(output, result.getOutput());
    }

    @Test
    public void testGettersBasicNoOutputSpecified() {
        ExecutionResult result = new ExecutionResult(code, nrgLeft);
        assertEquals(code, result.getResultCode());
        assertEquals(code.toInt(), result.getCode());
        assertEquals(nrgLeft, result.getNrgLeft());
    }

    @Test
    public void testSettersBasicWithOutputSpecified() {
        ExecutionResult result = new ExecutionResult(code, nrgLeft, output);
        int newCode = ResultCode.values()[0].toInt();
        long newNrg = 0;
        result.setCodeAndNrgLeft(newCode, newNrg);
        result.setOutput(null);
        assertEquals(newCode, result.getCode());
        assertEquals(ResultCode.fromInt(newCode), result.getResultCode());
        assertEquals(newNrg, result.getNrgLeft());
        assertNull(result.getOutput());
    }

    @Test
    public void testSettersBasicNoOutputSpecified() {
        ExecutionResult result = new ExecutionResult(code, nrgLeft, output);
        int newCode = ResultCode.values()[0].toInt();
        long newNrg = 0;
        result.setCodeAndNrgLeft(newCode, newNrg);
        assertEquals(newCode, result.getCode());
        assertEquals(ResultCode.fromInt(newCode), result.getResultCode());
        assertEquals(newNrg, result.getNrgLeft());
    }

    @Test
    public void testGetOutputWhenNoOutputSpecified() {
        ExecutionResult result = new ExecutionResult(code, nrgLeft);
        assertArrayEquals(new byte[0], result.getOutput());
    }

    @Test
    public void testEncodingResultCodesWithOutputSpecified() {
        for (ResultCode code : ResultCode.values()) {
            checkEncoding(new ExecutionResult(code, nrgLeft, output));
        }
    }

    @Test
    public void testEncodingResultCodesNoOutputSpecified() {
        for (ResultCode code : ResultCode.values()) {
            checkEncoding(new ExecutionResult(code, nrgLeft));
        }
    }

    @Test
    public void testEncodingMinMaxEnergyLeftWithOutputSpecified() {
        nrgLeft = 0;
        checkEncoding(new ExecutionResult(code, nrgLeft, output));
        nrgLeft = Long.MAX_VALUE;
        checkEncoding(new ExecutionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingMinMaxEnergyLeftNoOutputSpecified() {
        nrgLeft = 0;
        checkEncoding(new ExecutionResult(code, nrgLeft));
        nrgLeft = Long.MAX_VALUE;
        checkEncoding(new ExecutionResult(code, nrgLeft));
    }

    @Test
    public void testEncodingNullOutput() {
        checkEncoding(new ExecutionResult(code, nrgLeft, null));
    }

    @Test
    public void testEncodingZeroLengthOutput() {
        output = new byte[0];
        checkEncoding(new ExecutionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingLengthOneOutput() {
        output = new byte[]{(byte) 0x4A};
        checkEncoding(new ExecutionResult(code, nrgLeft, output));
    }

    @Test
    public void testEncodingLongOutput() {
        output = RandomUtils.nextBytes(10_000);
        checkEncoding(new ExecutionResult(code, nrgLeft, output));
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
     * @param original The ExecutionResult to encode and decode.
     */
    private void checkEncoding(ExecutionResult original) {
        byte[] encoding = original.toBytes();
        ExecutionResult decodedResult = ExecutionResult.parse(encoding);
        assertEquals(original, decodedResult);
    }

}
