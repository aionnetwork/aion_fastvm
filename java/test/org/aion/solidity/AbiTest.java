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

import org.aion.contract.ContractUtils;
import org.aion.solidity.Compiler.Options;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;

public class AbiTest {

    @Test
    public void testFromJSON() throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(ContractUtils.readContract("Simple.sol"), Options.ABI, Options.BIN);
        CompilationResult result = CompilationResult.parse(r.output);

        assertFalse(result.contracts.isEmpty());
        Abi abi = Abi.fromJSON(result.contracts.values().iterator().next().abi);
        String json = abi.toJSON();

        System.out.println(json);
    }

    @Test
    public void testToJSON() throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(ContractUtils.readContract("Simple.sol"), Options.ABI, Options.BIN);
        CompilationResult result = CompilationResult.parse(r.output);

        assertFalse(result.contracts.isEmpty());
        Abi.fromJSON(result.contracts.values().iterator().next().abi);
    }
}
