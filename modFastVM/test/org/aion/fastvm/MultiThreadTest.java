package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.aion.types.AionAddress;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.PruneConfig;
import org.aion.mcf.db.RepositoryConfig;
import org.aion.interfaces.vm.DataWord;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.contract.ContractUtils;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

public class MultiThreadTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private AionAddress origin = new AionAddress(RandomUtils.nextBytes(32));
    private AionAddress caller = origin;
    private AionAddress address = new AionAddress(RandomUtils.nextBytes(32));

    private AionAddress blockCoinbase = new AionAddress(RandomUtils.nextBytes(32));
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

    private SideEffects helper;
    private AionRepositoryCache repo;


    public MultiThreadTest() {}

    @Before
    public void setup() {
        nrgPrice = DataWordImpl.ONE;
        nrgLimit = 20000;
        callValue = DataWordImpl.ZERO;
        callData = new byte[0];
        helper = new SideEffects();

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

    private static AtomicInteger count = new AtomicInteger(0);

    @Test
    public void testRun() throws InterruptedException {
        int numThread = 1;
        ExecutorService es = Executors.newFixedThreadPool(numThread);

        long t1 = System.nanoTime();
        int repeat = 100;
        for (int i = 0; i < repeat; i++) {
            es.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            byte[] code = generateContract(count.incrementAndGet());

                            callData =
                                    ByteUtil.merge(
                                            Hex.decode("8256cff3"), new DataWordImpl(64).getData());

                            ExecutionContext ctx =
                                    new ExecutionContext(
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

                            FastVM vm = new FastVM();
                            FastVmTransactionResult result =
                                    vm.run(
                                            code,
                                            ctx,
                                            new KernelInterfaceForFastVM(
                                                    repo,
                                                    true,
                                                    false,
                                                    blockDifficulty,
                                                    blockNumber,
                                                    blockTimestamp,
                                                    blockNrgLimit,
                                                    blockCoinbase));
                            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
                        }
                    });
        }

        es.shutdown();
        es.awaitTermination(3, TimeUnit.MINUTES);
        long t2 = System.nanoTime();

        System.out.println("testRun(): " + (t2 - t1) / repeat / 1000 + " μs/call");
    }

    private byte[] generateContract(int baseSum) {
        try {
            String code = Hex.toHexString(ContractUtils.getContractBody("Simple.sol", "Simple"));

            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.putInt(baseSum);
            byte[] bytes = buf.array();

            code = code.replace("12345678", Hex.toHexString(bytes));
            return Hex.decode(code);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
