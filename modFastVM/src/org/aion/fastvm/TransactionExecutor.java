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
import org.aion.types.AionAddress;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.interfaces.tx.Transaction;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Transaction executor is the middle man between kernel and VM. It executes transactions and yields
 * transaction receipts.
 *
 * @author yulong
 */
public class TransactionExecutor {
    private static Object LOCK = new Object();

    private KernelInterface kernel;
    private KernelInterface kernelChild;
    private KernelInterface kernelGrandChild;

    private TransactionResult transactionResult;
    private TransactionContext context;
    private Transaction transaction;

    private boolean fork040Enable;

    public TransactionExecutor(
            Transaction transaction, TransactionContext context, KernelInterface kernel) {

        this(transaction, context, kernel, false);
    }

    public TransactionExecutor(
            Transaction transaction,
            TransactionContext context,
            KernelInterface kernel,
            boolean fork040Enable) {

        this.kernel = kernel;
        this.kernelChild = this.kernel.makeChildKernelInterface();
        this.kernelGrandChild = this.kernelChild.makeChildKernelInterface();

        this.transaction = transaction;
        this.context = context;

        long energyLeft = this.transaction.getEnergyLimit() - this.transaction.getTransactionCost();
        this.transactionResult =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, energyLeft, new byte[0]);
        this.fork040Enable = fork040Enable;
    }

    /**
     * Executes the transaction and returns a {@link TransactionResult}.
     *
     * <p>Guarantee: the {@link TransactionResult} that this method returns contains a {@link
     * KernelInterface} that consists of ALL valid state changes pertaining to this transaction.
     * Therefore, the recipient of this result can flush directly from this returned {@link
     * KernelInterface} without any checks or conditional logic to satisfy.
     */
    public TransactionResult execute() {
        return performChecksAndExecute();
    }

    private TransactionResult performChecksAndExecute() {
        synchronized (LOCK) {
            // prepare, preliminary check
            if (performChecks()) {

                KernelInterface track = this.kernelChild.makeChildKernelInterface();

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
                track.commit();

                // run the logic
                if (this.transaction.isContractCreationTransaction()) {
                    executeContractCreationTransaction();
                } else {
                    executeNonContractCreationTransaction();
                }
            }

            // kernelGrandchild holds all state changes that must be flushed upon SUCCESS.
            if (transactionResult.getResultCode().isSuccess()) {
                this.kernelGrandChild.commit();
            }

            // kernelChild holds state changes that must be flushed on anything that is not
            // REJECTED.
            if (!transactionResult.getResultCode().isRejected()) {
                this.kernelChild.commit();
            }

            transactionResult.setKernelInterface(this.kernel);
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
            if (!this.kernelChild.isValidEnergyLimitForCreate(txNrgLimit)) {
                transactionResult.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                transactionResult.setEnergyRemaining(txNrgLimit);
                return false;
            }
        } else {
            if (!this.kernelChild.isValidEnergyLimitForNonCreate(txNrgLimit)) {
                transactionResult.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                transactionResult.setEnergyRemaining(txNrgLimit);
                return false;
            }
        }

        // check nonce
        BigInteger txNonce = new BigInteger(1, this.transaction.getNonce());
        if (!this.kernelChild.accountNonceEquals(this.transaction.getSenderAddress(), txNonce)) {
            transactionResult.setResultCode(FastVmResultCode.INVALID_NONCE);
            transactionResult.setEnergyRemaining(0);
            return false;
        }

        // check balance
        BigInteger txValue = new BigInteger(1, this.transaction.getValue());
        BigInteger txTotal = txNrgPrice.multiply(BigInteger.valueOf(txNrgLimit)).add(txValue);
        if (!this.kernelChild.accountBalanceIsAtLeast(
                this.transaction.getSenderAddress(), txTotal)) {
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
                precompiledFactory.getPrecompiledContract(this.context, this.kernelGrandChild);
        if (pc != null) {
            transactionResult = pc.execute(transaction.getData(), context.getTransactionEnergy());
        } else {
            // execute code
            byte[] code = this.kernelGrandChild.getCode(transaction.getDestinationAddress());
            if (!ArrayUtils.isEmpty(code)) {
                FastVM fvm = new FastVM();
                if (fork040Enable) {
                    transactionResult = fvm.run_v1(code, context, this.kernelGrandChild);

                } else {
                    transactionResult = fvm.run(code, context, this.kernelGrandChild);
                }
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, transaction.getValue());
        this.kernelGrandChild.adjustBalance(transaction.getSenderAddress(), txValue.negate());
        this.kernelGrandChild.adjustBalance(transaction.getDestinationAddress(), txValue);
    }

    /** Prepares contract create. */
    private void executeContractCreationTransaction() {
        // TODO: computing contract address needs to be done correctly. This is a hack.
        AionAddress contractAddress = transaction.getContractAddress();

        boolean requireNewAccount = true;
        if (this.kernelGrandChild.hasAccountState(contractAddress)) {
            if (!fork040Enable) {
                transactionResult.setResultCode(FastVmResultCode.FAILURE);
                transactionResult.setEnergyRemaining(0);
                return;
            }

            byte[] code = this.kernelGrandChild.getCode(contractAddress);
            if (code != null && code.length > 0) {
                transactionResult.setResultCode(FastVmResultCode.FAILURE);
                transactionResult.setEnergyRemaining(0);
                return;
            }
            requireNewAccount = false;
        }

        // create account
        if (requireNewAccount) {
            this.kernelGrandChild.createAccount(contractAddress);
        }
        if (this.kernelGrandChild instanceof KernelInterfaceForFastVM) {
            // TODO: refactor to use the implementation directly
            ((KernelInterfaceForFastVM) this.kernelGrandChild).setVmType(contractAddress);
        }

        // execute contract deployer
        if (!ArrayUtils.isEmpty(transaction.getData())) {
            FastVM fvm = new FastVM();
            if (fork040Enable) {
                transactionResult =
                        fvm.run_v1(transaction.getData(), context, this.kernelGrandChild);

            } else {
                transactionResult = fvm.run(transaction.getData(), context, this.kernelGrandChild);
            }
            if (transactionResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
                this.kernelGrandChild.putCode(contractAddress, transactionResult.getReturnData());
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, transaction.getValue());
        this.kernelGrandChild.adjustBalance(transaction.getSenderAddress(), txValue.negate());
        this.kernelGrandChild.adjustBalance(contractAddress, txValue);
    }

    public void enableFork040() {
        fork040Enable = true;
    }
}
