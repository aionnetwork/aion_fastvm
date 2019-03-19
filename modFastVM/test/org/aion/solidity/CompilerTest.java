package org.aion.solidity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aion.contract.ContractUtils;
import org.aion.solidity.Compiler.Options;
import org.aion.solidity.Compiler.Result;
import org.junit.Test;

public class CompilerTest {

    @Test
    public void testCompile() throws IOException {
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

        Compiler.Result result =
                Compiler.getInstance().compile(contract.getBytes(), Options.ABI, Options.BIN);
        System.out.println(result.output);

        assertNotNull(result.output.contains("bin"));
        assertTrue(result.output.contains("abi"));
    }

    @Test
    public void testPayable() throws IOException {
        String contract =
                "pragma solidity ^0.4.0;\n"
                        + "\n"
                        + "contract Register {\n"
                        + "    mapping (address => bool) registeredAddresses;\n"
                        + "    uint price;\n"
                        + "\n"
                        + "    function Register(uint initialPrice) public { price = initialPrice; }\n"
                        + "\n"
                        + "    function register() public payable {\n"
                        + "        registeredAddresses[msg.sender] = true;\n"
                        + "    }\n"
                        + "\n"
                        + "    function changePrice(uint _price) public {\n"
                        + "        price = _price;\n"
                        + "    }\n"
                        + "}\n";
        System.out.println(contract);

        Compiler.Result r =
                Compiler.getInstance().compile(contract.getBytes(), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);

        Abi abi = Abi.fromJSON(cr.contracts.get("Register").abi);
        Abi.Function func = abi.findFunction(f -> f.name.equals("register"));

        System.out.println("Method: name = " + func.name + ", payable = " + func.payable);
        assertTrue(func.payable);
    }

    @Test
    public void testCompileZip() throws IOException {

        Compiler comp = Compiler.getInstance();

        InputStream in = ContractUtils.class.getResourceAsStream("contracts.zip");

        Result r =
                comp.compileZip(
                        in.readAllBytes(),
                        "Import.sol",
                        Compiler.Options.ABI,
                        Compiler.Options.BIN);

        assertFalse(r.isFailed());
    }
}
