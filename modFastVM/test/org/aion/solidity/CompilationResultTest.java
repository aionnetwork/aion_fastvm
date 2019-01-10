package org.aion.solidity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.aion.solidity.Compiler.Options;
import org.junit.Test;

public class CompilationResultTest {

    @Test
    public void testParse() throws IOException {
        String contract =
                "pragma solidity ^0.4.0;\n"
                        + //
                        "\n"
                        + //
                        "contract SimpleStorage {\n"
                        + //
                        "    uint storedData;\n"
                        + //
                        "\n"
                        + //
                        "    function set(uint x) {\n"
                        + //
                        "        storedData = x;\n"
                        + //
                        "    }\n"
                        + //
                        "\n"
                        + //
                        "    function get() constant returns (uint) {\n"
                        + //
                        "        return storedData;\n"
                        + //
                        "    }\n"
                        + //
                        "}";

        Compiler.Result r =
                Compiler.getInstance().compile(contract.getBytes(), Options.ABI, Options.BIN);
        CompilationResult result = CompilationResult.parse(r.output);

        assertFalse(result.contracts.isEmpty());
        assertTrue(result.version != null);

        for (String name : result.contracts.keySet()) {
            System.out.println(name);
            System.out.println(result.contracts.get(name).abi);
            System.out.println(result.contracts.get(name).bin);
        }
    }
}
