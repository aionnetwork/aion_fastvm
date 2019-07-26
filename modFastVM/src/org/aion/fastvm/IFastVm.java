package org.aion.fastvm;

public interface IFastVm {

    /**
     * The execution point prior to fork 0.4.0.
     */
    FastVmTransactionResult runPre040Fork(byte[] code, ExecutionContext context, IExternalStateForFvm state);

    /**
     * The execution point from fork 0.4.0 onwards.
     */
    FastVmTransactionResult runPost040Fork(byte[] code, ExecutionContext context, IExternalStateForFvm state);
}
