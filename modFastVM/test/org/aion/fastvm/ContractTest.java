package org.aion.fastvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.aion.ExternalCapabilitiesForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.contract.ContractUtils;
import org.aion.types.InternalTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ContractTest {

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

    public ContractTest() {}

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
    public void testByteArrayMap() throws IOException {
        byte[] contract = ContractUtils.getContractBody("ByteArrayMap.sol", "ByteArrayMap");

        repo.saveCode(address, contract);

        callData = Hex.decode("26121ff0");
        nrgLimit = 1_000_000L;

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        callData = Hex.decode("e2179b8e");
        nrgLimit = 1_000_000L;

        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        assertEquals(
                "000000000000000000000000000000100000000000000000000000000000040061000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000062",
                Hex.toHexString(result.getReturnData()));
    }

    @Test
    public void testFibonacci() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Fibonacci.sol", "Fibonacci");

        repo.saveCode(address, contract);

        callData = ByteUtil.merge(Hex.decode("ff40565e"), FvmDataWord.fromLong(6L).copyOfData());
        nrgLimit = 100_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(FvmDataWord.fromLong(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("231e93d4"), FvmDataWord.fromLong(6L).copyOfData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(FvmDataWord.fromLong(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("1dae8972"), FvmDataWord.fromLong(6L).copyOfData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(FvmDataWord.fromLong(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), FvmDataWord.fromLong(6L).copyOfData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(FvmDataWord.fromLong(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), FvmDataWord.fromLong(1024L).copyOfData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.REVERT, result.getResultCode());
        assertTrue(result.getEnergyRemaining() > 0);
    }

    @Test
    public void testRecursive1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        repo.saveCode(address, contract);

        int n = 10;
        callData =
                ByteUtil.merge(
                        Hex.decode("2d7df21a"),
                        address.toByteArray(),
                        FvmDataWord.fromInt(n).copyOfData());
        nrgLimit = 100_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(FvmDataWord.fromInt(n).toString(), Hex.toHexString(result.getReturnData()));

        // verify internal transactions
        List<InternalTransaction> txs = ctx.getSideEffects().getInternalTransactions();
        assertEquals(n - 1, txs.size());
        for (InternalTransaction tx : txs) {
            System.out.println(tx);
        }

        // verify logs
        List<Log> logs = ctx.getSideEffects().getExecutionLogs();
        assertEquals(n, logs.size());
        for (Log log : logs) {
            System.out.println(log);
        }
    }

    @Test
    public void testRecursive2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        repo.saveCode(address, contract);

        int n = 128;
        callData =
                ByteUtil.merge(
                        Hex.decode("2d7df21a"),
                        address.toByteArray(),
                        FvmDataWord.fromInt(n).copyOfData());
        nrgLimit = 10_000_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(FvmDataWord.fromInt(n).toString(), Hex.toHexString(result.getReturnData()));

        // verify internal transactions
        List<InternalTransaction> txs = ctx.getSideEffects().getInternalTransactions();
        assertEquals(n - 1, txs.size());

        // verify logs
        List<Log> logs = ctx.getSideEffects().getExecutionLogs();
        assertEquals(n, logs.size());
    }

    @Test
    public void testRecursive3() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        repo.saveCode(address, contract);

        int n = 1000;
        callData =
                ByteUtil.merge(
                        Hex.decode("2d7df21a"),
                        address.toByteArray(),
                        FvmDataWord.fromInt(n).copyOfData());
        nrgLimit = 10_000_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newStateImpl(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.REVERT, result.getResultCode());
    }

    private IExternalStateForFvm newStateImpl(RepositoryForTesting cache) {
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
