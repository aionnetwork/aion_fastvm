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
import org.aion.base.type.AionAddress;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionInterface;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Transaction executor is the middle man between kernel and VM. It executes transactions and yields
 * transaction receipts.
 *
 * @author yulong
 */
public class TransactionExecutor {
    private static Object LOCK = new Object();

    private KernelInterface kernelParent;
    private KernelInterface kernelChild;
    private TransactionResult transactionResult;
    private TransactionContext context;
    private TransactionInterface transaction;

    //TODO: once contract address is computed in the right place we can remove transaction from the
    //TODO: constructor altogether and derive all info it supplies from the context.

    TransactionExecutor(
            TransactionInterface transaction,
            TransactionContext context,
            KernelInterface kernel) {

        this.kernelParent = kernel;
        this.kernelChild = this.kernelParent.startTracking();
        this.transaction = transaction;
        this.context = context;

        long energyLeft =
                this.transaction.getEnergyLimit() - this.transaction.getTransactionCost();
        this.transactionResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, energyLeft, new byte[0]);
    }

    public TransactionResult execute() {
        return performChecksAndExecute();
    }

    private TransactionResult performChecksAndExecute() {
        synchronized (LOCK) {
            // prepare, preliminary check
            if (performChecks()) {

                KernelInterface track = this.kernelParent.startTracking();

                // increase nonce
                track.incrementNonce(this.transaction.getSenderAddress());

                // charge nrg cost
                // Note: if the tx is a inpool tx, it will temp charge more balance for the
                // account
                // once the block info been updated. the balance in pendingPool will correct.
                BigInteger nrgLimit = BigInteger.valueOf(this.transaction.getEnergyLimit());
                BigInteger nrgPrice = BigInteger.valueOf(this.transaction.getEnergyPrice());
                BigInteger txNrgCost = nrgLimit.multiply(nrgPrice);
                track.deductEnergyCost(this.transaction.getSenderAddress(), txNrgCost);
                track.flush();

                // run the logic
                if (this.transaction.isContractCreationTransaction()) {
                    executeContractCreationTransaction();
                } else {
                    executeNonContractCreationTransaction();
                }
            }

            transactionResult.setKernelInterface(this.kernelChild);
            return transactionResult;
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
     * @return true if call is local or if all criteria listed above are met.
     */
    private boolean performChecks() {
        BigInteger txNrgPrice = BigInteger.valueOf(this.transaction.getEnergyPrice());
        long txNrgLimit = this.transaction.getEnergyLimit();

        if (this.transaction.isContractCreationTransaction()) {
            if (!this.kernelParent.isValidEnergyLimitForCreate(txNrgLimit)) {
                transactionResult.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                transactionResult.setEnergyRemaining(txNrgLimit);
                return false;
            }
        } else {
            if (!this.kernelParent.isValidEnergyLimitForNonCreate(txNrgLimit)) {
                transactionResult.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                transactionResult.setEnergyRemaining(txNrgLimit);
                return false;
            }
        }

        // check nonce
        BigInteger txNonce = new BigInteger(1, this.transaction.getNonce());
        if (!this.kernelParent.accountNonceEquals(this.transaction.getSenderAddress(), txNonce)) {
            transactionResult.setResultCode(FastVmResultCode.INVALID_NONCE);
            transactionResult.setEnergyRemaining(0);
            return false;
        }

        // check balance
        BigInteger txValue = new BigInteger(1, this.transaction.getValue());
        BigInteger txTotal = txNrgPrice.multiply(BigInteger.valueOf(txNrgLimit)).add(txValue);
        if (!this.kernelParent.accountBalanceIsAtLeast(this.transaction.getSenderAddress(), txTotal)) {
            transactionResult.setResultCode(FastVmResultCode.INSUFFICIENT_BALANCE);
            transactionResult.setEnergyRemaining(0);
            return false;
        }

        // TODO: confirm if signature check is not required here

        return true;
    }

    /** Prepares contract call. */
    private void executeNonContractCreationTransaction() {
        ContractFactory precompiledFactory = new ContractFactory();
        PrecompiledContract pc =
                precompiledFactory.getPrecompiledContract(this.context, this.kernelChild);
        if (pc != null) {
            transactionResult =
                    pc.execute(transaction.getData(), context.getTransactionEnergy());
        } else {
            // execute code
            byte[] code = this.kernelChild.getCode(transaction.getDestinationAddress());
            if (!ArrayUtils.isEmpty(code)) {
                FastVM fvm = new FastVM();
                transactionResult = fvm.run(code, context, this.kernelChild);
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, transaction.getValue());
        this.kernelChild.adjustBalance(transaction.getSenderAddress(), txValue.negate());
        this.kernelChild.adjustBalance(transaction.getDestinationAddress(), txValue);
    }

    /** Prepares contract create. */
    private void executeContractCreationTransaction() {
        //TODO: computing contract address needs to be done correctly. This is a hack.
        AionAddress contractAddress = ((AionTransaction) transaction).getContractAddress();

        if (this.kernelChild.hasAccountState(contractAddress)) {
            transactionResult.setResultCode(FastVmResultCode.FAILURE);
            transactionResult.setEnergyRemaining(0);
            return;
        }

        // create account
        this.kernelChild.createAccount(contractAddress);

        // execute contract deployer
        if (!ArrayUtils.isEmpty(transaction.getData())) {
            FastVM fvm = new FastVM();
            transactionResult = fvm.run(transaction.getData(), context, this.kernelChild);

            if (transactionResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
                this.kernelChild.putCode(contractAddress, transactionResult.getOutput());
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, transaction.getValue());
        this.kernelChild.adjustBalance(transaction.getSenderAddress(), txValue.negate());
        this.kernelChild.adjustBalance(contractAddress, txValue);
    }
}
