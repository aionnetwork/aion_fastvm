package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.ExternalStateForTesting;
import org.aion.base.AionTransaction;
import org.aion.contract.ContractUtils;
import org.aion.repository.BlockchainForTesting;
import org.aion.repository.EnergyLimitRuleForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class only tests the {@code run()} method of the {@link FastVirtualMachine} class.
 *
 * The other methods in this class are all tested out as unit tests in the {@link FastVirtualMachineUnitTest} file.
 */
public class FastVirtualMachineIntegTest {
    private enum Contract { DEFAULT_PAYABLE, TICKER }
    private enum TickerFunction { TICKING, GET_TICKER }
    private enum DefaultPayableFunction { NON_PAYABLE }

    @Test
    public void testBalanceTransferToRegularAccountPre040Fork() {
        AionAddress sender = randomAddress();
        AionAddress beneficiary = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        byte[] data = "nonsense".getBytes();
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, beneficiary, value.toByteArray(), data, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);

        state.setNonce(sender, senderNonce);
        state.addBalance(sender, transactionCost);
        state.addBalance(beneficiary, beneficiaryBalance);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        Assert.assertEquals(energyLimit - transaction.getTransactionCost(), result.getEnergyRemaining());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(beneficiary));

        // Verify the account balances.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance.add(value), state.getBalance(beneficiary));
    }

    @Test
    public void testBalanceTransferToRegularAccountPost040Fork() {
        AionAddress sender = randomAddress();
        AionAddress beneficiary = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        byte[] data = "nonsense".getBytes();
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, beneficiary, value.toByteArray(), data, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);

        state.setNonce(sender, senderNonce);
        state.addBalance(sender, transactionCost);
        state.addBalance(beneficiary, beneficiaryBalance);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
        Assert.assertEquals(energyLimit - transaction.getTransactionCost(), result.getEnergyRemaining());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(beneficiary));

        // Verify the account balances.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance.add(value), state.getBalance(beneficiary));
    }

    @Test
    public void testBalanceTransferToContractPre040Fork() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        byte[] data = new byte[0];
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, contract, value.toByteArray(), data, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);

        state.setNonce(sender, senderNonce);
        state.addBalance(sender, transactionCost);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.DEFAULT_PAYABLE);
        state.putCode(contract, code);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the account balances.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance.add(value), state.getBalance(contract));
    }

    @Test
    public void testBalanceTransferToContractPost040Fork() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        byte[] data = new byte[0];
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, contract, value.toByteArray(), data, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);

        state.setNonce(sender, senderNonce);
        state.addBalance(sender, transactionCost);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.DEFAULT_PAYABLE);
        state.putCode(contract, code);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the account balances.
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance.add(value), state.getBalance(contract));
    }

    @Test
    public void testCallContractPre040Fork() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);
        BigInteger senderBalance = transactionCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.TICKER);
        state.putCode(contract, code);

        byte[] data = encodeCallToTickerContract(TickerFunction.TICKING);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, contract, value.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the account balances.
        Assert.assertEquals(senderBalance.subtract(transactionCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance.add(value), state.getBalance(contract));
    }

    @Test
    public void testCallContractPost040Fork() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);
        BigInteger senderBalance = transactionCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.TICKER);
        state.putCode(contract, code);

        byte[] data = encodeCallToTickerContract(TickerFunction.TICKING);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, contract, value.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the account balances.
        Assert.assertEquals(senderBalance.subtract(transactionCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance.add(value), state.getBalance(contract));
    }

    @Test
    public void testWriteAndReadContractCallsPre040Fork() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);
        BigInteger senderBalance = energyCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.addBalance(sender, senderBalance);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.TICKER);
        state.putCode(contract, code);

        // Call TICKING, which will increment the contract's counter to 1.
        byte[] data = encodeCallToTickerContract(TickerFunction.TICKING);
        AionTransaction transaction = new AionTransaction(BigInteger.ZERO.toByteArray(), sender, contract, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the account nonces and balances.
        Assert.assertEquals(BigInteger.ONE, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));

        // Call GET_TICKER, to grab the value we've incremented.
        data = encodeCallToTickerContract(TickerFunction.GET_TICKER);
        transaction = new AionTransaction(BigInteger.ONE.toByteArray(), sender, contract, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the output of the contract, which is the counter that should be set to 1.
        Assert.assertEquals(1, FvmDataWord.fromBytes(result.getReturnData()).toInt());

        // Verify the account nonces and balances.
        Assert.assertEquals(BigInteger.TWO, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));
        Assert.assertEquals(senderBalance.subtract(energyCost).subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));
    }

    @Test
    public void testWriteAndReadContractCallsPost040Fork() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);
        BigInteger senderBalance = energyCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.addBalance(sender, senderBalance);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.TICKER);
        state.putCode(contract, code);

        // Call TICKING, which will increment the contract's counter to 1.
        byte[] data = encodeCallToTickerContract(TickerFunction.TICKING);
        AionTransaction transaction = new AionTransaction(BigInteger.ZERO.toByteArray(), sender, contract, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the account nonces and balances.
        Assert.assertEquals(BigInteger.ONE, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));

        // Call GET_TICKER, to grab the value we've incremented.
        data = encodeCallToTickerContract(TickerFunction.GET_TICKER);
        transaction = new AionTransaction(BigInteger.ONE.toByteArray(), sender, contract, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the output of the contract, which is the counter that should be set to 1.
        Assert.assertEquals(1, FvmDataWord.fromBytes(result.getReturnData()).toInt());

        // Verify the account nonces and balances.
        Assert.assertEquals(BigInteger.TWO, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));
        Assert.assertEquals(senderBalance.subtract(energyCost).subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));
    }

    @Test
    public void testCreateContractPre040Fork() throws Exception {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the sender account.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a contract now on top of the regular account.
        byte[] data = getContractBytecode(Contract.TICKER);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, null, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination now has the correct contract code.
        byte[] expectedCode = getContractRuntimeBytecode(Contract.TICKER);
        Assert.assertArrayEquals(expectedCode, state.getCode(contract));

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the account balances.
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(contract));
    }

    @Test
    public void testCreateContractPost040Fork() throws Exception {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the sender account.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a contract now on top of the regular account.
        byte[] data = getContractBytecode(Contract.TICKER);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, null, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        AionAddress contract = transaction.getContractAddress();

        // Verify the destination now has the correct contract code.
        byte[] expectedCode = getContractRuntimeBytecode(Contract.TICKER);
        Assert.assertArrayEquals(expectedCode, state.getCode(contract));

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the account balances.
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(contract));
    }

    @Test
    public void testCreateContractPost040ForkWhenDestinationIsRegularAccount() throws Exception {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);
        BigInteger senderBalance = energyCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a contract now on top of the regular account.
        byte[] data = getContractBytecode(Contract.TICKER);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, null, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        // Destination has account state, but is not a contract.
        AionAddress contract = transaction.getContractAddress();
        state.addBalance(contract, beneficiaryBalance);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

        // Verify the destination now has the correct contract code.
        byte[] expectedCode = getContractRuntimeBytecode(Contract.TICKER);
        Assert.assertArrayEquals(expectedCode, state.getCode(contract));

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the account balances.
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));
    }

    @Test
    public void testCallContractPre040ForkFailure() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);
        BigInteger senderBalance = transactionCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.DEFAULT_PAYABLE);
        state.putCode(contract, code);

        // We attempt to transfer value to a non-payable function, this is why we fail!
        byte[] data = encodeCallToDefaultPayableContract(DefaultPayableFunction.NON_PAYABLE);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, contract, value.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.REVERT, result.getResultCode());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the sender was charged the energy cost, but no value was transferred.
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));
    }

    @Test
    public void testCallContractPost040ForkFailure() throws Exception {
        AionAddress sender = randomAddress();
        AionAddress contract = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);
        BigInteger senderBalance = transactionCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);
        state.addBalance(contract, beneficiaryBalance);

        // Give the destination some code so that it is a contract.
        byte[] code = getContractRuntimeBytecode(Contract.DEFAULT_PAYABLE);
        state.putCode(contract, code);

        // We attempt to transfer value to a non-payable function, this is why we fail!
        byte[] data = encodeCallToDefaultPayableContract(DefaultPayableFunction.NON_PAYABLE);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, contract, value.toByteArray(), data, energyLimit, energyPrice);

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertEquals(FastVmResultCode.REVERT, result.getResultCode());

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));

        // Verify the sender was charged the energy cost, but no value was transferred.
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));
    }

    @Test
    public void testCreateContractPre040ForkFailure() {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a nonsensical contract, this is why we fail!
        byte[] data = RandomUtils.nextBytes(3110);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, null, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        AionAddress destination = transaction.getContractAddress();

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertTrue(result.getResultCode().isFailed());

        // Verify the destination has no code.
        Assert.assertNull(state.getCode(destination));

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the sender was charged the energy cost.
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    @Test
    public void testCreateContractPost040ForkFailure() {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.add(BigInteger.valueOf(1_000_000_000L));

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a nonsensical contract, this is why we fail!
        byte[] data = RandomUtils.nextBytes(3110);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, null, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        AionAddress destination = transaction.getContractAddress();

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, true);
        Assert.assertTrue(result.getResultCode().isFailed());

        // Verify the destination has no code.
        Assert.assertNull(state.getCode(destination));

        // Verify the account nonces.
        Assert.assertEquals(senderNonce.add(BigInteger.ONE), state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the sender was charged the energy cost.
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    @Test
    public void testRejectedRun() {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        // Set up the accounts. The sender does not have enough balance to cover the transaction.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.subtract(BigInteger.ONE);

        ExternalStateForTesting state = newState();
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a nonsensical contract, this is why we fail!
        byte[] data = RandomUtils.nextBytes(3110);
        AionTransaction transaction = new AionTransaction(senderNonce.toByteArray(), sender, null, BigInteger.ZERO.toByteArray(), data, energyLimit, energyPrice);

        AionAddress destination = transaction.getContractAddress();

        // Run the transaction.
        FastVmTransactionResult result = FastVirtualMachine.run(state, transaction, false);
        Assert.assertEquals(FastVmResultCode.INSUFFICIENT_BALANCE, result.getResultCode());

        // Verify the destination has no code.
        Assert.assertNull(state.getCode(destination));

        // Verify the account nonces did not change.
        Assert.assertEquals(senderNonce, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the account balances did not change.
        Assert.assertEquals(senderBalance, state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    private static byte[] encodeCallToDefaultPayableContract(DefaultPayableFunction function) {
        switch (function) {
            case NON_PAYABLE: return ByteUtil.hexStringToBytes("52da86d5");
            default: throw new IllegalArgumentException("Expected: NON_PAYABLE. Found: " + function);
        }
    }

    private static byte[] encodeCallToTickerContract(TickerFunction function) {
        switch (function) {
            case TICKING: return ByteUtil.hexStringToBytes("dae29f29");
            case GET_TICKER: return ByteUtil.hexStringToBytes("c0004213");
            default: throw new IllegalArgumentException("Expected: TICKING, GET_TICKER. Found: " + function);
        }
    }

    private static byte[] getContractBytecode(Contract contract) throws Exception {
        String contractName;
        switch (contract) {
            case DEFAULT_PAYABLE:
                contractName = "DefaultPayable";
                break;
            case TICKER:
                contractName = "Ticker";
                break;
            default: throw new IllegalArgumentException("Expected: DEFAULT_PAYABLE, TICKER. Found: " + contract);
        }
        return ContractUtils.getContractDeployer(contractName + ".sol", contractName);
    }

    private static byte[] getContractRuntimeBytecode(Contract contract) throws Exception {
        String contractName;
        switch (contract) {
            case DEFAULT_PAYABLE:
                contractName = "DefaultPayable";
                break;
            case TICKER:
                contractName = "Ticker";
                break;
            default: throw new IllegalArgumentException("Expected: DEFAULT_PAYABLE, TICKER. Found: " + contract);
        }
        return ContractUtils.getContractBody(contractName + ".sol", contractName);
    }

    private static ExternalStateForTesting newState() {
        return new ExternalStateForTesting(RepositoryForTesting.newRepository(), new BlockchainForTesting(), randomAddress(), FvmDataWord.fromLong(0L), false, true, false, 0L, 0L, 15_000_000L);
    }

    private static AionAddress randomAddress() {
        byte[] bytes = RandomUtils.nextBytes(AionAddress.LENGTH);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }
}
