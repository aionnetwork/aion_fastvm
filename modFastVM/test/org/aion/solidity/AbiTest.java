package org.aion.solidity;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import org.aion.contract.ContractUtils;
import org.aion.solidity.Compiler.Options;
import org.junit.Test;

public class AbiTest {

    @Test
    public void testFromJSON() throws IOException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Simple.sol"), Options.ABI, Options.BIN);
        CompilationResult result = CompilationResult.parse(r.output);

        assertFalse(result.contracts.isEmpty());
        Abi abi = Abi.fromJSON(result.contracts.values().iterator().next().abi);
        String json = abi.toJSON();

        System.out.println(json);
    }

    @Test
    public void testToJSON() throws IOException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Simple.sol"), Options.ABI, Options.BIN);
        CompilationResult result = CompilationResult.parse(r.output);

        assertFalse(result.contracts.isEmpty());
        Abi.fromJSON(result.contracts.values().iterator().next().abi);
    }
}
