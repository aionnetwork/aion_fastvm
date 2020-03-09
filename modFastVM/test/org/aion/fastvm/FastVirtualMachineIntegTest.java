package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.ExternalCapabilitiesForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.contract.ContractUtils;
import org.aion.repository.BlockchainForTesting;
import org.aion.repository.EnergyLimitRuleForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;
import org.aion.fastvm.util.HexUtil;
import org.aion.fastvm.util.TransactionResultUtil;
import org.aion.fastvm.util.TransactionUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This class only tests the {@code run()} method of the {@link FastVirtualMachine} class.
 *
 * The other methods in this class are all tested out as unit tests in the {@link FastVirtualMachineUnitTest} file.
 */
public class FastVirtualMachineIntegTest {
    private static IExternalCapabilities capabilities;
    private enum Contract { DEFAULT_PAYABLE, TICKER }
    private enum TickerFunction { TICKING, GET_TICKER }
    private enum DefaultPayableFunction { NON_PAYABLE }

    @BeforeClass
    public static void setupCapabilities() {
        capabilities = new ExternalCapabilitiesForTesting();
    }

    @Test
    public void testBalanceTransferToRegularAccountPre040Fork() {
        AionAddress sender = randomAddress();
        AionAddress beneficiary = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        BigInteger value = BigInteger.valueOf(21873);
        byte[] data = "nonsense".getBytes();
        long energyLimit = EnergyLimitRuleForTesting.MAX_NON_CREATE_ENERGY_LIMIT;
        long energyPrice = 4L;

        Transaction transaction = Transaction.contractCallTransaction(sender, beneficiary, new byte[32], senderNonce, value, data, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);

        state.setNonce(sender, senderNonce);
        state.addBalance(sender, transactionCost);
        state.addBalance(beneficiary, beneficiaryBalance);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());
        Assert.assertEquals(TransactionUtil.computeTransactionCost(transaction), result.energyUsed);

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

        Transaction transaction = Transaction.contractCallTransaction(sender, beneficiary, new byte[32], senderNonce, value, data, energyLimit, energyPrice);
        ExternalStateForTesting state = newState();

        // Set up the accounts.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger transactionCost = energyCost.add(value);
        BigInteger beneficiaryBalance = BigInteger.valueOf(12978);

        state.setNonce(sender, senderNonce);
        state.addBalance(sender, transactionCost);
        state.addBalance(beneficiary, beneficiaryBalance);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());
        Assert.assertEquals(TransactionUtil.computeTransactionCost(transaction), result.energyUsed);

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

        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], senderNonce, value, data, energyLimit, energyPrice);
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
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

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

        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], senderNonce, value, data, energyLimit, energyPrice);
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
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

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
        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], senderNonce, value, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

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
        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], senderNonce, value, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

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
        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], BigInteger.ZERO, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

        // Verify the account nonces and balances.
        Assert.assertEquals(BigInteger.ONE, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));

        // Call GET_TICKER, to grab the value we've incremented.
        data = encodeCallToTickerContract(TickerFunction.GET_TICKER);
        transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], BigInteger.ONE, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

        // Verify the output of the contract, which is the counter that should be set to 1.
        Assert.assertEquals(1, FvmDataWord.fromBytes(result.copyOfTransactionOutput().orElse(new byte[0])).toInt());

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
        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], BigInteger.ZERO, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

        // Verify the account nonces and balances.
        Assert.assertEquals(BigInteger.ONE, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(contract));
        Assert.assertEquals(senderBalance.subtract(energyCost), state.getBalance(sender));
        Assert.assertEquals(beneficiaryBalance, state.getBalance(contract));

        // Call GET_TICKER, to grab the value we've incremented.
        data = encodeCallToTickerContract(TickerFunction.GET_TICKER);
        transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], BigInteger.ONE, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

        // Verify the output of the contract, which is the counter that should be set to 1.
        Assert.assertEquals(1, FvmDataWord.fromBytes(result.copyOfTransactionOutput().orElse(new byte[0])).toInt());

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
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

        AionAddress contract = capabilities.computeNewContractAddress(sender, senderNonce);

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
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

        AionAddress contract = capabilities.computeNewContractAddress(sender, senderNonce);

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
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Destination has account state, but is not a contract.
        AionAddress contract = capabilities.computeNewContractAddress(sender, senderNonce);
        state.addBalance(contract, beneficiaryBalance);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true).result;
        Assert.assertTrue(result.transactionStatus.isSuccess());

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
        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], senderNonce, value, data, energyLimit, energyPrice);

        // Run the transaction.
        FvmWrappedTransactionResult wrappedResult = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false);
        Assert.assertTrue(wrappedResult.result.transactionStatus.isReverted());

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
        Transaction transaction = Transaction.contractCallTransaction(sender, contract, new byte[32], senderNonce, value, data, energyLimit, energyPrice);

        // Run the transaction.
        FvmWrappedTransactionResult wrappedResult = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true);
        Assert.assertTrue(wrappedResult.result.transactionStatus.isReverted());

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
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        AionAddress destination = capabilities.computeNewContractAddress(sender, senderNonce);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        Assert.assertTrue(result.transactionStatus.isFailed());

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
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        AionAddress destination = capabilities.computeNewContractAddress(sender, senderNonce);

        // Run the transaction.
        FvmWrappedTransactionResult wrappedResult = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, true);
        Assert.assertTrue(wrappedResult.result.transactionStatus.isFailed());

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
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        AionAddress destination = capabilities.computeNewContractAddress(sender, senderNonce);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        TransactionStatus status = TransactionResultUtil.transactionStatusFromFvmResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
        Assert.assertEquals(status, result.transactionStatus);

        // Verify the destination has no code.
        Assert.assertNull(state.getCode(destination));

        // Verify the account nonces did not change.
        Assert.assertEquals(senderNonce, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the account balances did not change.
        Assert.assertEquals(senderBalance, state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    @Test
    public void testContractDeployRejectedRunBeforeUnity() {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        byte[] data = RandomUtils.nextBytes(3110);

        long energyLimit = TransactionUtil.computeTransactionCost(true, data);
        long energyPrice = 4L;

        // Set up the accounts. The sender does not have enough energy limit to cover the transaction.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.subtract(BigInteger.ONE);

        ExternalStateForTesting state = newState(true);
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a nonsensical contract, this is why we fail!
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        AionAddress destination = capabilities.computeNewContractAddress(sender, senderNonce);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        TransactionStatus status = TransactionResultUtil.transactionStatusFromFvmResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
        Assert.assertEquals(status, result.transactionStatus);

        // Verify the destination has no code.
        Assert.assertNull(state.getCode(destination));

        // Verify the account nonces did not change.
        Assert.assertEquals(senderNonce, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the account balances did not change.
        Assert.assertEquals(senderBalance, state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    @Test
    public void testContractDeployRejectedRunAfterUnity() {
        AionAddress sender = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        byte[] data = RandomUtils.nextBytes(3110);

        long energyLimit = TransactionUtil.computeTransactionCost(true, data) - 1;
        long energyPrice = 4L;

        // Set up the accounts. The sender does not have enough energy limit to cover the transaction.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.subtract(BigInteger.ONE);

        ExternalStateForTesting state = newState(true);
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a nonsensical contract, this is why we fail!
        Transaction transaction = Transaction.contractCreateTransaction(sender, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        AionAddress destination = capabilities.computeNewContractAddress(sender, senderNonce);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        TransactionStatus status = TransactionResultUtil.transactionStatusFromFvmResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
        Assert.assertEquals(status, result.transactionStatus);

        // Verify the destination has no code.
        Assert.assertNull(state.getCode(destination));

        // Verify the account nonces did not change.
        Assert.assertEquals(senderNonce, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the account balances did not change.
        Assert.assertEquals(senderBalance, state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    @Test
    public void testTransactionRejectedRunBeforeUnity() {
        AionAddress sender = randomAddress();
        AionAddress destination = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        byte[] data = RandomUtils.nextBytes(3110);

        long energyLimit = TransactionUtil.computeTransactionCost(false, data);
        long energyPrice = 4L;

        // Set up the accounts. The sender does not have enough energy limit to cover the transaction.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.subtract(BigInteger.ONE);

        ExternalStateForTesting state = newState(true);
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a nonsensical contract, this is why we fail!
        Transaction transaction = Transaction.contractCallTransaction(sender, destination, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        TransactionStatus status = TransactionResultUtil.transactionStatusFromFvmResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
        Assert.assertEquals(status, result.transactionStatus);

        // Verify the account nonces did not change.
        Assert.assertEquals(senderNonce, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the account balances did not change.
        Assert.assertEquals(senderBalance, state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    @Test
    public void testTransactionRejectedRunAfterUnity() {
        AionAddress sender = randomAddress();
        AionAddress destination = randomAddress();
        BigInteger senderNonce = BigInteger.valueOf(12);
        byte[] data = RandomUtils.nextBytes(3110);

        long energyLimit = TransactionUtil.computeTransactionCost(false, data) - 1;
        long energyPrice = 4L;

        // Set up the accounts. The sender does not have enough energy limit to cover the transaction.
        BigInteger energyCost = BigInteger.valueOf(energyLimit).multiply(BigInteger.valueOf(energyPrice));
        BigInteger senderBalance = energyCost.subtract(BigInteger.ONE);

        ExternalStateForTesting state = newState(true);
        state.setNonce(sender, senderNonce);
        state.addBalance(sender, senderBalance);

        // We attempt to deploy a nonsensical contract, this is why we fail!
        Transaction transaction = Transaction.contractCallTransaction(sender, destination, new byte[32], senderNonce, BigInteger.ZERO, data, energyLimit, energyPrice);

        // Run the transaction.
        TransactionResult result = FastVirtualMachine.run(state, new ExternalCapabilitiesForTesting(), transaction, false).result;
        TransactionStatus status = TransactionResultUtil.transactionStatusFromFvmResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
        Assert.assertEquals(status, result.transactionStatus);

        // Verify the account nonces did not change.
        Assert.assertEquals(senderNonce, state.getNonce(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getNonce(destination));

        // Verify the account balances did not change.
        Assert.assertEquals(senderBalance, state.getBalance(sender));
        Assert.assertEquals(BigInteger.ZERO, state.getBalance(destination));
    }

    private static byte[] encodeCallToDefaultPayableContract(DefaultPayableFunction function) {
        switch (function) {
            case NON_PAYABLE: return HexUtil.hexStringToBytes("52da86d5");
            default: throw new IllegalArgumentException("Expected: NON_PAYABLE. Found: " + function);
        }
    }

    private static byte[] encodeCallToTickerContract(TickerFunction function) {
        switch (function) {
            case TICKING: return HexUtil.hexStringToBytes("dae29f29");
            case GET_TICKER: return HexUtil.hexStringToBytes("c0004213");
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
        return newState(false);
    }

    private static ExternalStateForTesting newState(boolean afterUnity) {
        return new ExternalStateForTesting(RepositoryForTesting.newRepository(), new BlockchainForTesting(), randomAddress(), FvmDataWord.fromLong(0L), false, true, false, 0L, 0L, 15_000_000L, afterUnity);
    }

    private static AionAddress randomAddress() {
        byte[] bytes = RandomUtils.nextBytes(AionAddress.LENGTH);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }
}
