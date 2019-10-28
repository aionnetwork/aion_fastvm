package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;

import org.aion.ExternalCapabilitiesForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;

import org.aion.util.ByteUtil;
import org.aion.contract.ContractUtils;
import org.aion.util.HexUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class DoSBlockGasLimitTest {

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
    private TransactionKind kind = TransactionKind.CALL;
    private int flags = 0;
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
        nrgLimit = 100000;
        callValue = BigInteger.ZERO;
        callData = new byte[0];
        repo = RepositoryForTesting.newRepository();
    }

    @Test
    public void testGasOverLimitPreFork() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        callData = ByteUtil.merge(HexUtil.decode("e0aeb5e1"), FvmDataWord.fromLong(nrgLimit).copyOfData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
                vm.runPre040Fork(contract, ctx, new ExternalStateForTesting(
                    repo,
                    new BlockchainForTesting(),
                    blockCoinbase,
                    blockDifficulty,
                    false,
                    true,
                    false,
                    blockNumber,
                    blockTimestamp,
                    blockNrgLimit,
                    false));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testGasOverLimitPostFork() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        callData = ByteUtil.merge(HexUtil.decode("e0aeb5e1"), FvmDataWord.fromLong(nrgLimit).copyOfData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
            vm.runPost040Fork(contract, ctx, new ExternalStateForTesting(
                repo,
                new BlockchainForTesting(),
                blockCoinbase,
                blockDifficulty,
                false,
                true,
                false,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                false));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testGasOverLimitFail1PreFork() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        nrgLimit = 200;

        callData = ByteUtil.merge(HexUtil.decode("e0aeb5e1"), FvmDataWord.fromLong(nrgLimit).copyOfData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
                vm.runPre040Fork(contract, ctx, new ExternalStateForTesting(
                    repo,
                    new BlockchainForTesting(),
                    blockCoinbase,
                    blockDifficulty,
                    false,
                    true,
                    false,
                    blockNumber,
                    blockTimestamp,
                    blockNrgLimit,
                    false));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    @Test
    public void testGasOverLimitFail1PostFork() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        nrgLimit = 200;

        callData = ByteUtil.merge(HexUtil.decode("e0aeb5e1"), FvmDataWord.fromLong(nrgLimit).copyOfData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
            vm.runPost040Fork(contract, ctx, new ExternalStateForTesting(
                repo,
                new BlockchainForTesting(),
                blockCoinbase,
                blockDifficulty,
                false,
                true,
                false,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                false));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    @Test
    public void testGasOverLimitFail2PreFork() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        nrgLimit = 405;

        callData = ByteUtil.merge(HexUtil.decode("e0aeb5e1"), FvmDataWord.fromLong(nrgLimit).copyOfData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
                vm.runPre040Fork(contract, ctx, new ExternalStateForTesting(
                    repo,
                    new BlockchainForTesting(),
                    blockCoinbase,
                    blockDifficulty,
                    false,
                    true,
                    false,
                    blockNumber,
                    blockTimestamp,
                    blockNrgLimit,
                    false));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    @Test
    public void testGasOverLimitFail2PostFork() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        repo.saveCode(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        nrgLimit = 405;

        callData = ByteUtil.merge(HexUtil.decode("e0aeb5e1"), FvmDataWord.fromLong(nrgLimit).copyOfData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
            vm.runPost040Fork(contract, ctx, new ExternalStateForTesting(
                repo,
                new BlockchainForTesting(),
                blockCoinbase,
                blockDifficulty,
                false,
                true,
                false,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                false));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    private ExecutionContext newExecutionContext() {
        return ExecutionContext.from(
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
