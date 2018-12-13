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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.DummyRepository;
import org.aion.vm.ExecutionContext;
import org.aion.vm.KernelInterfaceForFastVM;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

public class ContractTest {

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

    public ContractTest() throws CloneNotSupportedException {}

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 20000;
        callValue = DataWord.ZERO;
        callData = new byte[0];
    }

    @Test
    public void testByteArrayMap() throws IOException {
        byte[] contract = ContractUtils.getContractBody("ByteArrayMap.sol", "ByteArrayMap");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        callData = Hex.decode("26121ff0");
        nrgLimit = 1_000_000L;

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
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        callData = Hex.decode("e2179b8e");
        nrgLimit = 1_000_000L;

        ctx =
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
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        assertEquals(
                "000000000000000000000000000000100000000000000000000000000000040061000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000062",
                Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testFibonacci() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Fibonacci.sol", "Fibonacci");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        callData = ByteUtil.merge(Hex.decode("ff40565e"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
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
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("231e93d4"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
        ctx =
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
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("1dae8972"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
        ctx =
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
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
        ctx =
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
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), new DataWord(1024L).getData());
        nrgLimit = 100_000L;
        ctx =
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
        vm = new FastVM();
        result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.REVERT, result.getResultCode());
        assertTrue(result.getEnergyRemaining() > 0);
    }

    @Test
    public void testRecursive1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        int n = 10;
        callData =
                ByteUtil.merge(
                        Hex.decode("2d7df21a"), address.toBytes(), new DataWord(n).getData());
        nrgLimit = 100_000L;
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
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWord(n).toString(), Hex.toHexString(result.getOutput()));

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

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        int n = 128;
        callData =
                ByteUtil.merge(
                        Hex.decode("2d7df21a"), address.toBytes(), new DataWord(n).getData());
        nrgLimit = 10_000_000L;
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
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        assertEquals(new DataWord(n).toString(), Hex.toHexString(result.getOutput()));

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

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        int n = 1000;
        callData =
                ByteUtil.merge(
                        Hex.decode("2d7df21a"), address.toBytes(), new DataWord(n).getData());
        nrgLimit = 10_000_000L;
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
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.run(contract, ctx, wrapInKernelInterface(repo));
        System.out.println(result);

        // verify result
        assertEquals(FastVmResultCode.REVERT, result.getResultCode());
    }

    private static KernelInterfaceForFastVM wrapInKernelInterface(IRepositoryCache cache) {
        return new KernelInterfaceForFastVM(cache, true, false);
    }
}
