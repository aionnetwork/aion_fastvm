package org.aion.fastvm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutionContext;
import org.aion.vm.IContractFactory;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.types.AionInternalTx;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * This class handles all callbacks from the JIT side. It is not thread-safe and should be
 * synchronized for parallel execution.
 *
 * <p>All methods are static for better JNI performance.
 *
 * @author yulong
 */
public class Callback {

    private static LinkedList<
                    Pair<
                            ExecutionContext,
                            IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>>>
            stack = new LinkedList<>();

    /**
     * Pushes a pair of context and repository into the callback stack.
     *
     * @param pair
     */
    public static void push(
            Pair<ExecutionContext, IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>>
                    pair) {
        stack.push(pair);
    }

    /** Pops the last <context, repository> pair */
    public static void pop() {
        stack.pop();
    }

    /**
     * Returns the current context.
     *
     * @return
     */
    public static ExecutionContext context() {
        return stack.peek().getLeft();
    }

    /**
     * Returns the current repository.
     *
     * @return
     */
    public static IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo() {
        return stack.peek().getRight();
    }

    /**
     * Returns the hash of the given block.
     *
     * @param number
     * @return
     */
    public static byte[] getBlockHash(long number) {
        byte[] hash = repo().getBlockStore().getBlockHashByNumber(number);
        return hash == null ? new byte[32] : hash;
    }

    /**
     * Returns the code of a contract.
     *
     * @param address
     * @return
     */
    public static byte[] getCode(byte[] address) {
        byte[] code = repo().getCode(AionAddress.wrap(address));
        return code == null ? new byte[0] : code;
    }

    /**
     * Returns the balance of an account.
     *
     * @param address
     * @return
     */
    public static byte[] getBalance(byte[] address) {
        BigInteger balance = repo().getBalance(AionAddress.wrap(address));
        return balance == null ? DataWord.ZERO.getData() : new DataWord(balance).getData();
    }

    /**
     * Returns whether an account exists.
     *
     * @param address
     * @return
     */
    public static boolean exists(byte[] address) {
        return repo().hasAccountState(AionAddress.wrap(address));
    }

    /**
     * Returns the value that is mapped to the given key.
     *
     * @param address
     * @param key
     * @return
     */
    public static byte[] getStorage(byte[] address, byte[] key) {
        IDataWord value = repo().getStorageValue(AionAddress.wrap(address), new DataWord(key));

        // System.err.println("GET_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + (value == null ?
        // "":Hex.toHexString(value.getData())));

        return value == null ? DataWord.ZERO.getData() : value.getData();
    }

    /**
     * Sets the value that is mapped to the given key.
     *
     * @param address
     * @param key
     * @param value
     */
    public static void putStorage(byte[] address, byte[] key, byte[] value) {

        // System.err.println("PUT_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + Hex.toHexString(value));

        repo().addStorageRow(AionAddress.wrap(address), new DataWord(key), new DataWord(value));
    }

    /**
     * Processes SELFDESTRUCT opcode.
     *
     * @param owner
     * @param beneficiary
     */
    public static void selfDestruct(byte[] owner, byte[] beneficiary) {
        BigInteger balance = repo().getBalance(AionAddress.wrap(owner));

        // add internal transaction
        AionInternalTx internalTx =
                newInternalTx(
                        AionAddress.wrap(owner),
                        AionAddress.wrap(beneficiary),
                        repo().getNonce(AionAddress.wrap(owner)),
                        new DataWord(balance),
                        ByteUtil.EMPTY_BYTE_ARRAY,
                        "selfdestruct");
        context().helper().addInternalTransaction(internalTx);

        // transfer
        repo().addBalance(AionAddress.wrap(owner), balance.negate());
        if (!Arrays.equals(owner, beneficiary)) {
            repo().addBalance(AionAddress.wrap(beneficiary), balance);
        }

        context().helper().addDeleteAccount(AionAddress.wrap(owner));
    }

    /**
     * Processes LOG opcode.
     *
     * @param address
     * @param topics
     * @param data
     */
    public static void log(byte[] address, byte[] topics, byte[] data) {
        List<byte[]> list = new ArrayList<>();

        for (int i = 0; i < topics.length; i += 32) {
            byte[] t = Arrays.copyOfRange(topics, i, i + 32);
            list.add(t);
        }

        context().helper().addLog(new Log(AionAddress.wrap(address), list, data));
    }

    /**
     * This method only exists so that FastVM and ContractFactory can be mocked for testing. This
     * method was formerly called call and now the call method simply invokes this method with new
     * istances of the fast vm and contract factory.
     */
    static byte[] performCall(byte[] message, FastVM vm, ContractFactory factory) {
        ExecutionContext ctx = parseMessage(message);
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track =
                repo().startTracking();

        // check call stack depth
        if (ctx.depth() >= Constants.MAX_CALL_DEPTH) {
            return new TransactionResult(ResultCode.FAILURE, 0).toBytes();
        }

        // check value
        BigInteger endowment = ctx.callValue().value();
        BigInteger callersBalance = repo().getBalance(ctx.sender());
        if (callersBalance.compareTo(endowment) < 0) {
            return new TransactionResult(ResultCode.FAILURE, 0).toBytes();
        }

        // call sub-routine
        TransactionResult result;
        if (ctx.kind() == ExecutionContext.CREATE) {
            result = doCreate(ctx, vm);
        } else {
            result = doCall(ctx, vm, factory);
        }

        // merge the effects
        context().helper().merge(ctx.helper(), result.getResultCode().toInt() == ResultCode.SUCCESS.toInt());

        return result.toBytes();
    }

    /**
     * Process CALL/CALLCODE/DELEGATECALL/CREATE opcode.
     *
     * @param message
     * @return
     */
    public static byte[] call(byte[] message) {
        return performCall(message, new FastVM(), new ContractFactory());
    }

    /**
     * The method handles the CALL/CALLCODE/DELEGATECALL opcode.
     *
     * @param ctx
     * @return
     */
    private static TransactionResult doCall(
            ExecutionContext ctx, FastVM jit, IContractFactory factory) {
        Address codeAddress = ctx.address();
        if (ctx.kind() == ExecutionContext.CALLCODE
                || ctx.kind() == ExecutionContext.DELEGATECALL) {
            ctx.address = context().address();
        }

        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track =
                repo().startTracking();
        TransactionResult result = new TransactionResult(ResultCode.SUCCESS, ctx.nrgLimit());

        // add internal transaction
        AionInternalTx internalTx =
                newInternalTx(
                        ctx.sender(),
                        ctx.address(),
                        track.getNonce(ctx.sender()),
                        ctx.callValue(),
                        ctx.callData(),
                        "call");
        context().helper().addInternalTransaction(internalTx);
        ctx.setTransactionHash(internalTx.getTransactionHash());

        // transfer balance
        if (ctx.kind() != ExecutionContext.DELEGATECALL
                && ctx.kind() != ExecutionContext.CALLCODE) {
            track.addBalance(ctx.sender(), ctx.callValue().value().negate());
            track.addBalance(ctx.address(), ctx.callValue().value());
        }

        IPrecompiledContract pc = factory.getPrecompiledContract(ctx, track);
        if (pc != null) {
            result = pc.execute(ctx.callData(), ctx.nrgLimit());
        } else {
            // get the code
            byte[] code =
                    track.hasAccountState(codeAddress)
                            ? track.getCode(codeAddress)
                            : ByteUtil.EMPTY_BYTE_ARRAY;

            // execute transaction
            if (ArrayUtils.isNotEmpty(code)) {
                result = jit.run(code, ctx, track);
            }
        }

        // post execution
        if (result.getResultCode().toInt() != ResultCode.SUCCESS.toInt()) {
            internalTx.reject();
            ctx.helper().rejectInternalTransactions(); // reject all

            track.rollback();
        } else {
            track.flush();
        }

        return result;
    }

    /**
     * This method handles the CREATE opcode.
     *
     * @param ctx execution context
     * @return
     */
    private static TransactionResult doCreate(ExecutionContext ctx, FastVM jit) {
        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track =
                repo().startTracking();
        TransactionResult result = new TransactionResult(ResultCode.SUCCESS, ctx.nrgLimit());

        // compute new address
        byte[] nonce = track.getNonce(ctx.sender()).toByteArray();
        AionAddress newAddress = AionAddress.wrap(HashUtil.calcNewAddr(ctx.sender().toBytes(), nonce));
        ctx.setDestination(newAddress);

        // add internal transaction
        // TODO: should the `to` address be null?
        AionInternalTx internalTx =
                newInternalTx(
                        ctx.sender(),
                        ctx.address(),
                        track.getNonce(ctx.sender()),
                        ctx.callValue(),
                        ctx.callData(),
                        "create");
        context().helper().addInternalTransaction(internalTx);
        ctx.setTransactionHash(internalTx.getTransactionHash());

        // in case of hashing collisions
        boolean alreadyExsits = track.hasAccountState(newAddress);
        BigInteger oldBalance = track.getBalance(newAddress);
        track.createAccount(newAddress);
        track.incrementNonce(newAddress); // EIP-161
        track.addBalance(newAddress, oldBalance);

        // transfer balance
        track.addBalance(ctx.sender(), ctx.callValue().value().negate());
        track.addBalance(newAddress, ctx.callValue().value());

        // update nonce
        track.incrementNonce(ctx.sender());

        // add internal transaction
        internalTx =
                newInternalTx(
                        ctx.sender(),
                        null,
                        track.getNonce(ctx.sender()),
                        ctx.callValue(),
                        ctx.callData(),
                        "create");
        ctx.helper().addInternalTransaction(internalTx);

        // execute transaction
        if (alreadyExsits) {
            result.setResultCodeAndEnergyRemaining(ResultCode.FAILURE, 0);
        } else {
            if (ArrayUtils.isNotEmpty(ctx.callData())) {
                result = jit.run(ctx.callData(), ctx, track);
            }
        }

        // post execution
        if (result.getResultCode().toInt() != ResultCode.SUCCESS.toInt()) {
            internalTx.reject();
            ctx.helper().rejectInternalTransactions(); // reject all

            track.rollback();
        } else {
            // charge the codedeposit
            if (result.getEnergyRemaining() < Constants.NRG_CODE_DEPOSIT) {
                result.setResultCodeAndEnergyRemaining(ResultCode.FAILURE, 0);
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
     *
     * @param message
     * @return
     */
    protected static ExecutionContext parseMessage(byte[] message) {
        ExecutionContext prev = context();

        ByteBuffer buffer = ByteBuffer.wrap(message);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte[] txHash = prev.transactionHash();

        byte[] address = new byte[AionAddress.SIZE];
        buffer.get(address);
        Address origin = prev.origin();
        byte[] caller = new byte[AionAddress.SIZE];
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

        return new ExecutionContext(
                txHash,
                AionAddress.wrap(address),
                origin,
                AionAddress.wrap(caller),
                nrgPrice,
                nrgLimit,
                callValue,
                callData,
                depth,
                kind,
                flags,
                blockCoinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockDifficulty);
    }

    /** Creates a new internal transaction. */
    private static AionInternalTx newInternalTx(
            Address from, Address to, BigInteger nonce, DataWord value, byte[] data, String note) {
        byte[] parentHash = context().transactionHash();
        int depth = context().depth();
        int index = context().helper().getInternalTransactions().size();

        return new AionInternalTx(
                parentHash,
                depth,
                index,
                new DataWord(nonce).getData(),
                from,
                to,
                value.getData(),
                data,
                note);
    }
}
