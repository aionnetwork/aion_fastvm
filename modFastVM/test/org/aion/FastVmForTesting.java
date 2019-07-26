package org.aion;

import org.aion.fastvm.ExecutionContext;
import org.aion.fastvm.FastVmTransactionResult;
import org.aion.fastvm.IExternalStateForFvm;
import org.aion.fastvm.IFastVm;

public final class FastVmForTesting implements IFastVm {
    public FastVmTransactionResult pre040ForkResult;
    public FastVmTransactionResult post040ForkResult;

    @Override
    public FastVmTransactionResult runPre040Fork(byte[] code, ExecutionContext context, IExternalStateForFvm state) {
        return this.pre040ForkResult;
    }

    @Override
    public FastVmTransactionResult runPost040Fork(byte[] code, ExecutionContext context, IExternalStateForFvm state) {
        return this.post040ForkResult;
    }
}
