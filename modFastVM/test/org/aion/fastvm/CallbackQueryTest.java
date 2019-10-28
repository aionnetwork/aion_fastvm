package org.aion.fastvm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
 * Tests the query methods of the {@link Callback} class.
 */
public class CallbackQueryTest {

    @After
    public void teardown() {
        clearCallbackStack();
    }

    @Test
    public void testGetBlockHash() {
        // Set up the block hash.
        long blockNumber = 5;
        byte[] hash = RandomUtils.nextBytes(32);
        BlockchainForTesting blockchain = new BlockchainForTesting();
        blockchain.registerBlockHash(blockNumber, hash);

        // Push a context and state onto the stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState(blockchain);
        pushContextAndStateToCallbackStack(context, state);

        // 1. Query a block hash that does not exist, we expect to see a byte array of 32 zeros.
        Assert.assertArrayEquals(new byte[32], Callback.getBlockHash(blockNumber + 1));

        // 2. Query a block hash that does exist, we expect to see the real hash.
        Assert.assertArrayEquals(hash, Callback.getBlockHash(blockNumber));
    }

    @Test
    public void testGetBlockHashAtMultipleStackDepths() {
        List<byte[]> hashesForState1 = randomBytes(3);
        List<byte[]> hashesForState2 = randomBytes(2);

        // Create the blockchains and register the blocks.
        // We give blockchain1 blocks 0,1,2 and blockchain2 blocks 3,4.
        BlockchainForTesting blockchainForState1 = new BlockchainForTesting();
        BlockchainForTesting blockchainForState2 = new BlockchainForTesting();

        for (int i = 0; i < 3; i++) {
            blockchainForState1.registerBlockHash(i, hashesForState1.get(i));
        }
        for (int i = 0; i < 2; i++) {
            blockchainForState2.registerBlockHash(i + 3, hashesForState2.get(i));
        }

        // Push two context/state pairs onto the stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState(blockchainForState1);
        pushContextAndStateToCallbackStack(context, state);

        context = newDummyContext();
        state = newState(blockchainForState2);
        pushContextAndStateToCallbackStack(context, state);

        // 1. Query the first 5 blocks and verify only the blocks for state2 are present.
        // Note that a missing block returns a hash of 32 zero bytes.
        Assert.assertArrayEquals(new byte[32], Callback.getBlockHash(0));
        Assert.assertArrayEquals(new byte[32], Callback.getBlockHash(1));
        Assert.assertArrayEquals(new byte[32], Callback.getBlockHash(2));
        Assert.assertArrayEquals(hashesForState2.get(0), Callback.getBlockHash(3));
        Assert.assertArrayEquals(hashesForState2.get(1), Callback.getBlockHash(4));

        // Now pop the top context/state pair off the stack.
        Callback.pop();

        // 2. Query the first 5 blocks and verify only the blocks for state1 are present.
        Assert.assertArrayEquals(hashesForState1.get(0), Callback.getBlockHash(0));
        Assert.assertArrayEquals(hashesForState1.get(1), Callback.getBlockHash(1));
        Assert.assertArrayEquals(hashesForState1.get(2), Callback.getBlockHash(2));
        Assert.assertArrayEquals(new byte[32], Callback.getBlockHash(3));
        Assert.assertArrayEquals(new byte[32], Callback.getBlockHash(4));
    }

    @Test
    public void testGetCode() {
        AionAddress notContract = randomAddress();
        AionAddress contract = randomAddress();
        byte[] code = RandomUtils.nextBytes(52);

        // Save the code to the account and push this onto the Callback stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState(new BlockchainForTesting());
        state.putCode(contract, code);
        pushContextAndStateToCallbackStack(context, state);

        // 1. Query the code of the non-contract address with no code. We expect an empty byte array.
        Assert.assertArrayEquals(new byte[0], Callback.getCode(notContract.toByteArray()));

        // 2. Query the code of the contract. We expect to see the original code.
        Assert.assertArrayEquals(code, Callback.getCode(contract.toByteArray()));
    }

    @Test
    public void testGetCodeAtMultipleStackDepths() {
        List<AionAddress> contractsForState1 = randomAddresses(3);
        List<byte[]> codesForState1 = randomBytes(3);
        List<AionAddress> contractsForState2 = randomAddresses(2);
        List<byte[]> codesForState2 = randomBytes(2);

        // Save the code to the accounts for state1, and push to the Callback stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState(new BlockchainForTesting());
        for (int i = 0; i < 3; i++) {
            state.putCode(contractsForState1.get(i), codesForState1.get(i));
        }
        pushContextAndStateToCallbackStack(context, state);

        // Save the code to the accounts for state2, and push to the Callback stack.
        context = newDummyContext();
        state = newState(new BlockchainForTesting());
        for (int i = 0; i < 2; i++) {
            state.putCode(contractsForState2.get(i), codesForState2.get(i));
        }
        pushContextAndStateToCallbackStack(context, state);

        // 1. Query the code of all the addresses. We only expect to see the code for the state2 addresses.
        // The others will have no code, so we expect to see an empty byte array.
        for (AionAddress contractForState1 : contractsForState1) {
            Assert.assertArrayEquals(new byte[0], Callback.getCode(contractForState1.toByteArray()));
        }
        int index = 0;
        for (AionAddress contractForState2 : contractsForState2) {
            Assert.assertArrayEquals(codesForState2.get(index), Callback.getCode(contractForState2.toByteArray()));
            index++;
        }

        // Now pop the context/state pair off the stack.
        Callback.pop();

        // 2. Query the code of all the addresses. Now we only expect to see the code for state1 addresses.
        index = 0;
        for (AionAddress contractForState1 : contractsForState1) {
            Assert.assertArrayEquals(codesForState1.get(index), Callback.getCode(contractForState1.toByteArray()));
            index++;
        }
        for (AionAddress contractForState2 : contractsForState2) {
            Assert.assertArrayEquals(new byte[0], Callback.getCode(contractForState2.toByteArray()));
        }
    }

    @Test
    public void testAccountExists() {
        AionAddress nonExistingAddress = randomAddress();
        AionAddress existingAddress = randomAddress();

        // We add 1 balance to the address so that it will have state and will exist, and push to the Callback stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState(new BlockchainForTesting());
        state.addBalance(existingAddress, BigInteger.ONE);
        pushContextAndStateToCallbackStack(context, state);

        // 1. Query the non-existent address, we expect to see that it does not exist.
        Assert.assertFalse(Callback.exists(nonExistingAddress.toByteArray()));

        // 2. Query the existent address, we expect to see that it does exist.
        Assert.assertTrue(Callback.exists(existingAddress.toByteArray()));
    }

    @Test
    public void testAccountExistsAtMultipleStackDepths() {
        List<AionAddress> addressesForState1 = randomAddresses(3);
        List<AionAddress> addressesForState2 = randomAddresses(2);

        // Add balance to the accounts for state1 so they exist, and push to the Callback stack.
        ExecutionContext context = newDummyContext();
        ExternalStateForTesting state = newState(new BlockchainForTesting());
        for (AionAddress addressForState1 : addressesForState1) {
            state.addBalance(addressForState1, BigInteger.ONE);
        }
        pushContextAndStateToCallbackStack(context, state);

        // Add balance to the accounts for state2 so they exist, and push to the Callback stack.
        context = newDummyContext();
        state = newState(new BlockchainForTesting());
        for (AionAddress addressForState2 : addressesForState2) {
            state.addBalance(addressForState2, BigInteger.ONE);
        }
        pushContextAndStateToCallbackStack(context, state);

        // 1. Query the existence of all the addresses, we only expect that state2 addresses exist.
        for (AionAddress addressForState1 : addressesForState1) {
            Assert.assertFalse(Callback.exists(addressForState1.toByteArray()));
        }
        for (AionAddress addressForState2 : addressesForState2) {
            Assert.assertTrue(Callback.exists(addressForState2.toByteArray()));
        }

        // Now pop off the context/state pair from the Callback stack.
        Callback.pop();

        // 2. Query the existence of all the addresses, we only expect that state1 addresses exist.
        for (AionAddress addressForState1 : addressesForState1) {
            Assert.assertTrue(Callback.exists(addressForState1.toByteArray()));
        }
        for (AionAddress addressForState2 : addressesForState2) {
            Assert.assertFalse(Callback.exists(addressForState2.toByteArray()));
        }
    }

    private static List<AionAddress> randomAddresses(int num) {
        List<AionAddress> addresses = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            addresses.add(randomAddress());
        }
        return addresses;
    }

    private static List<byte[]> randomBytes(int num) {
        List<byte[]> bytes = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            bytes.add(RandomUtils.nextBytes(32));
        }
        return bytes;
    }

    private static void pushContextAndStateToCallbackStack(ExecutionContext context, ExternalStateForTesting state) {
        Callback.push(Pair.of(context, state));
    }

    private static ExecutionContext newDummyContext() {
        return ExecutionContext.from(new byte[32], randomAddress(), randomAddress(), randomAddress(), 1L, 1L, BigInteger.ZERO, new byte[0], 0, TransactionKind.CALL, 0, randomAddress(), 0L, 0L, 500_000L, FvmDataWord.fromLong(0L));
    }

    private static ExternalStateForTesting newState(BlockchainForTesting blockchain) {
        return new ExternalStateForTesting(RepositoryForTesting.newRepository(), blockchain, randomAddress(), FvmDataWord.fromInt(0), false, true, false, 0L, 0L, 0L, false);
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
