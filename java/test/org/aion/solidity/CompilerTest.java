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
