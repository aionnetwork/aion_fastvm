package org.aion.fastvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import org.aion.ExternalCapabilitiesForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.util.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.contract.ContractUtils;
import org.aion.util.types.AddressUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class FastVMTest {

    private byte[] txHash = RandomUtils.nextBytes(32);

    private AionAddress origin = new AionAddress(RandomUtils.nextBytes(32));
    private AionAddress caller = origin;
    private AionAddress address = new AionAddress(RandomUtils.nextBytes(32));

    private AionAddress blockCoinbase = new AionAddress(RandomUtils.nextBytes(32));
    private long blockNumber = 1;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private FvmDataWord blockDifficulty = FvmDataWord.fromLong(0x100000000L);

    private long nrgPrice;
    private long nrgLimit;
    private BigInteger callValue;
    private byte[] callData;

    private int depth = 0;
    private TransactionKind kind = TransactionKind.CALL;
    private int flags = 0;

    private RepositoryForTesting repo;

    public FastVMTest() {}

    @BeforeClass
    public static void setupCapabilities() {
        CapabilitiesProvider.installExternalCapabilities(new ExternalCapabilitiesForTesting());
    }

    @AfterClass
    public static void teardownCapabilities() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void setup() {
        nrgPrice = 1;
        nrgLimit = 20000;
        callValue = BigInteger.ZERO;
        callData = new byte[0];
        repo = RepositoryForTesting.newRepository();
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
                vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(19985, result.getEnergyRemaining());
        assertEquals(16, result.getReturnData().length);
    }

    @Test
    public void testGetCodeByAddress1() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6020600060E06F111111111111111111111111111111116F000000000000000000000000111111113C602060E0F3");

        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "0000000000000000000000000000000000000000000000000000000000000000",
                Hex.toHexString(result.getReturnData()));
    }

    @Test
    public void testGetCodeByAddress2() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6020600060E06F111111111111111111111111111111116F111111111111111111111111111111113C602060E0F3");
        repo.saveCode(AddressUtils.wrapAddress(
                "1111111111111111111111111111111111111111111111111111111111111111"), Hex.decode("11223344"));

        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "1122334400000000000000000000000000000000000000000000000000000000",
                Hex.toHexString(result.getReturnData()));
    }

    @Test
    public void testGetCodeSize() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6020600060E06F111111111111111111111111111111116F111111111111111111111111111111113B60E052601060E0F3");

        repo.saveCode(
                AddressUtils.wrapAddress(
                                "1111111111111111111111111111111111111111111111111111111111111111"),
                Hex.decode("11223344"));

        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals("00000000000000000000000000000004", Hex.toHexString(result.getReturnData()));
    }

    @Test
    public void testBalance() {
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        byte[] code =
                Hex.decode(
                        "6F111111111111111111111111111111116F111111111111111111111111111111113160E052601060E0F3");

        repo.addBalance(
            AddressUtils.wrapAddress(
                                "1111111111111111111111111111111111111111111111111111111111111111"),
                BigInteger.valueOf(0x34));

        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals("00000000000000000000000000000034", Hex.toHexString(result.getReturnData()));
    }

    @Test
    public void testCall() throws IOException {
        byte[] calleeCtr = ContractUtils.getContractBody("Call.sol", "Callee");
        byte[] callerCtr = ContractUtils.getContractBody("Call.sol", "Caller");

        caller =
            AddressUtils.wrapAddress(
                                "3333333333333333333333333333333333333333333333333333333333333333");
        origin = caller;
        address =
            AddressUtils.wrapAddress(
                                "2222222222222222222222222222222222222222222222222222222222222222");

        callData =
                Hex.decode(
                        "fc68521a1111111111111111111111111111111111111111111111111111111111111111");

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();

        repo.createAccount(
            AddressUtils.wrapAddress(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        repo.createAccount(
            AddressUtils.wrapAddress(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
        repo.saveCode(
            AddressUtils.wrapAddress(
                                "1111111111111111111111111111111111111111111111111111111111111111"),
                calleeCtr);
        repo.saveCode(
            AddressUtils.wrapAddress(
                                "2222222222222222222222222222222222222222222222222222222222222222"),
                callerCtr);

        FastVmTransactionResult result = vm.runPre040Fork(callerCtr, ctx, newState(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals("00000000000000000000000000000003", Hex.toHexString(result.getReturnData()));
    }

    @Test
    public void testCreate() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Create.sol", "Create");

        callData = Hex.decode("26121ff0");
        nrgLimit = 600_000L;

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();


        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);

        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(32, result.getReturnData().length);
    }

    @Test
    public void testDynamicArray1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("a76af697"), FvmDataWord.fromLong(512L).copyOfData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testDynamicArray2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("a76af697"), FvmDataWord.fromLong(1_000_000_000L).copyOfData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testDynamicArray3() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("e59cc974"), FvmDataWord.fromLong(512L).copyOfData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testDynamicArray4() throws IOException {
        byte[] contract = ContractUtils.getContractBody("DynamicArray.sol", "DynamicArray");

        callData = ByteUtil.merge(Hex.decode("e59cc974"), FvmDataWord.fromLong(1_000_000_000L).copyOfData());
        nrgLimit = 100_000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, contract);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
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

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(Hex.decode(code), ctx, newState(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testLOG0Loop() {
        String code = "5b" + "632fffffff6000a0" + "600056";

        ExecutionContext ctx = newExecutionContext();

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(Hex.decode(code), ctx, newState(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testSHA3Loop() {
        String code = "5b" + "632fffffff60002050" + "600056";

        ExecutionContext ctx = newExecutionContext();

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(Hex.decode(code), ctx, newState(repo));
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

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(Hex.decode(code), ctx, newState(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testShortConstructor() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Short.sol", "Short");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 100000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertTrue(result.getReturnData().length > 0);
    }

    @Test
    public void testLongConstructor1() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Long.sol", "Long");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 100000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testLongConstructor2() throws IOException {
        byte[] code = ContractUtils.getContractDeployer("Long.sol", "LongCreator");

        callData = ByteUtil.EMPTY_BYTE_ARRAY;
        nrgLimit = 500_000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);

        // NOTE: after the byzantine fork, if the CREATE call fails, the
        // reserved gas got revertd.
        assertEquals(FastVmResultCode.REVERT, result.getResultCode());
        assertTrue(result.getEnergyRemaining() > 0);
    }

    @Test
    public void testBytes32Array() throws IOException {
        byte[] code = ContractUtils.getContractBody("Bytes32.sol", "Test");

        callData = Hex.decode("26121ff0");
        nrgLimit = 100000L;

        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "0011223344556677889900112233445566778899001122334455667788990011",
                Hex.toHexString(result.getReturnData()));
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
        repo.saveCode(address, code);

        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(
                "00000000000000000000000000000010000000000000000000000000000000010011223344556677889900112233445566778899001122334455667788990011",
                Hex.toHexString(result.getReturnData()));
    }

    @Test
    public void testLocalVarDepth() throws IOException {
        byte[] code = ContractUtils.getContractBody("LocalVarDepth.sol", "LocalVar");

        callData = Hex.decode("f220ff7b"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000003"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "0000000000000000000000000000000000000000000000000000000000000005"
                + "0000000000000000000000000000000000000000000000000000000000000006"
                + "0000000000000000000000000000000000000000000000000000000000000007"
                + "0000000000000000000000000000000000000000000000000000000000000008"
                + "0000000000000000000000000000000000000000000000000000000000000009"
                + "000000000000000000000000000000000000000000000000000000000000000a"
                + "000000000000000000000000000000000000000000000000000000000000000b"
                + "000000000000000000000000000000000000000000000000000000000000000c"
                + "000000000000000000000000000000000000000000000000000000000000000d"
                + "000000000000000000000000000000000000000000000000000000000000000e");
        nrgLimit = 1000_000L;



        ExecutionContext ctx = newExecutionContext();
        repo.saveCode(address, code);


        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(code, ctx, newState(repo));
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());

        result = vm.runPost040Fork(code, ctx, newState(repo));
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @After
    public void teardown() {}

    private IExternalStateForFvm newState(RepositoryForTesting cache) {
        return new ExternalStateForTesting(
            cache,
            new BlockchainForTesting(),
            blockCoinbase,
            blockDifficulty,
            false,
            true,
            false,
            blockNumber,
            blockTimestamp,
            blockNrgLimit);
    }

    private ExecutionContext newExecutionContext() {
        return ExecutionContext.from(
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
