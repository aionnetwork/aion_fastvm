package org.aion.solidity;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import org.aion.contract.ContractUtils;
import org.aion.solidity.Compiler.Options;
import org.junit.Test;

public class WalletTest {

    @Test
    public void testCompile() throws IOException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Wallet.sol"), Options.BIN, Options.ABI);
        CompilationResult cr = CompilationResult.parse(r.output);
        CompilationResult.Contract meta = cr.contracts.get("Wallet");

        assertNotNull(meta.bin);
        assertNotNull(meta.abi);
    }
}
