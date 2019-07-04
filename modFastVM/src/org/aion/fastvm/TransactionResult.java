package org.aion.fastvm;

import org.aion.mcf.types.KernelInterface;
import org.aion.mcf.types.ResultCode;
import org.aion.precompiled.type.TransactionSideEffects;

/**
 * The result of executing a transaction. In addition to the {@link ResultCode} of the transaction,
 * which does a lot in the way of summarizing the result, additional information is also provided by
 * this class relating to the outcome of the execution.
 */
public interface TransactionResult {

    /**
     * Returns the {@link KernelInterface} that contains all of the state changes caused by
     * executing this transaction.
     *
     * <p>This {@link KernelInterface} must be ready to be consumed immediately after running the
     * transaction without the caller having to perform any checks. That is, the difference in state
     * changes caused by failures and successes must already be captured by this kernel interface.
     *
     * @return The kernel interface that contains all the transaction state changes.
     */
    KernelInterface getKernelInterface();

    /**
     * The result code of executing the transaction.
     *
     * @return The transaction result code.
     */
    ResultCode getResultCode();

    /**
     * The output of the transaction. This is the explicit output that the transaction generated.
     *
     * @return The transaction output.
     */
    byte[] getReturnData();

    /**
     * Returns the amount of energy that was unused in executing this transaction, out of all the
     * provided energy as specified by the energy limit.
     *
     * @return The amount of energy remaining.
     */
    long getEnergyRemaining();

    /**
     * Sets the {@link KernelInterface} that this result returns.
     *
     * @param kernel The new kernel interface.
     */
    void setKernelInterface(KernelInterface kernel);

    /**
     * Sets the result code that this result returns.
     *
     * @param resultCode The new result code.
     */
    void setResultCode(ResultCode resultCode);

    /**
     * Sets the output data that this result returns.
     *
     * @param returnData The new output data.
     */
    void setReturnData(byte[] returnData);

    /**
     * Sets the amount of energy remaining that this result returns.
     *
     * @param energyRemaining The new energy remaining.
     */
    void setEnergyRemaining(long energyRemaining);

    /**
     * Returns the side-effects that were the result of executing the corresponding transaction.
     *
     * @return The execution side-effects of the transaction.
     */
    TransactionSideEffects getSideEffects();

    byte[] toBytes();
}
