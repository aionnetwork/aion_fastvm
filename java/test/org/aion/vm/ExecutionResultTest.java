/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
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
