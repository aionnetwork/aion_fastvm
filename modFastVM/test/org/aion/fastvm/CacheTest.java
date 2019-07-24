package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import org.aion.ExternalCapabilitiesForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.util.HexUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class CacheTest {
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
    private TransactionKind kind = TransactionKind.CREATE;
    private int flags = 0;

    private SideEffects helper;

    private RepositoryForTesting repo;

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
        helper = new SideEffects();
        repo = RepositoryForTesting.newRepository();
    }

    @Test
    @Ignore
    public void testCache() {
        callData = HexUtil.decode("8256cff3");
        ExecutionContext ctx =
                ExecutionContext.from(
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

        long t1 = System.currentTimeMillis();
        int repeat = 1000;
        for (int i = 0; i < repeat; i++) {
            byte[] code = generateContract(i);
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

            if (i % 100 == 0) {
                System.out.println(i + "/" + repeat);
            }
        }
        long t2 = System.currentTimeMillis();

        System.out.println(t2 - t1);
    }

    private byte[] generateContract(int baseSum) {
        // pragma solidity ^0.4.0;
        //
        // contract simple {
        // function f(uint n) constant returns(uint) {
        // uint sum = 0x12345678;
        // for (uint i = 0; i < n; i++) {
        // sum = sum + i;
        // }
        // return sum;
        // }
        // }
        final String contract =
                "60506040526000356c01000000000000000000000000900463ffffffff1680638256cff314602d575b600080fd5b3415603757600080fd5b604b60048080359060100190919050506061565b6040518082815260100191505060405180910390f35b600080600063123456789150600090505b83811015608b5780820191505b80806001019150506072565b8192505b50509190505600a165627a7a7230582031f5099d322de19215175c3f31d2afdc1cb3ce6ffd6c8541681584cad8a075c60029";
        byte[] ops = HexUtil.decode(contract);

        ops[104] = (byte) ((baseSum >>> 24) & 0xff);
        ops[105] = (byte) ((baseSum >>> 16) & 0xff);
        ops[106] = (byte) ((baseSum >>> 8) & 0xff);
        ops[107] = (byte) (baseSum & 0xff);
        return ops;
    }
}
