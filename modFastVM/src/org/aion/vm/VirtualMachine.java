package org.aion.vm;

import org.aion.base.db.IRepositoryCache;
import org.aion.core.AccountState;
import org.aion.db.IBlockStoreBase;
import org.aion.vm.types.DataWord;

/**
 * High-level interface of Aion virtual machine.
 *
 * @author yulong
 */
public interface VirtualMachine {

    /**
     * Run the given code, under the specified context.
     *
     * @param code  byte code
     * @param ctx   the execution context
     * @param track state repository track
     * @return the execution result
     */
    ExecutionResult run(byte[] code, ExecutionContext ctx, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track);
}
