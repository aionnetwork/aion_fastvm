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

import java.math.BigInteger;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxExecSummary;
import org.aion.base.type.ITxReceipt;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.TransactionInterface;
import org.aion.vm.api.interfaces.TransactionResult;
import org.slf4j.Logger;

public abstract class AbstractExecutor {
    protected static Logger LOGGER;
    protected static Object lock = new Object();
    protected TransactionResult exeResult;
    private long blockRemainingNrg;
    protected boolean isLocalCall;

    protected KernelInterfaceForFastVM kernelParent;
    protected KernelInterfaceForFastVM kernelChild;

    public AbstractExecutor(KernelInterfaceForFastVM kernel, Logger logger, long energyRemaining) {
        this.kernelParent = kernel;
        this.kernelChild = this.kernelParent.startTracking();
        this.blockRemainingNrg = energyRemaining;
        LOGGER = logger;
    }




    public AbstractExecutor(
            KernelInterfaceForFastVM kernel, boolean isLocalCall, long _blkRemainingNrg, Logger _logger) {
        this.kernelParent = kernel;
        this.kernelChild = this.kernelParent.startTracking();
        this.blockRemainingNrg = _blkRemainingNrg;
        this.isLocalCall = isLocalCall;
        LOGGER = _logger;
    }

    protected TransactionResult executeNoFinish(TransactionInterface tx, long contextNrgLmit) {
        synchronized (lock) {
            // prepare, preliminary check
            if (prepare(tx, contextNrgLmit)) {

                KernelInterfaceForFastVM track = this.kernelParent.startTracking();

                // increase nonce
                track.incrementNonce(tx.getSenderAddress());

                // charge nrg cost
                // Note: if the tx is a inpool tx, it will temp charge more balance for the
                // account
                // once the block info been updated. the balance in pendingPool will correct.
                BigInteger nrgLimit = BigInteger.valueOf(tx.getEnergyLimit());
                BigInteger nrgPrice = BigInteger.valueOf(tx.getEnergyPrice());
                BigInteger txNrgCost = nrgLimit.multiply(nrgPrice);
                track.deductEnergyCost(tx.getSenderAddress(), txNrgCost);
                track.flush();

                // run the logic
                if (tx.isContractCreationTransaction()) {
                    create();
                } else {
                    call();
                }
            }

            exeResult.setKernelInterface(this.kernelChild);
            return exeResult;
        }
    }

    /**
     * Checks that the transaction passes the basic validation criteria. These criteria are: 1. the
     * transaction energy limit is within the acceptable limit range and is larger than the
     * remaining energy in the block that contains the transaction. 2. contextNrgLimit is
     * non-negative. 3. the transaction nonce is equal to the transaction sender's nonce. 4. the
     * transaction sender has enough funds to cover the cost of the transaction.
     *
     * <p>Returns true if all crtieria are met or if the call is local. Returns false if the call is
     * not local and at least one criterion is not met. In this case, the execution result has its
     * result code and energy left set appropriately.
     *
     * @param tx The transaction to check.
     * @param contextNrgLmit The execution context's energy limit.
     * @return true if call is local or if all criteria listed above are met.
     */
    public final boolean prepare(TransactionInterface tx, long contextNrgLmit) {
        BigInteger txNrgPrice = BigInteger.valueOf(tx.getEnergyPrice());
        long txNrgLimit = tx.getEnergyLimit();

        if (tx.isContractCreationTransaction()) {
            if (!this.kernelParent.isValidEnergyLimitForCreate(txNrgLimit)) {
                exeResult.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                exeResult.setEnergyRemaining(txNrgLimit);
                return false;
            }
        } else {
            if (!this.kernelParent.isValidEnergyLimitForNonCreate(txNrgLimit)) {
                exeResult.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                exeResult.setEnergyRemaining(txNrgLimit);
                return false;
            }
        }

        // TODO: blockRemainingNrg needs to be brought out into BulkExecutor
        // TODO: this contextNrgLimit check should also be brought out too.
        if (!isLocalCall) {
            if (txNrgLimit > blockRemainingNrg || contextNrgLmit < 0) {
                exeResult.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                exeResult.setEnergyRemaining(0);
                return false;
            }
        }

        // check nonce
        BigInteger txNonce = new BigInteger(1, tx.getNonce());
        if (!this.kernelParent.accountNonceEquals(tx.getSenderAddress(), txNonce)) {
            exeResult.setResultCode(FastVmResultCode.INVALID_NONCE);
            exeResult.setEnergyRemaining(0);
            return false;
        }

        // check balance
        BigInteger txValue = new BigInteger(1, tx.getValue());
        BigInteger txTotal = txNrgPrice.multiply(BigInteger.valueOf(txNrgLimit)).add(txValue);
        if (!this.kernelParent.accountBalanceIsAtLeast(tx.getSenderAddress(), txTotal)) {
            exeResult.setResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
            exeResult.setEnergyRemaining(0);
            return false;
        }

        // TODO: confirm if signature check is not required here

        return true;
    }

    protected abstract ITxExecSummary finish();

    protected abstract void call();

    protected abstract void create();

    /**
     * Returns the energy remaining after the transaction was executed. Prior to execution this
     * method simply returns the energy limit for the transaction.
     *
     * @return The energy left after the transaction executes or its energy limit prior to
     *     execution.
     */
    public long getNrgLeft() {
        return exeResult.getEnergyRemaining();
    }

    /**
     * Returns the energy remaining after the amount of leftover energy from the transaction
     * execution is deducted from limit.
     *
     * @param limit The upper bound to deduct the transaction energy remainder from.
     * @return the energy used as defined above.
     */
    private long getNrgUsed(long limit) {
        return limit - exeResult.getEnergyRemaining();
    }

    /**
     * Builds a new transaction receipt on top of receipt out of tx and logs.
     *
     * @param receipt The receipt to build off of.
     * @param tx The transaction to which this receipt corresponds.
     * @param logs The logs relating to the transaction execution.
     * @return receipt with the new receipt added to it.
     */
    @SuppressWarnings("unchecked")
    public ITxReceipt buildReceipt(ITxReceipt receipt, ITransaction tx, List logs) {
        // TODO probably remove receipt and instantiate a new empty one here?
        receipt.setTransaction(tx);
        receipt.setLogs(logs);
        receipt.setNrgUsed(getNrgUsed(tx.getEnergyLimit())); // amount of energy used to execute tx
        receipt.setExecutionResult(exeResult.getOutput()); // misnomer -> output is named result
        receipt.setError(
                exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()
                        ? ""
                        : exeResult.getResultCode().name());

        return receipt;
    }

    /**
     * Updates the repository only if the call is not local and the transaction summary was not
     * marked as rejected.
     *
     * <p>If the repository qualifies for an update then it is updated as follows: 1. The
     * transaction sender is refunded for whatever outstanding energy was not consumed. 2. The
     * transaction energy consumption amount is set accordingly. 3. The fee is transferred to the
     * coinbase account. 4. All accounts marked for deletion (given that the transaction was
     * successful) are deleted.
     *
     * @param summary The transaction summary.
     * @param tx The transaction.
     * @param coinbase The coinbase for the block in which the transaction was sealed.
     * @param deleteAccounts The list of accounts to be deleted if tx was successful.
     */
    public void updateRepo(
            ITxExecSummary summary,
            ITransaction tx,
            Address coinbase,
            List<Address> deleteAccounts) {

        if (!isLocalCall && !summary.isRejected()) {
            KernelInterfaceForFastVM track = this.kernelParent.startTracking();
            // refund nrg left
            if (exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()
                    || exeResult.getResultCode().toInt() == FastVmResultCode.REVERT.toInt()) {
                track.adjustBalance(tx.getSenderAddress(), summary.getRefund());
            }

            tx.setNrgConsume(getNrgUsed(tx.getEnergyLimit()));

            // Transfer fees to miner
            track.adjustBalance(coinbase, summary.getFee());

            if (exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
                // Delete accounts
                for (Address addr : deleteAccounts) {
                    track.deleteAccount(addr);
                }
            }
            track.flush();
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Transaction receipt: {}", summary.getReceipt());
            LOGGER.debug("Transaction logs: {}", summary.getLogs());
        }
    }

    public void setTransactionResult(FastVmTransactionResult result) {
        exeResult = result;
    }

    // These methods below should be removed..

    public IRepositoryCache getRepoTrack() {
        return this.kernelChild.getRepositoryCache();
    }

    public void setResult(TransactionResult result) {
        exeResult = result;
    }

    public TransactionResult getResult() {
        return exeResult;
    }

}
