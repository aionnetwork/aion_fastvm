package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.base.AionTransaction;
import org.aion.mcf.types.KernelInterface;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.apache.commons.lang3.ArrayUtils;

public final class FastVirtualMachine {

    /**
     * Returns the result of executing the specified transaction.
     *
     * <p>Any state changes that occur during execution will be committed to the provided kernel.
     *
     * @param kernel The kernel.
     * @param transaction The transaction to run.
     * @return the execution result.
     */
    public static FastVmTransactionResult run(
            KernelInterface kernel, AionTransaction transaction, boolean isFork040enabled) {
        if (kernel == null) {
            throw new NullPointerException("Cannot run using a null kernel!");
        }
        if (transaction == null) {
            throw new NullPointerException("Cannot run null transaction!");
        }

        ExecutionContext context = constructTransactionContext(transaction, kernel);
        KernelInterface childKernel = kernel.makeChildKernelInterface();
        KernelInterface grandChildKernel = childKernel.makeChildKernelInterface();

        FastVmTransactionResult result =
                new FastVmTransactionResult(
                        FastVmResultCode.SUCCESS,
                        transaction.getEnergyLimit() - transaction.getTransactionCost());

        // Perform the rejection checks and return immediately if transaction is rejected.
        performRejectionChecks(childKernel, transaction, result);
        if (!result.getResultCode().isSuccess()) {
            return result;
        }

        incrementNonceAndDeductEnergyCost(childKernel, transaction);

        if (transaction.isContractCreationTransaction()) {
            result =
                    runContractCreationTransaction(
                            grandChildKernel, context, result, isFork040enabled);
        } else {
            result =
                    runNonContractCreationTransaction(
                            grandChildKernel, context, result, isFork040enabled);
        }

        // If the execution was successful then we can safely commit any changes in the grandChild
        // up to the child kernel.
        if (result.getResultCode().isSuccess()) {
            grandChildKernel.commit();
        }

        // If the execution was not rejected then we can safely commit any changes in the child
        // kernel up to its parent.
        if (!result.getResultCode().isRejected()) {
            childKernel.commit();
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
     * @param kernel The kernel.
     * @param context The transaction context.
     * @param result The current state of the transaction result.
     * @param isFork040enabled Whether or not the 0.4.0 fork is enabled.
     * @return the result of executing the transaction.
     */
    public static FastVmTransactionResult runContractCreationTransaction(
            KernelInterface kernel,
            ExecutionContext context,
            FastVmTransactionResult result,
            boolean isFork040enabled) {
        AionTransaction transaction = context.getTransaction();
        AionAddress contractAddress = transaction.getContractAddress();

        // If the destination address already has state, we attempt to overwrite this address as a
        // contract if possible.
        if (kernel.hasAccountState(contractAddress)) {
            // Prior to this fork this situation always resulted in a failure.
            if (!isFork040enabled) {
                result.setResultCode(FastVmResultCode.FAILURE);
                result.setEnergyRemaining(0);
                return result;
            }

            // We cannot overwrite the address if it is a contract address already.
            byte[] code = kernel.getCode(contractAddress);
            if (code != null && code.length > 0) {
                result.setResultCode(FastVmResultCode.FAILURE);
                result.setEnergyRemaining(0);
                return result;
            }
        } else {
            kernel.createAccount(contractAddress);
        }

        if (kernel instanceof KernelInterfaceForFastVM) {
            ((KernelInterfaceForFastVM) kernel).setVmType(contractAddress);
        }

        // Execute the transaction.
        FastVmTransactionResult newResult = null;
        if (!ArrayUtils.isEmpty(transaction.getData())) {
            if (isFork040enabled) {
                newResult = new FastVM().run_v1(transaction.getData(), context, kernel);
            } else {
                newResult = new FastVM().run(transaction.getData(), context, kernel);
            }

            // If the deployment succeeded, then save the contract's code.
            if (newResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
                kernel.putCode(contractAddress, newResult.getReturnData());
            }
        }

        // Transfer any specified value from the sender to the contract.
        BigInteger transferValue = new BigInteger(1, transaction.getValue());
        kernel.adjustBalance(transaction.getSenderAddress(), transferValue.negate());
        kernel.adjustBalance(contractAddress, transferValue);

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
     * @param kernel The kernel.
     * @param context The transaction context.
     * @param result The current state of the transaction result.
     * @param isFork040enabled Whether or not the 0.4.0 fork is enabled.
     * @return the result of executing the transaction.
     */
    public static FastVmTransactionResult runNonContractCreationTransaction(
            KernelInterface kernel,
            ExecutionContext context,
            FastVmTransactionResult result,
            boolean isFork040enabled) {
        ContractFactory precompiledFactory = new ContractFactory();
        PrecompiledContract precompiledContract =
                precompiledFactory.getPrecompiledContract(
                        toPrecompiledTransactionContext(context), kernel);

        AionTransaction transaction = context.getTransaction();

        // Execute the transaction as either a precompiled or fvm transaction.
        FastVmTransactionResult newResult = null;
        if (precompiledContract != null) {
            newResult =
                    precompiledToFvmResult(
                            precompiledContract.execute(
                                    transaction.getData(), context.getTransactionEnergy()));
        } else {
            byte[] code = kernel.getCode(transaction.getDestinationAddress());
            if (!ArrayUtils.isEmpty(code)) {
                if (isFork040enabled) {
                    newResult = new FastVM().run_v1(code, context, kernel);
                } else {
                    newResult = new FastVM().run(code, context, kernel);
                }
            }
        }

        // Transfer any specified value from the sender to the recipient.
        BigInteger transferValue = new BigInteger(1, transaction.getValue());
        kernel.adjustBalance(transaction.getSenderAddress(), transferValue.negate());
        kernel.adjustBalance(transaction.getDestinationAddress(), transferValue);

        return (newResult == null) ? result : newResult;
    }

    /**
     * Increments the nonce of the sender of the transaction and deducts the energy cost from the
     * sender's account as well. The energy cost is equal to the energy limit multiplied by the
     * energy price.
     *
     * <p>These state changes are made directly in the given kernel.
     *
     * @param kernel The kernel.
     * @param transaction The transaction.
     */
    public static void incrementNonceAndDeductEnergyCost(
            KernelInterface kernel, AionTransaction transaction) {
        KernelInterface childKernel = kernel.makeChildKernelInterface();
        childKernel.incrementNonce(transaction.getSenderAddress());
        BigInteger energyLimit = BigInteger.valueOf(transaction.getEnergyLimit());
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        BigInteger energyCost = energyLimit.multiply(energyPrice);
        childKernel.deductEnergyCost(transaction.getSenderAddress(), energyCost);
        childKernel.commit();
    }

    /**
     * Returns a SUCCESS result only if the specified transaction is not to be rejected.
     *
     * <p>Otherwise, returns a REJECTED result with the appropriate error cause specified.
     *
     * @param kernel The kernel.
     * @param transaction The transaction to verify.
     * @param result The current state of the transaction result.
     * @return the rejection-check result.
     */
    public static void performRejectionChecks(
            KernelInterface kernel, AionTransaction transaction, FastVmTransactionResult result) {
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        long energyLimit = transaction.getEnergyLimit();

        if (transaction.isContractCreationTransaction()) {
            if (!kernel.isValidEnergyLimitForCreate(energyLimit)) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        } else {
            if (!kernel.isValidEnergyLimitForNonCreate(energyLimit)) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        }

        BigInteger txNonce = new BigInteger(1, transaction.getNonce());
        if (!kernel.accountNonceEquals(transaction.getSenderAddress(), txNonce)) {
            result.setResultCode(FastVmResultCode.INVALID_NONCE);
            result.setEnergyRemaining(0);
            return;
        }

        BigInteger transferValue = new BigInteger(1, transaction.getValue());
        BigInteger transactionCost =
                energyPrice.multiply(BigInteger.valueOf(energyLimit)).add(transferValue);
        if (!kernel.accountBalanceIsAtLeast(transaction.getSenderAddress(), transactionCost)) {
            result.setResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
            result.setEnergyRemaining(0);
        }
    }

    /**
     * Returns an execution context pertaining to the specified transaction and kernel.
     *
     * @param transaction The transaction.
     * @param kernel The kernel.
     * @return the execution context.
     */
    public static ExecutionContext constructTransactionContext(
            AionTransaction transaction, KernelInterface kernel) {
        byte[] transactionHash = transaction.getTransactionHash();
        AionAddress originAddress = transaction.getSenderAddress();
        AionAddress callerAddress = transaction.getSenderAddress();
        DataWordImpl energyPrice = new DataWordImpl(transaction.getEnergyPrice());
        long energyRemaining = transaction.getEnergyLimit() - transaction.getTransactionCost();
        DataWordImpl transferValue =
                new DataWordImpl(ArrayUtils.nullToEmpty(transaction.getValue()));
        byte[] data = ArrayUtils.nullToEmpty(transaction.getData());
        AionAddress minerAddress = kernel.getMinerAddress();
        long blockNumber = kernel.getBlockNumber();
        long blockTimestamp = kernel.getBlockTimestamp();
        long blockEnergyLimit = kernel.getBlockEnergyLimit();
        DataWordImpl blockDifficulty = new DataWordImpl(kernel.getBlockDifficulty());
        AionAddress destinationAddress =
                transaction.isContractCreationTransaction()
                        ? transaction.getContractAddress()
                        : transaction.getDestinationAddress();
        int kind =
                transaction.isContractCreationTransaction()
                        ? ExecutionContext.CREATE
                        : ExecutionContext.CALL;

        return new ExecutionContext(
                transaction,
                transactionHash,
                destinationAddress,
                originAddress,
                callerAddress,
                energyPrice,
                energyRemaining,
                transferValue,
                data,
                0,
                kind,
                0,
                minerAddress,
                blockNumber,
                blockTimestamp,
                blockEnergyLimit,
                blockDifficulty);
    }

    private static FastVmTransactionResult precompiledToFvmResult(
            PrecompiledTransactionResult precompiledResult) {
        FastVmTransactionResult fvmResult = new FastVmTransactionResult();

        fvmResult.addLogs(precompiledResult.getLogs());
        fvmResult.addInternalTransactions(precompiledResult.getInternalTransactions());
        fvmResult.addDeletedAddresses(precompiledResult.getDeletedAddresses());

        fvmResult.setEnergyRemaining(precompiledResult.getEnergyRemaining());
        fvmResult.setResultCode(precompiledToFvmResultCode(precompiledResult.getResultCode()));
        fvmResult.setReturnData(precompiledResult.getReturnData());
        fvmResult.setKernelInterface(precompiledResult.getKernelInterface());

        return fvmResult;
    }

    private static FastVmResultCode precompiledToFvmResultCode(
            PrecompiledResultCode precompiledResultCode) {
        switch (precompiledResultCode) {
            case BAD_JUMP_DESTINATION:
                return FastVmResultCode.BAD_JUMP_DESTINATION;
            case VM_INTERNAL_ERROR:
                return FastVmResultCode.VM_INTERNAL_ERROR;
            case STATIC_MODE_ERROR:
                return FastVmResultCode.STATIC_MODE_ERROR;
            case INVALID_NRG_LIMIT:
                return FastVmResultCode.INVALID_NRG_LIMIT;
            case STACK_UNDERFLOW:
                return FastVmResultCode.STACK_UNDERFLOW;
            case BAD_INSTRUCTION:
                return FastVmResultCode.BAD_INSTRUCTION;
            case STACK_OVERFLOW:
                return FastVmResultCode.STACK_OVERFLOW;
            case INVALID_NONCE:
                return FastVmResultCode.INVALID_NONCE;
            case VM_REJECTED:
                return FastVmResultCode.VM_REJECTED;
            case OUT_OF_NRG:
                return FastVmResultCode.OUT_OF_NRG;
            case SUCCESS:
                return FastVmResultCode.SUCCESS;
            case FAILURE:
                return FastVmResultCode.FAILURE;
            case REVERT:
                return FastVmResultCode.REVERT;
            case ABORT:
                return FastVmResultCode.ABORT;
            case INSUFFICIENT_BALANCE:
                return FastVmResultCode.INSUFFICIENT_BALANCE;
            case INCOMPATIBLE_CONTRACT_CALL:
                return FastVmResultCode.INCOMPATIBLE_CONTRACT_CALL;
            default:
                throw new IllegalStateException("Unknown code: " + precompiledResultCode);
        }
    }

    private static PrecompiledTransactionContext toPrecompiledTransactionContext(
            ExecutionContext context) {
        return new PrecompiledTransactionContext(
                context.getDestinationAddress(),
                context.getOriginAddress(),
                context.getSenderAddress(),
                context.getSideEffects().getExecutionLogs(),
                context.getSideEffects().getInternalTransactions(),
                context.getSideEffects().getAddressesToBeDeleted(),
                context.getHashOfOriginTransaction(),
                context.getTransactionHash(),
                context.getBlockNumber(),
                context.getTransactionEnergy(),
                context.getTransactionStackDepth());
    }
}
