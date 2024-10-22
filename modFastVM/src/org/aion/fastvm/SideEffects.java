/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.fastvm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction.RejectedStatus;
import org.aion.types.Log;
import org.aion.types.InternalTransaction;

/**
 * An internal helper class which holds all the dynamically generated effects:
 *
 * <p>
 *
 * <ol>
 *   <li>logs created
 *   <li>internal txs created
 *   <li>account deleted
 *       <p>
 *
 * @author yulong
 */
public class SideEffects {

    private Set<AionAddress> deleteAccounts = new HashSet<>();
    private List<InternalTransaction> internalTxs = new ArrayList<>();
    private List<Log> logs = new ArrayList<>();
    private List<Call> calls = new ArrayList<>();

    public static class Call {

        private final byte[] data;
        private final byte[] destination;
        private final byte[] value;

        Call(byte[] data, byte[] destination, byte[] value) {
            this.data = data;
            this.destination = destination;
            this.value = value;
        }

        public byte[] getData() {
            return data;
        }

        public byte[] getDestination() {
            return destination;
        }

        public byte[] getValue() {
            return value;
        }
    }

    public void addToDeletedAddresses(AionAddress address) {
        deleteAccounts.add(address);
    }

    /**
     * Adds the collection addresses to the set of deleted accounts if addresses is non-null.
     *
     * @param addresses The addressed to add to the set of deleted accounts.
     */
    public void addAllToDeletedAddresses(Collection<AionAddress> addresses) {
        for (AionAddress addr : addresses) {
            if (addr != null) {
                deleteAccounts.add(addr);
            }
        }
    }

    /**
     * Adds log to the execution logs.
     *
     * @param log The log to add to the execution logs.
     */
    public void addLog(Log log) {
        logs.add(log);
    }

    /**
     * Adds a collection of logs, logs, to the execution logs.
     *
     * @param logs The collection of logs to add to the execution logs.
     */
    public void addLogs(Collection<Log> logs) {
        for (Log log : logs) {
            if (log != null) {
                this.logs.add(log);
            }
        }
    }

    /**
     * Adds a call whose parameters are given by the parameters to this method.
     *
     * @param data The call data.
     * @param destination The call destination.
     * @param value The call value.
     */
    public void addCall(byte[] data, byte[] destination, byte[] value) {
        calls.add(new Call(data, destination, value));
    }

    /**
     * Adds an internal transaction, tx, to the internal transactions list.
     *
     * @param tx The internal transaction to add.
     */
    public void addInternalTransaction(InternalTransaction tx) {
        if (tx != null) {
            internalTxs.add(tx);
        }
    }

    /**
     * Adds a collection of internal transactions, txs, to the internal transactions list.
     *
     * @param txs The collection of internal transactions to add.
     */
    public void addInternalTransactions(List<InternalTransaction> txs) {
        for (InternalTransaction tx : txs) {
            addInternalTransaction(tx);
        }
    }

    public void markAllInternalTransactionsAsRejected() {
        internalTxs = copyAllTransactionsAsRejected(internalTxs);
    }

    public void markMostRecentInternalTransactionAsRejected() {
        if (internalTxs.isEmpty()) {
            return;
        }

        InternalTransaction toReject = internalTxs.get(internalTxs.size() - 1);
        InternalTransaction rejected = copyTransactionAsRejected(toReject);
        internalTxs.remove(toReject);
        internalTxs.add(rejected);
    }

    public void merge(SideEffects other) {
        addInternalTransactions(other.getInternalTransactions());
        addAllToDeletedAddresses(other.getAddressesToBeDeleted());
        addLogs(other.getExecutionLogs());
    }

    public List<AionAddress> getAddressesToBeDeleted() {
        return new ArrayList<>(deleteAccounts);
    }

    public List<Log> getExecutionLogs() {
        return logs;
    }

    /**
     * Returns the calls.
     *
     * @return the calls.
     */
    public List<Call> getCalls() {
        return calls;
    }

    /**
     * Returns the internal transactions.
     *
     * @return the internal transactions.
     */
    public List<InternalTransaction> getInternalTransactions() {
        return internalTxs;
    }

    /**
     * Returns a list of transactions such that the i'th returned transaction has every field equal
     * to the i'th input transaction, except that it is marked rejected, for all transactions in the
     * list.
     *
     * @param transactions The transactions that the new transactions are derived from.
     * @return the new rejected transactions.
     */
    private static List<InternalTransaction> copyAllTransactionsAsRejected(List<InternalTransaction> transactions) {
        List<InternalTransaction> rejectedTransactions = new ArrayList<>();
        for (InternalTransaction transaction : transactions) {
            rejectedTransactions.add(copyTransactionAsRejected(transaction));
        }
        return rejectedTransactions;
    }

    /**
     * Returns a transaction such that every field in the returned transaction are equal to the
     * input transaction, except that it is marked rejected.
     *
     * @param transaction The transaction that the new transaction is derived from.
     * @return the new rejected transaction.
     */
    private static InternalTransaction copyTransactionAsRejected(InternalTransaction transaction) {
        if (transaction.isCreate) {
            return InternalTransaction.contractCreateTransaction(RejectedStatus.REJECTED, transaction.sender, transaction.senderNonce, transaction.value, transaction.copyOfData(), transaction.energyLimit, transaction.energyPrice);
        } else {
            return InternalTransaction.contractCallTransaction(RejectedStatus.REJECTED, transaction.sender, transaction.destination, transaction.senderNonce, transaction.value, transaction.copyOfData(), transaction.energyLimit, transaction.energyPrice);
        }
    }
}
