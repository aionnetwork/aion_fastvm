package org.aion.fastvm;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.format;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.aion.base.type.Address;
import org.aion.mcf.vm.types.Log;
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
import org.junit.Ignore;
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
        compareContexts(context, stackContext);
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
            compareContexts((ExecutionContext) pair.getLeft(), Callback.context());
            compareRepos((IRepositoryCache) pair.getRight(), Callback.repo());
            Callback.pop();
        }
        try { Callback.pop(); } catch (NoSuchElementException e) { return; }    // hit bottom as wanted.
        fail();
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

            ExecutionContext ctx = Callback.context();
            Callback.selfDestruct(owner.toBytes(), beneficiary.toBytes());
            checkSelfDestruct(owner, ownerBalance, ownerNonce, beneficiary, benBalance);
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

    private IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> mockRepo() {
        IRepositoryCache cache = mock(IRepositoryCache.class);
        when(cache.toString()).thenReturn("mocked repo.");
        when(cache.getCode(Mockito.any(Address.class))).thenReturn(RandomUtils.nextBytes(30));
        return cache;
    }

    private void compareContexts(ExecutionContext context, ExecutionContext other) {
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

}
