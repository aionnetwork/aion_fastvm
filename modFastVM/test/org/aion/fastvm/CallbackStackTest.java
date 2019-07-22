package org.aion.fastvm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests out some stack-related operations on the {@link Callback} class.
 */
public class CallbackStackTest {

    @After
    public void tearDown() {
        clearCallbackStack();
    }

    @Test(expected = NoSuchElementException.class)
    public void testPopOnEmptyStack() {
        Callback.pop();
    }

    @Test(expected = NullPointerException.class)
    public void testGetContextFromEmptyStack() {
        Callback.context();
    }

    @Test(expected = NullPointerException.class)
    public void testGetStateFromEmptyStack() {
        Callback.externalState();
    }

    @Test
    public void testPushToStack() {
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState();
        Callback.push(Pair.of(context, state));

        Assert.assertSame(context, Callback.context());
        Assert.assertSame(state, Callback.externalState());
    }

    @Test
    public void testPushingAndPoppingStack() {
        List<Pair<ExecutionContext, IExternalStateForFvm>> pairs1 = newContextStatePairs(3);
        List<Pair<ExecutionContext, IExternalStateForFvm>> pairs2 = newContextStatePairs(3);
        List<Pair<ExecutionContext, IExternalStateForFvm>> pairs3 = newContextStatePairs(2);

        // First we push all of pairs1 and pairs2 onto the Callback stack.
        for (Pair<ExecutionContext, IExternalStateForFvm> pair1 : pairs1) {
            Callback.push(pair1);
        }
        for (Pair<ExecutionContext, IExternalStateForFvm> pair2 : pairs2) {
            Callback.push(pair2);
        }

        // Now we pop off the top 3 pairs (ie. all of pairs2) and assert we saw the correct instances.
        for (int i = 2; i >= 0; i--) {
            Assert.assertSame(pairs2.get(i).getLeft(), Callback.context());
            Assert.assertSame(pairs2.get(i).getRight(), Callback.externalState());
            Callback.pop();
        }

        // Now we push all of pairs3 to the stack.
        for (Pair<ExecutionContext, IExternalStateForFvm> pair3 : pairs3) {
            Callback.push(pair3);
        }

        // Finally, we pop the next 5 pairs, which should leave the stack empty. We expect to see pairs3 then pairs1.
        for (int i = 1; i >= 0; i--) {
            Assert.assertSame(pairs3.get(i).getLeft(), Callback.context());
            Assert.assertSame(pairs3.get(i).getRight(), Callback.externalState());
            Callback.pop();
        }
        for (int i = 2; i >= 0; i--) {
            Assert.assertSame(pairs1.get(i).getLeft(), Callback.context());
            Assert.assertSame(pairs1.get(i).getRight(), Callback.externalState());
            Callback.pop();
        }
        Assert.assertTrue(Callback.stackIsEmpty());
    }

    private static List<Pair<ExecutionContext, IExternalStateForFvm>> newContextStatePairs(int num) {
        List<Pair<ExecutionContext, IExternalStateForFvm>> pairs = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            pairs.add(Pair.of(newDummyContext(), newState()));
        }
        return pairs;
    }

    private static ExecutionContext newDummyContext() {
        return new ExecutionContext(null, null, randomAddress(), randomAddress(), randomAddress(), FvmDataWord.fromLong(1L), 1L, FvmDataWord.fromBigInteger(
            BigInteger.ZERO), new byte[0], 0, TransactionKind.CALL, 0, randomAddress(), 0L, 0L, 500_000L, FvmDataWord.fromLong(0L));
    }

    private static ExternalStateForTesting newState() {
        return new ExternalStateForTesting(RepositoryForTesting.newRepository(), new BlockchainForTesting(), randomAddress(), FvmDataWord.fromInt(0), false, true, false, 0L, 0L, 0L);
    }

    private static AionAddress randomAddress() {
        byte[] bytes = RandomUtils.nextBytes(AionAddress.LENGTH);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }

    private static void clearCallbackStack() {
        while (!Callback.stackIsEmpty()) {
            Callback.pop();
        }
    }
}
