package org.aion.precompiled;

import org.aion.fastvm.FastVmTransactionResult;

public interface IPrecompiled {

    FastVmTransactionResult run();

}
