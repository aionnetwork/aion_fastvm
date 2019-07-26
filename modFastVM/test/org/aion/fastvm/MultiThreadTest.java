package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.contract.ContractUtils;
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
    private FvmDataWord blockDifficulty = FvmDataWord.fromLong(0x100000000L);

    private FvmDataWord nrgPrice;
    private long nrgLimit;
    private FvmDataWord callValue;
    private byte[] callData;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    private SideEffects helper;
    private RepositoryForTesting repo;


    public MultiThreadTest() {}

    @Before
    public void setup() {
        nrgPrice = FvmDataWord.fromLong(1);
        nrgLimit = 20000;
        callValue = FvmDataWord.fromLong(0);
        callData = new byte[0];
        helper = new SideEffects();
        repo = RepositoryForTesting.newRepository();
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
                                            Hex.decode("8256cff3"), FvmDataWord.fromLong(64).copyOfData());

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
                                    vm.runPre040Fork(
                                            code,
                                            ctx,
                                            new ExternalStateForTesting(
                                                repo,
                                                new BlockchainForTesting(),
                                                blockCoinbase,
                                                blockDifficulty,
                                                false,
                                                true,
                                                false,
                                                blockNumber,
                                                blockTimestamp,
                                                blockNrgLimit));
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
