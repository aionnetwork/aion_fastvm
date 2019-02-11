package org.aion.fastvm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import org.aion.type.api.type.AionAddress;
import org.aion.type.api.util.ByteUtil;
import org.aion.type.api.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.vm.DummyRepository;
import org.aion.vm.api.interfaces.Address;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

public class DoSBlockGasLimitTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = AionAddress.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = AionAddress.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = AionAddress.wrap(RandomUtils.nextBytes(32));
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
        nrgLimit = 100000;
        callValue = DataWord.ZERO;
        callData = new byte[0];
    }

    @Test
    public void testGasOverLimit() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        callData = ByteUtil.merge(Hex.decode("e0aeb5e1"), new DataWord(nrgLimit).getData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
                vm.run(contract, ctx, new KernelInterfaceForFastVM(repo, true, false));
        System.out.println(result);
        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testGasOverLimitFail1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        nrgLimit = 200;

        callData = ByteUtil.merge(Hex.decode("e0aeb5e1"), new DataWord(nrgLimit).getData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
                vm.run(contract, ctx, new KernelInterfaceForFastVM(repo, true, false));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
    }

    @Test
    public void testGasOverLimitFail2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("BlockGasLimit.sol", "BlockGasLimit");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        BigInteger balance = BigInteger.valueOf(1000L);
        repo.addBalance(address, balance);

        nrgLimit = 405;

        callData = ByteUtil.merge(Hex.decode("e0aeb5e1"), new DataWord(nrgLimit).getData());

        ExecutionContext ctx = newExecutionContext();
        FastVM vm = new FastVM();
        FastVmTransactionResult result =
                vm.run(contract, ctx, new KernelInterfaceForFastVM(repo, true, false));
        System.out.println(result);
        assertEquals(FastVmResultCode.OUT_OF_NRG, result.getResultCode());
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
