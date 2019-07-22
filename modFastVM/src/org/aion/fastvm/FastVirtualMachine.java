package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.base.AionTransaction;
import org.aion.types.AionAddress;
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
    public static FastVmTransactionResult run(
            IExternalStateForFvm externalState, AionTransaction transaction, boolean isFork040enabled) {
        if (externalState == null) {
            throw new NullPointerException("Cannot run using a null externalState!");
        }
        if (transaction == null) {
            throw new NullPointerException("Cannot run null transaction!");
        }

        ExecutionContext context = constructTransactionContext(transaction, externalState);
        IExternalStateForFvm childExternalState = externalState.newChildExternalState();
        IExternalStateForFvm grandChildExternalState = childExternalState.newChildExternalState();

        FastVmTransactionResult result =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS,
                        transaction.getEnergyLimit() - transaction.getTransactionCost());

        // Perform the rejection checks and return immediately if transaction is rejected.
        performRejectionChecks(childExternalState, transaction, result);
        if (!result.getResultCode().isSuccess()) {
            return result;
        }

        incrementNonceAndDeductEnergyCost(childExternalState, transaction);

        if (transaction.isContractCreationTransaction()) {
            result =
                    runContractCreationTransaction(
                            new FastVM(), grandChildExternalState, context, result, isFork040enabled);
        } else {
            result =
                    runNonContractCreationTransaction(
                            new FastVM(), grandChildExternalState, context, result, isFork040enabled);
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
        result.addLogs(sideEffects.getExecutionLogs());
        result.addInternalTransactions(sideEffects.getInternalTransactions());
        result.addDeletedAddresses(sideEffects.getAddressesToBeDeleted());

        return result;
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
     * @param result The current state of the transaction result.
     * @param isFork040enabled Whether or not the 0.4.0 fork is enabled.
     * @return the result of executing the transaction.
     */
    public static FastVmTransactionResult runContractCreationTransaction(
            IFastVm fvm,
            IExternalStateForFvm externalState,
            ExecutionContext context,
            FastVmTransactionResult result,
            boolean isFork040enabled) {
        AionTransaction transaction = context.getTransaction();
        AionAddress contractAddress = transaction.getContractAddress();

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
        } else {
            externalState.createAccount(contractAddress);
        }

        externalState.setVmType(contractAddress);

        // Execute the transaction.
        FastVmTransactionResult newResult = null;
        if (!ArrayUtils.isEmpty(transaction.getData())) {

            synchronized (FVM_LOCK) {
                if (isFork040enabled) {
                    newResult = fvm.runPost040Fork(transaction.getData(), context, externalState);
                } else {
                    newResult = fvm.runPre040Fork(transaction.getData(), context, externalState);
                }
            }

            // If the deployment succeeded, then save the contract's code.
            if (newResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
                externalState.putCode(contractAddress, newResult.getReturnData());
            }
        }

        // Transfer any specified value from the sender to the contract.
        BigInteger transferValue = new BigInteger(1, transaction.getValue());
        externalState.addBalance(transaction.getSenderAddress(), transferValue.negate());
        externalState.addBalance(contractAddress, transferValue);

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
     * @param result The current state of the transaction result.
     * @param isFork040enabled Whether or not the 0.4.0 fork is enabled.
     * @return the result of executing the transaction.
     */
    public static FastVmTransactionResult runNonContractCreationTransaction(
            IFastVm fvm,
            IExternalStateForFvm externalState,
            ExecutionContext context,
            FastVmTransactionResult result,
            boolean isFork040enabled) {

        AionTransaction transaction = context.getTransaction();

        // Execute the transaction.
        FastVmTransactionResult newResult = null;
        byte[] code = externalState.getCode(transaction.getDestinationAddress());
        if (!ArrayUtils.isEmpty(code)) {

            synchronized (FVM_LOCK) {
                if (isFork040enabled) {
                    newResult = fvm.runPost040Fork(code, context, externalState);
                } else {
                    newResult = fvm.runPre040Fork(code, context, externalState);
                }
            }
        }

        // Transfer any specified value from the sender to the recipient.
        BigInteger transferValue = new BigInteger(1, transaction.getValue());
        externalState.addBalance(transaction.getSenderAddress(), transferValue.negate());
        externalState.addBalance(transaction.getDestinationAddress(), transferValue);

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
            IExternalStateForFvm externalState, AionTransaction transaction) {
        IExternalStateForFvm childExternalState = externalState.newChildExternalState();
        childExternalState.incrementNonce(transaction.getSenderAddress());
        BigInteger energyLimit = BigInteger.valueOf(transaction.getEnergyLimit());
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        BigInteger energyCost = energyLimit.multiply(energyPrice);
        childExternalState.deductEnergyCost(transaction.getSenderAddress(), energyCost);
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
     * @return the rejection-check result.
     */
    public static void performRejectionChecks(
            IExternalStateForFvm externalState, AionTransaction transaction, FastVmTransactionResult result) {
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        long energyLimit = transaction.getEnergyLimit();

        if (transaction.isContractCreationTransaction()) {
            if (!externalState.isValidEnergyLimitForCreate(energyLimit)) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        } else {
            if (!externalState.isValidEnergyLimitForNonCreate(energyLimit)) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        }

        BigInteger txNonce = new BigInteger(1, transaction.getNonce());
        if (!externalState.accountNonceEquals(transaction.getSenderAddress(), txNonce)) {
            result.setResultCode(FastVmResultCode.INVALID_NONCE);
            result.setEnergyRemaining(0);
            return;
        }

        BigInteger transferValue = new BigInteger(1, transaction.getValue());
        BigInteger transactionCost =
                energyPrice.multiply(BigInteger.valueOf(energyLimit)).add(transferValue);
        if (!externalState.accountBalanceIsAtLeast(transaction.getSenderAddress(), transactionCost)) {
            result.setResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
            result.setEnergyRemaining(0);
        }
    }

    /**
     * Returns an execution context pertaining to the specified transaction and kernel.
     *
     * @param transaction The transaction.
     * @param externalState The world state.
     * @return the execution context.
     */
    public static ExecutionContext constructTransactionContext(
            AionTransaction transaction, IExternalStateForFvm externalState) {
        //TODO: AKI-288 difficulty is capped as a long, this is probably not what we want, especially for Unity?
        return ExecutionContext.fromTransaction(transaction, externalState.getMinerAddress(), externalState.getBlockNumber(), externalState.getBlockTimestamp(), externalState.getBlockEnergyLimit(), FvmDataWord.fromLong(externalState.getBlockDifficulty()));
    }
}
