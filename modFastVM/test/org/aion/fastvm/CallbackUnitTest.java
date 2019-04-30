package org.aion.fastvm;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.interfaces.vm.DataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionSideEffects;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/** Unit tests for Callback class. */
public class CallbackUnitTest {
    private AionRepositoryCache dummyRepo;
    private RepositoryConfig repoConfig;

    @Before
    public void setup() {
        repoConfig =
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

        dummyRepo = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
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

    @Test(expected = NoSuchElementException.class)
    public void testPopEmptyStack() {
        Callback.pop();
    }

    @Test(expected = NullPointerException.class)
    public void testPeekAtEmptyStack() {
        Callback.context();
    }

    @Test(expected = NullPointerException.class)
    public void testPeekAtEmptyStack2() {
        Callback.kernelRepo();
    }

    @Test
    public void testPeekAtContextInStackSizeOne() {
        ExecutionContext context = mockContext();
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        Callback.push(pair);
        TransactionContext stackContext = Callback.context();
        compareMockContexts(context, stackContext);
    }

    @Test
    public void testPeekAtRepoInStackSizeOne() {
        KernelInterfaceForFastVM repo = mockKernelRepo();
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> stackRepo =
                Callback.kernelRepo().getRepositoryCache();
        System.out.println(repo.getRepositoryCache());
        compareRepos(repo.getRepositoryCache(), stackRepo);
    }

    @Test
    public void testPushesAndPopsUntilEmpty() {
        int reps = RandomUtils.nextInt(10, 50);
        for (int i = 0; i < reps; i++) {
            Callback.push(mockEmptyPair());
        }
        for (int i = 0; i < reps; i++) {
            Callback.pop();
        }
        try {
            Callback.pop();
        } catch (NoSuchElementException e) {
            return;
        } // hit bottom as wanted.
        fail();
    }

    @Test
    public void testEachDepthOfPoppingLargeStack() {
        int reps = RandomUtils.nextInt(10, 30);
        Pair[] pairs = new Pair[reps];
        for (int i = 0; i < reps; i++) {
            ExecutionContext ctx = mockContext();
            KernelInterfaceForFastVM repo = mockKernelRepo();
            Pair<TransactionContext, KernelInterfaceForFastVM> mockedPair = mockEmptyPair();
            when(mockedPair.getLeft()).thenReturn(ctx);
            when(mockedPair.getRight()).thenReturn(repo);
            pairs[i] = mockedPair;
        }
        for (int i = 0; i < reps; i++) {
            Callback.push(pairs[reps - 1 - i]);
        }
        for (Pair pair : pairs) {
            compareMockContexts((ExecutionContext) pair.getLeft(), Callback.context());
            compareRepos(
                    ((KernelInterfaceForFastVM) pair.getRight()).getRepositoryCache(),
                    Callback.kernelRepo().getRepositoryCache());
            Callback.pop();
        }
        try {
            Callback.pop();
        } catch (NoSuchElementException e) {
            return;
        } // hit bottom as wanted.
        fail();
    }

    @Test
    public void testGetBlockHashOnValidBlock() {
        long blockNum = RandomUtils.nextLong(10, 10_000);
        byte[] hash = RandomUtils.nextBytes(50);
        pushNewBlockHash(blockNum, hash);
        assertArrayEquals(hash, Callback.getBlockHash(blockNum));
    }

    @Test
    public void testGetBlockHashOnInvalidBlock() {
        long blockNum = RandomUtils.nextLong(10, 10_000);
        pushNewBlockHash(blockNum, null);
        assertArrayEquals(new byte[32], Callback.getBlockHash(blockNum));
    }

    @Test
    public void testGetBlockHashAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        long[] blockNums = new long[depths];
        byte[][] hashes = new byte[depths][];
        for (int i = 0; i < depths; i++) {
            blockNums[depths - 1 - i] = RandomUtils.nextLong(10, 10_000);
            hashes[depths - 1 - i] = RandomUtils.nextBytes(RandomUtils.nextInt(5, 25));
            pushNewBlockHash(blockNums[depths - 1 - i], hashes[depths - 1 - i]);
        }
        for (int i = 0; i < depths; i++) {
            assertArrayEquals(hashes[i], Callback.getBlockHash(blockNums[i]));
            Callback.pop();
        }
    }

    @Test
    public void testGetCodeIsValidCode() {
        Address address = getNewAddress();
        byte[] code = RandomUtils.nextBytes(30);
        pushNewCode(address, code);
        assertArrayEquals(code, Callback.getCode(address.toBytes()));
    }

    @Test
    public void testGetCodeIsNoCode() {
        Address address = getNewAddress();
        pushNewCode(address, null);
        assertArrayEquals(new byte[0], Callback.getCode(address.toBytes()));
    }

    @Test
    public void testGetCodeAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        Address[] addresses = new Address[depths];
        byte[][] codes = new byte[depths][];
        for (int i = 0; i < depths; i++) {
            addresses[depths - 1 - i] = getNewAddress();
            // test half the methods on no valid code.
            if ((depths - 1 - i) % 2 == 0) {
                codes[depths - 1 - i] = null;
            } else {
                codes[depths - 1 - i] = RandomUtils.nextBytes(25);
            }
            pushNewCode(addresses[depths - 1 - i], codes[depths - 1 - i]);
        }
        for (int i = 0; i < depths; i++) {
            if (codes[i] == null) {
                assertArrayEquals(new byte[0], Callback.getCode(addresses[i].toBytes()));
            } else {
                assertArrayEquals(codes[i], Callback.getCode(addresses[i].toBytes()));
            }
            Callback.pop();
        }
    }

    @Test
    public void testGetBalanceAccountExists() {
        BigInteger balance = BigInteger.valueOf(RandomUtils.nextLong(100, 10_000));
        Address address = pushNewBalance(balance);
        assertArrayEquals(
                new DataWordImpl(balance).getData(), Callback.getBalance(address.toBytes()));
    }

    @Test
    public void testGetBalanceNoSuchAccount() {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        assertArrayEquals(
                new byte[DataWordImpl.BYTES], Callback.getBalance(getNewAddress().toBytes()));
    }

    @Test
    public void testGetBalanceAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        BigInteger[] balances = new BigInteger[depths];
        Address[] addresses = new Address[depths];
        for (int i = 0; i < depths; i++) {
            balances[depths - 1 - i] = BigInteger.valueOf(RandomUtils.nextLong(100, 10_000));
            addresses[depths - 1 - i] = pushNewBalance(balances[depths - 1 - i]);
        }
        for (int i = 0; i < depths; i++) {
            assertArrayEquals(
                    new DataWordImpl(balances[i]).getData(),
                    Callback.getBalance(addresses[i].toBytes()));
            Callback.pop();
        }
    }

    @Test
    public void testHasAccountStateWhenAccountExists() {
        Address address = getNewAddress();
        KernelInterfaceForFastVM repo = mockKernelRepo();
        when(repo.hasAccountState(address)).thenReturn(true);
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        assertTrue(Callback.exists(address.toBytes()));
    }

    @Test
    public void testHasAccountStateWhenAccountDoesNotExist() {
        Address address = getNewAddress();
        KernelInterfaceForFastVM repo = mockKernelRepo();
        when(repo.hasAccountState(address)).thenReturn(false);
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        assertFalse(Callback.exists(address.toBytes()));
    }

    @Test
    public void testGetStorageIsValidEntry() {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(DataWordImpl.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWordImpl.BYTES);
        Address address = pushNewStorageEntry(repo, key, value, true);
        assertArrayEquals(value, Callback.getStorage(address.toBytes(), key));
    }

    @Test
    public void testGetStorageNoSuchEntry() {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(DataWordImpl.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWordImpl.BYTES);
        Address address = pushNewStorageEntry(repo, key, value, true);
        byte[] badKey = Arrays.copyOf(key, DataWordImpl.BYTES);
        badKey[0] = (byte) ~key[0];
        assertArrayEquals(
                DataWordImpl.ZERO.getData(), Callback.getStorage(address.toBytes(), badKey));
    }

    @Test
    public void testGetStorageMultipleAddresses() {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        int numAddrs = RandomUtils.nextInt(5, 10);
        List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, true);
        Address[] addresses = unpackAddresses(packs);
        byte[][] keys = unpackKeys(packs);
        byte[][] values = unpackValues(packs);
        for (int i = 0; i < numAddrs; i++) {
            assertArrayEquals(values[i], Callback.getStorage(addresses[i].toBytes(), keys[i]));
        }
    }

    @Test
    public void testGetStorageMultipleAddressesAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        List<List<ByteArrayWrapper>> packsPerDepth = new ArrayList<>();
        for (int i = 0; i < depths; i++) {
            RepositoryCache repo =
                    new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
            pushNewRepo(repo);
            int numAddrs = RandomUtils.nextInt(5, 10);
            List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, true);
            packsPerDepth.add(0, packs);
        }
        for (int i = 0; i < depths; i++) {
            Address[] addresses = unpackAddresses(packsPerDepth.get(i));
            byte[][] keys = unpackKeys(packsPerDepth.get(i));
            byte[][] values = unpackValues(packsPerDepth.get(i));
            for (int j = 0; j < addresses.length; j++) {
                assertArrayEquals(values[j], Callback.getStorage(addresses[j].toBytes(), keys[j]));
            }
            Callback.pop();
        }
    }

    @Test
    public void testPutStorage() {
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(DataWordImpl.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWordImpl.BYTES);
        Address address = putInStorage(key, value);
        assertArrayEquals(
                value,
                new DataWordImpl(
                                repo.getStorageValue(address, new DataWordImpl(key).toWrapper())
                                        .getData())
                        .getData());
    }

    @Test
    public void testPutStorageMultipleEntries() {
        int num = RandomUtils.nextInt(3, 10);
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        Address[] addresses = new Address[num];
        byte[][] keys = new byte[num][];
        byte[][] values = new byte[num][];
        for (int i = 0; i < num; i++) {
            keys[num - 1 - i] = RandomUtils.nextBytes(DataWordImpl.BYTES);
            values[num - 1 - i] = RandomUtils.nextBytes(DataWordImpl.BYTES);
            addresses[num - 1 - i] = putInStorage(keys[num - 1 - i], values[num - 1 - i]);
        }
        for (int i = 0; i < num; i++) {
            assertArrayEquals(
                    values[i],
                    new DataWordImpl(
                                    repo.getStorageValue(
                                                    addresses[i],
                                                    new DataWordImpl(keys[i]).toWrapper())
                                            .getData())
                            .getData());
        }
    }

    @Test
    public void testPutStorageMultipleAddresses() {
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        int numAddrs = RandomUtils.nextInt(5, 10);
        List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, false);
        Address[] addresses = unpackAddresses(packs);
        byte[][] keys = unpackKeys(packs);
        byte[][] values = unpackValues(packs);
        for (int i = 0; i < numAddrs; i++) {
            assertArrayEquals(
                    values[i],
                    new DataWordImpl(
                                    repo.getStorageValue(
                                                    addresses[i],
                                                    new DataWordImpl(keys[i]).toWrapper())
                                            .getData())
                            .getData());
        }
    }

    @Test
    public void testPutStorageMultipleAddressesAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>>[] repos = new RepositoryCache[depths];
        List<List<ByteArrayWrapper>> packsPerDepth = new ArrayList<>();
        for (int i = 0; i < depths; i++) {
            repos[depths - 1 - i] =
                    new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
            pushNewRepo(repos[depths - 1 - i]);
            int numAddrs = RandomUtils.nextInt(5, 10);
            List<ByteArrayWrapper> packs =
                    pushNewStorageEntries(repos[depths - 1 - i], numAddrs, false);
            packsPerDepth.add(0, packs);
        }
        for (int i = 0; i < depths; i++) {
            Address[] addresses = unpackAddresses(packsPerDepth.get(i));
            byte[][] keys = unpackKeys(packsPerDepth.get(i));
            byte[][] values = unpackValues(packsPerDepth.get(i));
            for (int j = 0; j < addresses.length; j++) {
                assertArrayEquals(
                        values[j],
                        new DataWordImpl(
                                        repos[i].getStorageValue(
                                                        addresses[j],
                                                        new DataWordImpl(keys[j]).toWrapper())
                                                .getData())
                                .getData());
            }
            Callback.pop();
        }
    }

    @Test
    public void testPutThenGetStorage() {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        Address address = getNewAddress();
        byte[] key = RandomUtils.nextBytes(DataWordImpl.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWordImpl.BYTES);
        Callback.putStorage(address.toBytes(), key, value);
        assertArrayEquals(value, Callback.getStorage(address.toBytes(), key));
    }

    @Test
    public void testPutThenGetStorageMultipleTimes() {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        pushNewRepo(repo);
        int numAddrs = RandomUtils.nextInt(5, 10);
        List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, false);
        Address[] addresses = unpackAddresses(packs);
        byte[][] keys = unpackKeys(packs);
        byte[][] values = unpackValues(packs);
        for (int i = 0; i < numAddrs; i++) {
            assertArrayEquals(values[i], Callback.getStorage(addresses[i].toBytes(), keys[i]));
        }
    }

    @Test
    public void testPutThenGetStorageAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        List<List<ByteArrayWrapper>> packsPerDepth = new ArrayList<>();
        for (int i = 0; i < depths; i++) {
            RepositoryCache repo =
                    new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
            pushNewRepo(repo);
            int numAddrs = RandomUtils.nextInt(5, 10);
            List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, false);
            packsPerDepth.add(0, packs);
        }
        for (int i = 0; i < depths; i++) {
            Address[] addresses = unpackAddresses(packsPerDepth.get(i));
            byte[][] keys = unpackKeys(packsPerDepth.get(i));
            byte[][] values = unpackValues(packsPerDepth.get(i));
            for (int j = 0; j < addresses.length; j++) {
                assertArrayEquals(values[j], Callback.getStorage(addresses[j].toBytes(), keys[j]));
            }
            Callback.pop();
        }
    }

    @Test
    public void testSelfDestructOwnerIsBeneficiary() {
        BigInteger ownerBalance = new BigInteger("2385234");
        BigInteger ownerNonce = new BigInteger("353245");
        Address owner = getNewAddressInRepo(ownerBalance, ownerNonce);
        Address beneficiary = new Address(Arrays.copyOf(owner.toBytes(), Address.SIZE));
        SideEffects helper = new SideEffects();
        ExecutionContext ctx = mockContext();
        when(ctx.getSideEffects()).thenReturn(helper);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(ctx);
        when(pair.getRight()).thenReturn(wrapInKernelInterface(dummyRepo));
        Callback.push(pair);

        Callback.selfDestruct(owner.toBytes(), owner.toBytes());
        checkSelfDestruct(owner, ownerBalance, ownerNonce, beneficiary, BigInteger.ZERO);
    }

    @Test
    public void testSelfDestructOwnerNotBeneficiary() {
        BigInteger ownerBalance = new BigInteger("32542345634");
        BigInteger ownerNonce = new BigInteger("32565378");
        BigInteger benBalance = new BigInteger("3252323");
        BigInteger benNonce = new BigInteger("4334342355");
        Address owner = getNewAddressInRepo(ownerBalance, ownerNonce);
        Address beneficiary = getNewAddressInRepo(benBalance, benNonce);
        SideEffects helper = new SideEffects();
        ExecutionContext ctx = mockContext();
        when(ctx.getSideEffects()).thenReturn(helper);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(ctx);
        when(pair.getRight()).thenReturn(wrapInKernelInterface(dummyRepo));
        Callback.push(pair);

        Callback.selfDestruct(owner.toBytes(), beneficiary.toBytes());
        checkSelfDestruct(owner, ownerBalance, ownerNonce, beneficiary, benBalance);
    }

    @Test
    public void testSelfDestructOnMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        for (int i = 0; i < depths; i++) {
            Callback.push(mockPair());
        }
        for (int i = 0; i < depths; i++) {
            BigInteger ownerBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 1_000_000));
            BigInteger ownerNonce = BigInteger.valueOf(RandomUtils.nextLong(0, 1_000_000));
            BigInteger benBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 1_000_000));
            BigInteger benNonce = BigInteger.valueOf(RandomUtils.nextLong(0, 1_000_000));
            Address owner =
                    getNewAddressInRepo(
                            Callback.kernelRepo().getRepositoryCache(), ownerBalance, ownerNonce);
            Address beneficiary =
                    getNewAddressInRepo(
                            Callback.kernelRepo().getRepositoryCache(), benBalance, benNonce);

            // Test every other with owner as beneficiary
            if (i % 2 == 0) {
                Callback.selfDestruct(owner.toBytes(), beneficiary.toBytes());
                checkSelfDestruct(owner, ownerBalance, ownerNonce, beneficiary, benBalance);
            } else {
                Callback.selfDestruct(owner.toBytes(), owner.toBytes());
                checkSelfDestruct(owner, ownerBalance, ownerNonce, owner, BigInteger.ZERO);
            }
            Callback.pop();
        }
    }

    @Test
    public void testLogWithZeroTopics() {
        Address address = getNewAddress();
        byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
        byte[] topics = makeTopics(0);
        Callback.push(mockPair());
        Callback.log(address.toBytes(), topics, data);
        checkLog(address, topics, data);
    }

    @Test
    public void testLogWithOneTopic() {
        Address address = getNewAddress();
        byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
        byte[] topics = makeTopics(1);
        Callback.push(mockPair());
        Callback.log(address.toBytes(), topics, data);
        checkLog(address, topics, data);
    }

    @Test
    public void testLogWithMultipleTopics() {
        Address address = getNewAddress();
        byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
        byte[] topics = makeTopics(RandomUtils.nextInt(3, 10));
        Callback.push(mockPair());
        Callback.log(address.toBytes(), topics, data);
        checkLog(address, topics, data);
    }

    @Test
    public void testLogAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        for (int i = 0; i < depths; i++) {
            Callback.push(mockPair());
        }
        for (int i = 0; i < depths; i++) {
            Address address = getNewAddress();
            byte[] data = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
            byte[] topics = makeTopics(1);
            Callback.log(address.toBytes(), topics, data);
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
                        new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES)),
                        false,
                        false,
                        ExecutionContext.DELEGATECALL,
                        nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight())
                .thenReturn(
                        new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig)));
        Callback.push(pair);
        ExecutionContext ctx =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES)),
                        false,
                        false,
                        ExecutionContext.DELEGATECALL,
                        nrgLimit);
        byte[] message =
                generateContextMessage(
                        ctx.getDestinationAddress(),
                        ctx.getSenderAddress(),
                        ctx.getTransactionEnergy(),
                        new DataWordImpl(ctx.getTransferValue()),
                        ctx.getTransactionData(),
                        ctx.getTransactionStackDepth(),
                        ctx.getTransactionKind(),
                        ctx.getFlags());

        TransactionContext expectedContext = makeExpectedContext(context, ctx);
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
                            new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES)),
                            false,
                            false,
                            ExecutionContext.DELEGATECALL,
                            nrgLimit);
            Pair pair = mockEmptyPair();
            when(pair.getLeft()).thenReturn(context);
            when(pair.getRight())
                    .thenReturn(
                            new AionRepositoryCache(
                                    AionRepositoryImpl.createForTesting(repoConfig)));
            Callback.push(pair);
        }
        for (int i = 0; i < depths; i++) {
            // test every other ctx with empty data
            ExecutionContext ctx =
                    newExecutionContext(
                            getNewAddress(),
                            getNewAddress(),
                            new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES)),
                            i % 2 == 0,
                            false,
                            ExecutionContext.DELEGATECALL,
                            nrgLimit);
            byte[] message =
                    generateContextMessage(
                            ctx.getDestinationAddress(),
                            ctx.getSenderAddress(),
                            ctx.getTransactionEnergy(),
                            new DataWordImpl(ctx.getTransferValue()),
                            ctx.getTransactionData(),
                            ctx.getTransactionStackDepth(),
                            ctx.getTransactionKind(),
                            ctx.getFlags());
            TransactionContext expectedContext = makeExpectedContext(Callback.context(), ctx);
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
                        new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES)),
                        false,
                        false,
                        ExecutionContext.DELEGATECALL,
                        nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight())
                .thenReturn(
                        new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig)));
        Callback.push(pair);
        ExecutionContext ctx =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES)),
                        true,
                        false,
                        ExecutionContext.DELEGATECALL,
                        nrgLimit);
        byte[] message =
                generateContextMessage(
                        ctx.getDestinationAddress(),
                        ctx.getSenderAddress(),
                        ctx.getTransactionEnergy(),
                        new DataWordImpl(ctx.getTransferValue()),
                        ctx.getTransactionData(),
                        ctx.getTransactionStackDepth(),
                        ctx.getTransactionKind(),
                        ctx.getFlags());

        TransactionContext expectedContext = makeExpectedContext(context, ctx);
        compareContexts(expectedContext, Callback.parseMessage(message));
    }

    @Test
    public void testCallStackDepthTooLarge() {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                newExecutionContext(
                        getNewAddress(),
                        getNewAddress(),
                        new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES)),
                        false,
                        false,
                        ExecutionContext.DELEGATECALL,
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
                        new DataWordImpl(context.getTransferValue()),
                        context.getTransactionData(),
                        context.getTransactionStackDepth(),
                        Constants.MAX_CALL_DEPTH,
                        0);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.call(message));
        assertEquals(FastVmResultCode.FAILURE, result.getResultCode());
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testCallCallersBalanceLessThanCallValue() {
        BigInteger balance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        Address caller = getNewAddressInRepo(repo, balance, BigInteger.ZERO);
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context =
                newExecutionContext(
                        caller,
                        getNewAddress(),
                        new DataWordImpl(balance.add(BigInteger.ONE)),
                        false,
                        false,
                        ExecutionContext.DELEGATECALL,
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
                        new DataWordImpl(context.getTransferValue()),
                        context.getTransactionData(),
                        context.getTransactionStackDepth(),
                        0,
                        0);
        FastVmTransactionResult result = FastVmTransactionResult.fromBytes(Callback.call(message));
        assertEquals(FastVmResultCode.FAILURE, result.getResultCode());
        assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledIsSuccessSeptForkDisabled() {
        performCallIsPrecompiledIsSuccessSeptForkDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledIsSuccessSeptForkDisabled() {
        performCallIsPrecompiledIsSuccessSeptForkDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledIsSuccessSeptForkDisabled() {
        performCallIsPrecompiledIsSuccessSeptForkDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledNotSuccessSeptForkDisabled() {
        performCallIsPrecompiledNotSuccessSeptForkDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledNotSuccessSeptForkDisabled() {
        performCallIsPrecompiledNotSuccessSeptForkDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledNotSuccessSeptForkDisabled() {
        performCallIsPrecompiledNotSuccessSeptForkDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledIsSuccessSeptForkEnabled() {
        performCallIsPrecompiledIsSuccessSeptForkEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledIsSuccessSeptForkEnabled() {
        performCallIsPrecompiledIsSuccessSeptForkEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledIsSuccessSeptForkEnabled() {
        performCallIsPrecompiledIsSuccessSeptForkEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledNotSuccessSeptForkEnabled() {
        performCallIsPrecompiledNotSuccessSeptForkEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledNotSuccessSeptForkEnabled() {
        performCallIsPrecompiledNotSuccessSeptForkEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledNotSuccessSeptForkEnabled() {
        performCallIsPrecompiledNotSuccessSeptForkEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoRecipientSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(
                ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoRecipientSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoRecipientSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoRecipientSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(
                ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoRecipientSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoRecipientSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoCodeSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoCodeSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoCodeSeptForkDisabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoCodeSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoCodeSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoCodeSeptForkEnabled() {
        performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(ExecutionContext.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(
                ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(
                ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(ExecutionContext.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(
                ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(
                ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(ExecutionContext.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(
                ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(
                ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(ExecutionContext.CALL);
    }

    @Test
    public void
            testPerformCallDelegateCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(
                ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(
                ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(ExecutionContext.CALL);
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
                        ExecutionContext.CREATE,
                        new byte[0],
                        true,
                        false,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
                        ExecutionContext.CREATE,
                        new byte[0],
                        true,
                        false,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null); // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(null); // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(null); // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        mockedResult.setResultCodeAndEnergyRemaining(
                FastVmResultCode.FAILURE, 0); // nrgLimit causes failure post-execution.
        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        true,
                        nrgLimit);

        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        mockedResult.setResultCodeAndEnergyRemaining(
                FastVmResultCode.FAILURE, 0); // nrgLimit causes failure post-execution.
        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        false,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        false,
                        nrgLimit);

        byte[] code = RandomUtils.nextBytes(50);
        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT, code);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context =
                setupTestForPerformCall(
                        callerBalance,
                        recipientBalance,
                        true,
                        ExecutionContext.CREATE,
                        null,
                        false,
                        false,
                        nrgLimit);

        byte[] code = RandomUtils.nextBytes(50);
        FastVmTransactionResult mockedResult =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT, code);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(
                context,
                vm,
                factory,
                mockedResult,
                false,
                ExecutionContext.CREATE,
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
                long nrgLimit = Constants.NRG_CODE_DEPOSIT;
                ExecutionContext context =
                        setupTestForPerformCall(
                                callerBalance,
                                recipientBalance,
                                false,
                                ExecutionContext.CREATE,
                                null,
                                false,
                                false,
                                nrgLimit);

                byte[] code = RandomUtils.nextBytes(50);
                FastVmTransactionResult mockedResult =
                        new FastVmTransactionResult(resCode, Constants.NRG_CODE_DEPOSIT, code);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);

                runPerformCallAndCheck(
                        context,
                        vm,
                        factory,
                        mockedResult,
                        false,
                        ExecutionContext.CREATE,
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
                long nrgLimit = Constants.NRG_CODE_DEPOSIT;
                ExecutionContext context =
                        setupTestForPerformCall(
                                callerBalance,
                                recipientBalance,
                                true,
                                ExecutionContext.CREATE,
                                null,
                                false,
                                false,
                                nrgLimit);

                byte[] code = RandomUtils.nextBytes(50);
                FastVmTransactionResult mockedResult =
                        new FastVmTransactionResult(resCode, Constants.NRG_CODE_DEPOSIT, code);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);

                runPerformCallAndCheck(
                        context,
                        vm,
                        factory,
                        mockedResult,
                        false,
                        ExecutionContext.CREATE,
                        false,
                        false,
                        code);
                checkContextHelper(false);
                checkPerformCallState(context, callerBalance, false, false, false);
            }
        }
    }

    // <----------METHODS BELOW ARE TESTS THAT ARE SHARED BY MULTIPLE TESTS AND SO REUSED---------->

    private void performCallIsPrecompiledIsSuccessSeptForkDisabled(int kind) {
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
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(mockedResult);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private void performCallIsPrecompiledNotSuccessSeptForkDisabled(int kind) {
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
                                new byte[0],
                                false,
                                false,
                                nrgLimit);

                FastVmTransactionResult mockedResult = new FastVmTransactionResult(code, 0);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(mockedResult);

                runPerformCallAndCheck(
                        context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private void performCallIsPrecompiledIsSuccessSeptForkEnabled(int kind) {
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
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(mockedResult);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private void performCallIsPrecompiledNotSuccessSeptForkEnabled(int kind) {
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
                                nrgLimit);

                FastVmTransactionResult mockedResult = new FastVmTransactionResult(code, 0);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(mockedResult);

                runPerformCallAndCheck(
                        context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractNoRecipientSeptForkDisabled(int kind) {
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
            ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

            // There is no recipient hence vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, factory, mockedResult, true, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractNoRecipientSeptForkEnabled(int kind) {
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
            ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

            // There is no recipient hence vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, factory, mockedResult, true, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractNoCodeSeptForkDisabled(int kind) {
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
            ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

            // The recipient's code is empty, hence the vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, factory, mockedResult, true, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractNoCodeSeptForkEnabled(int kind) {
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
            ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

            // The recipient's code is empty, hence the vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setResultCode(FastVmResultCode.SUCCESS);
            runPerformCallAndCheck(
                    context, vm, factory, mockedResult, true, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkDisabled(int kind) {
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
        ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkDisabled(int kind) {
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
                ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

                runPerformCallAndCheck(
                        context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractIsCodeIsSuccessSeptForkEnabled(int kind) {
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
        ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private void performCallIsNotPrecompiledContractIsCodeNotSuccessSeptForkEnabled(int kind) {
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
                ContractFactory factory = mockFactory(null); // signal not a precompiled contract.

                runPerformCallAndCheck(
                        context, vm, factory, mockedResult, false, kind, false, false, null);
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

    private Pair<TransactionContext, KernelInterfaceForFastVM> mockEmptyPair() {
        return mock(Pair.class);
    }

    /**
     * Returns a mocked pair whose left entry is a mocked context that returns a new helper when
     * helper is called and whose right entry is a new DummyRepository.
     */
    private Pair<TransactionContext, KernelInterfaceForFastVM> mockPair() {
        ExecutionContext context = mockContext();
        SideEffects helper = new SideEffects();
        when(context.getSideEffects()).thenReturn(helper);
        Pair pair = mock(Pair.class);
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight())
                .thenReturn(
                        new KernelInterfaceForFastVM(
                                new AionRepositoryCache(
                                        AionRepositoryImpl.createForTesting(repoConfig)),
                                true,
                                false,
                                new DataWordImpl(),
                                0L,
                                0L,
                                0L,
                                Address.ZERO_ADDRESS()));
        return pair;
    }

    private ExecutionContext mockContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getBlockNumber()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getBlockTimestamp()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getBlockDifficulty()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getBlockEnergyLimit()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getMinerAddress()).thenReturn(getNewAddress());
        when(context.getSenderAddress()).thenReturn(getNewAddress());
        when(context.getTransactionData())
                .thenReturn(RandomUtils.nextBytes(RandomUtils.nextInt(0, 50)));
        when(context.getTransferValue())
                .thenReturn(new BigInteger(1, RandomUtils.nextBytes(DataWordImpl.BYTES)));
        when(context.getTransactionEnergy()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getTransactionHash()).thenReturn(RandomUtils.nextBytes(32));
        when(context.getTransactionStackDepth()).thenReturn(RandomUtils.nextInt(0, 1000));
        return context;
    }

    private ExecutionContext newExecutionContext(
            Address caller,
            Address recipient,
            DataWordImpl callValue,
            boolean isEmptyData,
            boolean septForkEnabled,
            int kind,
            long nrgLimit) {

        byte[] txHash = RandomUtils.nextBytes(32);
        Address origin = getNewAddress();
        DataWordImpl nrgPrice = new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES));
        byte[] callData;
        if (isEmptyData) {
            callData = new byte[0];
        } else {
            callData = RandomUtils.nextBytes(RandomUtils.nextInt(10, 50));
        }
        int depth = RandomUtils.nextInt(0, Constants.MAX_CALL_DEPTH - 1);
        int flags = RandomUtils.nextInt(100, 100_000);
        Address blockCoinbase = getNewAddress();
        long blockNumber;
        if (!septForkEnabled) {
            blockNumber = RandomUtils.nextLong(0, 999_999);
        } else {
            blockNumber = 1_000_000;
        }
        long blockTimestamp = RandomUtils.nextLong(100, 100_000);
        long blockNrgLimit = RandomUtils.nextLong(100, 100_000);
        DataWordImpl blockDifficulty = new DataWordImpl(RandomUtils.nextBytes(DataWordImpl.BYTES));
        return new ExecutionContext(
                null,
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
            Address address,
            Address caller,
            long nrgLimit,
            DataWord callValue,
            byte[] callData,
            int depth,
            int kind,
            int flags) {

        int len =
                (Address.SIZE * 2)
                        + DataWordImpl.BYTES
                        + (Integer.BYTES * 4)
                        + Long.BYTES
                        + callData.length;
        ByteBuffer buffer = ByteBuffer.allocate(len).order(ByteOrder.BIG_ENDIAN);
        buffer.put(address.toBytes());
        buffer.put(caller.toBytes());
        buffer.putLong(nrgLimit);
        buffer.put(callValue.getData());
        buffer.putInt(callData.length);
        buffer.put(callData);
        buffer.putInt(depth);
        buffer.putInt(kind);
        buffer.putInt(flags);
        return buffer.array();
    }

    private void compareContexts(TransactionContext context, TransactionContext other) {
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

    private KernelInterfaceForFastVM mockKernelRepo() {
        RepositoryCache cache = mock(RepositoryCache.class);
        when(cache.toString()).thenReturn("mocked repo.");
        KernelInterfaceForFastVM kernel = mock(KernelInterfaceForFastVM.class);
        when(kernel.getRepositoryCache()).thenReturn(cache);
        when(kernel.getCode(Mockito.any(Address.class))).thenReturn(RandomUtils.nextBytes(30));
        return kernel;
    }

    private void compareMockContexts(TransactionContext context, TransactionContext other) {
        assertEquals(context.getBlockNumber(), other.getBlockNumber());
        assertEquals(context.getSenderAddress(), other.getSenderAddress());
        assertArrayEquals(context.getTransactionData(), other.getTransactionData());
        assertEquals(context.getTransferValue(), other.getTransferValue());
        assertEquals(context.getTransactionEnergy(), other.getTransactionEnergy());
        assertArrayEquals(context.getTransactionHash(), other.getTransactionHash());
        assertEquals(context.getTransactionStackDepth(), other.getTransactionStackDepth());
    }

    private void compareRepos(RepositoryCache cache, RepositoryCache other) {
        Address addr = getNewAddress();
        assertEquals(cache.toString(), other.toString());
        assertEquals(cache.getCode(addr), other.getCode(addr));
    }

    private Address getNewAddressInRepo(
            RepositoryCache repo, BigInteger balance, BigInteger nonce) {
        Address address = getNewAddress();
        repo.createAccount(address);
        repo.addBalance(address, balance);
        repo.setNonce(address, nonce);
        return address;
    }

    private Address getNewAddressInRepo(BigInteger balance, BigInteger nonce) {
        Address address = getNewAddress();
        dummyRepo.createAccount(address);
        dummyRepo.addBalance(address, balance);
        dummyRepo.setNonce(address, nonce);
        return address;
    }

    private Address getNewAddress() {
        byte[] bytes = RandomUtils.nextBytes(Address.SIZE);
        bytes[0] = (byte) 0xa0;
        return new Address(bytes);
    }

    /**
     * Checks the state after selfDestruct is called to ensure it is as expected.
     *
     * @param owner The transaction owner.
     * @param ownerBalance The owner's balance prior to selfDestruct.
     * @param ownerNonce The owner's nonce prior to selfDestruct.
     * @param beneficiary The transaction beneficiary.
     * @param beneficiaryOldBalance The beneficiary's balance prior to selfDestruct.
     */
    private void checkSelfDestruct(
            Address owner,
            BigInteger ownerBalance,
            BigInteger ownerNonce,
            Address beneficiary,
            BigInteger beneficiaryOldBalance) {

        RepositoryCache repo = Callback.kernelRepo().getRepositoryCache();
        TransactionContext ctx = Callback.context();
        TransactionSideEffects helper = ctx.getSideEffects();
        assertEquals(BigInteger.ZERO, repo.getBalance(owner));
        if (!owner.equals(beneficiary)) {
            assertEquals(beneficiaryOldBalance.add(ownerBalance), repo.getBalance(beneficiary));
        }
        assertEquals(1, helper.getAddressesToBeDeleted().size());
        assertEquals(helper.getAddressesToBeDeleted().get(0), owner);
        assertEquals(1, helper.getInternalTransactions().size());
        InternalTransactionInterface tx = helper.getInternalTransactions().get(0);
        assertEquals(owner, tx.getSenderAddress());
        assertEquals(beneficiary, tx.getDestinationAddress());
        assertEquals(ownerNonce, new BigInteger(tx.getNonce()));
        assertEquals(new DataWordImpl(ownerBalance), new DataWordImpl(tx.getValue()));
        assertArrayEquals(new byte[0], tx.getData());
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
    private void checkLog(Address address, byte[] topics, byte[] data) {
        TransactionSideEffects helper = Callback.context().getSideEffects();
        assertEquals(1, helper.getExecutionLogs().size());
        IExecutionLog log = helper.getExecutionLogs().get(0);
        assertEquals(address, log.getSourceAddress());
        assertArrayEquals(data, log.getData());
        List<byte[]> logTopics = log.getTopics();
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
    private TransactionContext makeExpectedContext(
            TransactionContext previous, TransactionContext context) {
        return new ExecutionContext(
                null,
                previous.getTransactionHash(),
                context.getDestinationAddress(),
                previous.getOriginAddress(),
                context.getSenderAddress(),
                new DataWordImpl(previous.getTransactionEnergyPrice()),
                context.getTransactionEnergy(),
                new DataWordImpl(context.getTransferValue()),
                context.getTransactionData(),
                context.getTransactionStackDepth(),
                context.getTransactionKind(),
                context.getFlags(),
                previous.getMinerAddress(),
                previous.getBlockNumber(),
                previous.getBlockTimestamp(),
                previous.getBlockEnergyLimit(),
                new DataWordImpl(previous.getBlockDifficulty()));
    }

    /**
     * Pushes a new pair onto the stack which holds a repo whose getBlockStore method returns a
     * block store whose getBlockHashByNumber method returns hash when called using blockNum.
     */
    private void pushNewBlockHash(long blockNum, byte[] hash) {
        ExecutionContext context = mockContext();
        KernelInterfaceForFastVM kernel = mockKernelRepo();
        when(kernel.getBlockHashByNumber(blockNum)).thenReturn(hash);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(kernel);
        Callback.push(pair);
    }

    /**
     * Pushes a new pair onto the stack which holds a repo whose getCode method returns code when
     * called using address.
     */
    private void pushNewCode(Address address, byte[] code) {
        ExecutionContext context = mockContext();
        KernelInterfaceForFastVM repo = mockKernelRepo();
        when(repo.getCode(address)).thenReturn(code);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
    }

    /**
     * Pushes a new pair onto the stack which holds a repo with an account that has balance balance.
     * The newly created account with this balance is returned.
     */
    private Address pushNewBalance(BigInteger balance) {
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        Address address = getNewAddressInRepo(repo, balance, BigInteger.ZERO);
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(wrapInKernelInterface(repo));
        Callback.push(pair);
        return address;
    }

    private Address[] unpackAddresses(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        Address[] addresses = new Address[len];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).toBytes();
            addresses[i] = new Address(Arrays.copyOfRange(pack, 0, Address.SIZE));
        }
        return addresses;
    }

    private byte[][] unpackKeys(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        byte[][] keys = new byte[len][];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).toBytes();
            keys[i] = Arrays.copyOfRange(pack, Address.SIZE, Address.SIZE + DataWordImpl.BYTES);
        }
        return keys;
    }

    private byte[][] unpackValues(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        byte[][] values = new byte[len][];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).toBytes();
            values[i] = Arrays.copyOfRange(pack, pack.length - DataWordImpl.BYTES, pack.length);
        }
        return values;
    }

    private byte[] packIntoBytes(Address address, byte[] key, byte[] value) {
        byte[] pack = new byte[Address.SIZE + (DataWordImpl.BYTES * 2)];
        System.arraycopy(address.toBytes(), 0, pack, 0, Address.SIZE);
        System.arraycopy(key, 0, pack, Address.SIZE, DataWordImpl.BYTES);
        System.arraycopy(value, 0, pack, Address.SIZE + DataWordImpl.BYTES, DataWordImpl.BYTES);
        return pack;
    }

    private List<ByteArrayWrapper> packup(Address[] addresses, byte[][] keys, byte[][] values) {
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
            RepositoryCache repo, int num, boolean pushToRepo) {

        Address[] addresses = new Address[num];
        byte[][] keys = new byte[num][];
        byte[][] values = new byte[num][];
        for (int i = 0; i < num; i++) {
            keys[num - 1 - i] = RandomUtils.nextBytes(DataWordImpl.BYTES);
            values[num - 1 - i] = RandomUtils.nextBytes(DataWordImpl.BYTES);
            addresses[num - 1 - i] =
                    pushNewStorageEntry(repo, keys[num - 1 - i], values[num - 1 - i], pushToRepo);
        }
        return packup(addresses, keys, values);
    }

    /**
     * Pushes a new entry into repo if pushToRepo is true, otherwise pushes it onto Callback stack,
     * with key and value as the key-value pair for some random address, which is then returned.
     */
    private Address pushNewStorageEntry(
            RepositoryCache repo, byte[] key, byte[] value, boolean pushToRepo) {

        Address address = getNewAddress();
        if (pushToRepo) {
            repo.addStorageRow(
                    address,
                    new DataWordImpl(key).toWrapper(),
                    new ByteArrayWrapper(new DataWordImpl(value).getNoLeadZeroesData()));
        } else {
            Callback.putStorage(address.toBytes(), key, value);
        }
        return address;
    }

    /**
     * Pushes repo onto the top of the Callback stack by adding a new mocked Pair whose right entry
     * is repo.
     */
    private void pushNewRepo(RepositoryCache repo) {
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(wrapInKernelInterface(repo));
        Callback.push(pair);
    }

    /**
     * Puts the key-value pair key and value into the Callback storage under a random address and
     * returns that address.
     */
    private Address putInStorage(byte[] key, byte[] value) {
        Address address = getNewAddress();
        Callback.putStorage(address.toBytes(), key, value);
        return address;
    }

    /**
     * Returns a mocked ContractFactory whose returned contract's execute method will return result.
     * Unless result is null! In this case the mocked factory returns null, which tells Callback
     * that the transaction is not a precompiled contract.
     */
    private ContractFactory mockFactory(FastVmTransactionResult result) {
        PrecompiledContract contract = mock(PrecompiledContract.class);
        when(contract.execute(Mockito.any(byte[].class), Mockito.anyLong())).thenReturn(result);
        ContractFactory factory = mock(ContractFactory.class);
        if (result == null) {
            when(factory.getPrecompiledContract(
                            Mockito.any(ExecutionContext.class),
                            Mockito.any(KernelInterfaceForFastVM.class)))
                    .thenReturn(null);
            return factory;
        } else {
            when(factory.getPrecompiledContract(
                            Mockito.any(ExecutionContext.class),
                            Mockito.any(KernelInterfaceForFastVM.class)))
                    .thenReturn(contract);
        }
        return factory;
    }

    /** Returns a mocked FastVM whose run method returns result. */
    private FastVM mockFastVM(FastVmTransactionResult result) {
        FastVM vm = mock(FastVM.class);
        when(vm.run(
                        Mockito.any(byte[].class),
                        Mockito.any(ExecutionContext.class),
                        Mockito.any(KernelInterfaceForFastVM.class)))
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
            int kind,
            FastVmResultCode resultCode) {

        TransactionContext ctx = Callback.context();
        checkInternalTransaction(
                context,
                ctx.getSideEffects().getInternalTransactions().get(0),
                isCreateContract,
                true);
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
     * @param mockFac A mocked ContractFactory.
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
            ContractFactory mockFac,
            FastVmTransactionResult expectedResult,
            boolean vmGotBadCode,
            int kind,
            boolean contractExisted,
            boolean postExecuteWasSuccess,
            byte[] code) {

        byte[] message =
                generateContextMessage(
                        context.getDestinationAddress(),
                        context.getSenderAddress(),
                        context.getTransactionEnergy(),
                        new DataWordImpl(context.getTransferValue()),
                        context.getTransactionData(),
                        context.getTransactionStackDepth(),
                        kind,
                        0);
        FastVmTransactionResult result =
                FastVmTransactionResult.fromBytes(Callback.performCall(message, mockVM, mockFac));
        assertEquals(expectedResult.getResultCode(), result.getResultCode());
        if (vmGotBadCode) {
            assertEquals(context.getTransactionEnergy(), result.getEnergyRemaining());
        } else {
            assertEquals(expectedResult.getEnergyRemaining(), result.getEnergyRemaining());
        }
        if (kind == ExecutionContext.CREATE) {
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
            Address contract =
                    (result.getReturnData() == null
                                    || Arrays.equals(result.getReturnData(), new byte[0]))
                            ? null
                            : new Address(result.getReturnData());

            if (contract != null) {
                assertArrayEquals(code, Callback.kernelRepo().getCode(contract));
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
            int kind,
            byte[] code,
            boolean contractExists,
            boolean dataIsEmpty,
            long nrgLimit) {

        BigInteger callerNonce = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        RepositoryCache repo =
                new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        Address caller = getNewAddressInRepo(repo, callerBalance, callerNonce);
        if (contractExists) {
            Address contract =
                    new Address(HashUtil.calcNewAddr(caller.toBytes(), callerNonce.toByteArray()));
            repo.createAccount(contract);
            repo.addBalance(contract, BigInteger.ZERO);
        }

        Address recipient;
        if (code == null) {
            recipient = getNewAddress();
        } else {
            recipient = getNewAddressInRepo(repo, recipientBalance, BigInteger.ZERO);
            repo.saveCode(recipient, code);
        }
        ExecutionContext context =
                newExecutionContext(
                        caller,
                        recipient,
                        new DataWordImpl(callerBalance),
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
    private void checkHelperForRejections(TransactionSideEffects helper) {
        for (InternalTransactionInterface tx : helper.getInternalTransactions()) {
            assertTrue(tx.isRejected());
        }
    }

    /**
     * Asserts that the account balances are in the expected state after a call to performCall. Note
     * if we are doing CALLCODE or DELEGATECALL then no value gets transferred in Callback.
     */
    private void checkPerformCallBalances(
            Address caller,
            BigInteger callerPrevBalance,
            Address recipient,
            BigInteger recipientPrevBalance,
            BigInteger callValue,
            boolean wasNoRecipient,
            int kind,
            FastVmResultCode resultCode) {

        if (caller.equals(recipient)) {
            assertEquals(callerPrevBalance, Callback.kernelRepo().getBalance(caller));
        } else {
            if (kind == ExecutionContext.DELEGATECALL || kind == ExecutionContext.CALLCODE) {
                assertEquals(callerPrevBalance, Callback.kernelRepo().getBalance(caller));
            } else {
                if (!resultCode.isSuccess()) {
                    assertEquals(callerPrevBalance, Callback.kernelRepo().getBalance(caller));
                } else {
                    assertEquals(
                            callerPrevBalance.subtract(callValue),
                            Callback.kernelRepo().getBalance(caller));
                }
            }

            if (wasNoRecipient) {
                // if there was no recipient then DummyRepository created that account when Callback
                // transferred balance to it, so its balance should be callValue.
                if (kind == ExecutionContext.DELEGATECALL || kind == ExecutionContext.CALLCODE) {
                    assertEquals(BigInteger.ZERO, Callback.kernelRepo().getBalance(recipient));
                } else {
                    assertEquals(callValue, Callback.kernelRepo().getBalance(recipient));
                }
            } else {
                if (kind == ExecutionContext.DELEGATECALL || kind == ExecutionContext.CALLCODE) {
                    assertEquals(recipientPrevBalance, Callback.kernelRepo().getBalance(recipient));
                } else {
                    if (!resultCode.isSuccess()) {
                        assertEquals(
                                recipientPrevBalance, Callback.kernelRepo().getBalance(recipient));
                    } else {
                        assertEquals(
                                recipientPrevBalance.add(callValue),
                                Callback.kernelRepo().getBalance(recipient));
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
            TransactionContext context,
            InternalTransactionInterface tx,
            boolean isCreateContract,
            boolean wasSuccess) {

        assertEquals(context.getSenderAddress(), tx.getSenderAddress());
        if (isCreateContract) {
            // Decrement nonce because the transaction incremented it after address was made.
            Address contract =
                    new Address(
                            HashUtil.calcNewAddr(
                                    context.getSenderAddress().toBytes(),
                                    wasSuccess
                                            ? Callback.kernelRepo()
                                                    .getNonce(context.getSenderAddress())
                                                    .subtract(BigInteger.ONE)
                                                    .toByteArray()
                                            : Callback.kernelRepo()
                                                    .getNonce(context.getSenderAddress())
                                                    .toByteArray()));
            assertEquals(contract, tx.getDestinationAddress());
        } else {
            assertEquals(context.getDestinationAddress(), tx.getDestinationAddress());
        }

        if (isCreateContract && wasSuccess) {
            assertEquals(
                    Callback.kernelRepo()
                            .getNonce(context.getSenderAddress())
                            .subtract(BigInteger.ONE),
                    new BigInteger(1, tx.getNonce()));
        } else {
            assertEquals(
                    Callback.kernelRepo().getNonce(context.getSenderAddress()),
                    new BigInteger(1, tx.getNonce()));
        }

        assertEquals(new DataWordImpl(context.getTransferValue()), new DataWordImpl(tx.getValue()));
        assertArrayEquals(context.getTransactionData(), tx.getData());
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
        TransactionContext context = Callback.context();
        List<InternalTransactionInterface> internalTxs =
                context.getSideEffects().getInternalTransactions();
        assertEquals(2, internalTxs.size());
        checkInternalTransaction(context, internalTxs.get(0), true, wasSuccess);
        checkSecondInteralTransaction(context, internalTxs.get(1));
        if (!wasSuccess) {
            assertTrue(internalTxs.get(1).isRejected());
        }
    }

    /** Checks the second of the 2 internal transactions created during the CREATE opcode. */
    private void checkSecondInteralTransaction(
            TransactionContext context, InternalTransactionInterface tx) {
        Address caller = context.getSenderAddress();
        assertEquals(context.getSenderAddress(), tx.getSenderAddress());
        if (tx.isRejected()) {
            assertEquals(
                    Callback.kernelRepo().getNonce(caller),
                    new BigInteger(1, tx.getNonce()).subtract(BigInteger.ONE));
            assertArrayEquals(
                    new DataWordImpl(Callback.kernelRepo().getNonce(caller).add(BigInteger.ONE))
                            .getData(),
                    tx.getNonce());
        } else {
            assertEquals(Callback.kernelRepo().getNonce(caller), new BigInteger(1, tx.getNonce()));
            assertArrayEquals(
                    new DataWordImpl(Callback.kernelRepo().getNonce(caller)).getData(),
                    tx.getNonce());
        }
        assertEquals(new DataWordImpl(context.getTransferValue()), new DataWordImpl(tx.getValue()));

        assertArrayEquals(context.getTransactionData(), tx.getData());
        assertNull(tx.getDestinationAddress());
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
        Address caller = Callback.context().getSenderAddress();
        Address contract;
        contract =
                new Address(
                        HashUtil.calcNewAddr(
                                caller.toBytes(),
                                Callback.kernelRepo()
                                        .getNonce(caller)
                                        .subtract(BigInteger.ONE)
                                        .toByteArray()));
        if (postExecuteWasSuccess) {
            assertEquals(callerBalance.subtract(value), Callback.kernelRepo().getBalance(caller));
        } else {
            assertEquals(callerBalance, Callback.kernelRepo().getBalance(caller));
        }
        if (contractExisted) {
            assertEquals(BigInteger.ZERO, Callback.kernelRepo().getBalance(contract));
        } else {
            if (postExecuteWasSuccess && !nrgLessThanDeposit) {
                assertEquals(value, Callback.kernelRepo().getBalance(contract));
            } else {
                assertEquals(BigInteger.ZERO, Callback.kernelRepo().getBalance(contract));
            }
        }
    }

    private static KernelInterfaceForFastVM wrapInKernelInterface(RepositoryCache cache) {
        return new KernelInterfaceForFastVM(
                cache, true, false, new DataWordImpl(), 0L, 0L, 0L, Address.ZERO_ADDRESS());
    }
}
