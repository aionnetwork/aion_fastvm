package org.aion.fastvm;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.aion.ByteArrayWrapper;
import org.aion.ExternalCapabilitiesForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.repository.AccountStateForTesting.VmType;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.ByteUtil;
import org.aion.types.InternalTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/** Unit tests for Callback class. */
public class CallbackUnitTest {
    private static IExternalCapabilities capabilities;
    private RepositoryForTesting dummyRepo;

    @BeforeClass
    public static void setupCapabilities() {
        capabilities = new ExternalCapabilitiesForTesting();
        CapabilitiesProvider.installExternalCapabilities(capabilities);
    }

    @AfterClass
    public static void teardownCapabilities() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void setup() {
        dummyRepo = RepositoryForTesting.newRepository();
    }

    @After
    public void tearDown() {
        dummyRepo = null;
        while (true) {
            try {
                Callback.pop();
            } catch (NoSuchElementException e) {
                break;
            }
        }
    }

    @Test
    public void testGetBalanceAccountExists() {
        BigInteger balance = BigInteger.valueOf(RandomUtils.nextLong(100, 10_000));
        AionAddress address = pushNewBalance(balance);
        assertArrayEquals(
                FvmDataWord.fromBigInteger(balance).copyOfData(), Callback.getBalance(address.toByteArray()));
    }

    @Test
    public void testGetBalanceNoSuchAccount() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        assertArrayEquals(
                new byte[FvmDataWord.SIZE], Callback.getBalance(getNewAddress().toByteArray()));
    }

    @Test
    public void testGetBalanceAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        BigInteger[] balances = new BigInteger[depths];
        AionAddress[] addresses = new AionAddress[depths];
        for (int i = 0; i < depths; i++) {
            balances[depths - 1 - i] = BigInteger.valueOf(RandomUtils.nextLong(100, 10_000));
            addresses[depths - 1 - i] = pushNewBalance(balances[depths - 1 - i]);
        }
        for (int i = 0; i < depths; i++) {
            assertArrayEquals(
                    FvmDataWord.fromBigInteger(balances[i]).copyOfData(),
                    Callback.getBalance(addresses[i].toByteArray()));
            Callback.pop();
        }
    }

    @Test
    public void testGetStorageIsValidEntry() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(FvmDataWord.SIZE);
        byte[] value = RandomUtils.nextBytes(FvmDataWord.SIZE);
        AionAddress address = pushNewStorageEntry(repo, key, value, true);
        assertArrayEquals(value, Callback.getStorage(address.toByteArray(), key));
    }

    @Test
    public void testGetStorageNoSuchEntry() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(FvmDataWord.SIZE);
        byte[] value = RandomUtils.nextBytes(FvmDataWord.SIZE);
        AionAddress address = pushNewStorageEntry(repo, key, value, true);
        byte[] badKey = Arrays.copyOf(key, FvmDataWord.SIZE);
        badKey[0] = (byte) ~key[0];
        assertArrayEquals(
                FvmDataWord.fromBytes(new byte[0]).copyOfData(), Callback.getStorage(address.toByteArray(), badKey));
    }

    @Test
    public void testGetStorageMultipleAddresses() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        int numAddrs = RandomUtils.nextInt(5, 10);
        List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, true);
        AionAddress[] addresses = unpackAddresses(packs);
        byte[][] keys = unpackKeys(packs);
        byte[][] values = unpackValues(packs);
        for (int i = 0; i < numAddrs; i++) {
            assertArrayEquals(values[i], Callback.getStorage(addresses[i].toByteArray(), keys[i]));
        }
    }

    @Test
    public void testGetStorageMultipleAddressesAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        List<List<ByteArrayWrapper>> packsPerDepth = new ArrayList<>();
        for (int i = 0; i < depths; i++) {
            RepositoryForTesting repo = RepositoryForTesting.newRepository();
            pushNewRepo(repo);
            int numAddrs = RandomUtils.nextInt(5, 10);
            List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, true);
            packsPerDepth.add(0, packs);
        }
        for (int i = 0; i < depths; i++) {
            AionAddress[] addresses = unpackAddresses(packsPerDepth.get(i));
            byte[][] keys = unpackKeys(packsPerDepth.get(i));
            byte[][] values = unpackValues(packsPerDepth.get(i));
            for (int j = 0; j < addresses.length; j++) {
                assertArrayEquals(
                        values[j], Callback.getStorage(addresses[j].toByteArray(), keys[j]));
            }
            Callback.pop();
        }
    }

    @Test
    public void testPutStorage() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(FvmDataWord.SIZE);
        byte[] value = RandomUtils.nextBytes(FvmDataWord.SIZE);
        AionAddress address = putInStorage(key, value);
        assertArrayEquals(
                value,
                FvmDataWord.fromBytes(
                                repo.getStorageValue(address, FvmDataWord.fromBytes(key)).copyOfData())
                                        .copyOfData());
    }

    @Test
    public void testPutStorageMultipleEntries() {
        int num = RandomUtils.nextInt(3, 10);
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        AionAddress[] addresses = new AionAddress[num];
        byte[][] keys = new byte[num][];
        byte[][] values = new byte[num][];
        for (int i = 0; i < num; i++) {
            keys[num - 1 - i] = RandomUtils.nextBytes(FvmDataWord.SIZE);
            values[num - 1 - i] = RandomUtils.nextBytes(FvmDataWord.SIZE);
            addresses[num - 1 - i] = putInStorage(keys[num - 1 - i], values[num - 1 - i]);
        }
        for (int i = 0; i < num; i++) {
            assertArrayEquals(
                    values[i],
                    repo.getStorageValue(addresses[i], FvmDataWord.fromBytes(keys[i])).copyOfData());
        }
    }

    @Test
    public void testPutStorageMultipleAddresses() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        int numAddrs = RandomUtils.nextInt(5, 10);
        List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, false);
        AionAddress[] addresses = unpackAddresses(packs);
        byte[][] keys = unpackKeys(packs);
        byte[][] values = unpackValues(packs);
        for (int i = 0; i < numAddrs; i++) {
            assertArrayEquals(
                    values[i],
                    repo.getStorageValue(addresses[i], FvmDataWord.fromBytes(keys[i])).copyOfData());
        }
    }

    @Test
    public void testPutStorageMultipleAddressesAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        RepositoryForTesting[] repos = new RepositoryForTesting[depths];
        List<List<ByteArrayWrapper>> packsPerDepth = new ArrayList<>();
        for (int i = 0; i < depths; i++) {
            repos[depths - 1 - i] = RepositoryForTesting.newRepository();
            pushNewRepo(repos[depths - 1 - i]);
            int numAddrs = RandomUtils.nextInt(5, 10);
            List<ByteArrayWrapper> packs =
                    pushNewStorageEntries(repos[depths - 1 - i], numAddrs, false);
            packsPerDepth.add(0, packs);
        }
        for (int i = 0; i < depths; i++) {
            AionAddress[] addresses = unpackAddresses(packsPerDepth.get(i));
            byte[][] keys = unpackKeys(packsPerDepth.get(i));
            byte[][] values = unpackValues(packsPerDepth.get(i));
            for (int j = 0; j < addresses.length; j++) {
                assertArrayEquals(
                        values[j],
                        repos[i].getStorageValue(addresses[j], FvmDataWord.fromBytes(keys[j])).copyOfData());
            }
            Callback.pop();
        }
    }

    @Test
    public void testPutThenGetStorage() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        AionAddress address = getNewAddress();
        byte[] key = RandomUtils.nextBytes(FvmDataWord.SIZE);
        byte[] value = RandomUtils.nextBytes(FvmDataWord.SIZE);
        Callback.putStorage(address.toByteArray(), key, value);
        assertArrayEquals(value, Callback.getStorage(address.toByteArray(), key));
    }

    @Test
    public void testPutThenGetStorageMultipleTimes() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        pushNewRepo(repo);
        int numAddrs = RandomUtils.nextInt(5, 10);
        List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, false);
        AionAddress[] addresses = unpackAddresses(packs);
        byte[][] keys = unpackKeys(packs);
        byte[][] values = unpackValues(packs);
        for (int i = 0; i < numAddrs; i++) {
            assertArrayEquals(values[i], Callback.getStorage(addresses[i].toByteArray(), keys[i]));
        }
    }

    @Test
    public void testPutThenGetStorageAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        List<List<ByteArrayWrapper>> packsPerDepth = new ArrayList<>();
        for (int i = 0; i < depths; i++) {
            RepositoryForTesting repo = RepositoryForTesting.newRepository();
            pushNewRepo(repo);
            int numAddrs = RandomUtils.nextInt(5, 10);
            List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, false);
            packsPerDepth.add(0, packs);
        }
        for (int i = 0; i < depths; i++) {
            AionAddress[] addresses = unpackAddresses(packsPerDepth.get(i));
            byte[][] keys = unpackKeys(packsPerDepth.get(i));
            byte[][] values = unpackValues(packsPerDepth.get(i));
            for (int j = 0; j < addresses.length; j++) {
                assertArrayEquals(
                        values[j], Callback.getStorage(addresses[j].toByteArray(), keys[j]));
            }
            Callback.pop();
        }
    }

    @Test
    public void testLogWithZeroTopics() {
        AionAddress address = getNewAddress();
        byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
        byte[] topics = makeTopics(0);
        Callback.push(mockPair());
        Callback.log(address.toByteArray(), topics, data);
        checkLog(address, topics, data);
    }

    @Test
    public void testLogWithOneTopic() {
        AionAddress address = getNewAddress();
        byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
        byte[] topics = makeTopics(1);
        Callback.push(mockPair());
        Callback.log(address.toByteArray(), topics, data);
        checkLog(address, topics, data);
    }

    @Test
    public void testLogWithMultipleTopics() {
        AionAddress address = getNewAddress();
        byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
        byte[] topics = makeTopics(RandomUtils.nextInt(3, 10));
        Callback.push(mockPair());
        Callback.log(address.toByteArray(), topics, data);
        checkLog(address, topics, data);
    }

    @Test
    public void testLogAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        for (int i = 0; i < depths; i++) {
            Callback.push(mockPair());
        }
        for (int i = 0; i < depths; i++) {
            AionAddress address = getNewAddress();
            byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
            byte[] topics = makeTopics(1);
            Callback.log(address.toByteArray(), topics, data);
            checkLog(address, topics, data);
            Callback.pop();
        }
    }

    @Test
    public void testParseMessage() {
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                        false,
                        false,
                        TransactionKind.DELEGATE_CALL,
                        nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight())
                .thenReturn(RepositoryForTesting.newRepository());
        Callback.push(pair);
        ExecutionContext ctx =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                        false,
                        false,
                        TransactionKind.DELEGATE_CALL,
                        nrgLimit);
        byte[] message =
                generateContextMessage(
                        ctx.getDestinationAddress(),
                        ctx.getSenderAddress(),
                        ctx.getTransactionEnergy(),
                        FvmDataWord.fromBigInteger(ctx.getTransferValue()),
                        ctx.getTransactionData(),
                        ctx.getTransactionStackDepth(),
                        ctx.getTransactionKind(),
                        ctx.getFlags());

        ExecutionContext expectedContext = makeExpectedContext(context, ctx);
        compareContexts(expectedContext, Callback.parseMessage(message));
    }

    @Test
    public void testParseMessageAtMultipleStackDepths() {
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        int depths = RandomUtils.nextInt(3, 10);
        for (int i = 0; i < depths; i++) {
            ExecutionContext context =
                    newExecutionContext(
                            getNewAddress(),
                            getNewAddress(),
                            new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                            false,
                            false,
                            TransactionKind.DELEGATE_CALL,
                            nrgLimit);
            Pair pair = mockEmptyPair();
            when(pair.getLeft()).thenReturn(context);
            when(pair.getRight())
                    .thenReturn(RepositoryForTesting.newRepository());
            Callback.push(pair);
        }
        for (int i = 0; i < depths; i++) {
            // test every other ctx with empty data
            ExecutionContext ctx =
                    newExecutionContext(
                            getNewAddress(),
                            getNewAddress(),
                            new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                            i % 2 == 0,
                            false,
                            TransactionKind.DELEGATE_CALL,
                            nrgLimit);
            byte[] message =
                    generateContextMessage(
                            ctx.getDestinationAddress(),
                            ctx.getSenderAddress(),
                            ctx.getTransactionEnergy(),
                            FvmDataWord.fromBigInteger(ctx.getTransferValue()),
                            ctx.getTransactionData(),
                            ctx.getTransactionStackDepth(),
                            ctx.getTransactionKind(),
                            ctx.getFlags());
            ExecutionContext expectedContext = makeExpectedContext(Callback.context(), ctx);
            compareContexts(expectedContext, Callback.parseMessage(message));
            Callback.pop();
        }
    }

    @Test
    public void testParseMessageUsingZeroLengthData() {
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                        false,
                        false,
                        TransactionKind.DELEGATE_CALL,
                        nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight())
                .thenReturn(RepositoryForTesting.newRepository());
        Callback.push(pair);
        ExecutionContext ctx =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                        true,
                        false,
                        TransactionKind.DELEGATE_CALL,
                        nrgLimit);
        byte[] message =
                generateContextMessage(
                        ctx.getDestinationAddress(),
                        ctx.getSenderAddress(),
                        ctx.getTransactionEnergy(),
                        FvmDataWord.fromBigInteger(ctx.getTransferValue()),
                        ctx.getTransactionData(),
                        ctx.getTransactionStackDepth(),
                        ctx.getTransactionKind(),
                        ctx.getFlags());

        ExecutionContext expectedContext = makeExpectedContext(context, ctx);
        compareContexts(expectedContext, Callback.parseMessage(message));
    }

    @Test
    public void testCallStackDepthTooLarge() {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                        false,
                        false,
                        TransactionKind.DELEGATE_CALL,
                        nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(wrapInKernelInterface(repo));
        Callback.push(pair);
        byte[] message =
                generateContextMessage(
                        context.getDestinationAddress(),
                        context.getSenderAddress(),
                        context.getTransactionEnergy(),
                        FvmDataWord.fromBigInteger(context.getTransferValue()),
                        context.getTransactionData(),
                        context.getTransactionStackDepth(),
                        TransactionKind.CALL,
                        0);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.call(message));
        assertEquals(FastVmResultCode.FAILURE, result.getResultCode());
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testCallCallersBalanceLessThanCallValue() {
        BigInteger balance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        AionAddress caller = getNewAddressInRepo(repo, balance, BigInteger.ZERO);
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                newExecutionContext(
                        caller,
                        getNewAddress(),
                        new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)),
                        false,
                        false,
                        TransactionKind.DELEGATE_CALL,
                        nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(wrapInKernelInterface(repo));
        Callback.push(pair);
        byte[] message =
                generateContextMessage(
                        context.getDestinationAddress(),
                        context.getSenderAddress(),
                        context.getTransactionEnergy(),
                        FvmDataWord.fromBigInteger(context.getTransferValue()),
                        context.getTransactionData(),
                        context.getTransactionStackDepth(),
                        TransactionKind.CALL,
                        0);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.call(message));
        assertEquals(FastVmResultCode.FAILURE, result.getResultCode());
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testPerformCallCallIsPrecompiledNotSuccessSeptForkEnabledwithAVMCheck() {
        performCallIsPrecompiledNotSuccessSeptForkEnabledwithAVMCheck(TransactionKind.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoRecipientSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(
                TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoRecipientSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoRecipientSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(TransactionKind.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoRecipientSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(
                TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoRecipientSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoRecipientSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(TransactionKind.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoCodeSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoCodeSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoCodeSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(TransactionKind.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoCodeSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoCodeSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoCodeSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(TransactionKind.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(
                TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(TransactionKind.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(
                TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(
                TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(TransactionKind.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(
                TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(
                TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(TransactionKind.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(
                TransactionKind.DELEGATE_CALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(
                TransactionKind.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(TransactionKind.CALL);
    }

    @Test
    public void testPerformCallCreateCallContractExistsSeptForkDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        TransactionKind.CREATE,
                        new byte[0],
                        true,
                        false,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                true,
                false,
                null);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, true, false, false);
    }

    @Test
    public void testPerformCallCreateCallContractExistsSeptForkEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        TransactionKind.CREATE,
                        new byte[0],
                        true,
                        false,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                true,
                false,
                null);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, true, false, false);
    }

    @Test
    public void
            testPerformCallCreateCallContractIsNewDataIsEmptyNrgLessThanDepositSeptForkDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        TransactionKind.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                true,
                new byte[0]);
        checkContextHelper(false);
        // Seems misleading -- we are saying it was SUCCESS when obviously it was FAILURE, this is
        // because in 'post execution' it was SUCCESS but its low nrgLimit made it fail afterwards.
        // Nonetheless, the changes we expect of SUCCESS here did occur.
        checkPerformCallState(context, callerBalance, false, false, true);
    }

    @Test
    public void
            testPerformCallCreateCallContractIsNewDataIsEmptyNrgLessThanDepositSeptForkEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        TransactionKind.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                true,
                new byte[0]);
        checkContextHelper(false);
        // Seems misleading -- we are saying it was SUCCESS when obviously it was FAILURE, this is
        // because in 'post execution' it was SUCCESS but its low nrgLimit made it fail afterwards.
        // Nonetheless, the changes we expect of SUCCESS here did occur.
        checkPerformCallState(context, callerBalance, false, false, true);
    }

    @Test
    public void
            testPerformCallCreateCallContractIsNewDataIsEmptyNrgMoreThanDepositSeptForkDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        TransactionKind.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, FvmConstants.ENERGY_CODE_DEPOSIT);
        FastVM vm = mockFastVM(null); // we bypass vm

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                true,
                new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, false, true, false);
    }

    @Test
    public void
            testPerformCallCreateCallContractIsNewDataIsEmptyNrgMoreThanDepositSeptForkEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        TransactionKind.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, FvmConstants.ENERGY_CODE_DEPOSIT);
        FastVM vm = mockFastVM(null); // we bypass vm

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                true,
                new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, false, true, false);
    }

    @Test
    public void
            testPerformCallCreateCallContractIsNewDataNotEmptyNrgLessThanDepositSeptForkDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        TransactionKind.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, FvmConstants.ENERGY_CODE_DEPOSIT);
        FastVM vm = mockFastVM(mockedResult);

        mockedResult.setResultCodeAndEnergyRemaining(
                FastVmResultCode.FAILURE, 0); // nrgLimit causes failure post-execution.
        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                true,
                new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, false, false, true);
    }

    @Test
    public void
            testPerformCallCreateCallContractIsNewDataNotEmptyNrgLessThanDepositSeptForkEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        TransactionKind.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, FvmConstants.ENERGY_CODE_DEPOSIT);
        FastVM vm = mockFastVM(mockedResult);

        mockedResult.setResultCodeAndEnergyRemaining(
                FastVmResultCode.FAILURE, 0); // nrgLimit causes failure post-execution.
        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                false,
                new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, false, false, true);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyIsSuccessSeptForkDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        TransactionKind.CREATE,
                        null,
                        false,
                        false,
                        nrgLimit);

        byte[] code = RandomUtils.nextBytes(50);
        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, FvmConstants.ENERGY_CODE_DEPOSIT, code);
        FastVM vm = mockFastVM(mockedResult);

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                true,
                code);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, false, true, false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyIsSuccessSeptForkEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        TransactionKind.CREATE,
                        null,
                        false,
                        false,
                        nrgLimit);

        byte[] code = RandomUtils.nextBytes(50);
        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, FvmConstants.ENERGY_CODE_DEPOSIT, code);
        FastVM vm = mockFastVM(mockedResult);

        runPerformCallAndCheck(
                context,
                vm,
                mockedResult,
                false,
                TransactionKind.CREATE,
                false,
                true,
                code);
        checkContextHelper(false);
        checkPerformCallState(context, callerBalance, false, true, false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNotSuccessSeptForkDisabled() {
        for (FastVmResultCode resCode : FastVmResultCode.values()) {
            if (!resCode.equals(FastVmResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT;
                ExecutionContext context =
                        setupTestForPerformCall(
                                callerBalance,
                                recipientBalance,
                                false,
                                TransactionKind.CREATE,
                                null,
                                false,
                                false,
                                nrgLimit);

                byte[] code = RandomUtils.nextBytes(50);
                FastVmTransactionResult mockedResult =
                        new FastVmTransactionResult(resCode, FvmConstants.ENERGY_CODE_DEPOSIT, code);
                FastVM vm = mockFastVM(mockedResult);

                runPerformCallAndCheck(
                        context,
                        vm,
                        mockedResult,
                        false,
                        TransactionKind.CREATE,
                        false,
                        false,
                        code);
                checkContextHelper(false);
                checkPerformCallState(context, callerBalance, false, false, false);
            }
        }
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNotSuccessSeptForkEnabled() {
        for (FastVmResultCode resCode : FastVmResultCode.values()) {
            if (!resCode.equals(FastVmResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = FvmConstants.ENERGY_CODE_DEPOSIT;
                ExecutionContext context =
                        setupTestForPerformCall(
                                callerBalance,
                                recipientBalance,
                                true,
                                TransactionKind.CREATE,
                                null,
                                false,
                                false,
                                nrgLimit);

                byte[] code = RandomUtils.nextBytes(50);
                FastVmTransactionResult mockedResult =
                        new FastVmTransactionResult(resCode, FvmConstants.ENERGY_CODE_DEPOSIT, code);
                FastVM vm = mockFastVM(mockedResult);

                runPerformCallAndCheck(
                        context,
                        vm,
                        mockedResult,
                        false,
                        TransactionKind.CREATE,
                        false,
                        false,
                        code);
                checkContextHelper(false);
                checkPerformCallState(context, callerBalance, false, false, false);
            }
        }
    }

    // <----------METHODS BELOW ARE TESTS THAT ARE SHARED BY MULTIPLE TESTS AND SO REUSED---------->
    private void performCallIsPrecompiledNotSuccessSeptForkEnabledwithAVMCheck(TransactionKind kind) {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            if (!code.equals(FastVmResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context =
                    setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        kind,
                        new byte[0],
                        false,
                        false,
                        nrgLimit,
                        VmType.AVM);

                FastVmTransactionResult mockedResult = new FastVmTransactionResult(FastVmResultCode.INCOMPATIBLE_CONTRACT_CALL, 0);
                FastVM vm = mockFastVM(mockedResult);

                runPerformCallAndCheck(
                    context, vm, mockedResult, false, kind, false, false, null);
                checkHelperForRejections(Callback.context().getSideEffects());

                // before create a new internal tx, the callback.call already set the transaction to fail state.
                assertEquals(0, Callback.context().getSideEffects().getInternalTransactions().size());
                assertEquals(0, Callback.context().getSideEffects().getExecutionLogs().size());
                assertEquals(0, Callback.context().getSideEffects().getAddressesToBeDeleted().size());

                checkPerformCallResults(
                    context,
                    callerBalance,
                    recipientBalance,
                    false,
                    false,
                    kind,
                    mockedResult.getResultCode());
            }
        }
    }

    private void performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(TransactionKind kind) {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context =
                    setupTestForPerformCall(
                            callerBalance,
                            recipientBalance,
                            false,
                            kind,
                            null,
                            false,
                            false,
                            nrgLimit);

            FastVmTransactionResult mockedResult =
                    new FastVmTransactionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);

            // There is no recipient hence vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, mockedResult, true, kind, false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(
                    context,
                    callerBalance,
                    recipientBalance,
                    true,
                    false,
                    kind,
                    mockedResult.getResultCode());
        }
    }

    private void performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(TransactionKind kind) {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context =
                    setupTestForPerformCall(
                            callerBalance,
                            recipientBalance,
                            true,
                            kind,
                            null,
                            false,
                            false,
                            nrgLimit);

            FastVmTransactionResult mockedResult =
                    new FastVmTransactionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);

            // There is no recipient hence vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, mockedResult, true, kind, false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(
                    context,
                    callerBalance,
                    recipientBalance,
                    true,
                    false,
                    kind,
                    mockedResult.getResultCode());
        }
    }

    private void performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(TransactionKind kind) {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context =
                    setupTestForPerformCall(
                            callerBalance,
                            recipientBalance,
                            false,
                            kind,
                            new byte[0],
                            false,
                            false,
                            nrgLimit);

            FastVmTransactionResult mockedResult =
                    new FastVmTransactionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);

            // The recipient's code is empty, hence the vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, mockedResult, true, kind, false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(
                    context,
                    callerBalance,
                    recipientBalance,
                    false,
                    false,
                    kind,
                    mockedResult.getResultCode());
        }
    }

    private void performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(TransactionKind kind) {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context =
                    setupTestForPerformCall(
                            callerBalance,
                            recipientBalance,
                            true,
                            kind,
                            new byte[0],
                            false,
                            false,
                            nrgLimit);

            FastVmTransactionResult mockedResult =
                    new FastVmTransactionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);

            // The recipient's code is empty, hence the vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, mockedResult, true, kind, false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(
                    context,
                    callerBalance,
                    recipientBalance,
                    false,
                    false,
                    kind,
                    mockedResult.getResultCode());
        }
    }

    private void performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(TransactionKind kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        kind,
                        RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)),
                        false,
                        false,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);

        runPerformCallAndCheck(context, vm, mockedResult, false, kind, false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(
                context,
                callerBalance,
                recipientBalance,
                false,
                false,
                kind,
                mockedResult.getResultCode());
    }

    private void performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(TransactionKind kind) {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            if (!code.equals(FastVmResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context =
                        setupTestForPerformCall(
                                callerBalance,
                                recipientBalance,
                                false,
                                kind,
                                RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)),
                                false,
                                false,
                                nrgLimit);

                FastVmTransactionResult mockedResult =
                        new FastVmTransactionResult(code, RandomUtils.nextLong(0, 10_000));
                FastVM vm = mockFastVM(mockedResult);

                runPerformCallAndCheck(
                        context, vm, mockedResult, false, kind, false, false, null);
                checkHelperForRejections(Callback.context().getSideEffects());
                checkContextHelper(true);
                checkPerformCallResults(
                        context,
                        callerBalance,
                        recipientBalance,
                        false,
                        false,
                        kind,
                        mockedResult.getResultCode());
            }
        }
    }

    private void performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(TransactionKind kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        kind,
                        RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)),
                        false,
                        false,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);

        runPerformCallAndCheck(context, vm, mockedResult, false, kind, false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(
                context,
                callerBalance,
                recipientBalance,
                false,
                false,
                kind,
                mockedResult.getResultCode());
    }

    private void performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(TransactionKind kind) {
        for (FastVmResultCode code : FastVmResultCode.values()) {
            if (!code.equals(FastVmResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context =
                        setupTestForPerformCall(
                                callerBalance,
                                recipientBalance,
                                true,
                                kind,
                                RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)),
                                false,
                                false,
                                nrgLimit);

                FastVmTransactionResult mockedResult =
                        new FastVmTransactionResult(code, RandomUtils.nextLong(0, 10_000));
                FastVM vm = mockFastVM(mockedResult);

                runPerformCallAndCheck(
                        context, vm, mockedResult, false, kind, false, false, null);
                checkHelperForRejections(Callback.context().getSideEffects());
                checkContextHelper(true);
                checkPerformCallResults(
                        context,
                        callerBalance,
                        recipientBalance,
                        false,
                        false,
                        kind,
                        mockedResult.getResultCode());
            }
        }
    }

    // <---------------------------------------HELPERS BELOW--------------------------------------->

    private Pair<ExecutionContext, IExternalStateForFvm> mockEmptyPair() {
        return mock(Pair.class);
    }

    /**
     * Returns a mocked pair whose left entry is a mocked context that returns a new helper when
     * helper is called and whose right entry is a new DummyRepository.
     */
    private Pair<ExecutionContext, IExternalStateForFvm> mockPair() {
        ExecutionContext context = mockContext();
        SideEffects helper = new SideEffects();
        when(context.getSideEffects()).thenReturn(helper);
        Pair pair = mock(Pair.class);
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight())
                .thenReturn(
                        new ExternalStateForTesting(
                                RepositoryForTesting.newRepository(),
                                new BlockchainForTesting(),
                                new AionAddress(new byte[32]),
                                FvmDataWord.fromBytes(new byte[0]),
                                false,
                                true,
                                false,
                                0L,
                                0L,
                                0L));
        return pair;
    }

    private ExecutionContext mockContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getBlockNumber()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getBlockTimestamp()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getBlockDifficulty()).thenReturn(FvmDataWord.fromLong(RandomUtils.nextLong(0, 10_000)));
        when(context.getBlockEnergyLimit()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getMinerAddress()).thenReturn(getNewAddress());
        when(context.getSenderAddress()).thenReturn(getNewAddress());
        when(context.getTransactionData())
                .thenReturn(RandomUtils.nextBytes(RandomUtils.nextInt(0, 50)));
        when(context.getTransferValue())
                .thenReturn(new BigInteger(1, RandomUtils.nextBytes(FvmDataWord.SIZE)));
        when(context.getTransactionEnergy()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getTransactionHash()).thenReturn(RandomUtils.nextBytes(32));
        when(context.getTransactionStackDepth()).thenReturn(RandomUtils.nextInt(0, 1000));
        return context;
    }

    private ExecutionContext newExecutionContext(
            AionAddress caller,
            AionAddress recipient,
            BigInteger callValue,
            boolean isEmptyData,
            boolean septForkEnabled,
            TransactionKind kind,
            long nrgLimit) {

        byte[] txHash = RandomUtils.nextBytes(32);
        AionAddress origin = getNewAddress();
        long nrgPrice = RandomUtils.nextLong(1, 100);
        byte[] callData;
        if (isEmptyData) {
            callData = new byte[0];
        } else {
            callData = RandomUtils.nextBytes(RandomUtils.nextInt(10, 50));
        }
        int depth = RandomUtils.nextInt(0, FvmConstants.MAX_CALL_DEPTH - 1);
        int flags = RandomUtils.nextInt(100, 100_000);
        AionAddress blockCoinbase = getNewAddress();
        long blockNumber;
        if (!septForkEnabled) {
            blockNumber = RandomUtils.nextLong(0, 999_999);
        } else {
            blockNumber = 1_000_000;
        }
        long blockTimestamp = RandomUtils.nextLong(100, 100_000);
        long blockNrgLimit = RandomUtils.nextLong(100, 100_000);
        FvmDataWord blockDifficulty = FvmDataWord.fromLong(RandomUtils.nextLong(1, 10_000));
        return ExecutionContext.from(
                txHash,
                recipient,
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

    /**
     * Generates a message that is to be passed into the parseMessage method. This message is of the
     * following format:
     *
     * <p>|32b - address|32b - caller|8b - nrgLimit|16b - callValue|4b - callDataLength|?b -
     * callData| 4b - depth|4b - kind|4b - flags|
     */
    private byte[] generateContextMessage(
            AionAddress address,
            AionAddress caller,
            long nrgLimit,
            FvmDataWord callValue,
            byte[] callData,
            int depth,
            TransactionKind kind,
            int flags) {

        int len =
                (AionAddress.LENGTH * 2)
                        + FvmDataWord.SIZE
                        + (Integer.BYTES * 4)
                        + Long.BYTES
                        + callData.length;
        ByteBuffer buffer = ByteBuffer.allocate(len).order(ByteOrder.BIG_ENDIAN);
        buffer.put(address.toByteArray());
        buffer.put(caller.toByteArray());
        buffer.putLong(nrgLimit);
        buffer.put(callValue.copyOfData());
        buffer.putInt(callData.length);
        buffer.put(callData);
        buffer.putInt(depth);
        buffer.putInt(kind.intValue);
        buffer.putInt(flags);
        return buffer.array();
    }

    private void compareContexts(ExecutionContext context, ExecutionContext other) {
        assertEquals(context.getDestinationAddress(), other.getDestinationAddress());
        assertEquals(context.getOriginAddress(), other.getOriginAddress());
        assertEquals(context.getSenderAddress(), other.getSenderAddress());
        assertEquals(context.getMinerAddress(), other.getMinerAddress());
        assertEquals(context.getTransactionEnergyPrice(), other.getTransactionEnergyPrice());
        assertEquals(context.getTransferValue(), other.getTransferValue());
        assertEquals(context.getBlockDifficulty(), other.getBlockDifficulty());
        assertEquals(context.getTransactionEnergy(), other.getTransactionEnergy());
        assertEquals(context.getBlockNumber(), other.getBlockNumber());
        assertEquals(context.getBlockTimestamp(), other.getBlockTimestamp());
        assertEquals(context.getBlockEnergyLimit(), other.getBlockEnergyLimit());
        assertEquals(context.getTransactionStackDepth(), other.getTransactionStackDepth());
        assertEquals(context.getTransactionKind(), other.getTransactionKind());
        assertEquals(context.getFlags(), other.getFlags());
        assertArrayEquals(context.getTransactionHash(), other.getTransactionHash());
        assertArrayEquals(context.getTransactionData(), other.getTransactionData());
    }

    private IExternalStateForFvm mockState() {
        RepositoryForTesting cache = mock(RepositoryForTesting.class);
        when(cache.toString()).thenReturn("mocked repo.");
        IExternalStateForFvm externalState = mock(IExternalStateForFvm.class);
        when(externalState.getCode(Mockito.any(AionAddress.class))).thenReturn(RandomUtils.nextBytes(30));
        return externalState;
    }

    private void compareMockContexts(ExecutionContext context, ExecutionContext other) {
        assertEquals(context.getBlockNumber(), other.getBlockNumber());
        assertEquals(context.getSenderAddress(), other.getSenderAddress());
        assertArrayEquals(context.getTransactionData(), other.getTransactionData());
        assertEquals(context.getTransferValue(), other.getTransferValue());
        assertEquals(context.getTransactionEnergy(), other.getTransactionEnergy());
        assertArrayEquals(context.getTransactionHash(), other.getTransactionHash());
        assertEquals(context.getTransactionStackDepth(), other.getTransactionStackDepth());
    }

    private void compareRepos(RepositoryForTesting cache, RepositoryForTesting other) {
        AionAddress addr = getNewAddress();
        assertEquals(cache.toString(), other.toString());
        assertEquals(cache.getCode(addr), other.getCode(addr));
    }

    private AionAddress getNewAddressInRepo(
            RepositoryForTesting repo, BigInteger balance, BigInteger nonce) {
        AionAddress address = getNewAddress();
        repo.createAccount(address);
        repo.addBalance(address, balance);
        repo.setNonce(address, nonce);
        return address;
    }

    private AionAddress getNewAddressInRepo(
        RepositoryForTesting repo, BigInteger balance, BigInteger nonce, VmType vmType) {
        AionAddress address = getNewAddress();
        repo.createAccount(address);
        repo.addBalance(address, balance);
        repo.setNonce(address, nonce);
        repo.setVmType(address, vmType);
        return address;
    }

    private AionAddress getNewAddressInRepo(BigInteger balance, BigInteger nonce) {
        AionAddress address = getNewAddress();
        dummyRepo.createAccount(address);
        dummyRepo.addBalance(address, balance);
        dummyRepo.setNonce(address, nonce);
        return address;
    }

    private AionAddress getNewAddress() {
        byte[] bytes = RandomUtils.nextBytes(AionAddress.LENGTH);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }

    private byte[] makeTopics(int num) {
        return RandomUtils.nextBytes(num * 32);
    }

    /**
     * Checks state after log method is called in Callback.
     *
     * @param address The address passed into log.
     * @param topics The topics passed into log.
     * @param data The data passed into log.
     */
    private void checkLog(AionAddress address, byte[] topics, byte[] data) {
        SideEffects helper = Callback.context().getSideEffects();
        assertEquals(1, helper.getExecutionLogs().size());
        Log log = helper.getExecutionLogs().get(0);
        assertEquals(address, new AionAddress(log.copyOfAddress()));
        assertArrayEquals(data, log.copyOfData());
        List<byte[]> logTopics = log.copyOfTopics();
        int index = 0;
        for (byte[] topic : logTopics) {
            assertArrayEquals(topic, Arrays.copyOfRange(topics, index, index + 32));
            index += 32;
        }
        if (logTopics.isEmpty()) {
            assertEquals(0, topics.length);
        }
    }

    /**
     * Makes the expected ExecutionContext object that parseMessage would return when previous is
     * the context at the top of the stack when the fields in context are used to generate the
     * message given to the parseMessage method.
     */
    private ExecutionContext makeExpectedContext(
            ExecutionContext previous, ExecutionContext context) {
        return ExecutionContext.from(
                previous.getTransactionHash(),
                context.getDestinationAddress(),
                previous.getOriginAddress(),
                context.getSenderAddress(),
                previous.getTransactionEnergyPrice(),
                context.getTransactionEnergy(),
                context.getTransferValue(),
                context.getTransactionData(),
                context.getTransactionStackDepth(),
                context.getTransactionKind(),
                context.getFlags(),
                previous.getMinerAddress(),
                previous.getBlockNumber(),
                previous.getBlockTimestamp(),
                previous.getBlockEnergyLimit(),
                previous.getBlockDifficulty());
    }

    /**
     * Pushes a new pair onto the stack which holds a repo whose getBlockStore method returns a
     * block store whose getBlockHashByNumber method returns hash when called using blockNum.
     */
    private void pushNewBlockHash(long blockNum, byte[] hash) {
        ExecutionContext context = mockContext();
        IExternalStateForFvm externalState = mockState();
        when(externalState.getBlockHashByNumber(blockNum)).thenReturn(hash);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(externalState);
        Callback.push(pair);
    }

    /**
     * Pushes a new pair onto the stack which holds a repo whose getCode method returns code when
     * called using address.
     */
    private void pushNewCode(AionAddress address, byte[] code) {
        ExecutionContext context = mockContext();
        IExternalStateForFvm externalState = mockState();
        when(externalState.getCode(address)).thenReturn(code);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(externalState);
        Callback.push(pair);
    }

    /**
     * Pushes a new pair onto the stack which holds a repo with an account that has balance balance.
     * The newly created account with this balance is returned.
     */
    private AionAddress pushNewBalance(BigInteger balance) {
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        AionAddress address = getNewAddressInRepo(repo, balance, BigInteger.ZERO);
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(wrapInKernelInterface(repo));
        Callback.push(pair);
        return address;
    }

    private AionAddress[] unpackAddresses(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        AionAddress[] addresses = new AionAddress[len];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).copyOfBytes();
            addresses[i] = new AionAddress(Arrays.copyOfRange(pack, 0, AionAddress.LENGTH));
        }
        return addresses;
    }

    private byte[][] unpackKeys(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        byte[][] keys = new byte[len][];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).copyOfBytes();
            keys[i] =
                    Arrays.copyOfRange(
                            pack, AionAddress.LENGTH, AionAddress.LENGTH + FvmDataWord.SIZE);
        }
        return keys;
    }

    private byte[][] unpackValues(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        byte[][] values = new byte[len][];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).copyOfBytes();
            values[i] = Arrays.copyOfRange(pack, pack.length - FvmDataWord.SIZE, pack.length);
        }
        return values;
    }

    private byte[] packIntoBytes(AionAddress address, byte[] key, byte[] value) {
        byte[] pack = new byte[AionAddress.LENGTH + (FvmDataWord.SIZE * 2)];
        System.arraycopy(address.toByteArray(), 0, pack, 0, AionAddress.LENGTH);
        System.arraycopy(key, 0, pack, AionAddress.LENGTH, FvmDataWord.SIZE);
        System.arraycopy(
                value, 0, pack, AionAddress.LENGTH + FvmDataWord.SIZE, FvmDataWord.SIZE);
        return pack;
    }

    private List<ByteArrayWrapper> packup(AionAddress[] addresses, byte[][] keys, byte[][] values) {
        assertEquals(addresses.length, keys.length);
        assertEquals(keys.length, values.length);
        int len = addresses.length;
        List<ByteArrayWrapper> packs = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            packs.add(new ByteArrayWrapper(packIntoBytes(addresses[i], keys[i], values[i])));
        }
        return packs;
    }

    /**
     * Pushes num new entries into repo if pushToRepo is true, otherwise pushes onto Callback stack,
     * with random key-value pairs for random addresses. Returns a list of all the 'packed'
     * address-key-value objects per each entry packed together as a single byte array inside a
     * wrapper.
     */
    private List<ByteArrayWrapper> pushNewStorageEntries(
            RepositoryForTesting repo, int num, boolean pushToRepo) {

        AionAddress[] addresses = new AionAddress[num];
        byte[][] keys = new byte[num][];
        byte[][] values = new byte[num][];
        for (int i = 0; i < num; i++) {
            keys[num - 1 - i] = RandomUtils.nextBytes(FvmDataWord.SIZE);
            values[num - 1 - i] = RandomUtils.nextBytes(FvmDataWord.SIZE);
            addresses[num - 1 - i] =
                    pushNewStorageEntry(repo, keys[num - 1 - i], values[num - 1 - i], pushToRepo);
        }
        return packup(addresses, keys, values);
    }

    /**
     * Pushes a new entry into repo if pushToRepo is true, otherwise pushes it onto Callback stack,
     * with key and value as the key-value pair for some random address, which is then returned.
     */
    private AionAddress pushNewStorageEntry(
            RepositoryForTesting repo, byte[] key, byte[] value, boolean pushToRepo) {

        AionAddress address = getNewAddress();
        if (pushToRepo) {
            repo.addToStorage(
                    address,
                    FvmDataWord.fromBytes(key),
                    FvmDataWord.fromBytes(ByteUtil.stripLeadingZeroes(value)));
        } else {
            Callback.putStorage(address.toByteArray(), key, value);
        }
        return address;
    }

    /**
     * Pushes repo onto the top of the Callback stack by adding a new mocked Pair whose right entry
     * is repo.
     */
    private void pushNewRepo(RepositoryForTesting repo) {
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(wrapInKernelInterface(repo));
        Callback.push(pair);
    }

    /**
     * Puts the key-value pair key and value into the Callback storage under a random address and
     * returns that address.
     */
    private AionAddress putInStorage(byte[] key, byte[] value) {
        AionAddress address = getNewAddress();
        Callback.putStorage(address.toByteArray(), key, value);
        return address;
    }

    /** Returns a mocked FastVM whose run method returns result. */
    private FastVM mockFastVM(FastVmTransactionResult result) {
        FastVM vm = mock(FastVM.class);
        when(vm.runPre040Fork(
                        Mockito.any(byte[].class),
                        Mockito.any(ExecutionContext.class),
                        Mockito.any(IExternalStateForFvm.class)))
                .thenReturn(result);
        return vm;
    }

    /**
     * Checks the state after the performCall method is called and makes assertions on the expected
     * states.
     *
     * @param context The context whose params were used in the test set up.
     * @param callerBalance The balance of the caller's account.
     * @param recipientBalance The balance of the recipient's account.
     * @param wasNoRecipient There was no recipient at time of performCall.
     * @param isCreateContract If the op code is CREATE
     * @param kind Transaction kind.
     * @param resultCode fvm execution result
     */
    private void checkPerformCallResults(
            ExecutionContext context,
            BigInteger callerBalance,
            BigInteger recipientBalance,
            boolean wasNoRecipient,
            boolean isCreateContract,
            TransactionKind kind,
            FastVmResultCode resultCode) {

        ExecutionContext ctx = Callback.context();
        if (!ctx.getSideEffects().getInternalTransactions().isEmpty()) {
            checkInternalTransaction(
                    context,
                    ctx.getSideEffects().getInternalTransactions().get(0),
                    isCreateContract,
                    true);
        }
        checkPerformCallBalances(
                context.getSenderAddress(),
                callerBalance,
                context.getDestinationAddress(),
                recipientBalance,
                context.getTransferValue(),
                wasNoRecipient,
                kind,
                resultCode);
    }

    /**
     * Runs the performCall method in Callback -- this is the mock-friendly version of the call
     * method that we really want tested -- and makes some simple assertions on the returned
     * ExecutioResult from this method.
     *
     * @param context The context whose params were used in the test set up.
     * @param mockVM A mocked VM.
     * @param expectedResult The expected performCall result.
     * @param vmGotBadCode Signals that the code for the VM to execute was null or empty.
     * @param kind The opcode to run.
     * @param contractExisted If this is for CREATE and the contract already existed prior to call.
     * @param postExecuteWasSuccess If in post execute the result is SUCCESS
     * @param code The code to add to contract after CREATE call.
     */
    private void runPerformCallAndCheck(
            ExecutionContext context,
            FastVM mockVM,
            FastVmTransactionResult expectedResult,
            boolean vmGotBadCode,
            TransactionKind kind,
            boolean contractExisted,
            boolean postExecuteWasSuccess,
            byte[] code) {

        byte[] message =
                generateContextMessage(
                        context.getDestinationAddress(),
                        context.getSenderAddress(),
                        context.getTransactionEnergy(),
                        FvmDataWord.fromBigInteger(context.getTransferValue()),
                        context.getTransactionData(),
                        context.getTransactionStackDepth(),
                        kind,
                        0);
        FastVmTransactionResult result =
                FastVmTransactionResult.fromBytes(Callback.performCall(message, mockVM));
        assertEquals(expectedResult.getResultCode(), result.getResultCode());
        if (vmGotBadCode) {
            assertEquals(context.getTransactionEnergy(), result.getEnergyRemaining());
        } else {
            assertEquals(expectedResult.getEnergyRemaining(), result.getEnergyRemaining());
        }
        if (kind == TransactionKind.CREATE) {
            checkCodeAfterCreate(contractExisted, postExecuteWasSuccess, result, code);
        }
    }

    /**
     * Checks that code is saved under the contract address in the repo at the top of the stack and
     * that result's output is the address of that contract. This is for the CREATE opcode.
     */
    private void checkCodeAfterCreate(
            boolean contractAlreadyExists,
            boolean postExecuteWasSuccess,
            FastVmTransactionResult result,
            byte[] code) {

        if (!contractAlreadyExists && postExecuteWasSuccess) {
            AionAddress contract =
                    (result.getReturnData() == null
                                    || Arrays.equals(result.getReturnData(), new byte[0]))
                            ? null
                            : new AionAddress(result.getReturnData());

            if (contract != null) {
                assertArrayEquals(code, Callback.externalState().getCode(contract));
            }
        }
    }

    /**
     * Sets up the Callback class, including its top-stack context and repo, for the performCall
     * testing. Two accounts, a caller and a recipient, are created and these accounts have balances
     * callerBalance and recipientBalance respectively.
     */
    private ExecutionContext setupTestForPerformCall(
        BigInteger callerBalance,
        BigInteger recipientBalance,
        boolean septForkEnabled,
        TransactionKind kind,
        byte[] code,
        boolean contractExists,
        boolean dataIsEmpty,
        long nrgLimit) {
        return setupTestForPerformCall(
                callerBalance,
                recipientBalance,
                septForkEnabled,
                kind,
                code,
                contractExists,
                dataIsEmpty,
                nrgLimit,
                null);
    }


    private ExecutionContext setupTestForPerformCall(
            BigInteger callerBalance,
            BigInteger recipientBalance,
            boolean septForkEnabled,
            TransactionKind kind,
            byte[] code,
            boolean contractExists,
            boolean dataIsEmpty,
            long nrgLimit,
            VmType vmType) {

        BigInteger callerNonce = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        RepositoryForTesting repo = RepositoryForTesting.newRepository();
        AionAddress caller = getNewAddressInRepo(repo, callerBalance, callerNonce);
        if (contractExists) {
            AionAddress contract = capabilities.computeNewContractAddress(caller, callerNonce);
            repo.createAccount(contract);
            repo.addBalance(contract, BigInteger.ZERO);
        }

        AionAddress recipient;
        if (code == null) {
            recipient = getNewAddress();
        } else {
            if (vmType != null) {
                recipient = getNewAddressInRepo(repo, recipientBalance, BigInteger.ZERO, vmType);
            } else {
                recipient = getNewAddressInRepo(repo, recipientBalance, BigInteger.ZERO);
            }

            repo.saveCode(recipient, code);
        }
        ExecutionContext context =
                newExecutionContext(
                        caller,
                        recipient,
                        callerBalance,
                        dataIsEmpty,
                        septForkEnabled,
                        kind,
                        nrgLimit);

        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(wrapInKernelInterface(repo));
        Callback.push(pair);
        return context;
    }

    /** Asserts that all of the internal transactions in helper have been rejected. */
    private void checkHelperForRejections(SideEffects helper) {
        for (InternalTransaction tx : helper.getInternalTransactions()) {
            assertTrue(tx.isRejected);
        }
    }

    /**
     * Asserts that the account balances are in the expected state after a call to performCall. Note
     * if we are doing CALLCODE or DELEGATECALL then no value gets transferred in Callback.
     */
    private void checkPerformCallBalances(
            AionAddress caller,
            BigInteger callerPrevBalance,
            AionAddress recipient,
            BigInteger recipientPrevBalance,
            BigInteger callValue,
            boolean wasNoRecipient,
            TransactionKind kind,
            FastVmResultCode resultCode) {

        if (caller.equals(recipient)) {
            assertEquals(callerPrevBalance, Callback.externalState().getBalance(caller));
        } else {
            if (kind == TransactionKind.DELEGATE_CALL || kind == TransactionKind.CALLCODE) {
                assertEquals(callerPrevBalance, Callback.externalState().getBalance(caller));
            } else {
                if (!resultCode.isSuccess()) {
                    assertEquals(callerPrevBalance, Callback.externalState().getBalance(caller));
                } else {
                    assertEquals(
                            callerPrevBalance.subtract(callValue),
                            Callback.externalState().getBalance(caller));
                }
            }

            if (wasNoRecipient) {
                // if there was no recipient then DummyRepository created that account when Callback
                // transferred balance to it, so its balance should be callValue.
                if (kind == TransactionKind.DELEGATE_CALL || kind == TransactionKind.CALLCODE) {
                    assertEquals(BigInteger.ZERO, Callback.externalState().getBalance(recipient));
                } else {
                    assertEquals(callValue, Callback.externalState().getBalance(recipient));
                }
            } else {
                if (kind == TransactionKind.DELEGATE_CALL || kind == TransactionKind.CALLCODE) {
                    assertEquals(recipientPrevBalance, Callback.externalState().getBalance(recipient));
                } else {
                    if (!resultCode.isSuccess()) {
                        assertEquals(
                                recipientPrevBalance, Callback.externalState().getBalance(recipient));
                    } else {
                        assertEquals(
                                recipientPrevBalance.add(callValue),
                                Callback.externalState().getBalance(recipient));
                    }
                }
            }
        }
    }

    /**
     * Asserts that the values of the internal transaction tx is in its expected state given that
     * context was the context used to set up the performCall test.
     */
    private void checkInternalTransaction(
            ExecutionContext context,
            InternalTransaction tx,
            boolean isCreateContract,
            boolean wasSuccess) {

        if (!isCreateContract) {
            assertEquals(context.getDestinationAddress(), tx.destination);
        }

        if (isCreateContract && wasSuccess) {
            BigInteger nonce = Callback.externalState().getNonce(context.getSenderAddress()).subtract(BigInteger.ONE);

            AionAddress contract = capabilities.computeNewContractAddress(context.getSenderAddress(), nonce);
            assertTrue(Callback.exists(contract.toByteArray()));

            assertEquals(Callback.externalState().getNonce(context.getSenderAddress()).subtract(BigInteger.ONE), tx.senderNonce);
        } else {
            assertEquals(Callback.externalState().getNonce(context.getSenderAddress()), tx.senderNonce);
        }

        assertEquals(context.getSenderAddress(), tx.sender);
        assertEquals(context.getTransferValue(), tx.value);
        assertArrayEquals(context.getTransactionData(), tx.copyOfData());
    }

    /**
     * Checks the state of the context helper following a performCall call.
     *
     * @param performCallTriggeredDoCall If the op code chose the doCall path.
     */
    private void checkContextHelper(boolean performCallTriggeredDoCall) {
        if (performCallTriggeredDoCall) {
            assertEquals(1, Callback.context().getSideEffects().getInternalTransactions().size());
            assertEquals(0, Callback.context().getSideEffects().getExecutionLogs().size());
            assertEquals(0, Callback.context().getSideEffects().getAddressesToBeDeleted().size());
        } else {
            assertEquals(2, Callback.context().getSideEffects().getInternalTransactions().size());
            assertEquals(0, Callback.context().getSideEffects().getExecutionLogs().size());
            assertEquals(0, Callback.context().getSideEffects().getAddressesToBeDeleted().size());
        }
    }

    /**
     * Checks the state after the performCall method is called and makes assertions on the expected
     * states.
     *
     * @param context The context whose params were used in the test set up.
     * @param callerBalance The balance of the caller prior to the transaction.
     * @param contractExisted If the contract already existed prior to the call.
     * @param wasSuccess If the call result was SUCCESS in post execute.
     * @param nrgLessThanDeposit If energy limit was less than deposit amount.
     */
    private void checkPerformCallState(
            ExecutionContext context,
            BigInteger callerBalance,
            boolean contractExisted,
            boolean wasSuccess,
            boolean nrgLessThanDeposit) {

        checkInternalTransactionsAfterCreate(wasSuccess);
        checkCreateBalances(
                context, callerBalance, contractExisted, wasSuccess, nrgLessThanDeposit);
    }

    /**
     * Checks the state of the internal transactions in the context at the top of the stack's helper
     * after a call using the CREATE opcode.
     */
    private void checkInternalTransactionsAfterCreate(boolean wasSuccess) {
        ExecutionContext context = Callback.context();
        List<InternalTransaction> internalTxs = context.getSideEffects().getInternalTransactions();
        assertEquals(2, internalTxs.size());
        checkInternalTransaction(context, internalTxs.get(0), true, wasSuccess);
        checkSecondInteralTransaction(context, internalTxs.get(1));
        if (!wasSuccess) {
            assertTrue(internalTxs.get(1).isRejected);
        }
    }

    /** Checks the second of the 2 internal transactions created during the CREATE opcode. */
    private void checkSecondInteralTransaction(ExecutionContext context, InternalTransaction tx) {
        AionAddress caller = context.getSenderAddress();
        assertEquals(context.getSenderAddress(), tx.sender);
        if (tx.isRejected) {
            assertEquals(
                Callback.externalState().getNonce(caller),
                tx.senderNonce.subtract(BigInteger.ONE));
        } else {
            assertEquals(Callback.externalState().getNonce(caller), tx.senderNonce);
        }
        assertEquals(context.getTransferValue(), tx.value);

        assertArrayEquals(context.getTransactionData(), tx.copyOfData());
        assertNull(tx.destination);
    }

    /**
     * Checks that the balances of the caller and contract are in their expected state after a
     * CREATE opcode.
     */
    private void checkCreateBalances(
            ExecutionContext context,
            BigInteger callerBalance,
            boolean contractExisted,
            boolean postExecuteWasSuccess,
            boolean nrgLessThanDeposit) {

        BigInteger value = context.getTransferValue();
        AionAddress caller = Callback.context().getSenderAddress();
        AionAddress contract;
        contract = capabilities.computeNewContractAddress(caller, Callback.externalState().getNonce(caller).subtract(BigInteger.ONE));
        if (postExecuteWasSuccess) {
            assertEquals(callerBalance.subtract(value), Callback.externalState().getBalance(caller));
        } else {
            assertEquals(callerBalance, Callback.externalState().getBalance(caller));
        }
        if (contractExisted) {
            assertEquals(BigInteger.ZERO, Callback.externalState().getBalance(contract));
        } else {
            if (postExecuteWasSuccess && !nrgLessThanDeposit) {
                assertEquals(value, Callback.externalState().getBalance(contract));
            } else {
                assertEquals(BigInteger.ZERO, Callback.externalState().getBalance(contract));
            }
        }
    }

    private static IExternalStateForFvm wrapInKernelInterface(RepositoryForTesting cache) {
        return new ExternalStateForTesting(
                cache, new BlockchainForTesting(), new AionAddress(new byte[32]), FvmDataWord.fromBytes(new byte[0]), false, true, false, 0L, 0L, 0L);
    }
}
