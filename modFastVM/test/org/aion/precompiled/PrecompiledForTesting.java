package org.aion.precompiled;

import org.aion.fastvm.FastVmTransactionResult;

public final class PrecompiledForTesting implements IPrecompiled {
    public FastVmTransactionResult result;

    @Override
    public FastVmTransactionResult run() {
        return this.result;
    }
}
