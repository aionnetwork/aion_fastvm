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

import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class ExecutionResultTest {

    @Test
    public void testParse() {
        ResultCode code = ResultCode.INTERNAL_ERROR;
        long nrgLeft = 0x12345678L;
        byte[] output = RandomUtils.nextBytes(32);

        byte[] encoded = new ExecutionResult(code, nrgLeft, output).toBytes();
        ExecutionResult decoded = ExecutionResult.parse(encoded);
        assertEquals(code, decoded.getResultCode());
        assertEquals(nrgLeft, decoded.getNrgLeft());
        assertArrayEquals(output, decoded.getOutput());
    }
}
