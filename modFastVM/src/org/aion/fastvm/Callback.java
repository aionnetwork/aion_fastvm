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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.AbstractExecutionResult.ResultCode;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.IPrecompiledContract;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.TransactionResult;
import org.aion.zero.types.AionInternalTx;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * This class handles all callbacks from the JIT side. It is not thread-safe and should be
 * synchronized for parallel execution.
 * <p>
 * All methods are static for better JNI performance.
 *
 * @author yulong
 */
public class Callback {

    private static LinkedList<Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>>> stack = new LinkedList<>();

    /**
     * Pushes a pair of context and repository into the callback stack.
     */
    public static void push(
        Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>> pair) {
        stack.push(pair);
    }

    /**
     * Pops the last <context, repository> pair
     */
    public static void pop() {
        stack.pop();
    }

    /**
     * Returns the current context.
     */
    public static ExecutionContext context() {
        return stack.peek().getLeft();
    }

    /**
     * Returns the current repository.
     */
    public static IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo() {
        return stack.peek().getRight();
    }

    /**
     * Returns the hash of the given block.
     */
    public static byte[] getBlockHash(long number) {
        byte[] hash = repo().getBlockStore().getBlockHashByNumber(number);
        return hash == null ? new byte[32] : hash;
    }

    /**
     * Returns the code of a contract.
     */
    public static byte[] getCode(byte[] address) {
        byte[] code = repo().getCode(Address.wrap(address));
        return code == null ? new byte[0] : code;
    }

    /**
     * Returns the balance of an account.
     */
    public static byte[] getBalance(byte[] address) {
        BigInteger balance = repo().getBalance(Address.wrap(address));
        return balance == null ? DataWord.ZERO.getData() : new DataWord(balance).getData();
    }

    /**
     * Returns whether an account exists.
     */
    public static boolean exists(byte[] address) {
        return repo().hasAccountState(Address.wrap(address));
    }

    /**
     * Returns the value that is mapped to the given key.
     */
    public static byte[] getStorage(byte[] address, byte[] key) {
        Optional<IDataWord> value = Optional
            .ofNullable(repo().getStorageValue(Address.wrap(address), new DataWord(key)));

        // System.err.println("GET_STORAGE: address = " + Hex.toHexString(address) + ", key = " + Hex.toHexString(key) + ", value = " + (value == null ? "":Hex.toHexString(value.getData())));

        return !value.isPresent() ? DataWord.ZERO.getData() : value.get().getData();
    }

    /**
     * Sets the value that is mapped to the given key.
     */
    public static void putStorage(byte[] address, byte[] key, byte[] value) {

        // System.err.println("PUT_STORAGE: address = " + Hex.toHexString(address) + ", key = " + Hex.toHexString(key) + ", value = " + Hex.toHexString(value));

        repo().addStorageRow(Address.wrap(address), new DataWord(key), new DataWord(value));
    }

    /**
     * Processes SELFDESTRUCT opcode.
     */
    public static void selfDestruct(byte[] owner, byte[] beneficiary) {
        BigInteger balance = repo().getBalance(Address.wrap(owner));

        newInternalTx(Address.wrap(owner), Address.wrap(beneficiary),
            repo().getNonce(Address.wrap(owner)), new DataWord(balance), ByteUtil.EMPTY_BYTE_ARRAY,
            "selfdestruct");

        repo().addBalance(Address.wrap(owner), balance.negate());

        if (!owner.equals(beneficiary)) {
            repo().addBalance(Address.wrap(beneficiary), balance);
        }

        context().result().addDeleteAccount(Address.wrap(owner));
    }

    /**
     * Processes LOG opcode.
     */
    public static void log(byte[] address, byte[] topics, byte[] data) {
        List<byte[]> list = new ArrayList<>();

        for (int i = 0; i < topics.length; i += 32) {
            byte[] t = Arrays.copyOfRange(topics, i, i + 32);
            list.add(t);
        }

        context().result().addLog(new Log(Address.wrap(address), list, data));
    }

    /**
     * Process CALL/CALLCODE/DELEGATECALL/CREATE opcode.
     */
    public static byte[] call(byte[] message) {
        ExecutionContext ctx = parseMessage(message);

        // check call stack depth
        if (ctx.depth() >= Constants.MAX_CALL_DEPTH) {
            return new ExecutionResult(ResultCode.FAILURE, 0).toBytes();
        }

        // check value
        BigInteger endowment = ctx.callValue().value();
        BigInteger callersBalance = repo().getBalance(ctx.caller());
        if (callersBalance.compareTo(endowment) < 0) {
            return new ExecutionResult(ResultCode.FAILURE, 0).toBytes();
        }

        // call sub-routine
        if (ctx.kind() == ExecutionContext.CREATE) {
            return doCreate(ctx).toBytes();
        } else {
            return doCall(ctx).toBytes();
        }
    }

    /**
     * The method handles the CALL/CALLCODE/DELEGATECALL opcode.
     */
    private static ExecutionResult doCall(ExecutionContext ctx) {
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track = repo()
            .startTracking();
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, ctx.nrgLimit());

        // transfer balance
        track.addBalance(ctx.caller(), ctx.callValue().value().negate());
        track.addBalance(ctx.address(), ctx.callValue().value());

        // update nonce
        track.incrementNonce(ctx.caller());
        // add internal transaction TODO: basic transaction cost?
        AionInternalTx internalTx = newInternalTx(ctx.caller(), ctx.address(),
            track.getNonce(ctx.caller()),
            ctx.callValue(), ctx.callData(), "call");

        ctx.result().addInternalTransaction(internalTx);

        IPrecompiledContract pc = ContractFactory
            .getPrecompiledContract(ctx.address(), ctx.caller(), track);
        if (pc != null) {
            result = (ExecutionResult) pc.execute(ctx.callData(), ctx.nrgLimit());
        } else {
            // get the code
            byte[] code = track.hasAccountState(ctx.address()) ? track.getCode(ctx.address())
                : ByteUtil.EMPTY_BYTE_ARRAY;

            // execute transaction
            if (ArrayUtils.isNotEmpty(code)) {
                FastVM jit = new FastVM();
                result = (ExecutionResult) jit.run(code, ctx, track);
            }
        }

        // post execution
        if (result.getCode() != ResultCode.SUCCESS.toInt()) {
            internalTx.reject();
            ctx.result().rejectInternalTransactions(); // reject all

            track.rollback();
        } else {
            track.flush();
        }

        return result;
    }

    /**
     * This method handles the CREATE opcode.
     */
    private static ExecutionResult doCreate(ExecutionContext ctx) {
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track = repo()
            .startTracking();
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, ctx.nrgLimit());

        // compute new address
        byte[] nonce = track.getNonce(ctx.caller()).toByteArray();
        Address newAddress = Address.wrap(HashUtil.calcNewAddr(ctx.caller().toBytes(), nonce));
        ctx.setAddress(newAddress);

        // in case of hashing collisions
        boolean alreadyExsits = track.hasAccountState(newAddress);
        BigInteger oldBalance = track.getBalance(newAddress);
        track.createAccount(newAddress);
        track.incrementNonce(newAddress); // EIP-161
        track.addBalance(newAddress, oldBalance);

        // transfer balance
        track.addBalance(ctx.caller(), ctx.callValue().value().negate());
        track.addBalance(newAddress, ctx.callValue().value());

        // update nonce
        track.incrementNonce(ctx.caller());

        // add internal transaction
        AionInternalTx internalTx = newInternalTx(ctx.caller(), null, track.getNonce(ctx.caller()), ctx.callValue(),
                ctx.callData(), "create");
        ctx.result().addInternalTransaction(internalTx);

        // execute transaction
        if (alreadyExsits) {
            result.setCodeAndNrgLeft(ResultCode.FAILURE.toInt(), 0);
        } else {
            if (ArrayUtils.isNotEmpty(ctx.callData())) {
                FastVM jit = new FastVM();
                result = (ExecutionResult) jit.run(ctx.callData(), ctx, track);
            }
        }

        // post execution
        if (result.getCode() != ResultCode.SUCCESS.toInt()) {
            internalTx.reject();
            ctx.result().rejectInternalTransactions(); // reject all

            track.rollback();
        } else {
            // charge the codedeposit
            if (result.getNrgLeft() < Constants.NRG_CODE_DEPOSIT) {
                result.setCodeAndNrgLeft(ResultCode.FAILURE.toInt(), 0);
                return result;
            }
            byte[] code = result.getOutput();
            track.saveCode(newAddress, code == null ? new byte[0] : code);

            result.setOutput(newAddress.toBytes());

            track.flush();
        }

        return result;
    }

    /**
     * Parses the execution context from encoded message.
     */
    private static ExecutionContext parseMessage(byte[] message) {
        ExecutionContext prev = context();

        ByteBuffer buffer = ByteBuffer.wrap(message);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte[] txHash = prev.transactionHash();

        byte[] address = new byte[Address.ADDRESS_LEN];
        buffer.get(address);
        Address origin = prev.origin();
        byte[] caller = new byte[Address.ADDRESS_LEN];
        buffer.get(caller);

        DataWord nrgPrice = prev.nrgPrice();
        long nrgLimit = buffer.getLong();
        byte[] buf = new byte[16];
        buffer.get(buf);
        DataWord callValue = new DataWord(buf);
        byte[] callData = new byte[buffer.getInt()];
        buffer.get(callData);

        int depth = buffer.getInt();
        int kind = buffer.getInt();
        int flags = buffer.getInt();

        Address blockCoinbase = prev.blockCoinbase();
        long blockNumber = prev.blockNumber();
        long blockTimestamp = prev.blockTimestamp();
        long blockNrgLimit = prev.blockNrgLimit();
        DataWord blockDifficulty = prev.blockDifficulty();

        TransactionResult txResult = prev.result();

        return new ExecutionContext(txHash, Address.wrap(address), origin, Address.wrap(caller),
            nrgPrice, nrgLimit, callValue, callData, depth,
            kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit, blockDifficulty,
            txResult);
    }

    /**
     * Creates a new internal transaction.
     */
    private static AionInternalTx newInternalTx(Address from, Address to, BigInteger nonce,
        DataWord value, byte[] data,
        String note) {
        // TODO: heavily test internal transaction

        byte[] parentHash = context().transactionHash();
        int deep = stack.size();
        int idx = context().result().getInternalTransactions().size();

        return new AionInternalTx(parentHash, deep, idx, new DataWord(nonce).getData(), from, to,
            value.getData(), data,
            note);
    }
}
