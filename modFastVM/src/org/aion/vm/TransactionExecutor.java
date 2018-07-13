/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */
package org.aion.vm;

import static org.aion.mcf.valid.TxNrgRule.isValidNrgContractCreate;
import static org.aion.mcf.valid.TxNrgRule.isValidNrgTx;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

import java.math.BigInteger;
import java.util.List;

import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.fastvm.FastVM;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.Log;
import org.aion.vm.ExecutionResult.Code;
import org.aion.vm.PrecompiledContracts.PrecompiledContract;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;
import org.spongycastle.util.Arrays;

/**
 * Transaction executor is the middle man between kernel and VM. It executes transactions and yields
 * transaction receipts.
 *
 * @author yulong
 */
public class TransactionExecutor {

    private static final Logger logger = AionLoggerFactory.getLogger(LogEnum.VM.name());

    private AionTransaction tx;
    private IAionBlock block;
    private IRepository<AccountState, DataWord, IBlockStoreBase<?, ?>> repo;
    private IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repoTrack;
    private boolean isLocalCall;
    private long blockRemainingNrg;

    private ExecutionContext ctx;
    private ExecutionResult result;

    private static Object lock = new Object();
    private boolean askNonce = true;

    /**
     * Create a new transaction executor. <br>
     * <br>
     * IMPORTANT: be sure to accumulate nrg used in a block outside the transaction executor
     *
     * @param tx transaction to be executed
     * @param block a temporary block used to garner relevant environmental variables
     */
    public TransactionExecutor(AionTransaction tx, IAionBlock block,
        IRepository<AccountState, DataWord, IBlockStoreBase<?, ?>> repo,
        boolean isLocalCall,
        long blockRemainingNrg) {
        if (logger.isDebugEnabled()) {
            logger.debug("Executing transaction: {}", tx);
        }

        this.tx = tx;
        this.block = block;
        this.repo = repo;
        this.repoTrack = this.repo.startTracking();
        this.isLocalCall = isLocalCall;
        this.blockRemainingNrg = blockRemainingNrg;

        /**
         * transaction info
         */
        byte[] txHash = tx.getHash();
        Address address = tx.isContractCreation() ? tx.getContractAddress() : tx.getTo();
        Address origin = tx.getFrom();
        Address caller = tx.getFrom();

        /**
         * nrg info
         */
        DataWord nrgPrice = tx.nrgPrice();
        long nrgLimit = tx.nrgLimit() - tx.transactionCost(block.getNumber());
        DataWord callValue = new DataWord(nullToEmpty(tx.getValue()));
        byte[] callData =
            tx.isContractCreation() ? ByteUtil.EMPTY_BYTE_ARRAY : nullToEmpty(tx.getData());

        /**
         * execution info
         */
        int depth = 0;
        int kind = tx.isContractCreation() ? ExecutionContext.CREATE : ExecutionContext.CALL;
        int flags = 0;

        /**
         * block info
         */
        Address blockCoinbase = block.getCoinbase();
        long blockNumber = block.getNumber();
        long blockTimestamp = block.getTimestamp();
        long blockNrgLimit = block.getNrgLimit();

        // TODO: temp solution for difficulty length
        byte[] diff = block.getDifficulty();
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        DataWord blockDifficulty = new DataWord(diff);

        /**
         * execution context and results
         */
        ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue, callData, depth,
                kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit, blockDifficulty,
                new ExecutionHelper());
        result = new ExecutionResult(Code.SUCCESS, nrgLimit);
    }

    /**
     * Creates a transaction executor (use block nrg limit).
     */
    public TransactionExecutor(AionTransaction tx, IAionBlock block,
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo, boolean isLocalCall) {
        this(tx, block, repo, isLocalCall, block.getNrgLimit());
    }

    /**
     * Create a transaction executor (non constant call, use block nrg limit).
     */
    public TransactionExecutor(AionTransaction tx, IAionBlock block,
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo) {
        this(tx, block, repo, false, block.getNrgLimit());
    }

    /**
     * Execute the transaction
     */
    public AionTxExecSummary execute() {
        synchronized (lock) {
            // prepare, preliminary check
            if (prepare()) {

                if (!isLocalCall) {
                    IRepositoryCache track = repo.startTracking();
                    // increase nonce
                    if (askNonce) {
                        track.incrementNonce(tx.getFrom());
                    }

                    // charge nrg cost
                    // Note: if the tx is a inpool tx, it will temp charge more balance for the account
                    // once the block info been updated. the balance in pendingPool will correct.
                    BigInteger txNrgLimit = BigInteger.valueOf(tx.nrgLimit());
                    BigInteger txNrgPrice = tx.nrgPrice().value();
                    BigInteger txNrgCost = txNrgLimit.multiply(txNrgPrice);
                    track.addBalance(tx.getFrom(), txNrgCost.negate());
                    track.flush();
                }

                // run the logic
                if (tx.isContractCreation()) {
                    create();
                } else {
                    call();
                }
            }

            // finalize
            return finish();
        }
    }

    /**
     * Prepares the context for transaction execution.
     */
    protected boolean prepare() {

        // check nrg limit
        if (!isValidTxNrg()) {
            return false;
        }

        if (isLocalCall) {
            return true;
        }

        long txNrgLimit = tx.nrgLimit();
        if (txNrgLimit > blockRemainingNrg || ctx.nrgLimit() < 0) {
            result.setCodeAndNrgLeft(Code.INVALID_NRG_LIMIT, 0);
            return false;
        }

        // check nonce
        if (askNonce) {
            BigInteger txNonce = new BigInteger(1, tx.getNonce());
            BigInteger nonce = repo.getNonce(tx.getFrom());

            if (!txNonce.equals(nonce)) {
                result.setCodeAndNrgLeft(Code.INVALID_NONCE, 0);
                return false;
            }
        }

        // check balance
        BigInteger txNrgPrice = tx.nrgPrice().value();
        BigInteger txValue = new BigInteger(1, tx.getValue());
        BigInteger txTotal = txNrgPrice.multiply(BigInteger.valueOf(txNrgLimit)).add(txValue);
        BigInteger balance = repo.getBalance(tx.getFrom());
        if (txTotal.compareTo(balance) > 0) {
            result.setCodeAndNrgLeft(Code.INSUFFICIENT_BALANCE, 0);
            return false;
        }

        // TODO: confirm if signature check is not required here

        return true;
    }

    private boolean isValidTxNrg() {
        long txNrgLimit = tx.nrgLimit();
        if (tx.isContractCreation()) {
            if (!isValidNrgContractCreate(txNrgLimit)) {
                result.setCodeAndNrgLeft(Code.INVALID_NRG_LIMIT, txNrgLimit);
                return false;
            }
        } else {
            if (!isValidNrgTx(txNrgLimit)) {
                result.setCodeAndNrgLeft(Code.INVALID_NRG_LIMIT, txNrgLimit);
                return false;
            }
        }
        return true;
    }

    /**
     * Prepares contract call.
     */
    protected void call() {
        PrecompiledContract pc = PrecompiledContracts
            .getPrecompiledContract(tx.getTo(), this.repoTrack, ctx);

        if (pc != null) {
            result = pc.execute(tx.getData(), ctx.nrgLimit());
        } else {
            // execute code
            byte[] code = repoTrack.getCode(tx.getTo());
            if (!isEmpty(code)) {
                VirtualMachine fvm = new FastVM();
                result = fvm.run(code, ctx, repoTrack);
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, tx.getValue());
        repoTrack.addBalance(tx.getFrom(), txValue.negate());
        repoTrack.addBalance(tx.getTo(), txValue);
    }

    /**
     * Prepares contract create.
     */
    protected void create() {
        Address contractAddress = tx.getContractAddress();

        if (repoTrack.hasAccountState(contractAddress)) {
            result.setCodeAndNrgLeft(Code.CONTRACT_ALREADY_EXISTS, 0);
            return;
        }

        // create account
        repoTrack.createAccount(contractAddress);

        // execute contract deployer
        if (!isEmpty(tx.getData())) {
            VirtualMachine fvm = new FastVM();
            result = fvm.run(tx.getData(), ctx, repoTrack);

            if (result.getCode() == Code.SUCCESS) {
                repoTrack.saveCode(contractAddress, result.getOutput());
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, tx.getValue());
        repoTrack.addBalance(tx.getFrom(), txValue.negate());
        repoTrack.addBalance(contractAddress, txValue);
    }

    /**
     * Finalize state changes and returns summary.
     */
    protected AionTxExecSummary finish() {

        ExecutionHelper h = new ExecutionHelper();
        h.merge(ctx.helper(), result.getCode() == Code.SUCCESS);

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(getReceipt(h.getLogs())) //
                .logs(h.getLogs()) //
                .deletedAccounts(h.getDeleteAccounts()) //
                .internalTransactions(h.getInternalTransactions()) //
                .result(result.getOutput());

        switch (result.getCode()) {
            case SUCCESS:
                repoTrack.flush();
                break;
            case INVALID_NONCE:
            case INVALID_NRG_LIMIT:
            case INSUFFICIENT_BALANCE:
                builder.markAsRejected();
                break;
            case CONTRACT_ALREADY_EXISTS:
            case FAILURE:
            case OUT_OF_NRG:
            case BAD_INSTRUCTION:
            case BAD_JUMP_DESTINATION:
            case STACK_OVERFLOW:
            case STACK_UNDERFLOW:
            case REVERT:
            case INTERNAL_ERROR:
                builder.markAsFailed();
                break;
            default:
                throw new RuntimeException("invalid code path, should not ever default");
        }

        AionTxExecSummary summary = builder.build();

        if (!isLocalCall && !summary.isRejected()) {
            IRepositoryCache track = repo.startTracking();
            // refund nrg left
            if (result.getCode() == Code.SUCCESS || result.getCode() == Code.REVERT) {
                track.addBalance(tx.getFrom(), summary.getRefund());
            }

            tx.setNrgConsume(getNrgUsed());

            // Transfer fees to miner
            track.addBalance(block.getCoinbase(), summary.getFee());

            if (result.getCode() == Code.SUCCESS) {
                // Delete accounts
                for (Address addr : h.getDeleteAccounts()) {
                    track.deleteAccount(addr);
                }
            }
            track.flush();
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Transaction receipt: {}", summary.getReceipt());
            logger.debug("Transaction logs: {}", summary.getLogs());
        }

        return summary;
    }

    /**
     * Returns the transaction receipt.
     */
    protected AionTxReceipt getReceipt(List<Log> logs) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(tx);
        receipt.setLogs(logs);
        receipt.setNrgUsed(getNrgUsed());
        receipt.setExecutionResult(result.getOutput());
        receipt.setError(result.getCode() == Code.SUCCESS ? "" : result.getCode().name());

        return receipt;
    }

    /**
     * Returns the nrg left after execution.
     */
    protected long getNrgLeft() {
        return result.getNrgLeft();
    }

    /**
     * Returns the nrg used after execution.
     */
    protected long getNrgUsed() {
        return tx.nrgLimit() - result.getNrgLeft();
    }

    public void setBypassNonce(boolean bypassNonce) {
        this.askNonce = !bypassNonce;
    }
}
