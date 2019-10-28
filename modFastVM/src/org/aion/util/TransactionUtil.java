package org.aion.util;

import org.aion.fastvm.FvmConstants;
import org.aion.types.Transaction;

public final class TransactionUtil {

    public static long computeTransactionCost(Transaction transaction) {
        byte[] data = transaction.copyOfTransactionData();
        long nonZeroes = nonZeroBytesInData(data);
        long zeroes = zeroBytesInData(data);

        return (transaction.isCreate ? FvmConstants.CREATE_TRANSACTION_FEE : 0)
            + FvmConstants.TRANSACTION_BASE_FEE
            + zeroes * FvmConstants.ZERO_BYTE_FEE
            + nonZeroes * FvmConstants.NONZERO_BYTE_FEE;
    }

    public static long computeTransactionCost(boolean isCreate, byte[] data) {
        long nonZeroes = nonZeroBytesInData(data);
        long zeroes = zeroBytesInData(data);

        return (isCreate ? FvmConstants.CREATE_TRANSACTION_FEE : 0)
            + FvmConstants.TRANSACTION_BASE_FEE
            + zeroes * FvmConstants.ZERO_BYTE_FEE
            + nonZeroes * FvmConstants.NONZERO_BYTE_FEE;
    }

    private static long nonZeroBytesInData(byte[] data) {
        int total = (data == null) ? 0 : data.length;

        return total - zeroBytesInData(data);
    }

    private static long zeroBytesInData(byte[] data) {
        if (data == null) {
            return 0;
        }

        int c = 0;
        for (byte b : data) {
            c += (b == 0) ? 1 : 0;
        }
        return c;
    }
}
