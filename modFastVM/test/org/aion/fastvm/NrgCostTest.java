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
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.fastvm.Instruction.Tier;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.DummyRepository;
import org.aion.vm.ExecutionContext;
import org.aion.vm.KernelInterfaceForFastVM;
import org.aion.zero.impl.db.AionRepositoryImpl;
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
    private AionAddress origin = AionAddress.wrap(RandomUtils.nextBytes(32));
    private AionAddress caller = origin;
    private AionAddress address = AionAddress.wrap(RandomUtils.nextBytes(32));

    private AionAddress blockCoinbase = AionAddress.wrap(RandomUtils.nextBytes(32));
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

    public NrgCostTest() {}

    @BeforeClass
    public static void note() {
        System.out.println(
                "\nNOTE: compilation time was not counted; extra cpu time was introduced for some opcodes.");
    }

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 10_000_000L;
        callValue = DataWord.ZERO;
        callData = new byte[0];

        // JVM warm up
        byte[] code = {0x00};
        ExecutionContext ctx =
                new ExecutionContext(
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
        DummyRepository repo = new DummyRepository();
        repo.addContract(address, code);
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

            ExecutionContext ctx =
                    new ExecutionContext(
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
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

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

            ExecutionContext ctx =
                    new ExecutionContext(
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
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

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

            ExecutionContext ctx =
                    new ExecutionContext(
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
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

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

            ExecutionContext ctx =
                    new ExecutionContext(
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
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

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

            ExecutionContext ctx =
                    new ExecutionContext(
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
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

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

            ExecutionContext ctx =
                    new ExecutionContext(
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
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

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

            ExecutionContext ctx =
                    new ExecutionContext(
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
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

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
                IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo = db.startTracking();
                for (int j = 0; j < transactions; j++) {
                    AionAddress address =
                            AionAddress.wrap(
                                    ByteUtil.merge(zeros28, ByteUtil.intToBytes(i * 1024 + j)));
                    repo.addStorageRow(
                            address,
                            new DataWord(RandomUtils.nextBytes(16)).toWrapper(),
                            new DataWord(RandomUtils.nextBytes(16)).toWrapper());
                }
                repo.flush();
                db.flush();
            }
            long t2 = System.nanoTime();

            long t3 = System.nanoTime();
            for (int i = 0; i < blocks; i++) {
                IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo = db.startTracking();
                for (int j = 0; j < transactions; j++) {
                    AionAddress address =
                            AionAddress.wrap(
                                    ByteUtil.merge(zeros28, ByteUtil.intToBytes(i * 1024 + j)));
                    repo.getStorageValue(
                            address, new DataWord(RandomUtils.nextBytes(16)).toWrapper());
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

    private static KernelInterfaceForFastVM wrapInKernelInterface(IRepositoryCache cache) {
        return new KernelInterfaceForFastVM(cache, true, false);
    }
}
