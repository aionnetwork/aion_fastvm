package org.aion.fastvm;

import java.util.List;
import org.aion.types.AionAddress;
import org.aion.types.TransactionResult;

public final class FvmWrappedTransactionResult {
    public final TransactionResult result;
    public final List<AionAddress> deletedAddresses;

    /**
     * Constructs a new result wrapper that wraps the provided result and also contains additional
     * information such as the list of deleted addresses.
     *
     * It is strongly recommended that this class only ever be created via the {@link org.aion.util.TransactionResultUtil} class!
     *
     * @param result The result to wrap.
     * @param deletedAddresses The deleted addresses.
     */
    public FvmWrappedTransactionResult(TransactionResult result, List<AionAddress> deletedAddresses) {
        if (result == null) {
            throw new NullPointerException("Cannot construct TransactionResult with null result!");
        }

        this.result = result;
        this.deletedAddresses = deletedAddresses;
    }

    @Override
    public String toString() {
        return "FvmWrappedTransactionResult { result = " + this.result + ", deleted addresses = " + this.deletedAddresses + " }";
    }
}
