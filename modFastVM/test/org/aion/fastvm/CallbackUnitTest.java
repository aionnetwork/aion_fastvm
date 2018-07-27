package org.aion.fastvm;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.format;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import org.aion.base.type.Address;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.aion.base.db.IRepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.ExecutionContext;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for Callback class.
 */
public class CallbackUnitTest {

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
        Pair pair = mockPair();
        when(pair.getLeft()).thenReturn(context);
        Callback.push(pair);
        ExecutionContext stackContext = Callback.context();
        compareContexts(context, stackContext);
    }

    @Test
    public void testPeekAtRepoInStackSizeOne() {
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo = mockRepo();
        Pair pair = mockPair();
        when(pair.getRight()).thenReturn(repo);
        Callback.push(pair);
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> stackRepo = Callback.repo();
        compareRepos(repo, stackRepo);
    }

    @Test
    public void testPushesAndPopsUntilEmpty() {
        int reps = RandomUtils.nextInt(10, 50);
        for (int i = 0; i < reps; i++) {
            Callback.push(mockPair());
        }
        for (int i = 0; i < reps; i++) {
            Callback.pop();
        }
        try { Callback.pop(); } catch (NoSuchElementException e) { return; }    // hit bottom as wanted.
        fail();
    }

    @Test
    public void testEachDepthOfPoppingLargeStack() {
        //TODO
    }

    // <---------------------------------------HELPERS BELOW--------------------------------------->

    private Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>> mockPair() {
        return mock(Pair.class);
    }

    private ExecutionContext mockContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getBlockNumber()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(context.getCaller()).thenReturn(getNewAddress());
        when(context.getCallData()).thenReturn(RandomUtils.nextBytes(RandomUtils.nextInt(0, 50)));
        when(context.getCallValue()).thenReturn(new DataWord(RandomUtils.nextBytes(DataWord.BYTES)));
        when(context.getNrgLimit()).thenReturn(RandomUtils.nextLong(0, 10_000));
        return context;
    }

    private IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> mockRepo() {
        IRepositoryCache cache = mock(IRepositoryCache.class);
        when(cache.toString()).thenReturn("mocked repo.");
        when(cache.getCode(Mockito.any(Address.class))).thenReturn(RandomUtils.nextBytes(30));
        return cache;
    }

    private Address getNewAddress() {
        return new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
    }

    private void compareContexts(ExecutionContext context, ExecutionContext other) {
        assertEquals(context.getBlockNumber(), other.getBlockNumber());
        assertEquals(context.getCaller(), other.getCaller());
        assertArrayEquals(context.getCallData(), other.getCallData());
        assertEquals(context.getCallValue(), other.getCallValue());
        assertEquals(context.getNrgLimit(), other.getNrgLimit());
    }

    private void compareRepos(IRepositoryCache cache, IRepositoryCache other) {
        Address addr = getNewAddress();
        assertEquals(cache.toString(), other.toString());
        assertEquals(cache.getCode(addr), other.getCode(addr));
    }

}
