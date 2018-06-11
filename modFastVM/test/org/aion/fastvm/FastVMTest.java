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
package org.aion.fastvm;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.ExecutionResult.Code;
import org.aion.vm.TransactionResult;
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FastVMTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = Address.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockNumber = 1;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private DataWord blockDifficulty = new DataWord(0x100000000L);

    private DataWord nrgPrice;
    private long nrgLimit;
    private DataWord callValue;
    private byte[] callData;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    private TransactionResult txResult;

    public FastVMTest() throws CloneNotSupportedException {
    }

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 20000;
        callValue = DataWord.ZERO;
        callData = new byte[0];
        txResult = new TransactionResult();
    }

    @Test
    public void testLoadLibrary() {
        assertNotNull(FastVM.class);
    }

    @Test
    public void testRun() {
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        byte[] code = Hex.decode("6FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF60020160E052601060E0F3");
        ExecutionResult result = vm.run(code, ctx, new DummyRepository());
        System.out.println(result);

        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals(19985, result.getNrgLeft());
        assertEquals(16, result.getOutput().length);
    }

    @Test
    public void testGetCodeByAddress1() {
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        byte[] code = Hex
                .decode("6020600060E06F111111111111111111111111111111116F000000000000000000000000111111113C602060E0F3");
        DummyRepository repo = new DummyRepository();

        ExecutionResult result = vm.run(code, ctx, repo);
        System.out.println(result);

        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
                Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testGetCodeByAddress2() {
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        byte[] code = Hex
                .decode("6020600060E06F111111111111111111111111111111116F111111111111111111111111111111113C602060E0F3");
        DummyRepository repo = new DummyRepository();
        repo.addContract(Address.wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111")), Hex.decode("11223344"));

        ExecutionResult result = vm.run(code, ctx, repo);
        System.out.println(result);

        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals("1122334400000000000000000000000000000000000000000000000000000000",
                Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testGetCodeSize() {
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        byte[] code = Hex
                .decode("6020600060E06F111111111111111111111111111111116F111111111111111111111111111111113B60E052601060E0F3");
        DummyRepository repo = new DummyRepository();
        repo.addContract(Address.wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111")), Hex.decode("11223344"));

        ExecutionResult result = vm.run(code, ctx, repo);
        System.out.println(result);

        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals("00000000000000000000000000000004", Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testBalance() {
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        byte[] code = Hex
                .decode("6F111111111111111111111111111111116F111111111111111111111111111111113160E052601060E0F3");
        DummyRepository repo = new DummyRepository();
        repo.addBalance(Address.wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111")), BigInteger.valueOf(0x34));

        ExecutionResult result = vm.run(code, ctx, repo);
        System.out.println(result);

        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals("00000000000000000000000000000034", Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testCall() throws IOException {
        byte[] calleeCtr = ContractUtils.getContractBody("Call.sol", "Callee");
        byte[] callerCtr = ContractUtils.getContractBody("Call.sol", "Caller");

        caller = Address.wrap(Hex.decode("3333333333333333333333333333333333333333333333333333333333333333"));
        origin = caller;
        address = Address.wrap(Hex.decode("2222222222222222222222222222222222222222222222222222222222222222"));

        callData = Hex.decode("fc68521a1111111111111111111111111111111111111111111111111111111111111111");

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        DummyRepository repo = new DummyRepository();
        repo.createAccount(Address.wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111")));
        repo.createAccount(Address.wrap(Hex.decode("2222222222222222222222222222222222222222222222222222222222222222")));
        repo.addContract(Address.wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111")), calleeCtr);
        repo.addContract(Address.wrap(Hex.decode("2222222222222222222222222222222222222222222222222222222222222222")), callerCtr);

        ExecutionResult result = vm.run(callerCtr, ctx, repo);
        System.out.println(result);

        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals("00000000000000000000000000000003", Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testCreate() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Create.sol", "Create");

        callData = Hex.decode("26121ff0");
        nrgLimit = 600_000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        DummyRepository repo = new DummyRepository();

        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);

        assertEquals(Code.SUCCESS, result.getCode());
        assertTrue(result.getOutput().length == 32);
    }

    @Test
    public void testDynamicArray1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("a76af697"), new DataWord(512L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
    }

    @Test
    public void testDynamicArray2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("a76af697"), new DataWord(1_000_000_000L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
    }

    @Test
    public void testDynamicArray3() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("e59cc974"), new DataWord(512L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
    }

    @Test
    public void testDynamicArray4() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("e59cc974"), new DataWord(1_000_000_000L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.OUT_OF_NRG, result.getCode());
    }

    @Test
    public void testSSTORELoop() {
        String code = "6000" /* counter */
                + "5b" /* jump point */
                + "80600101" /* DUP, 0x01, ADD */
                + "905080" /* SWAP, POP, DUP */
                + "60339055" /* SSTORE(counter, 0x33) */
                + "600256" /* JUMP */;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(Hex.decode(code), ctx, repo);
        System.out.println(result);
        assertEquals(0, result.getNrgLeft());
    }

    @Test
    public void testLOG0Loop() throws Exception {
        String code = "5b" + "632fffffff6000a0" + "600056";

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(Hex.decode(code), ctx, repo);
        System.out.println(result);
        assertEquals(0, result.getNrgLeft());
    }

    @Test
    public void testSHA3Loop() throws Exception {
        String code = "5b" + "632fffffff60002050" + "600056";

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(Hex.decode(code), ctx, repo);
        System.out.println(result);
        assertEquals(0, result.getNrgLeft());
    }

    @Test
    public void testSHA3LargeMemory() throws Exception {
        String code = "6f0000000000000000000000003FFFFFFF" // push
                + "6f00000000000000000000000000000000" // push
                + "20"; // SHA3

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(Hex.decode(code), ctx, repo);
        System.out.println(result);
        assertEquals(0, result.getNrgLeft());
    }

    @Test
    public void testShortConstructor() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Short.sol", "Short");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 100000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(code, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
        assertTrue(result.getOutput().length > 0);
    }

    @Test
    public void testLongConstructor1() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Long.sol", "Long");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 100000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(code, ctx, repo);
        System.out.println(result);
        assertEquals(0, result.getNrgLeft());
    }

    @Test
    public void testLongConstructor2() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Long.sol", "LongCreator");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 500_000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(code, ctx, repo);
        System.out.println(result);

        // NOTE: after the byzantine fork, if the CREATE call fails, the
        // reserved gas got revertd.
        assertEquals(Code.REVERT, result.getCode());
        assertTrue(result.getNrgLeft() > 0);
    }

    @Test
    public void testBlockCreationExploit() throws InterruptedException {
        String testerByteCode = "0x605060405234156100105760006000fd5b5b3360006000508282909180600101839055555050505b61002c565b6103828061003b6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680634f2be91f14610049578063b4b65ae01461005f578063cd909c75146100cb57610043565b60006000fd5b34156100555760006000fd5b61005d6100e1565b005b341561006b5760006000fd5b6100736100e7565b6040518080601001828103825283818151815260100191508051906010019060200280838360005b838110156100b75780820151818401525b60108101905061009b565b505050509050019250505060405180910390f35b34156100d75760006000fd5b6100df61015d565b005b6002505b565b6100ef6101cf565b600260005080548060200260100160405190810160405280929190818152601001828054801561014e576020028201919060005260106000209050905b816000508060010154905482528160100152602001906002019080831161012c575b5050505050905061015a565b90565b600060006101696101e6565b604051809103906000f08015821516156101835760006000fd5b915091506002600050805480600101828161019e91906101f5565b91909060005260106000209050906002020160005b84849091929091925091909091806001018390555550505b5050565b601060405190810160405280600081526010015090565b60405160fa8061025d83390190565b8154818355818115116102245760020281600202836000526010600020905091820191016102239190610229565b5b505050565b6102599190610233565b8082111561025557600081815080600090556001016000905550600201610233565b5090565b905600605060405234156100105760006000fd5b5b4260006000508190909055507fd4fc977b8ac41e3fa318bb6650de6044046ea9e8cda72be27b6b0c458726c1666000600050546040518082815260100191505060405180910390a15b61005f565b608d8061006d6000396000f30060506040526000356c01000000000000000000000000900463ffffffff16806316ada54714603157602b565b60006000fd5b3415603c5760006000fd5b60426058565b6040518082815260100191505060405180910390f35b600060005054815600a165627a7a723058206919d683bc89f37f2bf6a52877fe0997e5d9b83057967fa1fd4a420b5da707b20029a165627a7a723058202d4cb48cf45eb1f4907e249b6060d84330669ff7f27d967554eb3a20e1c1f6840029";
        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        BigInteger nonce = BigInteger.ZERO;

        /* byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice */
        AionTransaction tx = new AionTransaction(
                nonce.toByteArray(),
                null,
                new byte[0],
                ByteUtil.hexStringToBytes(testerByteCode),
                1_000_000L,
                1L
        );
        tx.sign(deployerAccount);
        assertThat(tx.isContractCreation()).isTrue();

        BlockContext context = bc.createNewBlockContext(
                bc.getBestBlock(), Collections.singletonList(tx), false);

        // try to connect the deployment block
        ImportResult result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Address contractAddress = tx.getContractAddress();
        System.out.println("xxx = " + bc.getRepository().getNonce(Address.wrap(deployerAccount.getAddress())));
        Thread.sleep(1000L);

        // try executing a makeTest() call
        byte[] funcSig = ByteUtil.hexStringToBytes("0xcd909c75");
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction makeTestCallTx = new AionTransaction(
                nonce.toByteArray(),
                contractAddress,
                new byte[0],
                funcSig,
                1_000_000L,
                1L
        );
        makeTestCallTx.sign(deployerAccount);

        BlockContext context2 = bc.createNewBlockContext(
                bc.getBestBlock(), Collections.singletonList(makeTestCallTx), false);
        ImportResult result2 = bc.tryToConnect(context2.block);
        assertThat(result2).isEqualTo(ImportResult.IMPORTED_BEST);
        System.out.println("xxx = " + bc.getRepository().getNonce(Address.wrap(deployerAccount.getAddress())));
        System.out.println("yyy = " + bc.getRepository().getNonce(contractAddress));
        Thread.sleep(1000L);

        // try executing another makeTest() call
        funcSig = ByteUtil.hexStringToBytes("0xcd909c75");
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction anotherMakeTestCall = new AionTransaction(
                nonce.toByteArray(),
                contractAddress,
                new byte[0],
                funcSig,
                1_000_000L,
                1L
        );
        anotherMakeTestCall.sign(deployerAccount);

        BlockContext context3 = bc.createNewBlockContext(
                bc.getBestBlock(), Collections.singletonList(anotherMakeTestCall), false);
        ImportResult result3 = bc.tryToConnect(context3.block);
        assertThat(result3).isEqualTo(ImportResult.IMPORTED_BEST);
        System.out.println("xxx = " + bc.getRepository().getNonce(Address.wrap(deployerAccount.getAddress())));
        System.out.println("yyy = " + bc.getRepository().getNonce(contractAddress));

        assertEquals(BigInteger.valueOf(3), bc.getRepository().getNonce(Address.wrap(deployerAccount.getAddress())));
        assertEquals(BigInteger.valueOf(2), bc.getRepository().getNonce(contractAddress));
    }
}
