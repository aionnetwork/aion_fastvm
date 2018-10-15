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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.aion.solidity.Compiler.Options;
import org.junit.Test;

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

        Compiler.Result result = Compiler.getInstance()
            .compile(contract.getBytes(), Options.ABI, Options.BIN);
        System.out.println(result.output);

        assertNotNull(result.output.contains("bin"));
        assertTrue(result.output.contains("abi"));
    }

    @Test
    public void testPayable() throws IOException {
        String contract = "pragma solidity ^0.4.0;\n" +
            "\n" +
            "contract Register {\n" +
            "    mapping (address => bool) registeredAddresses;\n" +
            "    uint price;\n" +
            "\n" +
            "    function Register(uint initialPrice) public { price = initialPrice; }\n" +
            "\n" +
            "    function register() public payable {\n" +
            "        registeredAddresses[msg.sender] = true;\n" +
            "    }\n" +
            "\n" +
            "    function changePrice(uint _price) public {\n" +
            "        price = _price;\n" +
            "    }\n" +
            "}\n";
        System.out.println(contract);

        Compiler.Result r = Compiler.getInstance()
            .compile(contract.getBytes(), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);

        Abi abi = Abi.fromJSON(cr.contracts.get("Register").abi);
        Abi.Function func = abi.findFunction(f -> f.name.equals("register"));

        System.out.println("Method: name = " + func.name + ", payable = " + func.payable);
        assertTrue(func.payable);
    }
}
