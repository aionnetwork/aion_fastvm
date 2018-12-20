package org.aion.fastvm;

import java.math.BigInteger;
import java.util.List;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.SimpleFuture;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.vm.api.interfaces.VirtualMachine;
import org.aion.zero.types.AionTransaction;

public class FastVirtualMachine implements VirtualMachine {

    // All state updates for any transactions run in bulk here are parented by this snapshot, which
    // is never released to the above caller, so that the caller recieves the correct historical
    // updates in each separately returned KernelInterface and must flushTo its intended repository.
    private KernelInterface kernelSnapshot;

    @Override
    public void setKernelInterface(KernelInterface kernel) {
        if (kernel == null) {
            throw new NullPointerException("Cannot set null KernelInterface.");
        }
        this.kernelSnapshot = kernel.makeChildKernelInterface();
    }

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
    public SimpleFuture<TransactionResult>[] run(TransactionContext[] contexts) {
        FastVmSimpleFuture<TransactionResult>[] transactionResults = new FastVmSimpleFuture[contexts.length];

        for (int i = 0; i < contexts.length; i++) {
            TransactionExecutor executor = new TransactionExecutor(contexts[i].getTransaction(), contexts[i], this.kernelSnapshot.makeChildKernelInterface());
            transactionResults[i] = new FastVmSimpleFuture();
            transactionResults[i].result = executor.execute();

            // We want to flush back up to the snapshot without losing any state, so that we can pass that state to the caller.
            ((KernelInterfaceForFastVM) transactionResults[i].result.getKernelInterface())
                .getRepositoryCache()
                .flushTo(((KernelInterfaceForFastVM) this.kernelSnapshot).getRepositoryCache(), false);

            // Mock the updateRepo call
            TransactionResult txResult = transactionResults[i].result;
            AionTransaction transaction = (AionTransaction) contexts[i].getTransaction();
            Address miner = contexts[i].getMinerAddress();
            List<Address> accountsToDelete = contexts[i].getSideEffects().getAddressesToBeDeleted();

            updateSnapshot(txResult, transaction, miner, accountsToDelete);
        }

        return transactionResults;
    }

    private void updateSnapshot(TransactionResult txResult, AionTransaction tx, Address coinbase, List<Address> deleteAccounts) {
        if (!txResult.getResultCode().isRejected()) {
            KernelInterface snapshotTracker = this.kernelSnapshot.makeChildKernelInterface();

            long energyUsed = computeEnergyUsed(tx.getEnergyLimit(), txResult);

            // Refund energy if transaction was successfully or reverted.
            if (txResult.getResultCode().isSuccess() || txResult.getResultCode().isRevert()) {
                snapshotTracker.refundAccount(tx.getSenderAddress(), computeRefundForSender(tx, energyUsed));
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

    private class FastVmSimpleFuture<R> implements SimpleFuture {
        private R result;

        @Override
        public R get() {
            return this.result;
        }

    }

}
