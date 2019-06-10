package org.aion.fastvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.interfaces.vm.DataWord;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.vm.api.types.Address;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.contract.ContractUtils;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

public class ContractTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = Address.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockNumber = 1;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private DataWord blockDifficulty = new DataWordImpl(0x100000000L);

    private DataWord nrgPrice;
    private long nrgLimit;
    private DataWord callValue;
    private byte[] callData;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    private AionRepositoryCache repo;


    public ContractTest() {}

    @Before
    public void setup() {
        nrgPrice = DataWordImpl.ONE;
        nrgLimit = 20000;
        callValue = DataWordImpl.ZERO;
        callData = new byte[0];

        RepositoryConfig repoConfig =
            new RepositoryConfig() {
                @Override
                public String getDbPath() {
                    return "";
                }

                @Override
                public PruneConfig getPruneConfig() {
                    return new CfgPrune(false);
                }

                @Override
                public ContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                    return props;
                }
            };

        repo = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
    }

    @Test
    public void testByteArrayMap() throws IOException {
        byte[] contract = ContractUtils.getContractBody("ByteArrayMap.sol", "ByteArrayMap");

        repo.saveCode(address, contract);

        callData = Hex.decode("26121ff0");
        nrgLimit = 1_000_000L;

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        callData = Hex.decode("e2179b8e");
        nrgLimit = 1_000_000L;

        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
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

        callData = ByteUtil.merge(Hex.decode("ff40565e"), new DataWordImpl(6L).getData());
        nrgLimit = 100_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWordImpl(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("231e93d4"), new DataWordImpl(6L).getData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWordImpl(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("1dae8972"), new DataWordImpl(6L).getData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWordImpl(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), new DataWordImpl(6L).getData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWordImpl(8L).toString(), Hex.toHexString(result.getReturnData()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), new DataWordImpl(1024L).getData());
        nrgLimit = 100_000L;
        ctx = newExecutionContext();
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
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
                        Hex.decode("2d7df21a"), address.toBytes(), new DataWordImpl(n).getData());
        nrgLimit = 100_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWordImpl(n).toString(), Hex.toHexString(result.getReturnData()));

        // verify internal transactions
        List<InternalTransactionInterface> txs = ctx.getSideEffects().getInternalTransactions();
        assertEquals(n - 1, txs.size());
        for (InternalTransactionInterface tx : txs) {
            System.out.println(tx);
        }

        // verify logs
        List<IExecutionLog> logs = ctx.getSideEffects().getExecutionLogs();
        assertEquals(n, logs.size());
        for (IExecutionLog log : logs) {
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
                        Hex.decode("2d7df21a"), address.toBytes(), new DataWordImpl(n).getData());
        nrgLimit = 10_000_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWordImpl(n).toString(), Hex.toHexString(result.getReturnData()));

        // verify internal transactions
        List<InternalTransactionInterface> txs = ctx.getSideEffects().getInternalTransactions();
        assertEquals(n - 1, txs.size());

        // verify logs
        List<IExecutionLog> logs = ctx.getSideEffects().getExecutionLogs();
        assertEquals(n, logs.size());
    }

    @Test
    public void testRecursive3() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        repo.saveCode(address, contract);

        int n = 1000;
        callData =
                ByteUtil.merge(
                        Hex.decode("2d7df21a"), address.toBytes(), new DataWordImpl(n).getData());
        nrgLimit = 10_000_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.REVERT, result.getResultCode());
    }

    private KernelInterfaceForFastVM wrapInKernelInterface(RepositoryCache cache) {
        return new KernelInterfaceForFastVM(
                cache,
                true,
                false,
                blockDifficulty,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockCoinbase);
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
