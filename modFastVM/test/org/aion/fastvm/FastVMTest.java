package org.aion.fastvm;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.ExecutionResult.Code;
import org.aion.vm.TransactionResult;
import org.aion.vm.types.DataWord;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.Assert.*;

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
        assertEquals(Code.FAILURE, result.getCode());
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
}
