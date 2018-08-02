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
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.DummyRepository;
import org.aion.vm.ExecutionHelper;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;
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
        when(ctx.helper()).thenReturn(helper);
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
        when(ctx.helper()).thenReturn(helper);
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
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = newExecutionContext(getNewAddress(), getNewAddress(),
            new DataWord(RandomUtils.nextBytes(DataWord.BYTES)),false,
            false, false, ExecutionContext.DELEGATECALL, nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(new DummyRepository());
        Callback.push(pair);
        ExecutionContext ctx = newExecutionContext(getNewAddress(), getNewAddress(),
            new DataWord(RandomUtils.nextBytes(DataWord.BYTES)),false,
            false, false, ExecutionContext.DELEGATECALL, nrgLimit);
        byte[] message = generateContextMessage(ctx.address(), ctx.caller(), ctx.nrgLimit(),
            ctx.callValue(), ctx.callData(), ctx.depth(), ctx.kind(), ctx.flags());

        ExecutionContext expectedContext = makeExpectedContext(context, ctx);
        compareContexts(expectedContext, Callback.parseMessage(message));
    }

    @Test
    public void testParseMessageAtMultipleStackDepths() {
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        int depths = RandomUtils.nextInt(3, 10);
        for (int i = 0; i < depths; i++) {
            ExecutionContext context = newExecutionContext(getNewAddress(), getNewAddress(),
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)),false,
                false, false, ExecutionContext.DELEGATECALL, nrgLimit);
            Pair pair = mockEmptyPair();
            when(pair.getLeft()).thenReturn(context);
            when(pair.getRight()).thenReturn(new DummyRepository());
            Callback.push(pair);
        }
        for (int i = 0; i < depths; i++) {
            // test every other ctx with empty data
            ExecutionContext ctx = newExecutionContext(getNewAddress(), getNewAddress(),
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)),i % 2 == 0,
                false, false, ExecutionContext.DELEGATECALL, nrgLimit);
            byte[] message = generateContextMessage(ctx.address(), ctx.caller(), ctx.nrgLimit(),
                ctx.callValue(), ctx.callData(), ctx.depth(), ctx.kind(), ctx.flags());
            ExecutionContext expectedContext = makeExpectedContext(Callback.context(), ctx);
            compareContexts(expectedContext, Callback.parseMessage(message));
            Callback.pop();
        }
    }

    @Test
    public void testParseMessageUsingZeroLengthData() {
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = newExecutionContext(getNewAddress(), getNewAddress(),
            new DataWord(RandomUtils.nextBytes(DataWord.BYTES)),false,
            false, false, ExecutionContext.DELEGATECALL, nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(new DummyRepository());
        Callback.push(pair);
        ExecutionContext ctx = newExecutionContext(getNewAddress(), getNewAddress(),
            new DataWord(RandomUtils.nextBytes(DataWord.BYTES)),true,
            false, false, ExecutionContext.DELEGATECALL, nrgLimit);
        byte[] message = generateContextMessage(ctx.address(), ctx.caller(), ctx.nrgLimit(),
            ctx.callValue(), ctx.callData(), ctx.depth(), ctx.kind(), ctx.flags());

        ExecutionContext expectedContext = makeExpectedContext(context, ctx);
        compareContexts(expectedContext, Callback.parseMessage(message));
    }

    @Test
    public void testCallStackDepthTooLarge() {
        IRepositoryCache repo = new DummyRepository();
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = newExecutionContext(getNewAddress(), getNewAddress(),
            new DataWord(RandomUtils.nextBytes(DataWord.BYTES)),false,
            false, false, ExecutionContext.DELEGATECALL, nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        byte[] message = generateContextMessage(context.address(), context.caller(),
            context.nrgLimit(), context.callValue(), context.callData(), context.depth(),
            Constants.MAX_CALL_DEPTH, 0);
        ExecutionResult result = ExecutionResult.parse(Callback.call(message));
        assertEquals(ResultCode.FAILURE, result.getResultCode());
        assertEquals(0, result.getNrgLeft());
    }

    @Test
    public void testCallCallersBalanceLessThanCallValue() {
        BigInteger balance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        IRepositoryCache repo = new DummyRepository();
        Address caller = getNewAddressInRepo(repo, balance, BigInteger.ZERO);
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = newExecutionContext(caller, getNewAddress(),
            new DataWord(balance.add(BigInteger.ONE)),false,
            false, false, ExecutionContext.DELEGATECALL, nrgLimit);
        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        byte[] message = generateContextMessage(context.address(), context.caller(),
            context.nrgLimit(), context.callValue(), context.callData(), context.depth(),
            0, 0);
        ExecutionResult result = ExecutionResult.parse(Callback.call(message));
        assertEquals(ResultCode.FAILURE, result.getResultCode());
        assertEquals(0, result.getNrgLeft());
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledIsSuccessJuneSeptForksDisabled() {
        performCallIsPrecompiledIsSuccessJuneSeptForksDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledIsSuccessJuneSeptForksDisabled() {
        performCallIsPrecompiledIsSuccessJuneSeptForksDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledIsSuccessJuneSeptForksDisabled() {
        performCallIsPrecompiledIsSuccessJuneSeptForksDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledNotSuccessJuneSeptForksDisabled() {
        performCallIsPrecompiledNotSuccessJuneSeptForksDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledNotSuccessJuneSeptForksDisabled() {
        performCallIsPrecompiledNotSuccessJuneSeptForksDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledNotSuccessJuneSeptForksDisabled() {
        performCallIsPrecompiledNotSuccessJuneSeptForksDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledIsSuccessJuneForkEnabledSeptDisabled() {
        performCallIsPrecompiledIsSuccessJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledIsSuccessJuneForkEnabledSeptDisabled() {
        performCallIsPrecompiledIsSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledIsSuccessJuneForkEnabledSeptDisabled() {
        performCallIsPrecompiledIsSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledNotSuccessJuneForkEnabledSeptDisabled() {
        performCallIsPrecompiledNotSuccessJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledNotSuccessJuneForkEnabledSeptDisabled() {
        performCallIsPrecompiledNotSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledNotSuccessJuneForkEnabledSeptDisabled() {
        performCallIsPrecompiledNotSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledIsSuccessJuneSeptForksEnabled() {
        performCallIsPrecompiledIsSuccessJuneSeptForksEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledIsSuccessJuneSeptForksEnabled() {
        performCallIsPrecompiledIsSuccessJuneSeptForksEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledIsSuccessJuneSeptForksEnabled() {
        performCallIsPrecompiledIsSuccessJuneSeptForksEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsPrecompiledNotSuccessJuneSeptForksEnabled() {
        performCallIsPrecompiledNotSuccessJuneSeptForksEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsPrecompiledNotSuccessJuneSeptForksEnabled() {
        performCallIsPrecompiledNotSuccessJuneSeptForksEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsPrecompiledNotSuccessJuneSeptForksEnabled() {
        performCallIsPrecompiledNotSuccessJuneSeptForksEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoRecipientJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneSeptForksDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoRecipientJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneSeptForksDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoRecipientJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneSeptForksDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoRecipientJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoRecipientJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneForkEnabledSeptDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoRecipientJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneForkEnabledSeptDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoRecipientJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneSeptForksEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoRecipientJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneSeptForksEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoRecipientJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractNoRecipientJuneSeptForksEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoCodeJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractNoCodeJuneSeptForksDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoCodeJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractNoCodeJuneSeptForksDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoCodeJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractNoCodeJuneSeptForksDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoCodeJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractNoCodeJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoCodeJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractNoCodeJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoCodeJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractNoCodeJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractNoCodeJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractNoCodeJuneSeptForksEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractNoCodeJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractNoCodeJuneSeptForksEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractNoCodeJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractNoCodeJuneSeptForksEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractIsCodeIsSuccessJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeIsSuccessJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeIsSuccessJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractIsCodeNotSuccessJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneForkEnabledSeptDisabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeNotSuccessJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeNotSuccessJuneForkEnabledSeptDisabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneForkEnabledSeptDisabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksEnabled(ExecutionContext.DELEGATECALL);
    }

    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksEnabled(ExecutionContext.CALLCODE);
    }

    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallDelegateCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksEnabled(ExecutionContext.DELEGATECALL);
    }
    @Test
    public void testPerformCallCallcodeIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksEnabled(ExecutionContext.CALLCODE);
    }
    @Test
    public void testPerformCallCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksEnabled() {
        performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksEnabled(ExecutionContext.CALL);
    }

    @Test
    public void testPerformCallCreateCallContractExistsJuneSeptForksDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, false, false,
            ExecutionContext.CREATE, new byte[0], true, false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            true, false, null);
        checkContextHelper(false);
        checkPerformCallState(context, false, callerBalance, true, false,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractExistsJuneForkEnabledSeptForkDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, false,
            ExecutionContext.CREATE, new byte[0], true, false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            true, false, null);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, true, false,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractExistsJuneSeptForksEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, true,
            ExecutionContext.CREATE, new byte[0], true, false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            true, false, null);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, true, false,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataIsEmptyNrgLessThanDepositJuneSeptForksDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, false, false,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        // Seems misleading -- we are saying it was SUCCESS when obviously it was FAILURE, this is
        // because in 'post execution' it was SUCCESS but its low nrgLimit made it fail afterwards.
        // Nonetheless, the changes we expect of SUCCESS here did occur.
        checkPerformCallState(context, false, callerBalance, false, true,
            true);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataIsEmptyNrgLessThanDepositJuneForkEnabledSeptDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, false,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        // Seems misleading -- we are saying it was SUCCESS when obviously it was FAILURE, this is
        // because in 'post execution' it was SUCCESS but its low nrgLimit made it fail afterwards.
        // Nonetheless, the changes we expect of SUCCESS here did occur.
        checkPerformCallState(context, true, callerBalance, false, true,
            true);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataIsEmptyNrgLessThanDepositJuneSeptForksEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, true,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.FAILURE, 0);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        // Seems misleading -- we are saying it was SUCCESS when obviously it was FAILURE, this is
        // because in 'post execution' it was SUCCESS but its low nrgLimit made it fail afterwards.
        // Nonetheless, the changes we expect of SUCCESS here did occur.
        checkPerformCallState(context, true, callerBalance, false, true,
            true);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataIsEmptyNrgMoreThanDepositJuneSeptForksDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, false, false,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, false, callerBalance, false, true,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataIsEmptyNrgMoreThanDepositJuneForkEnabledSeptDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, false,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, false, true,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataIsEmptyNrgMoreThanDepositJuneSeptForksEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, true,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(null);   // we bypass vm
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, false, true,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNrgLessThanDepositJuneSeptForksDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, false, false,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        mockedResult.setCodeAndNrgLeft(ResultCode.FAILURE.toInt(), 0);  // nrgLimit causes failure post-execution.
        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, false, callerBalance, false, true,
            true);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNrgLessThanDepositJuneForkEnabledSeptDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, false,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        mockedResult.setCodeAndNrgLeft(ResultCode.FAILURE.toInt(), 0);  // nrgLimit causes failure post-execution.
        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, false, true,
            true);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNrgLessThanDepositJuneSeptForksEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT - 1;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, true,
            ExecutionContext.CREATE, null, false, true, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        mockedResult.setCodeAndNrgLeft(ResultCode.FAILURE.toInt(), 0);  // nrgLimit causes failure post-execution.
        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, new byte[0]);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, false, true,
            true);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyIsSuccessJuneSeptForksDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, false, false,
            ExecutionContext.CREATE, null, false, false, nrgLimit);

        byte[] code = RandomUtils.nextBytes(50);
        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT, code);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, code);
        checkContextHelper(false);
        checkPerformCallState(context, false, callerBalance, false, true,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyIsSuccessJuneForkEnabledSeptForkDisabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, false,
            ExecutionContext.CREATE, null, false, false, nrgLimit);

        byte[] code = RandomUtils.nextBytes(50);
        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT, code);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, code);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, false, true,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyIsSuccessJuneSeptForksEnabled() {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = Constants.NRG_CODE_DEPOSIT;
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, true,
            ExecutionContext.CREATE, null, false, false, nrgLimit);

        byte[] code = RandomUtils.nextBytes(50);
        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, Constants.NRG_CODE_DEPOSIT, code);
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
            false, true, code);
        checkContextHelper(false);
        checkPerformCallState(context, true, callerBalance, false, true,
            false);
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNotSuccessJuneSeptForksDisabled() {
        for (ResultCode resCode : ResultCode.values()) {
            if (!resCode.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = Constants.NRG_CODE_DEPOSIT;
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, false, false,
                    ExecutionContext.CREATE, null, false, false, nrgLimit);

                byte[] code = RandomUtils.nextBytes(50);
                ExecutionResult mockedResult = new ExecutionResult(resCode, Constants.NRG_CODE_DEPOSIT, code);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
                    false, false, code);
                checkContextHelper(false);
                checkPerformCallState(context, false, callerBalance, false, false,
                    false);
            }
        }
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNotSuccessJuneForkEnabledSeptDisabled() {
        for (ResultCode resCode : ResultCode.values()) {
            if (!resCode.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = Constants.NRG_CODE_DEPOSIT;
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, true, false,
                    ExecutionContext.CREATE, null, false, false, nrgLimit);

                byte[] code = RandomUtils.nextBytes(50);
                ExecutionResult mockedResult = new ExecutionResult(resCode, Constants.NRG_CODE_DEPOSIT, code);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
                    false, false, code);
                checkContextHelper(false);
                checkPerformCallState(context, true, callerBalance, false, false,
                    false);
            }
        }
    }

    @Test
    public void testPerformCallCreateCallContractIsNewDataNotEmptyNotSuccessJuneSeptForksEnabled() {
        for (ResultCode resCode : ResultCode.values()) {
            if (!resCode.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = Constants.NRG_CODE_DEPOSIT;
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, true, true,
                    ExecutionContext.CREATE, null, false, false, nrgLimit);

                byte[] code = RandomUtils.nextBytes(50);
                ExecutionResult mockedResult = new ExecutionResult(resCode, Constants.NRG_CODE_DEPOSIT, code);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, ExecutionContext.CREATE,
                    false, false, code);
                checkContextHelper(false);
                checkPerformCallState(context, true, callerBalance, false,
                    false, false);
            }
        }
    }

    // <----------METHODS BELOW ARE TESTS THAT ARE SHARED BY MULTIPLE TESTS AND SO REUSED---------->

    private void performCallIsPrecompiledIsSuccessJuneSeptForksDisabled(int kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, false, false,
            kind, new byte[0], false, false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(mockedResult);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
            false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(context, callerBalance, recipientBalance, false,
            false, false);
    }

    private void performCallIsPrecompiledNotSuccessJuneSeptForksDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, false, false,
                    kind, new byte[0], false, false, nrgLimit);

                ExecutionResult mockedResult = new ExecutionResult(code, 0);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(mockedResult);

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
                    false, false, null);
                checkHelperForRejections(Callback.context().helper());
                checkContextHelper(true);
                checkPerformCallResults(context, callerBalance, recipientBalance, false,
                    false, false);
            }
        }
    }

    private void performCallIsPrecompiledIsSuccessJuneForkEnabledSeptDisabled(int kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, false,
            kind, new byte[0], false, false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(mockedResult);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
            false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(context, callerBalance, recipientBalance, true,
            false, false);
    }

    private void performCallIsPrecompiledNotSuccessJuneForkEnabledSeptDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, true, false,
                    kind, new byte[0], false, false, nrgLimit);

                ExecutionResult mockedResult = new ExecutionResult(code, 0);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(mockedResult);

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
                    false, false, null);
                checkHelperForRejections(Callback.context().helper());
                checkContextHelper(true);
                checkPerformCallResults(context, callerBalance, recipientBalance, true,
                    false, false);
            }
        }
    }

    private void performCallIsPrecompiledIsSuccessJuneSeptForksEnabled(int kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, true,
            kind, new byte[0], false, false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(mockedResult);

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
            false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(context, callerBalance, recipientBalance, true,
            false, false);
    }

    private void performCallIsPrecompiledNotSuccessJuneSeptForksEnabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, true, true,
                    kind, new byte[0], false, false, nrgLimit);

                ExecutionResult mockedResult = new ExecutionResult(code, 0);
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(mockedResult);

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
                    false, false, null);
                checkHelperForRejections(Callback.context().helper());
                checkContextHelper(true);
                checkPerformCallResults(context, callerBalance, recipientBalance, true,
                    false, false);
            }
        }
    }

    private void performCallIsNotPrecompiledContractNoRecipientJuneSeptForksDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context = setupTestForPerformCall(
                callerBalance, recipientBalance, false, false,
                kind, null, false, false, nrgLimit);

            ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);
            ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

            // There is no recipient hence vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setCode(ResultCode.SUCCESS.toInt());
            runPerformCallAndCheck(context, vm, factory, mockedResult, true, kind,
                false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(context, callerBalance, recipientBalance, false,
                true, false);
        }
    }

    private void performCallIsNotPrecompiledContractNoRecipientJuneForkEnabledSeptDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context = setupTestForPerformCall(
                callerBalance, recipientBalance, true, false,
                kind, null, false, false, nrgLimit);

            ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);
            ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

            // There is no recipient hence vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setCode(ResultCode.SUCCESS.toInt());
            runPerformCallAndCheck(context, vm, factory, mockedResult, true, kind,
                false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(context, callerBalance, recipientBalance, true,
                true, false);
        }
    }

    private void performCallIsNotPrecompiledContractNoRecipientJuneSeptForksEnabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context = setupTestForPerformCall(
                callerBalance, recipientBalance, true, true,
                kind, null, false, false, nrgLimit);

            ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);
            ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

            // There is no recipient hence vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setCode(ResultCode.SUCCESS.toInt());
            runPerformCallAndCheck(context, vm, factory, mockedResult, true, kind,
                false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(context, callerBalance, recipientBalance, true,
                true, false);
        }
    }

    private void performCallIsNotPrecompiledContractNoCodeJuneSeptForksDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context = setupTestForPerformCall(
                callerBalance, recipientBalance, false, false,
                kind, new byte[0], false, false, nrgLimit);

            ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);
            ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

            // The recipient's code is empty, hence the vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setCode(ResultCode.SUCCESS.toInt());
            runPerformCallAndCheck(context, vm, factory, mockedResult, true, kind,
                false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(context, callerBalance, recipientBalance, false,
                false, false);
        }
    }

    private void performCallIsNotPrecompiledContractNoCodeJuneForkEnabledSeptDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context = setupTestForPerformCall(
                callerBalance, recipientBalance, true, false,
                kind, new byte[0], false, false, nrgLimit);

            ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);
            ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

            // The recipient's code is empty, hence the vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setCode(ResultCode.SUCCESS.toInt());
            runPerformCallAndCheck(context, vm, factory, mockedResult, true, kind,
                false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(context, callerBalance, recipientBalance, true,
                false, false);
        }
    }

    private void performCallIsNotPrecompiledContractNoCodeJuneSeptForksEnabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
            BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
            long nrgLimit = RandomUtils.nextLong(0, 10_000);
            ExecutionContext context = setupTestForPerformCall(
                callerBalance, recipientBalance, true, true,
                kind, new byte[0], false, false, nrgLimit);

            ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
            FastVM vm = mockFastVM(mockedResult);
            ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

            // The recipient's code is empty, hence the vm gets bad code.
            // Since VM didn't execute code it always returns success as default.
            mockedResult.setCode(ResultCode.SUCCESS.toInt());
            runPerformCallAndCheck(context, vm, factory, mockedResult, true, kind,
                false, false, null);
            checkContextHelper(true);
            checkPerformCallResults(context, callerBalance, recipientBalance, true,
                false, false);
        }
    }

    private void performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksDisabled(int kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, false, false,
            kind, RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)), false, false,
            nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
            false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(context, callerBalance, recipientBalance, false,
            false, false);
    }

    private void performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, false, false,
                    kind, RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)), false,
                    false, nrgLimit);

                ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
                    false, false, null);
                checkHelperForRejections(Callback.context().helper());
                checkContextHelper(true);
                checkPerformCallResults(context, callerBalance, recipientBalance,
                    false, false, false);
            }
        }
    }

    private void performCallIsNotPrecompiledContractIsCodeIsSuccessJuneForkEnabledSeptDisabled(int kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, false,
            kind, RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)), false,
            false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
            false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(context, callerBalance, recipientBalance, true,
            false, false);
    }

    private void performCallIsNotPrecompiledContractIsCodeNotSuccessJuneForkEnabledSeptDisabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, true, false,
                    kind, RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)), false,
                    false, nrgLimit);

                ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
                    false, false, null);
                checkHelperForRejections(Callback.context().helper());
                checkContextHelper(true);
                checkPerformCallResults(context, callerBalance, recipientBalance,
                    true, false, false);
            }
        }
    }

    private void performCallIsNotPrecompiledContractIsCodeIsSuccessJuneSeptForksEnabled(int kind) {
        BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
        BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        long nrgLimit = RandomUtils.nextLong(0, 10_000);
        ExecutionContext context = setupTestForPerformCall(
            callerBalance, recipientBalance, true, true,
            kind, RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)), false,
            false, nrgLimit);

        ExecutionResult mockedResult = new ExecutionResult(ResultCode.SUCCESS, RandomUtils.nextLong(0, 10_000));
        FastVM vm = mockFastVM(mockedResult);
        ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

        runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
            false, false, null);
        checkContextHelper(true);
        checkPerformCallResults(context, callerBalance, recipientBalance, true,
            false, false);
    }

    private void performCallIsNotPrecompiledContractIsCodeNotSuccessJuneSeptForksEnabled(int kind) {
        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS)) {
                BigInteger callerBalance = BigInteger.valueOf(RandomUtils.nextLong(10, 10_000));
                BigInteger recipientBalance = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
                long nrgLimit = RandomUtils.nextLong(0, 10_000);
                ExecutionContext context = setupTestForPerformCall(
                    callerBalance, recipientBalance, true, true,
                    kind, RandomUtils.nextBytes(RandomUtils.nextInt(5, 30)), false,
                    false, nrgLimit);

                ExecutionResult mockedResult = new ExecutionResult(code, RandomUtils.nextLong(0, 10_000));
                FastVM vm = mockFastVM(mockedResult);
                ContractFactory factory = mockFactory(null);    // signal not a precompiled contract.

                runPerformCallAndCheck(context, vm, factory, mockedResult, false, kind,
                    false, false, null);
                checkHelperForRejections(Callback.context().helper());
                checkContextHelper(true);
                checkPerformCallResults(context, callerBalance, recipientBalance,
                    true, false, false);
            }
        }
    }

    // <---------------------------------------HELPERS BELOW--------------------------------------->

    private Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>> mockEmptyPair() {
        return mock(Pair.class);
    }

    /**
     * Returns a mocked pair whose left entry is a mocked context that returns a new helper when
     * helper is called and whose right entry is a new DummyRepository.
     */
    private Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>> mockPair() {
        ExecutionContext context = mockContext();
        ExecutionHelper helper = new ExecutionHelper();
        when(context.helper()).thenReturn(helper);
        Pair pair = mock(Pair.class);
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(new DummyRepository());
        return pair;
    }

    private ExecutionContext mockContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.blockNumber()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.caller()).thenReturn(getNewAddress());
        when(context.callData()).thenReturn(RandomUtils.nextBytes(RandomUtils.nextInt(0, 50)));
        when(context.callValue()).thenReturn(new DataWord(RandomUtils.nextBytes(DataWord.BYTES)));
        when(context.nrgLimit()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.transactionHash()).thenReturn(RandomUtils.nextBytes(32));
        when(context.depth()).thenReturn(RandomUtils.nextInt(0, 1000));
        return context;
    }

    private ExecutionContext newExecutionContext(Address caller, Address recipient, DataWord callValue,
        boolean isEmptyData, boolean juneForkEnabled, boolean septForkEnabled, int kind, long nrgLimit) {

        byte[] txHash = RandomUtils.nextBytes(32);
        Address origin = getNewAddress();
        DataWord nrgPrice = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
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
        if (!juneForkEnabled && !septForkEnabled) {
            blockNumber = RandomUtils.nextLong(0, 167_690);
        } else if (juneForkEnabled && !septForkEnabled) {
            blockNumber = RandomUtils.nextLong(167_692, 999_999);
        } else {
            blockNumber = 1_000_000;
        }
        long blockTimestamp = RandomUtils.nextLong(100, 100_000);
        long blockNrgLimit = RandomUtils.nextLong(100, 100_000);
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
        assertEquals(context.address(), other.address());
        assertEquals(context.origin(), other.origin());
        assertEquals(context.caller(), other.caller());
        assertEquals(context.blockCoinbase(), other.blockCoinbase());
        assertEquals(context.nrgPrice(), other.nrgPrice());
        assertEquals(context.callValue(), other.callValue());
        assertEquals(context.blockDifficulty(), other.blockDifficulty());
        assertEquals(context.nrgLimit(), other.nrgLimit());
        assertEquals(context.blockNumber(), other.blockNumber());
        assertEquals(context.blockTimestamp(), other.blockTimestamp());
        assertEquals(context.blockNrgLimit(), other.blockNrgLimit());
        assertEquals(context.depth(), other.depth());
        assertEquals(context.kind(), other.kind());
        assertEquals(context.flags(), other.flags());
        assertArrayEquals(context.transactionHash(), other.transactionHash());
        assertArrayEquals(context.callData(), other.callData());
    }

    private IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> mockRepo() {
        IRepositoryCache cache = mock(IRepositoryCache.class);
        when(cache.toString()).thenReturn("mocked repo.");
        when(cache.getCode(Mockito.any(Address.class))).thenReturn(RandomUtils.nextBytes(30));
        return cache;
    }

    private void compareMockContexts(ExecutionContext context, ExecutionContext other) {
        assertEquals(context.blockNumber(), other.blockNumber());
        assertEquals(context.caller(), other.caller());
        assertArrayEquals(context.callData(), other.callData());
        assertEquals(context.callValue(), other.callValue());
        assertEquals(context.nrgLimit(), other.nrgLimit());
        assertArrayEquals(context.transactionHash(), other.transactionHash());
        assertEquals(context.depth(), other.depth());
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
        ExecutionHelper helper = ctx.helper();
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
        assertEquals(ctx.depth(), tx.getDeep());
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
        ExecutionHelper helper = Callback.context().helper();
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
        return new ExecutionContext(previous.transactionHash(), context.address(),
            previous.origin(), context.caller(), previous.nrgPrice(), context.nrgLimit(),
            context.callValue(), context.callData(), context.depth(), context.kind(),
            context.flags(), previous.blockCoinbase(), previous.blockNumber(),
            previous.blockTimestamp(), previous.blockNrgLimit(), previous.blockDifficulty());
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

    /**
     * Returns a mocked ContractFactory whose returned contract's execute method will return result.
     * Unless result is null! In this case the mocked factory returns null, which tells Callback
     * that the transaction is not a precompiled contract.
     */
    private ContractFactory mockFactory(ExecutionResult result) {
        IPrecompiledContract contract = mock(IPrecompiledContract.class);
        when(contract.execute(Mockito.any(byte[].class), Mockito.anyLong())).thenReturn(result);
        ContractFactory factory = mock(ContractFactory.class);
        if (result == null) {
            when(factory.fetchPrecompiledContract(Mockito.any(ExecutionContext.class),
                Mockito.any(IRepositoryCache.class))).
                thenReturn(null);
            return factory;
        } else {
            when(factory.fetchPrecompiledContract(Mockito.any(ExecutionContext.class),
                Mockito.any(IRepositoryCache.class))).
                thenReturn(contract);

        }
        return factory;
    }

    /**
     * Returns a mocked FastVM whose run method returns result.
     */
    private FastVM mockFastVM(ExecutionResult result) {
        FastVM vm = mock(FastVM.class);
        when(vm.run(Mockito.any(byte[].class), Mockito.any(ExecutionContext.class),
            Mockito.any(IRepositoryCache.class))).
            thenReturn(result);
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
     */
    private void checkPerformCallResults(ExecutionContext context, BigInteger callerBalance,
        BigInteger recipientBalance, boolean juneForkEnabled, boolean wasNoRecipient,
        boolean isCreateContract) {

        ExecutionContext ctx = Callback.context();
        checkInternalTransaction(context, ctx.helper().getInternalTransactions().get(0), juneForkEnabled,
            isCreateContract);
        checkPerformCallBalances(context.caller(), callerBalance, context.address(), recipientBalance,
            context.callValue().value(), wasNoRecipient);
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
    private void runPerformCallAndCheck(ExecutionContext context, FastVM mockVM, ContractFactory mockFac,
        ExecutionResult expectedResult, boolean vmGotBadCode, int kind, boolean contractExisted,
        boolean postExecuteWasSuccess, byte[] code) {

        byte[] message = generateContextMessage(context.address(), context.caller(),
            context.nrgLimit(), context.callValue(), context.callData(), context.depth(),
            kind, 0);
        ExecutionResult result = ExecutionResult.parse(Callback.performCall(message, mockVM, mockFac));
        assertEquals(expectedResult.getResultCode(), result.getResultCode());
        if (vmGotBadCode) {
            assertEquals(context.nrgLimit(), result.getNrgLeft());
        } else {
            assertEquals(expectedResult.getNrgLeft(), result.getNrgLeft());
        }
        if (kind == ExecutionContext.CREATE) {
            checkCodeAfterCreate(contractExisted, postExecuteWasSuccess, result, code);
        }
    }

    /**
     * Checks that code is saved under the contract address in the repo at the top of the stack and
     * that result's output is the address of that contract. This is for the CREATE opcode.
     */
    private void checkCodeAfterCreate(boolean contractAlreadyExists, boolean postExecuteWasSuccess,
        ExecutionResult result, byte[] code) {

        if (!contractAlreadyExists && postExecuteWasSuccess) {
            Address contract = new Address(result.getOutput());
            assertArrayEquals(code, Callback.repo().getCode(contract));
        }
    }

    /**
     * Sets up the Callback class, including its top-stack context and repo, for the performCall
     * testing. Two accounts, a caller and a recipient, are created and these accounts have balances
     * callerBalance and recipientBalance respectively.
     */
    private ExecutionContext setupTestForPerformCall(BigInteger callerBalance, BigInteger recipientBalance,
        boolean juneForkEnabled, boolean septForkEnabled, int kind, byte[] code, boolean contractExists,
        boolean dataIsEmpty, long nrgLimit) {

        BigInteger callerNonce = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        IRepositoryCache repo = new DummyRepository();
        Address caller = getNewAddressInRepo(repo, callerBalance, callerNonce);
        if (contractExists) {
            Address contract = new Address(HashUtil.calcNewAddr(caller.toBytes(), callerNonce.toByteArray()));
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
        ExecutionContext context = newExecutionContext(caller, recipient,
            new DataWord(callerBalance), dataIsEmpty, juneForkEnabled, septForkEnabled, kind, nrgLimit);

        Pair pair = mockEmptyPair();
        when(pair.getLeft()).thenReturn(context);
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        return context;
    }

    /**
     * Asserts that all of the internal transactions in helper have been rejected.
     */
    private void checkHelperForRejections(ExecutionHelper helper) {
        for (AionInternalTx tx : helper.getInternalTransactions()) {
            assertTrue(tx.isRejected());
        }
    }

    /**
     * Asserts that the account balances are in the expected state after a call to performCall.
     */
    private void checkPerformCallBalances(Address caller, BigInteger callerPrevBalance, Address recipient,
        BigInteger recipientPrevBalance, BigInteger callValue, boolean wasNoRecipient) {

        if (caller.equals(recipient)) {
            assertEquals(callerPrevBalance, Callback.repo().getBalance(caller));
        } else {
            assertEquals(callerPrevBalance.subtract(callValue), Callback.repo().getBalance(caller));
            // if there was no recipient then DummyRepository created that account when Callback
            // transferred balance to it, so its balance should be callValue.
            if (wasNoRecipient) {
                assertEquals(callValue, Callback.repo().getBalance(recipient));
            } else {
                assertEquals(recipientPrevBalance.add(callValue),
                    Callback.repo().getBalance(recipient));
            }
        }
    }

    /**
     * Asserts that the values of the internal transaction tx is in its expected state given that
     * context was the context used to set up the performCall test.
     */
    private void checkInternalTransaction(ExecutionContext context, AionInternalTx tx,
        boolean juneForkEnabled, boolean isCreateContract) {

        assertEquals(context.caller(), tx.getFrom());
        if (isCreateContract) {
            // Decrement nonce because the transaction incremented it after address was made.
            Address contract;
            if (isCreateContract && juneForkEnabled) {
                contract = new Address(HashUtil.calcNewAddr(context.caller().toBytes(),
                    Callback.repo().getNonce(context.caller()).subtract(BigInteger.TWO)
                        .toByteArray()));
            } else {
                contract = new Address(HashUtil.calcNewAddr(context.caller().toBytes(),
                    Callback.repo().getNonce(context.caller()).subtract(BigInteger.ONE)
                        .toByteArray()));
            }
            assertEquals(contract, tx.getTo());
        } else {
            assertEquals(context.address(), tx.getTo());
        }
        if (juneForkEnabled) {
            if (isCreateContract) {
                assertEquals(Callback.repo().getNonce(context.caller()).subtract(BigInteger.TWO),
                    tx.getNonceBI());
            } else {
                assertEquals(Callback.repo().getNonce(context.caller()).subtract(BigInteger.ONE),
                    tx.getNonceBI());
            }
        } else {
            if (isCreateContract) {
                assertEquals(Callback.repo().getNonce(context.caller()).subtract(BigInteger.ONE),
                    tx.getNonceBI());
            } else {
                assertEquals(Callback.repo().getNonce(context.caller()), tx.getNonceBI());
            }
        }
        assertEquals(context.callValue(), new DataWord(tx.getValue()));
        if (isCreateContract) {
            assertEquals("create", tx.getNote());
        } else {
            assertEquals("call", tx.getNote());
        }
        assertEquals(context.depth(), tx.getDeep());
        assertEquals(0, tx.getIndex());
        assertArrayEquals(context.callData(), tx.getData());
        assertArrayEquals(context.transactionHash(), tx.getParentHash());
    }

    /**
     * Checks the state of the context helper following a performCall call.
     *
     * @param performCallTriggeredDoCall If the op code chose the doCall path.
     */
    private void checkContextHelper(boolean performCallTriggeredDoCall) {
        if (performCallTriggeredDoCall) {
            assertEquals(1, Callback.context().helper().getInternalTransactions().size());
            assertEquals(0, Callback.context().helper().getLogs().size());
            assertEquals(0, Callback.context().helper().getDeleteAccounts().size());
        } else {
            assertEquals(2, Callback.context().helper().getInternalTransactions().size());
            assertEquals(0, Callback.context().helper().getLogs().size());
            assertEquals(0, Callback.context().helper().getDeleteAccounts().size());
        }
    }

    /**
     * Checks the state after the performCall method is called and makes assertions on the expected
     * states.
     *
     * @param context The context whose params were used in the test set up.
     * @param juneForkEnabled If june fork is enabled.
     * @param callerBalance The balance of the caller prior to the transaction.
     * @param contractExisted If the contract already existed prior to the call.
     * @param wasSuccess If the call result was SUCCESS in post execute.
     * @param nrgLessThanDeposit If energy limit was less than deposit amount.
     */
    private void checkPerformCallState(ExecutionContext context, boolean juneForkEnabled,
        BigInteger callerBalance, boolean contractExisted, boolean wasSuccess,
        boolean nrgLessThanDeposit) {

        checkInternalTransactionsAfterCreate(juneForkEnabled, wasSuccess);
        checkCreateBalances(context, callerBalance, contractExisted, juneForkEnabled,
            wasSuccess, nrgLessThanDeposit);
    }

    /**
     * Checks the state of the internal transactions in the context at the top of the stack's helper
     * after a call using the CREATE opcode.
     */
    private void checkInternalTransactionsAfterCreate(boolean juneForkEnabled, boolean wasSuccess) {
        ExecutionContext context = Callback.context();
        List<AionInternalTx> internalTxs = context.helper().getInternalTransactions();
        assertEquals(2, internalTxs.size());
        checkInternalTransaction(context, internalTxs.get(0), juneForkEnabled, true);
        checkSecondInteralTransaction(context, internalTxs.get(1));
        if (!wasSuccess) {
            assertTrue(internalTxs.get(1).isRejected());
        }
    }

    /**
     * Checks the second of the 2 internal transactions created during the CREATE opcode.
     */
    private void checkSecondInteralTransaction(ExecutionContext context, AionInternalTx tx) {
        Address caller = context.caller();
        assertEquals(context.caller(), tx.getFrom());
        assertEquals(Callback.repo().getNonce(caller), tx.getNonceBI());
        assertEquals(context.callValue(), new DataWord(tx.getValue()));
        assertEquals("create", tx.getNote());
        assertEquals(context.depth(), tx.getDeep());
        assertEquals(1, tx.getIndex());
        assertArrayEquals(context.transactionHash(), tx.getParentHash());
        assertArrayEquals(new DataWord(Callback.repo().getNonce(caller)).getData(), tx.getNonce());
        assertArrayEquals(context.callData(), tx.getData());
        assertNull(tx.getTo());
    }

    /**
     * Checks that the balances of the caller and contract are in their expected state after a
     * CREATE opcode.
     */
    private void checkCreateBalances(ExecutionContext context, BigInteger callerBalance,
        boolean contractExisted, boolean juneforkEnabled, boolean postExecuteWasSuccess,
        boolean nrgLessThanDeposit) {

        BigInteger value = context.callValue().value();
        Address caller = Callback.context().caller();
        Address contract;
        if (juneforkEnabled) {
            contract = new Address(HashUtil.calcNewAddr(caller.toBytes(),
                Callback.repo().getNonce(caller).subtract(BigInteger.TWO).toByteArray()));
        } else {
            contract = new Address(HashUtil.calcNewAddr(caller.toBytes(),
                Callback.repo().getNonce(caller).subtract(BigInteger.ONE).toByteArray()));
        }
        assertEquals(callerBalance.subtract(value), Callback.repo().getBalance(caller));
        if (contractExisted) {
            assertEquals(BigInteger.ZERO, Callback.repo().getBalance(contract));
        } else {
            if (postExecuteWasSuccess && !nrgLessThanDeposit) {
                assertEquals(value, Callback.repo().getBalance(contract));
            } else {
                assertEquals(BigInteger.ZERO, Callback.repo().getBalance(contract));
            }
        }
    }

}
