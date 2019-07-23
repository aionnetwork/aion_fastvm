package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.ExternalStateForTesting;
import org.aion.ExternalCapabilitiesForTesting;
import org.aion.FastVmForTesting;
import org.aion.base.AionTransaction;
import org.aion.repository.AccountStateForTesting.VmType;
import org.aion.repository.BlockchainForTesting;
import org.aion.repository.EnergyLimitRuleForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.types.AionAddress;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests on the {@link FastVirtualMachine} class.
 *
 * These tests do not hit the {@code run()} method, but all of the other methods that run() makes
 * use of. The run() method is tested as an integration test in the {@link FastVirtualMachineIntegTest} file.
 */
public class FastVirtualMachineUnitTest {

    @Test
    public void testCallContextConstruction() {
        AionAddress sender = randomAddress();
        AionAddress destination = randomAddress();
        BigInteger value = BigInteger.valueOf(23);
        byte[] data = RandomUtils.nextBytes(40);
        long energyPrice = 10;
        long energyLimit = 3329870;
        AionTransaction transaction = new AionTransaction(BigInteger.ZERO.toByteArray(), sender, destination, value.toByteArray(), data, energyLimit, energyPrice);

        AionAddress miner = randomAddress();
        long blockDifficulty = 235;
        long blockNumber = 2387;
        long blockTimestamp = 129831;
        long blockEnergyLimit = 500000;
        IExternalStateForFvm state = newState(miner, blockDifficulty, blockNumber, blockTimestamp, blockEnergyLimit);

        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);
        Assert.assertEquals(sender, context.getOriginAddress());
        Assert.assertEquals(sender, context.getSenderAddress());
        Assert.assertEquals(destination, context.getDestinationAddress());
        Assert.assertArrayEquals(transaction.getTransactionHash(), context.getTransactionHash());
        Assert.assertNull(context.getContractAddress());
        Assert.assertEquals(energyPrice, context.getTransactionEnergyPrice());
        Assert.assertEquals(energyLimit - transaction.getTransactionCost(), context.getTransactionEnergy());
        Assert.assertEquals(value, context.getTransferValue());
        Assert.assertArrayEquals(data, context.getTransactionData());
        Assert.assertEquals(0, context.getTransactionStackDepth());
        Assert.assertEquals(TransactionKind.CALL, context.getTransactionKind());
        Assert.assertEquals(0, context.getFlags());
        Assert.assertEquals(miner, context.getMinerAddress());
        Assert.assertEquals(blockNumber, context.getBlockNumber());
        Assert.assertEquals(blockTimestamp, context.getBlockTimestamp());
        Assert.assertEquals(blockEnergyLimit, context.getBlockEnergyLimit());
        Assert.assertEquals(blockDifficulty, context.getBlockDifficulty());
        Assert.assertArrayEquals(transaction.getTransactionHash(), context.getHashOfOriginTransaction());
        Assert.assertTrue(context.getSideEffects().getInternalTransactions().isEmpty());
        Assert.assertTrue(context.getSideEffects().getAddressesToBeDeleted().isEmpty());
        Assert.assertTrue(context.getSideEffects().getExecutionLogs().isEmpty());
    }

    @Test
    public void testCreateContextConstruction() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(542);
        byte[] data = new byte[0];
        long energyPrice = Long.MAX_VALUE;
        long energyLimit = 456845;
        AionTransaction transaction = new AionTransaction(BigInteger.ZERO.toByteArray(), sender, null, value.toByteArray(), data, energyLimit, energyPrice);

        AionAddress miner = randomAddress();
        long blockDifficulty = Long.MAX_VALUE;
        long blockNumber = 2387;
        long blockTimestamp = 129831;
        long blockEnergyLimit = 500000;
        IExternalStateForFvm state = newState(miner, blockDifficulty, blockNumber, blockTimestamp, blockEnergyLimit);

        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);
        Assert.assertEquals(sender, context.getOriginAddress());
        Assert.assertEquals(sender, context.getSenderAddress());
        Assert.assertEquals(transaction.getContractAddress(), context.getDestinationAddress());
        Assert.assertArrayEquals(transaction.getTransactionHash(), context.getTransactionHash());
        Assert.assertEquals(transaction.getContractAddress(), context.getContractAddress());
        Assert.assertEquals(energyPrice, context.getTransactionEnergyPrice());
        Assert.assertEquals(energyLimit - transaction.getTransactionCost(), context.getTransactionEnergy());
        Assert.assertEquals(value, context.getTransferValue());
        Assert.assertArrayEquals(data, context.getTransactionData());
        Assert.assertEquals(0, context.getTransactionStackDepth());
        Assert.assertEquals(TransactionKind.CREATE, context.getTransactionKind());
        Assert.assertEquals(0, context.getFlags());
        Assert.assertEquals(miner, context.getMinerAddress());
        Assert.assertEquals(blockNumber, context.getBlockNumber());
        Assert.assertEquals(blockTimestamp, context.getBlockTimestamp());
        Assert.assertEquals(blockEnergyLimit, context.getBlockEnergyLimit());
        Assert.assertEquals(blockDifficulty, context.getBlockDifficulty());
        Assert.assertArrayEquals(transaction.getTransactionHash(), context.getHashOfOriginTransaction());
        Assert.assertTrue(context.getSideEffects().getInternalTransactions().isEmpty());
        Assert.assertTrue(context.getSideEffects().getAddressesToBeDeleted().isEmpty());
        Assert.assertTrue(context.getSideEffects().getExecutionLogs().isEmpty());
    }

    @Test
    public void testCallEnergyLimitRejectionChecks() {
        // Test the minimum.
        long energyLimitTooLowForCall = EnergyLimitRuleForTesting.MIN_NON_CREATE_ENERGY_LIMIT - 1;
        AionTransaction transaction = randomCallTransaction(energyLimitTooLowForCall);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimitTooLowForCall);

        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.INVALID_NRG_LIMIT, result.getResultCode());
        Assert.assertEquals(energyLimitTooLowForCall, result.getEnergyRemaining());

        // Test the maximum.
        long energyLimitTooHighForCall = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT + 1;
        transaction = randomCallTransaction(energyLimitTooHighForCall);
        state = newState();
        result = newSuccessfulResult(energyLimitTooHighForCall);

        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.INVALID_NRG_LIMIT, result.getResultCode());
        Assert.assertEquals(energyLimitTooHighForCall, result.getEnergyRemaining());
    }

    @Test
    public void testCreateEnergyLimitRejectionChecks() {
        // Test the minimum.
        long energyLimitTooLowForCreate = EnergyLimitRuleForTesting.MIN_CREATE_ENERGY_LIMIT - 1;
        AionTransaction transaction = randomCreateTransaction(energyLimitTooLowForCreate);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimitTooLowForCreate);

        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.INVALID_NRG_LIMIT, result.getResultCode());
        Assert.assertEquals(energyLimitTooLowForCreate, result.getEnergyRemaining());

        // Test the maximum.
        long energyLimitTooHighForCall = EnergyLimitRuleForTesting.MAX_CREATE_ENERGY_LIMIT + 1;
        transaction = randomCreateTransaction(energyLimitTooHighForCall);
        state = newState();
        result = newSuccessfulResult(energyLimitTooHighForCall);

        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.INVALID_NRG_LIMIT, result.getResultCode());
        Assert.assertEquals(energyLimitTooHighForCall, result.getEnergyRemaining());
    }

    @Test
    public void testInvalidNonceRejectionChecks() {
        AionAddress sender = randomAddress();
        BigInteger nonce = BigInteger.valueOf(23876);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(EnergyLimitRuleForTesting.MIN_NON_CREATE_ENERGY_LIMIT);

        // Set the sender's nonce
        state.setNonce(sender, nonce);

        // First try with a nonce that is too small.
        AionTransaction transaction = randomCallTransaction(sender, nonce.subtract(BigInteger.ONE), EnergyLimitRuleForTesting.MIN_NON_CREATE_ENERGY_LIMIT);
        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.INVALID_NONCE, result.getResultCode());
        Assert.assertEquals(0, result.getEnergyRemaining());

        // Now try with a nonce that is too large.
        transaction = randomCallTransaction(sender, nonce.add(BigInteger.ONE), EnergyLimitRuleForTesting.MIN_NON_CREATE_ENERGY_LIMIT);
        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.INVALID_NONCE, result.getResultCode());
        Assert.assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testInsufficientBalanceRejectionChecks() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(3467);
        long energyLimit = EnergyLimitRuleForTesting.MAX_CREATE_ENERGY_LIMIT;
        long energyPrice = 13L;

        AionTransaction transaction = randomCreateTransaction(sender, value, energyLimit, energyPrice);
        IExternalStateForFvm state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);

        // Compute the cost of the transaction, and give the sender slightly less balance.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        state.addBalance(sender, transactionCost.subtract(BigInteger.ONE));

        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.INSUFFICIENT_BALANCE, result.getResultCode());
        Assert.assertEquals(0, result.getEnergyRemaining());
    }

    @Test
    public void testPassingRejectionChecks() {
        AionAddress sender = randomAddress();
        BigInteger nonce = BigInteger.valueOf(124);
        BigInteger value = BigInteger.valueOf(127841);
        long energyLimit = EnergyLimitRuleForTesting.MIN_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 50L;

        AionTransaction transaction = randomCallTransaction(sender, nonce, value, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);

        // Set the sender's nonce correctly.
        state.setNonce(sender, nonce);

        // Compute the transaction cost and give the sender just enough balance.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        state.addBalance(sender, transactionCost);

        FastVirtualMachine.performRejectionChecks(state, transaction, result);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        Assert.assertEquals(energyLimit, result.getEnergyRemaining());
    }

    @Test
    public void testIncrementNonceAndDeductEnergyCost() {
        AionAddress sender = randomAddress();
        BigInteger nonce = BigInteger.valueOf(124);
        long energyLimit = EnergyLimitRuleForTesting.MIN_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 3L;
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));

        AionTransaction transaction = randomCallTransaction(sender, nonce, BigInteger.ZERO, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();

        // Set the sender's nonce and balance.
        state.setNonce(sender, nonce);
        state.addBalance(sender, energyCost);

        FastVirtualMachine.incrementNonceAndDeductEnergyCost(state, transaction);

        // Verify the sender's nonce and balance.
        Assert.assertEquals(nonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
    }

    @Test
    public void testRunNonCreateForBalanceTransfer() {
        AionAddress sender = randomAddress();
        AionAddress beneficiary = randomAddress();
        BigInteger value = BigInteger.valueOf(23875);
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCallTransaction(sender, beneficiary, value, energyLimit);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // We do not expect the vm to get called, so we should never see this result.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.VM_REJECTED, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.VM_REJECTED, 0);

        // Run the call.
        FastVmTransactionResult runResult = FastVirtualMachine.runNonContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, runResult.getResultCode());
        Assert.assertEquals(energyLimit, runResult.getEnergyRemaining());

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(beneficiary));
    }

    @Test
    public void testRunNonCreateForCallForkEnabled() {
        AionAddress sender = randomAddress();
        AionAddress destination = randomAddress();
        BigInteger value = BigInteger.valueOf(23875);
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCallTransaction(sender, destination, value, energyLimit);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // Turn the destination into a contract by giving it some code.
        state.putCode(destination, RandomUtils.nextBytes(10));

        // We expect the v1 entry point to get called.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.VM_REJECTED, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);

        // Run the call.
        FastVmTransactionResult runResult = FastVirtualMachine.runNonContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.FAILURE, runResult.getResultCode());
        Assert.assertEquals(0, runResult.getEnergyRemaining());

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(destination));
    }

    @Test
    public void testRunNonCreateForCallForkDisabled() {
        AionAddress sender = randomAddress();
        AionAddress destination = randomAddress();
        BigInteger value = BigInteger.valueOf(23875);
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCallTransaction(sender, destination, value, energyLimit);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // Turn the destination into a contract by giving it some code.
        state.putCode(destination, RandomUtils.nextBytes(10));

        // We expect the original (not the v1) entry point to get called.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.VM_REJECTED, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);

        // Run the call.
        FastVmTransactionResult runResult = FastVirtualMachine.runNonContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, false);
        Assert.assertEquals(FastVmResultCode.VM_REJECTED, runResult.getResultCode());
        Assert.assertEquals(0, runResult.getEnergyRemaining());

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(destination));
    }

    @Test
    public void testRunCreateWhenDestinationAlreadyExistsPre040Fork() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // Give the destination account state.
        AionAddress destination = transaction.getContractAddress();
        BigInteger contractBalance = BigInteger.ONE;
        state.addBalance(destination, contractBalance);

        // We expect to fail out before the vm ever gets run, so we should never see these errors.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_JUMP_DESTINATION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, false);
        Assert.assertEquals(FastVmResultCode.FAILURE, runResult.getResultCode());
        Assert.assertEquals(0, runResult.getEnergyRemaining());

        // Verify no balance was transferred.
        Assert.assertEquals(value, state.getBalance(sender));
        Assert.assertEquals(destination, transaction.getContractAddress());
        Assert.assertEquals(contractBalance, state.getBalance(destination));
    }

    @Test
    public void testRunCreateWhenContractAlreadyExistsPost040Fork() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // Give the destination some code so that it is already a contract.
        AionAddress destination = transaction.getContractAddress();
        state.putCode(destination, new byte[1]);

        // We expect to fail out before the vm ever gets run, so we should never see these errors.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_JUMP_DESTINATION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, false);
        Assert.assertEquals(FastVmResultCode.FAILURE, runResult.getResultCode());
        Assert.assertEquals(0, runResult.getEnergyRemaining());

        // Verify no balance was transferred.
        Assert.assertEquals(value, state.getBalance(sender));
        Assert.assertEquals(destination, transaction.getContractAddress());
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    @Test
    public void testRunCreatePre040ForkSuccessful() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[1];
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // We expect to see the pre 040 successful result, which we can identify primarily by its remaining energy.
        byte[] code = RandomUtils.nextBytes(38);
        long energyRemaining = 81L;
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.SUCCESS, energyRemaining, code);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, runResult.getResultCode());
        Assert.assertEquals(energyRemaining, runResult.getEnergyRemaining());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify the code has been saved to the contract.
        Assert.assertArrayEquals(code, state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePre040ForkUnsuccessful() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[1];
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // We expect to see the pre 040 successful result.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.OUT_OF_NRG, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, false);
        Assert.assertEquals(FastVmResultCode.OUT_OF_NRG, runResult.getResultCode());
        Assert.assertEquals(0, runResult.getEnergyRemaining());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify the code has NOT been saved to the contract.
        Assert.assertNull(state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePost040ForkSuccessful() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[1];
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // We expect to see the post 040 successful result, which we can identify primarily by its remaining energy.
        byte[] code = RandomUtils.nextBytes(38);
        long energyRemaining = 81L;
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.SUCCESS, energyRemaining, code);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, runResult.getResultCode());
        Assert.assertEquals(energyRemaining, runResult.getEnergyRemaining());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify the code has been saved to the contract.
        Assert.assertArrayEquals(code, state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePost040ForkSuccessfulWhenDestinationIsRegularAccount() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[1];
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // Give the destination some account state, no code.
        AionAddress contract = transaction.getContractAddress();
        state.incrementNonce(contract);

        // We expect to see the post 040 successful result, which we can identify primarily by its remaining energy.
        byte[] code = RandomUtils.nextBytes(38);
        long energyRemaining = 81L;
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.SUCCESS, energyRemaining, code);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, runResult.getResultCode());
        Assert.assertEquals(energyRemaining, runResult.getEnergyRemaining());

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify the code has been saved to the contract.
        Assert.assertArrayEquals(code, state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePost040ForkUnsuccessful() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[1];
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // We expect to see the post 040 result.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_JUMP_DESTINATION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.BAD_JUMP_DESTINATION, runResult.getResultCode());
        Assert.assertEquals(0, runResult.getEnergyRemaining());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify the code has been saved to the contract.
        Assert.assertNull(state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePost040ForkUnsuccessfulWhenDestinationIsRegularAccount() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[1];
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // Give the destination some account state, but no code.
        AionAddress contract = transaction.getContractAddress();
        state.incrementNonce(contract);

        // We expect to see the post 040 result.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_JUMP_DESTINATION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.BAD_JUMP_DESTINATION, runResult.getResultCode());
        Assert.assertEquals(0, runResult.getEnergyRemaining());

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify the code has been saved to the contract.
        Assert.assertNull(state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePre040ForkNoData() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[0];  // we give an empty byte array.
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // We do not expect to see either result, since we gave no data the vm won't be run.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_JUMP_DESTINATION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, runResult.getResultCode());
        Assert.assertEquals(energyLimit, runResult.getEnergyRemaining());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify that NO code has been saved to the contract.
        Assert.assertNull(state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePost040ForkNoData() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[0];  // we give an empty byte array.
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // We do not expect to see either result, since we gave no data the vm won't be run.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_JUMP_DESTINATION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, runResult.getResultCode());
        Assert.assertEquals(energyLimit, runResult.getEnergyRemaining());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify that NO code has been saved to the contract.
        Assert.assertNull(state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    @Test
    public void testRunCreatePost040ForkNoDataWhenDestinationIsRegularAccount() {
        AionAddress sender = randomAddress();
        BigInteger value = BigInteger.valueOf(2378);
        byte[] data = new byte[0];  // we give an empty byte array.
        long energyLimit = 100_000L;

        AionTransaction transaction = randomCreateTransaction(sender, value, data, energyLimit, 1L);
        ExternalStateForTesting state = newState();
        FastVmTransactionResult result = newSuccessfulResult(energyLimit);
        ExecutionContext context = FastVirtualMachine.constructTransactionContext(transaction, transaction.getContractAddress(), state);

        // Give the sender sufficient balance.
        state.addBalance(sender, value);

        // Give the destination some account state, but no code.
        AionAddress contract = transaction.getContractAddress();
        state.incrementNonce(contract);

        // We do not expect to see either result, since we gave no data the vm won't be run.
        FastVmForTesting fvm = new FastVmForTesting();
        fvm.pre040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_JUMP_DESTINATION, 0);
        fvm.post040ForkResult = new FastVmTransactionResult(FastVmResultCode.BAD_INSTRUCTION, 0);

        // Run the create.
        FastVmTransactionResult runResult = FastVirtualMachine.runContractCreationTransaction(fvm, state, new ExternalCapabilitiesForTesting(), context, transaction, result, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, runResult.getResultCode());
        Assert.assertEquals(energyLimit, runResult.getEnergyRemaining());

        // Verify the destination has been marked as an FVM contract.
        Assert.assertEquals(VmType.FVM, state.getVmType(contract));

        // Verify that NO code has been saved to the contract.
        Assert.assertNull(state.getCode(contract));

        // Verify the balance was transferred.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(value, state.getBalance(contract));
    }

    private static FastVmTransactionResult newSuccessfulResult(long energyRemaining) {
        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, energyRemaining);
    }

    private static AionTransaction randomCallTransaction(AionAddress sender, AionAddress destination, BigInteger value, long energyLimit) {
        return new AionTransaction(BigInteger.ZERO.toByteArray(), sender, destination, value.toByteArray(), RandomUtils.nextBytes(61), energyLimit, 1L);
    }

    private static AionTransaction randomCallTransaction(AionAddress sender, BigInteger nonce, BigInteger value, long energyLimit, long energyPrice) {
        return new AionTransaction(nonce.toByteArray(), sender, randomAddress(), value.toByteArray(), RandomUtils.nextBytes(61), energyLimit, energyPrice);
    }

    private static AionTransaction randomCreateTransaction(AionAddress sender, BigInteger value, byte[] data, long energyLimit, long energyPrice) {
        return new AionTransaction(BigInteger.ZERO.toByteArray(), sender, null, value.toByteArray(), data, energyLimit, energyPrice);
    }

    private static AionTransaction randomCreateTransaction(AionAddress sender, BigInteger value, long energyLimit, long energyPrice) {
        return new AionTransaction(BigInteger.ZERO.toByteArray(), sender, null, value.toByteArray(), RandomUtils.nextBytes(61), energyLimit, energyPrice);
    }

    private static AionTransaction randomCallTransaction(AionAddress sender, BigInteger nonce, long energyLimit) {
        return new AionTransaction(nonce.toByteArray(), sender, randomAddress(), BigInteger.ZERO.toByteArray(), RandomUtils.nextBytes(61), energyLimit, 1L);
    }

    private static AionTransaction randomCreateTransaction(long energyLimit) {
        return new AionTransaction(BigInteger.ZERO.toByteArray(), randomAddress(), null, BigInteger.ZERO.toByteArray(), RandomUtils.nextBytes(61), energyLimit, 1L);
    }

    private static AionTransaction randomCallTransaction(long energyLimit) {
        return new AionTransaction(BigInteger.ZERO.toByteArray(), randomAddress(), randomAddress(), BigInteger.ZERO.toByteArray(), RandomUtils.nextBytes(61), energyLimit, 1L);
    }

    private static ExternalStateForTesting newState() {
        return newState(randomAddress(), 0L, 0L, 0L, 15_000_000L);
    }

    private static ExternalStateForTesting newState(AionAddress miner, long blockDifficulty, long blockNumber, long blockTimestamp, long blockEnergyLimit) {
        return new ExternalStateForTesting(RepositoryForTesting.newRepository(), new BlockchainForTesting(), miner, FvmDataWord.fromLong(blockDifficulty), false, true, false, blockNumber, blockTimestamp, blockEnergyLimit);
    }

    private static AionAddress randomAddress() {
        byte[] bytes = RandomUtils.nextBytes(AionAddress.LENGTH);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }
}
