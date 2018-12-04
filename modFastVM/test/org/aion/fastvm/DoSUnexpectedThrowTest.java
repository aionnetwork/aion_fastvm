package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.DummyRepository;
import org.aion.vm.ExecutionContext;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

public class DoSUnexpectedThrowTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = Address.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
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

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 500;
        callValue = DataWord.ZERO;
        callData = new byte[0];
    }

    @Test
    public void testUnexpectedThrowFail() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("4dc80107"), address.toBytes(), new DataWord(bid).getData());
        nrgLimit = 69;
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
        TransactionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(ResultCode.OUT_OF_ENERGY, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowSuccess() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("4dc80107"), address.toBytes(), new DataWord(bid).getData());

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
        TransactionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowRefundAll1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("38e771ab"), address.toBytes(), new DataWord(bid).getData());

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
        TransactionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowRefundAll2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("38e771ab"), address.toBytes(), new DataWord(bid).getData());

        nrgLimit = 10000;
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
        TransactionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowRefundAllFail() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("38e771ab"), address.toBytes(), new DataWord(bid).getData());

        nrgLimit = 369;
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
        TransactionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(ResultCode.OUT_OF_ENERGY, result.getResultCode());
    }
}
