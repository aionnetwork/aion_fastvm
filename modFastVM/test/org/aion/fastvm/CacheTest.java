package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.interfaces.vm.DataWord;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.vm.api.types.Address;
import org.aion.util.conversions.Hex;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class CacheTest {
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

    private SideEffects helper;

    private AionRepositoryCache repo;


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

    @Test
    @Ignore
    public void testCache() {
        callData = Hex.decode("8256cff3");
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

        long t1 = System.currentTimeMillis();
        int repeat = 1000;
        for (int i = 0; i < repeat; i++) {
            byte[] code = generateContract(i);
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
        byte[] ops = Hex.decode(contract);

        ops[104] = (byte) ((baseSum >>> 24) & 0xff);
        ops[105] = (byte) ((baseSum >>> 16) & 0xff);
        ops[106] = (byte) ((baseSum >>> 8) & 0xff);
        ops[107] = (byte) (baseSum & 0xff);
        return ops;
    }
}
