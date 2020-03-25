package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.fastvm.util.TransactionResultUtil;
import org.aion.fastvm.util.TransactionUtil;
import org.apache.commons.lang3.ArrayUtils;

public final class FastVirtualMachine {
    private static final Object FVM_LOCK = new Object();

    /**
     * Returns the result of executing the specified transaction.
     *
     * <p>Any state changes that occur during execution will be committed to the provided kernel.
     * 
     * <p>This method is thread-safe.
     *
     * @param externalState The world state.
     * @param transaction The transaction to run.
     * @return the execution result.
     */
    public static FvmWrappedTransactionResult run(
            IExternalStateForFvm externalState, IExternalCapabilities capabilities, Transaction transaction, boolean isFork040enabled) {
        if (externalState == null) {
            throw new NullPointerException("Cannot run using a null externalState!");
        }
        if (transaction == null) {
            throw new NullPointerException("Cannot run null transaction!");
        }

        AionAddress contract = (transaction.isCreate) ? capabilities.computeNewContractAddress(transaction.senderAddress, transaction.nonce) : null;
        ExecutionContext context = constructTransactionContext(transaction, contract, externalState);
        IExternalStateForFvm childExternalState = externalState.newChildExternalState();
        IExternalStateForFvm grandChildExternalState = childExternalState.newChildExternalState();

        FastVmTransactionResult result =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS,
                        transaction.energyLimit - TransactionUtil.computeTransactionCost(transaction));

        // Perform the rejection checks and return immediately if transaction is rejected.
        performRejectionChecks(childExternalState, transaction, result);
        if (!result.getResultCode().isSuccess()) {
            return TransactionResultUtil.createWithCodeAndEnergyRemaining(
                    result.getResultCode(),
                    transaction.energyLimit - result.getEnergyRemaining());
        }

        incrementNonceAndDeductEnergyCost(childExternalState, transaction);

        if (transaction.isCreate) {
            result =
                    runContractCreationTransaction(
                            new FastVM(), grandChildExternalState, capabilities, context, transaction, result, isFork040enabled);
        } else {
            result =
                    runNonContractCreationTransaction(
                            new FastVM(), grandChildExternalState, capabilities, context, transaction, result, isFork040enabled);
        }

        // If the execution was successful then we can safely commit any changes in the grandChild
        // up to the child kernel.
        if (result.getResultCode().isSuccess()) {
            grandChildExternalState.commit();
        }

        // If the execution was not rejected then we can safely commit any changes in the child
        // kernel up to its parent.
        if (!result.getResultCode().isRejected()) {
            childExternalState.commit();
        }

        // Propagate any side-effects.
        SideEffects sideEffects = context.getSideEffects();

        return TransactionResultUtil.createFvmWrappedTransactionResult(
                result.getResultCode(),
                sideEffects.getInternalTransactions(),
                sideEffects.getExecutionLogs(),
                transaction.energyLimit - result.getEnergyRemaining(),
                result.getReturnData(),
                sideEffects.getAddressesToBeDeleted());
    }

    /**
     * Returns the result of executing the transaction whose context is given by the specified
     * context.
     *
     * <p>The assumption of this method is that the transaction in the provided context is a
     * contract create transaction!
     *
     * <p>This method does not commit any changes in the provided kernel, it is the responsibility
     * of the caller to evaluate the returned result and determine how to proceed with the state
     * changes.
     *
     * @param externalState The world state.
     * @param context The transaction context.
     * @param transaction The transaction.
     * @param result The current state of the transaction result.
     * @param isFork040enabled Whether or not the 0.4.0 fork is enabled.
     * @return the result of executing the transaction.
     */
    public static FastVmTransactionResult runContractCreationTransaction(
            IFastVm fvm,
            IExternalStateForFvm externalState,
            IExternalCapabilities capabilities,
            ExecutionContext context,
            Transaction transaction,
            FastVmTransactionResult result,
            boolean isFork040enabled) {
        AionAddress contractAddress = capabilities.computeNewContractAddress(transaction.senderAddress, transaction.nonce);

        // If the destination address already has state, we attempt to overwrite this address as a
        // contract if possible.
        if (externalState.hasAccountState(contractAddress)) {
            // Prior to this fork this situation always resulted in a failure.
            if (!isFork040enabled) {
                result.setResultCode(FastVmResultCode.FAILURE);
                result.setEnergyRemaining(0);
                return result;
            }

            // We cannot overwrite the address if it is a contract address already.
            byte[] code = externalState.getCode(contractAddress);
            if (code != null && code.length > 0) {
                result.setResultCode(FastVmResultCode.FAILURE);
                result.setEnergyRemaining(0);
                return result;
            }

            BigInteger nonce = externalState.getNonce(contractAddress);
            if (!nonce.equals(BigInteger.ZERO)) {
                result.setResultCode(FastVmResultCode.FAILURE);
                result.setEnergyRemaining(0);
                return result;
            }

            if (externalState.hasStorage(contractAddress)) {
                result.setResultCode(FastVmResultCode.FAILURE);
                result.setEnergyRemaining(0);
                return result;
            }
        } else {
            externalState.createAccount(contractAddress);
        }

        externalState.setVmType(contractAddress);

        // Execute the transaction.
        FastVmTransactionResult newResult = null;
        if (!ArrayUtils.isEmpty(transaction.copyOfTransactionData())) {

            synchronized (FVM_LOCK) {
                // Install the external capabilities for the fvm to use.
                CapabilitiesProvider.installExternalCapabilities(capabilities);

                if (isFork040enabled) {
                    newResult = fvm.runPost040Fork(transaction.copyOfTransactionData(), context, externalState);
                } else {
                    newResult = fvm.runPre040Fork(transaction.copyOfTransactionData(), context, externalState);
                }

                // Remove the newly installed capabilities.
                CapabilitiesProvider.removeExternalCapabilities();
            }

            // If the deployment succeeded, then save the contract's code.
            if (newResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
                externalState.putCode(contractAddress, newResult.getReturnData());
            }
        }

        // Transfer any specified value from the sender to the contract.
        externalState.addBalance(transaction.senderAddress, transaction.value.negate());
        externalState.addBalance(contractAddress, transaction.value);

        return (newResult == null) ? result : newResult;
    }

    /**
     * Returns the result of executing the transaction whose context is given by the specified
     * context.
     *
     * <p>The assumption of this method is that the transaction in the provided context is a
     * contract call transaction!
     *
     * <p>This method does not commit any changes in the provided kernel, it is the responsibility
     * of the caller to evaluate the returned result and determine how to proceed with the state
     * changes.
     *
     * @param externalState The world state.
     * @param context The transaction context.
     * @param transaction The transaction.
     * @param result The current state of the transaction result.
     * @param isFork040enabled Whether or not the 0.4.0 fork is enabled.
     * @return the result of executing the transaction.
     */
    public static FastVmTransactionResult runNonContractCreationTransaction(
            IFastVm fvm,
            IExternalStateForFvm externalState,
            IExternalCapabilities capabilities,
            ExecutionContext context,
            Transaction transaction,
            FastVmTransactionResult result,
            boolean isFork040enabled) {

        // Execute the transaction.
        FastVmTransactionResult newResult = null;
        byte[] code = externalState.getCode(transaction.destinationAddress);
        if (!ArrayUtils.isEmpty(code)) {

            synchronized (FVM_LOCK) {
                // Install the external capabilities for the fvm to use.
                CapabilitiesProvider.installExternalCapabilities(capabilities);

                if (isFork040enabled) {
                    newResult = fvm.runPost040Fork(code, context, externalState);
                } else {
                    newResult = fvm.runPre040Fork(code, context, externalState);
                }

                // Remove the newly installed capabilities.
                CapabilitiesProvider.removeExternalCapabilities();
            }
        }

        // Transfer any specified value from the sender to the recipient.
        externalState.addBalance(transaction.senderAddress, transaction.value.negate());
        externalState.addBalance(transaction.destinationAddress, transaction.value);

        return (newResult == null) ? result : newResult;
    }

    /**
     * Increments the nonce of the sender of the transaction and deducts the energy cost from the
     * sender's account as well. The energy cost is equal to the energy limit multiplied by the
     * energy price.
     *
     * <p>These state changes are made directly in the given kernel.
     *
     * @param externalState The world state.
     * @param transaction The transaction.
     */
    public static void incrementNonceAndDeductEnergyCost(
            IExternalStateForFvm externalState, Transaction transaction) {
        IExternalStateForFvm childExternalState = externalState.newChildExternalState();
        childExternalState.incrementNonce(transaction.senderAddress);
        BigInteger energyLimit = BigInteger.valueOf(transaction.energyLimit);
        BigInteger energyPrice = BigInteger.valueOf(transaction.energyPrice);
        BigInteger energyCost = energyLimit.multiply(energyPrice);
        childExternalState.deductEnergyCost(transaction.senderAddress, energyCost);
        childExternalState.commit();
    }

    /**
     * Returns a SUCCESS result only if the specified transaction is not to be rejected.
     *
     * <p>Otherwise, returns a REJECTED result with the appropriate error cause specified.
     *
     * @param externalState The world state.
     * @param transaction The transaction to verify.
     * @param result The current state of the transaction result.
     */
    public static void performRejectionChecks(
            IExternalStateForFvm externalState, Transaction transaction, FastVmTransactionResult result) {
        BigInteger energyPrice = BigInteger.valueOf(transaction.energyPrice);
        long energyLimit = transaction.energyLimit;

        if (transaction.isCreate) {
            if (!externalState.isValidEnergyLimitForCreate(energyLimit, transaction.copyOfTransactionData())) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        } else {
            if (!externalState.isValidEnergyLimitForNonCreate(energyLimit, transaction.copyOfTransactionData())) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        }

        if (!externalState.accountNonceEquals(transaction.senderAddress, transaction.nonce)) {
            result.setResultCode(FastVmResultCode.INVALID_NONCE);
            result.setEnergyRemaining(energyLimit);
            return;
        }

        BigInteger transactionCost =
                energyPrice.multiply(BigInteger.valueOf(energyLimit)).add(transaction.value);
        if (!externalState.accountBalanceIsAtLeast(transaction.senderAddress, transactionCost)) {
            result.setResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
            result.setEnergyRemaining(energyLimit);
        }
    }

    /**
     * Returns an execution context pertaining to the specified transaction and kernel.
     *
     * @param transaction The transaction.
     * @param contract The contract.
     * @param externalState The world state.
     * @return the execution context.
     */
    public static ExecutionContext constructTransactionContext(
            Transaction transaction, AionAddress contract, IExternalStateForFvm externalState) {
        return ExecutionContext.fromTransaction(transaction, contract, externalState.getMinerAddress(), externalState.getBlockNumber(), externalState.getBlockTimestamp(), externalState.getBlockEnergyLimit(), externalState.getBlockDifficulty());
    }
}
