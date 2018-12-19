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
package org.aion.fastvm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.vm.DummyRepository;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class FastVMTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = AionAddress.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = AionAddress.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = AionAddress.wrap(RandomUtils.nextBytes(32));
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

    public FastVMTest() {}

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 20000;
        callValue = DataWord.ZERO;
        callData = new byte[0];
    }

    @Test
    public void testLoadLibrary() {
        assertNotNull(FastVM.class);
    }

    @Test
    public void testRun() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code = Hex.decode("6FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF60020160E052601060E0F3");
        FastVmTransactionResult result =
                vm.run(code, ctx, wrapInKernelInterface(new DummyRepository()));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(19985, result.getEnergyRemaining());
        assertEquals(16, result.getOutput().length);
    }

    @Test
    public void testGetCodeByAddress1() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6020600060E06F111111111111111111111111111111116F000000000000000000000000111111113C602060E0F3");
        DummyRepository repo = new DummyRepository();

        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "0000000000000000000000000000000000000000000000000000000000000000",
                Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testGetCodeByAddress2() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6020600060E06F111111111111111111111111111111116F111111111111111111111111111111113C602060E0F3");
        DummyRepository repo = new DummyRepository();
        repo.addContract(
                AionAddress.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111")),
                Hex.decode("11223344"));

        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "1122334400000000000000000000000000000000000000000000000000000000",
                Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testGetCodeSize() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6020600060E06F111111111111111111111111111111116F111111111111111111111111111111113B60E052601060E0F3");
        DummyRepository repo = new DummyRepository();
        repo.addContract(
                AionAddress.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111")),
                Hex.decode("11223344"));

        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals("00000000000000000000000000000004", Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testBalance() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6F111111111111111111111111111111116F111111111111111111111111111111113160E052601060E0F3");
        DummyRepository repo = new DummyRepository();
        repo.addBalance(
                AionAddress.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111")),
                BigInteger.valueOf(0x34));

        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals("00000000000000000000000000000034", Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testCall() throws IOException {
        byte[] calleeCtr = ContractUtils.getContractBody("Call.sol", "Callee");
        byte[] callerCtr = ContractUtils.getContractBody("Call.sol", "Caller");

        caller =
                AionAddress.wrap(
                        Hex.decode(
                                "3333333333333333333333333333333333333333333333333333333333333333"));
        origin = caller;
        address =
                AionAddress.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));

        callData =
                Hex.decode(
                        "fc68521a1111111111111111111111111111111111111111111111111111111111111111");

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        DummyRepository repo = new DummyRepository();
        repo.createAccount(
                AionAddress.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111")));
        repo.createAccount(
                AionAddress.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222")));
        repo.addContract(
                AionAddress.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111")),
                calleeCtr);
        repo.addContract(
                AionAddress.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222")),
                callerCtr);

        FastVmTransactionResult result = vm.run(callerCtr, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals("00000000000000000000000000000003", Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testCreate() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Create.sol", "Create");

        callData = Hex.decode("26121ff0");
        nrgLimit = 600_000L;

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        DummyRepository repo = new DummyRepository();

        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(32, result.getOutput().length);
    }

    @Test
    public void testDynamicArray1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("a76af697"), new DataWord(512L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testDynamicArray2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("a76af697"), new DataWord(1_000_000_000L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testDynamicArray3() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("e59cc974"), new DataWord(512L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testDynamicArray4() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("e59cc974"), new DataWord(1_000_000_000L).getData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    @Test
    public void testSSTORELoop() {
        String code =
                "6000" /* counter */
                        + "5b" /* jump point */
                        + "80600101" /* DUP, 0x01, ADD */
                        + "905080" /* SWAP, POP, DUP */
                        + "60339055" /* SSTORE(counter, 0x33) */
                        + "600256" /* JUMP */;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(Hex.decode(code), ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testLOG0Loop() {
        String code = "5b" + "632fffffff6000a0" + "600056";

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(Hex.decode(code), ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testSHA3Loop() {
        String code = "5b" + "632fffffff60002050" + "600056";

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(Hex.decode(code), ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testSHA3LargeMemory() {
        String code =
                "6f0000000000000000000000003FFFFFFF" // push
                        + "6f00000000000000000000000000000000" // push
                        + "20"; // SHA3

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(Hex.decode(code), ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testShortConstructor() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Short.sol", "Short");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 100000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertTrue(result.getOutput().length > 0);
    }

    @Test
    public void testLongConstructor1() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Long.sol", "Long");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 100000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testLongConstructor2() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Long.sol", "LongCreator");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 500_000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        // NOTE: after the byzantine fork, if the CREATE call fails, the
        // reserved gas got revertd.
        assertEquals(FastVmResultCode.REVERT, result.getResultCode());
        assertTrue(result.getEnergyRemaining() > 0);
    }

    @Test
    public void testBlockCreationExploit() throws InterruptedException {
        String testerByteCode =
                "0x605060405234156100105760006000fd5b5b3360006000508282909180600101839055555050505b61002c565b6103828061003b6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680634f2be91f14610049578063b4b65ae01461005f578063cd909c75146100cb57610043565b60006000fd5b34156100555760006000fd5b61005d6100e1565b005b341561006b5760006000fd5b6100736100e7565b6040518080601001828103825283818151815260100191508051906010019060200280838360005b838110156100b75780820151818401525b60108101905061009b565b505050509050019250505060405180910390f35b34156100d75760006000fd5b6100df61015d565b005b6002505b565b6100ef6101cf565b600260005080548060200260100160405190810160405280929190818152601001828054801561014e576020028201919060005260106000209050905b816000508060010154905482528160100152602001906002019080831161012c575b5050505050905061015a565b90565b600060006101696101e6565b604051809103906000f08015821516156101835760006000fd5b915091506002600050805480600101828161019e91906101f5565b91909060005260106000209050906002020160005b84849091929091925091909091806001018390555550505b5050565b601060405190810160405280600081526010015090565b60405160fa8061025d83390190565b8154818355818115116102245760020281600202836000526010600020905091820191016102239190610229565b5b505050565b6102599190610233565b8082111561025557600081815080600090556001016000905550600201610233565b5090565b905600605060405234156100105760006000fd5b5b4260006000508190909055507fd4fc977b8ac41e3fa318bb6650de6044046ea9e8cda72be27b6b0c458726c1666000600050546040518082815260100191505060405180910390a15b61005f565b608d8061006d6000396000f30060506040526000356c01000000000000000000000000900463ffffffff16806316ada54714603157602b565b60006000fd5b3415603c5760006000fd5b60426058565b6040518082815260100191505060405180910390f35b600060005054815600a165627a7a723058206919d683bc89f37f2bf6a52877fe0997e5d9b83057967fa1fd4a420b5da707b20029a165627a7a723058202d4cb48cf45eb1f4907e249b6060d84330669ff7f27d967554eb3a20e1c1f6840029";
        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        BigInteger nonce = BigInteger.ZERO;

        /* byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice */
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(testerByteCode),
                        1_000_000L,
                        1L);
        tx.sign(deployerAccount);
        assertThat(tx.isContractCreationTransaction()).isTrue();

        BlockContext context =
                bc.createNewBlockContext(bc.getBestBlock(), Collections.singletonList(tx), false);

        // try to connect the deployment block
        ImportResult result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Address contractAddress = tx.getContractAddress();
        System.out.println(
                "xxx = "
                        + bc.getRepository()
                                .getNonce(AionAddress.wrap(deployerAccount.getAddress())));
        Thread.sleep(1000L);

        // try executing a makeTest() call
        byte[] funcSig = ByteUtil.hexStringToBytes("0xcd909c75");
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction makeTestCallTx =
                new AionTransaction(
                        nonce.toByteArray(), contractAddress, new byte[0], funcSig, 1_000_000L, 1L);
        makeTestCallTx.sign(deployerAccount);

        BlockContext context2 =
                bc.createNewBlockContext(
                        bc.getBestBlock(), Collections.singletonList(makeTestCallTx), false);
        ImportResult result2 = bc.tryToConnect(context2.block);
        assertThat(result2).isEqualTo(ImportResult.IMPORTED_BEST);
        System.out.println(
                "xxx = "
                        + bc.getRepository()
                                .getNonce(AionAddress.wrap(deployerAccount.getAddress())));
        System.out.println("yyy = " + bc.getRepository().getNonce(contractAddress));
        Thread.sleep(1000L);

        // try executing another makeTest() call
        funcSig = ByteUtil.hexStringToBytes("0xcd909c75");
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction anotherMakeTestCall =
                new AionTransaction(
                        nonce.toByteArray(), contractAddress, new byte[0], funcSig, 1_000_000L, 1L);
        anotherMakeTestCall.sign(deployerAccount);

        BlockContext context3 =
                bc.createNewBlockContext(
                        bc.getBestBlock(), Collections.singletonList(anotherMakeTestCall), false);
        ImportResult result3 = bc.tryToConnect(context3.block);
        assertThat(result3).isEqualTo(ImportResult.IMPORTED_BEST);
        System.out.println(
                "xxx = "
                        + bc.getRepository()
                                .getNonce(AionAddress.wrap(deployerAccount.getAddress())));
        System.out.println("yyy = " + bc.getRepository().getNonce(contractAddress));

        assertEquals(
                BigInteger.valueOf(3),
                bc.getRepository().getNonce(AionAddress.wrap(deployerAccount.getAddress())));
        assertEquals(BigInteger.valueOf(2), bc.getRepository().getNonce(contractAddress));
    }

    @Test
    public void testAddInvalidAndThenValidBlocks() throws InterruptedException {
        String testerByteCode =
                "0x60506040525b3360006000508282909180600101839055555050505b610020565b6107ab8061002f6000396000f30060506040523615610096576000356c01000000000000000000000000900463ffffffff16806306c8dcde1461009a578063590e1ae3146100c657806367a3914e146100dc5780636aee4cac1461010857806379ba5097146101525780638135ceea146101685780638da5cb5b146101d9578063a6f9dae11461020a578063b22fce4014610236578063d4ee1d901461027a57610096565b5b5b005b34156100a65760006000fd5b6100c4600480808060100135903590916020019091929050506102ab565b005b34156100d25760006000fd5b6100da610308565b005b34156100e85760006000fd5b6101066004808080601001359035909160200190919290505061035a565b005b34156101145760006000fd5b610138600480808060100135903590600019169090916020019091929050506103b7565b604051808215151515815260100191505060405180910390f35b341561015e5760006000fd5b6101666103e3565b005b34156101745760006000fd5b6101d760048080806010013590359060001916909091602001909192908080601001359035906000191690909160200190919290803590601001908201803590601001919091929080359060100190820180359060100191909192905050610470565b005b34156101e55760006000fd5b6101ed61062b565b604051808383825281601001526020019250505060405180910390f35b34156102165760006000fd5b6102346004808080601001359035909160200190919290505061063a565b005b34156102425760006000fd5b61026060048080806010013590359091602001909192905050610676565b604051808215151515815260100191505060405180910390f35b34156102865760006000fd5b61028e6106a2565b604051808383825281601001526020019250505060405180910390f35b600060005080600101549054339091149190141615156102cb5760006000fd5b600160046000506000848482528160100152602001908152601001600020905060006101000a81548160ff0219169083151502179055505b5b5050565b600060005080600101549054339091149190141615156103285760006000fd5b336108fc3031908115029060405160006040518083038185898989f194505050505015156103565760006000fd5b5b5b565b6000600050806001015490543390911491901416151561037a5760006000fd5b600060046000506000848482528160100152602001908152601001600020905060006101000a81548160ff0219169083151502179055505b5b5050565b60056000506020528181600052601052603060002090506000915091509054906101000a900460ff1681565b60026000508060010154905433909114919014161561046d5760026000508060010154905460006000508282909180600101839055555050506000600060026000508282909180600101839055555050506000600050806001015490547fa701229f4b9ddf00aa1c7228d248e6320ee7c581d856ddfba036e73947cd0d1360405160405180910390a25b5b565b60006000600050806001015490543390911491901416806104b85750600460005060003382528160100152602001908152601001600020905060009054906101000a900460ff165b15156104c45760006000fd5b868660056000506000838390600019169090600019169082528160100152602001908152601001600020905060009054906101000a900460ff1615151561050b5760006000fd5b600092506000925082505b8686905083101561057157610563878785818110151561053257fe5b905090906020020180601001359035878787818110151561054f57fe5b9050909060100201356106b163ffffffff16565b5b8280600101935050610516565b6001600560005060008b8b90600019169090600019169082528160100152602001908152601001600020905060006101000a81548160ff0219169083151502179055507f1fa305c7f8521af161de570532762ed7a60199cde79e18e1d259af34595625218c8c8c8c6040518085859060001916909060001916908252816010015260200183839060001916909060001916908252816010015260200194505050505060405180910390a15b5b50505b505050505050505050565b60006000508060010154905482565b6000600050806001015490543390911491901416151561065a5760006000fd5b818160026000508282909180600101839055555050505b5b5050565b60046000506020528181600052601052603060002090506000915091509054906101000a900460ff1681565b60026000508060010154905482565b6000600050806001015490543390911491901416806106f75750600460005060003382528160100152602001908152601001600020905060009054906101000a900460ff165b15156107035760006000fd5b82826108fc83908115029060405160006040518083038185898989f194505050505015156107315760006000fd5b7fdc3b8ebc415c945740a70187f1d472ad2d64a9e7a87047f38023aec56516976b84848460405180848482528160100152602001828152601001935050505060405180910390a15b5b5050505600a165627a7a7230582042b26e68ff40177f10fcfc05c70e102d5d14648a2134ffeec77c7138bf8d27dd0029";
        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        // =======================================================================
        BigInteger nonce = BigInteger.ZERO;

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(testerByteCode),
                        1_000_000L,
                        1L);
        tx.sign(deployerAccount);
        assertThat(tx.isContractCreationTransaction()).isTrue();

        BlockContext context =
                bc.createNewBlockContext(bc.getBestBlock(), Collections.singletonList(tx), false);

        ImportResult result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Address contractAddress = tx.getContractAddress();
        Thread.sleep(1000L);

        // =======================================================================
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction tx2 =
                new AionTransaction(
                        nonce.toByteArray(),
                        contractAddress,
                        new BigInteger("100000000000000000000").toByteArray(),
                        new byte[0],
                        1_000_000L,
                        1L);
        tx2.sign(deployerAccount);

        BlockContext context2 =
                bc.createNewBlockContext(bc.getBestBlock(), Collections.singletonList(tx2), false);

        AionBlock block = context2.block;
        byte[] originalReceiptsHash = block.getReceiptsRoot();

        // try importing an invalid block
        block.getHeader().setReceiptsRoot(new byte[32]);
        ImportResult result2 = bc.tryToConnect(context2.block);
        assertThat(result2).isEqualTo(ImportResult.INVALID_BLOCK);

        // try importing the correct block
        block.getHeader().setReceiptsRoot(originalReceiptsHash);
        ImportResult result3 = bc.tryToConnect(context2.block);
        assertThat(result3).isEqualTo(ImportResult.IMPORTED_BEST);
    }

    @Ignore
    @Test
    public void testModifer() throws InterruptedException {
        String testerByteCode =
                "0x605060405234156100105760006000fd5b5b3360006000508282909180600101839055555050505b61002c565b61016e8061003b6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680630acf8c2e14610049578063421b2d8b14610073578063e61a60bb1461009f57610043565b60006000fd5b34156100555760006000fd5b61005d6100c9565b6040518082815260100191505060405180910390f35b341561007f5760006000fd5b61009d600480808060100135903590916020019091929050506100d7565b005b34156100ab5760006000fd5b6100b3610113565b6040518082815260100191505060405180910390f35b6000600a90506100d4565b90565b600060005080600101549054339091149190141615156100f75760006000fd5b818160026000508282909180600101839055555050505b5b5050565b6000600260005080600101549054339091149190141615156101355760006000fd5b600b905061013e565b5b905600a165627a7a723058201d8c8bf193120213679831363ac65fecb0dcb5be8b65e6c0a1c97f4a7d3d3ef20029";
        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        // =======================================================================
        BigInteger nonce = BigInteger.ZERO;

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(testerByteCode),
                        1_000_000L,
                        1L);
        tx.sign(deployerAccount);
        assertThat(tx.isContractCreationTransaction()).isTrue();

        BlockContext context =
                bc.createNewBlockContext(bc.getBestBlock(), Collections.singletonList(tx), false);

        ImportResult result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Address contractAddress = tx.getContractAddress();
        Thread.sleep(1000L);

        // =======================================================================
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction tx2 =
                new AionTransaction(
                        nonce.toByteArray(),
                        contractAddress,
                        new byte[0],
                        ByteUtil.hexStringToBytes("0xe61a60bb"), // getEleven()
                        1_000_000L,
                        1L);
        tx2.sign(deployerAccount);

        BlockContext context2 =
                bc.createNewBlockContext(bc.getBestBlock(), Collections.singletonList(tx2), false);
        ImportResult result2 = bc.tryToConnect(context2.block);
        assertThat(result2).isEqualTo(ImportResult.IMPORTED_BEST);
        Thread.sleep(1000L);

        // assert failure
        AionTxInfo info2 =
                bc.getTransactionInfo(
                        context2.block.getTransactionsList().get(0).getTransactionHash());
        assertEquals("REVERT", info2.getReceipt().getError());

        // =======================================================================
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction tx3 =
                new AionTransaction(
                        nonce.toByteArray(),
                        contractAddress,
                        new byte[0],
                        ByteUtil.hexStringToBytes(
                                "0x421b2d8b"
                                        + Hex.toHexString(
                                                deployerAccount.getAddress())), // addUser(address)
                        1_000_000L,
                        1L);
        tx3.sign(deployerAccount);

        BlockContext context3 =
                bc.createNewBlockContext(bc.getBestBlock(), Collections.singletonList(tx3), false);
        ImportResult result3 = bc.tryToConnect(context3.block);
        assertThat(result3).isEqualTo(ImportResult.IMPORTED_BEST);
        Thread.sleep(1000L);

        // assert failure
        AionTxInfo info3 =
                bc.getTransactionInfo(
                        context3.block.getTransactionsList().get(0).getTransactionHash());
        assertEquals("", info3.getReceipt().getError());
        Thread.sleep(1000L);

        // =======================================================================
        nonce = nonce.add(BigInteger.ONE);

        AionTransaction tx4 =
                new AionTransaction(
                        nonce.toByteArray(),
                        contractAddress,
                        new byte[0],
                        ByteUtil.hexStringToBytes("0xe61a60bb"), // getEleven()
                        1_000_000L,
                        1L);
        tx4.sign(deployerAccount);

        BlockContext context4 =
                bc.createNewBlockContext(bc.getBestBlock(), Collections.singletonList(tx4), false);
        ImportResult result4 = bc.tryToConnect(context4.block);
        assertThat(result4).isEqualTo(ImportResult.IMPORTED_BEST);
        Thread.sleep(1000L);

        // assert failure
        AionTxInfo info4 =
                bc.getTransactionInfo(
                        context4.block.getTransactionsList().get(0).getTransactionHash());
        assertEquals("", info4.getReceipt().getError());
        assertEquals(11, new DataWord(info4.getReceipt().getTransactionOutput()).intValue());
    }

    @Test
    public void testBytes32Array() throws IOException {
        byte[] code = ContractUtils.getContractBody("Bytes32.sol", "Test");

        callData = Hex.decode("26121ff0");
        nrgLimit = 100000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "0011223344556677889900112233445566778899001122334455667788990011",
                Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testBytes32Array2() throws IOException {
        byte[] code = ContractUtils.getContractBody("Bytes32.sol", "Test");

        callData =
                Hex.decode(
                        "31e9552c"
                                + "00000000000000000000000000000010"
                                + "00000000000000000000000000000001"
                                + "00112233445566778899001122334455"
                                + "66778899001122334455667788990011");
        nrgLimit = 100000L;

        ExecutionContext ctx = newExecutionContext();
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(code, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "00000000000000000000000000000010000000000000000000000000000000010011223344556677889900112233445566778899001122334455667788990011",
                Hex.toHexString(result.getOutput()));
    }

    @After
    public void teardown() {}

    private static KernelInterfaceForFastVM wrapInKernelInterface(IRepositoryCache cache) {
        return new KernelInterfaceForFastVM(cache, true, false);
    }

    private ExecutionContext newExecutionContext() {
        return new ExecutionContext(
                null,
                txHash,
                address,
                origin,
                caller,
                nrgPrice,
                nrgLimit,
                callValue,
                callData,
                depth,
                kind,
                flags,
                blockCoinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockDifficulty);
    }
}
