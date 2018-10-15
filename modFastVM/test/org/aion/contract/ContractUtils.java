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
package org.aion.contract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.aion.base.util.Hex;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.Compiler;
import org.aion.solidity.Compiler.Options;

public class ContractUtils {

    /**
     * Reads the given contract.
     */
    public static byte[] readContract(String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InputStream in = ContractUtils.class.getResourceAsStream(fileName);
        for (int c; (c = in.read()) != -1; ) {
            out.write(c);
        }
        in.close();

        return out.toByteArray();
    }


    /**
     * Compiles the given solidity source file and returns the deployer code for the given
     * contract.
     */
    public static byte[] getContractDeployer(String fileName, String contractName)
        throws IOException {
        Compiler.Result r = Compiler.getInstance()
            .compile(readContract(fileName), Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get(contractName).bin;
        return Hex.decode(deployer);
    }

    /**
     * Compiles the given solidity source file and returns the contract code for the given
     * contract.
     * <p>
     * NOTE: This method assumes the constructor is empty.
     */
    public static byte[] getContractBody(String fileName, String contractName) throws IOException {
        Compiler.Result r = Compiler.getInstance()
            .compile(readContract(fileName), Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get(contractName).bin;
        String contract = deployer.substring(deployer.indexOf("60506040", 1));
        return Hex.decode(contract);
    }
}
