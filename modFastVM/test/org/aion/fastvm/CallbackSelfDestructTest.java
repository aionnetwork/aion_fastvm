package org.aion.fastvm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the SELFDESTRUCT opcode processing logic in the {@link Callback} class.
 */
public class CallbackSelfDestructTest {

    @After
    public void tearDown() {
        clearCallbackStack();
    }

    @Test
    public void testSelfDestruct() {
        BigInteger deadAddressBalance = BigInteger.valueOf(375);
        AionAddress addressToKill = randomAddress();
        AionAddress beneficiary = randomAddress();

        // Add the balance to the address that will self destruct, and push context/state to the Callback stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState();
        state.addBalance(addressToKill, deadAddressBalance);
        pushContextAndStateToCallbackStack(context, state);

        // Run the self destruct.
        Callback.selfDestruct(addressToKill.toByteArray(), beneficiary.toByteArray());

        // Verify all of the balance has transferred to the beneficiary.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(addressToKill));
        Assert.assertEquals(deadAddressBalance, state.getBalance(beneficiary));

        // Verify the list of deleted accounts holds the dead address.
        Assert.assertEquals(1, context.getSideEffects().getAddressesToBeDeleted().size());
        Assert.assertEquals(addressToKill, context.getSideEffects().getAddressesToBeDeleted().get(0));

        // Verify there is an internal transaction that reports the balance transfer.
        Assert.assertEquals(1, context.getSideEffects().getInternalTransactions().size());
        InternalTransaction transaction = context.getSideEffects().getInternalTransactions().get(0);

        Assert.assertEquals(addressToKill, transaction.sender);
        Assert.assertEquals(beneficiary, transaction.destination);
        Assert.assertEquals(deadAddressBalance, transaction.value);
        Assert.assertFalse(transaction.isRejected);
        Assert.assertFalse(transaction.isCreate);
    }

    @Test
    public void testSelfDestructWithSelfAsBeneficiary() {
        BigInteger deadAddressBalance = BigInteger.valueOf(375);
        AionAddress addressToKill = randomAddress();

        // Add the balance to the address that will self destruct, and push context/state to the Callback stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState();
        state.addBalance(addressToKill, deadAddressBalance);
        pushContextAndStateToCallbackStack(context, state);

        // Run the self destruct.
        Callback.selfDestruct(addressToKill.toByteArray(), addressToKill.toByteArray());

        // Verify the dead account has zero balance now.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(addressToKill));

        // Verify the list of deleted accounts holds the dead address.
        Assert.assertEquals(1, context.getSideEffects().getAddressesToBeDeleted().size());
        Assert.assertEquals(addressToKill, context.getSideEffects().getAddressesToBeDeleted().get(0));

        // Verify there is an internal transaction that reports the balance transfer.
        Assert.assertEquals(1, context.getSideEffects().getInternalTransactions().size());
        InternalTransaction transaction = context.getSideEffects().getInternalTransactions().get(0);

        Assert.assertEquals(addressToKill, transaction.sender);
        Assert.assertEquals(addressToKill, transaction.destination);
        Assert.assertEquals(deadAddressBalance, transaction.value);
        Assert.assertFalse(transaction.isRejected);
        Assert.assertFalse(transaction.isCreate);
    }

    @Test
    public void testSelfDestructAtMultipleStackDepths() {
        BigInteger[] addressBalancesForState1 = new BigInteger[]{ BigInteger.valueOf(732), BigInteger.valueOf(34), BigInteger.valueOf(987) };
        List<AionAddress> addressesToKillForState1 = randomAddresses(3);
        BigInteger[] addressBalancesForState2 = new BigInteger[]{ BigInteger.valueOf(435), BigInteger.valueOf(4356) };
        List<AionAddress> addressesToKillForState2 = randomAddresses(2);
        AionAddress beneficiary = randomAddress();

        Map<AionAddress, BigInteger> accountBalances = new HashMap<>();
        accountBalances.put(addressesToKillForState1.get(0), addressBalancesForState1[0]);
        accountBalances.put(addressesToKillForState1.get(1), addressBalancesForState1[1]);
        accountBalances.put(addressesToKillForState1.get(2), addressBalancesForState1[2]);
        accountBalances.put(addressesToKillForState2.get(0), addressBalancesForState2[0]);
        accountBalances.put(addressesToKillForState2.get(1), addressBalancesForState2[1]);

        // Give all of the state1 addresses their balance and push this context/state pair to the Callback stack.
        ExecutionContext context1 = newDummyContext();
        ExternalStateForTesting state1 = newState();
        for (int i = 0; i < 3; i++) {
            state1.addBalance(addressesToKillForState1.get(i), addressBalancesForState1[i]);
        }
        pushContextAndStateToCallbackStack(context1, state1);

        // Give all of the state2 addresses their balance and push this context/state pair to the Callback stack.
        ExecutionContext context2 = newDummyContext();
        ExternalStateForTesting state2 = newState();
        for (int i = 0; i < 2; i++) {
            state2.addBalance(addressesToKillForState2.get(i), addressBalancesForState2[i]);
        }
        pushContextAndStateToCallbackStack(context2, state2);

        // 1. Run self destruct on each of the addresses, we only expect the state2 addresses to actually exist and therefore transfer balance.
        for (AionAddress addressToKillForState2 : addressesToKillForState2) {
            Callback.selfDestruct(addressToKillForState2.toByteArray(), beneficiary.toByteArray());
        }
        for (AionAddress addressToKillForState1 : addressesToKillForState1) {
            Callback.selfDestruct(addressToKillForState1.toByteArray(), beneficiary.toByteArray());
        }

        // Verify the state2 addresses have no balance and the beneficiary has their sum.
        Assert.assertEquals(BigInteger.ZERO, state2.getBalance(addressesToKillForState2.get(0)));
        Assert.assertEquals(BigInteger.ZERO, state2.getBalance(addressesToKillForState2.get(1)));
        Assert.assertEquals(addressBalancesForState2[0].add(addressBalancesForState2[1]), state2.getBalance(beneficiary));

        // Verify the list of deleted accounts holds every address.
        Assert.assertEquals(5, context2.getSideEffects().getAddressesToBeDeleted().size());
        Set<AionAddress> allAddresses = mergeAndAddToSet(addressesToKillForState1, addressesToKillForState2);
        Set<AionAddress> killAddresses = new HashSet<>(context2.getSideEffects().getAddressesToBeDeleted());
        Assert.assertEquals(allAddresses, killAddresses);

        // Verify that 5 internal transactions were fired off, and they were all correct.
        Assert.assertEquals(5, context2.getSideEffects().getInternalTransactions().size());
        for (InternalTransaction transaction : context2.getSideEffects().getInternalTransactions()) {

            if (addressesToKillForState2.contains(transaction.sender)) {
                // If this address is in state2 then an actual transfer occurred.
                assertTransactionCorrect(transaction, beneficiary, accountBalances.get(transaction.sender));
            } else {
                // If this address is in state1 then no balance was transferred.
                assertTransactionCorrect(transaction, beneficiary, BigInteger.ZERO);
            }
        }

        // Now pop the context/state pair off the stack.
        Callback.pop();

        // 2. Run self destruct on each of the addresses, we only expect the state1 addresses to actually exist and therefore transfer balance.
        for (AionAddress addressToKillForState2 : addressesToKillForState2) {
            Callback.selfDestruct(addressToKillForState2.toByteArray(), beneficiary.toByteArray());
        }
        for (AionAddress addressToKillForState1 : addressesToKillForState1) {
            Callback.selfDestruct(addressToKillForState1.toByteArray(), beneficiary.toByteArray());
        }

        // Verify the state1 addresses have no balance and the beneficiary has their sum.
        Assert.assertEquals(BigInteger.ZERO, state1.getBalance(addressesToKillForState1.get(0)));
        Assert.assertEquals(BigInteger.ZERO, state1.getBalance(addressesToKillForState1.get(1)));
        Assert.assertEquals(BigInteger.ZERO, state1.getBalance(addressesToKillForState1.get(2)));
        Assert.assertEquals(addressBalancesForState1[0].add(addressBalancesForState1[1]).add(addressBalancesForState1[2]), state1.getBalance(beneficiary));

        // Verify the list of deleted accounts holds every address.
        Assert.assertEquals(5, context1.getSideEffects().getAddressesToBeDeleted().size());
        allAddresses = mergeAndAddToSet(addressesToKillForState1, addressesToKillForState2);
        killAddresses = new HashSet<>(context1.getSideEffects().getAddressesToBeDeleted());
        Assert.assertEquals(allAddresses, killAddresses);

        // Verify that 5 internal transactions were fired off, and they were all correct.
        Assert.assertEquals(5, context1.getSideEffects().getInternalTransactions().size());
        for (InternalTransaction transaction : context1.getSideEffects().getInternalTransactions()) {

            if (addressesToKillForState1.contains(transaction.sender)) {
                // If this address is in state1 then an actual transfer occurred.
                assertTransactionCorrect(transaction, beneficiary, accountBalances.get(transaction.sender));
            } else {
                // If this address is in state2 then no balance was transferred.
                assertTransactionCorrect(transaction, beneficiary, BigInteger.ZERO);
            }
        }
    }

    private static void assertTransactionCorrect(InternalTransaction transaction, AionAddress destination, BigInteger value) {
        Assert.assertEquals(destination, transaction.destination);
        Assert.assertEquals(value, transaction.value);
        Assert.assertFalse(transaction.isRejected);
        Assert.assertFalse(transaction.isCreate);
    }

    private static Set<AionAddress> mergeAndAddToSet(List<AionAddress> addresses1, List<AionAddress> addresses2) {
        Set<AionAddress> addresses = new HashSet<>();
        addresses.addAll(addresses1);
        addresses.addAll(addresses2);
        return addresses;
    }

    private static List<AionAddress> randomAddresses(int num) {
        List<AionAddress> addresses = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            addresses.add(randomAddress());
        }
        return addresses;
    }

    private static void pushContextAndStateToCallbackStack(ExecutionContext context, ExternalStateForTesting state) {
        Callback.push(Pair.of(context, state));
    }

    private static ExecutionContext newDummyContext() {
        return new ExecutionContext(null, null, randomAddress(), randomAddress(), randomAddress(), FvmDataWord.fromLong(1L), 1L, FvmDataWord.fromBigInteger(
            BigInteger.ZERO), new byte[0], 0, ExecutionContext.CALL, 0, randomAddress(), 0L, 0L, 500_000L, FvmDataWord.fromLong(0L));
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
