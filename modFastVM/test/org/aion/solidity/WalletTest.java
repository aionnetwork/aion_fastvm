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
package org.aion.solidity;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import org.aion.contract.ContractUtils;
import org.aion.solidity.Compiler.Options;
import org.junit.Test;

public class WalletTest {


    @Test
    public void testCompile() throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(
            ContractUtils.readContract("Wallet.sol"), Options.BIN, Options.ABI);
        CompilationResult cr = CompilationResult.parse(r.output);
        CompilationResult.Contract meta = cr.contracts.get("Wallet");

        assertNotNull(meta.bin);
        assertNotNull(meta.abi);
    }
}
