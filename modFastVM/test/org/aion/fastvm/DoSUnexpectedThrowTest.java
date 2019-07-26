package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
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

public class DoSUnexpectedThrowTest {

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

    private RepositoryForTesting repo;


    @Before
    public void setup() {
        nrgPrice = FvmDataWord.fromLong(1);
        nrgLimit = 500;
        callValue = FvmDataWord.fromLong(0);
        callData = new byte[0];
        repo = RepositoryForTesting.newRepository();
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

    @Test
    public void testUnexpectedThrowFail() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("4dc80107"), address.toByteArray(), FvmDataWord.fromInt(bid).copyOfData());
        nrgLimit = 69;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowSuccess() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("4dc80107"), address.toByteArray(), FvmDataWord.fromInt(bid).copyOfData());

        nrgLimit = 100_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowRefundAll1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("38e771ab"), address.toByteArray(), FvmDataWord.fromInt(bid).copyOfData());

        nrgLimit = 100_000L;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowRefundAll2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("38e771ab"), address.toByteArray(), FvmDataWord.fromInt(bid).copyOfData());

        nrgLimit = 10000;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testUnexpectedThrowRefundAllFail() throws IOException {
        byte[] contract = ContractUtils.getContractBody("FailedRefund.sol", "FailedRefund");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        int bid = 100;

        callData =
                ByteUtil.merge(
                        Hex.decode("38e771ab"), address.toByteArray(), FvmDataWord.fromInt(bid).copyOfData());

        nrgLimit = 369;
        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result = vm.runPre040Fork(contract, ctx, newState(repo));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    private IExternalStateForFvm newState(RepositoryForTesting cache) {
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
}
