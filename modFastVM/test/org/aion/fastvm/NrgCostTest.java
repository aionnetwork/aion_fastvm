package org.aion.fastvm;

import static org.aion.fastvm.Instruction.ADD;
import static org.aion.fastvm.Instruction.ADDMOD;
import static org.aion.fastvm.Instruction.ADDRESS;
import static org.aion.fastvm.Instruction.AND;
import static org.aion.fastvm.Instruction.BYTE;
import static org.aion.fastvm.Instruction.CALLDATALOAD;
import static org.aion.fastvm.Instruction.CALLDATASIZE;
import static org.aion.fastvm.Instruction.CALLER;
import static org.aion.fastvm.Instruction.CALLVALUE;
import static org.aion.fastvm.Instruction.CODESIZE;
import static org.aion.fastvm.Instruction.COINBASE;
import static org.aion.fastvm.Instruction.DIFFICULTY;
import static org.aion.fastvm.Instruction.DIV;
import static org.aion.fastvm.Instruction.DUP1;
import static org.aion.fastvm.Instruction.EQ;
import static org.aion.fastvm.Instruction.EXP;
import static org.aion.fastvm.Instruction.GAS;
import static org.aion.fastvm.Instruction.GASLIMIT;
import static org.aion.fastvm.Instruction.GASPRICE;
import static org.aion.fastvm.Instruction.GT;
import static org.aion.fastvm.Instruction.ISZERO;
import static org.aion.fastvm.Instruction.JUMPI;
import static org.aion.fastvm.Instruction.LT;
import static org.aion.fastvm.Instruction.MLOAD;
import static org.aion.fastvm.Instruction.MOD;
import static org.aion.fastvm.Instruction.MSIZE;
import static org.aion.fastvm.Instruction.MSTORE;
import static org.aion.fastvm.Instruction.MSTORE8;
import static org.aion.fastvm.Instruction.MUL;
import static org.aion.fastvm.Instruction.MULMOD;
import static org.aion.fastvm.Instruction.NOT;
import static org.aion.fastvm.Instruction.NUMBER;
import static org.aion.fastvm.Instruction.OR;
import static org.aion.fastvm.Instruction.ORIGIN;
import static org.aion.fastvm.Instruction.PC;
import static org.aion.fastvm.Instruction.POP;
import static org.aion.fastvm.Instruction.PUSH1;
import static org.aion.fastvm.Instruction.SDIV;
import static org.aion.fastvm.Instruction.SGT;
import static org.aion.fastvm.Instruction.SHA3;
import static org.aion.fastvm.Instruction.SIGNEXTEND;
import static org.aion.fastvm.Instruction.SLT;
import static org.aion.fastvm.Instruction.SMOD;
import static org.aion.fastvm.Instruction.SUB;
import static org.aion.fastvm.Instruction.SWAP1;
import static org.aion.fastvm.Instruction.TIMESTAMP;
import static org.aion.fastvm.Instruction.XOR;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
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
import org.aion.vm.api.types.ByteArrayWrapper;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.fastvm.Instruction.Tier;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NrgCostTest {
    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address;

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

    public NrgCostTest() {}

    private AionRepositoryCache repo;

    @BeforeClass
    public static void note() {
        System.out.println(
                "\nNOTE: compilation time was not counted; extra cpu time was introduced for some opcodes.");
    }

    @Before
    public void setup() {
        nrgPrice = DataWordImpl.ONE;
        nrgLimit = 10_000_000L;
        callValue = DataWordImpl.ZERO;
        callData = new byte[0];

        address = Address.wrap(RandomUtils.nextBytes(32));

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

        // JVM warm up
        byte[] code = {0x00};
        ExecutionContext ctx = newExecutionContext();
        repo.createAccount(address);
        repo.saveCode(address, code);
        for (int i = 0; i < 10000; i++) {
            new FastVM().run(code, ctx, wrapInKernelInterface(repo));
        }
    }

    private byte[] repeat(int n, Object... codes) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int i = 0; i < n; i++) {
            for (Object o : codes) {
                buf.write(
                        o instanceof Instruction
                                ? ((Instruction) o).code()
                                : ((Integer) o).byteValue());
            }
        }

        return buf.toByteArray();
    }

    @Test
    public void test1Base() {
        /**
         * Number of repeats of the instruction. You may get different results by adjusting this
         * number. It's a tradeoff between the instruction execution and system cost. We should only
         * interpret the results relatively.
         */
        int x = 64;

        /** Number of VM invoking. */
        int y = 1000;

        /** Energy cost for this group of instructions. */
        int z = Tier.BASE.cost(); // energy cost

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the Base tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {
            ADDRESS,
            ORIGIN,
            CALLER,
            CALLVALUE,
            CALLDATASIZE,
            CODESIZE,
            GASPRICE,
            COINBASE,
            TIMESTAMP,
            NUMBER,
            DIFFICULTY,
            GASLIMIT, /* POP, */
            PC,
            MSIZE,
            GAS
        };

        for (Instruction inst : instructions) {
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();
            repo.createAccount(address);
            repo.saveCode(address, code);

            // compile
            FastVmTransactionResult result =
                    new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }

        System.out.println();
    }

    @Test
    public void test2VeryLow() {
        int x = 64;
        int y = 1000;
        int z = Tier.VERY_LOW.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the VeryLow tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {
            ADD,
            SUB,
            NOT,
            LT,
            GT,
            SLT,
            SGT,
            EQ,
            ISZERO,
            AND,
            OR,
            XOR,
            BYTE,
            CALLDATALOAD,
            MLOAD,
            MSTORE,
            MSTORE8, /* PUSH1, */
            DUP1,
            SWAP1
        };

        for (Instruction inst : instructions) {
            callData =
                    Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();
            repo.createAccount(address);
            repo.saveCode(address, code);

            // compile
            FastVmTransactionResult result =
                    new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test3Low() {
        int x = 64;
        int y = 1000;
        int z = Tier.LOW.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the Low tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {MUL, DIV, SDIV, MOD, SMOD, SIGNEXTEND};

        for (Instruction inst : instructions) {
            callData =
                    Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();
            repo.createAccount(address);
            repo.saveCode(address, code);

            // compile
            FastVmTransactionResult result =
                    new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test4Mid() {
        int x = 64;
        int y = 1000;
        int z = Tier.MID.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the Mid tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {
            ADDMOD, MULMOD, /* JUMP */
        };

        for (Instruction inst : instructions) {
            callData =
                    Hex.decode(
                            "000000000000000000000000000000010000000000000000000000000000000200000000000000000000000000000003");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();
            repo.createAccount(address);
            repo.saveCode(address, code);

            // compile
            FastVmTransactionResult result =
                    new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test5High() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {JUMPI};

        for (Instruction inst : instructions) {
            callData =
                    Hex.decode(
                            "000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000003");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();
            repo.createAccount(address);
            repo.saveCode(address, code);

            // compile
            FastVmTransactionResult result =
                    new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test6SHA3() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {SHA3};

        for (Instruction inst : instructions) {
            callData =
                    Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(x, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst, POP, POP);

            ExecutionContext ctx = newExecutionContext();
            repo.createAccount(address);
            repo.saveCode(address, code);

            // compile
            FastVmTransactionResult result =
                    new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            System.out.println(result);
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test7Exp() {
        int x = 64;
        int y = 1000;
        int z = 10;

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the VeryLow tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {EXP};

        for (Instruction inst : instructions) {
            callData =
                    Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();
            repo.createAccount(address);
            repo.saveCode(address, code);

            // compile
            FastVmTransactionResult result =
                    new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test8Remaining() {

        for (Instruction inst : Instruction.values()) {
            if (inst.tier() != Tier.BASE
                    && inst.tier() != Tier.LOW
                    && inst.tier() != Tier.VERY_LOW
                    && inst.tier() != Tier.MID
                    && inst.tier() != Tier.HIGH) {
                System.out.println(inst.name() + "\t" + inst.tier());
            }
        }
    }

    /**
     * The following test case benchmarks database performance. It maximizes database read/write and
     * minimizes cache usage.
     *
     * <p>It simulate the situation where there are <code>X</code> blocks, each of which contains
     * <code>Y</code> transactions. Each transaction reads/writes one storage entry of an unique
     * account. This whole process is repeated <code>Z</code> time.
     *
     * <p>There will be <code>X * Y</code> accounts created. Trie serialization/deserialization is
     * expected to happen during the test.
     *
     * <p>NOTE: Before you run this test, make sure the database is empty, to get consistent
     * results.
     */
    @Test
    @Ignore
    public void testDB() {
        AionRepositoryImpl db = AionRepositoryImpl.inst();
        byte[] zeros28 = new byte[28];

        int repeat = 1000;
        int blocks = 32;
        int transactions = 1024;

        long totalWrite = 0;
        long totalRead = 0;
        for (int r = 1; r <= repeat; r++) {

            long t1 = System.nanoTime();
            for (int i = 0; i < blocks; i++) {
                RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo = db.startTracking();
                for (int j = 0; j < transactions; j++) {
                    Address address =
                            Address.wrap(
                                    ByteUtil.merge(zeros28, ByteUtil.intToBytes(i * 1024 + j)));
                    repo.addStorageRow(
                            address,
                            new DataWordImpl(RandomUtils.nextBytes(16)).toWrapper(),
                            new ByteArrayWrapper(
                                    new DataWordImpl(RandomUtils.nextBytes(16)).getNoLeadZeroesData()));
                }
                repo.flush();
                db.flush();
            }
            long t2 = System.nanoTime();

            long t3 = System.nanoTime();
            for (int i = 0; i < blocks; i++) {
                RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo = db.startTracking();
                for (int j = 0; j < transactions; j++) {
                    Address address =
                            Address.wrap(
                                    ByteUtil.merge(zeros28, ByteUtil.intToBytes(i * 1024 + j)));
                    new DataWordImpl(
                            repo.getStorageValue(
                                            address,
                                            new DataWordImpl(RandomUtils.nextBytes(16)).toWrapper())
                                    .getData());
                }
                repo.flush();
                db.flush();
            }
            long t4 = System.nanoTime();

            totalWrite += (t2 - t1);
            totalRead += (t4 - t3);
            System.out.printf(
                    "write = %7d,  read = %7d,  avg. write = %7d,  avg. read = %7d\n",
                    (t2 - t1) / (blocks * transactions),
                    (t4 - t3) / (blocks * transactions),
                    totalWrite / (r * blocks * transactions),
                    totalRead / (r * blocks * transactions));
        }
    }

    private  KernelInterfaceForFastVM wrapInKernelInterface(RepositoryCache cache) {
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
