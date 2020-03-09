package org.aion.fastvm.util;

import java.util.ArrayList;
import java.util.List;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.FvmWrappedTransactionResult;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;

public final class TransactionResultUtil {

    public static TransactionStatus transactionStatusFromFvmResultCode(FastVmResultCode code) {
        if (code.isSuccess()) {
            return TransactionStatus.successful();
        } else if (code.isRejected()) {
            return TransactionStatus.rejection(code.toString());
        } else if (code.isRevert()) {
            return TransactionStatus.revertedFailure();
        } else if (code.isFailed()) {
            return TransactionStatus.nonRevertedFailure(code.toString());
        } else {
            return TransactionStatus.fatal(code.toString());
        }
    }

    public static FvmWrappedTransactionResult createFvmWrappedTransactionResult(
            FastVmResultCode code,
            List<InternalTransaction> internalTransactions,
            List<Log> logs,
            long energyUsed,
            byte[] output,
            List<AionAddress> deletedAddresses) {

        TransactionStatus status = transactionStatusFromFvmResultCode(code);
        TransactionResult result = new TransactionResult(status, logs, internalTransactions, energyUsed, output);

        return new FvmWrappedTransactionResult(result, deletedAddresses);
    }

    public static FvmWrappedTransactionResult createWithCodeAndEnergyRemaining(
            FastVmResultCode code,
            long energyUsed) {

        TransactionStatus status = transactionStatusFromFvmResultCode(code);
        TransactionResult result = new TransactionResult(status, new ArrayList<>(), new ArrayList<>(), energyUsed, new byte[0]);

        return new FvmWrappedTransactionResult(result, new ArrayList<>());
    }
}
