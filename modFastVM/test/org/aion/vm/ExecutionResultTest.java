package org.aion.vm;

import org.aion.vm.ExecutionResult.Code;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ExecutionResultTest {

    @Test
    public void testParse() {
        Code code = Code.INTERNAL_ERROR;
        long nrgLeft = 0x12345678L;
        byte[] output = RandomUtils.nextBytes(32);

        byte[] encoded = new ExecutionResult(code, nrgLeft, output).toBytes();
        ExecutionResult decoded = ExecutionResult.parse(encoded);
        assertEquals(code, decoded.getCode());
        assertEquals(nrgLeft, decoded.getNrgLeft());
        assertArrayEquals(output, decoded.getOutput());
    }
}
