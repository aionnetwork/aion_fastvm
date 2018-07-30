package org.aion.fastvm;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
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
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.DummyRepo;
import org.aion.vm.DummyRepository;
import org.aion.vm.ExecutionHelper;
import org.aion.zero.types.AionInternalTx;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.aion.base.db.IRepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.ExecutionContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for Callback class.
 */
public class CallbackUnitTest {
    private DummyRepository dummyRepo;

    @Before
    public void setup() {
        dummyRepo = new DummyRepository();
    }

    @After
    public void tearDown() {
        dummyRepo = null;
        while (true) {
            try { Callback.pop(); } catch (NoSuchElementException e) { break; }
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
        Callback.repo();
    }

    @Test
    public void testPeekAtContextInStackSizeOne() {
        ExecutionContext context = mockContext();
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        Callback.push(pair);
        ExecutionContext stackContext = Callback.context();
        compareMockContexts(context, stackContext);
    }

    @Test
    public void testPeekAtRepoInStackSizeOne() {
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo = mockRepo();
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> stackRepo = Callback.repo();
        compareRepos(repo, stackRepo);
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
        try { Callback.pop(); } catch (NoSuchElementException e) { return; }    // hit bottom as wanted.
        fail();
    }

    @Test
    public void testEachDepthOfPoppingLargeStack() {
        int reps = RandomUtils.nextInt(10, 30);
        Pair[] pairs = new Pair[reps];
        for (int i = 0; i < reps; i++) {
            ExecutionContext ctx = mockContext();
            IRepositoryCache repo = mockRepo();
            Pair mockedPair = mockEmptyPair();
            when(mockedPair.getLeft()).thenReturn(ctx);
            when(mockedPair.getRight()).thenReturn(repo);
            pairs[i] = mockedPair;
        }
        for (int i = 0; i < reps; i++) {
            Callback.push(pairs[reps - 1 - i]);
        }
        for (Pair pair : pairs) {
            compareMockContexts((ExecutionContext) pair.getLeft(), Callback.context());
            compareRepos((IRepositoryCache) pair.getRight(), Callback.repo());
            Callback.pop();
        }
        try { Callback.pop(); } catch (NoSuchElementException e) { return; }    // hit bottom as wanted.
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
        assertArrayEquals(new DataWord(balance).getData(), Callback.getBalance(address.toBytes()));
    }

    @Test
    public void testGetBalanceNoSuchAccount() {
        IRepositoryCache repo = new DummyRepository();
        pushNewRepo(repo);
        assertArrayEquals(new byte[DataWord.BYTES], Callback.getBalance(getNewAddress().toBytes()));
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
            assertArrayEquals(new DataWord(balances[i]).getData(), Callback.getBalance(addresses[i].toBytes()));
            Callback.pop();
        }
    }

    @Test
    public void testHasAccountStateWhenAccountExists() {
        Address address = getNewAddress();
        IRepositoryCache repo = mockRepo();
        when(repo.hasAccountState(address)).thenReturn(true);
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        assertTrue(Callback.exists(address.toBytes()));
    }

    @Test
    public void testHasAccountStateWhenAccountDoesNotExist() {
        Address address = getNewAddress();
        IRepositoryCache repo = mockRepo();
        when(repo.hasAccountState(address)).thenReturn(false);
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        assertFalse(Callback.exists(address.toBytes()));
    }

    @Test
    public void testGetStorageIsValidEntry() {
        IRepositoryCache repo = new DummyRepository();
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(DataWord.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        Address address = pushNewStorageEntry(repo, key, value, true);
        assertArrayEquals(value, Callback.getStorage(address.toBytes(), key));
    }

    @Test
    public void testGetStorageNoSuchEntry() {
        IRepositoryCache repo = new DummyRepository();
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(DataWord.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        Address address = pushNewStorageEntry(repo, key, value, true);
        byte[] badKey = Arrays.copyOf(key, DataWord.BYTES);
        badKey[0] = (byte) ~key[0];
        assertArrayEquals(DataWord.ZERO.getData(), Callback.getStorage(address.toBytes(), badKey));
    }

    @Test
    public void testGetStorageMultipleAddresses() {
        IRepositoryCache repo = new DummyRepository();
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
            IRepositoryCache repo = new DummyRepository();
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
        IRepositoryCache repo = new DummyRepository();
        pushNewRepo(repo);
        byte[] key = RandomUtils.nextBytes(DataWord.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        Address address = putInStorage(key, value);
        assertArrayEquals(value, repo.getStorageValue(address, new DataWord(key)).getData());
    }

    @Test
    public void testPutStorageMultipleEntries() {
        int num = RandomUtils.nextInt(3, 10);
        IRepositoryCache repo = new DummyRepository();
        pushNewRepo(repo);
        Address[] addresses = new Address[num];
        byte[][] keys = new byte[num][];
        byte[][] values = new byte[num][];
        for (int i = 0; i < num; i++) {
            keys[num - 1 - i] = RandomUtils.nextBytes(DataWord.BYTES);
            values[num - 1 - i] = RandomUtils.nextBytes(DataWord.BYTES);
            addresses[num - 1 - i] = putInStorage(keys[num -1 - i], values[num - 1 - i]);
        }
        for (int i = 0; i < num; i++) {
            assertArrayEquals(values[i], repo.getStorageValue(addresses[i], new DataWord(keys[i])).getData());
        }
    }

    @Test
    public void testPutStorageMultipleAddresses() {
        IRepositoryCache repo = new DummyRepository();
        pushNewRepo(repo);
        int numAddrs = RandomUtils.nextInt(5, 10);
        List<ByteArrayWrapper> packs = pushNewStorageEntries(repo, numAddrs, false);
        Address[] addresses = unpackAddresses(packs);
        byte[][] keys = unpackKeys(packs);
        byte[][] values = unpackValues(packs);
        for (int i = 0; i < numAddrs; i++) {
            assertArrayEquals(values[i], repo.getStorageValue(addresses[i], new DataWord(keys[i])).getData());
        }
    }

    @Test
    public void testPutStorageMultipleAddressesAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        IRepositoryCache[] repos = new IRepositoryCache[depths];
        List<List<ByteArrayWrapper>> packsPerDepth = new ArrayList<>();
        for (int i = 0; i < depths; i++) {
            repos[depths - 1 - i] = new DummyRepository();
            pushNewRepo(repos[depths - 1 - i]);
            int numAddrs = RandomUtils.nextInt(5, 10);
            List<ByteArrayWrapper> packs = pushNewStorageEntries(
                repos[depths - 1 - i], numAddrs, false);
            packsPerDepth.add(0, packs);
        }
        for (int i = 0; i < depths; i++) {
            Address[] addresses = unpackAddresses(packsPerDepth.get(i));
            byte[][] keys = unpackKeys(packsPerDepth.get(i));
            byte[][] values = unpackValues(packsPerDepth.get(i));
            for (int j = 0; j < addresses.length; j++) {
                assertArrayEquals(values[j], repos[i].getStorageValue(
                    addresses[j], new DataWord(keys[j])).getData());
            }
            Callback.pop();
        }
    }

    @Test
    public void testPutThenGetStorage() {
        IRepositoryCache repo = new DummyRepository();
        pushNewRepo(repo);
        Address address = getNewAddress();
        byte[] key = RandomUtils.nextBytes(DataWord.BYTES);
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        Callback.putStorage(address.toBytes(), key, value);
        assertArrayEquals(value, Callback.getStorage(address.toBytes(), key));
    }

    @Test
    public void testPutThenGetStorageMultipleTimes() {
        IRepositoryCache repo = new DummyRepository();
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
            IRepositoryCache repo = new DummyRepository();
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
        Address beneficiary = new Address(Arrays.copyOf(owner.toBytes(), Address.ADDRESS_LEN));
        ExecutionHelper helper = new ExecutionHelper();
        ExecutionContext ctx = mockContext();
        when(ctx.getHelper()).thenReturn(helper);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(ctx);
        when(pair.getRight()).thenReturn(dummyRepo);
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
        ExecutionHelper helper = new ExecutionHelper();
        ExecutionContext ctx = mockContext();
        when(ctx.getHelper()).thenReturn(helper);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(ctx);
        when(pair.getRight()).thenReturn(dummyRepo);
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
            Address owner = getNewAddressInRepo(Callback.repo(), ownerBalance, ownerNonce);
            Address beneficiary = getNewAddressInRepo(Callback.repo(), benBalance, benNonce);

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
        ExecutionContext context = newExecutionContext(false);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(new DummyRepository());
        Callback.push(pair);
        ExecutionContext ctx = newExecutionContext(false);
        byte[] message = generateContextMessage(ctx.getRecipient(), ctx.getCaller(), ctx.getNrgLimit(),
            ctx.getCallValue(), ctx.getCallData(), ctx.getDepth(), ctx.getKind(), ctx.getFlags());

        ExecutionContext expectedContext = makeExpectedContext(context, ctx);
        compareContexts(expectedContext, Callback.parseMessage(message));
    }

    @Test
    public void testParseMessageAtMultipleStackDepths() {
        int depths = RandomUtils.nextInt(3, 10);
        for (int i = 0; i < depths; i++) {
            ExecutionContext context = newExecutionContext(false);
            Pair pair = mockEmptyPair();
            when(pair.getLeft()).thenReturn(context);
            when(pair.getRight()).thenReturn(new DummyRepository());
            Callback.push(pair);
        }
        for (int i = 0; i < depths; i++) {
            // test every other ctx with empty data
            ExecutionContext ctx = newExecutionContext(i % 2 == 0);
            byte[] message = generateContextMessage(ctx.getRecipient(), ctx.getCaller(), ctx.getNrgLimit(),
                ctx.getCallValue(), ctx.getCallData(), ctx.getDepth(), ctx.getKind(), ctx.getFlags());
            ExecutionContext expectedContext = makeExpectedContext(Callback.context(), ctx);
            compareContexts(expectedContext, Callback.parseMessage(message));
            Callback.pop();
        }
    }

    @Test
    public void testParseMessageUsingZeroLengthData() {
        ExecutionContext context = newExecutionContext(false);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(new DummyRepository());
        Callback.push(pair);
        ExecutionContext ctx = newExecutionContext(true);
        byte[] message = generateContextMessage(ctx.getRecipient(), ctx.getCaller(), ctx.getNrgLimit(),
            ctx.getCallValue(), ctx.getCallData(), ctx.getDepth(), ctx.getKind(), ctx.getFlags());

        ExecutionContext expectedContext = makeExpectedContext(context, ctx);
        compareContexts(expectedContext, Callback.parseMessage(message));
    }

    // <---------------------------------------HELPERS BELOW--------------------------------------->

    private Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>> mockEmptyPair() {
        return mock(Pair.class);
    }

    /**
     * Returns a mocked pair whose left entry is a mocked context that returns a new helper when
     * getHelper is called and whose right entry is a new DummyRepository.
     */
    private Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>> mockPair() {
        ExecutionContext context = mockContext();
        ExecutionHelper helper = new ExecutionHelper();
        when(context.getHelper()).thenReturn(helper);
        Pair pair = mock(Pair.class);
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(new DummyRepository());
        return pair;
    }

    private ExecutionContext mockContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getBlockNumber()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getCaller()).thenReturn(getNewAddress());
        when(context.getCallData()).thenReturn(RandomUtils.nextBytes(RandomUtils.nextInt(0, 50)));
        when(context.getCallValue()).thenReturn(new DataWord(RandomUtils.nextBytes(DataWord.BYTES)));
        when(context.getNrgLimit()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getTransactionHash()).thenReturn(RandomUtils.nextBytes(32));
        when(context.getDepth()).thenReturn(RandomUtils.nextInt(0, 1000));
        return context;
    }

    private ExecutionContext newExecutionContext(boolean isEmptyData) {
        byte[] txHash = RandomUtils.nextBytes(32);
        Address recipient = getNewAddress();
        Address origin = getNewAddress();
        Address caller = getNewAddress();
        DataWord nrgPrice = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        long nrgLimit = RandomUtils.nextLong(100, 100_000);
        DataWord callValue = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        byte[] callData;
        if (isEmptyData) {
            callData = new byte[0];
        } else {
            callData = RandomUtils.nextBytes(RandomUtils.nextInt(10, 50));
        }
        int depth = RandomUtils.nextInt(100, 100_000);
        int kind = RandomUtils.nextInt(100, 100_000);
        int flags = RandomUtils.nextInt(100, 100_000);
        Address blockCoinbase = getNewAddress();
        long blockNumber = RandomUtils.nextLong(100, 100_000);;
        long blockTimestamp = RandomUtils.nextLong(100, 100_000);;
        long blockNrgLimit = RandomUtils.nextLong(100, 100_000);;
        DataWord blockDifficulty = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        return new ExecutionContext(txHash, recipient, origin, caller, nrgPrice, nrgLimit, callValue,
            callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
            blockDifficulty);
    }

    /**
     * Generates a message that is to be passed into the parseMessage method. This message is of the
     * following format:
     *
     *   |32b - address|32b - caller|8b - nrgLimit|16b - callValue|4b - callDataLength|?b - callData|
     *   4b - depth|4b - kind|4b - flags|
     */
    private byte[] generateContextMessage(Address address, Address caller, long nrgLimit,
        DataWord callValue, byte[] callData, int depth, int kind, int flags) {

        int len = (Address.ADDRESS_LEN * 2) + DataWord.BYTES + (Integer.BYTES * 4) + Long.BYTES +
            callData.length;
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

    private void compareContexts(ExecutionContext context, ExecutionContext other) {
        assertEquals(context.getRecipient(), other.getRecipient());
        assertEquals(context.getOrigin(), other.getOrigin());
        assertEquals(context.getCaller(), other.getCaller());
        assertEquals(context.getBlockCoinbase(), other.getBlockCoinbase());
        assertEquals(context.getNrgPrice(), other.getNrgPrice());
        assertEquals(context.getCallValue(), other.getCallValue());
        assertEquals(context.getBlockDifficulty(), other.getBlockDifficulty());
        assertEquals(context.getNrgLimit(), other.getNrgLimit());
        assertEquals(context.getBlockNumber(), other.getBlockNumber());
        assertEquals(context.getBlockTimestamp(), other.getBlockTimestamp());
        assertEquals(context.getBlockNrgLimit(), other.getBlockNrgLimit());
        assertEquals(context.getDepth(), other.getDepth());
        assertEquals(context.getKind(), other.getKind());
        assertEquals(context.getFlags(), other.getFlags());
        assertArrayEquals(context.getTransactionHash(), other.getTransactionHash());
        assertArrayEquals(context.getCallData(), other.getCallData());
    }

    private IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> mockRepo() {
        IRepositoryCache cache = mock(IRepositoryCache.class);
        when(cache.toString()).thenReturn("mocked repo.");
        when(cache.getCode(Mockito.any(Address.class))).thenReturn(RandomUtils.nextBytes(30));
        return cache;
    }

    private void compareMockContexts(ExecutionContext context, ExecutionContext other) {
        assertEquals(context.getBlockNumber(), other.getBlockNumber());
        assertEquals(context.getCaller(), other.getCaller());
        assertArrayEquals(context.getCallData(), other.getCallData());
        assertEquals(context.getCallValue(), other.getCallValue());
        assertEquals(context.getNrgLimit(), other.getNrgLimit());
        assertArrayEquals(context.getTransactionHash(), other.getTransactionHash());
        assertEquals(context.getDepth(), other.getDepth());
    }

    private void compareRepos(IRepositoryCache cache, IRepositoryCache other) {
        Address addr = getNewAddress();
        assertEquals(cache.toString(), other.toString());
        assertEquals(cache.getCode(addr), other.getCode(addr));
    }

    private Address getNewAddressInRepo(IRepositoryCache repo, BigInteger balance, BigInteger nonce) {
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
        return new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
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
    private void checkSelfDestruct(Address owner, BigInteger ownerBalance, BigInteger ownerNonce,
        Address beneficiary, BigInteger beneficiaryOldBalance) {

        IRepositoryCache repo = Callback.repo();
        ExecutionContext ctx = Callback.context();
        ExecutionHelper helper = ctx.getHelper();
        assertEquals(BigInteger.ZERO, repo.getBalance(owner));
        if (!owner.equals(beneficiary)) {
            assertEquals(beneficiaryOldBalance.add(ownerBalance), repo.getBalance(beneficiary));
        }
        assertEquals(1, helper.getDeleteAccounts().size());
        assertEquals(helper.getDeleteAccounts().get(0), owner);
        assertEquals(1, helper.getInternalTransactions().size());
        AionInternalTx tx = helper.getInternalTransactions().get(0);
        assertEquals(owner, tx.getFrom());
        assertEquals(beneficiary, tx.getTo());
        assertEquals(ownerNonce, new BigInteger(tx.getNonce()));
        assertEquals(new DataWord(ownerBalance), new DataWord(tx.getValue()));
        assertArrayEquals(new byte[0], tx.getData());
        assertEquals("selfdestruct", tx.getNote());
        assertEquals(ctx.getDepth(), tx.getDeep());
        assertEquals(0, tx.getIndex());
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
        ExecutionHelper helper = Callback.context().getHelper();
        assertEquals(1, helper.getLogs().size());
        Log log = helper.getLogs().get(0);
        assertEquals(address, log.getAddress());
        assertArrayEquals(data, log.getData());
        List<byte[]> logTopics = log.getTopics();
        int index = 0;
        for (byte[] topic : logTopics) {
            assertArrayEquals(topic, Arrays.copyOfRange(topics, index, index + 32));
            index += 32;
        }
        if (logTopics.isEmpty()) { assertEquals(0,topics.length); }
    }

    /**
     * Makes the expected ExecutionContext object that parseMessage would return when previous is
     * the context at the top of the stack when the fields in context are used to generate the
     * message given to the parseMessage method.
     */
    private ExecutionContext makeExpectedContext(ExecutionContext previous, ExecutionContext context) {
        return new ExecutionContext(previous.getTransactionHash(), context.getRecipient(),
            previous.getOrigin(), context.getCaller(), previous.getNrgPrice(), context.getNrgLimit(),
            context.getCallValue(), context.getCallData(), context.getDepth(), context.getKind(),
            context.getFlags(), previous.getBlockCoinbase(), previous.getBlockNumber(),
            previous.getBlockTimestamp(), previous.getBlockNrgLimit(), previous.getBlockDifficulty());
    }

    /**
     * Pushes a new pair onto the stack which holds a repo whose getBlockStore method returns a
     * block store whose getBlockHashByNumber method returns hash when called using blockNum.
     */
    private void pushNewBlockHash(long blockNum, byte[] hash) {
        ExecutionContext context = mockContext();
        IRepositoryCache repo = mockRepo();
        IBlockStoreBase blockstore = mock(IBlockStoreBase.class);
        when(blockstore.getBlockHashByNumber(blockNum)).thenReturn(hash);
        when(repo.getBlockStore()).thenReturn(blockstore);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
    }

    /**
     * Pushes a new pair onto the stack which holds a repo whose getCode method returns code when
     * called using address.
     */
    private void pushNewCode(Address address, byte[] code) {
        ExecutionContext context = mockContext();
        IRepositoryCache repo = mockRepo();
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
        IRepositoryCache repo = new DummyRepository();
        Address address = getNewAddressInRepo(repo, balance, BigInteger.ZERO);
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        return address;
    }

    private Address[] unpackAddresses(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        Address[] addresses = new Address[len];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).toBytes();
            addresses[i] = new Address(Arrays.copyOfRange(pack, 0, Address.ADDRESS_LEN));
        }
        return addresses;
    }

    private byte[][] unpackKeys(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        byte[][] keys = new byte[len][];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).toBytes();
            keys[i] = Arrays.copyOfRange(pack, Address.ADDRESS_LEN, Address.ADDRESS_LEN + DataWord.BYTES);
        }
        return keys;
    }

    private byte[][] unpackValues(List<ByteArrayWrapper> packs) {
        int len = packs.size();
        byte[][] values = new byte[len][];
        for (int i = 0; i < len; i++) {
            byte[] pack = packs.get(i).toBytes();
            values[i] = Arrays.copyOfRange(pack,pack.length - DataWord.BYTES, pack.length);
        }
        return values;
    }

    private byte[] packIntoBytes(Address address, byte[] key, byte[] value) {
        byte[] pack = new byte[Address.ADDRESS_LEN + (DataWord.BYTES * 2)];
        System.arraycopy(address.toBytes(), 0, pack, 0, Address.ADDRESS_LEN);
        System.arraycopy(key, 0, pack, Address.ADDRESS_LEN, DataWord.BYTES);
        System.arraycopy(value, 0, pack, Address.ADDRESS_LEN + DataWord.BYTES, DataWord.BYTES);
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
    private List<ByteArrayWrapper> pushNewStorageEntries(IRepositoryCache repo, int num,
        boolean pushToRepo) {

        Address[] addresses = new Address[num];
        byte[][] keys = new byte[num][];
        byte[][] values = new byte[num][];
        for (int i = 0; i < num; i++) {
            keys[num - 1 - i] = RandomUtils.nextBytes(DataWord.BYTES);
            values[num - 1 - i] = RandomUtils.nextBytes(DataWord.BYTES);
            addresses[num - 1 - i] = pushNewStorageEntry(
                repo, keys[num - 1 - i], values[num - 1 - i], pushToRepo);
        }
        return packup(addresses, keys, values);
    }

    /**
     * Pushes a new entry into repo if pushToRepo is true, otherwise pushes it onto Callback stack,
     * with key and value as the key-value pair for some random address, which is then returned.
     */
    private Address pushNewStorageEntry(IRepositoryCache repo, byte[] key, byte[] value,
        boolean pushToRepo) {

        Address address = getNewAddress();
        if (pushToRepo) {
            repo.addStorageRow(address, new DataWord(key), new DataWord(value));
        } else {
            Callback.putStorage(address.toBytes(), key, value);
        }
        return address;
    }

    /**
     * Pushes repo onto the top of the Callback stack by adding a new mocked Pair whose right entry
     * is repo.
     */
    private void pushNewRepo(IRepositoryCache repo) {
        Pair pair = mockEmptyPair();
        when(pair.getRight()).thenReturn(repo);
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

}
