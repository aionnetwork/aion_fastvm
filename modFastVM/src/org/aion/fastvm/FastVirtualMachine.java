package org.aion.fastvm;

import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.SimpleFuture;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.vm.api.interfaces.VirtualMachine;

public class FastVirtualMachine implements VirtualMachine {
    private KernelInterface kernel;

    @Override
    public void setKernelInterface(KernelInterface kernel) {
        if (kernel == null) {
            throw new NullPointerException("Cannot set null KernelInterface.");
        }
        this.kernel = kernel;
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("The FastVirtualMachine is not long-lived.");
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("The FastVirtualMachine is not long-lived.");
    }

    @Override
    public SimpleFuture<TransactionResult>[] run(TransactionContext[] contexts) {
        FastVmSimpleFuture<TransactionResult>[] transactionResults = new FastVmSimpleFuture[contexts.length];

        for (int i = 0; i < contexts.length; i++) {
            TransactionExecutor executor = new TransactionExecutor(contexts[i].getTransaction(), contexts[i], this.kernel);
            transactionResults[i] = new FastVmSimpleFuture();
            transactionResults[i].result = executor.execute();
        }

        return transactionResults;
    }

    private class FastVmSimpleFuture<R> implements SimpleFuture {
        private R result;

        @Override
        public R get() {
            return this.result;
        }

    }

}
