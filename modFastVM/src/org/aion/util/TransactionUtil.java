package org.aion.util;

import org.aion.base.Constants;
import org.aion.types.Transaction;

public final class TransactionUtil {

    public static long computeTransactionCost(Transaction transaction) {
        byte[] data = transaction.copyOfTransactionData();
        long nonZeroes = nonZeroBytesInData(data);
        long zeroes = zeroBytesInData(data);

        return (transaction.isCreate ? Constants.NRG_CREATE_CONTRACT_MIN : 0)
            + Constants.NRG_TRANSACTION_MIN
            + zeroes * Constants.NRG_TX_DATA_ZERO
            + nonZeroes * Constants.NRG_TX_DATA_NONZERO;
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
