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
package org.aion.solidity;

import org.aion.solidity.Compiler.Options;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CompilerTest {

    @Test
    public void testCompile() throws IOException {
        String contract = "pragma solidity ^0.4.0;\n" + //
                "\n" + //
                "contract SimpleStorage {\n" + //
                "    uint storedData;\n" + //
                "\n" + //
                "    function set(uint x) {\n" + //
                "        storedData = x;\n" + //
                "    }\n" + //
                "\n" + //
                "    function get() constant returns (uint) {\n" + //
                "        return storedData;\n" + //
                "    }\n" + //
                "}";

        Compiler.Result result = Compiler.getInstance().compile(contract.getBytes(), Options.ABI, Options.BIN);
        System.out.println(result.output);

        assertNotNull(result.output.contains("bin"));
        assertTrue(result.output.contains("abi"));
    }
}
