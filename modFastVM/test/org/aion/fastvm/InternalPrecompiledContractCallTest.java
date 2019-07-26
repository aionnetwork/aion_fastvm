package org.aion.fastvm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Properties;
import org.aion.ExternalStateForTesting;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.PruneConfig;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.db.RepositoryConfig;
import org.aion.precompiled.PrecompiledFactoryForTesting;
import org.aion.precompiled.PrecompiledForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Tests the CALL, DELEGATECALL, and CALLCODE paths are invoking a precompiled contract during an
 * internal call to the {@link Callback} class.
 */
public class InternalPrecompiledContractCallTest {
    private static final long ENERGY_LIMIT = 100_000L;
    private PrecompiledForTesting precompiledContract;
    private AionAddress precompiledAddress;
    private AionAddress originAddress;  // the address that 'sent' the external transaction.
    private AionAddress senderAddress;  // the address making the internal call.

    @Before
    public void setup() {
        this.originAddress = randomAddress();
        this.senderAddress = randomAddress();
        this.precompiledContract = new PrecompiledForTesting();
        this.precompiledAddress = randomAddress();
    }

    @After
    public void tearDown() {
        this.originAddress = null;
        this.senderAddress = null;
        this.precompiledContract = null;
        this.precompiledAddress = null;
    }

    @Test
    public void testUnsuccessfulDelegateCall() {
        FastVmTransactionResult precompiledResult = new FastVmTransactionResult(FastVmResultCode.FAILURE, ENERGY_LIMIT);
        this.precompiledContract.result = precompiledResult;
        PrecompiledFactoryForTesting.registerPrecompiledContract(this.precompiledAddress, this.precompiledContract);

        // We push a context and state onto the stack, this represents the external transaction.
        // Note that because we are doing delegate-call, the original destination address becomes
        // the new destination address, and we are just proving the point here.
        setupCallbackStack(this.originAddress, this.precompiledAddress);

        BigInteger senderOriginalBalance = BigInteger.valueOf(500);
        addBalanceToAccount(this.senderAddress, senderOriginalBalance);

        BigInteger originOriginalBalance = BigInteger.valueOf(750);
        addBalanceToAccount(this.originAddress, originOriginalBalance);

        BigInteger transferAmount = BigInteger.valueOf(220);
        byte[] encodedContext = encodeContext(this.senderAddress, this.senderAddress, transferAmount, new byte[0], ExecutionContext.DELEGATECALL);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.performCall(encodedContext, null));

        assertResultCorrect(precompiledResult, result);
        assertNonceIsZero(this.senderAddress);
        assertNonceIsZero(this.precompiledAddress);
        assertNonceIsZero(this.originAddress);
        assertBalanceCorrect(this.senderAddress, senderOriginalBalance);
        assertBalanceCorrect(this.precompiledAddress, BigInteger.ZERO);
        assertBalanceCorrect(this.originAddress, originOriginalBalance);

        // Verify that our internal transaction was marked rejected.
        Assert.assertTrue(Callback.context().getSideEffects().getInternalTransactions().get(0).isRejected);

        clearCallbackStack();
        PrecompiledFactoryForTesting.clearContracts();
    }

    @Test
    public void testSuccessfulDelegateCall() {
        FastVmTransactionResult precompiledResult = new FastVmTransactionResult(FastVmResultCode.SUCCESS, 500);
        this.precompiledContract.result = precompiledResult;
        PrecompiledFactoryForTesting.registerPrecompiledContract(this.precompiledAddress, this.precompiledContract);

        // We push a context and state onto the stack, this represents the external transaction.
        // Note that because we are doing delegate-call, the original destination address becomes
        // the new destination address, and we are just proving the point here.
        setupCallbackStack(this.originAddress, this.precompiledAddress);

        BigInteger senderOriginalBalance = BigInteger.valueOf(500);
        addBalanceToAccount(this.senderAddress, senderOriginalBalance);

        BigInteger originOriginalBalance = BigInteger.valueOf(750);
        addBalanceToAccount(this.originAddress, originOriginalBalance);

        BigInteger transferAmount = BigInteger.valueOf(220);
        byte[] encodedContext = encodeContext(this.senderAddress, this.senderAddress, transferAmount, new byte[0], ExecutionContext.DELEGATECALL);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.performCall(encodedContext, null));

        assertResultCorrect(precompiledResult, result);
        assertNonceIsZero(this.senderAddress);
        assertNonceIsZero(this.precompiledAddress);
        assertNonceIsZero(this.originAddress);
        assertBalanceCorrect(this.senderAddress, senderOriginalBalance);
        assertBalanceCorrect(this.precompiledAddress, BigInteger.ZERO);
        assertBalanceCorrect(this.originAddress, originOriginalBalance);

        // Verify that our internal transaction was NOT marked rejected.
        Assert.assertFalse(Callback.context().getSideEffects().getInternalTransactions().get(0).isRejected);

        clearCallbackStack();
        PrecompiledFactoryForTesting.clearContracts();
    }

    @Test
    public void testUnsuccessfulCallcode() {
        FastVmTransactionResult precompiledResult = new FastVmTransactionResult(FastVmResultCode.FAILURE, ENERGY_LIMIT);
        this.precompiledContract.result = precompiledResult;
        PrecompiledFactoryForTesting.registerPrecompiledContract(this.precompiledAddress, this.precompiledContract);

        // We push a context and state onto the stack, this represents the external transaction.
        // Note that because we are doing callcode, the original destination address becomes
        // the new destination address, and we are just proving the point here.
        setupCallbackStack(this.originAddress, this.precompiledAddress);

        BigInteger senderOriginalBalance = BigInteger.valueOf(500);
        addBalanceToAccount(this.senderAddress, senderOriginalBalance);

        BigInteger originOriginalBalance = BigInteger.valueOf(750);
        addBalanceToAccount(this.originAddress, originOriginalBalance);

        BigInteger transferAmount = BigInteger.valueOf(220);
        byte[] encodedContext = encodeContext(this.senderAddress, this.senderAddress, transferAmount, new byte[0], ExecutionContext.CALLCODE);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.performCall(encodedContext, null));

        assertResultCorrect(precompiledResult, result);
        assertNonceIsZero(this.senderAddress);
        assertNonceIsZero(this.precompiledAddress);
        assertNonceIsZero(this.originAddress);
        assertBalanceCorrect(this.senderAddress, senderOriginalBalance);
        assertBalanceCorrect(this.precompiledAddress, BigInteger.ZERO);
        assertBalanceCorrect(this.originAddress, originOriginalBalance);

        // Verify that our internal transaction was marked rejected.
        Assert.assertTrue(Callback.context().getSideEffects().getInternalTransactions().get(0).isRejected);

        clearCallbackStack();
        PrecompiledFactoryForTesting.clearContracts();
    }

    @Test
    public void testSuccessfulCallcode() {
        FastVmTransactionResult precompiledResult = new FastVmTransactionResult(FastVmResultCode.SUCCESS, 500);
        this.precompiledContract.result = precompiledResult;
        PrecompiledFactoryForTesting.registerPrecompiledContract(this.precompiledAddress, this.precompiledContract);

        // We push a context and state onto the stack, this represents the external transaction.
        // Note that because we are doing callcode, the original destination address becomes
        // the new destination address, and we are just proving the point here.
        setupCallbackStack(this.originAddress, this.precompiledAddress);

        BigInteger senderOriginalBalance = BigInteger.valueOf(500);
        addBalanceToAccount(this.senderAddress, senderOriginalBalance);

        BigInteger originOriginalBalance = BigInteger.valueOf(750);
        addBalanceToAccount(this.originAddress, originOriginalBalance);

        BigInteger transferAmount = BigInteger.valueOf(220);
        byte[] encodedContext = encodeContext(this.senderAddress, this.senderAddress, transferAmount, new byte[0], ExecutionContext.CALLCODE);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.performCall(encodedContext, null));

        assertResultCorrect(precompiledResult, result);
        assertNonceIsZero(this.senderAddress);
        assertNonceIsZero(this.precompiledAddress);
        assertNonceIsZero(this.originAddress);
        assertBalanceCorrect(this.senderAddress, senderOriginalBalance);
        assertBalanceCorrect(this.precompiledAddress, BigInteger.ZERO);
        assertBalanceCorrect(this.originAddress, originOriginalBalance);

        // Verify that our internal transaction was NOT marked rejected.
        Assert.assertFalse(Callback.context().getSideEffects().getInternalTransactions().get(0).isRejected);

        clearCallbackStack();
        PrecompiledFactoryForTesting.clearContracts();
    }

    @Test
    public void testUnsuccessfulCall() {
        FastVmTransactionResult precompiledResult = new FastVmTransactionResult(FastVmResultCode.FAILURE, ENERGY_LIMIT);
        this.precompiledContract.result = precompiledResult;
        PrecompiledFactoryForTesting.registerPrecompiledContract(this.precompiledAddress, this.precompiledContract);

        // We push a context and state onto the stack, this represents the external transaction.
        setupCallbackStack(this.originAddress, this.senderAddress);

        BigInteger senderOriginalBalance = BigInteger.valueOf(500);
        addBalanceToAccount(this.senderAddress, senderOriginalBalance);

        BigInteger originOriginalBalance = BigInteger.valueOf(750);
        addBalanceToAccount(this.originAddress, originOriginalBalance);

        BigInteger transferAmount = BigInteger.valueOf(220);
        byte[] encodedContext = encodeContext(this.senderAddress, this.precompiledAddress, transferAmount, new byte[0], ExecutionContext.CALL);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.performCall(encodedContext, null));

        assertResultCorrect(precompiledResult, result);
        assertNonceIsZero(this.senderAddress);
        assertNonceIsZero(this.precompiledAddress);
        assertNonceIsZero(this.originAddress);
        assertBalanceCorrect(this.senderAddress, senderOriginalBalance);
        assertBalanceCorrect(this.precompiledAddress, BigInteger.ZERO);
        assertBalanceCorrect(this.originAddress, originOriginalBalance);

        // Verify that our internal transaction was marked rejected.
        Assert.assertTrue(Callback.context().getSideEffects().getInternalTransactions().get(0).isRejected);

        clearCallbackStack();
        PrecompiledFactoryForTesting.clearContracts();
    }

    @Test
    public void testSuccessfulCall() {
        FastVmTransactionResult precompiledResult = new FastVmTransactionResult(FastVmResultCode.SUCCESS, 500);
        this.precompiledContract.result = precompiledResult;
        PrecompiledFactoryForTesting.registerPrecompiledContract(this.precompiledAddress, this.precompiledContract);

        // We push a context and state onto the stack, this represents the external transaction.
        setupCallbackStack(this.originAddress, this.senderAddress);

        BigInteger senderOriginalBalance = BigInteger.valueOf(500);
        addBalanceToAccount(this.senderAddress, senderOriginalBalance);

        BigInteger originOriginalBalance = BigInteger.valueOf(750);
        addBalanceToAccount(this.originAddress, originOriginalBalance);

        BigInteger transferAmount = BigInteger.valueOf(220);
        byte[] encodedContext = encodeContext(this.senderAddress, this.precompiledAddress, transferAmount, new byte[0], ExecutionContext.CALL);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.performCall(encodedContext, null));

        assertResultCorrect(precompiledResult, result);
        assertNonceIsZero(this.senderAddress);
        assertNonceIsZero(this.precompiledAddress);
        assertNonceIsZero(this.originAddress);
        assertBalanceCorrect(this.senderAddress, senderOriginalBalance.subtract(transferAmount));
        assertBalanceCorrect(this.precompiledAddress, transferAmount);
        assertBalanceCorrect(this.originAddress, originOriginalBalance);

        // Verify that our internal transaction was NOT marked rejected.
        Assert.assertFalse(Callback.context().getSideEffects().getInternalTransactions().get(0).isRejected);

        clearCallbackStack();
        PrecompiledFactoryForTesting.clearContracts();
    }

    /**
     * NOTE: we assume that there is always exactly ONE state in the Callback stack, and so we can
     * safely access it and assume it has the real state changes!
     */
    private static void assertBalanceCorrect(AionAddress address, BigInteger expectedBalance) {
        Assert.assertEquals(expectedBalance, Callback.externalState().getBalance(address));
    }

    /**
     * None of these calls are CREATEs, so nobody's nonce should change.
     *
     * NOTE: we assume that there is always exactly ONE state in the Callback stack, and so we can
     * safely access it and assume it has the real state changes!
     */
    private static void assertNonceIsZero(AionAddress address) {
        Assert.assertEquals(BigInteger.ZERO, Callback.externalState().getNonce(address));
    }

    private static void assertResultCorrect(FastVmTransactionResult expected, FastVmTransactionResult actual) {
        Assert.assertEquals(expected.getResultCode(), actual.getResultCode());
        Assert.assertEquals(expected.getEnergyRemaining(), actual.getEnergyRemaining());
        Assert.assertEquals(expected.getInternalTransactions(), actual.getInternalTransactions());
        Assert.assertEquals(expected.getDeletedAddresses(), actual.getDeletedAddresses());
        Assert.assertEquals(expected.getLogs(), actual.getLogs());
        Assert.assertArrayEquals(expected.getReturnData(), actual.getReturnData());
    }

    /**
     * Returns an encoded context that gets decoded inside the Callback class.
     */
    private static byte[] encodeContext(AionAddress caller, AionAddress destination, BigInteger value, byte[] data, int kind) {
        int len = (AionAddress.LENGTH * 2) + FvmDataWord.SIZE + (Integer.BYTES * 4) + Long.BYTES + data.length;

        ByteBuffer buffer = ByteBuffer.allocate(len).order(ByteOrder.BIG_ENDIAN);
        buffer.put(destination.toByteArray());
        buffer.put(caller.toByteArray());
        buffer.putLong(ENERGY_LIMIT);
        buffer.put(FvmDataWord.fromBigInteger(value).copyOfData());
        buffer.putInt(data.length);
        buffer.put(data);
        buffer.putInt(0);
        buffer.putInt(kind);
        buffer.putInt(0);
        return buffer.array();
    }

    private static AionAddress randomAddress() {
        byte[] bytes = RandomUtils.nextBytes(AionAddress.LENGTH);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }

    /**
     * NOTE: we assume that there is always exactly ONE state in the Callback stack, and so we can
     * safely access it and assume it has the real state changes!
     */
    private static void addBalanceToAccount(AionAddress address, BigInteger amount) {
        Callback.externalState().addBalance(address, amount);
    }

    private static void setupCallbackStack(AionAddress originAddress, AionAddress destination) {
        ExecutionContext context = newDummyContext(originAddress, destination);
        ExternalStateForTesting state = newState();
        pushContextAndStateToCallbackStack(context, state);
    }

    private static void pushContextAndStateToCallbackStack(ExecutionContext context, ExternalStateForTesting state) {
        Callback.push(Pair.of(context, state));
    }

    private static void clearCallbackStack() {
        while (!Callback.stackIsEmpty()) {
            Callback.pop();
        }
    }

    private static ExecutionContext newDummyContext(AionAddress originAddress, AionAddress recipient) {
        return new ExecutionContext(null, null, recipient, originAddress, originAddress, FvmDataWord.fromLong(1L), ENERGY_LIMIT, FvmDataWord.fromBigInteger(BigInteger.ZERO), new byte[0], 0, ExecutionContext.CALL, 0, randomAddress(), 0L, 0L, 500_000L, FvmDataWord.fromLong(0L));
    }

    private static ExternalStateForTesting newState() {
        return new ExternalStateForTesting(newRepository(), new BlockchainForTesting(), randomAddress(), FvmDataWord.fromInt(0), false, true, false, 0L, 0L, 0L);
    }

    private static RepositoryCache<AccountState, IBlockStoreBase> newRepository() {
        RepositoryConfig repoConfig = new RepositoryConfig() {
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
        return new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
    }
}