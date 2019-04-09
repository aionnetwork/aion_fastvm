package org.aion.fastvm;

import java.math.BigInteger;
import java.util.List;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.tx.Transaction;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.types.Address;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.SimpleFuture;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionInterface;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.vm.api.interfaces.VirtualMachine;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.ArrayUtils;

public class FastVirtualMachine implements VirtualMachine {

    // All state updates for any transactions run in bulk here are parented by this snapshot, which
    // is never released to the above caller, so that the caller recieves the correct historical
    // updates in each separately returned KernelInterface and must flushTo its intended repository.
    private KernelInterface kernelSnapshot;

    @Override
    public void start() {
        throw new UnsupportedOperationException("The FastVirtualMachine is not long-lived.");
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("The FastVirtualMachine is not long-lived.");
    }

    /**
     * Runs the transactions provided by the contexts and returns the results. Each result is
     * guaranteed to contain a {@link KernelInterface} such that its state changes are historical
     * (any previous state changes by other transactions in this batch do belong to its history) and
     * are immediately available to be consumed by some other repository without any checks.
     */
    @Override
    public SimpleFuture<TransactionResult>[] run(
            KernelInterface kernel, TransactionInterface[] transactions) {
        if (kernel == null) {
            throw new NullPointerException("Cannot set null KernelInterface.");
        }

        TransactionContext[] contexts = new TransactionContext[transactions.length];
        for (int i = 0; i < transactions.length; i++) {
            contexts[i] = constructTransactionContext(transactions[i], kernel);
        }

        this.kernelSnapshot = kernel.makeChildKernelInterface();

        FastVmSimpleFuture<TransactionResult>[] transactionResults =
                new FastVmSimpleFuture[contexts.length];

        boolean fork040Enable = ((KernelInterfaceForFastVM) kernel).isFork040Enable();
        for (int i = 0; i < contexts.length; i++) {

            TransactionExecutor executor =
                    new TransactionExecutor(
                            (Transaction) contexts[i].getTransaction(),
                            contexts[i],
                            this.kernelSnapshot.makeChildKernelInterface(),
                            fork040Enable);

            transactionResults[i] = new FastVmSimpleFuture();
            transactionResults[i].setResult(executor.execute());

            // We want to flush back up to the snapshot without losing any state, so that we can
            // pass that state to the caller.
            KernelInterfaceForFastVM fvmKernel =
                    (KernelInterfaceForFastVM) transactionResults[i].result.getKernelInterface();
            RepositoryCache fvmKernelRepo = fvmKernel.getRepositoryCache();
            KernelInterfaceForFastVM snapshotKernel =
                    (KernelInterfaceForFastVM) this.kernelSnapshot;
            fvmKernelRepo.flushCopiesTo(snapshotKernel.getRepositoryCache(), false);

            // Mock the updateRepo call
            TransactionResult txResult = transactionResults[i].result;
            AionTransaction transaction = (AionTransaction) contexts[i].getTransaction();
            Address miner = contexts[i].getMinerAddress();
            List<Address> accountsToDelete = contexts[i].getSideEffects().getAddressesToBeDeleted();

            updateSnapshot(txResult, transaction, miner, accountsToDelete);
            txResult.getSideEffects().merge(contexts[i].getSideEffects());
        }

        return transactionResults;
    }

    private void updateSnapshot(
            TransactionResult txResult,
            AionTransaction tx,
            Address coinbase,
            List<Address> deleteAccounts) {
        if (!txResult.getResultCode().isRejected()) {
            KernelInterface snapshotTracker = this.kernelSnapshot.makeChildKernelInterface();

            long energyUsed = computeEnergyUsed(tx.getEnergyLimit(), txResult);

            // Refund energy if transaction was successfully or reverted.
            if (txResult.getResultCode().isSuccess() || txResult.getResultCode().isRevert()) {
                snapshotTracker.refundAccount(
                        tx.getSenderAddress(), computeRefundForSender(tx, energyUsed));
            }

            // Pay the miner.
            snapshotTracker.payMiningFee(coinbase, computeMiningFee(tx, energyUsed));

            // Delete any accounts marked for deletion.
            if (txResult.getResultCode().isSuccess()) {
                for (Address addr : deleteAccounts) {
                    snapshotTracker.deleteAccount(addr);
                }
            }
            snapshotTracker.commit();
        }
    }

    private long computeEnergyUsed(long limit, TransactionResult result) {
        return limit - result.getEnergyRemaining();
    }

    private BigInteger computeRefundForSender(AionTransaction transaction, long energyUsed) {
        BigInteger energyLimit = BigInteger.valueOf(transaction.getEnergyLimit());
        BigInteger energyConsumed = BigInteger.valueOf(energyUsed);
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        return energyLimit.subtract(energyConsumed).multiply(energyPrice);
    }

    private BigInteger computeMiningFee(AionTransaction transaction, long energyUsed) {
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        BigInteger energyConsumed = BigInteger.valueOf(energyUsed);
        return energyConsumed.multiply(energyPrice);
    }

    private ExecutionContext constructTransactionContext(
        TransactionInterface transaction, KernelInterface kernel) {
        byte[] txHash = transaction.getTransactionHash();
        Address address =
                transaction.isContractCreationTransaction()
                        ? transaction.getContractAddress()
                        : transaction.getDestinationAddress();
        Address origin = transaction.getSenderAddress();
        Address caller = transaction.getSenderAddress();

        DataWordImpl nrgPrice = new DataWordImpl(transaction.getEnergyPrice());
        long nrg = transaction.getEnergyLimit() - transaction.getTransactionCost();
        DataWordImpl callValue = new DataWordImpl(ArrayUtils.nullToEmpty(transaction.getValue()));
        byte[] callData = ArrayUtils.nullToEmpty(transaction.getData());

        int depth = 0;
        int kind =
                transaction.isContractCreationTransaction()
                        ? ExecutionContext.CREATE
                        : ExecutionContext.CALL;
        int flags = 0;

        Address blockCoinbase = kernel.getMinerAddress();
        long blockNumber = kernel.getBlockNumber();
        long blockTimestamp = kernel.getBlockTimestamp();
        long blockNrgLimit = kernel.getBlockEnergyLimit();
        DataWordImpl blockDifficulty = new DataWordImpl(kernel.getBlockDifficulty());

        return new ExecutionContext(
                transaction,
                txHash,
                address,
                origin,
                caller,
                nrgPrice,
                nrg,
                callValue,
                callData,
                depth,
                kind,
                flags,
                blockCoinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockDifficulty);
    }

    private class FastVmSimpleFuture<R> implements SimpleFuture {
        private R result;

        private void setResult(R result) {
            this.result = result;
        }

        @Override
        public R get() {
            return this.result;
        }
    }
}
